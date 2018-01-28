(ns shelving.trivial-edn
  "A naive EDN backed shelving unit."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "Eclipse Public License 1.0"
   :added   "0.1.0"}
  (:require [shelving.core :as sh] 
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [superset?]]))

;; Configs open into shelves
(defmethod sh/open ::config [{:keys [path schema] :as s}]
  (let [state            (-> (if (.exists (io/file path))
                               (-> path
                                   io/reader
                                   java.io.PushbackReader.
                                   edn/read
                                   ((fn [v] (assert (map? v)) v))) 
                               {})
                             atom)
        conn             (-> s
                             (assoc :type ::shelf)
                             (assoc ::state state))
        persisted-schema (-> state deref :schema)]
    ;; Validate that the new schema supersets the old
    (if (and persisted-schema
             (not (superset? (sh/schema->specs schema) persisted-schema)))
      ;; If we have a mismatch, throw
      (throw (ex-info "Persisted schema does not validate against loaded schema!"
                      {:persisted  persisted-schema
                       :configured (sh/schema->specs schema)}))
      ;; Install the schema in a new db
      (swap! state assoc :schema (sh/schema->specs schema)))

    ;; Validate any loaded data
    (doseq [[spec records] (-> state deref :store)
            [id record]    records]
      (if-not (s/valid? spec record)
        (throw (ex-info "Failed to validate record!"
                        {:spec        spec
                         :record-id   id
                         :record      record
                         :explanation (s/explain spec record)}))))

    conn))

;; EDN shelves don't have write batching and are always open.
(defmethod sh/open ::shelf [s] s)

;; EDN shelves don't have write batching and don't have to be closed.
(defmethod sh/flush ::shelf [{:keys [shelving.trivial-edn/state path]}]
  (let [file (io/file path)]
    (if-let [parent (.getParentFile file)]
      (.mkdirs parent))
    (with-open [writer (io/writer file)]
      (binding [*out*                               writer
                clojure.core/*print-namespace-maps* false
                clojure.core/*print-readably*       true]
        (pr @state)))))

(defmethod sh/close ::shelf [s]
  (sh/flush s))

(defmethod sh/get ::shelf [{:keys [shelving.trivial-edn/state]} spec record-id]
  (-> @state (get :store) (get spec) (get record-id)))

(defmethod sh/put ::shelf
  ([{:keys [schema] :as conn} spec record]1
   (sh/put conn spec (sh/id-for-record schema spec record) record))
  ([{:keys [shelving.trivial-edn/state schema flush-after-write]} spec record-id record]
   (let [state-val @state]
     (dosync
      (assert (uuid? record-id))
      (assert (sh/has-spec? schema spec))
      (assert (s/valid? spec record))
      (swap! state assoc-in [:store spec record-id] record)))
   (when flush-after-write
     (flush state))
   record-id))

(defmethod sh/enumerate ::shelf
  ([{:keys [schema shelving.trivial-edn/state]}]
   (-> @state (get :schema) seq))
  ([{:keys [schema shelving.trivial-edn/state]} spec]
   (-> @state (get :store) (get spec) keys)))

(defn ->TrivialEdnShelf
  "Configures a (trivial) EDN shelf which can be opened for reading and writing."
  [schema path & {:keys [flush-after-write]
                  :or   {flush-after-write false}}]
  {:type              ::config
   :schema            schema
   :path              path
   :flush-after-write flush-after-write})
