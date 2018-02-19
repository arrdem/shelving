## [shelving.walk/*trace-walk*](shelving/walk.clj#L15)

**UNSTABLE**: This API will probably change in the future

Dynamic variable controlling whether `#'walk-with-spec` logs its progress to `*err*`.

## [shelving.walk/*trace-walk*](shelving/walk.clj#L15)

**UNSTABLE**: This API will probably change in the future

Dynamic variable controlling whether `#'walk-with-spec` logs its progress to `*err*`.

## [shelving.walk/walk-with-spec*](shelving/walk.clj#L23)
 - `(walk-with-spec* spec-kw spec obj before after)`

**UNSTABLE**: This API will probably change in the future

Implementation detail of walk-with-spec.

Uses multiple dispatch to handle actually walking the spec tree.

## [shelving.walk/walk-with-spec](shelving/walk.clj#L149)
 - `(walk-with-spec before after spec-kw obj)`

**UNSTABLE**: This API will probably change in the future

An extensible postwalk over data via specs. Visits every spec-defined substructure of the given spec, applying both `before` and `after` precisely once for each node.

`before` is applied as `(before spec obj)` before any recursion. It must produce a value conforming to the given spec. The resulting value is recursively walked according to its spec parse.

`after` is applied as `(after spec obj*)` after recursion, where `obj*` is the result of the recursive walk. `obj*` need not conform to the spec which described the traversal that produced it.

For instance one could traverse using a database insert operation which returned an ID, producing a tree of IDs rather than a legal record or some sort.

`#'walk-with-spec` requires that all subspecs be named, rather than being anonymous specs. This is required for being able to provide a spec name at every node.

Note: predicates are considered to be terminals. No effort is currently made to recur through maps, or to traverse sequences. Record structures are the goal.

If an `Exception` is thrown while traversing, no teardown is provided. `before` functions SHOULD NOT rely on `after` being called to maintain global state.

## [shelving.walk/postwalk-with-spec](shelving/walk.clj#L187)
 - `(postwalk-with-spec f spec-kw obj)`

**UNSTABLE**: This API will probably change in the future

A postwalk according to the spec.

See `#'walk-with-spec` for details.

## [shelving.walk/prewalk-with-spec](shelving/walk.clj#L197)
 - `(prewalk-with-spec f spec-kw obj)`

**UNSTABLE**: This API will probably change in the future

A prewalk according to the spec.

See `#'walk-with-spec` for details.

