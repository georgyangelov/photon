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

```ruby
${a: 1, b: 'test'}
```

An implicit interface will be created for variables that this struct is assigned to:

```ruby
interface ...
  a: Int
  b: String
end
```

## Interfaces

Interfaces enforce the contract between a piece of data (a struct) and a variable (parameter or return value):

```ruby
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

```ruby
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

```ruby
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

```ruby
def factorial(n: Int): Int
  Range.new(1, n).reduce { _ * _ }
end
```

## Modules

Modules are containers for functions. These may be thought as mixins:

```ruby
interface Person
  age: Int
end

module PersonMethods
  def can_buy_alcohol?(person: Person): Bool
    person.age >= 18
  end

  def can_drive?(person: Person): Bool
    person.age >= 18
  end
end

user = ${name: 'Georgi', age: 25}

if PersonMethods.can_buy_alcohol?(user) and PersonMethods.can_drive?(user)
  puts 'Yay! Just not both at the same time...'
end
```

This direct usage is not ideal in terms of syntax because we need to keep using the module name. Instead, one can include the module into an interface, which will allow for it to be called as an instance method:

```ruby
interface Person
  age: Int

  include PersonMethods
end

# We need to specify the interface to use here. If this is a method, this would be natural...
user = ${name: 'Georgi', age: 25}: Person

if user.can_buy_alcohol? and user.can_drive?
  puts 'Yay! Just not both at the same time...'
end

# ...like this
def check(user: Person)
  if user.can_buy_alcohol? and user.can_drive?
    puts 'Yay! Just not both at the same time...'
  end
end
```

The above only works for methods that have their first argument compatible with the interface they are included in. For such module methods, the first argument would preferrably be named `self`.

Depending on the interface type, methods can be different:

```ruby
interface Cat
  name: String

  # If a module is used only for a particular interface, an anonymous one can be created and included directly.
  # This also implicitly sets the `self: Cat` argument
  include module
    def talk
      # If the argument is called `self`, methods can be used without explicit receiver
      puts "Meow! May name iz #{name}"
    end
  end
end

interface Dog
  name: String

  include module
    def talk
      puts "Bork! Names #{name}"
    end
  end
end

a: Cat = ${name: 'Lucky'}
b: Dog = a

a.talk # => Meow! May name iz Lucky
b.talk # => Bork! Names Lucky
```

You can also specify the self argument globally for each method in the module:

```ruby
module CatMethods
  self: Cat

  def talk
    puts "Meow! May name is #{name}"
  end
end

module HelloMethods
  self: interface
    name: String
  end

  def say_hello
    puts "Hello, #{name}"
  end
end
```

## Polymorphism

Including modules in interfaces only allows static dispatch. To use polymorphism, one can include modules in structs:

```ruby
interface Animal
  name: String

  greeting: String
end

module CatMethods
  self: Animal

  def greeting: String
    "Meow! #{name}"
  end
end

module DogMethods
  self: Animal

  def greeting: String
    "Bork! #{name}"
  end
end

def greet(animal: Animal)
  puts animal.greeting
end

cat = ${name: 'Lucky', include CatMethods}
dog = ${name: 'Lucky', include DogMethods}
fox = ${name: 'Lucky', greeting: '???'}

greet cat # Meow! Lucky
greet dog # Bork! Lucky
greet fox # ???
```

## Private attributes

One can simulate classes:

```ruby
interface Request
  @method: String
  @headers: [String: String]

  include module
    def static new(method: String, headers: [String: String]): Request
      ${
        @method: method,
        @headers: headers,

        include Request
      }
    end

    def get?: Bool
      @method.downcase == 'get'
    end

    def post?: Bool
      @method.downcase == 'post'
    end

    def header(name: String): String?
      @headers[name]
    end
  end
end
```
