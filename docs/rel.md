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

Using spec, values [[1]](/docs/schema.md#values) (and records [[1]](/docs/schema.md#records)) can be
decomposed into their component parts, and the resulting fragments related back to each-other.

Consider for example the following spec -

```clj
(s/def ::group    string?)
(s/def ::artifact string?)
(s/def ::version  string?)
(s/def ::package-identifier
  (s/keys :req-un [::group
                   ::artifact
                   ::version]))

(s/valid? ::package-identifier
          {:group "org.clojure"
           :artifact "clojure"
           :version "1.10.0"})
;; => true
```

The `::package-identifier` spec is composed of three string values together in a known
format. Imagine being able to take a collection of such identifiers, break them down to their
components and declaratively ask traditional query questions such as "what groups have a clojure
artifact?" or "what versions of ring are there?".

The relations API provides a way to describe how to decompose a spec into its component values so
that indices can be built for efficient query access.

## [shelving.core/spec-rel](/src/main/clj/shelving/core.clj#L441)
 - `(spec-rel schema [from-spec to-spec :as rel-id] to-fn)`

Enters a rel(ation) into a schema, returning a new schema which will maintain that rel.

rels are identified uniquely by a pair of specs, stating that there exists a unidirectional relation from values conforming to `from-spec` to values conforming to `to-spec`. This relation has an inverse, which maps values of `to-spec` back to the values of `from-spec` which projected to them.

The rel is determined by the `to-fn` which projects values conforming to the `from-spec` to values conforming to `to-spec`. `to-fn` MUST be a pure function.

When inserting with indices, all `to-spec` instances will be inserted into their corresponding tables.

At insertion time, the `to-spec` must exist as a supported shelf.

The `to-spec` MAY NEVER name a "record" shelf. `to-spec` must be a "value" shelf.

## [shelving.core/has-rel?](/src/main/clj/shelving/core.clj#L484)
 - `(has-rel? schema [from-spec to-spec :as rel-id])`

True if and only if both specs in the named rel, and the named rel exist in the schema.

## [shelving.core/is-alias?](/src/main/clj/shelving/core.clj#L500)
 - `(is-alias? schema rel-id)`

True if and only if the schema has the relation (`#'has-rel?`) and the relation is an alias.

## [shelving.core/resolve-alias](/src/main/clj/shelving/core.clj#L515)
 - `(resolve-alias schema rel-id)`

When the schema has a rel (`#'has-rel?`) and it is an alias (`#'is-alias?`) resolve it, returning the directed rel it aliases. Otherwise return the rel.

## [shelving.core/enumerate-rels](/src/main/clj/shelving/core.clj#L527)
 - `(enumerate-rels conn)`

Enumerates all the known rels by ID (their `[from-spec to-spec]` pair). Includes aliases.

Shelves may provide alternate implementation of this method.

## [shelving.core/enumerate-rel](/src/main/clj/shelving/core.clj#L540)
 - `(enumerate-rel conn rel-id)`

Enumerates the `(from-id to-id)` pairs of the given rel(ation).

Shelves must implement this method.

By default throws `me.arrdem.UnimplementedOperationException`.

## [shelving.core/relate-by-id](/src/main/clj/shelving/core.clj#L554)
 - `(relate-by-id conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation), being a pair `[from-spec to-spec]` and the ID of a record of the `from-spec` spec, return a seq of the IDs of records it relates to in `to-spec`.

If the given ID does not exist on the left side of the given relation, an empty seq must be produced.

Note that if the rel `[a b]` was created with `#'spec-rel`, the rel `[b a]` also exists and is the complement of mapping from `a`s to `b`s defined by `[a b]`.

By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full scan of the pairs constituting this relation.

Shelves may provide more efficient implementations of this method.

## [shelving.core/relate-by-value](/src/main/clj/shelving/core.clj#L583)
 - `(relate-by-value conn rel-id spec id)`

**UNSTABLE**: This API will probably change in the future

Given a rel(ation) and a value conforming to the left side of the relation, return a seq of the IDs of records on the right side of the relation (if any) it relates to.

By default uses [`#'enumerate-rel`](#enumerate-rel) to do a full rel scan.

Shelves may provide more efficient implementations of this method.

