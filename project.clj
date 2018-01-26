(defproject me.arrdem/shelving "0.1.0-SNAPSHOT"
  :description "A toolkit for rapidly defining hierarchical data stores."
  :url "https://github.com/arrdem/shelving"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.1.143"]]

  :source-paths   ["src/main/clj"
                   "src/main/cljc"]
  :resource-paths ["src/main/resources"]
  :profiles {:dev  {:source-paths   ["src/dev/clj"
                                     "src/dev/clj"]
                    :resource-paths ["src/dev/resources"]}
             :test {:source-paths   ["src/test/clj"
                                     "src/test/cljc"]
                    :resource-paths ["src/test/resources"]}})
