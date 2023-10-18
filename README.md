Photon is a **work in progress** experimental language.

It is:
- Mostly functional
- Statically & strongly typed with type inference
- Structurally typed (similar to Go and TypeScript)
- JIT-compiled running on GraalVM using the Truffle language implementation framework

# Table of Contents
- [Ideas](#ideas)
  - [Types as Objects of the Same Language](#types-as-objects-of-the-same-language)
  - [Compile-Time Evaluation](#compile-time-evaluation)
  - [Pattern-Matching on Types in Function Signatures](#pattern-matching-on-types-in-function-signatures)
  - [Implicit Asynchronicity](#implicit-asynchronicity)
  - [Structured Concurrency](#structured-concurrency)
- [Syntax](#syntax)

# Ideas

The main goal is to test out some (weird) ideas and how they work in integration.
**Note that currently some of them are implemented but most are yet to be, so don't think you can run it and
experiment with it yet.**

Here are some of the more interesting ideas:

## Types as Objects of the Same Language

Almost all statically-typed languages are actually two languages which are bundled. One language for
runtime execution and one language for defining types.

For example, in TypeScript, you have some higher-order types which apply modifications to the input
type such as `Pick<T, 'a' | 'b'>` (which creates a type like T but only containing the properties `a` and `b`
from `T`), `Partial<T>` which takes all properties from T and constructs a new type where those properties
are optional, etc.

In all cases these types are a separate language from what is executed at runtime. You can't define a new type
just by doing something like

```ts
function Partial(T) {
  return new Type(
    T.properties.map((name, type) => [name, Nullable(type)])
  )
}
```

Well, in Photon you can do just that. However, to do that, we need another language feature and that is
"compile-time evaluation".

## Compile-Time Evaluation

To be able to construct and manipulate static types in the same language we need to be able to execute
Photon code during compilation. This means code can run in two places - during compilation (on the computer
that compiles the code) and when running (on the computer on which the program is executed).

In Photon every function has a "run mode". Those run modes can be:
- Compile-Time Only - The function can only run during compilation. The code of this function is not present at run time.
- Prefer Compile-Time - The function can run both at compile time and at run time. If the arguments of the function are known at compile time - the function will be executed then and its result will be inlined into the program.
- Prefer Run-Time - The function can run both at compile time and at run time. It will be called at run time unless the function it is being called in is executed at compile time.
- Run-Time Only - The function should not run at compile-time at all.

For example, compile-time only functions are the ones that define types, such as `Class.new`, `Interface.new`, 
the `class`/`interface` macros, etc. Another example is the `Array(T)` function which is just a compile-time only function
that takes a type and creates a new `Array` type which stores only instances of `T`. The hypothetical `Partial(T)` function
is also a regular compile-time function returning a new type.

The "prefer run time" functions are usually functions performing IO. Yes, unless otherwise specified, functions like
`File.read('file.txt')` will read a file at runtime, however it's possible to wrap them in a compile-time only function
which will make the file read during compilation. This is useful for example for type generation. Imagine an SQL client
which introspects the database schema at compile-time and generates the appropriate classes.

## Pattern-Matching on Types in Function Signatures

Again part of the two-language problem we have generic functions. Let's take a `map<T, R>(array: T[], fn: (item: T) => R): R[] { <code> }`
function. The `<T, R>` here are like parameters in the type-language and `<code>` is in the runtime-language.

In Photon, you can match on types, so you can do:

```
def map(array: Array(val T), fn: (item: T) => val R): R {
  <code>
}
```

Note how we're capturing the type of array using `val T` and the return type of the function using `val R` - just
like you would do for a value if `Array` was a struct/enum.

Since types are values themselves, you could also pass them as parameters:

```
def filter(T: Type, array: Array(T), fn: (item: T) => Bool): Array(T)
```

## Implicit Asynchronicity

Most of the current languages that have asynchronously-executing primitives (e.g. Promise, Job, Future) introduce
the `async`/`await` syntax to await those async tasks. While they keep the code normal-looking (as opposed to the
callback / promise chaining style), it still introduces the [differently-colored functions problem](https://journal.stuffwithstuff.com/2015/02/01/what-color-is-your-function/).

Even in Kotlin, which doesn't use the `await` syntax, you have the two colors of functions - the `suspend` ones and the
regular ones.

In Photon, the goal is to have just one type of function, and the compiler/runtime will automatically suspend whenever
necessary. Imagine that you're writing Kotlin but all functions are `suspend` ones, and you don't have to write it.
Functions are also always awaited unless you decide that you want to call a group of functions in parallel.

## Structured Concurrency

I can't explain it better than https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/ 

What I can give is some usage examples:

```
# Async finishes execution once all calls have finished, 
# or fails if any of the runs fail
async (self) {
  run { http.get("http://example.com") }
  run { http.get("http://example.com/page/1") }
  run { http.get("http://example.com/page/2") }
}
```

```
# We can also use a stream with an `async` operator
Stream.of(
  "http://example.com",
  "http://example.com/page/1",
  "http://example.com/page/2"
)
  .async
  .map (url) http.get(url)
  .tap () Log.debug("Got result from an HTTP request (regardless of order)")
  .foreach () Log.debug("Got result from an HTTP request (in order)")

# We end up on this line of code only after the stream above finishes
# (i.e. `foreach` suspends until the Stream is exhausted)
```

Notice how since we have implicit asynchronicity, the `Stream` above works in both synchronous and asynchronous cases,
so it's essentially an rx-like `Observable` as well.

# Syntax

```
# Primitives
true
false
42
42.2
"strings"

# If
if "a" == "b" {
  "yes"
} else {
  "no"
}

# Class definitions
class Person {
  # Fields, expected in the constructor
  def firstName: String
  def lastName: String
  def age: Int
  
  # Methods, return type is inferred or can be specified with `:`
  def name {
    # String interpolation, and implicit `self.`
    "$firstName $lastName"
  }
  
  # Short body syntax - just write the expression after the parens
  def agePlus(num: Int) age + num
}

# Assigning variables. Note that all values are immutable
val pesho = Person.new("Petar", "Petrov", 42)

# Method calls can omit the parens, like in Ruby
Log.info pesho.name
Log.info(pesho.name)
Log.info(pesho.name())

# Interface definitions
interface WithName {
  def name: String
  
  # Can also contain functions with implementations
  def nameSize() name.size
}

# Values are assignable to interfaces if they have the required methods 
val withName: WithName = pesho
withName.nameSize() # 12

# Lambdas
val nameOf = (person: Person): String { person.name }
val nameOf = (person: Person) { person.name }
val nameOf = (person: Person) person.name

# Method chaining - the whitespace before `.filter` indicates it should be called on the result of 
# `Array.new(pesho).map (person) person.name` instead of on `person.name`
Array.new(pesho)
    .map (person) person.name
    .filter (name) name.size > 5

# Pattern matching
Person(val firstName, val lastName, _) = pesho

# Pattern matching can be used for comparison - the following throws an error
# because the lastName is not `Angelov`
Person(val firstName, "Angelov", _) = pesho

# Pattern matching in argument types
def map(array: Array(val T), fn: (item: T) => val R): R {
  # ...
}

# Pattern matching in argument values
def nameOf(Person(val firstName, val lastName, _): Person) {
  "$firstName $lastName"
}
```