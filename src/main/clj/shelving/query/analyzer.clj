(ns shelving.query.analyzer
  "Implementation details of the shelving query analyzer."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.string :as str]
            [shelving.core :as sh]
            [shelving.query.common :refer [lvar?]]))

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
      (doseq [[lhs [from to :as rel] rhs :as clause] clauses]
        (when-not (sh/has-spec? schema from)
          (throw (ex-info (format "Clause '%s' makes use of unknown spec '%s'!"
                                  clause from)
                          {:schema schema
                           :spec   from})))

        (when-not (sh/has-spec? schema to)
          (throw (ex-info (format "Clause '%s' makes use of unknown spec '%s'!"
                                  clause to)
                          {:schema schema
                           :spec   to}))))))
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

(defn annotate-dependees
  "Recursively annotate lvars which are depended on with their dependees
  until a fixed point is reached."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [depmap]
  (let [d* (reduce (fn [d* [lvar {:keys [dependencies]}]]
                     (reduce #(update-in %1 [%2 :dependees] (fnil conj #{}) lvar)
                             d* dependencies))
                   depmap depmap)]
    (if (not= depmap d*)
      (recur d*) d*)))

(defn flow-selection
  "Recursively annotate dependencies and dependees of selected lvars as
  selected until a fixed point is reached.

  This prevents the dataflow optimizer from tree-shaking out
  constraints on selected variables."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [dm]
  (let [dm* (reduce (fn [dm* [lvar {:keys [dependencies dependees]}]]
                      (reduce (fn [dm* lvar]
                                (assoc-in dm* [lvar :selected?] true))
                              dm* (concat dependencies dependees)))
                    dm dm)]
    (if (not= dm dm*)
      (recur dm*) dm*)))

(defn annotate-selected
  "Annotate each selected logic variable so that it won't be removed by
  `#'purge-unused`."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [depmap select-map]
  (reduce (fn [depmap [lvar spec]]
            (-> depmap
                (assoc-in [lvar :selected?] true)
                (update-in [lvar :spec] put-spec! lvar spec)))
          depmap select-map))

(defn dataflow-optimize
  "Tree shake out any relations which are not selected or used."
  {:stability  :stability/unstable
   :categories #{::sh/query}
   :added      "0.0.0"}
  [rels select-map]
  (let [depmap (-> rels
                   compile-dependency-map
                   annotate-dependees
                   (annotate-selected select-map)
                   ;; Transitively annotate dependencies and dependees of selected lvars as
                   ;; selected. This is required for correctness because we cannot relax any
                   ;; forwards or backwards constraints as a result of optimization.
                   flow-selection)
        unused (->> depmap
                    (keep (fn [[lvar {:keys [dependees selected?]}]]
                            (when (and (empty? dependees) (not selected?))
                              lvar)))
                    set)]
    (if-not (empty? unused)
      (do (binding [*out* *err*]
            (println "WARN shelving.query.analyzer/dataflow-optimize] Optimizing out unused lvars"
                     (pr-str unused)))
          (recur (remove (fn [[lhs rel rhs]]
                           (or (unused lhs) (unused rhs)))
                         rels)
                 select-map))
      depmap)))

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
