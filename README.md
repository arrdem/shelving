# Shelving (née Tupelov)
<img align="right" src="./etc/shelving.jpg" width=300/>

> Shelving; Noun;
>
> Collective form of Shelf; a thin slab of wood, metal, etc., fixed horizontally to a wall or in a
> frame, for supporting objects.

Before you can have [Stacks](https://github.com/arrdem/stacks), you need shelving for the books.

## Manifesto

There's already plenty of prior art in the Clojure ecosystem for in-memory databases.
[datascript](https://github.com/tonsky/datascript), [pldb](https://github.com/clojure/core.logic/wiki/Features) and [intension](https://github.com/alandipert/intension) pop to mind, among others.
These tools optimize for database-like query access over in-memory structures and don't provide concrete serialization stories.

For small applications which just need an initial persistence system, or applications which want to distribute data as resources, traditional databases which require a central available server are a poor fit.

Shelving is a tool set for trying to implement quick-and-dirty storage layers for your existing [`clojure.spec(.alpha)`](https://github.com/clojure/spec.alpha)'d data while retaining some of the query niceties that make ORMs and real databases compelling.

## Concepts
- [Design Overview](/docs/overview.md) - An overview of Shelving and its concepts.
- [Basic API](/docs/basic.md) - The core read and write API exposed to users.
  - [shelving.core/open](/docs/basic.md#shelvingcoreopen)
  - [shelving.core/close](/docs/basic.md#shelvingcoreclose)
  - [shelving.core/put-spec](/docs/basic.md#shelvingcoreput-spec)
  - [shelving.core/get-spec](/docs/basic.md#shelvingcoreget-spec)
- [Shelf Spec API](/docs/schema.md) - Specs describe the structure of values, but must be declared in a schema to associate semantics with that structure.
  - [shelving.core/empty-schema](/docs/schema.md#shelvingcoreemptyschema)
  - [shelving.core/value-spec](/docs/schema.md#shelvingcorevalue-spec)
  - [shelving.core/record-spec](/docs/schema.md#shelvingcorerecord-spec)
- [Shelf Relation API](/docs/rel.md) - Rel(ation)s between vals on a shelf. Used to implement the query system and interned values.
  - [shelving.core/shelf-rel](/docs/rel.md#shelvingcoreshelf-rel)
  - [shelving.core/enumerate-rels](/docs/rel.md#shelvingcoreenumerate-rels)
  - [shelving.core/enumerate-rel](/docs/rel.md#shelvingcoreenumerate-rel)
- [Shelf Query API](/docs/queries.md) - A Datalog query compiler & engine.
  - [shelving.query/q](/docs/queries.md#shelvingqueryq)
- [Implementer's API](/docs/impl.md) - The API between Shelving and Shelf implementations.

See also the [Grimoire v3 case study](/src/dev/clj/grimoire.clj) which motivates much of this work.

## License

Copyright © 2018 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later
version.
