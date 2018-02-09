# Shelving (née Tupelov)
<img align="right" src="./etc/shelving.jpg" width=300/>

> Shelving; Noun;
>
> Collective form of Shelf; a thin slab of wood, metal, etc., fixed horizontally to a wall or in a
> frame, for supporting objects.

Before you can have [Stacks](https://github.com/arrdem/stacks), you need shelving for the books.

## Manifesto

There's already plenty of prior art in the Clojure ecosystem for in-memory
databases. [datascript](https://github.com/tonsky/datascript) and
[pldb](https://github.com/clojure/core.logic/wiki/Features) pop to mind, among others. These tools
optimize for database-like query access over in-memory structures and don't provide concrete
serialization stories.

For small applications or applications which want to distribute data as resources, traditional
databases which require a central available server are a poor fit.

Shelving is a tool set for trying to implement quick-and-dirty storage layers for your existing
[spec](https://github.com/clojure/spec.alpha)'d data while retaining some of the query niceties that
make ORMs and real databases compelling.

## Usage
For brevity, only selected vars are listed here.
See the docs for complete listings.

- [Basic API](/docs/basic.md) - The core API
  - [shelving.core/open](/docs/basic.md#shelvingcoreopen)
  - [shelving.core/flush](/docs/basic.md#shelvingcoreflush)
  - [shelving.core/close](/docs/basic.md#shelvingcoreclose)
  - [shelving.core/put](/docs/basic.md#shelvingcoreput)
  - [shelving.core/get](/docs/basic.md#shelvingcoreget)
- [Shelf Spec API](/docs/schema.md#schema-api) - Specs describe the structure of values, but must be declared in a schema to associate semantics with that structure.
  - [shelving.core/empty-schema](/docs/schema.md#shelvingcoreemptyschema)
  - [shelving.core/value-spec](/docs/schema.md#shelvingcorevalue-spec)
  - [shelving.core/record-spec](/docs/schema.md#shelvingcorerecord-spec)
- [Shelf Relation API](/docs/rel.md#rel-api) - Rel(ation)s between vals on a shelf.
  Usable to implement query systems and interned values.
  - [shelving.core/shelf-rel](/docs/rel.md#shelvingcoreshelf-rel)
  - [shelving.core/enumerate-rels](/docs/rel.md#shelvingcoreenumerate-rels)
  - [shelving.core/enumerate-rel](/docs/rel.md#shelvingcoreenumerate-rel)
- [UUID helpers](/docs/helpers.md) - Mostly implementation details, but possibly interesting.

See also the [Grimoire v3 case study](/src/dev/clj/grimoire.clj) which motivates much of this work.

### Demo

Shelving includes a "trivial" back end, which provides most of the same behavior as simpledb, along
with the same trade-offs of always keeping everything in memory and using EDN for
serialization. This probably isn't the shelf you want, but it was the first one.

```clj
user> (require '[shelving.core :as sh])
nil
user> (require '[clojure.spec.alpha :as s])
nil
user> (s/def ::foo string?)
:user/foo
user> (s/def ::bar string?)
:user/bar
user> (def schema
        (-> sh/empty-schema
            (sh/value-spec  ::foo)   ;; values are immutable, unique
            (sh/record-spec ::bar))) ;; records are mutable; places
#'user/schema
user> (require '[shelving.trivial-edn :refer [->TrivialEdnShelf]])
nil
user> (def *conn
        (-> (->TrivialEdnShelf schema "demo.edn")
            (sh/open)))
#'user/*conn
user> (sh/put *conn ::foo "my first write")
#uuid "086c317d-9957-56ec-87e5-6c999c6f9b40"
user> (sh/put *conn ::foo "another write")
#uuid "10befd2c-0a4b-5aed-a0eb-afcac7004643"
user> (sh/enumerate-spec *conn ::foo)
(#uuid "086c317d-9957-56ec-87e5-6c999c6f9b40" #uuid "10befd2c-0a4b-5aed-a0eb-afcac7004643")
user> (sh/put *conn ::foo "another write")
#uuid "10befd2c-0a4b-5aed-a0eb-afcac7004643"
user> (sh/enumerate-spec *conn ::foo)
(#uuid "086c317d-9957-56ec-87e5-6c999c6f9b40" #uuid "10befd2c-0a4b-5aed-a0eb-afcac7004643")
user> (sh/put *conn ::bar "some text")
#uuid "78ac02ce-6fe8-426a-86c4-a9594339fd38"
user> (sh/put *conn ::bar *1 "some other text")
#uuid "78ac02ce-6fe8-426a-86c4-a9594339fd38"
user> (sh/get *conn ::bar *1)
"some other text"
user>
```

## License

Copyright © 2018 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later
version.
