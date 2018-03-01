# Implementer's API

## [shelving.impl/open](shelving/impl.clj#L29)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

## [shelving.impl/flush](shelving/impl.clj#L41)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/close](shelving/impl.clj#L55)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## [shelving.impl/put-spec](shelving/impl.clj#L70)
 - `(put-spec conn spec id val)`

The "raw" put operation on values. Inserts a fully decomposed value (tuple) into the designated spec, returning the ID at which it was inserted if an ID was not provided.

Users should universally prefer `#'shelving.core/put`. This method is an unprotected implementation detail not for general use.

Note that when inserting into "record" specs, all relations to the updated "record" ID must be invalidated.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/get-spec](shelving/impl.clj#L90)
 - `(get-spec conn spec record-id)`
 - `(get-spec conn spec record-id not-found)`

Fetches a single tuple, being part of a record, from a shelf by its spec and ID.

Returns the record if it exists, otherwise returning the user-provided `not-found` value, taken to be `nil` by default.

Implementation detail of `#'shelving.core/get`, which should be preferred by users.  Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/has?](shelving/impl.clj#L108)
 - `(has? conn spec record-id)`

Indicates whether a shelf has a record of a spec.

Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise must return `false`.

Implementations may provide alternate implementations of this method.

## [shelving.impl/put-rel](shelving/impl.clj#L128)
 - `(put-rel conn spec rel-id from-id to-id)`

The "raw" put operation on relations.

Inserts a `[from rel to]` triple into the data store unconditionally.

Users should universally prefer `#'shelving.core/put`. This method is an unprotected implementation detail not for general use.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/schema](shelving/impl.clj#L148)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/set-schema](shelving/impl.clj#L164)
 - `(set-schema conn schema)`

Attempts to alter the live schema of the connection by applying the given transformer function to the current schema state with any additional arguments.

Implementation detail of `#'shelving.core/alter-schema`, which should be universally preferred.

Returns the schema record for a given connection.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/enumerate-specs](shelving/impl.clj#L185)
 - `(enumerate-specs conn)`

Enumerates all the known specs.

Shelves may provide alternate implementations of this method.

## [shelving.impl/enumerate-spec](shelving/impl.clj#L198)
 - `(enumerate-spec conn spec)`

Enumerates all the known records of a spec by UUID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/count-spec](shelving/impl.clj#L212)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/enumerate-rels](shelving/impl.clj#L230)
 - `(enumerate-rels conn)`

Enumerates all the known rels by ID (their `[from-spec to-spec]` pair). Includes aliases.

Shelves may provide alternate implementation of this method.

## [shelving.impl/enumerate-rel](shelving/impl.clj#L243)
 - `(enumerate-rel conn rel-id)`

Enumerates the `(from-id to-id)` pairs of the given rel(ation).

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/count-rel](shelving/impl.clj#L257)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given relation.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the rel in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.impl/get-rel](shelving/impl.clj#L275)
 - `(get-rel conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and the ID of an record of the from-rel spec, return a seq of the IDs of records it relates to. If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

Note that if the rel `[a b]` was created with `#'spec-rel`, the rel `[b a]` also exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full scan of the pairs constituting this relation.

Shelves may provide more efficient implementations of this method.


