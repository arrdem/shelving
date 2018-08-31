(ns shelving.juxt
  "A naive shelving unit which repeats writes."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "Eclipse Public License 1.0"
   :added   "0.1.0"}
  (:refer-clojure :exclude [doall])
  (:require [shelving.core :as sh]))

(defn- doall [conns op & args]
  (let [res0 (apply op (first conns) args)]
    (doseq [conn (rest conns)]
      (let [res (apply op conn args)]
        (when-not (= res res0)
          (throw (ex-info "Backends returned inconsistent values!"
                          {:op                      op
                           :expected                res0
                           :got                     res
                           :inconsistent-connection conn})))))
    res0))

(defmethod sh/open ::config [{:keys [configs schema]}]
  {:type ::shelf
   :schema schema
   :conns (mapv #(sh/open (% schema)) configs)})

(defmethod sh/open ::shelf [s] s)

(defmethod sh/flush ::shelf [{:keys [conns]}]
  (doall conns sh/flush))

(defmethod sh/close ::shelf [{:keys [conns]}]
  (doall conns sh/flush))

(defmethod sh/get ::shelf [{:keys [conns]} spec record-id]
  (doall conns sh/get spec record-id))

;; Note: This one's a bit tricky because for records we have to generate only one consistent ID
;; across back-ends.
(defmethod sh/put ::shelf
  ([{:keys [conns schema] :as conn} spec val]
   (if (sh/is-record? schema spec)
     (sh/put conn spec (sh/id-for-record schema spec val) val)
     (doall conns sh/put spec val)))
  ([{:keys [conns]} spec id val]
   (doall conns sh/put spec id val)))

(defmethod sh/enumerate-specs ::shelf [{:keys [conns]}]
  (doall conns sh/enumerate-specs))

(defmethod sh/enumerate-spec ::shelf [{:keys [conns]} spec]
  (doall conns sh/enumerate-spec spec))

(defmethod sh/enumerate-rels ::shelf [{:keys [conns]}]
  (doall conns sh/enumerate-rels))

(defmethod sh/enumerate-rel ::shelf [{:keys [conns]} rel]
  (doall conns sh/enumerate-rels rel))

(defmethod sh/relate-by-id ::shelf [{:keys [conns]} rel id]
  (doall conns sh/relate-by-id rel id))

(defn ->JuxtShelf
  "Configures a shelf which will replicate reads & writes across all its back-ends."
  {:categories #{::sh/basic}
   :added      "0.0.1"
   :stability  :stability/stable}
  [schema & ->configs]
  {:type    ::config
   :schema  schema
   :configs ->configs})
