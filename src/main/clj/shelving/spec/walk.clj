(ns shelving.spec.walk
  "Tools for using `clojure.spec(.alpha)` to walk data structures."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.core.match :refer [match]]
            [shelving.spec :refer [keys-as-map]]))

;; Not a require to avoid a cyclic dependency
(alias 'sh 'shelving.core)

(declare walk-with-spec)

(defmulti walk-with-spec*
  "Implementation detail of walk-with-spec.

  Uses multiple dispatch to handle actually walking the spec tree."
  {:categories #{::sh/walk}
   :stability  :stability/unstable
   :added      "0.0.0"
   :arglists   '([spec-kw spec obj before after])}
  (fn [name spec obj _ _]
    (log/debug name spec (pr-str obj))
    (if (seq? spec)
      (first spec)
      (if (qualified-keyword? spec)
        ::alias
        (if (ifn? spec)
          ::predicate
          :default)))))

(defmethod walk-with-spec* :default [spec unknown obj before after]
  (if (seq? unknown)
    (throw (UnsupportedOperationException.
            (format "Walking '%s' is not supported!"
                    (first seq))))
    (throw (UnsupportedOperationException.
            (format "Could not walk unknown value '%s'" (pr-str unknown))))))

(def ^:dynamic *walk-through-aliases* true)

(defmethod walk-with-spec* ::alias [spec alias obj before after]
  {:pre [(qualified-keyword? alias)]} 
  (as-> obj %
    (before spec %)
    (do (s/assert spec %) %)
    (if *walk-through-aliases* (walk-with-spec before after (some-> spec s/get-spec s/describe*) %)
        %)
    (after spec %)))

(defmethod walk-with-spec* ::predicate [spec pred obj before after]
  (as-> obj %
    (before spec %)
    (do (s/assert spec %) %)
    (after spec %)))

(defmethod walk-with-spec* `s/multi-spec [spec [_ mm k] obj before after]
  (as-> obj %
    (before spec %)
    (do (s/assert spec %) %) 
    (let [spec* ((find-var mm) %)]
      (if *walk-through-aliases* (walk-with-spec before after spec* %) %))
    (after spec %)))

(defmethod walk-with-spec* `s/keys [spec keys-form obj before after]
  (as-> obj %
    (before spec %)
    (do (s/assert spec %) %)
    (let [spec-map (keys-as-map keys-form)]
      (->> (for [[k v] %
                 :let  [spec (get spec-map k)]
                 :when spec
                 :let  [v' (walk-with-spec before after spec v)]
                 :when v']
             [k v'])
           (into {})))
    (after spec %)))

(defmethod walk-with-spec* `s/nilable [spec [_ subspec] obj before after]
  (as-> obj %
    (before spec %)
    (do (s/assert spec %) %)
    (if %
      (walk-with-spec before after subspec %)
      %)
    (after spec %)))

(defmethod walk-with-spec* `s/or [spec [_ & tag-specs] obj before after]
  (loop [[subspec & subspecs* :as li] (map second (partition 2 tag-specs))]
    (when li
      (if (s/valid? subspec obj)
        (as-> obj %
          (before spec %)
          (walk-with-spec before after subspec %)
          (after spec %))
        (recur subspecs*)))))

(defmethod walk-with-spec* `s/and [spec [_ & kw-or-preds] obj before after]
  (as-> obj %
    (before spec %)
    (reduce (fn [o spec] (walk-with-spec before after spec o)) % kw-or-preds)
    (after spec %)))

(defmethod walk-with-spec* `s/merge [spec [_ & kw-or-preds] obj before after]
  (as-> obj %
    (before spec %)
    (reduce (fn [o spec] (walk-with-spec before after spec o)) % kw-or-preds)
    (after spec %)))

(defmethod walk-with-spec* `s/every [spec [_ subspec & {:as opts}] obj before after]
  (as-> obj %
    (before spec %)
    (map (partial walk-with-spec before after subspec) %)
    (into (empty obj) %)
    (after spec %)))

(defmethod walk-with-spec* `s/coll-of [spec [_ subspec & {:as opts}] obj before after]
  (walk-with-spec* spec [`s/every subspec] obj before after))

(defmethod walk-with-spec* `s/every-kv [spec [_ k-spec v-spec & {:as opts}] obj before after]
  (as-> obj %
    (before spec %)
    (map (fn [[k v]]
           [(walk-with-spec before after k-spec k)
            (walk-with-spec before after v-spec v)])
         %)
    (into (empty obj) %)
    (after spec %)))

(defmethod walk-with-spec* `s/tuple [spec [_ & specs] obj before after]
  (as-> obj %
    (before spec %)
    (mapv (fn [o subspec]
            (walk-with-spec before after subspec o))
          % specs)
    (after spec %)))

(defmethod walk-with-spec* `s/map-of [spec [_ & args] obj before after]
  (walk-with-spec* spec (cons `s/every args) obj before after))

(defn walk-with-spec
  "An extensible postwalk over data via specs. Visits every spec-defined
  substructure of the given spec, applying both `before` and `after`
  precisely once for each node.

  `before` is applied as `(before spec obj)` before any recursion. It
  must produce a value conforming to the given spec. The resulting
  value is recursively walked according to its spec parse.

  `after` is applied as `(after spec obj*)` after recursion, where
  `obj*` is the result of the recursive walk. `obj*` need not conform
  to the spec which described the traversal that produced it.

  For instance one could traverse using a database insert operation
  which returned an ID, producing a tree of IDs rather than a legal
  record or some sort.

  `#'walk-with-spec` requires that all subspecs be named, rather than
  being anonymous specs. This is required for being able to provide a
  spec name at every node.

  Note: predicates are considered to be terminals. No effort is
  currently made to recur through maps, or to traverse
  sequences. Record structures are the goal.

  If an `Exception` is thrown while traversing, no teardown is
  provided. `before` functions SHOULD NOT rely on `after` being called
  to maintain global state."
  {:categories #{::sh/walk}
   :stability  :stability/unstable
   :added      "0.0.0"}
  [before after spec-kw obj]
  {:pre [(qualified-keyword? spec-kw)]}
  (s/assert spec-kw obj)
  (walk-with-spec* spec-kw (some-> spec-kw s/get-spec s/describe*) obj before after))

(defn- just-value [spec o] o)

(defn postwalk-with-spec
  "A postwalk according to the spec.

  See `#'walk-with-spec` for details."
  {:categories #{::sh/walk}
   :stability  :stability/unstable
   :added      "0.0.0"}
  [f spec-kw obj]
  (walk-with-spec just-value f spec-kw obj))

(defn prewalk-with-spec
  "A prewalk according to the spec.

  See `#'walk-with-spec` for details."
  {:categories #{::sh/walk}
   :stability  :stability/unstable
   :added      "0.0.0"}
  [f spec-kw obj]
  (walk-with-spec f just-value spec-kw obj))
