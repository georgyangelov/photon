# Borrow checker ideas

## Problems

### Use after free

If a mutable ref and an immutable ref is allowed at the same time:

    array = [move object]
    object_ref = array[0]

    # This deletes the object as it was owned by the array
    array.clear

    # Use after free
    object_ref.do_something

Possible solutions:

- Runtime error when an object is about to be deleted and is still referenced.

### Circular references

If there is dynamic ownership it becomes the same as reference counting:

    obj_1 = {}
    obj_2 = {}

    obj_1.put 'ref_2', move obj_2
    obj_1.get('ref_2').put 'ref_1', move obj_1

    # Both obj_1 and obj_2 here are invalid (because they have been moved),
    # but will not be deleted because they are each-other's owners

Need to prevent ownership cycles without needing to specify whether something is an owner statically.

Possible solutions:

- Do not allow move of object while a reference to it is still active. The `obj_1.get` holds the reference until the line passes.


### Moving a reference

If there is dynamic ownership, moves must still be statically validated:

    def fn(o: Object)
      something_else(move o)
    end

    # This works, fn is now the owner
    fn(move object)

    # This will break, fn is NOT the owner and cannot move
    fn(object)

Possible solutions:

- Infer whether ownership of a variable is required by particular scope. Iff
    - There are moves of this variable in the scope body, or
    - there are nested scopes that require the ownership.

## Algorithm

### Entities

1. S - Scope
    -> P(S) - parent scope

2. V - Variable

### Operations

1. `v2 = v1`
2. `v2 = move v1`
3. ``
