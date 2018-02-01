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
           [java.nio ByteBuffer])
  (:require [clojure.walk :refer [postwalk]]
            [clojure.spec.alpha :as s]))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(defmulti open
  "Opens a shelf for reading or writing.

  Shelves must implement this method."
  {:categories #{::basic}
   :arglists   '([config])}
  :type)

(defmulti flush
  "Flushes (commits) an open shelf.

  Shelves must implement this method."
  {:categories #{::basic}
   :arglists   '([conn])}
  :type)

(defmulti close
  "Closes an open shelf.

  Shelves may implement this method.

  By default just flushes."
  {:categories #{::basic}
   :arglists   '([conn])}
  :type)

(defmethod close :default [t]
  (flush t))

(defn- dx
  ([{:keys [type]}] type)
  ([{:keys [type]} _] type)
  ([{:keys [type]} _ _] type)
  ([{:keys [type]} _ _ _] type)
  ([{:keys [type]} _ _ _ _] type)
  ([{:keys [type]} _ _ _ _ _] type))

(defmulti get
  "Fetches a record from a shelf by its spec and ID.

  Shelves must implement this method."
  {:categories #{::basic}
   :arglists   '([conn spec record-id])}
  #'dx)

(defmulti put
  "Enters a record into a shelf according to its spec in the schema,
  inserting substructures and updating all relevant rel(ation)s.

  For shelves storing \"records\" not \"values\", the `id` parameter
  may be used to either control the ID of the record, say for
  achieving an upsert.

  It is an error to specify the ID when inserting into a \"value\" shelf.

  Shelves must implement this method."
  {:categories #{::basic}
   :arglists   '([conn spec val]
                 [conn spec id val])}
  #'dx)

(defmulti enumerate-specs
  "Enumerates all the known specs.

  Shelves must implement this method."
  {:categories #{::schema}
   :arglists   '([conn])}
  #'dx)

(defmulti enumerate-spec
  "Enumerates all the known records of a spec by UUID.

  Shelves must implement this method."
  {:categories #{::schema}
   :arglists   '([conn spec])}
  #'dx)

;; ID tools
;;--------------------------------------------------------------------------------------------------
(defn random-uuid
  "Returns a random UUID."
  {:categories #{::util}}
  [_]
  (UUID/randomUUID))

(defn digest->uuid
  "Takes the first 16b of a `byte[]` as a 1228bi UUID.

  An `IndexOutOfBoundsException` will probably be thrown if the
  `byte[]` is too small."
  {:categories #{::util}}
  [digest]
  (let [buff (ByteBuffer/wrap digest)]
    (UUID.
     (.getLong buff 0)
     (.getLong buff 1))))

(defn content-hash
  "A generic Clojure content hash based on using
  `#'clojure.walk/postwalk` to accumulate hash values from
  substructures.

  For general Objects, takes the `java.lang.Object/hashCode` for each
  object in postwalk's order. Strings however are fully digested as
  UTF-8 bytes.

  The precise hash used may be configured, but must be of at least
  128bi in length as the first 128bi are converted to a UUID, which is
  returned."
  {:categories #{::util}}
  ([val]
   (content-hash "SHA-256" val))
  ([digest-name val]
   (let [digester  (MessageDigest/getInstance digest-name)
         buff      (ByteBuffer/allocate (inc Integer/BYTES))
         add-hash! (fn [o]
                     (when o
                       (cond (string? o)
                             (.update digester (.getBytes o "UTF-8"))
                             :else
                             (do (.putInt buff 0 (hash o))
                                 (.update digester (.array buff))))
                       nil))]
     (postwalk (fn [o] (add-hash! o) o) val)
     (digest->uuid (.digest digester)))))

;; Intentional interface for schemas
;;--------------------------------------------------------------------------------------------------
(def ^{:doc "The empty Shelving schema.

  Should be used as the basis for all user-defined schemas."
       :categories #{::schema}}
  empty-schema
  {:type  ::schema
   :specs {}})

(defn ^:private shelf-spec*
  "Implementation detail of record-spec and value-spec."
  [schema spec record? id-fn]
  (update-in schema [:specs spec]
             merge {:type    ::spec
                    :record? record?
                    :id-fn   id-fn
                    :rels    #{}}))

(defn has-spec?
  "Helper used for preconditions."
  {:categories #{::schema}}
  [schema spec]
  (boolean (some-> schema :specs spec)))

(defn is-value?
  "True if and only if the spec exists and is a `:value` spec."
  {:categories #{::schema}}
  [schema spec]
  (boolean (some-> schema :specs spec :record? not)))

(defn value-spec
  "Enters a new \"value\" spec into a schema, returning a new schema.

  Values are addressed by a content hash derived ID, are unique and
  cannot be deleted or updated.

  Values may be related to other values via schema
  rel(ation)s. Records may relate to values, but values cannot relate
  to records except through reverse lookup on a record to value
  relation."
  {:categories #{::schema}}
  [schema spec & {:as opts}]
  {:pre  [(keyword? spec)
          (namespace spec)
          (name spec)
          (not (has-spec? schema spec))]
   :post [(has-spec? % spec)
          (is-value? % spec)]}
  (when (not-empty opts)
    (binding [*out* *err*]
      (println "Warning: value-spec got ignored opts" opts)))
  (shelf-spec* schema spec false content-hash))

(defn is-record?
  "True if and only if the spec exists and is a record spec."
  {:categories #{::schema}}
  [schema spec]
  (boolean (some-> schema :specs spec :record?)))

(defn record-spec
  "Enters a new \"record\" spec into a schema, returning a new schema.

  Records have traditional place semantics and are identified by
  randomly generated IDs, rather than the structural semantics
  ascribed to values."
  {:categories #{::schema}}
  [schema spec & {:as opts}]
  {:pre  [(keyword? spec)
          (namespace spec)
          (name spec)
          (not (has-spec? schema spec))]
   :post [(has-spec? % spec)
          (is-record? % spec)]}
  (when (not-empty opts)
    (binding [*out* *err*]
      (println "Warning: record-spec got ignored opts" opts)))
  (shelf-spec* schema spec true random-uuid))

(defn id-for-val
  "Returns the `val`'s identifying UUID according to the spec's schema entry."
  {:categories #{::schema}}
  [schema spec val]
  {:pre [(has-spec? schema spec)]}
  (let [key-fn (-> schema
                   :specs
                   (clojure.core/get spec)
                   (clojure.core/get :id-fn random-uuid))]
    (key-fn val)))

(defn schema->specs
  "Helper used for converting a schema record to a set of specs for storage."
  {:categories #{::schema}}
  [schema]
  (-> schema :specs keys set))

;; Relations
;;--------------------------------------------------------------------------------------------------
(defn spec-rel
  "Enters a rel(ation) into a schema, returning a new schema which will
  maintain that rel.

  rels are identified uniquely by a pair of specs, and the relation is
  determined by the to-fn which projects instances of the `from-spec`
  to instances of the `to-spec`. `to-fn` MUST be a pure function.

  When inserting with indices, all `to-spec` instances will be
  inserted into their corresponding tables.

  At insertion time, the `to-spec` must exist as a supported shelf.

  The `to-spec` MAY NEVER name a \"record\" shelf. `to-spec` must be a
  \"value\" shelf."
  {:categories #{::rel}}
  [schema [from-spec to-spec :as rel-id] to-fn]
  (-> schema
      (assoc-in [:rels rel-id]
                {:type  ::rel
                 :to-fn to-fn})
      (assoc-in [:rels [to-spec from-spec]]
                {:type  ::alias
                 :to rel-id})
      (update-in [:specs from-spec :rels]
                 (fnil conj #{}) rel-id)
      (update-in [:specs to-spec :rels]
                 (fnil conj #{}) rel-id)))

(defmulti enumerate-rels
  "Enumerates all the known rels by ID (their `[from-spec to-spec]` pair).

  Shelves must implement this method."
  {:categories #{::rel}
   :arglists   '([conn])}
  #'dx)

(defmulti enumerate-rel
  "Enumerates the `(from-id to-id)` pairs of the given rel(ation).

  Shelves must implement this method."
  {:categories #{::rel}
   :arglists   '([conn rel-id])}
  #'dx)

(defmulti relate-by-id
  "Given a rel(ation) and the ID of an record of the from-rel spec,
  return a seq of the IDs of records it relates to.

  By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full rel scan.

  Shelves may provide more efficient implementations of this method."
  {:categories #{::rel}
   :arglists   '([conn rel-id spec id])
   :unstable   true}
  #'dx)

(defmethod relate-by-id :default [conn rel-id id]
  (let [[filter-fn map-fn] (comp (if (= spec (first rel-id))
                                   [first second]
                                   [second first])
                                 #{id})]
    (->> (enumerate-rel conn rel-id)
         (filter filter-fn)
         (map map-fn))))

(defmulti relate-by-value
  "Given a rel(ation) and a value conforming to the left side of the relation,
  return a seq of the IDs of records on the right side of the
  relation (if any) it relates to.

  By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full rel scan.

  Shelves may provide more efficient implementations of this method."
  {:categories #{::rel}
   :arglists   '([conn rel-id spec id])
   :unstable   true}
  #'dx)

(defn schema->rels
  "Helper used for converting a schema record to a set of rel(ation)s for storage."
  {:categories #{::rel}}
  [schema]
  (-> schema :rels keys set))

(defn check-schema
  "Helper used for validating that a schema, being a collection of specs
  and rel(ation)s, is legal with respect to the various restrictions
  imposed by the current version of shelving.

  Enforces that Value specs cannot rel to Record specs. Enforces that
  all specs for all defined rels exist. Returns a sequence of errors,
  or nil if validation is successful."
  {:categories #{:schema}}
  [{:keys [specs rels] :as schema}]
  (-> (concat (mapcat (fn [[spec-id {spec-is-record? :record? spec-rels :rels}]]
                        (when-not spec-is-record?
                          (keep (fn [[from-spec to-spec]]
                                  (when (and (= from-spec spec-id)
                                             (is-record? schema to-spec))
                                    (format "Illegal relation - cannot relate value spec %s to record spec %s"
                                            from-spec to-spec)))
                                spec-rels)))
                      specs)
              (-> (mapcat (fn [specs]
                            (keep #(when-not (has-spec? schema %)
                                     (format "Illegal relation %s - unknown spec %s" specs %))
                                  specs))
                          (keys rels))
                  set seq))
      seq))
