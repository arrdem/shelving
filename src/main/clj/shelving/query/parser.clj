(ns shelving.query.parser
  "Implementation details of the shelving query parser."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as cs]
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

(s/def ::full-tuple
  (s/tuple ::lvar ::sh/rel-id some?))

(s/def ::terse-tuple
  (s/tuple ::lvar qualified-keyword? some?))

(s/def ::tuple
  (s/or :explicit-rel ::full-tuple
        :inferred-rel ::terse-tuple))

(s/def ::negation
  (s/and (s/cat :not #{:not}
                :clause ::clause)
         (s/conformer
          (fn [{:keys [clause]}] clause))))

(s/def ::guard
  (s/cat :guard #{:guard}
         :fn (constantly true) ;; FIXME lolsob
         :lvars (s/* ::lvar)))

(s/def ::clause
  (s/or :tuple ::tuple
        :guard ::guard
        :negation ::negation))

(s/def ::clauses
  (s/alt :wrapped (s/coll-of ::clause)
         :inline (s/* ::clause)))

;; seq-style datalog
;; 
;; Note that these apparently can't use conformers to simplify because they all inhabit the same seq
;; of inputs? Not sure how spec is handling the regex walk.
(s/def :shelving.query.parser.seq/find
  (s/cat :find #{:find}
         :symbols ::lvars))

(s/def :shelving.query.parser.seq/in
  (s/cat :in #{:in}
         :parameters ::lvars))

(s/def :shelving.query.parser.seq/where
  (s/cat :where #{:where}
         :rels ::clauses))

(defn- parse-datalog-seq
  "Provides a datalog front end to the Shelving query system."
  [v]
  (let [{{find :symbols}  :find
         {where :rels}    :where
         {in :parameters} :in} v]
    {:find  (second find)
     :where (second where)
     :in    (second in)}))

(s/def :shelving.query.parser.seq/datalog
  (s/and (s/cat :find  :shelving.query.parser.seq/find
                :in    (s/? :shelving.query.parser.seq/in)
                :where (s/? :shelving.query.parser.seq/where))
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
         (s/conformer second)))
