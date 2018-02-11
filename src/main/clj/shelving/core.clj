(ns shelving.core
  "Shelving; Noun;

  Collective form of Shelf; a thin slab of wood, metal, etc., fixed
  horizontally to a wall or in a frame, for supporting objects.

  Shelves are a structured store intended for use with linear scans
  for IDs and random point read/write. Shelves are appropriate only
  for use when the read and write load is so small that flushing the
  entire store every time is an acceptable cost compared to the
  complexity of a more traditional storage layer or database."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:refer-clojure :exclude [flush get])
  (:require [clojure.spec.alpha :as s]
            [hasch.core :refer [uuid]])
  (:import java.nio.ByteBuffer
           java.util.UUID
           me.arrdem.UnimplementedOperationException))

(defn- dx
  ([{:keys [type]}] type)
  ([{:keys [type]} _] type)
  ([{:keys [type]} _ _] type)
  ([{:keys [type]} _ _ _] type)
  ([{:keys [type]} _ _ _ _] type)
  ([{:keys [type]} _ _ _ _ _] type))

(defmacro ^:private required! [method]
  `(defmethod ~method :default [~'& args#]
     (throw
      (UnimplementedOperationException.
       (format "Shelves must implement method %s, no implementation for dispatch tag %s"
               (pr-str (var ~method)) (apply dx args#))))))

;; Intentional interface for shelves
;;--------------------------------------------------------------------------------------------------
(defmulti open
  "Opens a shelf for reading or writing.

  Shelves must implement this method."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([config])}
  #'dx)

(required! open)

(defmulti flush
  "Flushes (commits) an open shelf.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(required! flush)

(defmulti close
  "Closes an open shelf.

  Shelves may implement this method.

  By default just flushes."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(defmethod close :default [t]
  (flush t))

(defmulti get
  "Fetches a record from a shelf by its spec and ID.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec record-id])}
  #'dx)

(defmulti put
  "Enters a record into a shelf according to its spec in the schema,
  inserting substructures and updating all relevant rel(ation)s.

  For shelves storing \"records\" not \"values\", the `id` parameter
  may be used to either control the ID of the record, say for
  achieving an upsert.

  It is an error to specify the ID when inserting into a \"value\" shelf.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec val]
                 [conn spec id val])}
  #'dx)

(required! put)

(defmulti schema
  "Returns the schema record for a given connection.

  Schemas are fixed when the connection is opened.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(required! schema)

(defmulti enumerate-specs
  "Enumerates all the known specs.

  Shelves may provide alternate implementations of this method."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(defmethod enumerate-specs :default [conn]
  (-> conn schema :specs keys))

(defmulti enumerate-spec
  "Enumerates all the known records of a spec by UUID.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec])}
  #'dx)

(required! enumerate-spec)

(defmulti count-spec
  "Returns at least an upper bound on the cardinality of a given spec.

  Implementations of this method should be near constant time and
  should not require realizing the relation in question.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::query}
   :stability  :stability/unstable
   :added      "0.0.1"
   :arglists   '([conn spec])}
  #'dx)

(required! count-spec)

;; ID tools
;;--------------------------------------------------------------------------------------------------
(defn random-uuid
  "Returns a random UUID."
  {:categories #{::util}
   :added      "0.0.0"
   :stability  :stability/stable}
  [_]
  (UUID/randomUUID))

(defn digest->uuid
  "Takes the first 16b of a `byte[]` as a 1228bi UUID.

  An `IndexOutOfBoundsException` will probably be thrown if the
  `byte[]` is too small."
  {:categories #{::util}
   :added      "0.0.0"
   :stability  :stability/stable}
  [digest]
  (let [buff (ByteBuffer/wrap digest)]
    (UUID.
     (.getLong buff 0)
     (.getLong buff 1))))

;; Intentional interface for schemas
;;--------------------------------------------------------------------------------------------------

;; Specs
(s/def ::spec-id
  qualified-keyword?)

(s/def :shelving.core.spec/type
  #{::spec})

(s/def :shelving.core.spec/record?
  boolean?)

(s/def :shelving.core.spec/id-fn
  ifn?)

(s/def :shelving.core.spec/rels
  (s/coll-of #(s/valid? ::rel-id %)))

(s/def :shelving.core/spec
  (s/keys :req-un [:shelving.core.spec/type
                   :shelving.core.spec/record?
                   :shelving.core.spec/id-fn
                   :shelving.core.spec/rels]))

(s/def :shelving.core.schema/type #{::schema})

(s/def :shelving.core.schema/specs
  (s/map-of #(s/valid? ::spec-id %) #(s/valid? ::spec %)))

(s/def :shelving.core.schema/rels
  (s/map-of #(s/valid? ::rel-id %) #(s/valid? ::rel+alias %)))

(s/def ::schema
  (s/keys :req-un [:shelving.core.schema/type
                   :shelving.core.schema/specs
                   :shelving.core.schema/rels]))

(def ^{:doc "The empty Shelving schema.

  Should be used as the basis for all user-defined schemas."
       :categories #{::schema}
       :stability  :stability/stable
       :added      "0.0.0"}
  empty-schema
  {:type  ::schema
   :specs {}
   :rels  {}})

(s/fdef shelf-spec*
        :args (s/cat :schema ::schema
                     :spec ::spec
                     :record? boolean?
                     :id-fn :shelving.core.spec/id-fn)
        :ret ::schema)

(defn ^:private shelf-spec*
  "Implementation detail of record-spec and value-spec."
  [schema spec record? id-fn]
  (update-in schema [:specs spec]
             merge {:type    ::spec
                    :record? record?
                    :id-fn   id-fn
                    :rels    #{}}))

(s/fdef has-spec?
        :args (s/cat :schema ::schema
                     :spec   ::spec)
        :ret boolean?)

(defn has-spec?
  "Helper used for preconditions."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec]
  (boolean (some-> schema :specs spec)))

(s/fdef is-value?
        :args (s/cat :schema ::schema
                     :spec   ::spec)
        :ret boolean?)

(defn is-value?
  "True if and only if the spec exists and is a `:value` spec."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec]
  (boolean (some-> schema :specs spec :record? not)))

(s/fdef value-spec
        :args (s/cat :schema ::schema
                     :spec   ::spec)
        :ret ::schema)

(defn value-spec
  "Enters a new \"value\" spec into a schema, returning a new schema.

  Values are addressed by a content hash derived ID, are unique and
  cannot be deleted or updated.

  Values may be related to other values via schema
  rel(ation)s. Records may relate to values, but values cannot relate
  to records except through reverse lookup on a record to value
  relation."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
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
  (shelf-spec* schema spec false uuid))

(s/fdef is-record?
        :args (s/cat :schema ::schema
                     :spec   ::spec)
        :ret boolean?)

(defn is-record?
  "True if and only if the spec exists and is a record spec."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec]
  (boolean (some-> schema :specs spec :record?)))

(s/fdef record-spec
        :args (s/cat :schema ::schema
                     :spec   ::spec)
        :ret ::schema)

(defn record-spec
  "Enters a new \"record\" spec into a schema, returning a new schema.

  Records have traditional place semantics and are identified by
  randomly generated IDs, rather than the structural semantics
  ascribed to values."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
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

(s/fdef id-for-record
        :args (s/cat :schema ::schema
                     :spec   ::spec
                     :val    some)
        :ret uuid?)

(defn id-for-record
  "Returns the `val`'s identifying UUID according to the spec's schema entry."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec val]
  {:pre [(has-spec? schema spec)]}
  (let [key-fn (-> schema
                   :specs
                   (clojure.core/get spec)
                   (clojure.core/get :id-fn random-uuid))]
    (key-fn val)))

(s/fdef schema->specs
        :args (s/cat :schema ::schema)
        :ret (s/coll-of #(s/valid? ::spec %) :kind set?))

(defn schema->specs
  "Helper used for converting a schema record to a set of specs for storage."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema]
  (-> schema :specs keys set))

;; Relations
;;--------------------------------------------------------------------------------------------------
;; Relations (and aliases) have IDs
(s/def ::rel-id
  (s/tuple ::spec-id ::spec-id))

(s/def :shelving.core.rel/type
  #{::rel})

(s/def :shelving.core.rel/to-fn
  ifn?)

(s/def ::rel
  (s/keys :req-un [:shelving.core.rel/type
                   :shelving.core.rel/to-fn]))

;; Relations may have aliases
(s/def :shelving.core.alias/type
  #{::alias})

(s/def :shelving.core.alias/to
  ::rel-id)

(s/def ::alias
  (s/keys :req-un [:shelving.core.alias/type
                   :shelving.core.alias/to]))

;; Really when we way rel we mean either a rel or an alias
(s/def ::rel+alias
  (s/or :rel ::rel :alias ::alias))

(s/fdef spec-rel
        :args (s/cat :schema ::schema
                     :rel-id ::rel-id
                     :to-fn ifn?)
        :ret ::schema)

(defn spec-rel
  "Enters a rel(ation) into a schema, returning a new schema which will
  maintain that rel.

  rels are identified uniquely by a pair of specs, stating that there
  exists a unidirectional relation from values conforming to
  `from-spec` to values conforming to `to-spec`. This relation has an
  inverse, which maps values of `to-spec` back to the values of
  `from-spec` which projected to them.

  The rel is determined by the `to-fn` which projects values
  conforming to the `from-spec` to values conforming to
  `to-spec`. `to-fn` MUST be a pure function.

  When inserting with indices, all `to-spec` instances will be
  inserted into their corresponding tables.

  At insertion time, the `to-spec` must exist as a supported shelf.

  The `to-spec` MAY NEVER name a \"record\" shelf. `to-spec` must be a
  \"value\" shelf."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema [from-spec to-spec :as rel-id] to-fn]
  (-> schema
      (assoc-in [:rels rel-id]
                {:type  ::rel
                 :to-fn to-fn})
      (assoc-in [:rels [to-spec from-spec]]
                {:type ::alias
                 :to   rel-id})
      (update-in [:specs from-spec :rels]
                 (fnil conj #{}) rel-id)
      (update-in [:specs to-spec :rels]
                 (fnil conj #{}) rel-id)))

(s/fdef has-rel?
        :args (s/cat :schema ::schema
                     :rel-id ::rel-id
                     :to-fn ifn?)
        :ret boolean?)

(defn has-rel?
  "True if and only if both specs in the named rel, and the named rel exist in the schema."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema [from-spec to-spec :as rel-id]]
  (and (has-spec? schema from-spec)
       (has-spec? schema to-spec)
       (-> schema :rels (contains? rel-id))))

(s/fdef is-alias?
        :args (s/cat :schema ::schema
                     :rel-id ::rel-id
                     :to-fn ifn?)
        :ret boolean?)

(defn is-alias?
  "True if and only if the schema has the relation (`#'has-rel?`) and the relation is an alias."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema rel-id]
  (and (has-rel? schema rel-id)
       (= ::alias (-> schema :rels (clojure.core/get rel-id) :type))))

