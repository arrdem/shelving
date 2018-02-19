## [shelving.spec/keys-as-map](shelving/spec.clj#L12)
 - `(keys-as-map keys-form)`

**UNSTABLE**: This API will probably change in the future

Implementation detail of `#'walk-with-spec`.

Consumes a complete s/keys form, producing a map from map keys to the spec for that key.

Imposes the restriction that there be PRECISELY ONE spec for any given key. Will throw otherwise.

## [shelving.spec/keys-as-map](shelving/spec.clj#L12)
 - `(keys-as-map keys-form)`

**UNSTABLE**: This API will probably change in the future

Implementation detail of `#'walk-with-spec`.

Consumes a complete s/keys form, producing a map from map keys to the spec for that key.

Imposes the restriction that there be PRECISELY ONE spec for any given key. Will throw otherwise.

## [shelving.spec/subspecs](shelving/spec.clj#L29)
 - `(subspecs s)`

**UNSTABLE**: This API will probably change in the future

Given a `s/describe*` structure, return the subspecs (keyword identifiers and predicate forms) of the spec.

