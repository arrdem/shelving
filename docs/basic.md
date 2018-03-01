# Basic API

[back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## [shelving.core/open](shelving/impl.clj#L29)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

## [shelving.core/flush](shelving/impl.clj#L41)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/close](shelving/impl.clj#L55)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## [shelving.core/enumerate-specs](shelving/impl.clj#L185)
 - `(enumerate-specs conn)`

Enumerates all the known specs.

Shelves may provide alternate implementations of this method.

## [shelving.core/put-spec](shelving/core.clj#L144)
 - `(put-spec conn spec val)`
 - `(put-spec conn spec id val)`

Destructuring put.

Enters a record into a shelf according to its spec in the schema, inserting substructures and updating all relevant rel(ation)s.

For shelves storing "records" not "values", the `id` parameter may be used to either control the ID of the record, say for achieving an upsert.

It is an error to specify the ID when inserting into a "value" shelf.

Shelves must implement `#'shelving.impl/put`, which backs this method.

## [shelving.core/get-spec](shelving/core.clj#L172)
 - `(get-spec conn spec record-id)`
 - `(get-spec conn spec record-id not-found)`

Restructuring get.

Recovers a record from a shelf according to spec and ID, returning the given `not-found` sentinel if no such record exists, otherwise returning `nil`.

Shelves must implement `#'shelving.impl/get`, which backs this method.

## [shelving.core/has?](shelving/impl.clj#L108)
 - `(has? conn spec record-id)`

Indicates whether a shelf has a record of a spec.

Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise must return `false`.

Implementations may provide alternate implementations of this method.


