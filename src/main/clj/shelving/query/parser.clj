(ns shelving.query.parser
  "Implementation details of the shelving query parser."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [shelving.core :as sh]
            [shelving.query.common :refer [lvar?]]))

(s/def :shelving.query.parser/lvar
  (s/with-gen
    lvar?
    #(gen/fmap (fn [s]
                 (symbol (str "?" s)))
               (s/gen simple-symbol?))))

(s/def ::lvar+spec
  (s/tuple #{:from} qualified-keyword? ::lvar))

(s/def ::lvar+spec?
  (s/or :unspecd ::lvar
        :specd   ::lvar+spec))

(s/def ::lvars
  (s/alt :inline (s/+ ::lvar+spec?)
         :wrapped (s/coll-of ::lvar+spec?)))

(s/def ::find
  (s/cat :find #{:find}
         :symbols ::lvars))

(s/def ::in
  (s/cat :in #{:in}
         :parameters ::lvars))

(s/def ::full-tuple
  (s/tuple ::lvar ::sh/rel-id some?))

(s/def ::terse-tuple
  (s/tuple ::lvar qualified-keyword? some?))

(s/def ::tuple
  (s/or :explicit-rel ::full-tuple
        :inferred-rel ::terse-tuple))

(s/def ::negation
  (s/cat :not #{:not}
         :clause ::clause))

(s/def ::guard
  (s/cat :guard #{:guard}
         :clause (s/cat :fn qualified-symbol?
                        :lvar ::lvar)))

(s/def ::clause
  (s/or :tuple ::tuple
        :guard ::guard
        :negation ::negation))

(s/def ::clauses
  (s/alt :wrapped (s/coll-of ::clause)
         :inline (s/* ::clause)))

(s/def ::where
  (s/cat :where #{:where}
         :rels ::clauses))

;; seq-style datalog

(defn- parse-datalog-seq
  "Provides a datalog front end to the Shelving query system."
  [v]
  (if (= v ::s/invalid) v
      (let [{{find :symbols}  :find
             {where :rels}    :where
             {in :parameters} :in} v]
        {:find  (second find)
         :where (second where)
         :in    (second in)})))

(s/def :shelving.query.parser.seq/datalog
  (s/and (s/cat :find  ::find
                :in    (s/? ::in)
                :where (s/? ::where))
         (s/conformer parse-datalog-seq)))

;; map-style datalog

(s/def :shelving.query.parser.map/find
  ::lvars)

(s/def :shelving.query.parser.map/in
  ::lvars)

(s/def :shelving.query.parser.map/where
  ::clauses)

(defn- parse-datalog-map [{:keys [find in where]}]
  {:find  (second find)
   :in    (second in)
   :where (second where)})

(s/def :shelving.query.parser.map/datalog
  (s/and (s/keys :req-un [:shelving.query.parser.map/find]
                 :opt-un [:shelving.query.parser.map/in
                          :shelving.query.parser.map/where])
         (s/conformer parse-datalog-map)))

;; expose both styles as ::datalog

(s/def ::datalog
  (s/and (s/or :seq :shelving.query.parser.seq/datalog
               :map :shelving.query.parser.map/datalog)
         (s/conformer (fn [[tag val]] val))))
