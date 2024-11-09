# FXCoreMP
FXCore DSP Macro Preprocessor

This processor implements a macro-substitution language for the [FXCore DSP Assembler](https://www.experimentalnoize.com/product_FXCore.php) similar to
how the C/C++ preprocessor adds macro capabilities to C and C++ language files. Before the development
of this processor, an [attempt was made to leverage the C preprocessor](cpp.md) for this purpose. Shortcomings of that
approach inspired the creation of this preprocessor specifically designed for the FXCore assembly language.

This processor reads an FXCore assembler source file (usually named "x.fxc") and writes an output file which is
a modified version of the input. Modifications are made by the processing of macro statements in the source
file. The FXCoreMP processor is a textual substitution system, it does not evaluate mathematical expressions or
perform higher level functions. Although the macro nesting and arguments can be elaborate, ultimately it results
in text substitution. There is some limited conditional processing (see $set and $if statements).
 
The FXCoreMP macro language supports macro *definitions* and macro *invocations*.

## Macro Definitions
A macro definition specifies the name of the macro, the number and names of any arguments, and 1 or more lines of macro text.
A macro starts with the $macro statement, followed by a name, optional arguments, and macro (substitution) text. A simple
macro with no arguments can be defined on a single line:

>$macro PI 3.14

This creates a macro named "PI". When this macro is invoked, the invocation will be replaced with the macro text "3.14". The substitution will be done
*inline*, meaning that the text resulting from the evaluation (in this case "3.14") will exactly replace the macro
invocation, and any text on the same line before or after the invocation will be preserved. *Multi-line* macros (described
below) replace the entire source line with one or more macro lines.

The macro definition may specify one or more argument name. Each argument must be supplied on the macro 
invocation (either by name, or by position -- see the invocation section below). Macro names follow the usual rules
of variable naming (must start with a letter, cannot contain whitespace or other special characters).

>$macro MSTOSAMPLES48K(msec)	((${msec}/1000)/(1/48000))

This macro is defined to have one argument named "msec". The value of an argument supplied on an invocation will
be substituted into the macro text when it is found enclosed in "${" and "}" as shown above. (Note that unlike
the C preprocessor, macro argument replacement has a different syntax than macro invocation so the intent is
clear, and in fact an argument may have the same name as another macro with no ambiguity). 

There is no special syntax required to concatenate two arguments in the output text:

>$macro MAKE\_LABEL(prefix, name, postfix) 	${prefix}${name}${postfix}

The C preprocessor requires elaborate syntax to make adjacent concatenation like this. If this macro were
invoked with the argument values "a", "b", and "c" the result would be "abc".

A macro definition may define a *multi-line* (as opposed to *inline*) macro. A multi-line macro replaces the entire
source line with one or more macro lines. A multi-line macro starts with "$macro" followed by the macro name,
optional list of arguments, and a "++" continuation indicator. The following source lines, up to "$endmacro" 
constitute the macro text.

```
$macro MULT_16(cr1, cr2, crTemp) ++
sl       ${cr1},16          ; Move arg1 to upper 32 bits 
cpy_cc   ${crTemp},acc32    ; Save in temp 
sl       ${cr2},15          ; Move arg 2, not sure why 15 instead of 16 bits 
multrr   acc32,${crTemp}    ; acc32 = upper 32 bits of 64 bit result
$endmacro
```





When processing an input file
the processor reads macro definitions but does not write them to the output (e.g. they are removed from the source
text). A macro invocation supplies arguments for the macro and is replaced in the output with the results of
evaluating the macro text and substituting the arguments and (possibly) evaluating other macros. This is sometimes
referred to as "macro expansion". Macro may be "nested" - e.g. the macro definition text may include invocations
of other macros.

There are two types of substitution done in the macro processing. When a macro is evaluated, any arguments
supplied on the evaluation are substituted into the macro text using the ${arg-name} syntax. This fully
delimited syntax avoid any ambiguity about where the argument name ends, so it is possible for two
arguments to be directly adjoining with no ambiguity (unlike the C preprocessor where you have to jump through
syntactic hoops to concatenate substituted values). 
