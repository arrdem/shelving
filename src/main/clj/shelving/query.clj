(ns shelving.query
  "A Datalog-style query implementation over Shelves."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [shelving.core :as sh]))

(defn lvar?
  "Predicate. True if and only if the given object is a ?-prefixed
  symbol representing a logic variable."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
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
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
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
            clause*))
        clauses))

(defn check-specs!
  "Given a sequence of where clauses, check that every used spec exists
  in the connection's schema.

  Returns the unmodified sequence of clauses, or throws `ExceptionInfo`."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [clauses conn]
  (when conn
    (let [schema (sh/schema conn)]
      (doseq [[lhs rel rhs :as clause] clauses]
        (when-not (sh/has-spec? schema lhs)
          (throw (ex-info (format "Clause '%s' makes use of unknown spec '%s'!"
                                  clause lhs)
                          {:schema schema
                           :spec   lhs})))

        (when-not (sh/has-spec? schema rhs)
          (throw (ex-info (format "Clause '%s' makes use of unknown spec '%s'!"
                                  clause rhs)
                          {:schema schema
                           :spec   rhs}))))))
  clauses)

(defn check-rels!
  "Given a sequence of where clauses, check that every used relation
  exists in the connection's schema.

  Returns the unmodified sequence of clauses, or throws `ExceptionInfo`."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [clauses conn]
  (when conn
    (let [schema (sh/schema conn)]
      (doseq [[lhs rel rhs :as clause] clauses]
        (when-not (sh/has-rel? schema rel)
          (throw (ex-info (format "Clause '%s' makes use of unknown relation '%s!"
                                  clause rel)
                          {:schema schema
                           :clause clause
                           :rel    rel}))))))
  clauses)

(defn check-constant-clauses!
  "Given a sequence of where clauses, check that every clause relates either logic variables to
  logic variables, or logic variables to constants.

  Relating constants to constants is operationally meaningless.

  Returns the unmodified sequence of clauses, or throws `ExceptionInfo`."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [clauses conn]
  (when conn
    (doseq [[lhs rel rhs :as clause] clauses]
      (when-not (or (lvar? lhs) (lvar? rhs))
        (throw (ex-info (format "Parameterized clause '%s' contains no logic variables!"
                                clause)
                        {:clause clause})))))
  clauses)

(defn normalize-where-constants
  "Where clauses may in the three forms:
   - `[lvar  rel-id lvar]`
   - `[lvar  rel-id const]`
   - `[const rel-id lvar]`

  Because relations are bidirectional with respect to their index
  behavior, normalize all relations to constants so that constants
  always occur on the right hand side of a relation."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
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
  drawn. Each clause and MAY have `:clauses`, being a map from
  relations the set of clauses for which lvar is the lhs. Each clause
  and MAY have a `:dependencies` set, being the set of lvars occurring
  on the rhs of relations to the given lvar."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [clauses]
  (reduce (fn [acc [lhs [from to :as rel] rhs :as clause]]
            {:pre [(lvar? lhs)]}
            (cond-> acc
              true        (update-in [lhs :clauses rel] (fnil conj #{}) clause)
              true        (update-in [lhs :spec] put-spec! lhs from)
              (lvar? rhs) (update-in [rhs :spec] put-spec! rhs to)
              (lvar? rhs) (update-in [lhs :dependencies] (fnil conj #{}) rhs)))
          {} clauses))

(defn check-select-exist!
  "Checks that all logic variables designated for selection exist within
  the query.

  Throws `ExceptionInfo` if errors are detected.

  Otherwise returns the dependency map unmodified."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [dependency-map select-map]
  (let [missing-lvars (->> select-map
                           keys
                           (keep #(when-not (contains? dependency-map %) %))
                           doall
                           seq)]
    (when missing-lvars
      (throw (ex-info "Found undefined logic variables!"
                      {:lvars missing-lvars}))))
  dependency-map)

(defn check-select-specs!
  "Checks that all logic variables designated for selection have their
  ascribed specs within the query.

  Throws `ExceptionInfo` if errors are detected.

  Otherwise returns the dependency map unmodified."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [dependency-map select-map]
  (doseq [[lvar select-spec] select-map
          :let               [analyzed-spec (-> (get dependency-map lvar) :spec)]]
    (when-not (= select-spec analyzed-spec)
      (throw (ex-info (format "lvar spec conflict detected! lvar '%s' seems to have spec '%s', but spec '%s' was requested."
                              lvar analyzed-spec select-spec)
                      {:lvar          lvar
                       :select-spec   select-spec
                       :analyzed-spec analyzed-spec}))))
  dependency-map)

(defn topological-sort-lvars
  "Return a topological sort of the logic variables."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [dependency-map] 
  (loop [dependency-map dependency-map,
         resolved       #{},
         result         []]
    (if (empty? dependency-map)
      result
      (if-let [lvar (some (fn [[lvar dependencies]]
                            (if (empty? (remove resolved dependencies))
                              lvar))
                          dependency-map)]
        (recur (dissoc dependency-map lvar)
               (conj resolved lvar)
               (conj result lvar))
        (throw (Exception.
                (str "Cyclic dependency: "
                     (str/join ", " (map :name (keys dependency-map))))))))))

(defn build-plan
  "Given a dependency map between logic variables, a topsort order on
  the logic variables and a set of lvars to select, produce and return
  an evaluation plan for the query.

  Returns a query plan as a tree of records."
  [depmap topsort-lvar-seq select]
  ;; Scans are for producing bindings out of a spec by ID
  ;;
  ;; {:op :scan
  ;;  :spec spec
  ;;  :as gensym}
  ;;
  ;; Filters are for shrinking down cardinality
  ;;
  ;; {:op :filter
  ;;  :rel rel
  ;;  :from sym
  ;;  :to sym | const
  ;;  :as gensym}
  ;;
  ;; Do I need joins? Are they what I'm calling filters? Need to read prior art.
  ;;
  ;; Are there any other cases?
  )

(defn compile-plan
  "Given a query plan, compile it to a directly executable stack of functions."
  [query-plan]
  ;; And now for the tricky bit
  )

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
   :added      "0.0.0"}
  [conn
   {:keys [params select where]
    :or   {params {}
           select {}
           where  []}
    :as   query}]
  (let [depmap (-> where
                   ;; Bind the given parameters to their logic variables
                   (bind-params params)

                   ;; Apply preconditions
                   (check-constant-clauses! conn)
                   (check-specs! conn)
                   (check-rels! conn)

                   ;; Normalize all constants to the rhs
                   normalize-where-constants

                   ;; Trivially deduplicate just in case
                   set seq
                   compile-dependency-map

                   ;; More preconditions which are easier with dependency data
                   (check-select-exist! select)
                   (check-select-specs! select))

        ;; This is a little tricky. The topological sort is stable with respect to the seq order of
        ;; the keys in the map. That is, the first key whose dependencies are satisfied is the key
        ;; which is selected. Using a normal Clojure map which sorts by key hashes, this results in
        ;; a correct topsort. However by first inserting all the dependency relations into a sorted
        ;; map by spec cardinality, we can ensure that dependees of equal order are sorted by their
        ;; database cardinality thus minimizing the size of intersection scans that need to be
        ;; resident at any given point in time.
        ordering (as-> depmap %
                   (map (fn [[k {:keys [dependencies]}]] [k dependencies]) %)
                   (into (sorted-map-by #(compare (sh/count-spec conn %2)
                                                  (sh/count-spec conn %1)))
                         %)
                   (topological-sort-lvars %))]
    ordering))

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
