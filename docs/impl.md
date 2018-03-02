# Implementer's API

[Back to the index](/README.md#usage)

Shelving uses a two part API - the [consumer API](/docs/basic.md) is intended to serve the needs of users who want to read and write data.
The implementer's API contains the raw machinery required to build a shelving store.

## Model Overview

Shelving's fundamental model of a storage layer is that of a key value store.
k/v stores are a simple abstraction, for which a large number of high quality implementations exist for instance [redis](https://redis.io/), [rocksdb](http://rocksdb.org/) and a host of others.
k/v stores are especially attractive in the context of a project like Shelving, because most values are expected to be write once leaving great opportunity for replication and caching rather than having to design for random access writes which requiring central coordination.

At any point in time, a Shelving storage layer is expected to present three distinct "kinds" of information:

1. The current schema for the store, in the form of `clojure.spec` specs as deserializing clients will understand it.
   This value will be requested (and checked) by connecting clients so they can verify their own integrity, and may be altered to achieve strict extension of the data model

2. The vales in the store.
   Values are identified by a spec and a UUID.
   Compound values which have a `s/keys` spec are expected to completely decompose and must be reconstituted from their relations.
   Other values such as strings and integers are expected to serialize and deserialize in the typical key/value pattern.

3. The relations in the store.
   Several models are used for this, none of which need reflect the implementation's machinery.
   Relations are exposed as a sequence of `from to` UUID pairs, identified by a `[from to]` relation pair.
   Primarily are consumed as a sequence of "to" UUIDs given a `[from to]` relation and an id.

## Connection Operations

[Back to the index](/README.md#usage)

No fundamental connection behavior is specified or expected.
`open` exists to differentiate between a connection's description and an active connection.
`close` is expected to close an open connection, freeing any related resources on both the client and any implementation or server.

### [shelving.impl/open](shelving/impl.clj#L29)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

### [shelving.impl/flush](shelving/impl.clj#L41)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/close](shelving/impl.clj#L55)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## Schema Operations

[Back to the index](/README.md#usage)

The database schema describes what kinds of values it expects to store.
Clients MAY NOT write to relations or specs which have not been entered into the schema.
[`#'shelving.core/put-spec`](/docs/basic.md#shelvingcoreput-spec), the intentional write API, has support for automatically manipulating and extending the store's schema.

### [shelving.impl/schema](shelving/impl.clj#L146)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/set-schema](shelving/impl.clj#L162)
 - `(set-schema conn schema)`

Attempts to alter the live schema of the connection by applying the given transformer function to the current schema state with any additional arguments.

Implementation detail of [`#'shelving.core/alter-schema`](/docs/basic.md#shelvingcorealter-schema), which should be universally preferred. This method is an unprotected implementation detail not for general use.

Returns the schema record for a given connection.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## Spec Operations

[Back to the index](/README.md#usage)

These operations are used to manipulate specs, which approximate single data storage columns in a database.
Specs may be written to, read from, scanned and counted.
The counting operation is used to attempt to optimize query implementations.

Deletion of spec entries is not supported.

### [shelving.impl/enumerate-specs](shelving/impl.clj#L182)
 - `(enumerate-specs conn)`

Enumerates all the known specs.

Shelves may provide alternate implementations of this method.

### [shelving.impl/enumerate-spec](shelving/impl.clj#L195)
 - `(enumerate-spec conn spec)`

Enumerates all the known records of a spec by UUID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/count-spec](shelving/impl.clj#L209)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/put-spec](shelving/impl.clj#L70)
 - `(put-spec conn spec id val)`

The "raw" put operation on values. Inserts a fully decomposed value (tuple) into the designated spec, returning the ID at which it was inserted if an ID was not provided.

Users should universally prefer [`#'shelving.core/put-spec`](/docs/basic.md#shelvingcoreput-spec). This method is an unprotected implementation detail not for general use.

Note that when inserting into "record" specs, all relations to the updated "record" ID must be invalidated.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/get-spec](shelving/impl.clj#L89)
 - `(get-spec conn spec record-id)`
 - `(get-spec conn spec record-id not-found)`

Fetches a single tuple, being part of a record, from a shelf by its spec and ID.

Returns the record if it exists, otherwise returning the user-provided `not-found` value, taken to be `nil` by default.

Implementation detail of [`#'shelving.core/get-spec`](/docs/basic.md#shelvingcoreget-spec), which should be preferred by users. This method is an unprotected implementation detail not for general use.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/has?](shelving/impl.clj#L108)
 - `(has? conn spec record-id)`

Indicates whether a shelf has a record of a spec.

Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise must return `false`.

Implementations may provide alternate implementations of this method.

## Relation operations

[Back to the index](/README.md#usage)

Relations well relate the IDs of values of one spec to the IDs of values of another spec.
Like the values themselves, the relations can be read from, written to and counted.

Deletion of relations is not supported.

### [shelving.impl/enumerate-rels](shelving/impl.clj#L226)
 - `(enumerate-rels conn)`

Enumerates all the known rels by ID (their `[from-spec to-spec]` pair). Includes aliases.

Shelves may provide alternate implementation of this method.

### [shelving.impl/enumerate-rel](shelving/impl.clj#L239)
 - `(enumerate-rel conn rel-id)`

Enumerates the `(from-id to-id)` pairs of the given rel(ation).

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/count-rel](shelving/impl.clj#L253)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given relation.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the rel in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/put-rel](shelving/impl.clj#L127)
 - `(put-rel conn spec rel-id from-id to-id)`

The "raw" put operation on relations.

Inserts a `[from rel to]` triple into the data store unconditionally.

Users should universally prefer [`#'shelving.core/put-spec`](/docs/basic.md#shelvingcoreput-spec). This method is an unprotected implementation detail not for general use.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.impl/get-rel](shelving/impl.clj#L270)
 - `(get-rel conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and the ID of an record of the from-rel spec, return a seq of the IDs of records it relates to. If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

Note that if the rel `[a b]` was created with [`#'shelving.core/spec-rel`](/docs/schema.md#shelvingcorespec-rel), the rel `[b a]` also exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

By default uses [`#'shelving.impl/enumerate-rel`](/docs/impl.md#shelvingimplenumerate-rel) to do a full scan of the pairs constituting this relation.

Shelves may provide more efficient implementations of this method.
