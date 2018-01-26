# Schema API

Shelving leverages clojure.spec to achieve data validation. Schemas
are a tool for talking about the set of specs which a shelf expects to
be able to round-trip. At present, schemas also provide an interface
for defining the ID generation behavior used for a given spec.

Shelves are expected to persist and check their schemas across close
and re-open.

In the future the schema structure may be used to define indices.

## shelving.core/empty-schema

The empty schema.

## shelving.core/extend-schema
- `(extend-schema schema spec id-fn & opts)`

Adds a spec to the schema, returning an updated schema. The spec must
be accompanied with a function from a record conforming to that spec
to a UUID. In the future other options may be supported.
