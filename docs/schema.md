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

