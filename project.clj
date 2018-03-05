(defproject me.arrdem/shelving "_"
  :description "A toolkit for building data stores."
  :url "https://github.com/arrdem/shelving"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :exclusions [org.clojure/clojure]
  :dependencies [[me.arrdem/clojure "1.10.0-SNAPSHOT"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [io.replikativ/hasch "0.3.4"
                  :exclusions [com.cemerick/austin]]
                 [me.arrdem/detritus "0.3.2"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/tools.logging "0.4.0"]
                 [potemkin "0.4.4"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.apache.logging.log4j/log4j-api "2.10.0"]
                 [org.apache.logging.log4j/log4j-core "2.10.0"]]
  
  :source-paths      ["src/main/clj"
                      "src/main/cljc"]
  :java-source-paths ["src/main/jvm"]
  :test-paths        ["src/test/clj"
                      "src/test/cljc"]

  :resource-paths ["src/main/resources"]
  :profiles {:dev {:dependencies      [[org.clojure/test.check "0.10.0-alpha2"]]
                   :source-paths      ["src/dev/clj"
                                       "src/dev/clj"]
                   :java-source-paths ["src/dev/jvm"]
                   :resource-paths    ["src/dev/resources"]
                   :doc-paths         ["README.md" "docs"]
                   :aliases           {"build-docs" ["run" "-m" "compile-docs/recompile-docs!"]}}}

  :plugins [[me.arrdem/lein-git-version "[2.0.0,3.0.0)"]
            [me.arrdem/lein-auto "0.1.4"]
            [lein-cljfmt "0.5.7"]]

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

  :auto {:default {:file-pattern #"\.(clj|cljs|cljx|cljc|edn|md)$"
                   :paths        [:java-source-paths
                                  :source-paths
                                  :resource-paths
                                  :test-paths
                                  :doc-paths]}}

  :cljfmt {:indents {quick-check [[:block 1]]
                     for-all     [[:block 1]]}})
