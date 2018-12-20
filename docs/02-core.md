# Core library

## Required compiler intrinsics

- Primitive values:
  - `Bool`, `Int`, `Float`

  - `String`
    - `new: String`
    - `size: Int`
    - `append(other: String): String`
    - `get_char(i: Int): String`
    - `equals?(other: String): Bool`
    - `substring(from: Int, to: Int): String`

  - `Maybe(T)` - optional value - contains either `None` or a value of type `T`
    - `None`
    - `Some(T)`
    - `present?: Bool`
    - `unwrap!: T`

  - `Array(T)` - immutable array
    - `new: Array(T)`
    - `capacity: Int`
    - `size: Int`
    - `resize(new_capacity: Int): Array(T)`
    - `get(i: Int): T`
    - `set(i: Int, value: T): Array(T)`

  - `Ref(T)` - mutable reference
    - `get: T`
    - `set(value: T)`

  - `Map(K, V)` - not strictly required but is nice to have
    - `size: Int`
    - `get(key: K): Maybe(V)`
    - `set(key: K, value: V): Map(K, V)`
    - `remove(key: K): Map(K, V)`

  - `Core::Module`
    - `name: String`
    - `methods: Ref(Map(String, Core::Method))`
    - `ancestors: Ref(Array(Core::Module))`
    - `supermodules: Ref(Array(Core::Module))`

- Opaque (for now) values:
  - `Core::Method`
  - `Core::CallContext`

- Compile-time behavior:
  - When calling a method on a value, call `$method` on the value to find the method, then call it

## Modules

```ruby
module Core::StructModule
  self: Struct

  def include(mod: Module)
    self.$module.include(mod)
  end

  def extend(mod: Module)
    self.$module.extend(mod)
  end

  def $method(name: String, context: Core::CallContext): Maybe(Core::Method)
    self.$module.$instance_method(name, context)
  end

  # TODO: How to attach static methods?
  # Solution: When defining a struct, it will automatically extend
  # the `Core::StructStaticModule`, which will define `$method`.
end

# Needs to be a compiler intrinsic:
#
# struct Core::Module
#   - name: String
#
#   # TODO: These arrays must be mutable but are allowed to be so only during
#   # the compilation phase. Once that is complete, we should disable their mutability.
#   # Maybe use a new type for that? `CompileTimeMutable`?
#   - methods: Ref(Map(String, Core::Method))
#   - ancestors: Ref(Array(Core::Module))
#   - supermodules: Ref(Array(Core::Module))
# end

module Core::ModuleModule
  self: Core::Module

  def include(mod: Core::Module): Core::Module
    # ancestors.update { prepend mod }
    self.ancestors.set self.ancestors.get.prepend(mod)
  end

  def extend(mod: Core::Module): Core::Module
    # supermodules.update { prepend mod }
    self.supermodules.set self.supermodules.get.prepend(mod)
  end

  def $method(name: String, context: Core::CallContext): Maybe(Core::Method)
    # Signature of find is:
    #
    #   def Array(T).find(predicate: Fn(T, Bool)): Maybe(T)
    #
    # This means that for this instance, the result of find would be
    # `Maybe(Maybe(Core::Method))`, so we need to unwrap that.
    #
    #   def Maybe(Maybe(T)).flat(): Maybe(T)
    #     self.or None
    #   end
    self.supermodules.get.find { _.$instance_method(name, context) }.flat
  end

  def $instance_method(name: String, context: Core::CallContext): Maybe(Core::Method)
    $def.methods.get.find(name)
    |.or { self.ancestors.get.find { _.$instance_method(name, context) }.flat }
  end

  def $def(method: Core::Method)
    # methods.update { add method.name, method }
    self.methods.set self.methods.get.add(method.name, method)
  end
end
```
