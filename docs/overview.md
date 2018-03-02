# Overview

[Back to the index](/README.md#usage)

Shelving leverages the same basic insight behind Alan Dipert's [intension][intension], that is a mapping such as

```clj
{:foo 1
 :bar 2
 :baz 3}
```

is actually a set of ordered pairs

```clj
#{[:foo 1]
  [:bar 2]
  [:baz 3]}
```

In [Datalog][datalog] and other logic database systems, facts such as `parent(Julie, Reid)` can be represented as tuples `("Julie" :parent "Reid")`.
If we give an identifier to a map, such as a `UUID` or a [content hash](https://github.com/replikativ/hasch), we could model the same map above as:

```clj
#{[id :foo 1]
  [id :bar 2]
  [id :baz 3]}
```

the advantage of which is that, so long as `id` is unique to the original source mapping, we can easily talk about queries through arbitrarily nested structures.

Unfortunately the idea of `(${ID} ${REL} ${ID})` is less than ideal.
Particularly, it fails to communicate the idea of what we're relating to what - type information if you will or at least semantic context for the relation.

The key idea behind shelving is that `clojure.spec(.alpha)` gives us an interesting alternative to talking about the key structure - we can leverage specs to think about structures and their relations.
To re-visit the same example yet again, we could use `clojure.spec` to describe the structure as such -

```clj
(require '[clojure.spec.alpha :as s])

(s/def ::foo pos-int?)

(s/def ::bar pos-int?)

(s/def ::baz pos-int?)

(s/def ::example
  (s/keys :req-un [::foo ::bar ::baz]))
```

which enables us to consider the set of tuples as relating from the spec `::example` to some other spec particularly.
This allows us to preserve spec (type) information in our relations by considering relations to be from one spec to another.
So our map example above could be encoded as

```clj
#{[id [::example ::foo] 1]
  [id [::example ::bar] 2]
  [id [::example ::baz] 3]}
```

Just like [intension][intension], we can use Datalog to query over these tuples, the relations are just tuples now.
Say we had a bunch of `::example` structures in a connection of some sort, we could use Datalog to say select all the `::foo`s

```clj
(require '[shelving.query :refer [q]])

(def q-fn
  "Produces a function of a connection and here 0 query parameters
  which executes the query producing a sequence of results."
  (q *conn*
      '[:find [[?foo ::foo]]]))

(q-fn *conn*)
;; => ({?foo 1}
;;     {?foo 2}
;;     ...}
```

But this presumes that we have a `*conn*` which we can read and query from.
How do we get there?

## Schemas

Before we can begin to read or write from a shelving store, we need to at least set up a sketch of what it is we'll be reading and writing.
Shelving, like SQL stores, has a notion of a schema.

Schemas serve two purposes - to enumerate the `clojure.spec(.alpha)` structures which we expect to be able to serialize and deserialize, how they relate to each-other, and how we want to respond when serializing un-declared schemas and relations.

A spec may be entered into a schema either as a "value" or a "record".
[`#'shelving.core/value-spec`](/docs/schema.md#shelvingcorevalue-spec) enters a new spec into a schema, producing a new schema which will treat values of that spec as well values - that is they can be written once, read forever and updated never.
[`#'shelving.core/record-spec`](/docs/schema.md#shelvingcorerecord-spec) is similar in that it enters a new spec into the schema, but record specs have update-in-place semantics.
They need not remain constant for all time.

Schemas also describe the ways that specs relate to each other.
[`#'shelving.core/spec-rel`](/docs/schema.md#shelvingcorespec-rel) enables us to update the schema by stating that we'll allow elements of one spec relate to those of another.
In our example after all, we want to be able to relate `::example` to all of `::foo`, `::bar` and `::baz`.

So lets set up our shelf.

```clj
(require '[shelving.core :as sh])

(def *schema
  (-> sh/empty-schema
      (sh/value-spec ::foo)
      (sh/value-spec ::bar
      (sh/value-spec ::baz)
      (sh/value-spec ::example)
      (sh/spec-rel [::example ::foo])
      (sh/spec-rel [::example ::bar])
      (sh/spec-rel [::example ::baz])))
```

While we can manually enumerate all the specs we want to use in our schema, doing so becomes tiresome.
In fact, it isn't possible if we want to be able to leverage multi-specs.

Schemas are immutable, but shelving stores support live schema extension.
We can configure our schema to allow for dynamic extension both to new specs and to new records.
[`#'shelving.core/automatic-specs`](/docs/schema.md#shelvingcoreautomatic-specs) and [`#'shelving.core/automatic-rels`](/docs/schema.md#shelvingcoreautomatic-rels) respectively direct Shelving to automatically perform a schema migration to add a spec or a rel when it is encountered the first time.

## Writing

Now that we've got our schema, we can connect to a store of one sort or another for reading and writing.
As of this writing, Shelving ships with a pair of simple, inefficient stores intended mainly to demonstrate that yes the query engine and persistence machinery works.

[datalog]:
https://en.wikipedia.org/wiki/Datalog
[intension]: https://github.com/alandipert/intension
