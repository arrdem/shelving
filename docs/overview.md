# Overview

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
[`shelving.core/value-spec`](/docs/schema)

[datalog]: https://en.wikipedia.org/wiki/Datalog
[intension]: https://github.com/alandipert/intension
