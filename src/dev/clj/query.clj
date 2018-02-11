(ns query
  (:require [clojure.spec.alpha :as s]))

(defn lvar?
  "Predicate. True if and only if the given object is a ?-prefixed
  symbol representing a logic variable."
  [obj]
  (and (symbol? obj)
       (.startsWith (name obj) "?")
       (not (namespace obj))))

(defn bind-params
  "Params provides late bindings of logic variables to constants,
  allowing \"fixed\" queries to be parameterized after the fact.

  Traverses the clauses for a query, replacing occurrences of
  parameterized logic variables with their constant.

  Throws `ExceptionInfo` if a clause becomes \"fully\" parameterized -
  that is the left and right hand sides are both constants."
  [clauses params]
  {:pre [(every? lvar? (keys params))]}
  (mapv (fn [[lhs rel rhs :as clause]]
          (let [lhs*    (if (lvar? lhs)
                          (get params lhs lhs)
                          lhs)
                rhs*    (if (lvar? rhs)
                          (get params rhs rhs)
                          rhs)
                clause* [lhs* rel rhs*]]
            (when-not (or (lvar? lhs*) (lvar? rhs*))
              (throw (ex-info (format "Parameterized clause '%s' contains no logic variables!"
                                      clause*)
                              {:params params
                               :input  clause
                               :output clause*})))
            clause*))
        clauses))

(defn normalize-where-constants
  "Where clauses may in the three forms:
   - `[lvar  rel-id lvar]`
   - `[lvar  rel-id const]`
   - `[const rel-id lvar]`

  Because relations are bidirectional with respect to their index
  behavior, normalize all relations to constants so that constants
  always occur on the right hand side of a relation."
  [clauses]
  (mapv (fn [[lhs [from to :as rel] rhs :as clause]]
          (if (and (lvar? rhs) (not (lvar? lhs)))
            [rhs [to from] lhs]
            clause))
        clauses))

(defn- put-spec! [current-spec lvar ascribed-spec]
  (if (and current-spec (not= current-spec ascribed-spec))
    (throw (IllegalStateException.
            (format "lvar '%s' ascribed incompatible specs '%s' and '%s'!"
                    lvar current-spec ascribed-spec)))
    ascribed-spec))

(defn compile-dependency-map
  "Compiles a sequence of where clauses into a map from logic variables
  to the constraints related to them.

  Returns a map of lvar symbols to \"clause\" structures.  Each clause
  structure MUST have a `:spec` being the spec from which lvar is
  drawn. Each clause and MAY have `:clauses`, being the set of clauses
  for which lvar is the lhs. Each clause and MAY have a
  `:dependencies` set, being the set of lvars occurring on the rhs of
  relations to the given lvar."
  [clauses]
  (reduce (fn [acc [lhs [from to :as rel] rhs :as clause]]
            {:pre [(lvar? lhs)]}
            (cond-> acc
              true        (update-in [lhs :clauses] (fnil conj #{}) clause)
              true        (update-in [lhs :spec] put-spec! lhs from)
              (lvar? rhs) (update-in [rhs :spec] put-spec! rhs to)
              (lvar? rhs) (update-in [lhs :dependencies] (fnil conj #{}) rhs)))
          {} clauses))

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
  [conn
   {:keys [params select where]
    :or   {params {}
           select {}
           where  []}
    :as   query}]
  (-> where
      (bind-params params)
      normalize-where-constants
      ;; Trivially deduplicate just in case
      set seq
      compile-dependency-map))

(comment
  (def *conn nil)
  ;; All packages in the org.clojure group
  (q *conn
     {:select '{?package :org.maven/package}
      :where  '[[?package [:org.maven/package :org.maven/group] "org.clojure"]]})

  ;; All groups which have a package at 1.0.0
  (q *conn
     {:select '{?group :org.maven/group}
      :where  '[[?group [:org.maven/group :org.maven/package] ?package]
                [?package [:org.maven/package :org.maven/version] "1.0.0"]]}))
