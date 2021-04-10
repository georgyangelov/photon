# Macros and Closures

## Problem

Given the following macro definition:

```ruby
Core.define_macro 'run', (parser) {
  lambda = parser.parse_next

  lambda.eval.call
}
```

The following code is broken:

```ruby
answer = 42

run { answer }
```

This does not work because at the time of the macro evaluation, the Scope for `answer = 42` has not been created yet.
This is because the macro expansion is done at the parsing phase, before the evaluator is run.

## Possible solution 1

The interpreter can check if lambdas have no scope and defer running them. The evaluator can run them later.

```ruby
answer = 42
run { answer }

# => in macro handler => 

answer = 42 
{ answer }.call

# => later, in the evaluator =>

42
```

## Possible solution 2

Define a new type of value for unbound lambdas, which will not be evaluated during ParseTime.
The interpreter will convert UnboundLambda to Lambda, which will then be callable.

The UnboundLambda can be `isStatic = false`, so it will not be evaluated early.

Actually maybe it can be under `Operations`, `Value.Operation(Operation.LambdaDefinition(...), location)`.