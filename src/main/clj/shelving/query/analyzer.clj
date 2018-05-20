(ns shelving.query.analyzer
  "Implementation details of the shelving query analyzer."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.string :as str]
            [clojure.core.match :refer [match]]
            [shelving.impl :as impl]
            [shelving.schema :as schema]
            [shelving.query.common :refer [lvar? spec?]]
            [clojure.tools.logging :as log]))

(defn tmap
  "Helper for fmapping through positive or negative tuple statements."
  [f]
  (fn [[tag tuple :as t]]
    [tag (f tuple)]))

(defn- put-spec!
  "Helper for ascribing types to lvars.

  If there is a non-nil, non-`::hole` old spec, check that the new
  spec and the old spec are the same to prevent type errors.

  If you try to ascribe `::hole` to something that already has a
  non-`::hole` spec, the already ascribed spec wins."
  [current-spec lvar ascribed-spec]
  (cond (and current-spec
             (not= current-spec ::hole)
             (= ascribed-spec ::hole))
        current-spec

        (and current-spec
             (not= current-spec ::hole)
             (not= current-spec ascribed-spec))
        (throw (IllegalStateException.
                (format "lvar '%s' ascribed incompatible specs '%s' and '%s'!"
                        lvar current-spec ascribed-spec)))

        :else
        ascribed-spec))

(defn- ascribe-spec!
  "Helper which tries to ascribe specs to lvars in the given dependency
  map, returning the updated dependency map."
  [depmap lvar spec]
  (update-in depmap [lvar :spec] put-spec! lvar spec))

(defn- normalize-clauses*
  "find and in clauses have the same spec'd/unspec'd forms, so they normalize the same way."
  {:stability :stability/unstable
   :added     "0.0.0"}
  [k query]
  (let [{:keys [depmap]
         :or   {depmap {}}} query
        depmap              (volatile! depmap)
        k*                  (mapv (fn [{:keys [lvar spec coll?]
                                        :or   {spec  ::hole
                                               coll? nil}}]
                                    (let [coll? (not (nil? coll?))]
                                      (vswap! depmap ascribe-spec! lvar spec)
                                      (vswap! depmap assoc-in [lvar :coll?] coll?)
                                      (when (and (= k :find) coll?)
                                        (log/warnf "While compiling :find clause for lvar %s, unsupported collection modifier was ignored!"
                                                   lvar))
                                      lvar))
                                  (get query k []))]
    (assoc query
           k k*
           :depmap @depmap)))

(defn normalize-find-clauses
  "Normalize typed and untyped find clauses down to a sequence of symbol
  identifiers, ascribing their specs to the lvars they define in the
  depmap where available."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [query]
  (normalize-clauses* :find query))

(defn normalize-in-clauses
  "Normalize typed and untyped in clauses down to a sequence of symbol
  identifiers, ascribing their specs to the lvars they define in the
  depmap where available."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [query]
  (normalize-clauses* :in query))

(defn normalize-where-holes
  "Normalizes all where clauses to a \"holes\" form where the right hand
  type may or may not be available. We'll check that all holes have or
  can be filled before we do anything."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [{:keys [where depmap] :as query}]
  (let [depmap (volatile! depmap)
        where* (mapv (tmap
                      (fn [tuple]
                        (match tuple
                          [:inferred-rel [lhs (spec :guard spec?) rhs]]
                          (do (when (lvar? lhs)
                                ;; Ensure the lhs exists by ascribing it ::hole if it doesn't exist
                                (vswap! depmap ascribe-spec! lhs ::hole))
                              
                              (when (lvar? rhs)
                                ;; Ascribe available spec information to rhs
                                (vswap! depmap ascribe-spec! rhs spec))
                              [lhs [::hole spec] rhs])

                          [:explicit-rel [lhs ([fs ts] :as rel-id) rhs]]
                          (do (when (lvar? lhs)
                                (vswap! depmap ascribe-spec! lhs fs))
                              (when (lvar? rhs)
                                (vswap! depmap ascribe-spec! rhs ts))
                              [lhs rel-id rhs]))))
                     where)]
    (assoc query
           :where where*
           :depmap @depmap)))

(defn fill-holes
  "Try to fill in holes in relations using accumulated spec
  information."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [{:keys [depmap where] :as query}]
  (let [where* (mapv (tmap
                      (fn fill* [tuple]
                        (match tuple
                          [(lhs :guard lvar?) [::hole (to-spec :guard spec?)] rhs]
                          [lhs [(-> (get depmap lhs) (get :spec ::hole)) to-spec] rhs]

                          [lhs [(fs :guard qualified-keyword?) (ts :guard qualified-keyword?)] rhs]
                          [lhs [fs ts] rhs])))
                     where)]
    (assoc query :where where*)))

