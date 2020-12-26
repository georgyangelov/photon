# No special struct syntax

Instead of having a special syntax for defining a struct, one can use the method call syntax. If calls start to support
named arguments:

```
${ answer = 42, introduction = "hello" }

Struct(answer = 42, introduction = "hello")
```

How could named arguments work?

```
fn = (answer, introduction) {
  # ...
}

fn(introduction = "hi", answer = 42)
```

How about getting the arguments as an object? Should it be a struct, an array, or both?

```
fn = (...args) {
  # What type is `args`? Maybe it should depend on the type specified
}
```
