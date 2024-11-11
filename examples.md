# FXCoreMP Sample Macros

```
; Copy a Special Function Register (SFR) to a Memory Register (MR)
; Uses ACC32
$macro COPY_SFR_TO_MR(mr, sfr) ++
cpy_cs	acc32, ${sfr} 
cpy_mc	${mr}, acc32 
$endmacro
```

```
; Copy a Memory Register (MR) to a Special Function Register (SFR)
; Uses ACC32
$macro COPY_MR_TO_SFR(sfr, mr) ++
cpy_cm	acc32, ${mr} 
cpy_sc	${sfr}, acc32 
$endmacro
```

```
; Copy a MR to another MR (uses ACC32)
$macro COPY_MR_TO_MR(mrTo, mrFrom) ++
cpy_cm	acc32, ${mrFrom}
cpy_mc	${mrTo}, acc32 
$endmacro
```

```
; Copy MR to MR with a temp register (does not use acc32)
$macro COPY_MR_TO_MR_TEMP(mrTarget, mrSource, crTemp) ++
cpy_cm		${crTemp}, ${mrSource} 	
cpy_mc		${mrTarget}, ${crTemp}
$endmacro
```

```
; Set an MR to a 32 bit value from (2) 16 bit constants
; Uses ACC32
$macro COPY_CONST_TO_MR(mrTo, constHi, constLo) ++
wrdld	acc32, ${constHi} 
ori		acc32, ${constLo}
cpy_mc	${mrTo}, acc32
$endmacro
```

```
; Branch to a target label if the given (debounced) SWITCH is LOW
; Uses ACC32
$macro IF_SWITCH_LOW(switch, label) ++
cpy_cs		acc32, SWITCH 
andi		acc32, ${switch}
jz			acc32, ${label}
$endmacro
```

```
; Branch to a target label if the given (debounced) SWITCH is HIGH.
; The first arg should be one of the SWxDB assembler constants.
; Uses ACC32
$macro IF_SWITCH_HIGH(switch, label) ++
cpy_cs		acc32, SWITCH 
andi		acc32, ${switch}
jnz			acc32, ${label}
$endmacro
```

```
; Short hand for IF_SWITCH_HIGH()
$macro IF_SWITCH(a,b) ++
$IF_SWITCH_HIGH(${a},${b})
$endmacro
```

```
; Invert a positive value in "cr" and leave result in acc32.
; The invert is defined as 1 minus the original value for any
; value between 0 and max pos (0x7FFFFFFF).
; Uses ACC32
$macro INVERT(cr) ++
wrdld	acc32, 0x7FFF 	; Load max pos value 
ori		acc32, 0xFFFF	
subs	acc32, ${cr}	; Leaves result in acc32
$endmacro
```

```
; ACC32 = Multiply the lower 16 bits of two core registers
$macro MULT_16(cr1, cr2, crTemp) ++
sl			${cr1}, 16			; Move arg1 to upper 32 bits 
cpy_cc		${crTemp}, acc32	; Save in temp 
sl			${cr2}, 15			; Move arg 2, not sure why 15 instead of 16 bits 
multrr		acc32, ${crTemp}	; acc32 = upper 32 bits of 64 bit result
$endmacro
```

```
; Convert msec to number (integral) number of delay samples, assuming 48kHz sample rate.
$macro MS_TO_SAMPLES_48K(msec) ((${msec}/1000)/(1/48000))
```

```
; Encode 4 bytes into a 32-bit word
$macro WORD32_BYTES(msb, b2, b1, lsb) (${msb}<<24)|(${b2}<<16)|(${b1}<<8)|${lsb}
```

```
; Multi-target branch based on value of a CR. A value of 0 will
; branch to label target0, a value of 1 will branch to target1,
; etc. Any value is >=3 will branch to the last (default) label.
; Uses ACC32.
$macro SWITCH4(crValue, target0, target1, target2, default) ++
jz			${crValue}, ${target0}

cpy_cc		acc32, ${crValue}
addi		acc32, -1			
jz			acc32, ${target1}

cpy_cc		acc32, ${crValue}		
addi		acc32, -2			
jz			acc32, ${target2}

jmp			${default}
$endmacro
```

```
; Multi-target branch based on value of a CR. A value of 0 will
; branch to label target0, a value of 1 will branch to target1,
; etc. If the value is >=2 will branch to the last (default) label.
; Uses ACC32.
$macro SWITCH3(crValue, target0, target1, default) ++
jz			${crValue}, ${target0}
cpy_cc		acc32, ${crValue}		
addi		acc32, -1			
jz			acc32, ${target1}
jmp			${default}
$endmacro
```