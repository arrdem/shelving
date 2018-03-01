(ns compile-docs
  "A quick hack for rebuilding docs containing manually situated var references."
  (:require [shelving.core :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.io.File))

(defn ensure-trailing-newline [s]
  (if-not (.endsWith s "\n")
    (str s "\n") s))

(defn relativize-path [p]
  (str/replace p (.getCanonicalPath (io/file ".")) ""))

(defn document-var [^clojure.lang.Var v heading]
  (let [{:keys [categories arglists doc stability line file]
         :as   var-meta} (meta v)]
    (log/infof "Documenting %s" v)
    (with-out-str
      (printf "%s [%s/%s](%s#L%s)\n"
              heading
              (ns-name (.ns v)) (.sym v)
              (relativize-path file) line)
      (doseq [params arglists]
        (printf " - `%s`\n" (cons (.sym v) params)))
      (printf "\n")
      (when (= stability :stability/unstable)
        (printf "**UNSTABLE**: This API will probably change in the future\n\n"))
      (printf (ensure-trailing-newline
               (-> doc
                   (str/replace #"(?<!\n)\n[\s&&[^\n\r]]+" " ")
                   (str/replace #"\n\n[\s&&[^\n\r]]+" "\n\n"))))
      (printf "\n"))))

(def var-doc-pattern
  #"(?ms)^(?<heading>#{2,}) \[(?<name>[^\]]+?)\]\((?<path>[^\#]+?)#L(?<line>\d+)\)?\n(?<body>.*?)((?=^#{2,} \[)|\Z)")

(defn recompile-docs [files]
  (doseq [f files]
    (log/infof "Updating %s" f)
    (try (let [buff (slurp f)]
           (spit f
                 (str/replace buff var-doc-pattern
                        (fn [[original heading name path line _body]]
                          (try (let [sym (symbol name)]
                            (require (symbol (namespace sym)))
                            (some-> sym resolve (document-var heading)))
                               (catch Exception e
                                 original))))))
         (catch Exception e
           (log/errorf "Encountered error while updating %s:\n%s" f e)))))

(defn recompile-docs!
  "Entry point suitable for a lein alias. Usable for automating doc rebuilding."
  [& args]
  (recompile-docs
   (filter #(.endsWith (.getPath ^File %) ".md")
           (file-seq (io/file "docs/")))))
