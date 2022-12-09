# class String {
#   def handle: NativeHandle
#
#   def split(delimiter: String) {
#     String.new(Internal.stringSplit(delimiter))
#   }
# }

class Stream(T: Type) {
  def foreach: (each: (value: T): Nothing): Nothing

  def map(fn: (item: T): val R) {
    Stream(R).new (next: (value: R): Nothing) {
      foreach (item) {
        next fn(item)
      }
    }
  }

  def reduce(initial: val R, fn: (result: R, item: T): R): R {
    val result = Ref(R).new(initial)

    foreach (item) {
      result.set fn(result.get, item)
    }

    result.get
  }

  def sum: Int {
    reduce 0, (a, b) a + b
  }

  def max: Optional(Int) {
    reduce 0, (a: Int, b: Int) {
      if (b > a) b else a
    }
  }
}

class Solution {
  def input: String

  def solve {
    val elfFoodItems = input.split("\n\n")
      .map (lines: String) { lines.split("\n").map (num: String) num.toInt }

    val sumOfCaloriesPerElf = elfFoodItems
      .map (items: Stream(Int)) { items.sum }

    val maxCaloriesOfElf = sumOfCaloriesPerElf.max

    maxCaloriesOfElf
  }
}

val input = "
1000
2000
3000

4000

5000
6000

7000
8000
9000

10000
"

Solution.new(input).solve


# class Solution {
#   def input: String
#
#   def solve {
#     val caloriesPerElf = input.split("\n\n").map (lines) {
#       lines
#         .split "\n"
#         .map (num) num.toInt
#         .sum
#     }
#
#     caloriesPerElf.max
#   }
# }
