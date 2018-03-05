# Schema API

[Back to the index](/README.md#usage)

Shelving leverages `clojure.spec(.alpha)` to achieve data validation and describe the structure of
data. Schemas are a tool for talking about the set of specs which a shelf expects to be able to
round-trip.

A Shelving schema consists of all the specs for data which may be stored, and the
[rel(ation)s](/doc/rel.md) which connect them.

Shelves are expected to persist and check their schemas across close and re-open.

There are two kinds of supported specs - "value" specs and "record" specs.

In traditional databases, one stores <span name="records">**"records"**</span> - places with unique
identifiers which may be updated. Records can be inserted, updated and removed.

In keeping with Clojure's focus on data, Shelving also supports content or <span name="values">**"value"**</span>
storage of immutable objects. Values can only be inserted.

## [shelving.core/empty-schema](shelving/schema.clj#L32)

The empty Shelving schema.

Should be used as the basis for all user-defined schemas.

Contains no specs, no rel(ation)s, and allows the automatic creation of neither specs nor rels.

## [shelving.core/value-spec](shelving/schema.clj#L92)
 - `(value-spec schema spec & {:as opts})`

Enters a new spec and its subspecs into the schema, returning a new schema. The given spec and its subspecs are all entered as "value" specs which have value identity and cannot be updated. Rel(ations) between all inserted specs and their subspecs are automatically added.

Values are addressed by a content hash derived ID, are unique and cannot be deleted or updated.

Values may be related to other values via schema rel(ation)s. Records may relate to values, but values cannot relate to records except through reverse lookup on a record to value relation.

## [shelving.core/record-spec](shelving/schema.clj#L133)
 - `(record-spec schema spec & {:as opts})`

Enters a new spec and its subspecs spec into a schema, returning a new schema. The given spec is inserted as "record" spec with update-in-place capabilities, its subspecs are inserted as "value" specs.

Records have traditional place semantics and are identified by randomly generated IDs, rather than the structural semantics ascribed to values.

## [shelving.core/automatic-specs](shelving/schema.clj#L159)
 - `(automatic-specs schema)`
 - `(automatic-specs schema bool)`

Function of a schema, returning a new schema which allows for the automatic addition of specs. Specs added automatically will always be "values".

## [shelving.core/automatic-specs?](shelving/schema.clj#L172)
 - `(automatic-specs? {:keys [automatic-specs?]})`

Function of a schema, indicating whether it allows for the automatic creation of "value" specs.

## [shelving.core/spec-rel](shelving/schema.clj#L288)
 - `(spec-rel schema [from-spec to-spec :as rel-id])`

Enters a rel(ation) into a schema, returning a new schema which will maintain that rel.

rels are identified uniquely by a pair of specs, stating that there exists a unidirectional relation from values conforming to `from-spec` to values conforming to `to-spec`. This relation has an inverse, which maps values of `to-spec` back to the values of `from-spec` which projected to them.

The rel is determined by the `to-fn` which projects values conforming to the `from-spec` to values conforming to `to-spec`. `to-fn` MUST be a pure function.

When inserting with indices, all `to-spec` instances will be inserted into their corresponding tables.

At insertion time, the `to-spec` must exist as a supported shelf.

The `to-spec` MAY NEVER name a "record" shelf. `to-spec` must be a "value" shelf.

## [shelving.core/automatic-rels](shelving/schema.clj#L356)
 - `(automatic-rels schema)`
 - `(automatic-rels schema bool)`

Function of a schema, returning a new schema which allows for the automatic addition of relations. Relations must be between known specs, and may not relate to records.

## [shelving.core/automatic-rels?](shelving/schema.clj#L369)
 - `(automatic-rels? {:keys [automatic-rels?]})`

Predicate indicating whether the schema supports automatic relations.
