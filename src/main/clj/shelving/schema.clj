(ns shelving.schema
  "The implementation of Shelving's schema system.

  Shelving stores are structured stores of values, described by
  `clojure.spec(.alpha) predicates in tables named by the spec.

  In order to consistency check Shelving stores across the persistence
  boundary, the set of specs and relations used in a given store are
  reified into the \"schema\" for the store."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [hasch.core :refer [uuid]]
            [shelving.spec :as ss]
            [shelving.spec.walk :as s.w])
  (:import java.nio.ByteBuffer
           java.util.UUID
           me.arrdem.UnimplementedOperationException))

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
(def ^{:doc "The empty Shelving schema.

  Should be used as the basis for all user-defined schemas."
       :categories #{::schema}
       :stability  :stability/stable
       :added      "0.0.0"}
  empty-schema
  {:type  ::schema
   :specs {}
   :rels  {}})

(defn- merge-specs
  [l r]
  (cond (or (and (not l) r)
            (and l r (:record? l) (:record? r))
            (and l r (not (:record? l)) (not (:record? r))))
        {:type    ::spec
         :record? (:record? r)
         :id-fn   (:id-fn r)
         :rels    (into (:rels r #{}) (:rels l))}

        :else (throw (ex-info "Illegal state - value/record conflict merging specs!"
                              {:left l :right r}))))

(defn- shelf-spec*
  "Implementation detail of record-spec and value-spec."
  [schema spec record? id-fn]
  (try
    (update-in schema [:specs spec]
               merge-specs {:type    ::spec
                            :record? record?
                            :id-fn   id-fn
                            :rels    #{}})
    (catch clojure.lang.ExceptionInfo e
      (throw (ex-info (format "Error installing spec '%s' to schema!" spec)
                      {:schema  schema
                       :spec    spec
                       :record? record?}
                      e)))))

(defn has-spec?
  "Helper used for preconditions."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec]
  (boolean (some-> schema :specs spec)))

(defn is-value?
  "True if and only if the spec exists and is a `:value` spec."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec]
  (boolean (some-> schema :specs spec :record? not)))

(declare has-rel? spec-rel)

(defn value-spec
  "Enters a new spec and its subspecs into the schema, returning a new
  schema. The given spec and its subspecs are all entered as \"value\"
  specs which have value identity and cannot be updated. Rel(ations)
  between all inserted specs and their subspecs are automatically
  added.

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
    (log/warn "value-spec got ignored opts" opts))
  (reduce #(cond-> %1
             (not (has-spec? %1 %2))       (value-spec %2) 
             (not (has-rel? %1 [spec %2])) (spec-rel [spec %2]))
          (shelf-spec* schema spec false uuid)
          (filter qualified-keyword?
                  (ss/subspec-pred-seq spec))))

(defn is-record?
  "True if and only if the spec exists and is a record spec."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema spec]
  (boolean (some-> schema :specs spec :record?)))

(defn record-spec
  "Enters a new spec and its subspecs spec into a schema,
  returning a new schema. The given spec is inserted as \"record\"
  spec with update-in-place capabilities, its subspecs are inserted as
  \"value\" specs.

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
  (reduce #(shelf-spec* %1 %2 false uuid)
          (shelf-spec* schema spec true random-uuid)
          (rest (ss/spec-seq spec))))

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

(defn schema->specs
  "Helper used for converting a schema record to a fully resolved spec serialization for storage."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema]
  (->> (map (juxt identity
                  #(assoc (select-keys (get-in schema [:specs %])
                                       [:record?])
                          :def (s/describe* (s/get-spec %))))
            (apply ss/spec-seq (-> schema :specs keys)))
       (into {})))

(defn check-specs!
  "Helper for use in checking compatibility between database persistable specs.
  Because proving equivalence on specs is basically impossible, we'll start with equality."
  {:categories #{::schema}
   :stability  :stability/stable
   :added      "0.0.0"}
  [persisted live]
  (doseq [s     (keys live)
          :let  [ls (clojure.core/get live s)
                 ps (clojure.core/get persisted s)]
          :when persisted]
    (when-not (= (:record? ls) (:record? ps))
      (throw (ex-info "Incompatible schemas - `:record?` differs!"
                      {:live      ls
                       :persisted ps})))

    ;; FIXME (arrdem 2018-02-19):
    ;;   This could make some attempt to be more general.
    (when-not (= (:def ls) (:def ps))
      (throw (ex-info "Incompatible schemas - `:def` differs!"
                      {:live      ls
                       :persisted ps})))))

;; Relations
;;--------------------------------------------------------------------------------------------------
;; Relations (and aliases) have IDs
(defn spec-rel*
  "Implementation detail of `#'spec-rel` which allows for the creation
  of arbitrary relations. Almost certainly isn't what you want."
  {:categories #{::rel}
   :stability  :stability/unstable
   :added      "0.0.0"}
  [schema [from-spec to-spec :as rel-id]]
  (-> schema
      (update :rels merge {[from-spec to-spec] {:type ::rel}
                           [to-spec from-spec] {:type ::alias :to rel-id}})
      (update :specs #(-> %
                          (update-in [from-spec :rels] (fnil conj #{}) rel-id)
                          (update-in [to-spec :rels] (fnil conj #{}) rel-id)))))

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
  [schema [from-spec to-spec :as rel-id]]
  {:pre [(s/get-spec from-spec)
         (s/get-spec to-spec)
         (contains? (set (ss/subspec-pred-seq from-spec)) to-spec)]}
  (spec-rel* schema rel-id))

(defn has-rel?
  "True if and only if both specs in the named rel, and the named rel exist in the schema."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema [from-spec to-spec :as rel-id]]
  (and (has-spec? schema from-spec)
       (has-spec? schema to-spec)
       (-> schema :rels (contains? rel-id))))

(defn is-alias?
  "True if and only if the schema has the relation (`#'has-rel?`) and the relation is an alias."
  {:categories #{::rel}
   :stability  :stability/stable
   :added      "0.0.0"}
  [schema rel-id]
  (and (has-rel? schema rel-id)
       (= ::alias (-> schema :rels (clojure.core/get rel-id) :type))))

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
  (-> (concat
       (mapcat (fn [[spec-id {spec-is-record? :record? spec-rels :rels}]]
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

(defn decompose
  "Given a schema, a spec in the schema, a record ID and the record as a
  value, decompose the record into its direct descendants and relations
  thereto."
  [schema spec id val]
  (let [acc! (volatile! [])]
    (binding [s.w/*walk-through-aliases* false]
      (s.w/postwalk-with-spec
       (fn [subspec subval]
         (if (and (qualified-keyword? subspec)
                  (not= spec subspec))
           (let [id* (id-for-record schema subspec subval)]
             (vswap! acc! conj [:record subspec id* subval])
             (vswap! acc! conj [:rel [spec subspec] id id*])
             id*)
           subval))
       spec val)
    @acc!)))
