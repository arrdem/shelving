(ns shelving.query.compiler
  "Implementation details of the shelving query compiler."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.core.match :refer [match]]
            [clojure.tools.logging :as log]
            [shelving.query.planner :as p]))

(defn compile-op
  "Implementation detail of `#'compile-clause`.

  Compiles a single `[lvar expr]` pair to a Clojure expression which
  consumes a sequence of lvar binding states and produces a sequence
  of lvar binding states.

  The free variable `conn` is assumed.

  It is assumed that the state sequence will be provided as the
  trailing argument ala by `->>`."
  {:stability  :stability/unstable
   :added      "0.0.0"}
  [[lvar clause]]
  (match clause
    {:type ::p/scan-rel, :rel rel, :id id}
    `(mapcat (fn [~'state]
               (log/debug ~(str (name lvar) " " (pr-str clause) "]\n        ") ~'state)
               (map (fn [~'e]
                      (assoc ~'state '~lvar ~'e))
                    (shelving.core/get-rel ~'conn ~rel ~id))))

    {:type ::p/scan-spec :spec spec}
    `(mapcat (fn [~'state]
               (log/debug ~(str (name lvar) " " (pr-str clause) "]\n        ") ~'state)
               (map (fn [~'e]
                      (assoc ~'state '~lvar ~'e))
                    (shelving.core/enumerate-spec ~'conn ~spec))))

    {:type ::p/project :rel rel :left left-var}
    `(mapcat (fn [~'state]
               (log/debug ~(str (name lvar) " " (pr-str clause) "]\n        ") ~'state)
               (map (fn [~'e]
                      (assoc ~'state '~lvar ~'e))
                    (shelving.core/get-rel ~'conn ~rel (get ~'state '~left-var)))))

    {:type ::p/intersect :left left-var :right right-var}
    `(keep (fn [~'state]
             (log/debug ~(str (name lvar) " " (pr-str clause) "]\n        ") ~'state)
             (let [l# (get ~'state '~left-var)]
               (when (= l# (get ~'state '~right-var))
                 (assoc ~'state '~lvar l#)))))))

(defn debug!
  "Implementation helper, used for debugging the logic variable pipeline."
  {:stability :stability/unstable
   :added "0.0.0"}
  [lvar]
  (map
   (fn [e]
     (log/infof "post-lvar %s] %s" lvar (pr-str e))
     e)))

(defn compile-plan
  "Implementation detail.

  Given a query plan, compile it to a directly executable stack of
  functions."
  {:stability :stability/unstable
   :added     "0.0.0"}
  [conn {:keys [find in depmap plan]}]
  ;; And now for the tricky bit - build a transducer stack which implements the query
  `(fn [~'conn ~@in]
     (transduce (comp
                 ~@(mapcat (fn [[_lvar clauses]]
                             (map compile-op clauses))
                           plan)
                 (map (fn [~'state]
                        (->> (for [[~'lvar ~'spec]
                                   '~(mapv #(vector % (get-in depmap [% :spec])) find)]
                               [~'lvar (shelving.core/get-spec ~'conn ~'spec (get ~'state ~'lvar))])
                             (into {})))))
                conj []
                ~(if (not-empty in)
                   `(let [~'schema (shelving.core/schema ~'conn)]
                      (for [~@(mapcat (fn [lvar]
                                        `[~lvar
                                          (map #(shelving.core/id-for-record ~'schema ~(get-in depmap [lvar :spec]) %)
                                               ~(if (get-in depmap [lvar :coll?]) lvar [lvar]))])
                                      in)]
                        ~(into {} (map #(vector (list 'quote %) %) in))))
                   [{}]))))
