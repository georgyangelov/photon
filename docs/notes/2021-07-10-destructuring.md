# Destructuring and Pattern Matching

There needs to be a way to pattern match values. This can me modelled similar to Scala and
Kotlin.

## Matching lists

Example syntax:

```ruby
list = [1, 2, 3, 4]

(head, ...tail) = list
(first, second, third, ...) = list

first = 1
(&first, second ...) = [1, 2, 3, 4]
# Success, second = 2

(&first, second, ...) = [2, 2, 3]
# Error, does not match
```

## Matching objects

Objects can implement the `T.$destructure(self): Any[]` method. Matching on the object will 

```ruby
type Location
  row: Int
  col: Int

  def destructure(self)
    [row, col]
  end
end

(row, col) = Location(3, 4)

match Location(3, 4) {
  when Location(3, column): column
  else 42
}

# =>
matchVal = Location(3, 4)
if Location(3, col) = matchVal {
  col
} else {
  42
}

# =>
matchVal = Location.new(3, 4)
matchValDestruct = matchVal.$destructure

if matchValDestruct
```

## Pattern matching for generics

```ruby
map = (list: List(&T), fn: T => &R): List(R) { ... }

numbers = List.new(1, 2, 3, 4)
map(numbers, (n) n * 2)
```

To implement this, we need the following:

```ruby
numbers.$type #=> List(Int)
List(Int).$destructure #=> [Int]
```
