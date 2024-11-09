## Why FXCoreMP?
Discuss diff with FXCore macro libraries, use of pure-text substitution, ability to reference
labels outside the macro, conditional processing, goal to allow more HLL (High Level Language) type constructs. Note also this 
processor does no type checking, there is no inherent notion of "input" or "output" arguments like
FXCore libraries -- everything is just text. Note that (with recent releases) it can be better to use
FXCore ".equ" statements instead of simple inline macros. ".equ" does real expression evaluation at compile time
with lots of useful math and bitwise operators.