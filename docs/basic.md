# Basic API

[back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## [shelving.core/put](shelving/core.clj#L64)
 - `(put conn spec val)`
 - `(put conn spec val id)`

Destructuring put.

Enters a record into a shelf according to its spec in the schema, inserting substructures and updating all relevant rel(ation)s.

For shelves storing "records" not "values", the `id` parameter may be used to either control the ID of the record, say for achieving an upsert.

It is an error to specify the ID when inserting into a "value" shelf.

Shelves must implement `#'shelving.impl/put`, which backs this method.

## [shelving.core/get](shelving/core.clj#L89)
 - `(get conn spec record-id)`
 - `(get conn spec record-id not-found)`

Restructuring get.

Recovers a record from a shelf according to spec and ID, returning the given `not-found` sentinel if no such record exists, otherwise returning `nil`.

Shelves must implement `#'shelving.impl/get`, which backs this method.

