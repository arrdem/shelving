(ns shelving.walk
  "Tools for using `clojure.spec(.alpha)` to walk data structures."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]))

(defn keys-as-map
  "Implementation detail of `#'walk-with-spec`.

  Consumes a complete s/keys form, producing a map from map keys to
  the spec for that key.

  Imposes the restriction that there be PRECISELY ONE spec for any
  given key. Will throw otherwise."
  [keys-form]
  (let [[_keys & {:keys [req req-un opt opt-un] :as opts}] keys-form]
    (->> (concat (mapcat (juxt #(keyword (name %)) identity) (concat req-un opt-un))
                 (mapcat (juxt identity identity) (concat req opt)))
         (apply hash-map))))

(declare walk-with-spec)

(def ^:dynamic *trace-walk*
  false)

(defmulti walk-with-spec*
  "Implementation detail of walk-with-spec.

  Uses multiple dispatch to handle actually walking the spec tree."
  {:arglists '([spec-kw spec obj before after])}
  (fn [name spec _ _ _]
    (when *trace-walk*
      (binding [*out* *err*]
        (println `walk-with-spec* "DEBUG ]" name spec)))
    (if (seq? spec)
      (first spec)
      (if (qualified-keyword? spec)
        ::alias
        (if (ifn? spec)
          ::predicate
          :default)))))

(defmethod walk-with-spec* ::alias [spec alias obj before after]
  {:pre [(qualified-keyword? alias)]} 
  (as-> obj %
    (before spec %)
    (do (s/assert spec %) %)
    (walk-with-spec before after (some-> spec s/get-spec s/describe*) %)
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
      (assert (qualified-keyword? spec*))
      (walk-with-spec before after spec* %))))

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
  [before after spec-kw obj]
  {:pre [(qualified-keyword? spec-kw)
         (s/valid? spec-kw obj)]}
  (walk-with-spec* spec-kw (some-> spec-kw s/get-spec s/describe*) obj before after))

(defn- just-value [spec o] o)

(defn postwalk-with-spec
  "A postwalk according to the spec.

  See `#'walk-with-spec` for details."
  [f spec-kw obj]
  (walk-with-spec just-value f spec-kw obj))

(defn prewalk-with-spec
  "A prewalk according to the spec.

  See `#'walk-with-spec` for details."
  [f spec-kw obj]
  (walk-with-spec f just-value spec-kw obj))
