(ns shelving.spec.core
  "Tools for working with `clojure.spec(.alpha)`."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]]
            [detritus.update :refer [fix]]))

(defn keys-as-map
  "Implementation detail of `#'walk-with-spec`.

  Consumes a complete s/keys form, producing a map from map keys to
  the spec for that key.

  Imposes the restriction that there be PRECISELY ONE spec for any
  given key. Will throw otherwise."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [keys-form]
  (let [[_keys & {:keys [req req-un opt opt-un] :as opts}] keys-form]
    (->> (concat (mapcat (juxt #(keyword (name %)) identity) (concat req-un opt-un))
                 (mapcat (juxt identity identity) (concat req opt)))
         (apply hash-map))))

(defn pred->preds
  "Given a `s/describe*` or 'pred' structure, return its component
  preds (keyword identifiers and predicate forms)."
  {:stability  :stability/unstable
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

(defn subspec-pred-seq
  "Given a keyword naming a spec, return the depth-first .

  Does not recur across spec keywords."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [s]
  (let [s* (or (s/get-spec s) ::s/unknown)]
    (when (= s* ::s/unknown)
      (throw (ex-info (format "Unable to resolve specs! Spec '%s' is unknown" s) {})))
    (let [s* (s/describe* s*)]
      (tree-seq seq? pred->preds s*))))

(defn spec-seq
  "Given a keyword naming a spec, recursively return a sequence of the
  distinct keywords naming the subspecs of that spec. The returned
  sequence includes the original spec.

  Throws if a `::s/unknown` spec is encountered.

  Named for `#'file-seq`."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [s & specs]
  (loop [[s & specs* :as specs] (cons s specs)
         acc                    []
         seen?                  #{}]
    (if (empty? specs) acc
        (if (and s (not (seen? s)))
          (if (qualified-keyword? s)
            (recur (concat specs* (filter qualified-keyword? (subspec-pred-seq s)))
                   (conj acc s)
                   (conj seen? s))
            (recur (concat specs* (filter qualified-keyword? (subspec-pred-seq s))) acc seen?))
          (recur specs* acc seen?)))))

;; FIXME (arrdem 2018-02-19):
;;
;;   spec "equivalence" checker? At least being able to show that `s/keys` forms subset or superset
;;   each-other would be nice and useful.
