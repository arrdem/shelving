# Basic API

[back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## [shelving.core/open](/src/main/clj/shelving/core.clj#L40)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

## [shelving.core/flush](/src/main/clj/shelving/core.clj#L52)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/close](/src/main/clj/shelving/core.clj#L66)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## [shelving.core/get](/src/main/clj/shelving/core.clj#L81)
 - `(get conn spec record-id)`

Fetches a record from a shelf by its spec and ID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/put](/src/main/clj/shelving/core.clj#L93)
 - `(put conn spec val)`
 - `(put conn spec id val)`

Enters a record into a shelf according to its spec in the schema, inserting substructures and updating all relevant rel(ation)s.

For shelves storing "records" not "values", the `id` parameter may be used to either control the ID of the record, say for achieving an upsert.

It is an error to specify the ID when inserting into a "value" shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/schema](/src/main/clj/shelving/core.clj#L115)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

