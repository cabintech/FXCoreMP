# FXCoreMP

This processor implements a macro-substitution language for the [FXCore DSP Assembler](https://www.experimentalnoize.com/product_FXCore.php) similar to
how the C/C++ preprocessor adds macro capabilities to C and C++ language files. Before the development
of this processor, an [attempt was made to leverage the C preprocessor](cpp.md) for this purpose. Shortcomings of that
approach inspired the creation of this preprocessor specifically designed for the FXCore assembly language.

*[Why?](why.md)*

This processor reads an FXCore assembler source file (usually named "*.fxc") and writes an output file which is
a modified version of the input. Modifications are made by the processing of macro statements in the source
file. The FXCoreMP processor is a textual substitution system, it does not evaluate mathematical expressions or
perform higher level functions. Although the macro nesting and arguments can be elaborate, ultimately it results
in text substitution. There is some limited conditional processing (see `$set` and `$if` statements).

Where possible FXCoreMP treats macro names, argument names, IF conditions, etc. as *case-insensitive*.
 
The FXCoreMP macro language supports the following statements:
| Syntax                                         | Description            |
|------------------------------------------------|------------------------|
| ```$macro <name>(argname1,argname2,...)```<br>```$endmacro``` | Macro definition       |
| ```$<name>(arg1,arg2,...)```                         | Macro invocation       |
| ```$include <filename>```<br>```#include <filename>```  | Imbed a file           |
| ```$set <envparm>=<value>```                           | Set env value          |
| ```$if (<envparm>=<value>)```<br>```$if (<envparm>!=<value>)```<br>```$endif```     | Conditional processing |

## Macro Definitions
A macro definition specifies the name of the macro, the number and names of any arguments, and 1 or more lines of macro text.
A macro starts with the $macro statement, followed by a name, optional arguments, and macro (substitution) text.

When processing an input file
the processor reads macro definitions but does not write them to the output (e.g. they are removed from the source
text). A macro *invocation* supplies arguments for the macro and is replaced in the output with the results of
evaluating the macro text and substituting the arguments and (possibly) evaluating other macros. This is sometimes
referred to as "macro expansion". Macros may be "nested" - e.g. the macro definition text may include invocations
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
source line with one or more macro lines. A multi-line macro starts with `$macro` followed by the macro name,
optional list of arguments, and a "++" continuation indicator. The following source lines, up to `$endmacro`
constitute the macro text.

```
$macro MULT_16(cr1, cr2, crTemp) ++
sl       ${cr1},16          ; Move arg1 to upper 32 bits 
cpy_cc   ${crTemp},acc32    ; Save in temp 
sl       ${cr2},15          ; Move arg 2, not sure why 15 instead of 16 bits 
multrr   acc32,${crTemp}    ; acc32 = upper 32 bits of 64 bit result
$endmacro
```
### Virtual Macro Arguments

In addition to the arguments defined in the macro definition, all macros have access to a set
of 'virtual' arguments which use the same substitution syntax but are not explicitly passed
on the macro invocation. These arguments provide access to special values created by the macro
processor. Virtual macro argument names start with ":". Each is described below.

#### ${:unique}
This substitutes a unique numeric value for each invocation of a macro. If this
is used more than once in a macro definition it will substitute the same value each time. This 
is useful to generate jump labels and unique names so that a macro may be used more than once
in a single source file without creating duplicate labels and identifiers.

For example, the following macro generates code that contains a **jmp** instruction and the
target location for it.

```
; Macro to subtract a fixed number of samples from the delay stored
; in an MR. If the result is less than zero, the delay is set to zero.
;
$macro SUBTRACT_DELAY(delayMR, samples, crTemp) ++
;---START SUBTRACT_DELAY
cpy_cm    ${crTemp}, ${delayMR}    ; Get calculated delay
wrdld     acc32, samples           ; Get number of samples to deduct
subs      ${crTemp}, acc32         ; Subtract deduction delay
jgez      acc32, sub_ok_${:unique} ; If positive, continue
xor       acc32, acc32             ; If neg, set to zero

sub_ok_${:unique}:
cpy_mc ${delayMR}, acc32          ; Store back adjusted delay
;---END SUBTRACT_DELAY

$endmacro
```

Note the use of `${:unique}` to make the jump target and the corresponding label unique
so this macro can be used multiple times in the same source (or included) file. Since the
substituted value is across all macros and all invocations, it is OK if two different macros
use the same generated label names (e.g. another macro could define a label `sub_ok_${:unique}`
and it would not create any conflict with the macro above).

#### ${:sourcefile}
This substitutes the name of the source file where this macro definition was
created. This is only the file name and does not include the path.

#### ${:sourcefile:root}
This substitutes the name of root source file being processed (e.g. the
first file on the command line). This is only the file name and does not include the path.

#### ${:outputfile}
This substitutes the name of the output file (e.g. the
second file on the command line). This is only the file name and does not include the path.

## Macro Invocation (Evaluation)

A macro invocation is indicated in the source text by a "$" character followed by the macro name, 
optionally followed by a comma-delimited list of values for the macro arguments. Macro
names are *case-insensitive*. For example:

```
$MULT_16(r0, r1, r8)
```

Macro expansion starts with substituting the macro arguments with their
place holders ${argname} in the macro text. Then any macro invocations in the macro text (e.g.
nested macros) are expanded. And if a nested macro itself has macro invocations, they are also
expanded. Finally the macro invocation text is removed and replaced with the
substituted and expanded macro text. For a multi-line macro, the entire source line is
removed and replaced. Inline macros retain the surrounding text on the invocation line.

By the example above, the MULT_16 macro definition is multi-line, the line containing the
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
#### Positional and Named Argument Values
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
can be made more readable by using named arguments in the invocation. A named argument is the
argument name (from the macro definition) followed by the "=" character, and then the 
argument value.

```
$CALC_DELAY(buffer_base=r0, offset=r6, cv=r8, tempReg1=r9, tempReg2=r12)
```

This makes for more self-documenting code, at the expense of a bit more typing. When using named
arguments the order is not important, values are assigned to macro arguments by name, not position. The
following is exactly equivalent to the example above:

```
$CALC_DELAY(tempReg1=r9, tempReg2=r12, offset=r6, buffer_base=r0, cv=r8)
```

Argument names must match the names used in the macro definition, but are *not* case sensitive.
Positional and named arguments cannot be mixed in the same macro invocation.

## SET/IF Conditional Processing

The `$set` and `$if` statements (along with command-line parameters) allow for conditional inclusion/exclusion of
blocks of lines from the source file. These statements cannot be used inside a macro. There are
multiple use cases but a common use is to conditionally include (or exclude) code based on some
value passed into the preprocessor at compile time. For example, it might be useful to include
extra debug code for development builds, but exclude that code when assembling for production distribution.

These statements operate on macro 'environment' variables defined either by `$set` statements or values passed
on the command line. (Not to be confused with operating system environment variables, these are
specific to the macro processing system). Macro environment variables have a name and value. If a variable has not been defined
(e.g. no `$set` statement has created it, and it was not specified on the command line) then it's value
is assumed to be an empty string. Macro environment names and values are *case-insensitive*.

The `$set` statement has the following syntax:

```
$set <name>=<value>
```

The environment variable of the given name will be assigned the value on the right side of the "=". The
value will be trimmed of leading and trailing whitespace, and includes everything up to a comment
or the end of the line.

This is a simple text assignment, the value is not interpreted in any way (it cannot be a macro invocation).
Alternatively, an environment variable may be assigned a value by passing an argument on the end of the command
line when starting the processor. Values assigned on the command line must be simple strings with no embedded whitespace.

```
java ... -Ename1=value1 -Ename2=value2
```

In the following example, a macro is defined one of two ways depending on a macro environment variable:

```
$if (debug=true)
$macro GENERATE_DEBUG_DATA(fromLoc, length) ++
;
; ... code to generate some debug info ...
;
$endmacro
$endif

$if (debug!=true)
$macro GENERATE_DEBUG_DATA(fromLoc, length) ++
; Do nothing
$endmacro
$endif
```

When this macro is evaluated it will either create the debug generation code, or just a comment line depending on
the macro environment variable. So invoking the processor with:

```
java ... -Edebug=true
```

will cause the macro to expand to the debug code, otherwise it will expand to a single comment line.

Currently, nested **$if** statements are not supported.

# INCLUDE Statement

The `$include` statement is used to embed lines from an external file into the source file (similar to the
C preprocessor *#include* directive). Once embedded they are treated
the same as original source (e.g. they are scanned and processed for macro statements). An included file itself may include additional files.
A file will be included only once (no need for conditional processing as in the #include C preprocessor directive). In the scope
of a single execution of the processor, a file will be included only once. Any additional includes of that file will be
skipped.

Included files are not limited to containing macro definitions, they may include executable assembler instructions as well. They are
inserted in place of the `$include` statement and then processed like all other source code.

Syntax:

```
$include filename
```

The file name is relative to the directory of the root source file being processed (e.g. the file
specified as the first parameter of the command line). So if a plain file name is given with
no path, it will be located in the same directory as the root source file.

# Limitations
Some known limitations of the macro processor:

1. Block comments using `/* comment */` that span multiple lines inside a macro definition do not appear in the expanded
macro output. Single line comments using those delimiters will appear in the expanded output.
2. Macro definitions may not appear inside other macro definitions. 
3. Macro invocations may be nested to any level
but must not be recursive (e.g. a macro must not invoke itself directly or or indirectly through other macros).
4. Nested `$if` statements are not supported.

# More Info

See these additional pages for more information:

[How to run the FXCoreMP macro processor](usage.md)<br>
[Examples](examples.md)
