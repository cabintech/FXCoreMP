# Target Of Operations Notation (TOON)

The FXCoreMP macro processor enables an alternative assembler language syntax that is more
expressive, simplifies common operations, and makes it easier to understand the 
sequence of operations and branching that makes up an FXCore program.

The TOON syntax can be freely mixed with regular FXCore assembler language syntax, so it
can be used exclusively, sparsely, or not at all. The macro processor will translate all
TOON statements into valid FXCore assembler.

TOON processing is a separate operation on the source code.
TOON syntax can be used with or without macros, and macros can be used with or without
TOON statements. By default TOON translation is run as the last step of the macro
processor, but it can also be run as a stand alone tool.

We found that re-formatting our existing FXCore assembly code into TOON statements has
improved our ability to modify, maintain, and develop new code features with
fewer errors and less
time consulting the FXCore instruction set documentation. Data movement and code
branching become more apparent which makes the code easier to understand (especially when coming
back to FXCore development after being away from it for a while).

## Target of an instruction

The original goal of TOON (and from which it derives its name) was to make the assembly language more explicit about the target of
machine operations. Many FXCore instructions have implied side effects that are not
evident in the assembler statement, in particular the target of the operation. For example:
```
xori r8,0x0F
```

It might be assumed by a casual reader that after this operation the value of R8 would have been XOR'ed
with 0x0F. But that is not the case, R8 remains unchanged and the result of the XOR is
written to the accumulator (ACC32). This is not uncommon in machine architectures but the
assembly language does not indicate the target of the operation. To know that, the programmer
must be either very familiar with the machine instructions, or constantly referencing
the documentation to know what is the target of an operation, and sometimes the ordering
of the operands.

TOON seeks to make the target of operations explicit so reading source code is more
self-explanatory and unambiguous. The TOON syntax for the above statement would be:
```
acc32 = r8 xor 0x0F
```

This makes it clear that the result of this operation is placed in (assigned to) the
ACC32 register, and by placing the opcode (mnemonic) between the operands the statement is in a
more natural language form "R8 is XOR'ed with 0x0F and assigned to ACC32". This reads
more like a high level computer language and the operation and results can be fully
understood without consulting the details of the <code>xor</code> machine instruction.
<blockquote>
  The experienced FXCore programer will notice that the TOON statement appears to be
  incorrect because it is using the <code>xor</code> instruction when the right-hand operand is an immediate (constant)
  value - the <code>xori</code> instruction would be expected. The TOON processor analyizes the
  operand types and infers the correct instruction. TOON will generate the <code>xori</code> instruction
  for the above assignment. See the [syntax reference] for other instructions that can
  be inferred.
</blockquote>

