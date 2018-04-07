(ns shelving.impl
  "Implementer API for Shelving stores.

  Store consumers should prefer the API provided by `shelving.core`."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:refer-clojure :exclude [flush get])
  (:require [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [hasch.core :refer [uuid]]
            [shelving.schema :as schema]
            [shelving.specs.core :as specs])
  (:import me.arrdem.UnimplementedOperationException))

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
(s/fdef open
  :args (s/cat :config ::specs/config)
  :ret ::specs/conn)

(defmulti open
  "Opens a shelf for reading or writing.

  Shelves must implement this method."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([config])}
  #'dx)

(required! open)

(s/fdef flush
  :args (s/cat :conn ::specs/conn)
  :ret  nil?)

(defmulti flush
  "Flushes (commits) an open shelf. Returns `nil`.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(required! flush)

(s/fdef close
  :arsg (s/cat :conn ::specs/conn)
  :ret nil?)

(defmulti close
  "Closes an open shelf. Returns `nil`.

  Shelves may implement this method.

  By default just flushes."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(defmethod close :default [t]
  (flush t))

(s/fdef put-spec
  :args (s/cat :conn ::specs/conn
               :spec ::specs/spec-id
               :id ::specs/some-id
               :val  any?))

(defmulti put-spec
  "The \"raw\" put operation on values. Inserts a fully decomposed value (tuple) into the designated
  spec, returning the ID at which it was inserted if an ID was not provided.

  Users should universally prefer `#'shelving.core/put-spec`. This method is an unprotected
  implementation detail not for general use.

  Note that when inserting into \"record\" specs, all relations to the updated \"record\" ID must be
  invalidated.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec id val])}
  #'dx)

(s/fdef get-spec
  :args (s/cat :conn ::specs/conn
               :spec ::specs/spec-id
               :id ::specs/some-id
               :not-found (s/? any?)))

(defmulti get-spec
  "Fetches a single tuple, being part of a record, from a shelf by its spec and ID.

  Returns the record if it exists.
  Otherwise returns the `not-found` value, taken to be `nil` by default.

  Implementation detail of `#'shelving.core/get-spec`, which should be preferred by users. This
  method is an unprotected implementation detail not for general use.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec record-id]
                 [conn spec record-id not-found])}
  #'dx)

(s/fdef has?
  :args (s/cat :conn ::specs/conn
               :spec ::specs/spec-id
               :record-id ::specs/some-id)
  :ret boolean?)

(defmulti has?
  "Indicates whether a shelf has a record of a spec.

  Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise
  must return `false`.

  Implementations may provide alternate implementations of this method."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec record-id])}
  #'dx)

(defmethod has? :default [conn spec record-id]
  (let [not-found (Object.)]
    (not= not-found (get-spec conn spec record-id not-found))))

(required! put-spec)

(s/fdef put-rel
   :ars (s/cat :conn ::specs/conn
               :spec ::specs/rel-id
               :from-id ::specs/some-id
               :to-id ::specs/some-id))

(defmulti put-rel
  "The \"raw\" put operation on relations.

  Inserts a `[from rel to]` triple into the data store unconditionally.

  Returns `nil`, throwing if it fails.

  Users should universally prefer `#'shelving.core/put-spec`. This method is an unprotected
  implementation detail not for general use.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec rel-id from-id to-id])}
  #'dx)

(required! put-rel)

(s/fdef schema
   :ars (s/cat :conn ::specs/conn)
   :ret ::specs/schema)

(defmulti schema
  "Returns the schema record for a given connection.

  Schemas are fixed when the connection is opened.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(required! schema)

(s/fdef schema
   :ars (s/cat :conn ::specs/conn)
   :ret ::specs/schema)

(defmulti set-schema
  "Attempts to alter the live schema of the connection by applying the given transformer function to
  the current schema state with any additional arguments.

  Implementation detail of `#'shelving.core/alter-schema`, which should be universally
  preferred. This method is an unprotected implementation detail not for general use.

  Returns the schema record for a given connection.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn schema])}
  #'dx)

(required! set-schema)

(s/fdef enumerate-specs
  :args (s/cat :conn ::specs/conn)
  :ret (s/coll-of ::specs/spec-id))

