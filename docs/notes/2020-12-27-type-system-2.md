# Type System

Idea is to have three concepts - `type`, `interface` and `module`:
 
- Types represent concrete data structures
- Interfaces represent data structures in an abstract way
- Modules contain behaviour - methods associated or not associated with a particular type

```
interface Named
  def name: String
end

introduction = (named: Named) "Hey! My name is #{named.name}"

type Cat
  def name: String
  def age: Int
end

type Dog
  def name: String
end

# These pass because the two types are compatible
introduction Cat("Mittens", 2)
introduction Dog("Ralph")
```

How would this be compiled? What structure does `introduction` have? Maybe it should be compiled as:

```
type Named
  def name: String
end

introduction = (named: Named) "Hey! My name is #{named.name}"

cat = Cat("Mittens", 2)
dog = Dog("Ralph")

introduction Named(name = cat.name)
introduction Named(name = dog.name)
```

It can also be compiled as a generic:

```
introduction_Cat = (named: Cat) "Hey! My name is #{named.name}"
introduction_Dog = (named: Dog) "Hey! My name is #{named.name}"

cat = Cat("Mittens", 2)
dog = Dog("Ralph")

introduction_Cat cat
introduction_Dog dog
```

So interfaces can rely on generics as an implementation. The implementation type may depend on the function - the
number of different places it's called from, the size of the function, etc.

---

So how would interfaces, types and modules interact?

```
type Cat
  def name: String
end

module Cat
  def meow(self)
    "Meow! My name is #{name}"
  end
end

cat = Cat("Mittens")

# How does this work?
cat.meow

interface Meowing
  def meow: String
end

# How does this call `meowing.meow`?
introduce = (meowing: Meowing) log.info meowing.meow
```

How is this processed and compiled?

```
Cat = Object(
  # Methods from type definition  

  properties = List(
    Object(name = "name", type = String)
  )

  call = (name: String) Object(name = name)
  
  # Can `Cat` be extended in any way?
  assignableFrom = (type: Type) type == Cat

  # Methods from module definition

  meow = (self: Cat) "Meow! My name is #{self.name}"
)

cat = Cat("Mittens")

# Algorithm for `cat.meow` is as follows:
# 1. If there is a property called `meow` on the object:
# 1.1. Return the property (function Cat.meow is resolved to a getter, which can be optimized further later) 
# 1.2. Search for a function on the module with this name, which accepts `self`. If present, use this
#
# Question in the above algorithm is what defines `the module` and how can we extend this to include other modules.
Cat.meow(cat)

Meowing = Object(
  properties = List(
    Object(name = "meow", type = String) 
  )
 
  assignableFrom = (type: Type) {
    properties.all? (property) { 
      type.properties.contains(property) or type.method(property)
    }
  }
)

# This type checks with `Meowing.assignableFrom(Cat)`, then processes it as a template function
introduce = (meowing: T) log.info meowing.meow
```

















List = (T) {
    Object(
      map = (fn: Function(T, )) {
        ...
      }
    )
}


length = (items: List(union(Int, Bool))) { ... }

length([...])



items = [1, 2, 3]
items = items.add 4

item, items = items.pop