See [32-Bit Operations](#32-bit-math-shift-logic-functions) for more details on these types of TOON statements.

The TOON processor will validate that the target of such operation assignment statements are valid
for the operation being performed. E.g. the XOR operation can only target the
32-bit accumulator, so if anything other then "acc32" appears as the target of that
instruction, an error will be flagged.

Because many FXCore instructions (implicitly) target ACC32 it may seem tedious and
unnecessary to write "acc32 =" over and over in the source code. It does however
allow many operations to be written in familiar programming assignment form and
has many benefits for readability of the program source.

Since its inception, TOON has expanded to improve the readability of many FXCore
assembly instructions by recasting them into forms similar to high level
languages. It should be noted that TOON is a 1:1 translator, each TOON statement
is translated into exactly one FXCore assembly instruction. Although the syntax may
be easier to understand, a through understanding of FXCore instructions is still required.

## Readability of TOON statements

TOON formated code can be (subjectively) easier to read and understand-at-a-glance. TOON seeks to make
the semantics of assembler code expicit, clear, and intuitive for progammers. Traditional assembler code
uses the same basic `opcode operand,operand` format for all statements no matter what they do. 
There is no visual distinction between assignments, data operations, conditional branches, etc. In higher
level languages those constructs have very different syntax. This helps with intuitive 
understanding of the code structure. Not having that syntactic visual aid makes assembler harder
to read and understand. (For more on the concepts of 'high level assembler' see the [Backgound](#background) section).

For example we often want to scan a block of code and know where (or if) a particular register is being modified.
Scanning traditional assembler code requires careful reading to find where registers are
being updated (explicitly in the list of operands, or implicitly by the definition of the instruction).

Consider the following assembler code:

```
.rn temp  r0
.rn temp1 r1
.rn scaleFactorMin r2
.rn scaleFactorMax r3
.rn scaleFactorRange r4
.equ delayLen 512
.mem delaymem0 delayLen
.mem delaymem1 delayLen

1.  cpy_cs      acc32,in0
2.  wrdel       delaymem0,acc32
3.  cpy_cs      acc32,in1
4.  wrdel       delaymem1,acc32
5.  cpy_cs      temp,pot0_smth
6.  wrdld       temp1,delayLen
7.  multrr      temp,temp1
8.  cpy_cc      scaleFactorMin,acc32
9.  cpy_cs      temp,pot1_smth
10. wrdld       temp1,delayLen
11. multrr      temp,temp1
12. cpy_cc      scaleFactorMax,acc32
13. subs        scaleFactorMax,scaleFactorMin
14. jgez        acc32,save_range
15. cpy_cc      temp,scaleFactorMax
16. cpy_cc      scaleFactorMax,scaleFactorMin
17. cpy_cc      scaleFactorMin,temp
18. abs	        acc32
19. save_range:
20. cpy_cc      scaleFactorRange,acc32
```

Is the register named `scaleFactorMax` modified by this code? If so, where? 
To answer those basic questions require some careful reading of the code. The symbol `scaleFactorMax` appears in several
places, but do any of those update it's value? Even finding that symbol in all the lists of operands takes some careful reading.

That same block of code in TOON format:

```
.rn temp  r0
.rn temp1 r1
.rn scaleFactorMin r2
.rn scaleFactorMax r3
.rn scaleFactorRange r4
.equ delayLen 512
.mem delaymem0 delayLen
.mem delaymem1 delayLen

1.  acc32          = in0
2.  (delaymem0)    = acc32
3.  acc32          = in1
4.  (delaymem1)    = acc32
5.  temp           = pot0_smth 
6.  temp1.u        = delayLen
7.  acc32          = temp mult temp1
8.  scaleFactorMin = acc32
9.  temp           = pot1_smth
10. temp1.u        = delayLen
11. acc32          = temp mult temp1
12. scaleFactorMax = acc32
13. acc32          = scaleFactorMax subs scaleFactorMin
14. if acc32 >=0 goto save_range
15. temp           = scaleFactorMax
16. scaleFactorMax = scaleFactorMin
17. scaleFactorMin = temp 
18. acc32          = abs acc32
19. save_range: 
20. scaleFactorRange = acc32
```

This is recognizable as a series of assignment and conditional branching statements. Just scan the left column and where you
find `scaleFactorMax' you know that it is modified by that instruction (lines 12 and 16 are easy to spot).

The example also show some other features of the TOON syntax. 
* The assignment in line 1 is from an SFR (IN0) to a core
register (ACC32). TOON determins that the assembler instruction `cpy_cs` is required and generates the approprate
assembler statement. See [Assignment Statements](#Assignments) section below. The TOON assignment statement has
addtional capabilities:
  + In line 4, ACC32 is written to delay memory by an ''indirect'' constant address. Delay memory addressing is indicated by the parens.
  + In line 6, a 16 bit constant value is assigned to the upper part of register temp1. The ".U" postfix makes the semantics of
this special assignment clear.
* The multiply on line 7 uses `MULT` which is not an FXCore instruction but is understood by
TOON to implement a multiply operation on the 2 operands. TOON infers the proper FXCore instruction and generates
the proper assembly instruction (`multrr` in this case because both operands are core registers). There are several
such generic TOON operators to reduce the distracting detail in the code. See the [TOON Syntax Reference](Toon-Syntax-Reference) for other
inferred instructions.
* The conditional branch on line 14 is written as a familier "if" statement. (See [Branching](#Branching) section below).

Many of these features are described in the following sections.

## Assignment statements

The backbone of all computer languages is data movement, or 'copy/load/store' instructions. The
well understood syntax of using the `=` symbol to denote assignment is common in many
languages. However in FXCore assembler (and most assembler languages), copy instructions
adhere to the rigid `<opcode> <operand>,<operand>` format:
```
cpy_cm r8,mr41
```

TOON makes special provisions for copy operations, expressing them in more
familiar assignment syntax. The above would be written in TOON syntax as simply:
```
r8 = mr41
```

The mnemonic is removed from the syntax all together to make an instantly more
readable statement. The developer does not have to consider all the `cpy_XX` variations and think carefully
about the order of the operands (which has great significance for the copy instructions). Anyone
versed in software development of any kind will immediately understand the target is on the left, and
the source is on the right of the `=` symbol.

The TOON processor interprets the assignment statement and infers the correct instruction
from the type of the source and destination operands.

All the `cpy_XX` mnemonics can be replaced with TOON assignment statements even if the
operands are symbolic:
```
.rn    delayTime    r12
$macro sampleWindow mr41

delayTime = $sampleWindow
```

The TOON processor will generate the proper `cpy_cm` instruction in this example.
### Delay Memory Assignments
TOON provides special syntax for indirect copy operations to/from delay memory.
Parens are used indicate indirect delay memory operations such as:
```
(r0) = acc32
```
This assignment write the contents of the register ACC32 to the delay memory location
contained in R0. E.g. R0 is an indirect reference to a delay memory location. TOON will
generate a `wrdelx` instruction for this assigment. TOON supports all the FXCore delay
addressing modes:
* Immediate load <code>Rx = (addr)</code> or store <code>(addr) = Rx</code>
* Indirect load <code>Rx = (Ry)</code> or store <code>(Rx) = Ry</code>
* Absolute (no AGU) indirect load <code>Rx = @(Ry)</code> or store <code>@(Rx) = Ry</code>
### Memory Register Assignments
In addition to the simple assignment of memory registers to/from core registers, e.g.
```
mr101 = acc32
```
The FXCore also supports indirect reading (not writing) of memory registers. This is
represented in TOON in a similar way to indirect delay memory, but using square brackets
instead of parens:
```
r5 = [r1]
```
This load the memory register addressed by the content of R1 into register R5. Note that
the reverse is not supported:
```
[r5] = r1 ; NOT supported by FXCore
```

### 64/32 Bit Assignments
FXCore also provides instructions for transferring 32 bit words between core
registers and the upper and lower half of the 64 bit accumulator (ACC64). Rather than
lookup those nmemonics, TOON assignment statements can generate the proper assembler code
from easy to write assignment statements such as:
```
acc64.u = r5
```
The semantics are clear in the statement, the content of R5 is written to the upper
half of ACC64. Likewise simple assignements transfer data the other way:
```
acc32 = acc64.l
```
See the [Syntax Reference](#Syntax) for all the 64 bit assignment statements.

## 32-Bit Math, Shift, Logic functions
Data manipulation makes up the bulk of most FXCore assembler programs, so improving the intuitive reading and understanding of the
semantics from the source code is very useful.

TOON provides a more readable and intuitive syntax for the FXCore 32-bit data operations such as math functions, bitwise functions, and logic functions.
These statements are written as assignments with functional expressions because they both modify data and move (copy) it to a specific location (ACC32). The statement
syntax makes this implicit copy operation explicit in the assignment syntax. They also place the instruction (function) between the operands like:
```
acc32 = r4 xor r9
acc32 = acc32 sl r8 ; Correct instruction will be inferred
acc32 = r0 andi 0xF
```
This gives move of a high level programming expression syntax to these instructions. The function between the 2 operands can be any of the 
FXCore 32-bit math or logic instruction nmemonics. TOON extends some mnemonics to make them generic, and then generates a correct mnemonic
by inferrence from the operand types. For example the 2nd statement above shows the "SL" instruction but the right side is a register
so the correct mnemonic should be "SLR". TOON recognizes "SL" as a inferrable instruction and substitutes the correct mnemonic. The
32-bit data instructions that TOON can infer are:
```
OR
AND
XOR
SL
SR
MULT
```
See the [Syntax Reference](Syntax#32bit) for all the 32 bit operation statements.
(Note a future version may support symbols `+ & | * << >>` for common operations like ADD, AND, OR, MULT, SHIFT, etc).

## 64-Bit Summation Operations

The FXcore has a number of instructions that accumulate (sum) results in the 64 bit accumulator. These instructions
can be used in TOON statements with the same assignment-expression syntax as [32-Bit Operations](#32-bit-math-shift-logic-functions). However, for source
code clarity, a `+=` assignment operator can be used as a reminder that the operation is not a straight assginment of
the expression results, but it is also a summation (add) of the current ACC64 value. This type of syntax is common
in high level languages as a shorthand for writing `a = a + b`. Many languags allow this to be written as `a += b`.

These statement are written as assignments with the target of ACC64. The `+=` notation is optional, they can also
be written with just the `=` symbol:
```
acc64 += R0 macrr R1
acc64 += R8 machri -0.7
acc64 = r8 macr -0.3
```
We recommend using the `+=` symbol for the added clarity. 

Note that the last statement uses a non-existant
nmemonic `macr`. Similar to the 32 bit operations, some generic inferred functions are recognized by TOON
and the proper FXCore instruction is generated by inferrence from the operands. In the case of the last
statement above, TOON will generate the immediate instruction `macri`.

See the [Syntax Reference](Syntax#64bit) for all the 64 bit operation statements.

## Branching

Like assignments, basic assembler syntax for branching uses the same syntax as all other statements. TOON allows conditional
branching to be express in a more familiar `IF` statement syntax with clearly readable conditions. For example the
assembler statement:
```
jgez        acc32,save_range		 
```

is rewritten as:
```
if acc32 >=0 goto save_range
```

This TOON syntax is different than assignments and other operations, so the branch points in the code are obvious. The test
condition uses familer boolean expression operators and how it operates is clear from the statement format. The `goto` token in the IF statement is
optional and can be omitted. The condition can be written with boolean operators as shown above, or by using
the assembler mnemonic such as `jgez` or just the condition part of the mnemonic `gez` in place of the operator. The following are
all equivalent and produce the same assembler instructions:
```
if r9 >=0 goto save_range
if r9 gez save_range
if r9 jgez goto save_range
```
Of couse the choice of condition operators is limited to those directly supported by FXCore branch
instructions, so the only allowed operators are:
* <code>=0</code> The register is zero
* <code><>0</code> or <code>!=0</code> The regsiter is not equal to zero
* <code>>=0</code> The register is greater than or equal to zero
* <code><=</code> The register is less than zero (e.g.negative)
* <code>!=acc32.sign></code> The register does not have the same sign as ACC32
  
Note the operators are single glyphs (tokens) and must be space/tab separated from surrounding text. The following will not
be understood by TOON and will cause an error to be flagged:
```
if r9>=0 goto label ; Need space between register and operator
if r9 >= 0 goto label ; Operator must have no embedded spaces
```
(Future versions may relax some of these syntatic requirements).

Using this familiar IF statement coding syntax makes it easy to spot key code branching points without lots of comments,
blank lines, or indentation to visually highlight the code flow.

## Other Assembler Instructions
At this time there are some FXCore instructions for which no TOON syntax has been defined. These instructions should
be written as FXCore assembler statements:
```
apa
apb
apra
aprb
aprra
aprrb
apma
apmb
chr
pitch
set
```
A future version may define a TOON syntax for these instructions.

## Code Generation

The TOON processor translates TOON to FXCore assembler statements one-for-one. Each TOON statement generates exactly
one assembler statement. The goal is to improve the development experience and ease-of-understanding for FXCore
assembler code, not to actually implement a higher level language. Even within the confines of assembler
and the FXCore instruction set, TOON provides a (subjectively) better experience for software development.

If a TOON statement contains a comment using the line-comment delimiters `;` or `//` then the generated assembler
output line will preserve that comment. This can aid in debugging the resulting assembler code. Likewise, if a
TOON statement uses a `.rn` renamed (symbolic) value, the generated code will also use that symbol and not its
resolved value.

TOON will not attempt to process any text between block comment delimiters `/* ... */` on a single line or
when such comments span multiple lines.

By default TOON will include the oringal TOON statement text in generated assebler statements as an
inline comment. This may be disabled with the `--noannotate` command line argument.

## Standalone execution

By default the TOON translator runs as the last step of the macro processor (e.g. after all macros
have been expanded). The TOON translator can also be run stand alone without the macro processor
using the following command:
```
java -jar fxcoremp.jar -cp com.cabintech.fxcoremp.Toon "input file name" "output file name" --noannotate
```
The `--noannotate` command line argument is optional. By default TOON will write the original TOON 
statement as an inline `/* comment */` in the generated output. Specifing this option will disable generation of those comments.

To run this in a tool chain and detect failures, the TOON translator will set a process exit code as follows:
- `0` No errors, output file has been written
- `1` Input files are not specified or not found
- `2` Syntax error in the input file
- `3` An unexpected failure

## Tool chain

Generally the macro processor (and TOON) are run as the first step in the assembler tool
chain, followed by the FXCore preprocessor (which expands FXCore library references). If
TOON statements are used in FXCore libraries they would not be translated and the assembler
would flag them as syntax errors. 

If it is desired to use TOON syntax in
FXCore assembler libraries, the TOON translator can be run a 2nd time in the chain (stand-alone) after the FXCore preprocessor
and before the assembler as an additional step in the tool chain.

## Background

Since their inception [in 1947](https://en.wikipedia.org/wiki/Assembly_language) assembler languages have been cryptic and rigid in their
syntax. Machine instructions are represented with short mnemonics and a statement format that is uniform with something like:
```
<label> <mnemonic> <op1>,<op2>
```

This was a reflection of the rigid nature of the underlying machine architecture where op codes
and operands were encoded into binary words which became the program stream understood by the CPU.
Early assembler languages were implemented on machines with limited processing and memory capacity,
so highly structured and predictable syntax, and short symbols were a necessity.

Over time features were added to allow some symbolic representation (EQU statements) various 
'macro' statements which allowed symbolic substitution and other features. 
The [S/370 mainframe macro assembly language](https://www.ibm.com/docs/en/SSENW6_1.6.0/pdf/asmp1024_pdf.pdf) is
powerful in its own right with extensive macro features that emulates some higher level language
constructs. But at it's essence, assembly languages are still largely cryptic and rigidly formatted.

This project is an attempt to loosen the traditional rigid rules for assembly language that impede easy
development and interpretation of source code. There is no logical reason modern assembly needs to
be cryptic with a fixed uniform format. There is plenty of processing power on the machines used to build
assembler code to provide a richer syntax and something closer to a high level language experience. This
is not a new concept, the roots of [High Level Assembly (HLA)](https://en.wikipedia.org/wiki/High_Level_Assembly) are from the 1990's.

HLAs in their modern form are exhibited in [HLA v2](https://www.randallhyde.com/AssemblyLanguage/HighLevelAsm/index.html) which 
has goals very similar to this project. However 
the abstractions and implementation are much too complex to implement in the FXCore instruction set. HLA is more oriented to
general purpose computers than specialty processors like DSPs. For this project it seemed much simpler to
expand the macro processor to recognize a more expressive (and yet still simple) syntax to achieve similar goals.
