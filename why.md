# Why FXCoreMP?

*FXCoreMP* was created to make some FXCore programming tasks easier. The FXCore preprocessor and assembler
supplied by [Experimental Noize](https://www.experimentalnoize.com/product_FXCore.php) have a lot of useful 
features, but to some degree they are oriented around
the graphical program builder (Gooeycore). From a professional software development perspective a 
text-based programming environment is preferred and
the toolset lacked some features a long-time programmer might expect in a software (or firmware) development
platform. Since this project's inception the FXCore toolset
has improved in many areas, but we still find this macro capability very useful for non-Gooeycore FXCore programming.

Note that FXCoreMP *complements* the FXCore toolset, it does not replace it or disable any of its features.
Programs written using FXCoreMP macros can also leverage all of the capabilities of the FXCore
preprocessor (library functions) and the assembler itself.

In general our goal has been improve the FXCore text-based programming experience. We have found it useful for
creation of constructs that are a bit closer to
higher level language programming while abstracting away details when possible. For example it is possible to
create a macro that emulates a high level language 'switch' style statement that branches to
one of several target locations based on the value of a variable (register). Of course at the basic level it
is still assembler language with the limitations of the hardware instruction set (like no backward branching, thus no looping constructs). We use
FXCoreMP macros to create moderately complex data structures in Memory Registers which improves readability
and insures the MRs are initialized and used correctly. We use conditional inclusion (`$if`) to enable
diagnostics code for development builds and for building multiple variations from common source code.

The lack of looping capability in the instruction set makes it important to be able to easily reuse large
blocks of code. For example if a complex operation needs to be done (say) on 3 different delay buffers,
there is no way to write code once and use a FOR loop to run it multiple times. The code must be
physically duplicated, and each copy must be customized to some degree (different buffer address, maybe
different parameters and control signals). It is problematic for maintenance and enhancements to have
so much duplicated code. FXCoreMP macros are ideally suited to these scenarios. The code
block can be written and tested, then easily turned into a macro by adding 2 lines of code, and then
invoked multiple times to create multiple expansions of the code block with customizations as needed by
macro arguments. All this is can be done in a single source file with minimal extra syntax.

There are other programming language macro processors, why create a new one? The first version of 
this project used the C language preprocessor (CPP) which is well known in the software community. However
after extensive use it was determined to have too many idiosyncratic behaviors that did not fit well
with the FXCore toolset or assembler language. Since the FXCore assembler syntax is relatively simple
and line-oriented, it was not difficult to implement a macro language on top of it without
elaborate parsing and still preserve the functionality of the FXCore preprocessor (library functions).

## When FXCoreMP?

There is some overlap in function between the FXCore preprocessor and it's library functions, the
assembler and it's .equ statement, and FXCoreMP macros. All of them can do some form of 
substitution or replacement. Some situations in which FXCoreMP can make FXCore programming easier:

1. FXCoreMP has a much simpler syntax for defining macros that
looks the same as normal assembler code and can be mixed into normal source code files.
FXCore preprocessor library functions (the equivalent of macros) are in separate files with a 
different format (XML). Note however, FXCoreMP macros are not directly usable in Gooeycore like
preprocessor library functions.
2. Arguments for FXCoreMP macros can be positional or named. The named format for macros
with many parameters can greatly increase the readability of the source code (see [Named Arguments](README.md#named-args)).
3. FXCoreMP macros can reference labels outside the macro. The sample SWITCH macros show how
target label names can be passed as macro arguments, something that is not possible with
the preprocessor library functions which have strict self-containment rules and lack general
text substitution capabilities.
4. FXCoreMP provides an `$include` statement to allow organization of source files in
complex projects with shared common code. The `$include` mechanism is simple and familiar to many
programmers.
5. FXCoreMP provides a means for conditional inclusion/exclusion of code depending on values passed
in on the command line or from `$set` statements. This makes it easy to support different build
scenarios (debug/test/prod) or control inclusion of optional features or experimental code.

### .EQU or inline macro?

For simple substitution, FXCoreMP inline macros and the FXCore assembler `.equ` statements differ in the syntax
and in how/when the substitution is done. The assembler `.equ` statement can evaluate
a complex mathematical expression and bitwise operations, using the resulting value wherever
the symbol appears elsewhere in the source code. An FXCoreMP inline macro is just direct text
replacement and does not evaluate any expressions, so substitutions elsewhere replace the symbol
with the full text of the expression, leaving it to the assembler to evaluate it in-place. So for example, consider the following
source code:

```
$macro			VALUE1		((4*48)<<8)|3
.equ			VALUE2		((4*48)<<8)|3

wrdld			r0			$VALUE1
wrdld			r1			VALUE2
```

After processing by FXCoreMP and the FXCore preprocessor, the source sent to the assembler would be:

```
.equ			VALUE2		((4*48)<<8)|3

wrdld			r0			((4*48)<<8)|3
wrdld			r1			VALUE2

```

On the first WRDLD statement the assembler would evaluate the expression and build an instruction to
load the constant value 195 into the upper 16 bits of r0. For the second one, the assembler would have
already resolved VALUE2 to be 195 and thus load that into the upper half of r1. The result is the same,
the only difference is when the expression is evaluated. In some cases it may be preferred (or even
necessary) to use `.equ` statements to pre-evaluate expressions when the value is used in a context
that does not support expressions. The use of `.equ` also improves the ability to read/debug the final
assembler code because is preserves the logical name in the source code (e.g. in the example above,
the second WRDLD is more readable because it still has the symbolic name VALUE2. The macro
symbol VALUE1 has disappeared from the source code).

In general, for substitution of mathematical expressions and simple values, it is preferred to use the assembler `.equ`
statements instead of one-line macros. FXCoreMP macros may be preferred when the substitution requires
arguments or multiple lines of code.