(s/fdef resolve-alias
        :args (s/cat :schema ::schema
                     :rel-id ::rel-id
                     :to-fn ifn?)
        :ret boolean?)

(defn resolve-alias
  "When the schema has a rel (`#'has-rel?`) and it is an
  alias (`#'is-alias?`) resolve it, returning the directed rel it
  aliases. Otherwise return the rel."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema rel-id]
  (if (is-alias? schema rel-id)
    (recur schema (-> schema :rels (clojure.core/get rel-id) :to))
    rel-id))

(defmulti enumerate-rels
  "Enumerates all the known rels by ID (their `[from-spec to-spec]` pair). Includes aliases.

  Shelves may provide alternate implementation of this method."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(defmethod enumerate-rels :default [conn]
  (-> conn schema :rels keys))

(defmulti enumerate-rel
  "Enumerates the `(from-id to-id)` pairs of the given rel(ation).

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn rel-id])}
  #'dx)

(required! enumerate-rel)

(defmulti count-rel
  "Returns at least an upper bound on the cardinality of a given relation.

  Implementations of this method should be near constant time and
  should not require realizing the relation in question.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::query}
   :stability  :stability/unstable
   :added      "0.0.1"
   :arglists   '([conn rel-id])}
  #'dx)

(required! count-rel)

