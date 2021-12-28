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






class A
  def fnA(b: B)
    ...
  end
end

class B
  def fnB(a: A)
    ...
  end
end

=>

A = Promise.new
B = Promise.new

A.resolve Class.new('A', () {
  def fnA(b: B)
    ...
  end
})

B.resolve Class.new('B', () {
  def fnB(a: A)
    ...
  end
})



Person = Promise.new
Person.resolve Class.new(
  name = 'Person',
  properties = List.of(
    Class.property('parent', Optional(Person)),
    Class.property('age', Int)
  ),
  instanceMethods = List.of(
    Class.method('asl', (self: Person) "$name, $age")
  )
)



A = Class.new( ... )
B = Class.new( ... )

=>

A = Ref.new
B = Ref.new

A.define Class.new( ... )
B.define Class.new( ... )


A = async(B) {
  
}

B = async(A) {

}






class A
  def fnA(b: B)
    ...
  end
end

class B
  def fnB(a: A)
    ...
  end
end

#=>

A = Promise.new
B = Promise.new

A.resolve Class.new(
  name = 'A',
  instanceMethods = List.of(
    B.then { Class.method('fnA', (b: B) ...) }
  )
)



A = Class.new (Self: Class) {
  
}







A = Promise.new
B = Promise.new

A.resolve Class.new(
  name = 'A',
  properties = List.of(
    Class.property('b', Optional(B))
  )
)

B.resolve Class.new(
  name = 'B',
  properties = List.of(
    Class.property('a', Optional(A))
  )
)




fnA = () { fnB() }
fnB = () { fnA() }





declare B

class A
  def b: Optional(B)
end

class B
  def a: Optional(A)
end






class A
  def b: Optional(B)
end

class B
  def a: Optional(A)
end

A.new(None)

#=>

letRec(
  A = class A
    def b: Optional(B)
  end, 
  B = class B
    def a: Optional(A)
  end
) {
  A.new(None)
}




scope.A = class A
  def b: Optional(scope.B)
end

scope.B = class B
  def a: Optional(scope.A)
end

scope.A.new(None)






## How it will work

class A
  def b: Optional(B)
end

class B
  def a: Optional(A)
end

- All top-level variables will automatically be forward declared

declare A
declare B

class A
  def b: Optional(B)
end

class B
  def a: Optional(A)
end

- Forward declaration will define (with a let) a Promise
- OR will wrap them in a letRec



letRec(
  A = class A
    def b: Optional(B)
  end,

  B = class B
    def a: Optional(A)
  end
) {
  # Will evaluate the types after defining
}




# Types should be evaluated lazily, only when needed (e.g. during typecheck)
# Lazy evaluation should be recursive (Optional(lazy A) -> lazy Optional(A))




letRec(
  A = () class A
    def b: Optional(B())
  end,

  B = () class B
    def a: Optional(A())
  end
) {
  A().new

  # Will evaluate the types after defining
}








recursive {
  class A
    def b: Optional(B)
  end

  class B
    def a: Optional(A)
  end
}

A.new

#=>

letRec(
  A = Lazy.new () class A
    def b: Optional(B)
  end,

  B = Lazy.new () class B
    def a: Optional(A)
  end
) {
  A.new
}






Person = () Class.new(
  fields = List.of(
    Class.field("age", Int),
    Class.field("parent", Optional(Person()))
  ),
  instanceMethods = List.of()
)
Person = Person()

person = Person.new(
  age = 11,
  parent = Some(Person.new(age = 42, parent = None))
)

person.nextAge



