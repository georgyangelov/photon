# Partially evaluating Let operations

```
a = 42
a + 1

# Translates to...
Let a = 42
  a + 1
end
```

Requirements:

1. Variables defined using let can be referenced inside the definition expression, but not during initial evaluation 
   of the definition

How does this partially evaluate?

```
let Cat = Struct(
  call = (name) Struct($type = Cat, name = name)
) {
 body
}
```

`let name = expression { body }`

1. During partial evaluation:
    a. Evaluate `expression` binding `name` to unknown. Make sure to check every direct reference to `name`, if one
       is encountered, it is an error.
    b. If expression evaluated to static, evaluate body with `name` bound to the result of `expression`.
    c. If expression could not evaluate fully, partially evaluate `body` with `name` bound to unknown.

2. During actual evaluation:
    a. Evaluate `expression` binding `name` to unknown. Verify no direct references are present.
    b. 

Actually, how do I change the reference?

```
let Cat = Struct(
  call = (name) Struct($type = Cat, name = name)
) { Cat.call("Mittens") }
```

->

```
result = evaluate Struct(call = (name) Struct($type = Cat, name = name)) in (Cat = Unknown)
```

if result is a simple expression (not operation, struct or lambda), no need to bind `Cat` to the actual value.
If result is an operation, struct or lambda - pass through the values and re-bind the scopes to a scope pointing to the
same value. But this doesn't work because the value isn't ...

What about implementing it this way:

```
let Cat = Struct(
  call = (name) Struct($type = Cat, name = name)
) { Cat.call("Mittens") }
```

->

```
catRef = Ref(Unknown)
Cat = Struct(
  call = (name) Struct($type = catRef.get, name = name)
)
catRef.set(Cat)

Cat.call("Mittens")
```