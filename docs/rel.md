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

