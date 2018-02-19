# Queries API

The searching API and helpers designed to support it.

## [shelving.query/q****](shelving/query.clj#L15)
 - `(q**** conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns the dependency map on the query clauses which will be used to construct a scan plan.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q***](shelving/query.clj#L46)
 - `(q*** conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns both the fully analyzed logic variable dependency structure, and the topological ordering  which will be used to drive query planning.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q**](shelving/query.clj#L83)
 - `(q** conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Given a query and a connection, builds and returns a sequence of plan "clauses" referred to as a query plan which can be compiled to a fn implementation.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q*](shelving/query.clj#L100)
 - `(q* conn query)`

**UNSTABLE**: This API will probably change in the future

Published implementation detail.

Builds and returns the list form of a function implementing the given datalog query.

Intended only as a mechanism for inspecting query planning & execution.

## [shelving.query/q](shelving/query.clj#L119)
 - `(q conn query)`

**UNSTABLE**: This API will probably change in the future

Cribbing from Datomic's q operator here.  `find` is a sequence of symbols naming logic variables (by convention having the `?-` prefix) and `[:from spec lvar]` spec statements. `find` indicates what logic variables should be realized to values and produced as query results.

`where` is a sequence of rel "constraint" triples. Constraint triples must fit one of four forms: - `[lvar rel-id   lvar]` - `[lvar rel-id   const]` - `[lvar rel-spec lvar]` - `[lvar rel-spec const]`

for `lvar` existentially being a logic variable, `rel-id` being a valid `[spec spec]` directed relation pair, `rel-spec` being the spec of the right hand side of a relation; the left hand side being type inferred and const being any constant value for which there exists a meaningful content hash.

`in` may be an inline or explicit sequence of logic variables, which may be annotated with a spec in the same `[:from <spec> <lvar>]` notation as supported by `find`. In parameters are compiled to arguments of the produced query function in the order the are given lexically.

Evaluation precedes by attempting to unify the logic variables over the specified relations.

Compiles and returns a new function of a connection and `in` parameters which will execute the compiled query.

Query compilation is somewhat expensive so it's suggested that queries be compiled once and then parameterized repeatedly.

## [shelving.query/*query-cache*](shelving/query.clj#L161)

**UNSTABLE**: This API will probably change in the future

A cache of compiled queries.

By default LRU caches 128 query implementations.

Queries are indexed by content hash without any attempt to normalize them. Run the same `q!` a bunch of times on related queries and this works. Spin lots of single use queries and you'll bust it.

## [shelving.query/q!](shelving/query.clj#L175)
 - `(q! conn query & args)`

**UNSTABLE**: This API will probably change in the future

Same as `#'q` but directly accepts arguments and executes the compiled query.

Queries are cached to avoid repeated compilation.

## [shelving.core/count-spec](shelving/core.clj#L181)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/count-rel](shelving/core.clj#L603)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given relation.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the rel in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

