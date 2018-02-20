(ns grimoire
  "A demo of the shelving API, and applying it to a Grimoire v2-like store."
  (:require [shelving.core :as shelving]
            [shelving.trivial-edn :as ts]
            [clojure.spec.alpha :as s]))

;; Packages, containers of entities we wish to document.
;;------------------------------------------------------------------------------------------
(defmulti package->spec
  :type)

(s/def ::package
  (s/multi-spec grimoire/package->spec :package))

(s/def :org.maven/type #{:org.maven/package})
(s/def :org.maven/group string?)
(s/def :org.maven/artifact string?)
(s/def :org.maven/version string?)
(s/def :org.maven/package
  (s/keys :req-un [:org.maven/type
                   :org.maven/group
                   :org.maven/artifact
                   :org.maven/version]))

(defn ->mvn-pkg [group artifact version]
  {:type     :org.maven/package
   :group    group
   :artifact artifact
   :version  version})

(defmethod package->spec :org.maven/package [_]
  :org.maven/package)

;; Entities, things we want to store information about.
;;--------------------------------------------------------------------------------------------------
(defmulti entity->spec
  :type)

(defmulti entity->package
  :type)

(s/def ::entity
  (s/multi-spec grimoire/entity->spec :entity))

(s/def :org.clojure.namespace/name
  string?)

(s/def :org.clojure.namespace/platform
  #{"clj" "cljc" "cljr" "cljs"})

(s/def :org.clojure.namespace/type
  #{:org.clojure/namespace})

(s/def :org.clojure/namespace
  (s/keys :req-un [:org.clojure.namespace/type
                   :org.clojure.namespace/name
                   ::package
                   :org.clojure.namespace/platform]))

(defn ->namespace [package platform name]
  {:type     :org.clojure/namespace
   :package  package
   :platform platform
   :name     name})

(defmethod entity->spec :org.clojure/namespace [_]
  :org.clojure/namespace)

(defmethod entity->package :org.clojure/namespace [{:keys [package]}]
  package)

(s/def :org.clojure.def/type
  #{:org.clojure/def})

(s/def :org.clojure.def/name
  string?)

(s/def :org.clojure/def
  (s/keys :req-un [:org.clojure.def/type
                   :org.clojure/namespace
                   :org.clojure.def/name]))

(defmethod entity->spec :org.clojure/def [_]
  :org.clojure/def)

(defmethod entity->package :org.clojure/def [{:keys [namespace]}]
  (entity->package namespace))

;; Annotations on entities.
;;--------------------------------------------------------------------------------------------------
(defmulti annotation->spec :type)
(defmulti annotation->entity :type)
(defmulti annotation->package :type)

(s/def ::annotation
  (s/multi-spec grimoire/annotation->spec :annotation))

;; Metadata
(s/def :org.clojure-grimoire.example/metadata
  map?)

;; Examples
(s/def :org.clojure-grimoire.example/type
  #{:org.clojure-grimoire/example})

(s/def :org.clojure-grimoire.example/name
  string?)

(s/def :org.clojure-grimoire.example/text
  string?)

(s/def :org.clojure-grimoire/example
  (s/and (s/keys :opt-un [:org.clojure-grimoire.example/name
                          :org.clojure-grimoire.example/metadata]
                 :req-un [:org.clojure-grimoire.example/type
                          :org.clojure-grimoire.example/text])
         (s/or :entity  (s/keys :req-un [::entity])
               :package (s/keys :req-un [::package]))))

(defmethod annotation->spec :org.clojure-grimoire/example [_]
  :org.clojure-grimoire/example)

(defmethod annotation->entity :org.clojure-grimoire/example [{:keys [entity]}]
  entity)

(defmethod annotation->package :org.clojure-grimoire/example [{:keys [package entity]}]
  (or package
      (entity->package entity)))

;; Articles
(s/def :org.clojure-grimoire.article/type
  #{:org.clojure-grimoire/article})

(s/def :org.clojure-grimoire.article/name
  string?)

(s/def :org.clojure-grimoire.article/text
  string?)

(s/def :org.clojure-grimoire.article/metadata
  map?)

(s/def :org.clojure-grimoire/article
  (s/and (s/keys :req-un [:org.clojure-grimoire.article/type
                          :org.clojure-grimoire.article/metadata
                          :org.clojure-grimoire.article/title
                          :org.clojure-grimoire.article/text]
                 :opt-un [:org.clojure-grimoire.article/metadata])
         (s/or :entity  (s/keys :req-un [::entity])
               :package (s/keys :req-un [::package]))))

(defmethod annotation->spec :org.clojure-grimoire/article [_]
  :org.clojure-grimoire/article)

(defmethod annotation->entity :org.clojure-grimoire/article [{:keys [entity]}]
  entity)

(defmethod annotation->package :org.clojure-grimoire/article [{:keys [package entity]}]
  (or package
      (entity->package entity)))

(def schema
  (-> shelving/empty-schema
      (shelving/value-spec ::package)
      (shelving/value-spec ::entity)
      (shelving/record-spec ::annotation)))
