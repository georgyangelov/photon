# Type System

## Simple types

- `None` - Can only represent the value `0` in memory
- `Byte` (== `UInt8`)
- `Bool` (== `UInt8` with different semantics)
- `Int` (possibly `Int8`, `Int16`, `Int32`, `Int64`, `UInt8`, `UInt16`, `UInt32`, `UInt64`)
- `Float` (possibly `Float32`, `Float64`)
-? `Pointer` (platform-specific, `UInt64`)

## Compound types

- `struct`
- `class`?

```
struct String
  memory: Pointer
  size: Int
end
```

## Struct literals

```ruby
test = {
  a: 123,
  b: true
}

typeOf(test)
#=> struct _
#..   a: Int
#..   b: Bool
#.. end
```

### Struct literal type can be inferred

```ruby
struct AStruct
  a: Int
  b: Bool
  c: Int?
end

a_struct_instance: AStruct = {
  a: 123,
  b: false
}

a_struct_instance_2: AStruct = {
  a: 123
}
#!> Error: Cannot assign {a: Int} to AStruct, member b missing
```

### Functions can be associated with structs

```ruby
module CounterMethods
  def increase
    @a += 1
  end

  def decrease
    @a -= 1
  end
end

struct Counter
  @a: Int

  include CounterMethods
end

counter: Counter = {a: 0}

counter.increase
counter.a #=> 1

counter.decrease
counter.a #=> 0
```

## Compile-time functions can return types

```ruby
module GenericListMethods
  include Enumerable

  def add(element)
    # ...
  end
end

def List(type: Type)
  struct GenericList
    memory: Pointer
    size: Int
    capacity: Int

    include GenericListMethods
  end
end

def main
  numbers = List(Int).new

  numbers.each do |number|
    puts number
  end
end
```

## Modules can also be parametrized

```ruby
def ListMethods(type: Type)
  module ListMethodsOfType
    def add(element: type)
      # ...
    end
  end
end

struct ListOfInts
  # ...

  include ListMethods(Int)
end
```

## Initialization special cases

```ruby
a: List(Int) = [5, 10]             # Same as List(Int).new(5, 10)
a: Hash(Int, Int) = [5: 10, 10: 5] # Same as Hash(Int, Int).new([5, 10, 10, 5])
```

## Type inference problem - how to do value inference on generic types

```ruby
a: List(Int) = List.new
```

Options:

- Generics are not macros but "regular" generics so the type system has more information
- Special-case the `new` construct?
  - Not great because we may want to do factory methods: `a: List(Int) = List.singleton(5)`
- Macro definition which takes the desired type?
- Use the inferred type static methods when building: `a: List(Int) = _.singleton(5)`
  - `_` is a placeholder for the static module of the inferred type of the expression

## Current type static module `_`

```ruby
module StaticMethods
  def self.new
    # ...
  end
end

struct Test
  include StaticMethods
end

a: Test = _.new
# We know `_.new` should return `Test` instance, so `_` is the `Test` type

# Equivalent to:
a = Test.new

# Works best in context:
puts _.from(5) # Calls `puts String.from(5)`

# And with generics:
a: List(Int) = _.singleton(5) # === `a = List(Int).singleton(5)`
```

What about factory methods on other types?

```ruby
interface ParsableFromString
  def self.parse(from: String): Self
end

module String
  def parse_all(as_type: ParsableFromString): List(as_type)
    self.split(',').map { |element| as_type.parse(element) }
  end
end

a: List(Int) = "1, 2, 3, 4".parse_all(Int)
```

```ruby
interface ParsableFromString
  def self.parse(from: String): Self
end

def ListMethods(type: Type): Module
  module
    def self.parse_from(string: String): List(type)
      string
      |.split ','
      |.map &.trim
      |.map { |element| type.parse(element) }
    end
  end
end

a: List(Int) = _.parse_from('1, 2, 3, 4')
```

