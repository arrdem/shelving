(ns shelving.query.planner
  "Implementation details of the Shelving query planner."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.core.match :refer [match]]
            [shelving.impl :as impl]
            [shelving.schema :as schema]
            [shelving.query.common :refer [lvar?]]))

(defn invert [[from to :as rel]]
  {:pre [(vector? rel)
         (qualified-keyword? from)
         (qualified-keyword? to)
         (= 2 (count rel))]}
  [to from])

(defn build-clause
  "Implementation detail of `#'build-plan`.

  Given a connection, a single relation statement, the logic var it
  consumes (`from`) and the logic var it produces (`to`), emit a
  sequence of concrete scan or relational operators specifying how to
  produce the possibility space of the `to` logic variable."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [conn clause from to]
  (match clause
    [:existance [lhs ([from-spec to-spec] :as rel) rhs]]
    (let [im1 (gensym "?e_")]
      (cond (and from (not (lvar? rhs)))
            ;; Project and join on a constant
            [[im1
              {:type ::scan-rel, :rel (invert rel),
               :id (-> conn impl/schema (schema/id-for-record to-spec rhs))}]
             [to
              {:type  ::intersect, :left from, :right im1}]]

            (and from (lvar? rhs))
            ;; Project and join on another lvar
            [[im1
              {:type ::project, :rel (invert rel), :left rhs}]
             [to
              {:type ::intersect, :left from, :right im1}]]

            (and (not from) (not (lvar? rhs)))
            ;; Creating an initial scan on a constant
            [[to
              {:type ::scan-rel, :rel (invert rel),
               :id (-> conn impl/schema (schema/id-for-record to-spec rhs))}]]

            (and (not from) (lvar? rhs))
            ;; Creating an initial scan on an lvar
            ;;
            ;; This is the case of a fully unconstrained logic variable.  Because we have topsort
            ;; order, we know that the rhs has already been emitted, so we just project it.
            [[to
              {:type ::project, :rel (invert rel), :left rhs}]]

            :else (throw (IllegalStateException.
                          (format "Could not build an operation sequence for %s" (pr-str clause))))))

    [:non-existance [lhs ([from-spec to-spec] :as rel) rhs]]
    ;; FIXME (arrdem 2018-05-19):
    ;;
    ;; It seems like this approach to non-existence leaves probably a lot on the table in large part
    ;; because there's no way to signal termination UPSTREAM to the transducer stack - only
    ;; downstream to the consumer. Maybe fundamentally this calls for a reworking of how I model
    ;; queries - recursive chains of pulling functions / streams? Does that force any questions
    ;; about how I currently manage state? I think most of that can stay the same...
    ;;
    ;; The core problem here is that "complements" as a stream operator needs to know when it sees
    ;; the end of the upstream scan or join - currently that isn't part of the state model.
    ;;
    ;; In order to be efficient, we also want to be able to fuse scans (which isn't supported yet
    ;; anyway) and to be able to terminate a given scan (which has consequences for fusion) when we
    ;; locate a counter-example under negation.

    ;; FIXME (arrdem 2018-05-19):
    ;; 
    ;;   Need to implement a stratification checker in order for negation to really be sane
    (let [im1 (gensym "?ne_")]
      (cond (and from (not (lvar? rhs)))
            ;; LHS DOES NOT project to RHS for any value
            [[im1
              {:type ::scan-rel, :rel (invert rel)
               :id (-> conn impl/schema (schema/id-for-record to-spec rhs))}]
             [to
              {:type ::complements, :left from, :right im1}]]

            (and from (lvar? rhs))
            ;; Disjoin on another lvar
            [[im1
              {:type ::project, :rel rel, :left rhs}]
             [to
              {:type ::complements, :left from, :right im1}]]

            :else (throw (IllegalStateException.
                          (format "Could not build an operation sequence for %s"
                                  (pr-str clause))))))))

(defn build-plan
  "Implementation detail.

  Given a dependency map between logic variables, a topsort order on
  the logic variables and a set of lvars to select, produce and return
  an evaluation plan for the query.

  The query plan is a sequence of pairs `[lvar, operations]` where
  each operation is pair of a logic variable and a single scan,
  projection or join defining that variable.

  Returns a query plan as a tree of records."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [conn depmap ordering]
  ;; Scans are for producing bindings out of a spec by ID Can scan a whole spec, producing the set
  ;; of its IDs.
  ;;
  ;;     {:type ::scan-spec
  ;;      :spec spec}
  ;;
  ;; Or can scan a relation at a constant, producing the opposing set of IDs.
  ;;
  ;;     {:type ::scan-rel
  ;;      :rel rel
  ;;      :id  uuid}
  ;;
  ;; We can also project from one set through a relation to another set of opposing IDs.
  ;;
  ;;     {:type ::project
  ;;      :rel  rel
  ;;      :left qvar}
  ;;
  ;; Joins don't really exist, they're intersections of sets.
  ;;
  ;;     {:type ::intersect
  ;;      :left qvar
  ;;      :right qvar}
  ;;
  ;; A query plan is a sequence of pairs `[lvar op]` where an op is one of the structures above and
  ;; the logic variable is the term for the product of that operation.

  (as-> ordering %
    (keep (fn [lvar]
            (let [{:keys [in? clauses spec]
                   :or   {in?     false
                          clauses []}
                   :as   lvar-deps} (get depmap lvar)]
              (when-not in?
                (let [clauses (mapcat #(get clauses %)
                                      (sort-by #(impl/count-rel conn %)
                                               (keys clauses)))

                      binds (concat (repeatedly (if-not clauses 0
                                                        (dec (count clauses)))
                                                #(gensym "?q_"))
                                    [lvar])]
                  [(gensym (str (name lvar) "_"))
                   (if (empty? clauses)
                     ;; emit a simple scan
                     [[lvar {:type ::scan-spec
                             :spec spec}]]

                     ;; compile all the clauses
                     (mapcat (partial build-clause conn)
                             clauses
                             (cons nil (butlast binds))
                             binds))]))))
          %)
    (into [] %)))
