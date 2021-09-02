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
