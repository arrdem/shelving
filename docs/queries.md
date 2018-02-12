# Queries API

The searching API and helpers designed to support it.

## [shelving.core/count-spec](/src/main/clj/shelving/core.clj#L157)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns at least an upper bound on the cardinality of a given spec.

Implementations of this method should be near constant time and should not require realizing the relation in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/count-rel](/src/main/clj/shelving/core.clj#L539)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns at least an upper bound on the cardinality of a given relation.

Implementations of this method should be near constant time and should not require realizing the relation in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

