# Evaluation modes

To achieve seamless compile-time and run-time execution, the system needs to know which functions 
need to be executed at what time.

## Function run modes

A function can be marked as any of the following (including multiple ones):

1. Compile-time
2. Runtime
3. Partial

### Runtime functions

A runtime function is allowed to be called during runtime.

### Compile-time functions

A compile-time function is allowed to be called during compile-time. It can only be called if all of its arguments
are known during compile-time execution. If they are not, if the function also has the run-time mode, then its
execution will be deferred. If the function is not called and it does not have the run-time mode, this should
result in an error. This error should be verifiable by either the interpreter or the type system.

A function evaluated in compile-time context needs to get fully evaluated, thus it needs to also run all
functions that it calls in its implementation. This overrides the run mode flags of all functions called within
to contain the `compile-time` mode.

For example, the following function will read the file during compile-time execution, even though `File.*`
functions do not have the `compile-time` mode:

```ruby
readConfig = () {
  text = File.read('config.txt')
  text
}.compileTimeOnly

config = readConfig()
```

If a function cannot be evaluated at compile-time, it should result in an error:

```ruby
addCompileTime = (a, b) { a + b }.compileTimeOnly

fourtyTwo = addCompileTime 41, Int.parse(File.read('config.txt'))
# This is a compile-time error because `File.read` is run-time-only and cannot produce a result
# at compile-time, which is required to call `addCompileTime`
```

### Partial functions

A function with the partial run-mode can be partially evaluated at compile-time. Partially evaluating the
function means it can be called without knowing all of its arguments. Because of this, it will possibly
produce a block of code (instead of a value as a compile-time or run-time function would). This block of
code then has a default of `runtime` run-mode.

This run mode is used as a hint to the compiler that it is allowed to execute part of the logic of the
function. It does not affect the run-modes of inner functions.

```ruby
unknown = Int.parse(File.read('config.txt'))

addOne = (a) { a + 1 }.partial
fourtyTwo = addOne(unknown)

# This should get evaluated to:
fourtyTwo = unknown + 1
```

```ruby
unknown = Int.parse(File.read('config.txt'))

nonPartialFn = (a) { a + 55 } 

addOne = (a) { nonPartialFn(a) + 1 }.partial
fourtyTwo = addOne(unknown)

# This should get evaluated to:
fourtyTwo = nonPartialFn(unknown) + 1
```

## Macros

```ruby
Core.define_macro "if", (parser) {
  condition = parser.parse_next
  if_true = parser.parse_next
  if_false = (parser.token.string == "else").if_else({ parser.parse_next.eval }, { {} })

  condition.eval.toBool.if_else(if_true.eval, if_false)
}
```

Example evaluation:

```ruby
if true { 42 }

###

(parser) {
  condition = parser.parse_next
  if_true = parser.parse_next
  if_false = (parser.token.string == "else").if_else({ parser.parse_next.eval }, { {} })

  condition.eval.toBool.if_else(if_true.eval, if_false)
}.partial()(???)

###

condition = parser.parse_next
if_true = parser.parse_next
if_false = (parser.token.string == "else").if_else({ parser.parse_next.eval }, { {} })

condition.eval.toBool.if_else(if_true.eval, if_false)

###

condition = [true]
if_true = [{ 42 }] 
if_false = ('' === 'else').if_else({ parser.parse_next.eval }, { {} })

condition.eval.toBool.if_else(if_true.eval, if_false)

###

condition = [true]
if_true = [{ 42 }] 
if_false = false.if_else({ parser.parse_next.eval }, { {} })

condition.eval.toBool.if_else(if_true.eval, if_false)

###

condition = [true]
if_true = [{ 42 }] 
if_false = false.if_else({ parser.parse_next.eval }, { {} })

condition.eval.to_bool.if_else(if_true.eval, if_false)

###

condition = [true]
if_true = [{ 42 }] 
if_false = {}

condition.eval.to_bool.if_else(if_true.eval, if_false)

###

[true].eval.to_bool.if_else([{ 42 }].eval, {})

###

true.to_bool.if_else({ 42 }, {})

###

true.if_else({ 42 }, {})

###

42
```

Note: The `if_else` should have the compile-time run-mode for this to work.

Note: Need to figure out when to inline variables in the AST and when not to. Maybe translate all to `let`s and 
only inline on full calls?

### if_else problem

```ruby
condition = [true]
if_true = [{ 42 }] 
if_false = true.if_else({ parser.parse_next.eval }, { {} })

condition.eval.to_bool.if_else(if_true.eval, if_false)

###

condition = [true]
if_true = [{ 42 }] 
if_false = parser.parse_next.eval

condition.eval.to_bool.if_else(if_true.eval, if_false)
```

## Partial Algorithm

1. For every partial lambda
2. Try to evaluate fully (as in compile-time), depending on the function
3. If at some point something can't be evaluated fully, switch to partial mode
    1. Evaluate: functions, then arguments, then calls if functions are known and partially evalauatable
    2. If encountering a partial function, evaluate recursively
    3. If encountering a compile-time-only function, evaluate or throw error 
       (depending on if arguments are known or unknown)
    4. If encountering a runtime function, leave as-is
4. While processing the function, keep track of argument usages.
    1. For arguments that have no usages left: Remove the argument
    2. For arguments that have usages, wrap using expressions in a `let` statement
    3. For arguments that have usages and are also references passed to the `call` statement, 
       update the code to keep the reference the same (to have no reassignment).
       Actually make this a separate step eliminating useless reassignments.
       
### Example

```ruby
square = (x) { x * x }.partial

square 2 #=> 4
```

```ruby
identity = (x) x
square = (x) { identity(x) * identity(x) }.partial

square 2

#=>

x = 2
identity(x) * identity(x)
```

```ruby
identity = (x) x
square = (x) { identity(x) * (x + 1) }.partial

square 2

#=>

identity(2) * 3
```

```ruby
identity = (x) x
square = (x) { identity(x) * identity(x) * (x + 1) }.partial

square 2

#=>

x = 2
identity(x) * identity(x) * 3
```
