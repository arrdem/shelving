(ns spec
  (:require [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]]))

(defn keys-as-map [keys-form]
  (let [[_keys & {:keys [req req-un opt opt-un] :as opts}] keys-form]
    (->> (concat (map (juxt #(keyword (name %)) identity) req-un)
                 (map (juxt #(keyword (name %)) identity) opt-un)
                 (map (juxt identity identity) req)
                 (map (juxt identity identity) opt))
         (into {}))))

(defn postwalk-with-spec [spec obj f]
  (if-let [spec* (some-> spec s/get-spec s/describe*)]
    (match [spec*]
      [([`s/multi-spec mm k] :seq)]
      (f spec (postwalk-with-spec ((find-var mm) obj) obj f))
      
      [(s :guard keyword?)]
      (f spec (postwalk-with-spec s obj f))
      
      [([`s/keys & args] :seq)]
      (f spec
         (let [spec-map (keys-as-map spec*)]
           (->> (for [[k v] obj
                      :let [spec (get spec-map k)]
                      :when spec
                      :let [v' (postwalk-with-spec spec v f)]
                      :when v']
                  [k v'])
                (into {}))))

      :else
      (f spec obj))
    (f spec obj)))
