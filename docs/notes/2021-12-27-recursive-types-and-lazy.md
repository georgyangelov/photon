# Recursive types and lazy evaluation

## Goal - make this work

```ruby
Person = Class.new(
  properties = List.of(
    Class.property("parent", Optional(Person))
  ),
  
  methods = List.of(
    Class.method("hasParent", (self: Person) parent.present?)
  )
)

Person.new(parent = None).hasParent
```

## Idea to wrap in Lazy values

```ruby
Person = Class.new(
  properties = List.of(
    Class.property("parent", Optional(Person))
  ),

  methods = List.of(
    Class.method("hasParent", (): AnyFunction { (self: Person) parent.present? })
  )
)

Person.new(parent = None).hasParent
```

```ruby
Person = Class.new(
  properties = List.of(
    Class.property("parent", Pick(Person, "parent"))
  )
)

Person.new(parent = None)
```

```ruby
Person = Class.new(
  properties = List.of(
    Class.property("name", String),
    Class.property("parent", (): Type { Pick(Person, "name") })
  )
)

Person.new(parent = None)
```
