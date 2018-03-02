# Schema API

[back to the index](/README.md#usage)

Shelving leverages `clojure.spec(.alpha)` to achieve data validation and describe the structure of
data. Schemas are a tool for talking about the set of specs which a shelf expects to be able to
round-trip.

A Shelving Schema consists of all the specs for data which may be stored, and the
[rel(ation)s](/doc/rel.md) which connect them.

Shelves are expected to persist and check their schemas across close and re-open.

There are two kinds of supported specs - "value" specs and "record" specs.

In traditional databases, one stores <span name="records">**"records"**</span> - places with unique
identifiers which may be updated. Records can be inserted, updated and removed.

In keeping with Clojure's focus on data, Shelving also supports content or <span name="values">**"value"**</span>
storage of immutable objects. Values can only be inserted.

## [shelving.core/enumerate-rels](shelving/impl.clj#L230)
 - `(enumerate-rels conn)`

Enumerates all the known rels by ID (their `[from-spec to-spec]` pair). Includes aliases.

Shelves may provide alternate implementation of this method.

## [shelving.core/put-rel](shelving/impl.clj#L128)
 - `(put-rel conn spec rel-id from-id to-id)`

The "raw" put operation on relations.

Inserts a `[from rel to]` triple into the data store unconditionally.

Users should universally prefer `#'shelving.core/put`. This method is an unprotected implementation detail not for general use.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/get-rel](shelving/impl.clj#L275)
 - `(get-rel conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and the ID of an record of the from-rel spec, return a seq of the IDs of records it relates to. If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

Note that if the rel `[a b]` was created with `#'spec-rel`, the rel `[b a]` also exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full scan of the pairs constituting this relation.

Shelves may provide more efficient implementations of this method.

## [shelving.core/enumerate-rel](shelving/impl.clj#L243)
 - `(enumerate-rel conn rel-id)`

Enumerates the `(from-id to-id)` pairs of the given rel(ation).

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/count-rel](shelving/impl.clj#L257)
 - `(count-rel conn rel-id)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given relation.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the rel in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/enumerate-spec](shelving/impl.clj#L198)
 - `(enumerate-spec conn spec)`

Enumerates all the known records of a spec by UUID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/count-spec](shelving/impl.clj#L212)
 - `(count-spec conn spec)`

**UNSTABLE**: This API will probably change in the future

Returns an upper bound on the cardinality of a given spec.

The bound should be as tight as possible if not precise. Implementations of this method should be near constant time and should not require realizing the spec in question.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/schema](shelving/impl.clj#L148)
 - `(schema conn)`

Returns the schema record for a given connection.

Schemas are fixed when the connection is opened.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/alter-schema](shelving/core.clj#L193)
 - `(alter-schema conn f & args)`

Attempts alter the schema of a live connection.

I CANNOT EMPHASIZE ENOUGH HOW DANGEROUS THIS COULD BE.

1. Gets the live schema from the connection 2. Attempts to apply the schema altering function 3. Attempts to validate that the produced new schema is compatible 4. Irreversibly writes the new schema to the store

Applies the given transformer function to the current live schema and the given arguments. Checks that the resulting schema is compatible with the existing schema (eg. strictly additive), sending the schema change to the connection only if compatibility checking succeeds.

Returns the new schema.

Throws `me.arrdem.shelving.SchemaMigrationexception` without impacting the connection or its backing store if schema incompatibilities are detected.


