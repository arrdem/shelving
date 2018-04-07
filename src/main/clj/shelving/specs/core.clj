(ns shelving.specs.core
  "Specs for `shelving.core`."
  (:require [clojure.spec.alpha :as s]
            [detritus.spec :refer [deftag]])
  (:import me.arrdem.shelving.RecordIdentifier))

(s/def ::spec-id
  qualified-keyword?)

(s/def ::record?
  boolean?)

(deftag spec
  [record? :- boolean?
   rels :- (s/coll-of ::rel-id)])

(deftag schema
  [specs :- (s/map-of ::spec-id ::spec)
   rels :- (s/map-of ::rel-id ::rel+alias)])

(s/def ::rel-id
  (s/tuple ::spec-id ::spec-id))

(s/def ::rel-tuple
  (s/tuple ::record-id ::record-id))

(s/def ::record-id
  #(instance? % RecordIdentifier))

(s/def ::some-id
  (s/or :id uuid?
        :spec+id ::record-id))

(s/def ::config
  any?)

(s/def ::conn
  any?)