```ruby
def HashMethods(k: Type, v: Type): Module
  module
    def self.new_with_default(fn: Lambda(k, v)): Self
      # ...
    end
  end
end

a: Hash(String, List(Int)) = _.new_with_default { _.new }
```

## Problem: How to do argument-based type inference

How to implement `first`? How to know the type parameter of the array?

### Solution 1 - Stupid way:

```ruby
def first(t: Type, array: Array(t)): t
  array[0]
end

f = first(Int, [1, 2, 3, 4, 5])
```

### Complex way

`Array(t)` is evaluated after the generic check, or the Type contains information about which function was used to generate it.

```ruby
def first<t>(array: Array(t)): t
  array[0]
end

f = first([1, 2, 3, 4, 5])
```

What about this implementation:

```ruby
def Array2(t: Type): Type
  Array(t)
end
```

### Base problem

How to do type pattern matching?

```ruby
Array(a) === Array(Int)
```

#### How can this be done with regular generics?

```ruby
Array<a> === Array<Int>
```

### Solution 2

Don't evaluate methods returning `struct`s until after generic methods:

```ruby
def Array(t: Type): Type
  struct
    # ...
  end
end

def Optional(t: Type): Type
  struct
    # ...
  end
end

def ArrayOfOptionals(t: Type): Type
  Array(Optional(t))
end

def unwrap_first<t>(array: ArrayOfOptionals(t)): t
end

unwrap_first([1, 2, 3, 4, 5])

#=> unwrap_first(array: ArrayOfOptionals(t)): t === unwrap_first(array: Array(Optional(Int)))
#=> ArrayOfOptionals(t) === Array(Optional(Int))
#=> Array(Optional(t)) === Array(Optional(Int))
#=> Optional(t) === Optional(Int)
#=> t === Int
```

This relies on partially evaluating AST expressions, then matching them with type variables.

## Interesting type inference problem

```ruby
def unwrap_first<t>(array: ArrayOfOptionals(t)): t
end

unwrap_first([_.of(1), _.of(2), _.of(3)])
```

How can it know `_` is `Optional(t)` and it has a `of` method?

### Base problem

```ruby
a: Array(t) = _.singleton(2)
```

How do we infer that `t` is `Int`? We can't if generics are methods.

## Ideas

- Structs have external immutability?
    - Methods can make changes but these create new object in memory
    - The reference is updated when changes happen (with `!`), such as `array.sort_by! 'key'` === `array = array.sort_by 'key'`

### Type methods use macro methods to define types

```ruby
def Array(t: Type)
  array_of_t = Runtime.create_type("Array(#{t.name})")
end
```

Nope, too complicated.

### Explicit mutability

- Structs are immutable
- There is a `Ref`/`Box`/`Pointer` type which can be mutated:

  ```ruby
  struct User
    name: String
    age: Int
  end

  struct App
    current_user: *User
  end

  def set_user_name(app: App, new_name: String)
    user = app.current_user

    app.current_user = user.copy_with(name: new_name)
  end
  ```

- Can be used well with STM
- Makes for mutable/immutable annotations
- Can include convenient mutation operations:

  ```ruby
  class AtomicInt
    @value: *Int

    def increment
      @value.swap { @value.get + 1 }
    end

    def decrement
      @value.swap { @value.get - 1 }
    end

    def wait_until_zero
      wait_until { @value.get == 0 }
    end
  end
  ```

- Atomic blocks:

  ```ruby
  class AtomicInt
    @value: *Int

    def increment_twice
      atomic do
        @value.swap { @value.get + 1 }
        @value.swap { @value.get + 1 }
      end
    end
  end
  ```

- Loop algorithms will still work:

  ```ruby
  def factorial(n: Int)
    current = Ref.new(n)
    result = Ref.new(1)

    while current.get != 0
      result.swap { current.get * current }
      current.swap { current - 1 }
    end

    result
  end
  ```

  ```ruby
  def factorial(n: Int)
    Range.new(1, n).reduce { |a, b| a * b }
  end
  ```
