## Why FXCoreMP?
Discuss diff with FXCore macro libraries, use of pure-text substitution, ability to reference
labels outside the macro, conditional processing, goal to allow more HLL (High Level Language) type constructs. Note also this 
processor does no type checking, there is no inherent notion of "input" or "output" arguments like
FXCore libraries -- everything is just text. Note that (with recent releases) it can be better to use
FXCore ".equ" statements instead of simple inline macros. ".equ" does real expression evaluation at compile time
with lots of useful math and bitwise operators.

Diffs with preprocessor

PP uses @ syntax to expand a library function with a 2 level naming hierarch (@libraryname.functionname).

PP related functions are defined in a Library which is coded as a nested XML structure and tags, with assember
code embedded in <code></code> tags. MP related macros
can can be defined in a single file with simple $macro statements and looks just like
regular assembler code (in fact the Notepad++ syntax highlighting works fine on such files).

PP library functions define the function arguments with a name, type, usage (in/out), and description. Much
of that meta information is for use by the Gooycore graphical editor. MP macro arguments have only a name.
MP does not know of, or enforce any argument types or have any notion of "input" versus "output" values.

Both support the notion of unique identifiers so that if a macro is used more than once in a single source
file it will generate unique names (usually labels) for each expansion. PP does this automatically with
*jmp* targets and memory declarations. MP does this with a virtual macro argument ${:unique} which can
be used in any way (e.g. prefix or postfix on any arbitrary text in the macro).