(ns shelving.query.parser
  "Implementation details of the shelving query parser."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "Eclipse Public License 1.0",
   :added   "0.0.0"}
  (:require [clojure.core.match :refer [match]]
            [clojure.spec.alpha :as s]
            [shelving.core :as sh]
            [shelving.query.common :refer [lvar?]]))

(s/def ::lvar+spec
  (s/tuple #{:from} qualified-keyword? lvar?))

(s/def ::lvar
  (s/or :specd   ::lvar+spec
        :unspecd lvar?))

(s/def ::seq-datalog
  (s/cat :find  ::find
         :in    (s/? ::in)
         :where (s/? ::where)))

(s/def ::find
  (s/cat :find #{:find}
         :symbols (s/coll-of ::lvar)))

(s/def ::in
  (s/cat :in #{:in}
         :parameters (s/coll-of ::lvar)))

(s/def ::full-tuple
  (s/tuple lvar? ::sh/rel-id some?))

(s/def ::terse-tuple
  (s/tuple lvar? qualified-keyword? some?))

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
        #_:guard #_::guard
        #_:negation #_::negation))

(s/def ::where
  (s/cat :where #{:where}
         :rels (s/coll-of ::clause)))

(defn- parse-datalog
  "Provides a datalog front end to the Shelving query system."
  [seq]
  (let [v (s/conform ::seq-datalog seq)]
    (if (= v ::s/invalid) v
        (let [{{find :symbols}  :find
               {where :rels}    :where
               {in :parameters} :in} v]
          {:find  find
           :where where
           :in    in}))))

(s/def ::datalog
  (s/conformer parse-datalog))
