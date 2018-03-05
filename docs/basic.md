# Basic API

[Back to the index](/README.md#usage)

The various shelving implementations define their own mechanisms for constructing
configurations. These operations should be shared by all implementations.

## Connecting

### [shelving.core/open](shelving/impl.clj#L31)
 - `(open config)`

Opens a shelf for reading or writing.

Shelves must implement this method.

### [shelving.core/flush](shelving/impl.clj#L43)
 - `(flush conn)`

Flushes (commits) an open shelf.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/close](shelving/impl.clj#L57)
 - `(close conn)`

Closes an open shelf.

Shelves may implement this method.

By default just flushes.

## Reading & Writing Specs

[Back to the index](/README.md#usage)



### [shelving.core/enumerate-specs](shelving/impl.clj#L184)
 - `(enumerate-specs conn)`

Enumerates all the known specs.

Shelves may provide alternate implementations of this method.

### [shelving.core/put-spec](shelving/core.clj#L147)
 - `(put-spec conn spec val)`
 - `(put-spec conn spec id val)`

Destructuring put.

Enters a record into a shelf according to its spec in the schema, inserting substructures and updating all relevant rel(ation)s.

For shelves storing "records" not "values", the `id` parameter may be used to either control the ID of the record, say for achieving an upsert.

It is an error to specify the ID when inserting into a "value" shelf.

Shelves must implement [`#'shelving.impl/put-spec`](/docs/impl.md#shelvingimplput-spec), which backs this method.

### [shelving.core/get-spec](shelving/core.clj#L174)
 - `(get-spec conn spec record-id)`
 - `(get-spec conn spec record-id not-found)`

Restructuring get.

Recovers a record from a shelf according to spec and ID, returning the given `not-found` sentinel if no such record exists, otherwise returning `nil`.

Shelves must implement [`#'shelving.impl/get-spec`](/docs/impl.md#shelvingimplget-spec), which backs this method.

### [shelving.core/has?](shelving/impl.clj#L110)
 - `(has? conn spec record-id)`

Indicates whether a shelf has a record of a spec.

Returns `true` if and only if the shelf contains a record if the given spec and ID.  Otherwise must return `false`.

Implementations may provide alternate implementations of this method.

### [shelving.core/count-spec](shelving/impl.clj#L211)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/enumerate-spec](shelving/impl.clj#L197)
 - `(enumerate-spec conn spec)`

Enumerates all the known records of a spec by UUID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## Reading & Writing Relations

[Back to the index](/README.md#usage)


### [shelving.core/put-rel](shelving/impl.clj#L129)
 - `(put-rel conn spec rel-id from-id to-id)`

The "raw" put operation on relations.

Inserts a `[from rel to]` triple into the data store unconditionally.

Users should universally prefer [`#'shelving.core/put-spec`](/docs/basic.md#shelvingcoreput-spec). This method is an unprotected implementation detail not for general use.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/get-rel](shelving/impl.clj#L272)
 - `(get-rel conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and the ID of an record of the from-rel spec, return a seq of the IDs of records it relates to. If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

Note that if the rel `[a b]` was created with [`#'shelving.core/spec-rel`](/docs/schema.md#shelvingcorespec-rel), the rel `[b a]` also exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

By default uses [`#'shelving.impl/enumerate-rel`](/docs/impl.md#shelvingimplenumerate-rel) to do a full scan of the pairs constituting this relation.

Shelves may provide more efficient implementations of this method.

## Querying

[Back to the index](/README.md#usage)

### [shelving.core/q](shelving/query.clj#L116)
 - `(q conn query)`

**UNSTABLE**: This API will probably change in the future

Cribbing from Datomic's q operator here.  `find` is a sequence of symbols naming logic variables (by convention having the `?-` prefix) and `[:from spec lvar]` spec statements. `find` indicates what logic variables should be realized to values and produced as query results.

`where` is a sequence of rel "constraint" triples. Constraint triples must fit one of four forms: - `[lvar rel-id   lvar]` - `[lvar rel-id   const]` - `[lvar rel-spec lvar]` - `[lvar rel-spec const]`

for `lvar` existentially being a logic variable, `rel-id` being a valid `[spec spec]` directed relation pair, `rel-spec` being the spec of the right hand side of a relation; the left hand side being type inferred and const being any constant value for which there exists a meaningful content hash.

`in` may be an inline or explicit sequence of logic variables, which may be annotated with a spec in the same `[:from <spec> <lvar>]` notation as supported by `find`. In parameters are compiled to arguments of the produced query function in the order the are given lexically.

Evaluation precedes by attempting to unify the logic variables over the specified relations.

Compiles and returns a new function of a connection and `in` parameters which will execute the compiled query.

Query compilation is somewhat expensive so it's suggested that queries be compiled once and then parameterized repeatedly.

### [shelving.core/q!](shelving/query.clj#L170)
 - `(q! conn query & args)`

**UNSTABLE**: This API will probably change in the future

Same as [`#'shelving.core/q`](/docs/basic.md#shelvingcoreq) but directly accepts arguments and executes the compiled query.

Queries are cached to avoid repeated compilation.

## Connection Schemas

[Back to the index](/README.md#usage)

### [shelving.core/schema](shelving/impl.clj#L148)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

### [shelving.core/alter-schema](shelving/core.clj#L191)
 - `(alter-schema conn f & args)`

Attempts alter the schema of a live connection.

I CANNOT EMPHASIZE ENOUGH HOW DANGEROUS THIS COULD BE.

1. Gets the live schema from the connection 2. Attempts to apply the schema altering function 3. Attempts to validate that the produced new schema is compatible 4. Irreversibly writes the new schema to the store

Applies the given transformer function to the current live schema and the given arguments. Checks that the resulting schema is compatible with the existing schema (eg. strictly additive), sending the schema change to the connection only if compatibility checking succeeds.

Returns the new schema.

Throws `me.arrdem.shelving.SchemaMigrationexception` without impacting the connection or its backing store if schema incompatibilities are detected.
