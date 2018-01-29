(ns spec
  (:require [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]]))

(defn walk-with-spec [spec obj]
  (println spec obj)
  
  (if-let [spec* (s/get-spec spec)]
    (match [(s/describe* spec*)]
      [([`s/multi-spec mm k] :seq)]
      (recur ((find-var mm) obj) obj)

      [(s :guard keyword?)]
      (recur s obj)

      [([`s/keys & args] :seq)]
      (let [{:keys [req req-un opt opt-un]} (apply hash-map args)]
        (concat (mapcat (partial apply walk-with-spec)
                        (keep #(if (contains? obj %)
                                 [% (get obj %)])
                              (concat req opt)))
                (mapcat (partial apply walk-with-spec)
                        (keep #(if (contains? obj (keyword (name %)))
                                 [% (get obj (keyword (name %)))])
                              (concat req-un opt-un)))))
      
      [s]
      [[s obj]])
    [spec obj]))