(defmulti relate-by-id
  "Given a rel(ation) and the ID of an record of the from-rel spec,
  return a seq of the IDs of records it relates to. If the given ID
  does not exist on the left side of the given relation, an empty seq
  must be produced.

  If the given ID does not exist on the left side of the given
  relation, an empty seq must be produced.

  Note that if the rel `[a b]` was created with `#'spec-rel`, the rel
  `[b a]` also exists and is the complement of mapping from `a`s to
  `b`s defined by `[a b]`.

  By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full scan
  of the pairs constituting this relation.

  Shelves may provide more efficient implementations of this method."
  {:categories #{::rel}
   :stability  :stability/unstable
   :added      "0.0.0"
   :arglists   '([conn rel-id spec id])}
  #'dx)

(defmethod relate-by-id :default [conn [from-spec to-spec :as rel-id] id]
  (let [real-rel-id (resolve-alias (schema conn) rel-id)
        f           (if (= rel-id real-rel-id)
                      #(when (= id (first %)) (second %))
                      #(when (= id (second %)) (first %)))]
    (keep f (enumerate-rel conn real-rel-id))))

(defmulti relate-by-value
  "Given a rel(ation) and a value conforming to the left side of the relation,
  return a seq of the IDs of records on the right side of the
  relation (if any) it relates to.

  By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full rel scan.

  Shelves may provide more efficient implementations of this method."
  {:categories #{::rel}
   :stability  :stability/unstable
   :added      "0.0.0"
   :arglists   '([conn rel-id spec id])}
  #'dx)

(defmethod relate-by-value :default [conn [from-spec to-spec :as rel-id] val]
  {:pre [(is-value? (schema conn) from-spec)]}
  (relate-by-id conn rel-id (id-for-record (schema conn) val)))

(defn check-schema
  "Helper used for validating that a schema, being a collection of specs
  and rel(ation)s, is legal with respect to the various restrictions
  imposed by the current version of shelving.

  Enforces that Value specs cannot rel to Record specs. Enforces that
  all specs for all defined rels exist. Returns a sequence of errors,
  or nil if validation is successful."
  {:categories #{:schema}
   :stability  :stability/stable
   :added      "0.0.0"}
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
