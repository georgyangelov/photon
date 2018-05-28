# Type checker

## Base types

The base types' concrete type is the same.

- `None` - Can only represent the value `0` in memory
- `Byte` (== `UInt8`)
- `Bool` (== `UInt8` with different semantics)
- `Int` (possibly `Int8`, `Int16`, `Int32`, `Int64`, `UInt8`, `UInt16`, `UInt32`, `UInt64`)
- `Float` (possibly `Float32`, `Float64`)
-? `Pointer` (platform-specific, `UInt64`)

## Compound types

### Array

Actually vector as it is dynamic and heap-allocated.

    # Concrete syntax
    a: Array(Int) = [1, 2, 3, 4]

    # Syntax sugar
    a: [Int] = [1, 2, 3, 4]
    a: [Int] = []

    # Type can be inferred
    a = [1, 2, 3, 4]

Concrete type is:

    def Array(a: Type): Type
      struct
        @size: Int
        @capacity: Int
        @elements: Pointer
      end
    end

### HashMap

    # Concrete syntax
    a: HashMap(String, Int) = [one: 1, two: 2, three: 3]

    # Syntax sugar
    a: [String: Int] = [one: 1, two: 2, three: 3]
    a: [String: Int] = []

    # Type inference
    a = [one: 1, two: 2, three: 3]

### Struct

    struct UserData
      id: Int
      activated: Bool
      age: Int
    end

### Union / Enum types

    # The concrete type adds discriminant as a first byte
    type Id = String | Number

    type Some(a) = a
    type Optional(a) = None | Some(a)

### Optional

    # Concrete syntax
    a: Optional(Int) = None
    a = Some(5)

    # Syntax sugar
    a: Int? = None

    # Automatic cast from `a` to `Optional(a)`
    a = 5

    if a
      # `a` is `Int` here, without the `Optional`
      print a + 1
    end

### String

    a: String = "Hello"

    # Strings are immutable
    b = a
    b = b + " world"
    expect a != b

    # String literals are interned
    b = "Hello"
    expect address_of(a) == address_of(b)

    # Strings can be interpolated
    b = "#{a} world"

### Reference

    # Concrete syntax
    struct LinkedListNode
      value: Int
      next: Ref(LinkedListNode)
    end

    # Syntax sugar
    struct LinkedListNode
      value: Int
      next: &LinkedListNode
    end

## Type constraints / Generic types

### Interface (structural typing)

    # Given...
    struct User
      @id: Int
      @activated: Bool
      @age: Int
    end

    # ...and...
    interface DBRecord
      id: Int
    end

    # ...then:

    def exists(record: DBRecord): Bool
      DB.find(record.id) != None
    end

    user: User = ...

    # Compile-time, this call generates a version of the function that accepts the concrete
    # `User` type.
    exists(user)

### Intersection types

    interface DBRecord
      @id: Int
    end

    interface UserData
      @activated: Bool
      @age: Int
    end

    type User = DBRecord & UserData

### Lambda / Fn

    a: Fn(None) = { print "hi" }
    a: Fn(Int) = { 42 }
    a: Fn(Int, Bool) = { |x: Int| x == 42 }

    # Syntax sugar
    a: () = { print "hi" }
    a: () -> Int = { 42 }
    a: (Int) -> Bool = { |x: Int| x == 42 }

    # Multiline syntax
    a: () = do
      print "hi"
    end

### Type variables

    def map(self: [a], mapper: (a) -> b): [b]
      # ...
    end
