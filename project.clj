(defproject me.arrdem/shelving "_"
  :description "A toolkit for building data stores."
  :url "https://github.com/arrdem/shelving"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [org.clojure/core.match "0.3.0-alpha5"]]

  :source-paths   ["src/main/clj"
                   "src/main/cljc"]
  :test-paths     ["src/test/clj"
                   "src/test/cljc"]

  :resource-paths ["src/main/resources"]
  :profiles {:dev {:dependencies   [[org.clojure/test.check "0.10.0-alpha2"]]
                   :source-paths   ["src/dev/clj"
                                    "src/dev/clj"]
                   :resource-paths ["src/dev/resources"]
                   :doc-paths      ["README.md" "docs"]}}

  :plugins [[me.arrdem/lein-git-version "2.0.4"]
            [me.arrdem/lein-auto "0.1.4"]]
  :git-version {:status-to-version
                (fn [{:keys [tag version branch ahead ahead? dirty?] :as git}]
                  (if (and tag (not ahead?) (not dirty?))
                    (do (assert (re-find #"\d+\.\d+\.\d+" tag)
                                "Tag is assumed to be a raw SemVer version")
                        tag)
                    (if (and tag (or ahead? dirty?))
                      (let [[_ prefix patch] (re-find #"(\d+\.\d+)\.(\d+)" tag)
                            patch            (Long/parseLong patch)
                            patch+           (inc patch)]
                        (format "%s.%d-%s-SNAPSHOT" prefix patch+ branch))
                      "0.1.0-SNAPSHOT")))}

  :auto           {"test" {:file-pattern #"\.(clj|cljs|cljx|cljc|edn|md)$"
                           :paths        [:source-paths :test-paths :doc-paths]}})
