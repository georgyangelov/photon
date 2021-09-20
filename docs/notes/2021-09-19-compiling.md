# Compiling and types

## Primitive representation

Bool    -> i1
Int     -> i64
Float   -> double
Pointer -> ptr

## Struct representation

### What the runtime needs

???

### What the type checker needs

prop $type -> another struct

If it's a type:
prop $methodTypes -> Map(name, fnType)
prop $vmethods -> List(name)


## Struct in an interface representation

### What the runtime needs

prop $type -> 



```
struct Type {
  void*[] vtable;
}

struct Struct {
  Type* type;
  void[] props;
}
```

```
obj.a()
# => typeof(obj).$vmethods["a"] => i

call &obj + obj->type->vtable[i]
```

```
Struct1 a = ...
Interface1 b = a

# =>
Struct1 a = ...
Interface1 b = new {
  $type = new {
    $vtable = Interface1.$vmethods.map (method) typeof(a).$vmethods[method]
  },
  object = &a
}
```

```
interface.a()
# => typeof(interface).$vmethods["a"] => i

call interface->object + interface->type->vtable[i]
```
