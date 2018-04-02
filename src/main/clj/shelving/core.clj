(ns shelving.core
  "Shelving; Noun;

  Collective form of Shelf; a thin slab of wood, metal, etc., fixed
  horizontally to a wall or in a frame, for supporting objects.

  Shelves are a structured store intended for use with linear scans
  for IDs and random point read/write on records / values."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:refer-clojure :exclude [flush])
  (:require [clojure.core.match :refer [match]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.core.cache :as cache]
            [potemkin :refer [import-vars]]
            [shelving.impl :as impl]
            [shelving.schema :as schema]
            [shelving.spec.walk :as s.w]
            [shelving.impl :as imp])
  (:import [me.arrdem.shelving
            MissingRelException
            MissingSpecException
            SchemaMigrationException
            RecordIdentifier]))

;; Data specs
;;--------------------------------------------------------------------------------------------------
(s/def ::spec-id
  qualified-keyword?)

(s/def :shelving.core.spec/type
  #{::spec})

(s/def :shelving.core.spec/record?
  boolean?)

(s/def :shelving.core.spec/rels
  (s/coll-of #(s/valid? ::rel-id %)))

(s/def ::spec
  (s/keys :req-un [:shelving.core.spec/type
                   :shelving.core.spec/record?
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
  ;; `put` and `get` have wrappers in this ns, so we don't import them
  has?
  schema
  enumerate-specs enumerate-rels
  count-spec count-rel
  enumerate-spec enumerate-rel
  put-rel get-rel
  q]
 [shelving.schema
  empty-schema value-spec record-spec
  has-spec? is-value? is-record?
  automatic-specs automatic-specs?
  automatic-rels automatic-rels?
  id-for-record
  check-schema
  check-schemas check-schemas!
  spec-rel has-rel? is-alias? resolve-alias
  id? ->id])

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

(defn- decompose
  "Given a schema, a spec in the schema, a record ID and the record as a
  value, decompose the record into its direct descendants and relations
  thereto."
  {:stability  :stability/stable
   :added      "0.0.0"}
  [schema spec id val]
  (let [acc! (volatile! [])
        ids (volatile! [])]
    (log/debug id (pr-str val))
    (binding [s.w/*walk-through-aliases* nil
              s.w/*walk-through-multis*  nil]
      (let [val* (s.w/walk-with-spec
                  (fn [subspec subval]
                    (vswap! ids conj
                            (if (= subspec spec)
                              (schema/as-id spec id)
                              (schema/id-for-record schema subspec subval)))
                    subval)
                  (fn [subspec subval]
                    (let [id* (last @ids)]
                      (vswap! ids pop)
                      (when (qualified-keyword? subspec)
                        (vswap! acc! conj [:record* subspec id* subval])
                        (when (not= spec subspec)
                          (vswap! acc! conj [:rel [spec subspec] id id*])))
                      id*))
                  spec val)]
        @acc!))))

;; FIXME (arrdem 2018-03-02):
;;   This recursively inserts sub-structures, but still inserts all structures fully formed.
;;   It should be able to insert only "denormalized" structures.
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
              ;; Base case. raw record that needs decomposing
              [:record spec* id* val*]
              (let [schema* (ensure-spec! conn spec)]
                (if (and (has? conn spec* id*)
                         (is-value? schema* spec*))
                  ;; skip the write
                  (recur queue* dirty?)
                  (recur (into queue* (decompose schema* spec* id* val*))
                         (conj dirty? id*))))

              ;; Case of an already decomposed record
              [:record* spec* id* val*]
              (let [schema* (ensure-spec! conn spec)]
                (if (and (has? conn spec* id*)
                         (is-value? schema* spec*))
                  ;; skip the write
                  (recur queue* dirty?)
                  (do (impl/put-spec conn spec* id* val*)
                      (recur queue* (conj dirty? id*)))))

              ;; Case of a record's rel(s)
              [:rel rel-id from-id to-id]
              (do (ensure-rel! conn rel-id)
                  (impl/put-rel conn rel-id from-id to-id)
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
   (let [id (id-for-record (impl/schema conn) spec val)]
     (put* conn spec id val)))
  ([conn spec id val]
   (s/assert spec val)
   (assert (is-record? (schema conn) spec))
   (put* conn spec id val)))

;; FIXME (arrdem 2018-03-04):
;;   figure out how to make this generic so that connections can leverage an object cache.
(defn get-spec
  "Restructuring get.

  Recovers a record from a shelf according to spec and ID, returning
  the given `not-found` sentinel if no such record exists, otherwise
  returning `nil`.

  Shelves must implement `#'shelving.impl/get-spec`, which backs this method."
  {:stability  :stability/stable
   :added      "0.0.0"}
  ([conn spec record-id]
   (get-spec conn spec record-id nil))
  ([conn spec record-id not-found]
   {:post [(or (s/valid? spec %)
               (= % not-found))]}
   (let [v (impl/get-spec conn spec (schema/as-id spec record-id) not-found)]
     (if (= v not-found) v
         (clojure.walk/prewalk
          (fn [node]
            (if-not (id? node) node
                    (get-spec conn
                              (.spec ^RecordIdentifier node)
                              (.id ^RecordIdentifier node))))
          v)))))

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
        (do (impl/set-schema conn schema*)
            schema*)))))

(def ^{:dynamic    true
       :stability  :stability/unstable
       :added      "0.0.0"}
  *query-cache*
  "A cache of compiled queries.

  By default LRU caches 128 query implementations.

  Queries are indexed by content hash without any attempt to normalize
  them. Run the same `#'q!` a bunch of times on related queries and this
  works. Spin lots of single use queries and you'll bust it."
  (atom (cache/lru-cache-factory {} :threshold 128)))

(defn q!
  "Direct query execution, compiling as required.

  Accepts a connection, a query, and a additional logic variable
  bindings. Caching compiled queries through `#'*query-cache*`,
  compiles the given query and executes it with the given logic
  variable bindings, returning a sequence of `:find` lvar maps."
  {:stability :stability/stable
   :added      "0.0.0"}
  [conn query & lvar-bindings]
  (let [query-id (impl/fingerprint-query conn query)]
    (apply (get (swap! *query-cache*
                       #(if (cache/has? % query-id)
                          (cache/hit % query-id)
                          (cache/miss % query-id (q conn query))))
                query-id)
           conn lvar-bindings)))