(defn check-holes!
  "Ensure that lvars have been ascribed non-`::hole` specs."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [{:keys [where depmap] :as query}]
  (doseq [[lvar {:keys [spec]}] depmap]
    (when (= spec ::hole)
      (throw (ex-info (format "Unable to resolve non-hole spec for lvar '%s'!" lvar)
                      {:lvar   lvar
                       :spec   spec
                       :depmap depmap}))))
  query)

(defn check-specs!
  "Ensure that all specs exist in the database schema."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [{:keys [where] :as query} conn]
  (when conn
    (let [schema (impl/schema conn)]
      (doseq [clause where]
        (match clause
          [_ [_ [(from :guard qualified-keyword?) (to :guard qualified-keyword?)] _]]
          (do (when-not (schema/has-spec? schema from)
                (throw (ex-info (format "Clause '%s' makes use of unknown spec '%s'!"
                                        clause from)
                                {:schema schema
                                 :spec   from})))
              
              (when-not (schema/has-spec? schema to)
                (throw (ex-info (format "Clause '%s' makes use of unknown spec '%s'!"
                                        clause to)
                                {:schema schema
                                 :spec   to}))))))))
  query)

(defn check-rels!
  "Given a sequence of where clauses, check that every used relation
  exists in the connection's schema.

  Returns the unmodified sequence of clauses, or throws `ExceptionInfo`."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [{:keys [where] :as query} conn]
  (when conn
    (let [schema (impl/schema conn)]
      (doseq [clause where]
        (match clause
          [_ [_ rel _]]
          (do (when-not (schema/has-rel? schema rel)
                (throw (ex-info (format "Clause '%s' makes use of unknown relation '%s!"
                                        clause rel)
                                {:schema schema
                                 :clause clause
                                 :rel    rel})))
              
              (when-not (schema/has-rel? schema rel)
                (throw (ex-info (format "Clause '%s' makes use of unknown relation '%s!"
                                        clause rel)
                                {:schema schema
                                 :clause clause
                                 :rel    rel}))))))))
  query)

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
   :added      "0.0.0"}
  [clauses]
  (reduce (fn dep* [acc clause]
            (match clause
              [(:or :existance :non-existance) [lhs ([from to] :as rel) rhs]]
              (cond-> acc
                true        (update-in [lhs :clauses rel] (fnil conj #{}) clause)
                true        (update-in [lhs :spec] put-spec! lhs from)
                (lvar? rhs) (update-in [rhs :spec] put-spec! rhs to)
                (lvar? rhs) (update-in [lhs :dependencies] (fnil conj #{}) rhs))))
          {} clauses))

(defn annotate-dependees
  "Recursively annotate lvars which are depended on with their dependees
  until a fixed point is reached."
  {:stability  :stability/unstable
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
   :added      "0.0.0"}
  [dm]
  (let [dm* (reduce (fn [dm* [lvar {:keys [dependencies dependees]}]]
                      (reduce (fn [dm* lvar]
                                (assoc-in dm* [lvar :selected?] true))
                              dm* (concat dependencies dependees)))
                    dm dm)]
    (if (not= dm dm*)
      (recur dm*) dm*)))

(defn- annotate-lvars
  [k depmap lvars]
  (reduce (fn [depmap lvar]
            (-> depmap
                (assoc-in [lvar :selected?] true)
                (assoc-in [lvar k] true)))
          depmap lvars))

(def ^:private annotate-selected
  (partial annotate-lvars :find?))

(def ^:private annotate-provided
  (partial annotate-lvars :in?))

(defn dataflow-optimize
  "Tree shake out any relations which are not selected or used."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [{:keys [depmap find in where] :as query}]
  (let [depmap (as-> where %
                 (compile-dependency-map %)
                 (annotate-dependees %)
                 ;; Annotate selected (find) and provided (in) vars so they cannot be elided
                 (annotate-selected % find)
                 (annotate-provided % in)
                 ;; Transitively annotate dependencies and dependees of selected lvars as
                 ;; selected. This is required for correctness because we cannot relax any
                 ;; forwards or backwards constraints as a result of optimization.
                 (flow-selection %)
                 (merge-with merge depmap %))
        unused (->> depmap
                    (keep (fn [[lvar {:keys [clauses dependees selected?]}]]
                            (when (and (empty? clauses) (empty? dependees) (not selected?))
                              lvar)))
                    set)]
    (if-not (empty? unused)
      (do (binding [*out* *err*]
            (println "WARN shelving.query.analyzer/dataflow-optimize] Optimizing out unused lvars"
                     (pr-str unused)))
          (recur (update query :where
                         #(remove (fn [[lhs rel rhs]]
                                    (or (unused lhs) (unused rhs)))
                                  %))))
      (assoc query
             :depmap depmap))))

(defn topological-sort-lvars
  "Return a topological sort of the logic variables."
  {:stability  :stability/unstable
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
