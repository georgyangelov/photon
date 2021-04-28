# Handling compile-time evaluations with lambdas 2

Problem:

```ruby
unknown = (){}.runTimeOnly; () { unknown() }

# =>

unknown = (){}; () { unknown() }
```

Right now, the `unknown` variable is eliminated because the lambda is considered fully evaluated.

There needs to be a difference between a lambda that is a closure and one that is not. However, this 
is  an optimization. There needs to be a rule for lambdas that they can either:

1. Be fully evaluated within their scope of definition
2. Be independent of their environment

The original idea was to keep all non-evaluated lambdas as LambdaDefinition operations, but this
does not work because of the transformations that need to be able to happen compile-time.
For example, `() { unknown() }.runTimeOnly` would evaluate the lambda, and the rule would be broken
(because it would be a Lambda Value now instead of a definition, but it's still not ready for
evaluation).

## Discussion (with myself)

A lambda is a fully-evaluated value only when:

1. We're within its scope of definition (shouldn't need to do anything about this in the evaluator
   right now)
2. All of its closure references are fully-evaluated

Both LambdaDefinition and Lambda can be seen in the value tree.

---

```ruby
fn = () {
    answer = 42
    () { answer }.runTimeOnly
}

fn() # => answer = 42; () { answer }.runTimeOnly
```

How would this work?

Can transform this to `answer = 42; () { answer }.runTimeOnly`. This is the way it should work
if the lambda is considered to be non-evaluated unless called.
Even if it is considered to be evaluated when all of its closure references are evaluated, then 
it should still result in the same code, because it still holds a reference to a parent value.
   
Should this rewrite the lambda's scope, as it'll no longer be part of the fn call scope?

---

Would it help if the lambda closure is explicitly transformed to a struct and passed around?

```ruby
fn = () {
    answer = 42
    ($scope) { $scope.answer }.runTimeOnly
}

fn() # => $scope = Struct(answer = 42); ($scope) { $scope.answer }.runTimeOnly
```

Doesn't look like it... Because the values will have to be extracted anyway.
