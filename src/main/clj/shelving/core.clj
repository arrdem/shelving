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
            [shelving.impl :as si]))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(import-vars
 [shelving.impl
  open close flush
  put get has?
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
