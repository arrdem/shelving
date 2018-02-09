(ns grimoire-test
  (:require [clojure.test :as t]
            [shelving.core :as shelving]
            [shelving.trivial-edn :as ts]
            [grimoire :refer :all :as g]))

(def *conn
  (-> (ts/->TrivialEdnShelf schema "target/grim.edn"
                            :load false)
      (shelving/open)))

(def clj-160
  (->mvn-pkg "org.clojure" "clojure" "1.6.0"))

(def clj-180
  (->mvn-pkg "org.clojure" "clojure" "1.8.0"))

(def clj-160-clojure-core
  (->namespace clj-160 "clj" "clojure.core"))

(def clj-180-clojure-core
  (->namespace clj-180 "clj" "clojure.core"))

(shelving/flush *conn)

(t/deftest test-indexing
  (let [;; write some CLJ versions
        clj-160-id (shelving/put *conn ::g/package clj-160)
        clj-180-id (shelving/put *conn ::g/package clj-180)

        ;; Write some core versions
        core-160-id (shelving/put *conn ::g/entity clj-160-clojure-core)
        core-180-id (shelving/put *conn ::g/entity clj-180-clojure-core)]))