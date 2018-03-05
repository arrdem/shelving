(ns shelving.log-shelf
  "A really fantastically stupid back-end based on an EDN serialized append only log with no compation.

  Hysterically inefficient but easy to get right.

  Grossly inappropriate for most applications."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.1.0"}
  (:require [clojure.core.match :refer [match]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [shelving.core :as sh]
            [shelving.impl :as imp]))

;; Configs open into shelves
(defmethod imp/open ::config [{:keys [path schema load] :as s}]
  (when-not load
    (binding [*out* *err*]
      (println "Warning: opening connection without trying to load existing data set!")))
  (let [state (->> (if (and load
                            (.exists (io/file path)))
                     (-> path
                         io/reader
                         java.io.PushbackReader.
                         edn/read)
                     (list))
                   (cons [:schema schema])
                   atom)]
    ;; Doesn't even bother with schema validation
    ;;
    ;; SERIOUSLY DON'T USE THIS
    {:type              ::shelf
     ::state            state
     :path              path
     :flush-after-write (:flush-after-write s)}))

;; EDN shelves don't have write batching and are always open.
(defmethod imp/open ::shelf [s] s)

(defmethod imp/schema ::shelf [{:keys [::state]}]
  (loop [[t & ts* :as ts] @state]
    (if-not ts nil
            (match t
              [:schema schema] schema
              :else (recur ts*)))))

(defmethod imp/set-schema ::shelf [{:keys [::state]} schema]
  (swap! state conj [:schema schema]))

;; EDN shelves don't have write batching and don't have to be closed.
(defmethod imp/flush ::shelf [{:keys [::state path]}]
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
   (sh/get-rel conn spec record-id nil))
  ([{:keys [::state]} spec record-id not-found]
   (loop [[t & ts* :as ts] @state]
     (if-not ts
       not-found
       (match t
         [spec record-id val] val
         :else (recur ts*))))))

(defmethod imp/put-rel ::shelf
  [{:keys [::state flush-after-write] :as conn} rel-id from-id to-id]
  (swap! state conj [from-id rel-id to-id])
  (when flush-after-write
    (sh/flush conn))
  [from-id rel-id to-id])

(defmethod imp/put-spec ::shelf
  [{:keys [::state flush-after-write] :as conn} spec id val]
  (swap! state conj [spec id val])
  (when flush-after-write
    (sh/flush conn))
  id)

(defmethod imp/enumerate-spec ::shelf [{:keys [::state]} spec]
  (keep (fn [t]
          (match t
            [spec uuid _] uuid
            :else nil))
        @state))

(defmethod imp/count-spec ::shelf [conn spec]
  (count (imp/enumerate-spec conn spec)))

(defn- enumerate-rel* [[t & t* :as tuples] from-spec fr? to-spec tr? invalidated]
  (if-not tuples nil
          (let [tv? (if tr? (complement invalidated) (constantly true))
                fv? (if fr? (complement invalidated) (constantly true))]
            (match t
              (:or [(to-id :guard tv?) [to-spec from-spec] (from-id :guard fv?)]
                   [(from-id :guard fv?) [from-spec to-spec] (to-id :guard tv?)])
              (cons [from-id to-id]
                    (lazy-seq
                     (enumerate-rel* t* from-spec fr? to-spec tr? invalidated)))

              [to-spec some-id _]
              (if tr?
                (recur t* from-spec fr? to-spec tr? (conj invalidated some-id))
                (recur t* from-spec fr? to-spec tr? invalidated))

              [from-spec some-id _]
              (if fr?
                (recur t* from-spec fr? to-spec tr? (conj invalidated some-id))
                (recur t* from-spec fr? to-spec tr? invalidated))

              :else
              (recur t* from-spec fr? to-spec tr? invalidated)))))

(defmethod imp/enumerate-rel ::shelf [{:keys [::state] :as conn} [from-spec to-spec]]
  (let [tuples @state
        schema (imp/schema conn)]
    (distinct
     (enumerate-rel* tuples
                     from-spec (sh/is-record? schema from-spec)
                     to-spec (sh/is-record? schema to-spec)
                     #{}))))

(defmethod imp/count-rel ::shelf [conn rel]
  (count (imp/enumerate-rel conn rel)))

(defn- get-rel* [[from-spec to-spec :as rel] id [t & ts* :as tuples] from-record? to-record? invalidated]
  (if-not tuples nil
          (let [valid? (complement invalidated)]
            (match t
              (:or [(id :guard valid?)    [from-spec to-spec] (to-id :guard valid?)]
                   [(to-id :guard valid?) [to-spec from-spec] (id :guard valid?)])
              (cons to-id (lazy-seq (get-rel* rel id ts* from-record? to-record? invalidated)))

              [from-spec id _]
              (if from-record? nil
                  (recur rel id ts* from-record? to-record? invalidated))

              [to-spec some-id _]
              (if to-record?
                (recur rel id ts* from-record? to-record? (conj invalidated some-id))
                (recur rel id ts* from-record? to-record? invalidated))

              :else
              (recur rel id ts* from-record? to-record? invalidated)))))

(defmethod imp/get-rel ::shelf [{:keys [::state] :as conn} rel id]
  (let [schema       (imp/schema conn)
        tuples       @state
        from-record? (sh/is-record? schema (first rel))
        to-record?   (sh/is-record? schema (second rel))]
    (distinct
     (get-rel* rel id tuples from-record? to-record? #{}))))

(defn ->LogShelf
  "Configures a (trivial) EDN shelf based on an append only log which
  can be opened for reading and writing."
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
