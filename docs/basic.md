# Basic API

[Back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## Connecting

### [shelving.core/open](../src/main/clj/shelving/impl.clj#L32)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

### [shelving.core/flush](../src/main/clj/shelving/impl.clj#L44)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/close](../src/main/clj/shelving/impl.clj#L58)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## Reading & Writing Specs

[Back to the index](/README.md#usage)



### [shelving.core/enumerate-specs](../src/main/clj/shelving/impl.clj#L185)
 - `(enumerate-specs conn)`

Enumerates all the known specs.

Shelves may provide alternate implementations of this method.

### [shelving.core/put-spec](../src/main/clj/shelving/core.clj#L186)
 - `(put-spec conn spec val)`
 - `(put-spec conn spec id val)`

Destructuring put.

Enters a record into a shelf according to its spec in the schema, inserting substructures and updating all relevant rel(ation)s.

For shelves storing "records" not "values", the `id` parameter may be used to either control the ID of the record, say for achieving an upsert.

It is an error to specify the ID when inserting into a "value" shelf.

Shelves must implement [`#'shelving.impl/put-spec`](impl.md#shelvingimplput-spec), which backs this method.

### [shelving.core/get-spec](../src/main/clj/shelving/core.clj#L212)
 - `(get-spec conn spec record-id)`
 - `(get-spec conn spec record-id not-found)`

Restructuring get.

Recovers a record from a shelf according to spec and ID, returning the given `not-found` sentinel if no such record exists, otherwise returning `nil`.

Shelves must implement [`#'shelving.impl/get-spec`](impl.md#shelvingimplget-spec), which backs this method.

### [shelving.core/has?](../src/main/clj/shelving/impl.clj#L111)
 - `(has? conn spec record-id)`

Indicates whether a shelf has a record of a spec.

Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise must return `false`.

Implementations may provide alternate implementations of this method.

### [shelving.core/count-spec](../src/main/clj/shelving/impl.clj#L212)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/enumerate-spec](../src/main/clj/shelving/impl.clj#L198)
 - `(enumerate-spec conn spec)`

Enumerates all the known records of a spec by UUID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## Reading & Writing Relations

[Back to the index](/README.md#usage)


### [shelving.core/put-rel](../src/main/clj/shelving/impl.clj#L130)
 - `(put-rel conn spec rel-id from-id to-id)`

The "raw" put operation on relations.

Inserts a `[from rel to]` triple into the data store unconditionally.

Users should universally prefer [`#'shelving.core/put-spec`](#shelvingcoreput-spec). This method is an unprotected implementation detail not for general use.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/get-rel](../src/main/clj/shelving/impl.clj#L273)
 - `(get-rel conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and the ID of an record of the from-rel spec, return a seq of the IDs of records it relates to. If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

Note that if the rel `[a b]` was created with [`#'shelving.core/spec-rel`](schema.md#shelvingcorespec-rel), the rel `[b a]` also exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

By default uses [`#'shelving.impl/enumerate-rel`](impl.md#shelvingimplenumerate-rel) to do a full scan of the pairs constituting this relation.

Shelves may provide more efficient implementations of this method.

## Querying

[Back to the index](/README.md#usage)

### [shelving.core/q](../src/main/clj/shelving/impl.clj#L300)
 - `(q conn query)`

Query compilation.

Given a connection and a query datastructure, return a function of a connection and 0 or more positional logic variable bindings per the `:in` clause of the compiled query. Query functions return sequences of maps from logic variables to values. Each produced map must contain all lvars occurring in the query's `:find` clause.

See the datalog documentation for a full description of the supported query form.

By default, `null` is used which attempts to compile queries against only the published `shelving.impl` API. Implementers may choose to provide their own query compilation pipelines but are warned that they do so wholesale. Query compilation is currently not modular.

### [shelving.core/q!](../src/main/clj/shelving/core.clj#L289)
 - `(q! conn query & lvar-bindings)`

Direct query execution, compiling as required.

Accepts a connection, a query, and a additional logic variable bindings. Caching compiled queries through `#'shelving.core/*query-cache*`, compiles the given query and executes it with the given logic variable bindings, returning a sequence of `:find` lvar maps.

## Connection Schemas

[Back to the index](/README.md#usage)

### [shelving.core/schema](../src/main/clj/shelving/impl.clj#L149)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/alter-schema](../src/main/clj/shelving/core.clj#L237)
 - `(alter-schema conn f & args)`

Attempts alter the schema of a live connection.

I CANNOT EMPHASIZE ENOUGH HOW DANGEROUS THIS COULD BE.

1. Gets the live schema from the connection 2. Attempts to apply the schema altering function 3. Attempts to validate that the produced new schema is compatible 4. Irreversibly writes the new schema to the store

Applies the given transformer function to the current live schema and the given arguments. Checks that the resulting schema is compatible with the existing schema (eg. strictly additive), sending the schema change to the connection only if compatibility checking succeeds.

Returns the new schema.

Throws `me.arrdem.shelving.SchemaMigrationexception` without impacting the connection or its backing store if schema incompatibilities are detected.
