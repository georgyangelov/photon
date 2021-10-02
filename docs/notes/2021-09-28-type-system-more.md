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