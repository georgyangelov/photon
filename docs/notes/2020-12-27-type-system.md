# Type System

There are ~three~ four components to this:

1. Type Verification
2. Type Inference
3. Generic Types
4. Structure for compilation

## Type Verification

```
hello = (name: String) "Hello, #{name}!"

# This passes
introduction: String = hello "V"

# This fails - invalid argument type supplied to `hello`
introduction: String = hello 42

# This fails - invalid assignment
introduction: Int = hello "V"

# This above is actually the same as
(introduction: Int) { <rest-of-the-code> }(hello "V")
```

---

What should be the syntax for specifying a lambda return type?

---

Places where verification needs to happen:

- At assignments
- At function calls

Since assignments in Photon are syntax sugar for function calls, the only place to check types would be function calls.

### How will type verification work?

```
hello = (name: String) "Hello, #{name}!"

hello 42
```

A set of rules:

1. There are the following primitive types: `Nothing`, `Bool`, `Int`, `Float`, `String`
2. When calling a function that has a parameter `param: TParam` with argument `arg: TArg`, `TArg` must be *assignable*
   to `TParam`.

`TArg` is assignable to `TParam` iff `TParam.assignableFrom(TArg)` returns `true`.

For the primitive types, the `assignableFrom` function is `(self, other) self == other`.

### How will struct types work?

```
Cat = Struct(
  call = (name: String, age: Int) {
    Struct(name = name, age = age, $companionObject = Cat)
  }

  # What is the type of Type?
  assignableFrom = (otherType: Type) otherType == Cat 
)

# This will be type-checked as `Cat.assignableFrom(Cat)`
lucky: Cat = Cat("Lucky", age = 1)
```

First, what is the type of `Type`? How is `Type` defined? Maybe it is the companion object (prototype) of all types?

```
Type = Struct(
  assignableFrom = (other: Struct) other == Type or other.$companionObject == Type
)

Cat = Struct(
  $companionObject = Type,

  call = (name: String, age: Int) {
    Struct(name = name, age = age, $companionObject = Cat)
  }

  # This will be typechecked as `Type.assignableFrom(...)`
  assignableFrom = (otherType: Type) otherType == Cat 
)
```

### What about inheritance?

```
Type = Struct(
  assignableFrom = (other: Struct) other == Type or other.$companionObject == Type
)

Animal = Struct(
  $companionObject = Type,
  assignableFrom = (otherType: Type) otherType == Animal
)

Cat = Struct(
  $companionObject = Animal,
  assignableFrom = (otherType: Type) otherType == Animal
)
```

Do I actually need inheritance? Will need to think about it.










