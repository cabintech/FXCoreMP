# Target Of Operations Notation (TOON)

The FXCoreMP macro processor enables an alternative assembler language syntax that is more
expressive, simplifies common copy operations, and makes it easier to understand the 
sequence of operations that makes up an FXCore program.

The TOON syntax can be freely mixed with regular FXCore assembler language syntax, so it
can be used exclusively, sparsely, or not at all. The macro processor will translate all
TOON statements into valid FXCore assembler in it's output stream.

## Target of an instruction

A key goal of TOON is to make the assembly language more explicit about the target of
machine operations. Many FXCore instructions have implied side effects that are not
evident in the assembler statement, in particular the target of the operation. For example:
```
xor r8,0x0F
```

It might be assumed that after this operation the value of R8 would have been XOR'ed
with 0x0F. But that is not the case, R8 remains unchanged and the result of the XOR is
written to the accumulator (acc32). This is not uncommon in machine architectures but the
assembly language does not indicate the target of the operation. To know that, the user
is either very familiar with the machine instructions, or they are constantly referencing
the documentation to know what is the target of an operation, and sometimes the ordering
of the operands.

TOON seeks to make the target of operations explicit so reading source code is more
self-explanatory and unambiguous. The TOON syntax for the above statement would be:
```
acc32 = r8 xor 0x0F
```

This makes it clear that the result of this operation is placed in (assigned to) the
ACC32 register, and by placing the opcode between the operands the statement is in a
more natural language form "R8 is XOR'ed with 0x0F and assigned to ACC32". This reads
more like a high level computer language and the operation and results can be fully
understood without consulting the details of the 'xor' machine instruction.

It may however have the down-side of reducing the mystic of programming in assembler :-).

## Assignment statements

The backbone of all computer languages is data movement, or 'copy' instructions. The
well understood syntax of using the '=' symbol to denote assignment is common in many
languages. However in FXCore assembler (and most assembler languages), copy instructions
adhere to the rigid "opcode operand1,operand2" format:
```
cpy_cm r8,mr41
```

TOON makes special provisions for copy operations, expressing them in more
familiar assignment syntax. The above would be written in TOON syntax as simply:
```
r8 = mr41
```

The mnemonic is removed from the syntax all together to make an instantly more
readable statement. The developer does not have to consider all the cpy_XX variations and think carefully
about the order of the operands (which has great significance for the copy instructions).
The macro processor interprets the assignment statement and infers the correct instruction
from the type of the source and destination operands.

All the cpy_XX mnemonics can be replaced with TOON assignment statements even if the
operands are symbolic:
```
.rn    delayTime    r12
$macro sampleWindow mr41

delayTime = sampleRate
```

The above assignment will correctly infer the source and target and generate the proper
assembler statement using the cpy_cm instruction.



## Background

Since the inception of assembly languages in 1947, they have been cryptic and rigid in their
syntax. Machine instructions are represented with short mnemonics that often look very similar
but with quite different uses, and statement format is uniform with all statements being something like:
```
<label> <mnemonic> <op1>,<op2>
```

This was a reflection of the rigid nature of the underlying machine language format where op codes
and operands were encoded into binary words which became the program stream understood by the CPU.
Over time features were added to allow some symbolic representation (EQU statements) and to simplify
repeditive tasks, various macro preprocessors were developed. The S/370 mainframe macro assembly language was
powerful in its own right with extensive macro features that emulates some higher level language
constructs. But at it's essence, assembly languages are still largely cryptic and rigidly tied to
the target machine design.