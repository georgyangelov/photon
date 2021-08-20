# Moving scopes

## Problem 1

`a = 42; () { a }` is evaluated to `() { a }`

Why? Because during interpretation `realValue` is `() { a }`, which is in a scope with `a = 42`.

### Solution 1

Change CompileTimeResult to include two things for `realValue`: Scope and Value.
When inlining `realValue`, do a `moveScope(value = realValue, from = compTimeRes.scope, to = currentScope)`.

This should work for calling functions and inlining their results as well.

### Solution 2

Add `scope` property to `Value`. This will allow me to inline values at any point. It will also allow
more free-use of values as an inner value can be referenced and it will work. But it includes more
processing and the scope itself shouldn't be part of the value - it's a state.

Is there a use-case where Solution 1 won't work?

## Problem 2

`() { a = 42; () { a } }()` fails with NoSuchElementException while trying to get scope
