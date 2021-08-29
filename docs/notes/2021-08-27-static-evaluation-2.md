# How to update `Value` so that the partial/comptime evaluation becomes easier

Issues I want to solve:

1. Inlining a lambda result of a fn call must inline the scope as well (preserve variables from it)
```
fn = (a) {
  () { a }
}

fn(42) #=> () { 42 }
```

2. Partially evaluating a lambda should preserve usage of the variables
```
fn = (a) {
  a + 42
}

b = unknown()
fn(b)
fn(b)

#=>
b = unknown()
b + 42
b + 42

# instead of:
unknown() + 42
unknown() + 42
```

## Thoughts

### Maybe the code/real value model is insufficient

What about the following:

1. Each value has a `evaluated: Option[Value]`

When evaluating `a + 1`, return the same thing but with `evaluated` = `42`.
When evaluating something, check if `evaluated` is present, otherwise just use the value itself.





3. Each value has a `source: Option[Value]`.

When evaluating `a + 1` to `42`, a new value will be produced (`42`), which has a source of `a + 1`.

```
unknown = ...
```


```
outer = () {
  a = 1
  
  inner = () {
    b = 2
    
    () { a + b } 
  }
  
  inner
}


```


```
fn = (a, b) {
  (a > 0).if_else {
    () { a + b }
  }, {
    () { b }
  }
}

fn(x, y)
#=>
    a = x
    b = y
    (a > 0).if_else {
      () { a + b }
    }, {
      () { b }
    }
    #=>
    a = x
    b = y
    a = a
    b = b
    () { a + b } 
```
