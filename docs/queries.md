# Queries API

The searching API and helpers designed to support it.

## [shelving.core/q](shelving/impl.clj#L300)
 - `(q conn query)`

Query compilation.

Given a connection and a query datastructure, return a function of a connection and 0 or more positional logic variable bindings per the `:in` clause of the compiled query. Query functions return sequences of maps from logic variables to values. Each produced map must contain all lvars occurring in the query's `:find` clause.

See the datalog documentation for a full description of the supported query form.

## [shelving.core/\*query-cache\*](shelving/core.clj#L276)

**UNSTABLE**: This API will probably change in the future

A cache of compiled queries.

By default LRU caches 128 query implementations.

Queries are indexed by content hash without any attempt to normalize them. Run the same [`#'shelving.core/q!`](/docs/basic.md#shelvingcoreq!) a bunch of times on related queries and this works. Spin lots of single use queries and you'll bust it.

## [shelving.core/q!](shelving/core.clj#L289)
 - `(q! conn query & lvar-bindings)`

Direct query execution, compiling as required.

Accepts a connection, a query, and a additional logic variable bindings. Caching compiled queries through `#'shelving.core/*query-cache*`, compiles the given query and executes it with the given logic variable bindings, returning a sequence of `:find` lvar maps.

## [shelving.core/count-spec](shelving/impl.clj#L212)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/count-rel](shelving/impl.clj#L256)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given relation.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the rel in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.
