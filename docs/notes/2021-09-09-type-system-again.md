# Type System Again

How would it look like from usage perspective?

```
type Person {
  val name: String
  val age: Int

  # Auto-generated:
  static def new(...args)
    Object($type = Person, ...args)
  end
  
  def asl
    "$age, $name"
  end
}

person = Person.new(name = 'John Doe', age = 24)

person.name
person.age
person.asl
```

```
Person = Object(
  $type = Type,
  $instanceMethods = Object(
    asl = () "$age, $name"
  ),

  new = (...args) Object(...args, $type = Person)
)

person = Person.new(name = 'John Doe', age = 24)

person.name #=> person.name, person.$type.$instanceMethods.name
```

## Type checking

```
42: Int
#=>
Core.typeCheck(42, Int)
#=>
Int.assignableFrom(42.$type)
```
