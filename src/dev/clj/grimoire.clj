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
  (s/multi-spec package->spec :package))

(s/def :org.maven/type #{:org.maven/package})
(s/def :org.maven/group string?)
(s/def :org.maven/artifact string?)
(s/def :org.maven/version string?)
(s/def :org.maven/package
  (s/keys :req-un [:org.maven/type
                   :org.maven/group
                   :org.maven/artifact
                   :org.maven/version]))

(defmulti ->id :type)

(defn ->mvn-pkg [group artifact version]
  {:type :org.maven/package
   :group group
   :artifact artifact
   :version version})

(defmethod ->id :org.maven/package [{:keys [group artifact version]}]
  (shelving/texts->sha-uuid group artifact version))

(defmethod package->spec :org.maven/package [_]
  :org.maven/package)

;; Entities, things we want to store information about.
;;--------------------------------------------------------------------------------------------------
(defmulti entity->spec
  :type)

(defmulti get-parent
  :type)

(s/def ::entity
  (s/multi-spec entity->spec :entity))

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
  {:type :org.clojure/namespace
   :package package
   :platform platform
   :name name})

(defmethod entity->spec :org.clojure/namespace [_]
  :org.clojure/namespace)

(defmethod get-parent :org.clojure/namespace [{:keys [package]}]
  package)

(defmethod ->id :org.clojure/namespace [{:keys [package platform name]}]
  (shelving/texts->sha-uuid (.toString (->id package)) platform name))

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

(defmethod get-parent :org.clojure/def [{:keys [namespace]}]
  namespace)

(defmethod ->id :org.clojure/def [{:keys [name namespace]}]
  (shelving/texts->sha-uuid (.toString (->id namespace)) name))

;; Annotations on entities.
;;--------------------------------------------------------------------------------------------------
(defmulti annotation->spec :type)

(s/def ::annotation
  (s/multi-spec annotation->spec :annotation))

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
  (s/keys :opt-un [:org.clojure-grimoire.example/name
                   :org.clojure-grimoire.example/metadata]
          :req-un [:org.clojure-grimoire.example/type
                   :org.clojure-grimoire.example/text]))

(defmethod annotation->spec :org.clojure-grimoire/example [_]
  :org.clojure-grimoire/example)

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
  (s/keys :req-un [:org.clojure-grimoire.article/type
                   :org.clojure-grimoire.article/metadata
                   :org.clojure-grimoire.article/title
                   :org.clojure-grimoire.article/text]
          :opt-un [:org.clojure-grimoire.article/metadata]))

(defmethod annotation->spec :org.clojure-grimoire/article [_]
  :org.clojure-grimoire/article)

(def schema
  (-> shelving/empty-schema
      (shelving/shelf-spec ::package ->id)

      (shelving/shelf-spec ::entity ->id)

      (shelving/shelf-spec ::annotation ->id)))

(comment
  (def *conn
    (-> (ts/->TrivialEdnShelf grimoire-schema "target/grim.edn")
        (shelving/open)))

  (shelving/put *conn ::package (->mvn-pkg "org.clojure" "clojure" "1.6.0"))

  (shelving/flush *conn))
