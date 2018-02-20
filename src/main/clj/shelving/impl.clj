(ns shelving.impl
  "Implementer API for Shelving stores.

  Store consumers should prefer the API provided by `shelving.core`."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:refer-clojure :exclude [flush get])
  (:require [shelving.schema :as schema])
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

  Returns the record if it exists, otherwise returning the
  user-provided `not-found` value, taken to be `nil` by default.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec record-id]
                 [conn spec record-id not-found])}
  #'dx)

(defmulti has?
  "Indicates whether a shelf has a record of a spec.

  Returns `true` if and only if the shelf contains a record if the
  given spec and ID.  Otherwise must return `false`.

  Implementations may provide alternate implementations of this
  method."
  {:categories #{::basic}
   :stability  :stability/stable
   :added      "0.0.0"
   :arglists   '([conn spec record-id])}
  #'dx)

(defmethod has? :default [conn spec record-id]
  (let [not-found (Object.)]
    (not= not-found (get conn spec record-id not-found))))

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
  "Returns an upper bound on the cardinality of a given spec.

  The bound should be as tight as possible if not
  precise. Implementations of this method should be near constant time
  and should not require realizing the spec in question.

  Shelves must implement this method.

  By default throws `me.arrdem.UnimplementedOperationException`."
  {:categories #{::query}
   :stability  :stability/unstable
   :added      "0.0.1"
   :arglists   '([conn spec])}
  #'dx)

(required! count-spec)

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
  "Returns an upper bound on the cardinality of a given relation.

  The bound should be as tight as possible if not
  precise. Implementations of this method should be near constant time
  and should not require realizing the rel in question.

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
  (let [real-rel-id (schema/resolve-alias (schema conn) rel-id)
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
  (relate-by-id conn rel-id (schema/id-for-record (schema conn) val)))
