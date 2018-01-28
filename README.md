# Shelving (née Tupelov)
<img align="right" src="https://github.com/arrdem/shelving/raw/master/etc/shelving.jpg" width=300/>

> Shelving; Noun;
>
> Collective form of Shelf; a thin slab of wood, metal, etc., fixed
> horizontally to a wall or in a frame, for supporting objects.

Before you can have [stacks](https://github.com/arrdem/stacks), you
need shelves for the books.

## Manifesto

There's already plenty of prior art in the Clojure ecosystem for
in-memory
databases. [datascript](https://github.com/tonsky/datascript) and
[pldb](https://github.com/clojure/core.logic/wiki/Features) pop to
mind, among others. These tools optimize for database-like query
access over in-memory structures and don't provide concrete
serialization stories. [simpledb](https://github.com/ibdknox/simpledb)
provided a trivial persistence model but had no features for data
validation or building multiple storage layers.

Shelving is a tool set for implementing quick-and-dirty yet more
general storage layers for structured using
[clojure.spec(.alpha)](https://github.com/clojure/spec.alpha) for data
validation.

### Caveats

Shelves are intended to make a particular set of trade-offs

- Multiple consistent back-ends are valuable
- Validation against a schema or spec is a desirable
- Read and write access is by point (single record) addresses
- Linear scans are acceptable if not the common case for exploring the store

Support for indexes and queries (datalog?) may be added if I can
figure out how to relate them to the spec structure of stored
data. Unfortunately some spec features such as multispecs, regular
expressions and arbitrary predicates make this difficult.

## Usage
- [Basic API](doc/basic.md)
  - [shelving.core/open](doc/basic.md#shelvingcoreopen)
  - [shelving.core/flush](doc/basic.md#shelvingcoreflush)
  - [shelving.core/close](doc/basic.md#shelvingcoreclose)
  - [shelving.core/put](doc/basic.md#shelvingcoreput)
  - [shelving.core/get](doc/basic.md#shelvingcoreget)
- [Schema API](doc/schema.md#schema-api)
  - [shelving.core/empty-schema](doc/schema.md#shelvingcoreemptyschema)
  - [shelving.core/extend-schema](doc/schema.md#shelvingcoreextend-schema)
- [UUID helpers](doc/helpers.md#uuid-helpers)
  - [shelving.core/random-uuid](doc/helpers.md#shelvingcorerandom-uuid)
- [Grimoire v3 case study](src/dev/clj/grimoire.clj)

Shelving includes a "trivial" back end, which provides most of the
same behavior as simpledb, along with the same trade-offs of always
keeping everything in memory and using EDN for serialization. This
probably isn't the shelf you want, but it was the first one.

```clj
user> (require '[shelving.core :as sh])
nil
user> (require '[clojure.spec.alpha :as s])
nil
user> (s/def ::foo string?)
:user/foo
user> (def schema
        (-> sh/empty-schema
            ;; Using content hashing for ID generation
            (sh/extend-schema ::foo sh/texts->sha-uuid)))
#'user/schema
user> (require '[shelving.trivial-edn :refer [->TrivialEdnShelf]])
nil
user> (def *conn
        (-> (->TrivialEdnShelf schema "demo.edn")
            (sh/open)))
#'user/*conn
user> (sh/put *conn ::foo "my first write")
#uuid "33a65680-b734-fec6-bd92-1cb7df6caacf"
user> (sh/put *conn ::foo "another write")
#uuid "d47453e5-4611-a98e-03cb-d151e644a286"
user> (sh/enumerate *conn)
(:user/foo)
user> (sh/enumerate *conn ::foo)
(#uuid "33a65680-b734-fec6-bd92-1cb7df6caacf" #uuid "d47453e5-4611-a98e-03cb-d151e644a286")
user> (map (partial sh/get *conn ::foo) (sh/enumerate *conn ::foo))
("my first write" "another write")
user> (sh/put *conn ::foo #uuid "33a65680-b734-fec6-bd92-1cb7df6caacf" "an overwrite")
#uuid "33a65680-b734-fec6-bd92-1cb7df6caacf"
user> (sh/get *conn ::foo #uuid "33a65680-b734-fec6-bd92-1cb7df6caacf")
"an overwrite"
user> 
```

## License

Copyright © 2018 Reid "arrdem" McKenzie

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
