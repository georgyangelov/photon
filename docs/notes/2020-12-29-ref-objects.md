# Ref objects

Ref objects are necessary for recursive let bindings. Dependencies are like this:

Type objects -> Let rec -> Ref objects

How could Ref objects be implemented? They need to have the following properties:

1. Ref objects are mutable pointers to other values
2. Ref objects cannot be partially evaluated because their values may change later 
3. (optionally) If `Ref.get` is not used, optimize-out the ref instance completely

Because of 2, they need to be marked as runtime-only functions.

## Function traits

Functions can have the following traits:

- non-partially-evaluatable = cannot be called if the code before it is not evaluated yet
- 




### Examples of functions with different function traits

Cannot be evaluated in partial context:
```
a = Ref(...)

if $? { a.set(41) } { b.set(42) }

# The `Ref.get` function should not be able to be evaluated partially
a.get
```

This should depend on the scope actually, because the following should be evaluatable:

```
a = Ref(...)
a.set(42)

a.get
```

and also

```
(b) {
  a = Ref(...)
  a.set(42)
    
  # The `Ref.get` function should not be able to be evaluated partially
  a.get + b
}($?)

# This above should be able to become:
(b) { 42 + b }($?)
```

Maybe the refs should be a special case in the language? As they are the only mutable object. Maybe it should track
calls to `get` and `set` and determine that all `set` calls have already been evaluated.

Leaving this for another day...

### More examples

```
condition = parser.parse_next
if_true = parser.parse_next
if_false = (parser.token.string == "else").if_else({ parser.parse_next.eval }, { {} })

condition.eval.to_bool.if_else(if_true.eval, if_false)
```

Since `parser` is mutable, the same applies that all previous calls to this object must be done before the next one is
evaluated.

This also has another reason to not be called in the first argument of `if_else`. It has a side-effect which means it
should not be called partially at all.

---

```
value = File.read(...)
```

`File.read` here does not have a (visible) side-effect, but should not be called during partial evaluation or during 
compile-time unless in a compile-time-only function.

## Traits

a. Non-partially-executable - TODO: Figure out a better name, maybe no-partial? Maybe has-side-effect?
b. Compile-time-only
c. Runtime-only

`File.read` is runtime-only. `parser.parse_next` is has-side-effect.

Maybe we can make them orthogonal? Two dimensions:

a. Does it have a side-effect?
    - No - can be called during partial evaluation even before the previous code is executed
    - Yes - can only be called if all previous code is executed (not in partial evaluation context)
b. In what context it can be executed? Both options can be valid for a function at the same time
    - At runtime - by default can be executed at runtime
    - At compile-time - by default can be executed at compile-time

Examples:
- `File.read` is (side-effect = false, execution = at-runtime)
- `Parser.parse_next` is (side-effect = true, execution = at-runtime,at-compile-time)
- `Ref.set` / `Ref.get` are (side-effect = true, execution = at-runtime,at-compile-time)

What does it mean for a function to be side-effect = false, execution = at-runtime? It can't be executed compile-time so
partial evaluation is not possible. It means it can be removed from the code if its value is not used?

## Forced compile-time execution of normally runtime functions

One should be able to read files compile-time to read data for code generation:

```
Config = Object(
  File.read(".env").lines.forEach (line: String) {
    name, value = line.split("=", 2)

    def #{value}: Optional(String)
  }
)
```

Some contexts, like the body of the `Object` need to always be executed compile-time.

```
# Normally, this will run during runtime
File.read(".env")

# But if it's part of a compile-time only function it will run during compile-time
readEnv = () { File.read(".env") }.compileTimeOnly

# This gets called compile-time
readEnv()
```

Maybe we just need to have a single coordinate:

a. In what context a function can be executed?
    - At partial-evaluation time - if arguments are known, can be run out-of-order with the rest of the code
    - At runtime
    - At compile time

## Verifying no compile-time-only functions are called during runtime

Can this check be done compile-time?

```
fn = if File.read(...)
  runtimeFunction
else 
  compileTimeFunction
end

fn(42)
```

This should result in an error - `compileTimeFunction` is not called during compile time. But verifying this requires
additional analysis. Maybe this should be part of the type system - if the trait flags are part of the type. This way
the `fn` type can be `Function(...) | Function(..., traits = CompileTime)` and the compiler can see a call to a function
that is possibly a compile-time-only function.