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

## Ideas

- Structs have external immutability?
    - Methods can make changes but these create new object in memory
    - The reference is updated when changes happen (with `!`), such as `array.sort_by! 'key'` === `array = array.sort_by 'key'`
