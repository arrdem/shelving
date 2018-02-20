## [shelving.spec/keys-as-map](shelving/spec.clj#L12)
 - `(keys-as-map keys-form)`

**UNSTABLE**: This API will probably change in the future

Implementation detail of `#'walk-with-spec`.

Consumes a complete s/keys form, producing a map from map keys to the spec for that key.

Imposes the restriction that there be PRECISELY ONE spec for any given key. Will throw otherwise.

## [shelving.spec/keys-as-map](shelving/spec.clj#L13)
 - `(keys-as-map keys-form)`

**UNSTABLE**: This API will probably change in the future

Implementation detail of `#'walk-with-spec`.

Consumes a complete s/keys form, producing a map from map keys to the spec for that key.

Imposes the restriction that there be PRECISELY ONE spec for any given key. Will throw otherwise.

## [shelving.spec/pred->preds](shelving/spec.clj#L30)
 - `(pred->preds s)`

**UNSTABLE**: This API will probably change in the future

Given a `s/describe*` or 'pred' structure, return its component preds (keyword identifiers and predicate forms).

## [shelving.spec/subspec-pred-seq](shelving/spec.clj#L50)
 - `(subspec-pred-seq s)`

**UNSTABLE**: This API will probably change in the future

Given a keyword naming a spec, return the depth-first .

Does not recur across spec keywords.

## [shelving.spec/spec-seq](shelving/spec.clj#L64)
 - `(spec-seq s & specs)`

**UNSTABLE**: This API will probably change in the future

Given a keyword naming a spec, recursively return a sequence of the distinct keywords naming the subspecs of that spec. The returned sequence includes the original spec.

Throws if a `::s/unknown` spec is encountered.

Named for `#'file-seq`.

