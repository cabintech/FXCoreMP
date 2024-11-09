# FXCoreMP
FXCore DSP Macro Preprocessor

This processor implements a macro-substitution language for the FXCore DSP assembler similar to
how the C/C++ preprocessor adds macro capabilities to C and C++ language files. Before the development
of this processor, an attempt was made to leverage the C preprocessor for this purpose.
 
The syntax of this macro language leverages the fact that the FXCore assembly language is farily simple
Syntactically. It does not use the "$" character for any purpose, so it is safe and easy to use it for
macro syntax (similar to how "#" is used by the C preprocessor).

There are two types of substitution done in the macro processing. When a macro is evaluated, any arguments
supplied on the evaluation are substituted into the macro text using the ${arg-name} syntax. This fully
delimited syntax avoid any ambiguity about where the argument name ends, so it is possible for two
arguments to be directly adjoining with no ambiguity (unlike the C preprocessor where you have to jump through
syntactic hoops to concatenate substituted values). 
