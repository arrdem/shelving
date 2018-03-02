(ns shelving.core
  "Shelving; Noun;

  Collective form of Shelf; a thin slab of wood, metal, etc., fixed
  horizontally to a wall or in a frame, for supporting objects.

  Shelves are a structured store intended for use with linear scans
  for IDs and random point read/write. Shelves are appropriate only
  for use when the read and write load is so small that flushing the
  entire store every time is an acceptable cost compared to the
  complexity of a more traditional storage layer or database."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:refer-clojure :exclude [flush get])
  (:require [clojure.core.match :refer [match]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [potemkin :refer [import-vars]]
            [shelving.impl :as imp]
            [shelving.schema :as schema]
            shelving.query)
  (:import [me.arrdem.shelving
            MissingRelException
            MissingSpecException
            SchemaMigrationException]))

;; Data specs
;;--------------------------------------------------------------------------------------------------
(s/def ::spec-id
  qualified-keyword?)

(s/def :shelving.core.spec/type
  #{::spec})

(s/def :shelving.core.spec/record?
  boolean?)

(s/def :shelving.core.spec/id-fn
  ifn?)

(s/def :shelving.core.spec/rels
  (s/coll-of #(s/valid? ::rel-id %)))

(s/def ::spec
  (s/keys :req-un [:shelving.core.spec/type
                   :shelving.core.spec/record?
                   :shelving.core.spec/id-fn
                   :shelving.core.spec/rels]))

(s/def :shelving.core.schema/type #{::schema})

(s/def :shelving.core.schema/specs
  (s/map-of #(s/valid? ::spec-id %) #(s/valid? ::spec %)))

(s/def :shelving.core.schema/rels
  (s/map-of #(s/valid? ::rel-id %) #(s/valid? ::rel+alias %)))

(s/def :shelving.core/schema
  (s/keys :req-un [:shelving.core.schema/type
                   :shelving.core.schema/specs
                   :shelving.core.schema/rels]))

(s/def ::rel-id
  (s/tuple ::spec-id ::spec-id))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(import-vars
 [shelving.impl
  ;; FIXME: add transactions / pipeline support
  open close flush
  ;; `put` and `get` have wrappers in this ns.
  has?
  schema
  enumerate-specs enumerate-rels
  count-spec count-rel
  enumerate-spec enumerate-rel 
  put-rel get-rel]
 [shelving.schema
  empty-schema value-spec record-spec
  has-spec? is-value? is-record?
  automatic-specs automatic-specs?
  automatic-rels automatic-rels?
  id-for-record
  check-schema
  check-schemas check-schemas!
  spec-rel has-rel? is-alias? resolve-alias]
 [shelving.query
  q q!])

(declare alter-schema)

(defn- ensure-spec! [conn spec]
  (let [schema (schema conn)]
    (cond (has-spec? schema spec)
          schema
          
          (not (automatic-specs? schema))
          (throw (MissingSpecException.
                  (format "Attempted to insert into unknown spec %s!" spec)))

          :else
          (alter-schema conn value-spec spec))))

(defn- ensure-rel! [conn [from to :as rel]]
  (let [schema (schema conn)]
    (cond (has-rel? schema rel)
          schema

          (not (automatic-rels? schema))
          (throw (MissingRelException.
                  (format "Attempted to insert into unknown rel %s!" rel)))

          :else
          (do (ensure-spec! conn from)
              (ensure-spec! conn to)
              (alter-schema conn spec-rel rel)))))

(defn- put*
  "Recursively traverse the given value, decomposing it into its
  constituent parts via a depth-first traversal, writing those parts
  and the appropriate relations to the shelving store if they don't
  exist already."
  [conn spec id val]
  (loop [[t & queue* :as queue] [[:record spec id val]], dirty? #{}]
    (if (empty? queue) id
        (do (log/debug t)
            (match t
              [:record spec* id* val*]
              (let [schema* (ensure-spec! conn spec)] 
                (if (and (has? conn spec* id*)
                         (is-value? schema* spec*))
                  ;; skip the write
                  (recur queue* dirty?)
                  (do (imp/put-spec conn spec* id* val*)
                      (recur (into queue*
                                   (schema/decompose schema* spec* id* val*))
                             (conj dirty? id*)))))

              [:rel rel-id from-id to-id]
              (do (ensure-rel! conn rel-id)
                  (imp/put-rel conn rel-id from-id to-id)
                  (recur queue* dirty?))))))
  id)

(defn put-spec
  "Destructuring put.

  Enters a record into a shelf according to its spec in the schema,
  inserting substructures and updating all relevant rel(ation)s.

  For shelves storing \"records\" not \"values\", the `id` parameter
  may be used to either control the ID of the record, say for
  achieving an upsert.

  It is an error to specify the ID when inserting into a \"value\" shelf.

  Shelves must implement `#'shelving.impl/put-spec`, which backs this method."
  {:stability  :stability/stable
   :added      "0.0.0"}
  ([conn spec val]
   (s/assert spec val)
   (let [id (id-for-record (imp/schema conn) spec val)]
     (put* conn spec id val)))
  ([conn spec id val]
   (s/assert spec val)
   (assert (is-record? (schema conn) spec))
   (put* conn spec id val)))


;; FIXME: this needs to do value recomposition if that's behavior supported by the back-end
;; currently in use. How to express that? Above we don't do decomposition reversably...
(defn get-spec
  "Restructuring get.

  Recovers a record from a shelf according to spec and ID, returning
  the given `not-found` sentinel if no such record exists, otherwise
  returning `nil`.

  Shelves must implement `#'shelving.impl/get-spec`, which backs this method."
  {:stability  :stability/stable
   :added      "0.0.0"}
  ([conn spec record-id]
   {:post [(s/valid? spec %)]}
   (imp/get-spec conn spec record-id nil))
  ([conn spec record-id not-found]
   {:post [(s/valid? spec %)]}
   (imp/get-spec conn spec record-id not-found)))

(defn alter-schema
  "Attempts alter the schema of a live connection.

  I CANNOT EMPHASIZE ENOUGH HOW DANGEROUS THIS COULD BE.

  1. Gets the live schema from the connection
  2. Attempts to apply the schema altering function
  3. Attempts to validate that the produced new schema is compatible
  4. Irreversibly writes the new schema to the store

  Applies the given transformer function to the current live schema
  and the given arguments. Checks that the resulting schema is
  compatible with the existing schema (eg. strictly additive), sending
  the schema change to the connection only if compatibility checking
  succeeds.

  Returns the new schema.

  Throws `me.arrdem.shelving.SchemaMigrationexception` without
  impacting the connection or its backing store if schema
  incompatibilities are detected."
  {:stability  :stability/stable
   :added      "0.0.0"}
  [conn f & args]
  (let [schema                  (schema conn)
        [schema* ^Throwable e?] (try [(apply f schema args) nil]
                                     (catch Throwable e [nil e]))]
    (if e?
      (throw (SchemaMigrationException.
              "Failed to migrate schema while applying transformation function!"
              e?))
      (if-let [problems (schema/check-schemas schema schema*)]
        (throw (SchemaMigrationException.
                "Failed to validate the migrated schema!" problems))
        ;; FIXME: this is SUPER FUCKING DANGEROUS. Who knows what the storage layer is gonna do. If
        ;; this throws, we're kinda shit out of luck here.
        (do (imp/set-schema conn schema*)
            schema*)))))
