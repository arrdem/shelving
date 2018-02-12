# Queries API

The searching API and helpers designed to support it.

## [shelving.query/lvar?](/src/main/clj/shelving/query.clj#L10)
 - `(lvar? obj)`

**UNSTABLE**: This API will probably change in the future

Predicate. True if and only if the given object is a ?-prefixed symbol representing a logic variable.

## [shelving.query/bind-params](/src/main/clj/shelving/query.clj#L21)
 - `(bind-params clauses params)`

**UNSTABLE**: This API will probably change in the future

Params provides late bindings of logic variables to constants, allowing "fixed" queries to be parameterized after the fact.

Traverses the clauses for a query, replacing occurrences of parameterized logic variables with their constant.

Throws `ExceptionInfo` if a clause becomes "fully" parameterized - that is the left and right hand sides are both constants.

## [shelving.query/check-specs!](/src/main/clj/shelving/query.clj#L46)
 - `(check-specs! clauses conn)`

**UNSTABLE**: This API will probably change in the future

Given a sequence of where clauses, check that every used spec exists in the connection's schema.

Returns the unmodified sequence of clauses, or throws `ExceptionInfo`.

## [shelving.query/check-rels!](/src/main/clj/shelving/query.clj#L71)
 - `(check-rels! clauses conn)`

**UNSTABLE**: This API will probably change in the future

Given a sequence of where clauses, check that every used relation exists in the connection's schema.

Returns the unmodified sequence of clauses, or throws `ExceptionInfo`.

## [shelving.query/check-constant-clauses!](/src/main/clj/shelving/query.clj#L91)
 - `(check-constant-clauses! clauses conn)`

**UNSTABLE**: This API will probably change in the future

Given a sequence of where clauses, check that every clause relates either logic variables to logic variables, or logic variables to constants.

Relating constants to constants is operationally meaningless.

Returns the unmodified sequence of clauses, or throws `ExceptionInfo`.

## [shelving.query/normalize-where-constants](/src/main/clj/shelving/query.clj#L110)
 - `(normalize-where-constants clauses)`

**UNSTABLE**: This API will probably change in the future

Where clauses may in the three forms: - `[lvar  rel-id lvar]` - `[lvar  rel-id const]` - `[const rel-id lvar]`

Because relations are bidirectional with respect to their index behavior, normalize all relations to constants so that constants always occur on the right hand side of a relation.

## [shelving.query/compile-dependency-map](/src/main/clj/shelving/query.clj#L136)
 - `(compile-dependency-map clauses)`

**UNSTABLE**: This API will probably change in the future

Compiles a sequence of where clauses into a map from logic variables to the constraints related to them.

Returns a map of lvar symbols to "clause" structures.  Each clause structure MUST have a `:spec` being the spec from which lvar is drawn. Each clause and MAY have `:clauses`, being a map from relations the set of clauses for which lvar is the lhs. Each clause and MAY have a `:dependencies` set, being the set of lvars occurring on the rhs of relations to the given lvar.

## [shelving.core/count-spec](/src/main/clj/shelving/core.clj#L157)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns at least an upper bound on the cardinality of a given spec.

Implementations of this method should be near constant time and should not require realizing the relation in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.query/check-select-exist!](/src/main/clj/shelving/query.clj#L159)
 - `(check-select-exist! dependency-map select-map)`

**UNSTABLE**: This API will probably change in the future

Checks that all logic variables designated for selection exist within the query.

Throws `ExceptionInfo` if errors are detected.

Otherwise returns the dependency map unmodified.

## [shelving.query/check-select-specs!](/src/main/clj/shelving/query.clj#L180)
 - `(check-select-specs! dependency-map select-map)`

**UNSTABLE**: This API will probably change in the future

Checks that all logic variables designated for selection have their ascribed specs within the query.

Throws `ExceptionInfo` if errors are detected.

Otherwise returns the dependency map unmodified.

## [shelving.query/topological-sort-lvars](/src/main/clj/shelving/query.clj#L201)
 - `(topological-sort-lvars dependency-map)`

**UNSTABLE**: This API will probably change in the future

Return a topological sort of the logic variables.

## [shelving.query/q](/src/main/clj/shelving/query.clj#L223)
 - `(q conn {:keys [params select where], :or {params {}, select {}, where []}, :as query})`

**UNSTABLE**: This API will probably change in the future

Cribbing from Datomic's q operator here.

`select` is a mapping of {symbol spec} pairs identifying logic variables to be selected, and the specs from which they are to be selected.

`where` is a sequence of rel "constraint" triples. Constraint triples must fit one of three forms: - `[lvar  rel-id lvar]` - `[lvar  rel-id const]` - `[const rel-id lvar]`

for lvar existentially being a logic variable, rel-id being a valid `[spec spec]` directed relation pair, and const being any constant value for which there exists a meaningful content hash.

`params` may be a map from lvars to constants, allowing for the specialization of queries.

Evaluation precedes by attempting to unify the logic variables over the specified relations.

Produces a sequence of solutions, being mappings from the selected logic variables to their values at solutions to the given relation constraints.

## [shelving.core/count-rel](/src/main/clj/shelving/core.clj#L539)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns at least an upper bound on the cardinality of a given relation.

Implementations of this method should be near constant time and should not require realizing the relation in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

