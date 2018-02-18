(ns shelving.query
  "A Datalog-style query implementation over Shelves."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [shelving.core :as sh]
            [shelving.query.analyzer :as s.q.a]
            [shelving.query.compiler :as s.q.c]
            [shelving.query.planner :as s.q.p]))

(defn q****
  "Published implementation detail.

  Given a query and a connection, builds and returns the dependency
  map on the query clauses which will be used to construct a scan
  plan.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [conn
   {:keys [params select where]
    :or   {params {}
           select {}
           where  []}
    :as   query}]
  (-> where
      ;; Bind the given parameters to their logic variables
      (s.q.a/bind-params params)

      ;; Apply preconditions
      (s.q.a/check-constant-clauses! conn)
      (s.q.a/check-specs! conn)
      (s.q.a/check-rels! conn)

      ;; Normalize all constants to the rhs
      s.q.a/normalize-where-constants

      ;; Trivially deduplicate
      set
      ;; Pin selected lvars, build the dependency map & minimize it
      (s.q.a/dataflow-optimize select)

      ;; More preconditions which are easier with dependency data
      (s.q.a/check-select-exist! select)
      (s.q.a/check-select-specs! select)))

(defn q***
  "Published implementation detail.

  Given a query and a connection, builds and returns both the fully
  analyzed logic variable dependency structure, and the topological
  ordering  which will be used to drive query planning.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :categories #{::sh/query}
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
  (let [depmap   (q**** conn query)
        ordering (as-> depmap %
                   (map (fn [[k {:keys [dependencies]
                                 :or   {dependencies #{}}}]]
                          [k dependencies]) %)
                   (into (sorted-map-by #(as-> (compare (sh/count-spec conn %2)
                                                        (sh/count-spec conn %1)) $
                                           (if (= $ 0)
                                             (compare %1 %2) $)))
                         %)
                   (s.q.a/topological-sort-lvars %))]
    {:depmap   depmap
     :ordering ordering}))

(defn q**
  "Published implementation detail.

  Given a query and a connection, builds and returns a sequence of
  plan \"clauses\" referred to as a query plan which can be compiled
  to a fn implementation.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"
   :arglists   (:arglists (meta #'q***))}
  [conn query]
  (let [{:keys [depmap ordering]} (q*** conn query)]
    (s.q.p/build-plan conn depmap ordering)))

(defn q*
  "Published implementation detail.

  Builds and returns the list form of a function implementing the
  given datalog query.

  Intended only as a mechanism for inspecting query planning &
  execution."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"
   :arglists   (:arglists (meta #'q**))}
  [conn {:keys [select] :as query}] 
  (as-> (q** conn query) %
    (s.q.c/compile-plan conn % select)))

(defn q
  "Cribbing from Datomic's q operator here.

  `select` is a mapping of {symbol spec} pairs identifying logic
  variables to be selected, and the specs from which they are to be
  selected.

  `where` is a sequence of rel \"constraint\" triples. Constraint
  triples must fit one of three forms:
   - `[lvar  rel-id lvar]`
   - `[lvar  rel-id const]`
   - `[const rel-id lvar]`

  for lvar existentially being a logic variable, rel-id being a valid
  `[spec spec]` directed relation pair, and const being any constant
  value for which there exists a meaningful content hash.

  `params` may be a map from lvars to constants, allowing for the
  specialization of queries.

  Evaluation precedes by attempting to unify the logic variables over
  the specified relations.

  Produces a sequence of solutions, being mappings from the selected
  logic variables to their values at solutions to the given relation
  constraints."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"
   :arglists   (:arglists (meta #'q*))}
  [conn {:keys [select] :as query}]
  (as-> (q* conn query) %
    ((eval %) conn select)
    (% [{}])))
