(ns shelving.specs.query
  "Specs for shelving's Datalog."
  (:require [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as cs]
            [clojure.test.check.generators :as gen]
            [shelving.query.common :refer [lvar?]]))

(s/def ::lvar
  (s/with-gen
    lvar?
    #(gen/fmap (fn [s]
                 (symbol (str "?" s)))
               (s/gen simple-symbol?))))

;; More stupid due to CLJ-2003
(s/def ::lvar+spec?
  (s/and (s/or :simple
               ::lvar

               :spec+lvar+coll
               (s/cat :from  #{:from}
                      :spec  qualified-keyword?
                      :lvar  ::lvar
                      :coll? #{'...})

               :spec+lvar
               (s/cat :from #{:from}
                      :spec qualified-keyword?
                      :lvar ::lvar)

               :lvar+coll
               (s/cat :lvar  ::lvar
                      :coll? #{'...})

               :lvar
               (s/cat :lvar ::lvar))
         (s/conformer
          (fn [o]
            (if (= o ::s/invalid) o
                (let [[tag e] o]
                  (if (= tag :simple)
                    {:lvar e} e))))
          (fn [{:keys [spec lvar coll?]}]
            (cond (and spec lvar coll?) [:spec+lvar+coll {:from :from :spec spec :lvar lvar :coll? '...}]
                  (and spec lvar)       [:spec+lvar {:from :from :spec spec :lvar lvar}]
                  (and lvar coll?)      [:lvar+coll {:lvar lvar :coll? '...}]
                  lvar                  [:simple lvar])))))

(s/def ::lvars
  (s/alt :inline (s/+ ::lvar+spec?)
         :wrapped (s/coll-of ::lvar+spec? :into [])))

(s/def ::full-tuple
  (s/tuple ::lvar :shelving.specs.core/rel-id some?))

(s/def ::terse-tuple
  (s/tuple ::lvar qualified-keyword? some?))

(s/def ::tuple
  (s/or :explicit-rel ::full-tuple
        :inferred-rel ::terse-tuple))

(s/def ::negation
  (s/and (s/cat :not #{:not}
                :clause ::clause)
         (s/conformer
          (fn [{:keys [clause] :as o}]
            (if (= ::s/invalid o) o clause))
          (fn [clause]
            {:not    :not
             :clause clause}))))

(s/def ::guard
  (s/and (s/cat :guard #{:guard}
                :fn (s/with-gen any? ;; FIXME lolsob
                      #(s/gen #{'clojure.core/even? 'clojure.core/odd?}))
                :lvars (s/* ::lvar))
         (s/conformer
          (fn [{:keys [fn lvars] :as o}]
            (if (= ::s/invalid o) o
                (cons fn lvars)))
          (fn [[fn & lvars]]
            {:guard :guard
             :fn    fn
             :lvars lvars}))))

(s/def ::clause
  (s/or :tuple ::tuple
        #_:guard #_::guard
        #_:negation #_::negation))

(s/def ::clauses
  (s/alt :wrapped (s/coll-of ::clause :into [])
         :inline (s/* ::clause)))

;; seq-style datalog
;;
;; Note that these apparently can't use conformers to simplify because they all inhabit the same seq
;; of inputs? Not sure how spec is handling the regex walk.
(s/def :shelving.specs.query.seq/find
  (s/cat :find #{:find}
         :symbols ::lvars))

(s/def :shelving.specs.query.seq/in
  (s/cat :in #{:in}
         :parameters ::lvars))

(s/def :shelving.specs.query.seq/where
  (s/cat :where #{:where}
         :rels ::clauses))

(defn- conform-datalog-seq
  "Provides a datalog front end to the Shelving query system."
  [o]
  (if (= ::s/invalid o) o
      (let [[tag v] o
            {{find :symbols}  :find
             {where :rels}    :where
             {in :parameters} :in} v]
        {:find  (or (second find) [])
         :where (or (second where) [])
         :in    (or (second in) [])})))

(defn- unform-datalog-seq
  [{:keys [find in where]}]
  [(cond (and find in where)
         :find+in+where
         (and find in (not where))
         :find+in
         (and find (not in) where)
         :find+where
         :else :find)
   {:find  {:find :find, :symbols (vector :wrapped find)}
    :in    {:in :in, :parameters (vector :wrapped in)}
    :where {:where :where, :rels (vector :wrapped where)}}])

(s/def :shelving.specs.query.seq/datalog
  ;; workaround to https://dev.clojure.org/jira/browse/CLJ-2003
  ;; just write out the possibilities as an s/alt which will unform
  (s/and
   (s/alt :find+in+where
          (s/cat :find  :shelving.specs.query.seq/find
                 :in    :shelving.specs.query.seq/in
                 :where :shelving.specs.query.seq/where)
          
          :find+in
          (s/cat :find  :shelving.specs.query.seq/find
                 :in    :shelving.specs.query.seq/in)
          
          :find+where
          (s/cat :find  :shelving.specs.query.seq/find
                 :where :shelving.specs.query.seq/where)
          
          :find
          (s/cat :find  :shelving.specs.query.seq/find))
   (s/conformer conform-datalog-seq
                unform-datalog-seq)))

;; map-style datalog

(s/def :shelving.specs.query.map/find
  (s/coll-of ::lvar+spec? :into []))

(s/def :shelving.specs.query.map/in
  (s/coll-of ::lvar+spec? :into []))

(s/def :shelving.specs.query.map/where
  (s/coll-of ::clause :into []))

(defn- conform-datalog-map [o]
  (if (= o ::s/invalid) o
      (let [{:keys [find in where]} o]
        {:find  (or find [])
         :in    (or in [])
         :where (or where [])})))

(defn- unform-datalog-map [{:keys [find in where] :as v}] v)

(s/def :shelving.specs.query.map/datalog
  (s/and (s/keys :req-un [:shelving.specs.query.map/find]
                 :opt-un [:shelving.specs.query.map/in
                          :shelving.specs.query.map/where])
         (s/conformer conform-datalog-map
                      unform-datalog-map)))

;; expose both styles as ::datalog

(s/def ::datalog
  (s/and (s/or :seq :shelving.specs.query.seq/datalog
               :map :shelving.specs.query.map/datalog)
         (s/conformer (fn [o]
                        (if (= ::s/invalid o) o
                            (second o)))
                      (fn [v] [:seq v]))))i
