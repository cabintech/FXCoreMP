# Target Of Operations Notation (TOON)

The FXCoreMP macro processor enables an alternative assembler language syntax that is more
expressive, simplifies common operations, and makes it easier to understand the 
sequence of operations and branching that makes up an FXCore program.

The TOON syntax can be freely mixed with regular FXCore assembler language syntax, so it
can be used exclusively, sparsely, or not at all. The macro processor will translate all
TOON statements into valid FXCore assembler.

TOON processing is a separate operation on the source code than the macro processor.
TOON syntax can be used with or without macros, and macros can be used with or without
TOON statements. By default TOON translation is run as the last step of the macro
processor, but it can also be run as a stand alone tool.

We found that the re-formatting our existing FXCode into TOON statements has
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
  incorrect because it is using the <code>xor</code> instruction when the operand is an immediate (constant)
  value - the <code>xori</code> instruction would be expected. The TOON processor analyizes the
  operand types and infers the correct instruction. TOON will generate the <code>xori</code> instruction
  for the above assignment. See the [syntax reference] for other instructions that can
  be inferred.
</blockquote>

The TOON processor will validate that the target of assignment statements are valid
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

TOON formated code can be (subjectively) easier to read and understand-at-a-glance. Assembler code
uses the same basic `opcode operand,operand` format for all statements no matter what they do. So
there is no visual distinction between assignments, data operations, conditional branches, etc. In higher
level languages those constructs have very different looking syntax. This helps with intuitive 
understanding of the code structure. Not having that syntactic visual aid makes assembler harder
to read and understand.

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

Where (or is) the register named `scaleFactorMax` modified by this code? 
To answer that question some careful reading of the code is required. The symbol `scaleFactorMax` appears in several
places, but do any of those update it's value? Even finding it in all the lists of operands takes some careful reading.

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
find `scaleFactorMax' you know that it is modified by that instruction (2 places are easy to find in the sample above).

The example also show some other features of the TOON syntax. 
* The assignment in line 1 is from an SFR (IN0) to a core
register (ACC32). TOON determins that the assembler instruction `cpy_cs` is required and generates the approprate
assembler statement.
* In line 4, ACC32 is written to delay memory by an ''indirect'' constant address. Delay memory addressing is indicated by the parens.
* In line 6, a 16 bit constant value is assigned to the upper part of register temp1. The ".U" postfix makes it clear what the
assignment is doing.
* The multiply on line 7 uses `MULT` which is not an FXCore instruction but is understood by
TOON to implement a multiply operation on the 2 operands. TOON infers the proper FXCore instruction and generates
the proper assembly instruction (`multrr` in this case because both operands are core registers). There are several
such generic TOON operators to reduce the distracting detail in the code.
* The conditional branch on line 14 is written as a familier "if" statement.

Many of these features are described in the following sections.

## Assignment statements

The backbone of all computer languages is data movement, or 'copy/load/store' instructions. The
well understood syntax of using the '=' symbol to denote assignment is common in many
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
generate a `wrdelx` instruction for this assigment.

The FXCore also supports indirect reading (not writing) of memory registers. This is
represented in TOON in a similar way, but using square brackets:
```
r5 = [r1]
```
This load the memory register addressed by the content of R1 into register R5.
### 64/32 Bit Assignments
FXCore also provides instructions for transferring 32 bit words between core
registers and the upper and lower half of the 64 bit accumulator (ACC64). Rather than
lookup those nmemonics, TOON assignment statements can generate the proper assembler code
from easy to write code such as:
```
acc64.u = r5
```
The semantics are clear in the statement, the content of R5 is written to the upper
half of ACC64. Likewise simple assignements transfer data the other way:
```
acc32 = acc64.l
```

## Branching

Like assignments, basic assembler syntax for branching uses the same syntax as all other statements. TOON allows conditional
branching to be express in a more familiar IF statement syntax with clearly readable conditions. In the example above,
an assembler statement:
```
jgez        acc32,save_range		 
```

is rewritten as:
```
if acc32 >=0 goto save_range
```

This syntax is different than assignments and other operations, so the branch points in the code are obvious. The test
condition and how it operates is clear from the statement format. The `goto` token in the IF statement is
optional and can be omitted. The condition can be written in expression notation as shown above, or by using
the assembler mnemonic such as `jgez' or the just the condition part of the mnemonic 'gez'. The following are
all equivalent and produce the same assembler instructions:
```
if acc32 >=0 goto save_range
if acc32 gez save_range
if acc32 jgez goto save_range
```

Using this familiar coding syntax makes it easy to spot key code branching points without lots of comments,
blank lines, or indentation to visually highlight the code flow.

## 1:1 statement translation

The macro processor translates TOON to FXCore assembler statements one-for-one. Each TOON statement generates exactly
one assembler statement. The goal is to improve the development experience and ease-of-understanding for FXCore
assembler code, not to actually implement a higher level language. Even within the confines of assembler
and the FXCore instruction set, TOON provides a (subjectively) better experience for software development.

## Standalone execution

By default the TOON translator runs as the last step of the macro processor (e.g. after all macros
have been expanded). The TOON translator can also be run stand alone without the macro processor
using the following command:
```
java -jar fxcoremp.jar -cp com.cabintech.fxcoremp.Toon "input file name" "output file name"
```

To run this in a tool chain and detect failures, the TOON translator will set a process exit code
- 0 No errors, output file has been written
- 1 Input files are not specified or not found
- 2 Syntax error in the input file
- 3 An unexpected failure

## Tool chain

The TOON translator must be run after all macro expansion has be done (otherwise it cannot reliably
infer assignment statement source and targets types). If it is desired to use TOON syntax in
FXCore assembler libraries, the TOON translator can be used after the FXCore preprocessor
and before the assembler as an
additional step in the tool chain.

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
expand the macro processor to recognize a more expressive (and yet still simple) syntax.
