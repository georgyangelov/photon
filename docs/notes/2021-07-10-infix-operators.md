# Infix Operators

```ruby
Set = Object(
  ...

  without = (self: Set(&T), item: T | Set(T)) { ... }
  with = (self: Set(&T), item: T | Set(T)) { ... }
)

s = Set.new(1, 2, 3) without 2 with 4
#=> Set(1, 3, 4) 
```
