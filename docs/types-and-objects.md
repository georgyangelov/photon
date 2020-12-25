# Types and Objects

## Goal

Need to have the following things:

1. A way to store data
2. A way to run procedures / functions
3. A way to implicitly pass context around with objects
4. Be thread/async safe
5. Be implicitly async
6. Be type-safe
7. Functions to be as static as possible in the default case (so that code lends itself to partial evaluation)

The programming paradigm must solve all of the above with the minimum number of primitives. This means implementing as 
much as possible in the language itself.

## Primitives

### 1. A way to store data

- Does it only need value-objects and plain functions, or does it need to have behaviour objects as well?
- Should interfaces be separate from the objects themselves?

```
type Location
  fileName: String

  startLine: Int
  startColumn: Int

  endLine: Int 
  endColumn: Int
end

object Location
  def beginningOfFile(fileName: String)
    Location(
      fileName,
      
      startLine = 0,
      startColumn = 0,
      
      endLine = 0,
      endColumn = 0
    )
  end

  def extendWith(other: Location)
    self.copy(
      endLine = other.endLine,
      endColumn = other.endColumn
    )
  end

  def toString
    if startLine == endLine and startColumn == endColumn
      "#{fileName}:#{startLine}:#{startColumn}"
    else
      "#{fileName}:(from #{startLine}:#{startColumn} to #{endLine}:#{endColumn})"
    end
  end
end
```

Can't distinguish between the "instance" functions and the "static" functions this way... Maybe if we add a first argument
called `self`?

```
type Location
  fileName: String

  startLine: Int
  startColumn: Int

  endLine: Int 
  endColumn: Int
end

object Location
  def beginningOfFile(fileName: String)
    Location(
      fileName,
      
      startLine = 0,
      startColumn = 0,
      
      endLine = 0,
      endColumn = 0
    )
  end

  def extendWith(self, other: Location)
    self.copy(
      endLine = other.endLine,
      endColumn = other.endColumn
    )
  end

  def toString(self)
    if startLine == endLine and startColumn == endColumn
      "#{fileName}:#{startLine}:#{startColumn}"
    else
      "#{fileName}:(from #{startLine}:#{startColumn} to #{endLine}:#{endColumn})"
    end
  end
end
```

Usage:

```
object Global
  def main()
    fileName = "test.y"
    location = Location.beginningOfFile(fileName)
    location = location.extendWith(Location(fileName, 2, 5, 6, 7))

    location.toString
  end
end
```

---

This should not have a problem having structural typing:

```
type LineRange
  fileName: String
  startLine: Int
  endLine: Int
end

object LineRange
  def isSingleLine(self)
    startLine == endLine
  end
end

def logRange(range: LineRange)
  # One can't use `range.extendWith` here or `range.toString`, because they are defined on the `Location` object,
  # and the `range` parameter is of type `LineRange`.
  #
  # But one can use `range.isSingleLine` because the type of `range` is `LineRange` and that associates it with the
  # `LineRange` object.

  if range.isSingleLine
    log.info "#{range.fileName}:#{startLine}"
  else
    log.info "#{range.fileName}:(from #{startLine} to #{endLine})"
  end
end

logRange Location(...)
```

---

One can also use the `isSingleLine` function on `Location` directly:

```
location = Location(...)

log.info LineRange.isSingleLine(location)
```

---

Or, you can extend the Location object with the LineRange object:

```
Location.extend(LineRange)
```

Actually, should this be allowed? This makes the original object mutable...

---

One can define a new type that implicitly includes both Location and LineRange:

```
def logRange(range: Location & LineRange)
  # From `LineRange` you can use the `isSingleLine` function
  if range.isSingleLine
    log.info "#{range.fileName}:#{startLine}"
  else
    # From `Location`, you can use the `toString` function
    log.info range.toString
  end
end
```

---

One can also extend types:

```
type LineRange
  include Location

  anotherProperty: String
end
```

which allows you to call methods defined on `Location` implicitly. This should be the same as:

```
type LineRange = Location & type { anotherProperty: String }
```

---

Why not have the functions inside the type?

```
type Location
  fileName: String

  startLine: Int
  startColumn: Int

  endLine: Int 
  endColumn: Int

  def beginningOfFile(fileName: String)
    Location(
      fileName,
      
      startLine = 0,
      startColumn = 0,
      
      endLine = 0,
      endColumn = 0
    )
  end

  def extendWith(self, other: Location)
    self.copy(
      endLine = other.endLine,
      endColumn = other.endColumn
    )
  end

  def toString(self)
    if startLine == endLine and startColumn == endColumn
      "#{fileName}:#{startLine}:#{startColumn}"
    else
      "#{fileName}:(from #{startLine}:#{startColumn} to #{endLine}:#{endColumn})"
    end
  end
end
```

Mixes concepts, probably better for them to be separate.

---

Defining a type implicitly creates a companion object:

