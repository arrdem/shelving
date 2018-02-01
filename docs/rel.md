# Rel(ation) API

[back to the index](/README.md#usage)

`clojure.spec(.alpha)` is a set of tools for describing data by composing descriptions of
substructures. In traditional relational data models, records relate to each-other by referencing
identifiers which are joined against.

In Prolog/Datalog "fact" databases, data is stored as triples of the form `(rel a b)` where `a` and
`b` are data including other relational statements.

One can imagine "destructuring" complex objects with specs by using their specs to describe in the
relational style the superstructure and the substructures. This is basically how relations in
shelving work.

Using spec, values [[1]](/docs/schema.md#values) (and records [[1]](/docs/schema.md#records))

## shelving.core/enumerate-rel
 - `(enumerate-rel conn rel-id)`

Enumerates the `(from-id to-id)` pairs of the given rel(ation).

Shelves must implement this method.

## shelving.core/enumerate-rels
 - `(enumerate-rels conn)`

Enumerates all the known rels by ID (their `[from-spec to-spec]` pair).

Shelves must implement this method.

## shelving.core/spec-rel
 - `(spec-rel schema [from-spec to-spec :as rel-id] to-fn)`

Enters a rel(ation) into a schema, returning a new schema which will maintain that rel.

rels are identified uniquely by a pair of specs, and the relation is determined by the to-fn which projects instances of the `from-spec` to instances of the `to-spec`. `to-fn` MUST be a pure function.

When inserting with indices, all `to-spec` instances will be inserted into their corresponding tables.

At insertion time, the `to-spec` must exist as a supported shelf.

The `to-spec` MAY NEVER name a "record" shelf. `to-spec` must be a "value" shelf.

## shelving.core/get-from-rel-by-id
 - `(get-from-rel-by-id conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and the ID of an record of the from-rel spec, return a seq of the IDs of records it relates to.

By default uses `enumerate-rel-by-id` to do a full rel scan.

Shelves may provide more efficient implementations of this method.

## shelving.core/schema->rels
 - `(schema->rels schema)`

Helper used for converting a schema record to a set of rel(ation)s for storage.
