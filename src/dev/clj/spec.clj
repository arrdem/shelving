(ns spec
  (:require [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]]))

(defn keys-as-map [keys-form]
  (let [[_keys & {:keys [req req-un opt opt-un] :as opts}] keys-form]
    (->> (concat (map (juxt #(keyword (name %)) identity) (concat req-un opt-un))
                 (map (juxt identity identity) (concat req opt)))
         (into {}))))

(defn postwalk-with-spec [spec obj f]
  (f spec
     (let [spec* (some-> spec s/get-spec s/describe*)]
       (match [spec*]
         [([`s/multi-spec mm k] :seq)]
         (postwalk-with-spec ((find-var mm) obj) obj f)

         [(s :guard keyword?)]
         (postwalk-with-spec s obj f)

         [([`s/keys & args] :seq)]
         (let [spec-map (keys-as-map spec*)]
           (->> (for [[k v] obj
                      :let [spec (get spec-map k)]
                      :when spec
                      :let [v' (postwalk-with-spec spec v f)]
                      :when v']
                  [k v'])
                (into {})))

         :else obj))))
