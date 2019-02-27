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
"test"

## Ints
42

## Floats
42.2

# Variable definition
variable = value

# Function calls
puts(42)
puts 42

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
```
