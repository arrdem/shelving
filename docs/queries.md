# Queries API

The searching API and helpers designed to support it.

## [shelving.query/q****](shelving/query.clj#L13)
 - `(q**** conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns the dependency map on the query clauses which will be used to construct a scan plan.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q***](shelving/query.clj#L44)
 - `(q*** conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns both the fully analyzed logic variable dependency structure, and the topological ordering  which will be used to drive query planning.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q**](shelving/query.clj#L81)
 - `(q** conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns a sequence of plan "clauses" referred to as a query plan which can be compiled to a fn implementation.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q*](shelving/query.clj#L98)
 - `(q* conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Builds and returns the list form of a function implementing the given datalog query.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q](shelving/query.clj#L117)
 - `(q conn query)`

**UNSTABLE**: This API will probably change in the future

Cribbing from Datomic's q operator here.  `find` is a sequence of symbols naming logic variables (by convention having the `?-` prefix) and `[:from spec lvar]` spec statements. `find` indicates what logic variables should be realized to values and produced as query results.

`where` is a sequence of rel "constraint" triples. Constraint triples must fit one of four forms: - `[lvar rel-id   lvar]` - `[lvar rel-id   const]` - `[lvar rel-spec lvar]` - `[lvar rel-spec const]`

for `lvar` existentially being a logic variable, `rel-id` being a valid `[spec spec]` directed relation pair, `rel-spec` being the spec of the right hand side of a relation; the left hand side being type inferred and const being any constant value for which there exists a meaningful content hash.

`in` may be an inline or explicit sequence of logic variables, which may be annotated with a spec in the same `[:from <spec> <lvar>]` notation as supported by `find`. In parameters are compiled to arguments of the produced query function in the order the are given lexically.

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

