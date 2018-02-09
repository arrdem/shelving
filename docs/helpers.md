# UUID helpers

[back to the index](/README.md#usage)

Utilities for computing UUIDs for use as shelf keys.

## [shelving.core/random-uuid](/src/main/clj/shelving/core.clj#L160)
 - `(random-uuid _)`

Returns a random UUID.

## [shelving.core/digest->uuid](/src/main/clj/shelving/core.clj#L168)
 - `(digest->uuid digest)`

Takes the first 16b of a `byte[]` as a 1228bi UUID.

An `IndexOutOfBoundsException` will probably be thrown if the `byte[]` is too small.