```
type Location
  fileName: String
end

# This is implicit
object Location
  def call(...args)
    # ...
  end

  def copy(...args)
    # ...
  end

  def toString(...args)
    # ...
  end
end

Location("name") # Same as Location.call("name")
```

---

### 1.1. What about polymorphism?

```
type Animal
  species: String
  name: String

  makeSound(): String
end

type Cat
  include Animal
end

object Cat
  def makeSound(self)
    "Meow, I'm #{name}"
  end
end

type Dog
  include Animal
end

object Dog
  def makeSound(self)
    "Woof, I'm #{name}"
  end
end

def introduce(animal: Animal)
  log.info animal.makeSound
end
```

This can't work because the methods are static, and we can't know which to call at runtime. Or can we?
If we have type tagging, we can make the method resolution dynamic. This will compile to:

```
def introduce(animal: Animal)
  log.info animal._companionObject.makeSound
end
```

---

But how will that work with the structural typing?

```
# Given the above Animal, Cat & Dog definition

type SomethingMakingSound
  makeSound(): String
end

def introduce(animal: SomethingMakingSound)
  log.info animal.$companionObject.makeSound
end
```

But in this case, the situation below will be weird:

```
type Animal
  makeSound(): String
end

type SomethingWithSound
end

object SomethingWithSound
  def makeSound
    "sound"
  end
end

def introduce(animal: SomethingMakingSound)
  # This calls `SomethingWithSound.makeSound` statically
  animal.makeSound
end

# It will not use the method in here
introduce Animal(makeSound = (){ "animal sound" })
```

First, setting a lambda when creating an object like this is weird - it will return the lambda, instead of calling it.
Unless the call is `animal.makeSound()`, but actually then the type should be `makeSound: (): String` instead of
`makeSound(): String`.

So this makes sense. It just leaves the question of how to support mocking - because this way the call to `makeSound`
is resolved statically. Maybe there can be object method overrides? Or some sort of compile-time flag which allows
mocking? It's definitely harder to mock when the methods are static. Actually this can be done with compile-time evaluation.
The function will know an object is mocked during compilation, and will not resolve it statically.

---

What about when there is a union type?

```
def introduce(animal: Cat | Dog)
  # This should be resolved like `Cat.makeSound` or `Dog.makeSound`
  animal.makeSound
end
```

It should probably compile to:

```
def introduce(animal)
  match animal.$companionObject
  when Cat: Cat.makeSound(animal)
  when Dog: Dog.makeSound(animal)
  else: Global.panic!
  end
end
```

Resolving it to `animal.$companionObject.makeSound` will not be correct because it may be a third type at runtime, 
but we've promised to call one of the two functions. Actually this is not correct, it can be resolved like this because
the type system will guarantee that it's not anything else. Actually 2, it cannot guarantee that because this is a
structural type, so passing another conforming object is valid. Actually 3, the `Global.panic!` there is wrong, because
we can pass a structurally correct object, but we cannot match on the companion object because it will be different...

Solution 1: Disallow such cases - on union types make it impossible to call methods implicitly.
Solution 2: Not have union types :)
Solution 3: Disable structural typing for union / intersection types
Solution 4: Don't have structural typing...
Solution 5: Have structural typing only for types without behaviour - `type`s without `object`s

Solution:

```
type Cat
  name: String
end

object Cat
  def makeSound(self)
    "Hi, I'm #{name}"
  end
end

type Dog
  name: String
end

object Dog
  def makeSound(self)
    "Hi, I'm #{name}"
  end
end

def introduce(animal: Cat | Dog)
  # This will not work, because the `makeSound` method is not part of the type, even though it's present on the
  # companion object.
  animal.makeSound
end

# However, this should work:
type CatOrDog
  makeSound(): String
end

def introduce(animal: CatOrDog)
  # This compiles to `animal.$companionObject.makeSound`
  # Actually it should be able to compile to `animal.makeSound` as well if the function is a lambda on a property...
  animal.makeSound
end

introduce Cat(name = "Mittens")
```

---

Types should probably have the following syntax: 

```
type Color = "black" | "white" | "yellow" | "mixed"

type Cat
  def name: String
  def color: Color
end
```

The `def`s are necessary to cement the idea that properties and methods with no arguments are indistinguishable. It would
also allow for the following way of metaprogramming:

```
type Cat
  ["name", "color"].forEach (color) {
    def #{color}: String
  }
end
```

`def` should probably be a macro like `def <name>: <type>` => `self.defineProperty "<name>", "<type>"`.

### 1.2. Implementation

- Structs with arbitrary properties
- Compile-time-only functions

### 2. A way to run procedures / functions

Lambda functions

```
{ 42 }
(a, b) { a + b }
```

### 3. A way to implicitly pass context around with objects



### 4. Be thread/async safe



### 5. Be implicitly async



### 6. Be type-safe



### 7. Functions to be as static as possible in the default case (so that code lends itself to partial evaluation)