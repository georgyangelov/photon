# Types

## Immutability

All types are immutable, except for the `Ref` type.

## Native types

- `Int`
- `Float`
- `Bool`
- `String`
- `Ref`

## Structs

Structs don't need to be defined as types in advance. A new struct is declared on first use by the types of the fields.

```
${a: 1, b: 'test'}
```

An implicit interface will be created for variables that this struct is assigned to:

```
interface ...
  a: Int
  b: String
end
```

## Interfaces

Interfaces enforce the contract between a piece of data (a struct) and a variable (parameter or return value):

```
interface Person
  name: String
end

def happy_birthday(person: Person)
  puts "Happy birthday to #{person.name}!"
end

happy_birthday ${name: 'You'}
happy_birthday ${name: 'You', age: 25}
```

## Data is immutable

Structs are immutable, data inside cannot be changed. However, one can still manage mutable state using the `Ref` type:

```
def factorial(n: Int): Int
  current = Ref.new(n)
  result = Ref.new(1)

  while current.get > 0
    result.set result.get * current.get
    current.set current.get - 1
  end

  result.get
end
```

This works for structs as well:

```
def factorial(n: Int): Int
  state = Ref.new ${
    current: n,
    result: 1
  }

  while state.get.current > 0
    state.set ${
      current: state.get.current - 1,
      result: state.get.result * state.get.current
    }
  end

  state.get.current
end
```

Of course, functional paradigms should be preferred over procedural style:

```
def factorial(n: Int): Int
  Range.new(1, n).reduce { _ * _ }
end
```
