# UUID helpers

[back to the index](/README.md#usage)

Utilities for computing UUIDs for use as shelf keys.
## shelving.core/random-uuid
 - `(random-uuid _)`

Returns a random UUID.

## shelving.core/content-hash
 - `(content-hash val)`
 - `(content-hash digest-name val)`

A generic Clojure content hash based on using `#'clojure.walk/postwalk` to accumulate hash values from substructures.

For general Objects, takes the `java.lang.Object/hashCode` for each object in postwalk's order. Strings however are fully digested as UTF-8 bytes.

The precise hash used may be configured, but must be of at least 128bi in length as the first 128bi are converted to a UUID, which is returned.

## shelving.core/digest->uuid
 - `(digest->uuid digest)`

Takes the first 16b of a `byte[]` as a 1228bi UUID.

An `IndexOutOfBoundsException` will probably be thrown if the `byte[]` is too small.
