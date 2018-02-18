# Queries API

The searching API and helpers designed to support it.

## [shelving.query/q****](/src/main/clj/shelving/query.clj#L11)
 - `(q**** conn {:keys [params select where], :or {params {}, select {}, where []}, :as query})`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns the dependency map on the query clauses which will be used to construct a scan plan.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q***](/src/main/clj/shelving/query.clj#L49)
 - `(q*** conn {:keys [params select where], :or {params {}, select {}, where []}, :as query})`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns both the fully analyzed logic variable dependency structure, and the topological ordering  which will be used to drive query planning.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q**](/src/main/clj/shelving/query.clj#L84)
 - `(q** conn {:keys [params select where], :or {params {}, select {}, where []}, :as query})`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns a sequence of plan "clauses" referred to as a query plan which can be compiled to a fn implementation.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q*](/src/main/clj/shelving/query.clj#L101)
 - `(q* conn {:keys [params select where], :or {params {}, select {}, where []}, :as query})`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Builds and returns the list form of a function implementing the given datalog query.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q](/src/main/clj/shelving/query.clj#L117)
 - `(q conn {:keys [params select where], :or {params {}, select {}, where []}, :as query})`

**UNSTABLE**: This API will probably change in the future

Cribbing from Datomic's q operator here.

`select` is a mapping of {symbol spec} pairs identifying logic variables to be selected, and the specs from which they are to be selected.

`where` is a sequence of rel "constraint" triples. Constraint triples must fit one of three forms: - `[lvar  rel-id lvar]` - `[lvar  rel-id const]` - `[const rel-id lvar]`

for lvar existentially being a logic variable, rel-id being a valid `[spec spec]` directed relation pair, and const being any constant value for which there exists a meaningful content hash.

`params` may be a map from lvars to constants, allowing for the specialization of queries.

Evaluation precedes by attempting to unify the logic variables over the specified relations.

Produces a sequence of solutions, being mappings from the selected logic variables to their values at solutions to the given relation constraints.

## [shelving.core/count-spec](shelving/core.clj#L179)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/count-rel](shelving/core.clj#L562)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given relation.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the rel in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

