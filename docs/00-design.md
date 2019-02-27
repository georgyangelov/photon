# Language Design

This document describes the general ideas behind Photon.

## Ideas

A central idea of Photon is to be a language based on partial compilation and metaprogramming. Goals:

- Photon should be able to execute code both during compilation and during runtime.
- A Photon compiler should be able to be implemented mostly in Photon itself, even without bootstrapping.
- Photon primitives (modules) should be able to be augmented in every program using metaprogramming.

## Bootstrapping and partial evaluation

A Photon compiler must only implement a small subset of the Photon language, called PML (Photon Minimal Language). Then, using PML, new constructs may be added to the language in this language subset, gradually expanding the supported constructs via metaprogramming / macros.

To achieve the goal of implementing most of Photon in Photon itself, a partial evaluation approach will be taken. A Photon compiler will take the following high-level steps:

1. Lexing PML
2. Parsing PML
3. Partial evaluation
4. PML compilation

Each of these steps is described in a separate section below.

### Lexing PML

Lexing is the easiest part of a Photon compiler as the goal of the language is to have a very small number of constructs.
