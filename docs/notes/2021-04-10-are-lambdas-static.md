# Are lambdas static?

## Problem definition

```ruby
unknown = (){}.runTimeOnly

outer = () {
  () { unknown() }
}

outer()
```

With the current rules, the above will get evaluated to the following at compile-time:

```ruby
unknown()
```

The `unknown` binding is eliminated because the evaluator thinks that the lambda is a static value.

## Problem 2

Is it possible that the lambda can be removed from its scope incorrectly?

```ruby
unknown = (){}.runTimeOnly

scope1 = (a) {
  () { a + unknown() }
}

scope2 = (a) {
  scope1(42)
}

scope2(1)
```

This will currently result in:

```ruby
() { a + unknown() }
```

There are two issues at play:

1. The let-elimination does not work correctly, because it assumes the lambda does not need it (since it's static).
2. Calling lambdas may result in other lambdas, which is not accounted for and may result in the lambda going outside
   its scope.

## Possible Solution 1

Make lambdas non-static values. Unfortunately, this would mean that they cannot be evaluated compile-time at all, which
is no good.

## Possible Solution 2

Make evaluation of lambdas possible only if their scope is a sub-scope of the current one. This does not work because
the lambda can still depend on non-accessible variables.

## Another problem

What should be the correct behaviour in this case?

```ruby
scope1 = (a) {
  unknown = () { 42 }.runTimeOnly

  () { a + unknown() }
}

scope1(1)
```

Should it result in the following:

```ruby
unknown = () 42
() { 1 + unknown() }
```
(renaming `unknown` if needed to not create collisions)

or should it not try to evaluate `scope1` at all?

## Discussion (with myself)

Need to be able to differentiate between lambdas which can be executed fully, and ones which can only be 
partially computed.

```ruby
() { 42 }
(a) { a + 1 }
```

Rules:

1. Functions with no bindings from the outer scope may be executed always
2. Functions with external bindings may be executed fully only if all of the bindings are known (!= Unknown, Operation)

## Idea

Keep lambdas as `LambdaDefinition`, only transform to `Lambda` when it can actually be evaluated.
So `codeValue` will be `LambdaDefinition`, and `realValue` will be `Lambda`.

Problem is `() { unknown() }.compileTimeOnly` will not work in this case. But it should be fine, because this indicates
that the function cannot actually be called at compile-time.

Should this be transitive?
