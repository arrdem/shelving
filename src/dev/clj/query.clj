(ns query)

(defn q
  "Cribbing from Datomic's q operator here.

  `select` is a mapping of {symbol spec} pairs identifying logic
  variables to be selected, and the specs from which they are to be
  selected.

  `where` is a sequence of rel \"constraint\" triples. Constraint
  triples must fit one of three forms:
   - `[lvar  rel-id lvar]`
   - `[lvar  rel-id const]`
   - `[const rel-id lvar]`

  for lvar existentially being a logic variable, rel-id being a valid
  `[spec spec]` directed relation pair, and const being any constant
  value for which there exists a meaningful content hash.

  `params` may be a map from lvars to constants, allowing for the
  specialization of queries.

  Evaluation precedes by attempting to unify the logic variables over
  the specified relations.

  Produces a sequence of solutions, being mappings from the selected
  logic variables to their values at solutions to the given relation
  constraints."
  [conn
   {:keys [params select where]
    :or   {params {}
           select {}
           where  []}
    :as   query}]
  ;; FIXME (arrdem 2018-02-08):
  ;;   and now for the tricky bit
  )

(comment
  ;; All packages in the org.clojure group
  (q *conn
     {:select '{?package :org.maven/package}
      :where  '[[?package [:org.maven/package :org.maven/group] "org.clojure"]]})

  ;; All groups which have a package at 1.0.0
  (q *conn
     {:select '{?group :org.maven/group}
      :where  '[[?group [:org.maven/group :org.maven/package] ?package]
                [?package [:org.maven/package :org.maven/version] "1.0.0"]]}))
