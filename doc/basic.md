# Basic API

[back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## shelving.core/open
- `(open config)`

Opens a configuration, producing a readable and writable shelf.

## shelving.core/flush
- `(flush conn)`

Good 'ol `fsync(2)`. Flushes the shelf but doesn't close it.

## shelving.core/close
- `(close conn)`

Closes an open shelf. No attempt is made to define the effect of closing a shelf twice.

## shelving.core/put
- `(put conn spec val)`
- `(put conn spec uuid val)`

Writes a val to the shelf. The spec must name a spec which is legal in the shelf's schema, and which
val conforms to. If no UUID is provided, then the table's ID generation strategy is used.

## shelving.core/get
- `(put conn spec uuid)`

Fetches a record from the shelf by spec and UUID.

## shelving.core/enumerate-specs
- `(enumerate-specs conn)`

Returns a sequence all the specs in the shelf.

## shelving.core/enumerate-records
- `(enumerate-records conn spec)`

Returns a sequence of the UUIDs of the records of that spec in the shelf.
