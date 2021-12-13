# Type System

interface Any
  def $type: Any
end

interface Type
  def name: String
  def assignableFrom(other: Type): Boolean
end

interface Object
  def $type: Type
end

interface Class
  include Object

  def new(...args: Any): Object

  def $instanceMethods: Map(String, Function)
  def $methods: Map(String, Function)
end

class Person
  def name: String
  def age: Int

  def static pesho
    Person.new(name = "Pesho", age = 42)
  end

  def asl
    "$name, $age"
  end
end

let person = Person.new(name = "Ivan", age = 42) #=> { name: "Ivan", age: 42, $type: Person }
person.asl #=> typeof(person).$instanceMethods["asl"].call()

Person.pesho #=> Person["pesho"].call()

## Rules

### Method Resolution

Given a call to `target.methodName`

1. If typeof(target).$type == Class, then use typeof(target).$instanceMethods["methodName"]
2. else wait until target is known and do dynamic dispatch: target["methodName"]

## Objects describing the types

class Person
  def name: String
  def age: Int

  def asl
    "$name, $age"
  end
end

->

Person = Class.new(
  fields = List.of(
    Class.field("name", String),
    Class.field("age", Int)
  ),

  instanceMethods = List.of(
    Class.method("asl", (self: Person): String "$name, $age")
  )
)

person = Person.new(name = "Ivan", age = 42)

person.asl #=> Ivan, 42

aslMethod = person.method("asl")
aslMethod.call #=> Ivan, 42

typeOf((self: Person): String "$name, $age") #=> Function(String)

(): String "$name, $age"
(self: Person): String "$name, $age"









class Person
  def name: String
  def age: Int

  def asl
    "$name, $age"
  end
end

->

builder = ClassBuilder.new('Person')

builder.property('name', String)
builder.property('age', Int)

builder.instanceMethod('asl', (Self: Type) (self: Self) "$name, $age")

Person = builder.build




class Person
  def parent: Optional(Person)
  def age: Int

  def asl
    "$name, $age"
  end
end

->

Person = Class.new('Person')
Person.addProperty('parent', Optional(Person))
Person.add
