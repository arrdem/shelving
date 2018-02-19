# Basic API

[back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## [shelving.core/open](shelving/core.clj#L41)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

## [shelving.core/flush](shelving/core.clj#L53)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/close](shelving/core.clj#L67)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## [shelving.core/get](shelving/core.clj#L82)
 - `(get conn spec record-id)`
 - `(get conn spec record-id not-found)`

Fetches a record from a shelf by its spec and ID.

Returns the record if it exists, otherwise returning the user-provided `not-found` value, taken to be `nil` by default.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/has?](shelving/core.clj#L98)
 - `(has? conn spec record-id)`

Indicates whether a shelf has a record of a spec.

Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise must return `false`.

Implementations may provide alternate implementations of this method.

## [shelving.core/put](shelving/core.clj#L116)
 - `(put conn spec val)`
 - `(put conn spec id val)`

Enters a record into a shelf according to its spec in the schema, inserting substructures and updating all relevant rel(ation)s.

For shelves storing "records" not "values", the `id` parameter may be used to either control the ID of the record, say for achieving an upsert.

It is an error to specify the ID when inserting into a "value" shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/schema](shelving/core.clj#L138)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

