# Intermediate representations

    [ Photon code ]
           |
           | Lexing
           | Parsing
           |
    [     AST     ]
           |
           | Type checking and inference
           |
    [     IR      ]
           |
           |
    [   LLVM IR   ]
           |
    [   Assembly  ]

## IR

For IR, the following transformations are done:

1. Types are made explicit.
2. Method calls are made explicit.

## References

- https://blog.rust-lang.org/2016/04/19/MIR.html
