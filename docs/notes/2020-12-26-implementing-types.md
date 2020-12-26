# Implementing types

We want to achieve this:

```
type Cat
  def name: String
end

cat = Cat("Mittens")
cat.name
```

How would this work? Need to implement it with macros and structs. Or with objects?

```
object Cat
  def call(name: String)
    ${ name: String = name, $companionObject = Cat }
  end
end

cat = Cat("Mittens")
cat.name
```

```
Cat = ${
  call = (name: String) {
    ${ name: String = name, $companionObject = Cat }
  }
}

cat = Cat.call("Mittens")
cat.name
```

But there is a difference between the object and the struct. The object needs to have methods callable instead of 
returning the lambda.

Should this be a property of the struct or of the lambda? Actually, we shouldn't need this for the `call` method, but
we'll need it for other methods:

```
object Cat
  def meow
    "Meow"
  end
end

Cat.meow # This should probably call the method instead of just returning the lambda behind it.
```

Can we make lambdas on structs be implicitly called? Or should we not...

---

```
Cat = Struct(
  call = (name: String) {
    Struct(name = name, $companionObject = Cat)
  }
)
```