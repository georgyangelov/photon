# Partial evaluation notes

## Reference

### Closures

`{ |a| { |b| a + b } }` -> `{ |a| { |$scope, b| $scope.a + b }.bind(${ a: a }) }`

---
This cannot be partially evaluated anymore:

```ruby
{ |a|
    { |b| a + b }(42)
}
```

We want to produce `{ |a| { a + 42 }  }` which we cannot because the above translates to 
```ruby
{ |a| 
    { |$scope, b| 
        $scope.a + b 
    }
    .bind(${ a: a })
    .call(42) 
}
```

The inner lambda is static, but the `.bind(...)` makes it non-static, so it can't be evaluated even partially.

Possible solutions:

1. Special-case the `bind` function so that it produces a lambda which is partially evaluatable:

    ```ruby
    { |a| 
        { |$scope, b| 
            $scope.a + b 
        }
        .bind(${ a: a })
        .call(42) 
    }
    ```
    
    -> 
    
    ```ruby
    { |a| 
        { |b|
            { |$scope| 
                $scope.a + $scope.b 
            }.bind(${ a: a, b: b })
        }
        .call(42) 
    }
    ```
    
    -> 
    
    ```ruby
    { |a| 
        { |$scope| 
            $scope.a + $scope.b 
        }.bind(${ a: a, b: 42 }) 
    }
    ```
    
    Now we can partially evaluate because there's a single argument and the lambda is static.

2. Rename `bind` -> `partial` and make all functions have a single struct argument

    ```ruby
    { |a|
        { |b| a + b }
    }
    ```
    
    ->
    
    ```ruby
    { |a|
       { |$args| $args.a + $args.b }.partial(${ a: a, b: $unknown })
    }
    ```
    
    In the other case,
    
    ```ruby
    { |a|
       { |b| a + b }(42)
    }
    ```
    
    ->
    
    ```ruby
    { |a|
       { |$args| $args.a + $args.b }.partial(${ a: a, b: $unknown }).call(42)
    }
    ```
   
    But here how would the call know that it should make the next step:
   
    ```ruby
    { |a|
        { |$args| $args.a + $args.b }.partial(${ a: a, b: 42 })
    }
    ```
   
3. Rename `bind` to `partial` and make `partial` a compile-time only function which does the following:
 
    In the following, `x` and `y` are dynamic (unknown) and `a` and `b` are static (known)
    
    - `{ |arg1| arg1 + 1 }.partial(x)` -> `{ |arg1| arg1 + 1 }(x)` - nothing to do, `x` is dynamic
    - `{ |arg1, arg2| arg1 + arg2 }.partial(a, x)` -> 
      `{ |arg2| { |arg1| arg1 + arg2 }(a) }.partial(x)` -> 
      `{ |arg2| { |arg1| arg1 + arg2 }(a) }(x)`
    - `{ |arg1, arg2| arg1 + arg2 }.partial(x)` -> `{ |arg1| { |arg2| arg1 + arg2 } }(x)`

    No, `partial` cannot be compile-time only because we can have closures in runtime code as well...

4. Make `call` smarter compile-time:

    - `{ |arg1| arg1 + 1 }.call(42)` -> `42`
    - `{ |arg1| arg1 + 1 }.call(x)` -> Nothing to do, no arguments are known
    - `{ |arg1, arg2| arg1 + arg2 }.call(42)` -> `{ |arg2| 42 + arg2 }`
    - `{ |arg1, arg2| arg1 + arg2 }.call(42, x)` -> `{ |arg2| 42 + arg2 }.call(x)` 
    - `{ |arg1, arg2| arg1 + arg2 }.call(x, 42)` -> `{ |arg1| arg1 + 42 }.call(x)`
    - `{ |arg1| { |arg2| arg1 + arg2 } }` -> 
      `{ |arg1| { |$scope, arg2| $scope.arg1 + arg2 }.call(${ arg1 }) }`
    - `{ |arg1| { |arg2| arg1 + arg2 } }.call(42)` -> 
      `{ |arg1| { |$scope, arg2| $scope.arg1 + arg2 }.call(${ arg1 }) }.call(42)` ->
      `{ |$scope, arg2| $scope.arg1 + arg2 }.call(${ arg1: 42 })` ->
      `{ |arg2| 42 + arg2 }`
    - `{ |arg1| { |arg2| arg1 + arg2 }.call(42) }` -> 
      `{ |arg1| { |$scope, arg2| $scope.arg1 + arg2 }.call(${ arg1 }).call(42) }` ->
      `{ |arg1| { { |$scope, arg2| $scope.arg1 + arg2 }.call(${ arg1 }) }.call(42) }`
