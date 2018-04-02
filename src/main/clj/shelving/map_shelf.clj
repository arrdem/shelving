(ns shelving.map-shelf
  "A naive EDN backed shelving unit."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "Eclipse Public License 1.0"
   :added   "0.1.0"}
  (:require [shelving.core :as sh]
            [shelving.impl :as impl]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [superset?]]
            [shelving.schema :as schema]))

;; Configs open into shelves
(defmethod impl/open ::config [{:keys [path schema load] :as s}]
  (when-not load
    (binding [*out* *err*]
      (println "Warning: opening connection without trying to load existing data set!")))
  (let [{persisted-schema :schema :as persisted-state}
        (if (and load
                 (.exists (io/file path)))
          (-> path
              io/reader
              java.io.PushbackReader.
              schema/read-with-shelving-tags
              ((fn [v] (assert (map? v)) v)))
          {})

        live-schema
        ;; Validate that the new schema supersets the old
        (if (and persisted-schema
                 (not (superset? (:specs schema) (:specs persisted-schema))))
          ;; If we have a mismatch, throw
          (throw (ex-info "Persisted schema does not validate against loaded schema!"
                          {:persisted  persisted-schema
                           :configured schema}))
          ;; Install the schema in a new db
          (merge persisted-schema schema))]

    (merge s {:type ::shelf, ::state (atom (assoc persisted-state :schema live-schema))})))

;; EDN shelves don't have write batching and are always open.
(defmethod impl/open ::shelf [s] s)

(defmethod impl/schema ::shelf [{:keys [::state]}]
  (:schema @state))

(defmethod impl/set-schema ::shelf [{:keys [::state]} schema*]
  (swap! state assoc :schema schema*))

;; EDN shelves don't have write batching and don't have to be closed.
(defmethod impl/flush ::shelf [{:keys [::state path]}]
  (let [file (io/file path)]
    (if-let [parent (.getParentFile file)]
      (.mkdirs parent))
    (with-open [writer (io/writer file)]
      (binding [*out*                               writer
                clojure.core/*print-namespace-maps* false
                clojure.core/*print-readably*       true]
        (pr @state)))))

(defmethod impl/close ::shelf [s]
  (sh/flush s))

(defmethod impl/get-spec ::shelf
  ([conn spec record-id]
   (sh/get-spec conn spec record-id nil))
  ([{:keys [::state]} spec record-id not-found]
   (-> @state (get :records) (get spec) (get record-id not-found))))

(declare ^:private put*)

(defn- rel-invalidate-record*
  "Relation implementation detail.
  Returns a new rel map, with the given record-id's entries purged."
  [[from-spec to-spec :as rel-id] rel-map record-id]
  (let [tos (-> rel-map (get from-spec) (get record-id))]
    (as-> rel-map %
      (update % from-spec dissoc record-id)
      (update % :pairs #(remove (comp #{record-id} first) %))
      (reduce (fn [% to]
                (update-in % [to-spec to] #(remove #{record-id} %)))
              % tos))))

(defn- rel-invalidate-record
  "Relation implementation detail.
  Removes all pairs containing the given `record-id` from all rel tables."
  [rels schema spec record-id]
  (->> (when (sh/is-record? schema spec)
         (for [[[from-spec to-spec :as rel-id] rel-map] rels
               :when                                    (or (= spec from-spec)
                                                            (= spec to-spec))]
           [rel-id (rel-invalidate-record* rel-id rel-map record-id)]))
       (into {})
       (merge rels)))

(defn- put*
  "Implementation detail.
  Backs `#'sh/put`, providing the actual recursive write logic."
  [state schema spec record-id record]
  (cond-> state
    ;; FIXME (arrdem 2018-02-25):
    ;;   can skip invalidation when we're inserting a new record
    (sh/is-record? schema spec) (update :rels rel-invalidate-record schema spec record-id)
    true                        (assoc-in [:records spec record-id] record)))

(defmethod impl/put-spec ::shelf
  [{:keys [::state flush-after-write] :as conn} spec id val]
  (swap! state put* (sh/schema conn) spec id val)
  (when flush-after-write
    (sh/flush conn))
  id)

(defmethod impl/enumerate-spec ::shelf [{:keys [::state]} spec]
  (some-> @state (get :records) (get spec) keys))

(defmethod impl/count-spec ::shelf [conn spec]
  (count (sh/enumerate-spec conn spec)))

(defmethod impl/put-rel ::shelf [{:keys [::state] :as conn} rel-id from-id to-id]
  (let [[from-spec to-spec :as rel-id*] (sh/resolve-alias (sh/schema conn) rel-id)
        [from-id* to-id*]               (if (= rel-id rel-id*) [from-id to-id] [to-id from-id])]
    (swap! state update-in [:rels rel-id*]
           (fn [state]
             (-> state
                 (update :pairs (fnil conj #{}) [from-id* to-id*])
                 (update-in [from-spec from-id*] (fnil conj #{}) to-id*)
                 (update-in [to-spec   to-id*]   (fnil conj #{}) from-id*))))))

(defmethod impl/enumerate-rel ::shelf [{:keys [::state] :as conn} rel]
  (let [schema (sh/schema conn)]
    (if (sh/is-alias? schema rel)
      (map reverse (sh/enumerate-rel conn (sh/resolve-alias schema rel)))
      (some-> @state (get :rels) (get rel) (get :pairs) seq))))

(defmethod impl/count-rel ::shelf [{:keys [::state] :as conn} rel]
  (count (sh/enumerate-rel conn (sh/resolve-alias (sh/schema conn) rel))))

(defmethod impl/get-rel ::shelf [{:keys [::state] :as conn} [from-spec to-spec :as rel] id]
  (some-> @state (get :rels) (get (sh/resolve-alias (sh/schema conn) rel)) (get from-spec) (get id) seq))

(defn ->MapShelf
  "Configures an in-memory, EDN serialized, association based shelf
  which can be opened for reading and writing."
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
