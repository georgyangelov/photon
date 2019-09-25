# Partial Evaluation

## Plan

- No partial evaluation for now on lambdas that have partially computable arguments

## Rules

1. Lambda calls that have defined target and arguments WILL be called compile-time and results will be inlined at the call site.
2. Name refs will not be auto-inlined at compile-time unless for rule 1.

## Special methods

These methods will only be called compile-time.

- `$inline(value) => value` - returns the value of the argument itself, not executing operations. Will resolve names

## Macro method example

```ruby
define_macro "if", { |parser, context|
  condition = parser.read_expression
  parser.expect_new_line
  block = parser.read_block_until('end')

  {
    $inline(condition).to_bool.if_true { $inline(block) }
  }
}
```
