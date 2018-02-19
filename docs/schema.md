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

## [shelving.core/enumerate-specs](shelving/core.clj#L154)
 - `(enumerate-specs conn)`

Enumerates all the known specs.

Shelves may provide alternate implementations of this method.

## [shelving.core/enumerate-spec](shelving/core.clj#L167)
 - `(enumerate-spec conn spec)`

Enumerates all the known records of a spec by UUID.

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/empty-schema](shelving/core.clj#L261)

The empty Shelving schema.

Should be used as the basis for all user-defined schemas.

## [shelving.core/has-spec?](shelving/core.clj#L293)
 - `(has-spec? schema spec)`

Helper used for preconditions.

## [shelving.core/is-value?](shelving/core.clj#L306)
 - `(is-value? schema spec)`

True if and only if the spec exists and is a `:value` spec.

## [shelving.core/value-spec](shelving/core.clj#L319)
 - `(value-spec schema spec & {:as opts})`

Enters a new "value" spec into a schema, returning a new schema.

Values are addressed by a content hash derived ID, are unique and cannot be deleted or updated.

Values may be related to other values via schema rel(ation)s. Records may relate to values, but values cannot relate to records except through reverse lookup on a record to value relation.

## [shelving.core/is-record?](shelving/core.clj#L349)
 - `(is-record? schema spec)`

True if and only if the spec exists and is a record spec.

## [shelving.core/record-spec](shelving/core.clj#L362)
 - `(record-spec schema spec & {:as opts})`

Enters a new "record" spec into a schema, returning a new schema.

Records have traditional place semantics and are identified by randomly generated IDs, rather than the structural semantics ascribed to values.

## [shelving.core/id-for-record](shelving/core.clj#L389)
 - `(id-for-record schema spec val)`

Returns the `val`'s identifying UUID according to the spec's schema entry.

## [shelving.core/schema->specs](shelving/core.clj#L408)
 - `(schema->specs schema)`

Helper used for converting a schema record to a fully resolved spec serialization for storage.

## [shelving.core/check-specs!](shelving/core.clj#L430)
 - `(check-specs! persisted live)`

Helper for use in checking compatibility between database persistable specs. Because proving equivalence on specs is basically impossible, we'll start with equality.

