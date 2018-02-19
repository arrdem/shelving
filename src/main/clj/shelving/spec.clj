(ns shelving.spec
  "Tools for working with `clojure.spec(.alpha)`."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]]))

;; Not a require to avoid a cyclic dependency
(alias 'sh 'shelving.core)

(defn keys-as-map
  "Implementation detail of `#'walk-with-spec`.

  Consumes a complete s/keys form, producing a map from map keys to
  the spec for that key.

  Imposes the restriction that there be PRECISELY ONE spec for any
  given key. Will throw otherwise."
  {:categories #{::sh/spec}
   :stability  :stability/unstable
   :added      "0.0.0"}
  [keys-form]
  (let [[_keys & {:keys [req req-un opt opt-un] :as opts}] keys-form]
    (->> (concat (mapcat (juxt #(keyword (name %)) identity) (concat req-un opt-un))
                 (mapcat (juxt identity identity) (concat req opt)))
         (apply hash-map))))

(defn subspecs
  "Given a `s/describe*` structure, return the subspecs (keyword
  identifiers and predicate forms) of the spec."
  {:categories #{::sh/spec}
   :stability  :stability/unstable
   :added      "0.0.0"}
  [s]
  (match s
    (s :guard qualified-keyword?) [s]
    (s :guard ifn?) []
    ([`s/multi-spec & _] :seq) [] ;; FIXME indeterminable?
    ([`s/keys & _] :seq) (vals (keys-as-map s))
    ([`s/nilable s] :seq) [s]
    ([`s/or & kw-preds] :seq) (map first (partition 2 kw-preds))
    ([`s/and & preds] :seq) preds
    ([`s/merge & preds] :seq) preds
    ([`s/coll-of pred & _] :seq) [pred]
    ([(:or `s/map-of `s/every-kv) k-pred v-pred] :seq) [k-pred v-pred]
    ([`s/tuple & preds] :seq) preds))

;; FIXME (arrdem 2018-02-19):
;; 
;;   spec "equivalence" checker? At least being able to show that `s/keys` forms subset or superset
;;   each-other would be nice and useful.
