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
  (:require [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]]
            [potemkin :refer [import-vars]]
            [shelving.impl :as impl]
            [shelving.schema :as schema]
            [shelving.impl :as imp]))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(import-vars
 [shelving.impl
  open close flush ;; FIXME: add transactions / pipeline support
  ;; `put` and `get` have wrappers in this ns.
  has?
  schema
  enumerate-specs enumerate-rels
  count-spec count-rel
  enumerate-spec enumerate-rel
  relate-by-id relate-by-value]
 [shelving.schema
  empty-schema value-spec record-spec
  has-spec? is-value? is-record?
  id-for-record
  schema->specs
  check-specs!
  spec-rel has-rel? is-alias? resolve-alias
  check-schema])

(defn- put*
  "Recursively traverse the given value, decomposing it into its
  constituent parts via a depth-first traversal, writing those parts
  and the appropriate relations to the shelving store if they don't
  exist already."
  [conn spec val id]
  ;; TODO: The implementation of this function ABSOLUTELY MUST cut the recursion when a value
  ;; already exists in the datastore.
  (let [schema (imp/schema conn)]
    (loop [[t & queue* :as queue] (schema/decompose schema spec id val)]
      (if (empty? queue) id
          (match t
            [:record spec* id* val*]
            (when-not (has? conn spec* id*)
              (imp/put-spec conn spec id* val*)
              (recur (into queue* (schema/decompose schema spec* id* val*))))
            
            [:rel rel-id from-id to-id]
            (imp/put-rel conn rel-id from-id to-id))))))

(defn put
  "Destructuring put.

  Enters a record into a shelf according to its spec in the schema,
  inserting substructures and updating all relevant rel(ation)s.

  For shelves storing \"records\" not \"values\", the `id` parameter
  may be used to either control the ID of the record, say for
  achieving an upsert.

  It is an error to specify the ID when inserting into a \"value\" shelf.

  Shelves must implement `#'shelving.impl/put`, which backs this method."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"}
  ([conn spec val]
   {:pre [(s/valid? spec val)]}
   (let [id (id-for-record (imp/schema conn) spec val)]
     (put* conn spec val id)))
  ([conn spec val id]
   {:pre [(s/valid? spec val)
          (schema/is-record? (imp/schema conn) spec)]}
   (put* conn spec val id)))

(defn get
  "Restructuring get.

  Recovers a record from a shelf according to spec and ID, returning
  the given `not-found` sentinel if no such record exists, otherwise
  returning `nil`.

  Shelves must implement `#'shelving.impl/get`, which backs this method."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"}
  ([conn spec record-id])
  ([conn spec record-id not-found]))
