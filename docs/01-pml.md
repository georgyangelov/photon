# Photon Minimal Language (PML)

The main goal of the Photon Minimal Language is to be:

- Easily interpretable and translatable to a different target language.
- Extremely extensible with metaprogramming and macros.

A Photon compiler's goal is to be able to interpret and optionally compile PML. The full Photon language can then be defined as an extension to PML (including syntax extensions).

## Partial evaluation

During compilation, a PML compiler must be able to execute PML according to the following rules:

- A statement is executed as soon as it is parsed because it may affect the way the next statements are parsed.
- Statements may be partially evaluated or reduced based on their AST.

## PML constructs

- Variable definitions (Static Single Assignment (SSA))
- Function definitions and function calls
- Conditional constructs (`if`) and loops (`while`)
- Core types

## Syntax

The syntax is designed to be as extensible as possible without needing to introduce additional parsing primitives, and is inspired by Ruby's syntax:

```ruby
# Single-line comment

# Primitive types

## None
$nothing

## Booleans
true
false

## Strings
'test'
"test"

## Ints
42

## Floats
42.2

# Variable definition
variable = value

# Function calls
print(42)
print 42

# Lambda functions / blocks
lambda = { 42 }
lambda()

# Structs
struct = ${a: 1, b: 2}
struct.a #=> 1
struct.b #=> 2

# Meta (Compile-time) API

# Methods in structs
struct = ${$call: { |method_name|
  if method_name == "test"
    42
  end
}}

# Object.define_method 'method_missing', { |target, method|
#   if target.has_property? '$call'
#
#   end
# }

struct.test #=> 42

######################################################
#                    Compiler API                    #
######################################################

# Special functions

## Marking expression as runtime-only
print_42_at_runtime = { print 42 }.at_runtime
print_42_at_runtime() # This will print 42 when the program runs

print_42_at_compile_time = { print 42 }.at_compile_time
print_42_at_compile_time() # This will print 42 during program compilation

print 42 # Default for side-effect functions (such as print) is to run during runtime

# Defining new syntax

Core.define_syntax 'if', { |parser, context|
  condition = parser.read_expression
  parser.expect_new_line
  block = parser.read_block_until('end')

  {
    context.eval(condition).to_bool.if_true { context.eval block }
  }
}

## The above is run in the following way:

if true
  print 42
end

### ->

{ |parser, context|
  condition = parser.read_expression
  parser.expect_new_line
  block = parser.read_block_until('end')

  {
    context.eval(condition).to_bool.if_true { context.eval block }
  }
}(..., ...)

### ->

{
  condition = <#AST 'true'>
  block = <#AST.Block 'print 42'>

  {
    context.eval(condition).to_bool.if_true { context.eval block }
  }
}()

### ->

true.to_bool.if_true { print 42 }

### ->

true.if_true { print 42 }

### ->

{ print 42 }()

### ->

print 42
```

## Partial evaluation

- `{ <compile-time> } -> compile-time`
- `{ method(<compile-time>) } -> compile-time`
- `{ <runtime> } -> runtime | compile-time`
- `{ <runtime> & <compile-time> } -> compile-time`

## Special methods

- `struct.method` => `struct.$method('method').$call(struct)`
- `struct.method(a, b, c)` => `struct.$method('method').$call(struct, a, b, c)`
- `struct::method` => `struct.$instance_method('method')`
- `struct::method()` => `struct.$instance_method('method').$call()`
- `struct::method(a, b, c)` => `struct.$instance_method('method').$call(a, b, c)`

How would these work with types? For example, if `struct: Module1`, then `struct.method` should translate to `Module1::method(struct)`.
Maybe the struct should have a compile-time-only property `$type` and `$method` should check that?

```
Struct.define_method '$method', { |name| $type.$instance_method(name) }
```

## Assignments

```ruby
answer = 42
answer + 1
```

->

```ruby
{ |answer|
  answer + 1
}(42)
```

This is done to make the scope immutable, which should ease processing.

**Q: How does this handle globals?**
_A: Maybe just have a method that defines them???_

## Can we transform the scopes somehow so that we can move the lambda definitions around?

```ruby
{ |a|
  { |b| a + b }(2)
}(1)
```

->

```ruby

```
