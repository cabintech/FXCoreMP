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
 
The FXCoreMP macro language supports the following statements:
| Syntax                                         | Description            |
|------------------------------------------------|------------------------|
| ```$macro <name>(argname1,argname2,...)```<br>```$endmacro``` | Macro definition       |
| ```$<name>(arg1,arg2,...)```                         | Macro invocation       |
| ```$include <filename>```<br>```#include <filename>```  | Imbed a file           |
| ```$set <envparm>=<value>```                           | Set env value          |
| ```$if <envparm>=<value>```                          | Conditional processing |

## Macro Definitions
A macro definition specifies the name of the macro, the number and names of any arguments, and 1 or more lines of macro text.
A macro starts with the $macro statement, followed by a name, optional arguments, and macro (substitution) text.

When processing an input file
the processor reads macro definitions but does not write them to the output (e.g. they are removed from the source
text). A macro *invocation* supplies arguments for the macro and is replaced in the output with the results of
evaluating the macro text and substituting the arguments and (possibly) evaluating other macros. This is sometimes
referred to as "macro expansion". Macro may be "nested" - e.g. the macro definition text may include invocations
of other macros.

### Inline Macro Definitions
A simple macro with no arguments can be defined on a single line:
```
$macro PI 3.14
```
This creates a macro named "PI". When this macro is invoked, the invocation will be replaced with the macro text "3.14". The substitution will be done
*inline*, meaning that the text resulting from the evaluation (in this case "3.14") will exactly replace the macro
invocation, and any text on the same line before or after the invocation will be preserved. *Multi-line* macros (described
below) replace the entire source line with one or more macro lines.

The macro definition may specify one or more argument name. Each argument must be supplied on the macro 
invocation (either by name, or by position -- see the invocation section below). Macro names follow the usual rules
of variable naming (must start with a letter, cannot contain whitespace or other special characters).
```
$macro MS_TO_SAMPLES_48K(msec)  ((${msec}/1000)/(1/48000))
```
This macro is defined to have one argument named "msec". The value of an argument supplied on an invocation will
be substituted into the macro text when it is found enclosed in "${" and "}" as shown above. (Note that unlike
the C preprocessor, macro argument replacement has a different syntax than macro invocation so the intent is
clear, and in fact an argument may have the same name as another macro with no ambiguity). 

There is no special syntax required to concatenate two arguments in the output text:
```
$macro MAKE_LABEL(prefix, name, postfix)  ${prefix}${name}${postfix}
```
The C preprocessor requires elaborate syntax to make adjacent concatenation like this. If this macro were
invoked with the argument values "a", "b", and "c" the result would be "abc".

### Multi-Line Macro Definitions

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

## Macro Invocation (Evaluation)

A macro invocation is indicated in the source text by a "$" character followed by the macro name, 
optionally followed by a comma-delimited list of values for the macro arguments. For example:

```
$MULT_16(r0, r1, r8)
```

Macro expansion starts with substituting the macro arguments with their
place holders ${argname} in the macro text. Then any macro invocations in the macro text (e.g.
nested macros) are expanded. And if a nested macro itself has macro invocations, they are also
expanded. Finally the macro invocation text is removed and replaced with the
substituted and expanded macro text. For a multi-line macro, the entire source line is
removed and replaced. Inline macros retain the surrounding text on the invocation line.

Since the MULT_16 macro definition is multi-line, the line containing the
macro invocation would be replaced with the following lines:

```
sl       r0,16          ; Move arg1 to upper 32 bits 
cpy_cc   r8,acc32    ; Save in temp 
sl       r1,15          ; Move arg 2, not sure why 15 instead of 16 bits 
multrr   acc32,r8    ; acc32 = upper 32 bits of 64 bit result
```

If a macro has no arguments the invocation may specify an empty list "()" or omit the list
all together. The following invocations have the same effect:

```
$PI
$PI()
```

In some rare cases it will be necessary to use the empty argument list to delimit the macro
name. If the macro invocation is immediately followed by a character which is a valid macro
name character (e.g. letter or numeric) then the parens are necessary. For example if the PI
macro is to be followed immediately by the letter "R", then this invocation will not work:

```
$PIR
```

The macro processor would fail to find a macro named "PIR". In this case the empty argument
list will make it unambiguous:

```
$PI()R
```
This will correctly substitute the PI macro expansion, followed by the letter "R" for a result of:

```
3.14R
```

Macro argument values may be supplied in a positional or named format. Most common is the positional
form where the supplied values are applied to the macro's argument list in the order they appear in
the macro definition. This good when there are few arguments and their usage is clear, such as:

```
$DIV(x,y)
```

It is reasonably clear that "x" will be the numerator and "y" the divisor. However, when there is 
a long list of arguments and especially when multiple registers are passed as the
values, the code becomes unclear:

```
$CALC_DELAY(r0, r6, r8, r9, r12)
```

It would be necessary to find and read the macro definition to have any idea
about what the arguments are, and if the correct registers are being used. In this case the code
can be made more readable by using named arguments in the invocation:

```
$CALC_DELAY(buffer_base=r0, offset=r6, cv=r8, tempReg1=r9, tempReg2=r12)
```

This makes for more self-documenting code, at the expense of a bit more typing. When using named
arguments the order is not important, values are assigned to macro arguments by name, not position. The
following is exactly equivalent to the example above:

```
$CALC_DELAY(tempReg1=r9, tempReg2=r12, offset=r6, buffer_base=r0, cv=r8)
```

Currently the macro processor allows a mix of named and positional values in a macro
invocation, but it is strongly discouraged as it can have unintended results. A future version
may disallow mixed form invocations.