(defmulti enumerate-specs
  "Enumerates all the known specs.

  Shelves may provide alternate implementations of this method."
  {:categories #{:shelving.core/impl :shelving.core/schema}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(defmethod enumerate-specs :default [conn]
  (-> conn schema :specs keys))

(s/fdef enumerate-spec
  :args (s/cat :conn ::specs/conn
               :spec ::specs/spec-id)
  :ret (s/coll-of ::specs/record-id))

(defmulti enumerate-spec
  "Enumerates all the known records of a spec by UUID.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/schema}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec])}
  #'dx)

(required! enumerate-spec)

(s/fdef enumerate-spec
  :args (s/cat :conn ::specs/conn
               :spec ::specs/spec-id)
  :ret nat-int?)

(defmulti count-spec
  "Returns an upper bound on the cardinality of a given spec.

  The bound should be as tight as possible if not precise. Implementations of this method should be
  near constant time and should not require realizing the spec in question.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/query}
   :stability  :stability/unstable
   :added      "0.0.1"
   :arglists   '([conn spec])}
  #'dx)

(required! count-spec)

(s/fdef enumerate-rels
  :args (s/cat :conn ::specs/conn)
  :ret (s/coll-of ::specs/rel-id))

(defmulti enumerate-rels
  "Enumerates all the known rels by ID (their `[from-spec to-spec]` pair). Includes aliases.

  Shelves may provide alternate implementation of this method."
  {:categories #{:shelving.core/impl :shelving.core/rel}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn])}
  #'dx)

(defmethod enumerate-rels :default [conn]
  (-> conn schema :rels keys))

(s/fdef enumerate-rel
  :args (s/cat :conn ::specs/conn
               :rel-id ::specs/rel-id)
  :ret (s/coll-of ::specs/rel-tuple))

(defmulti enumerate-rel
  "Enumerates the `(from-id to-id)` pairs of the given rel(ation).

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/rel}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn rel-id])}
  #'dx)

(required! enumerate-rel)

(s/fdef count-rel
  :args (s/cat :conn ::specs/conn
               :rel-id ::specs/rel-id)
  :ret nat-int?)

(defmulti count-rel
  "Returns an upper bound on the cardinality of a given relation.

  The bound should be as tight as possible if not precise. Implementations of this method should be
  near constant time and should not require realizing the rel in question.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{:shelving.core/impl :shelving.core/query}
   :stability  :stability/unstable
   :added      "0.0.1"
   :arglists   '([conn rel-id])}
  #'dx)

(required! count-rel)

(s/fdef get-rel
  :args (s/cat :conn ::specs/conn
               :rel-id ::specs/rel-id
               :spec ::specs/spec-id
               :id ::specs/some-id)
  :ret (s/coll-of ::specs/some-id))

(defmulti get-rel
  "Given a rel(ation) and the ID of an record of the from-rel spec,
  return a seq of the IDs of records it relates to. If the given ID does not exist on the left side
  of the given relation, an empty seq must be produced.

  If the given ID does not exist on the left side of the given relation, an empty seq must be
  produced.

  Note that if the rel `[a b]` was created with `#'shelving.core/spec-rel`, the rel `[b a]` also
  exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

  By default uses `#'shelving.impl/enumerate-rel` to do a full scan of the pairs constituting this
  relation.

  Shelves may provide more efficient implementations of this method."
  {:stability  :stability/unstable
   :added      "0.0.0"
   :arglists   '([conn rel-id spec id])}
  #'dx)

(defmethod get-rel :default [conn [from-spec to-spec :as rel-id] id]
  (let [real-rel-id (schema/resolve-alias (schema conn) rel-id)
        f           (if (= rel-id real-rel-id)
                      #(when (= id (first %)) (second %))
                      #(when (= id (second %)) (first %)))]
    (keep f (enumerate-rel conn real-rel-id))))

(defmulti q
  "Query compilation.

  Given a connection and a query datastructure, return a function of a
  connection and 0 or more positional logic variable bindings per the
  `:in` clause of the compiled query. Query functions return sequences
  of maps from logic variables to values. Each produced map must
  contain all lvars occurring in the query's `:find` clause.

  See the datalog documentation for a full description of the
  supported query form."
  {:categories #{:shelving.core/impl :shelving.core/query}
   :stability :stability/stable
   :added     "0.0.0"
   :arglists  '([conn query])}
  #'dx)

(defmethod q :default [conn query]
  (require 'shelving.query.core)
  ((resolve 'shelving.query.core/q) conn query))

(defmulti fingerprint-query
  "Query caching implementation detail.

  Function of a connection and a query which returns a unique
  identifier for the compilation of that pair."
  {:stability :stability/unstable
   :added     "0.0.0"
   :arglists  '([conn query])}
  #'dx)

(defn- get-multi-dispatch-fingerprint [^clojure.lang.MultiFn multifn args]
  ;; FIXME: doesn't handle :default, or dispatch preference correctly
  (hash (.getMethod multifn (apply (.dispatchFn multifn) args))))

(defmethod fingerprint-query :default [conn query]
  (require 'shelving.query.parser)
  (uuid [(get-multi-dispatch-fingerprint q [conn query])
         (s/conform :shelving.query.parser/datalog query)]))
