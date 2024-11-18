#Change List

## Version 1.1 (Nov 18, 2024)

- Added optional direction indicators to macro argument definitions and invocations. These are
visual cues to indicate if a macro argument is an input, output, or in-out value. This is an
extension to the syntax that is optional, backward compatibility is maintained.

- Added an optional alternative assembler statement syntax known as [TOON (Target Of Operations Notation)](toon.md).
This alternative syntax may be freely mixed with traditional FXCore assembly language. The macro
processor will translate the TOON syntax to valid FXCore assembler statements, including inference
of assignment operators and generation of appropriate copy mnemonics.