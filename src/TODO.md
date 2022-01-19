- [Interpreter] Need to at least partially evaluate all values - currently types / functions in Class.new are never evaluated
- [Value] The toUValue function assumes value will not be evaluated compile-time - need to encode method traits as well

- [Compiler] Detect if something is a value or needs a statement, to define a variable during call
- [Compiler] Support named arguments -> need to get typeCheck to return parameter types as well
- [Compiler]