(ns shelving.core
  "Shelving; Noun;

  Collective form of Shelf; a thin slab of wood, metal, etc., fixed
  horizontally to a wall or in a frame, for supporting objects.

  Shelves are a structured store intended for use with linear scans
  for IDs and random point read/write. Shelves are appropriate only
  for use when the read and write load is so small that flushing the
  entire store every time is an acceptable cost compared to the
  complexity of a more traditional storage layer or database."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "Eclipse Public License 1.0"
   :added   "0.1.0"}
  (:refer-clojure :exclude [flush get]) 
  (:import [java.util UUID]
           [java.security MessageDigest]
           [java.nio ByteBuffer]))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(defmulti open
  "Opens a shelf for reading or writing.

  Shelves must implement this method."
  :type)

(defmulti flush
  "Flushes (commits) an open shelf.

  Shelves must implement this method."
  :type)

(defmulti close
  "Closes an open shelf.

  Shelves may implement this method.
  By default just flushes."
  :type)

(defmethod close :default [t]
  (flush t))

(defn- dx
  ([{:keys [type]}] type)
  ([{:keys [type]} _] type)
  ([{:keys [type]} _ _] type)
  ([{:keys [type]} _ _ _] type)
  ([{:keys [type]} _ _ _ _] type))

(defmulti get
  "Fetches a record from a shelf by its spec and ID.

  Shelves must implement this method."
  {:arglists '([conn spec record-id])}
  #'dx)

(defmulti put
  "Enters a record into a shelf by its spec, returning its ID.

  If an ID is provided, the record will be upserted to that ID.

  Shelves must implement this method.
  Shelves are not required to flush after a put."
  {:arglists '([conn spec record]
               [conn spec record-id record])}
  #'dx)

(defmulti enumerate
  "Enumerates all the known specs, or the elements of a spec as UUIDs.

  Shelves must implement this method."
  {:arglists '([conn]
               [conn spec])}
  #'dx)

;; ID tools
;;--------------------------------------------------------------------------------------------------
(defn random-uuid [& _]
  (UUID/randomUUID))

(defn texts->sha-uuid
  "Accepts a number of texts (strings), and computes a UUID by
  truncating their hash to a UUID's 128bi.

  Internally uses the SHA-256sum algorithm."
  [& texts]
  (let [digester (MessageDigest/getInstance "SHA-256")]
    (locking digester
      (.reset digester)
      (doseq [text texts]
        (.update digester (.getBytes (str text) "UTF-8")))
      (let [arr (.digest digester)]
        (UUID.
         (.getLong
          (doto (ByteBuffer/allocate (inc Long/BYTES))
            (.put arr 0 8)
            (.flip)))
         (.getLong
          (doto (ByteBuffer/allocate (inc Long/BYTES))
            (.put arr 8 8)
            (.flip))))))))

;; Intentional interface for schemas
;;--------------------------------------------------------------------------------------------------
(def empty-schema
  "The empty Shelving schema.

  Should be used as the basis for all user-defined schemas."
  {:type  ::schema
   :specs {}})

(defn shelf-spec
  "Enters a spec type into a schema, returning a new schema.

  Data to be persisted must have a \"primary key\", being an element
  which is considered to uniquely name a record. Multiple writes with
  a single primary key will collide and the second one wins."
  [schema spec id-fn & {:as opts}]
  {:pre [(keyword? spec)
         (ifn? id-fn)]}
  (when (not-empty opts)
    (binding [*out* *err*]
      (println "Warning: shelf-schema got ignored opts" opts)))
  (assoc-in schema [:specs spec]
            {:type  ::spec
             :id-fn id-fn}))

(defn has-spec?
  "Helper used for preconditions."
  [schema spec]
  (boolean (some-> schema :specs spec)))

(defn id-for-record
  "Function of a record which computes its ID."
  [schema spec record]
  {:pre [(has-spec? schema spec)]}
  (let [key-fn (-> schema
                   :specs
                   (clojure.core/get spec)
                   (clojure.core/get :id-fn random-uuid))] 
    (key-fn record)))

(defn schema->specs
  "Helper used for converting a schema record to a set of specs for storage."
  [schema]
  (-> schema :specs keys set))
