(ns query-demo
  "A demo of the query engine."
  (:require [clojure.spec.alpha :as s]
            [shelving.core :as sh]
            [shelving.query :refer [q**** q*** q** q* q q!]]
            [shelving.trivial-edn :refer [->TrivialEdnShelf]]))

(s/def ::foo string?)
(s/def ::qux pos-int?)
(s/def :query.bar/type #{::bar})
(s/def ::bar
  (s/keys :req-un [::foo
                   ::qux
                   :query.bar/type]))

(def schema
  (-> sh/empty-schema
      (sh/value-spec ::foo)
      (sh/value-spec ::qux)
      (sh/value-spec ::bar)
      (sh/spec-rel [::bar ::foo] :foo)
      (sh/spec-rel [::bar ::qux] :qux)))

(def *conn
  (-> (->TrivialEdnShelf schema "target/query.edn"
                         :flush-after-write false
                         :load false)
      (sh/open)))

(sh/put *conn ::bar {:type ::bar :foo "a" :qux 1})
(sh/put *conn ::bar {:type ::bar :foo "a" :qux 2})
(sh/put *conn ::bar {:type ::bar :foo "a" :qux 3})
(sh/put *conn ::bar {:type ::bar :foo "b" :qux 1})
(sh/put *conn ::bar {:type ::bar :foo "c" :qux 1})

(comment
  ;; All packages in the org.clojure group
  (q! *conn
      '[:find  [?bar]
        :where [[?bar [::bar ::foo] "a"]]])

  ;; All groups which have a package at 1.0.0
  (q! *conn
      '[:find [[:from ::qux ?qux]]
        :where [[?bar [::bar ::foo] "a"]
                [?bar [::bar ::qux] ?qux]]]))
