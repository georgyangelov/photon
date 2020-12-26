# Structs and objects

## Plain structs

Two options for syntax:

```ruby
user = ${
  first_name = "Georgi"
  last_name = "Angelov"
}
```

- Easier to type later:

```ruby
user = ${
  first_name: String = "Georgi"
  last_name: String = "Angelov"
}
```

---

```ruby
user = ${
  first_name: "Georgi"
  last_name: "Angelov"
}
```

- Similar to JSON syntax, easier to remember
- Syntax for types may be:

```ruby
user = ${
  [first_name: String]: "Georgi"
  [last_name: String]: "Angelov"
}
```

or

```ruby
user = ${
  first_name: "Georgi":String
  last_name: "Angelov":String
}
```

### Usage

```ruby
user = ${
  first_name = "Georgi"
  last_name = "Angelov"
}

log.info user.first_name
```


## Structs with methods

```ruby
user = ${
  first_name = "Georgi"
  last_name = "Angelov"

  full_name = {
    first_name + " " + last_name
  }
}
```

Given the above, the following will not work:

```ruby
user.full_name # Result is the lambda, not the string it generates

user.full_name.call # Result is the string the lambda generates
```

Should it be this way? Otherwise structs will be used as objects. The above leads to structs containing direct references to lambdas instead of being extracted into an object or a class.
Should I care?

```ruby
user.full_name
```

## Classes

```ruby
class User
  def initialize(@first_name: String, @last_name: String)
  end

  def full_name
    @first_name + " " + @last_name
  end
end
```
