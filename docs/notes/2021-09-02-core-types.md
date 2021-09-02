# Core Types

## List

With prototype:
```
List = Object(
  of = (...args) Object(
    $prototype = List.methods,
    elements = <???>
  ),
  
  methods = Object(
    $type = () { List }.partialOnly, 

    filter = (self, fn) ...,
    map = (self, fn) ...,
  )
)
```

With traits:
```
List = Object(
  of = (...args) Object(
    $prototype = List.methods,
    elements = <???>
  ),
  
  methods = Object(
    $type = () { List }.partialOnly, 

    filter = (self, fn) ...,
    map = (self, fn) ...,
  )
)
```
