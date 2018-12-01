# Compiler Internals

## Overview

1. Lexing - splits text into tokens.
2. Parsing - generates AST. At this point, types are just AST expressions.
3. Phase 1 (type partial evaluation):
    1. Walk over the ASTs and:
        1. Read definitions:
            1. Structs
                1. Evaluate types
            2. Interfaces
                1. Evaluate types
            3. Methods
                1. Evaluate types
                2. Detect root type methods somehow (will be used for type naming and generic matching)
4. Phase 2 (type checking):
    1. Walk over ASTs:
        1. Type check method code
        2. Fill-in AST types
5. Phase 3 (partial evaluation)

## Evaluate type AST to a `Type` struct

1. Partially evaluate AST without evaluating root-type methods
    1. Root-type methods are ones which contain struct definitions directly

## Method definition evaluation

1. Evaluate argument types
2. Write to the current module

## Partial evaluation

Blocks can be partially evaluated
