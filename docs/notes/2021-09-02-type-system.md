# Type system internal mechanisms

## Type checking is compile-time evaluation

```
Int = Object(
  assignableFrom = (self, otherType) otherType == Int
)
```

- `$type` is a compile-time method, not present in runtime.

```
42.$type #=> Int
(42: Int) #=> Core.typeCheck(42, Int) #=> Int.assignableFrom(42.$type) ? 42 : compileTimeError("Cannot assign 42 to an Int")
```

```
intToString = (i: Int) i.toString

intToString.$type 
#=> 
Object(
  toString = "(i: Int): String",
  arguments = List.of(Int),
  returns = String,

  assignableFrom = (self, otherType) {
    argumentsAreAssignable = otherType.arguments
      .zip(self.arguments)
      .all (otherArgType, argType) argType.assignableFrom(otherArgType)
    
    returnIsAssignable = self.returns.assignableFrom(otherType.returns)
    
    argumentsAreAssignable and returnIsAssignable
  }
)
```

---

## Need a way to propagate the detected type upwards

Maybe attach traits to values? How would that look?

```
TypeSystem = Object(
  typeCheck = (value, type) ...,
  infer = (value) ...,
)
```

```
11 + 31

#=>
TypeSystem.infer(11) #=> Int
TypeSystem.infer(31) #=> Int

#=>
TypeSystem.infer(Int#+, List(Int, Int))
```

---

```
type Person {
  val @name: String
  val @age: Int
}
```

which is a macro for

```
Person = Object.compileTime(
  $prototype = Type,
  shape = List.of(
    Property.new('name', String),
    Property.new('age', Int)
  )
)
```

Maybe I need to distinguish between `Object` and `CompileTimeObject`, as the latter may not need
to have a shape.

```
type Person {
  val name: String
  val age: Int
  
  def self.new(name: String, age: Int): Person {
    Object(
      $type = Person,
      name = name,
      age = age
    )
  }

  def sayName(): String name
}
```

---

```
type Person(DataT: Type) {
  val name: String
  val age: Int
  val data: DataT
  
  def self.new(name: String, age: Int): Person(DataT) {
    Object(
      $type = Person,
      name = name,
      age = age
    )
  }

  def sayName(): String name
}
```

or

```
Person = Type.new {
  val name: String
  val age: Int
}
```
