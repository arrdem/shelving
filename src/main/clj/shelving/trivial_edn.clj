(ns shelving.trivial-edn
  "A naive EDN backed shelving unit."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "Eclipse Public License 1.0"
   :added   "0.1.0"}
  (:require [shelving.core :as sh]
            [shelving.impl :as imp]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [superset?]]))

;; Configs open into shelves
(defmethod imp/open ::config [{:keys [path schema load] :as s}]
  (when-not load
    (binding [*out* *err*]
      (println "Warning: opening connection without trying to load existing data set!")))
  (let [state           (-> (if (and load
                                     (.exists (io/file path)))
                              (-> path
                                  io/reader
                                  java.io.PushbackReader.
                                  edn/read
                                  ((fn [v] (assert (map? v)) v)))
                              {})
                            atom)
        conn            (-> s
                            (assoc :type ::shelf)
                            (assoc ::state state))
        persisted-specs (-> state deref :schema)
        our-specs       (sh/schema->specs schema)]
    ;; Check the persisted schema
    (sh/check-specs! persisted-specs our-specs)
    
    ;; Install the schema updates in a new db 
    (swap! state update :schema merge our-specs)

    ;; Validate any loaded data
    (doseq [spec (sh/enumerate-specs conn)
            id   (sh/enumerate-spec conn spec)
            :let [record (sh/get conn spec id)]]
      (if-not (s/valid? spec record)
        (throw (ex-info "Failed to validate record!"
                        {:spec        spec
                         :record-id   id
                         :record      record
                         :explanation (s/explain spec record)}))))

    conn))

;; EDN shelves don't have write batching and are always open.
(defmethod imp/open ::shelf [s] s)

(defmethod imp/schema ::shelf [{:keys [schema]}]
  schema)

;; EDN shelves don't have write batching and don't have to be closed.
(defmethod imp/flush ::shelf [{:keys [shelving.trivial-edn/state path]}]
  (let [file (io/file path)]
    (if-let [parent (.getParentFile file)]
      (.mkdirs parent))
    (with-open [writer (io/writer file)]
      (binding [*out*                               writer
                clojure.core/*print-namespace-maps* false
                clojure.core/*print-readably*       true]
        (pr @state)))))

(defmethod imp/close ::shelf [s]
  (sh/flush s))

(defmethod imp/get-spec ::shelf
  ([conn spec record-id]
   (sh/get conn spec record-id nil))
  ([{:keys [shelving.trivial-edn/state]} spec record-id not-found]
   (-> @state (get :records) (get spec) (get record-id not-found))))

(defmethod imp/put-rel ::shelf
  [{:keys [shelving.trivial-edn/state flush-after-write] :as conn} [from-spec to-spec :as rel-id] from-id to-id]
  (swap! state #(update-in % [:rels rel-id]
                  (fn [state]
                    (-> state
                        (update-in [from-spec from-id] (fnil conj #{}) to-id)
                        (update-in [to-spec to-id] (fnil conj #{}) from-id)
                        (update-in [:pairs] (fnil conj #{}) [from-id to-id])))))
  (when flush-after-write
    (sh/flush conn))
  [from-id rel-id to-id])

(defmethod imp/put-spec ::shelf
  [{:keys [shelving.trivial-edn/state flush-after-write] :as conn} spec id val]
  (swap! state assoc-in [:records spec id] val)
  (when flush-after-write
    (sh/flush conn))
  id)

(defmethod imp/enumerate-spec ::shelf [{:keys [shelving.trivial-edn/state]} spec]
  (some-> @state (get :records) (get spec) keys))

(defmethod imp/count-spec ::shelf [{:keys [shelving.trivial-edn/state]} spec]
  (or (some-> @state (get :records) (get spec) keys count) 0))

(defmethod imp/enumerate-rel ::shelf [{:keys [shelving.trivial-edn/state]} rel]
  (some-> @state (get :rels) (get rel) (get :pairs) seq))

(defmethod imp/count-rel ::shelf [{:keys [shelving.trivial-edn/state schema]} rel]
  (or (some-> @state (get :rels) (get (sh/resolve-alias schema rel)) :pairs count) 0))

(defmethod imp/get-rel ::shelf [{:keys [shelving.trivial-edn/state schema]} [from-spec to-spec :as rel] id]
  (some-> @state (get :rels) (get (sh/resolve-alias schema rel)) (get from-spec) (get id) seq))

(defn ->TrivialEdnShelf
  "Configures a (trivial) EDN shelf which can be opened for reading and writing."
  {:categories #{::sh/basic}
   :added      "0.0.0"
   :stability  :stability/stable}
  [schema path & {:keys [flush-after-write load]
                  :or   {flush-after-write false
                         load              true}}]
  {:type              ::config
   :schema            schema
   :path              path
   :flush-after-write flush-after-write
   :load              true})
