# Trait System

Traits are characteristics that any value can have. They are a way to propagate information
about values implicitly.

The type system would be built upon the trait system. The trait system would also allow stuff like
side-effect checks, etc.

## How would it work?

A new trait system would be implemented by implementing an object with the following methods:
```
# This implements a trait that detect if a value may have side effects or not
PuritySystem = Object(
  inferValue = (value) {
    # Check if value is pure and return `true` if it is, or `false` if it isn't.
    # This method will be called on simple values (PureValue).
    # Whatever the method returns will be attached to the value as metadata.
  },
  
  inferCall = (method, arguments) ...,
  inferBlock = (values) ...,
  
  
)
```
