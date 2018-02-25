# Basic API

[back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

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

## [shelving.core/alter-schema](shelving/core.clj#L190)
 - `(alter-schema conn f & args)`

Attempts alter the schema of a live connection.

I CANNOT EMPHASIZE ENOUGH HOW DANGEROUS THIS COULD BE.

1. Gets the live schema from the connection 2. Attempts to apply the schema altering function 3. Attempts to validate that the produced new schema is compatible 4. Irreversibly writes the new schema to the store

Applies the given transformer function to the current live schema and the given arguments. Checks that the resulting schema is compatible with the existing schema (eg. strictly additive), sending the schema change to the connection only if compatibility checking succeeds.

Returns the new schema.

Throws `me.arrdem.shelving.SchemaMigrationexception` without impacting the connection or its backing store if schema incompatibilities are detected.

