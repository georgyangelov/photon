# Statically evaluating recursive lambdas

## Problem

```ruby
factorial = (n) {
  if n == 1 {
    1
  } else {
    n * factorial(n - 1)
  }
}

factorial 5
```

The above keeps expanding the `{ n * factorial(n - 1) }` lambda because the `factorial` function is
defined and has known arguments. The static evaluation doesn't know that this lambda will not need to be
evaluated at all.

## Discussion

1. Do we know how to detect a recursive function?
    Maybe tag the function with number of calls and limit to a certain number of calls on the stack.
2. Can we not statically evaluate lambdas that won't be called?
    Can we detect if a lambda may be called?
    Can we make it so that calling a lambda prevents it from being statically processed?
        No, because it can be assigned to a variable.
   
## Solution

Keep call stack counter for functions being called, limit the number of calls to a specific number.

This seems like a bad way to solve the issue.

```ruby
factorial = (n) {
  else_branch = { n * factorial(n - 1) }

  if n == 1 {
    1
  } else else_branch
}
```