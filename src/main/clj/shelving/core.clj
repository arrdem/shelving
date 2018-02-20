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
            [potemkin :refer [import-vars]]
            [shelving.impl :as impl]
            [shelving.schema :as schema]))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(import-vars
 [shelving.impl
  open close flush ;; FIXME: add transactions / pipeline support
  get has?
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

(defn put
  "Enters a record into a shelf according to its spec in the schema,
  inserting substructures and updating all relevant rel(ation)s.

  For shelves storing \"records\" not \"values\", the `id` parameter
  may be used to either control the ID of the record, say for
  achieving an upsert.

  It is an error to specify the ID when inserting into a \"value\" shelf.

  Shelves must implement `#'shelving.impl/put`, which backs this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"}
  ([conn spec val]
   (let [schema (impl/schema conn)]
     ))
  ([conn spec val id]))
