# Static evaluation of Structs

## Problem

```ruby
unknown = () { 11 }.runTimeOnly
object = Struct(
  unknown = unknown, 
  answer = () 42
)

object.answer
```

This cannot be evaluated to `42` at compile-time because the `Struct` function will not be called due to `unknown` 
being unknown at compile-time.

## Possible solutions

### Make `Struct` be partially evaluatable

How would this function? Partially evaluatable (partial) functions may be called with some unevaluated arguments.
But how would they work? Maybe it will have a realValue of a regular struct but with values that can be unknown:

```ruby
object = Struct(
  unknown = unknown,
  answer = () 42
)
```

`object` will have a `codeValue` of the same expression and a `realValue` of an actual Struct, with 
`unknown: Value.Lambda` and `answer = () 42`.

Then, accessors will only work for known properties: `object.unknown` will not be callable, while `object.answer` will.