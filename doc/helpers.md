# UUID helpers

## shelving.core/random-uuid
- `(random-uuid & _)`

Ignores its arguments and returns a random UUID4.ssss

## shelving.core/texts->sha-uuid
- `(texts->sha-uuid & texts)`

Computes the SHA sum of many texts, truncating it to 128bi and
returning a UUID with that value.
