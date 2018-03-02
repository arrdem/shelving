(ns shelving.query
  "A Datalog query engine over Shelves."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [shelving.impl :as impl]
            [shelving.schema :as schema]
            [shelving.query.parser :as parser]
            [shelving.query.analyzer :as analyzer]
            [shelving.query.compiler :as compiler]
            [shelving.query.planner :as planner]
            [clojure.spec.alpha :as s]
            [clojure.core.cache :as cache]
            [hasch.core :refer [uuid]]))

(defn q****
  "Published implementation detail.

  Given a query and a connection, builds and returns the dependency
  map on the query clauses which will be used to construct a scan
  plan.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [conn query]
  {:pre [(s/valid? ::parser/datalog query)]}
  (as-> (s/conform ::parser/datalog query) %
    ;; Collect initially available spec information
    (analyzer/normalize-find-clauses %)
    (analyzer/normalize-in-clauses %)
    (analyzer/normalize-where-holes %)

    ;; Propagate available spec information, filling holes
    (analyzer/fill-holes %)

    ;; Apply preconditions before we go farther
    (analyzer/check-holes! %)
    (analyzer/check-specs! % conn)
    (analyzer/check-rels! % conn)

    ;; Apply the dataflow analyzer & optimizer
    (analyzer/dataflow-optimize %)))

(defn q***
  "Published implementation detail.

  Given a query and a connection, builds and returns both the fully
  analyzed logic variable dependency structure, and the topological
  ordering  which will be used to drive query planning.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :added      "0.0.0"
   :arglists   (:arglists (meta #'q****))}
  [conn query]
  ;; This is a little tricky. The topological sort is stable with respect to the seq order of the
  ;; keys in the map. That is, the first key whose dependencies are satisfied is the key which is
  ;; selected. Using a normal Clojure map which sorts by key hashes, this results in a correct
  ;; topsort. However by first inserting all the dependency relations into a sorted map by spec
  ;; cardinality, we can ensure that dependees of equal order are sorted by their database
  ;; cardinality thus minimizing the size of intersection scans that need to be resident at any
  ;; given point in time.
  (let [{:keys [depmap]
         :as   query} (q**** conn query)
        ordering      (as-> depmap %
                        (map (fn [[k {:keys [dependencies]
                                      :or   {dependencies #{}}}]]
                               [k dependencies]) %)
                        (into (sorted-map-by #(as-> (compare (impl/count-spec conn %2)
                                                             (impl/count-spec conn %1)) $
                                                (if (= $ 0)
                                                  (compare %1 %2) $)))
                              %)
                        (analyzer/topological-sort-lvars %))]
    (assoc query
           :depmap   depmap
           :ordering ordering)))

(defn q**
  "Published implementation detail.

  Given a query and a connection, builds and returns a sequence of
  plan \"clauses\" referred to as a query plan which can be compiled
  to a fn implementation.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :added      "0.0.0"
   :arglists   (:arglists (meta #'q***))}
  [conn query]
  (let [{:keys [depmap ordering] :as query} (q*** conn query)]
    (assoc query :plan (planner/build-plan conn depmap ordering))))

(defn q*
  "Published implementation detail.

  Builds and returns the list form of a function implementing the
  given datalog query.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :added      "0.0.0"
   :arglists   (:arglists (meta #'q**))}
  [conn query]
  (let [query (q** conn query)
        form  (compiler/compile-plan conn query)]
    (merge query
           {:form form
            :fn   (eval form)})))

(defn q
  "Cribbing from Datomic's q operator here.
  
  `find` is a sequence of symbols naming logic variables (by
  convention having the `?-` prefix) and `[:from spec lvar]` spec
  statements. `find` indicates what logic variables should be realized
  to values and produced as query results.

  `where` is a sequence of rel \"constraint\" triples. Constraint
  triples must fit one of four forms:
   - `[lvar rel-id   lvar]`
   - `[lvar rel-id   const]`
   - `[lvar rel-spec lvar]`
   - `[lvar rel-spec const]`

  for `lvar` existentially being a logic variable, `rel-id` being a
  valid `[spec spec]` directed relation pair, `rel-spec` being the
  spec of the right hand side of a relation; the left hand side being
  type inferred and const being any constant value for which there
  exists a meaningful content hash.

  `in` may be an inline or explicit sequence of logic variables, which
  may be annotated with a spec in the same `[:from <spec> <lvar>]`
  notation as supported by `find`. In parameters are compiled to
  arguments of the produced query function in the order the are given
  lexically.

  Evaluation precedes by attempting to unify the logic variables over
  the specified relations.

  Compiles and returns a new function of a connection and `in`
  parameters which will execute the compiled query.

  Query compilation is somewhat expensive so it's suggested that
  queries be compiled once and then parameterized repeatedly."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [conn query]
  (let [{:keys [fn in] :as query} (q* conn query)]
    fn))

(def ^{:dynamic    true
       :stability  :stability/unstable
       :added      "0.0.0"}
  *query-cache*
  "A cache of compiled queries.

  By default LRU caches 128 query implementations.

  Queries are indexed by content hash without any attempt to normalize
  them. Run the same `#'q!` a bunch of times on related queries and this
  works. Spin lots of single use queries and you'll bust it."
  (atom (cache/lru-cache-factory {} :threshold 128)))

(defn q!
  "Same as `#'q` but directly accepts arguments and executes the
  compiled query.

  Queries are cached to avoid repeated compilation."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [conn query & args]
  (let [query-id (uuid (s/conform ::parser/datalog query))]
    (apply (locking *query-cache*
             (get (if (cache/has? @*query-cache* query-id)
                    (swap! *query-cache* cache/hit query-id)
                    (swap! *query-cache* cache/miss query-id (q conn query)))
                  query-id))
           conn args)))
