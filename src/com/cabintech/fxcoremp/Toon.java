package com.cabintech.fxcoremp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class translates TOON source statements into FXCore assembly statements. The main() of this
 * class can be used to read a source file and output a file with translated source (e.g. it does
 * the TOON translations separate from the macro processing functions).
 * 
 * A single instance of this class can be used to translate one entire source file. For proper
 * translation all source statements must be processed through the reTOON() method. This allows
 * the translator to build a list of assembler renames (.rn statements) which are needed for
 * proper translation. The source cannot include un-expanded macros, e.g. macro expansion should
 * be done before TOON translation.
 * 
 * usage:
 * 
 * Toon toon = new Toon();
 * List<String> sourceList = new ArrayList<>();
 * 
 * //... load source code lines into sourceList ...
 * 
 * while (String sourceLine: sourceList) {
 * 		String s = toon.reTOON(sourceLine);
 * 		//... write translated line 's' somewhere...
 * } 
 * 
 * @author Mark McMillan
 */

public class Toon {
	
	// 2 operand instructions that target ACC32 'acc32 = x instr y'
	private Set<String> Ops2ArgAcc32Set = Set.of(
			"addi",
			"add",		// Synthetic
			"adds",		// Synthetic
			"addsi",
			"sub",
			"subs",
			"sl",		// Synthetic
			"slr",
			"sls",
			"slsr",
			"sr",		// Synthetic
			"srr",
			"sra",
			"srar",
			"or",		// Synthetic
			"ori",
			"and",		// Synthetic
			"andi",
			"xor",		// Synthetic
			"xori",
			"multrr",
			"multri",
			"mult"		// Synthetic
			);
	
	// 1 operand instructions that target ACC32 'acc32 = instr x'
	private Set<String> Ops1ArgAcc32Set = Set.of(
			"inv",
			"abs",
			"neg",
			"log2",
			"exp2",
			"interp"
			);

	// 2 operand instructions that target ACC64 'acc64 = x instr y'
	private Set<String> Ops2ArgAcc64Set = Set.of(
			"macrr",
			"macri",
			"macrd",
			"macid",
			"machrr",
			"machri",
			"machrd",
			"machid",
			"macr",		// Inferred
			"macd",		// Inferred
			"machr",	// Inferred
			"machd"	// Inferred
			);
	
	// Map of allowed conditional expressions for IF statements into target FXCore branch mnemonics.
	private Map<String,String> CondJmpExpr = Map.ofEntries(
			Map.entry("gez", "jgez"), 
			Map.entry(">=0", "jgez"),
			Map.entry("jgez", "jgez"),
			Map.entry("<0", "jneg"),
			Map.entry("neg", "jneg"),
			Map.entry("jneg", "jneg"),
			Map.entry("!=0", "jnz"),
			Map.entry("<>0", "jnz"),
			Map.entry("nz", "jnz"),
			Map.entry("jnz", "jnz"),
			Map.entry("=0", "jz"),
			Map.entry("z", "jz"),
			Map.entry("jz", "jz"),
			Map.entry("!=acc32.sign", "jzc"),
			Map.entry("zc", "jzc"),
			Map.entry("jzc", "jzc")
			);
	
	// All FXCore Special Function Registers
	static public Set<String> SrfNameSet = Set.of(
			"in0",
			"in1",
			"in2",
			"in3",
			"out0",
			"out1",
			"out2",
			"out3",
			"pin",
			"switch",
			"pot0_k",
			"pot1_k",
			"pot2_k",
			"pot3_k",
			"pot4_k",
			"pot5_k",
			"pot0",
			"pot1",
			"pot2",
			"pot3",
			"pot4",
			"pot5",
			"pot0_smth",
			"pot1_smth",
			"pot2_smth",
			"pot3_smth",
			"pot4_smth",
			"pot5_smth",
			"lfo0_f",
			"lfo1_f",
			"lfo2_f",
			"lfo3_f",
			"ramp0_f",
			"ramp1_f",
			"lfo0_s",
			"lfo0_c",
			"lfo1_s",
			"lfo1_c",
			"lfo2_s",
			"lfo2_c",
			"lfo3_s",
			"lfo3_c",
			"ramp0_r",
			"ramp1_r",
			"maxtempo",
			"taptempo",
			"samplecnt",
			"noise",
			"bootstat",
			"tapstkrld",
			"tapdbrld",
			"swdbrld",
			"prgdbrld",
			"oflrld"	
			);
	
	// Track assembler .rn statements so we can determine the type of assignment operands 'x = y'.
	private  Map<String,String> rnMap = new HashMap<>();
	
	private boolean annotate = true; // Write original TOON statements as comments in each line of output. 
	
	/**
	 * Return a concatenation of all the elements of 'tokens' starting
	 * at fromIndex, followed by any comment string from the stmt. This is
	 * used to insure that the re-generated statement includes everything
	 * following the TOON blank-delimited tokens. E.g. TOON stmt:
	 * 
	 * acc32 = r0 addi 31 + 4 ; my comment
	 * 
	 * This will parse as 7 tokens, but the base code will output:
	 * 
	 * addi r0,31
	 * 
	 * so this is used to append the remainder to make:
	 * 
	 * addi r0,31 + 4 ; my comment
	 * 
	 * @param tokens
	 * @param fromIndex
	 * @param stmt
	 * @return
	 */
	private String joinRemainder(String[] tokens, int fromIndex, Stmt stmt) {
		StringBuffer s = new StringBuffer();
		for (int i=fromIndex; i < tokens.length; i++) {
			s.append(" " + tokens[i]);
		}
		s.append(SEP);
		
		// Insert original stmt text as a comment for debugging */
		if (annotate) {
			s.append(" /* ");
			s.append(stmt.getText());
			s.append(" */");
		}
		
		// Add original comment, if any
		if (stmt.getComment().length() > 0) {
			s.append(SEP+stmt.getComment());
		}
		return s.toString();
	}
	
	private static String SEP = "\t\t"; // Separator between output tokens
	
	private static String[] insertElement(String[] a, int index, String newS) {
		if (index >= a.length) throw new IllegalArgumentException("Index value "+index+" is larger the array max index "+a.length);
		// Crude but effective if list is not large
		List<String> list = new ArrayList<>();
		// Add up to insert point
		for (int i=0; i<index; i++) {
			list.add(a[i]);
		}
		// Add the new element
		list.add(newS);
		// And the rest of the array
		for (int i=index; i<a.length; i++) {
			list.add(a[i]);
		}
		
		return list.toArray(new String[0]);
	}

	/**
	 * Returns the given source statement 's' as-is, or if it is recognized as a TOON
	 * statement, the translated FXCore assembler statement is returned.
	 * @param s
	 * @return
	 * @throws Exception
	 */
	public String reTOON(String s) throws Exception {
		
		Stmt stmt = new Stmt(s, 0, ""); // Use this to remove comments (TODO: redundant with earlier macro processing...)
		
		//TODO: Make a custom splitter to better handle operators adjacent to operands
		// acc66+=r0 macrr r1
		// if r0>=0 goto label
		
		String[] tokenList = Util.split(stmt.getText(), "\\p{Space}+"); // Tokenize on white space including tabs
		int tokenCnt = tokenList.length;
		
		// TOON statements can be written without whitespace around the assignment operator 'a= b'.
		// Any '=' in the first 2 tokens needs to be made a separate token (except in IF statements)
		for (int tokenNum = 0; tokenNum <= Math.min(1, tokenCnt-1); tokenNum++) {
			
			if (tokenNum == 0 && tokenList[0].toUpperCase().equals("IF")) {
				break; // IF statements can have '=' symbols, they are parsed separately below
			}
			
			if (tokenList[tokenNum].equals("=") || tokenList[tokenNum].equals("+=")) continue;
			
			String token = tokenList[tokenNum];
			
			if (token.endsWith("=")) {
				// 'a= b'
				tokenList[tokenNum] = Util.jsSubstring(token, 0, token.length()-1); // Remove trailing '='
				tokenList = insertElement(tokenList, 1, "=");
				tokenCnt = tokenList.length;
			}
			else if (token.startsWith("=")) {
				// 'a =b'
				tokenList[tokenNum] = Util.jsSubstring(token, 1); // Remove leading '='
				tokenList = insertElement(tokenList, 1, "=");
				tokenCnt = tokenList.length;
			}
			else if (token.contains("=")) {
				// 'a=b'
				String[] parts = Util.split(token, "=");
				if (parts.length != 2) throw new SyntaxException("Invalid assignment syntax '"+s+"'");
				List<String> list = new ArrayList<>();
				list.add(parts[0]);
				list.add("=");
				list.add(parts[1]);
				for (int i=1; i<tokenList.length; i++) {
					list.add(tokenList[i]);
				}
				tokenList = list.toArray(new String[0]);
				tokenCnt = tokenList.length;
			}
		}
		
		
		// Keep track of assembler .rn statements that give symbolic names to registers
		if (tokenCnt >= 3 && tokenList[0].equalsIgnoreCase(".rn")) {
			// Syntax: .rn name rX
			rnMap.put(tokenList[1].toUpperCase(), tokenList[2].toUpperCase());
			return s; // Nothing else to do with this statement, leave it unmodified
		}
		
		if (tokenCnt < 2) return s; // All TOON statements have > 2 tokens
		
		// IF conditional branch //TODO: This precludes a symbol named 'if', e.g. 'if = r0'.
		if (tokenList[0].equalsIgnoreCase("if")) {
			if (tokenCnt < 4) throw new SyntaxException("Invalid IF statement syntax, expected 4 tokens but found only "+tokenCnt+" in '"+s+"'");
			// 'IF <core-reg> <cond> <label>'
			//TODO: For now the conditional expression must be space-separated, cannot write 'if r6>=0'
			
			String cond = CondJmpExpr.get(tokenList[2].toLowerCase());
			if (cond == null) throw new SyntaxException("Invalid IF statement syntax, condition '"+tokenList[2]+"' not recognized in '"+s+"'");
			
			Operand target = new Operand(tokenList[1], rnMap);
			if (!target.isCR()) throw new SyntaxException("Invalid IF statement syntax, operand '"+tokenList[1]+"' must be a CR in '"+s+"'");
			
			// Skip optional 'goto' token
			int labelIndex = 3;  // If no GOTO token
			if (tokenList[3].equalsIgnoreCase("goto")) {
				labelIndex = 4; // Label is next token after GOTO
			}
			
			// Generate FXCore assembler format (use original symbol, if any, for the register) for readability of the generated code
			return cond+SEP+tokenList[1]+","+tokenList[labelIndex]+joinRemainder(tokenList, labelIndex+1, stmt);
		}
		
		// Unconditional branch
		if (tokenList[0].toUpperCase().equals("JMP") || tokenList[0].toUpperCase().equals("GOTO")) {
			return "jmp"+SEP+tokenList[1]+joinRemainder(tokenList, 2, stmt);
		}
		
		// Assignment style TOON statements 'x = ..."
		if (tokenList[1].equals("=") || tokenList[1].equals("+=")) {
			if (tokenCnt < 3) throw new SyntaxException("Invalid TOON instruction format, missing right side of assignment: '"+s+"'");
			
			Operand left = new Operand(tokenList[0], rnMap);
			Operand right = new Operand(tokenList[2], rnMap);
			
			// "+=" only supported for certain ACC64 assignments, it is for decoration only and not required
			if (tokenList[1].equals("+=")) {
				if (!left.isAcc64()) throw new SyntaxException("Summing operator '+=' is only valid for ACC64 assignments.");
				if (!left.isPlain()) throw new SyntaxException("Indirect and modifications on ACC64 is not valid for summing operator '+=' assignments.");
				if (!Ops2ArgAcc64Set.contains(tokenList[3].toLowerCase())) throw new SyntaxException("Summing operator is not valid for '"+tokenList[3]+"' instruction.");
				tokenList[1] = "="; // Treat like a regular assignment
			}
			
			// Acc64 assignments from 32 bit registers "acc64.u = r5"
			if (left.isAcc64() && tokenCnt==3) {
				if (!right.isCR()) throw new SyntaxException("Right side of ACC64 assignment must be a CR in '"+s+"'");
				char UL = left.isModLower() ? 'l' : (left.isModUpper() ? 'u' : '?');
				if (UL=='?') throw new SyntaxException("ACC64 must have .U or .L (Upper/Lower) postfix in '"+s+"'");
				return "ldacc64"+UL+SEP+right.getOpText() + joinRemainder(tokenList, 3, stmt);
			}
			// 32-bit register assignment from Acc64 "r5 = acc64.l"
			if (right.isAcc64() && tokenCnt==3) {
				if (!left.isCR()) throw new SyntaxException("Left side of ACC64 assignment must be a CR in '"+s+"'");
				String opCode = right.isModLower() ? "rdacc64l" : (right.isModUpper() ? "rdacc64u" : (right.isModSat() ? "sat64" : null));
				if (opCode == null) throw new SyntaxException("ACC64 must have .U or .L (Upper/Lower) or .SAT (Saturated) postfix in '"+s+"'");
				return opCode + SEP + left.getOpText() + joinRemainder(tokenList, 3, stmt);
			}
			
			
			// ACC32 operations (implicit assignment)
			
			// 1 arg functions "acc32 = <func> <cr>"
			if ((tokenCnt >= 3) && Ops1ArgAcc32Set.contains(tokenList[2].toLowerCase())) {
				if (tokenCnt < 4) throw new SyntaxException("Invalid assignment, missing expected operand after '"+tokenList[2]+"' in '"+s+"'");
				right = new Operand(tokenList[3], rnMap);
				
				if (!left.isAcc32()) throw new SyntaxException("Target of assignment for '"+tokenList[2]+"' function must be ACC32 in '"+s+"'");
				// Special case, "ACC32 = INTERP (cr+const)" - right side is indirect sum of CR and a constant
				if (tokenList[2].toLowerCase().equals("interp")) {
					if (!right.isDMIndirect()) throw new SyntaxException("INTERP operand must be indirect (CR+constant). Expected parens not found.");
					String is = right.getOpText();
					String parts[] = Util.split(is, "\\+", 2);
					if (parts.length != 2)  throw new SyntaxException("INTERP operand must be of the form (CR+constant).");
					Operand p1 = new Operand(parts[0], rnMap);
					Operand p2 = new Operand(parts[1], rnMap);
					if (!p1.isCR()) throw new SyntaxException("INTERP operand must be of the form (CR+constant). CR not found.");
					if (p2.isReg()) throw new SyntaxException("INTERP operand must be of the form (CR+constant). Constant not found.");
					// Looks like a valid INTERP statement
					return "interp" + SEP + p1.getOpText() + "," + p2.getOpText() + joinRemainder(tokenList, 4, stmt);
				}
				if (!left.isPlain() || !right.isPlain()) throw new SyntaxException("This assignment operation does not support indirection or modifiers in '"+s+"'");
				if (!right.isCR()) throw new SyntaxException("Source for assignment operation must be a CR '"+s+"'");
				return tokenList[2] + SEP + right.getOpText() + joinRemainder(tokenList, 4, stmt);
			}
			
			// 2 arg functions "acc32 = <cr> <func> <op2>"
			if ((tokenCnt >= 4) && Ops2ArgAcc32Set.contains(tokenList[3].toLowerCase()) ) {
				String func = tokenList[3].toLowerCase();
				if (tokenCnt < 5) throw new SyntaxException("Invalid assignment, missing expected operand after '"+func+"' in '"+s+"'");
				Operand op1 = new Operand(tokenList[2], rnMap);
				Operand op2 = new Operand(tokenList[4], rnMap);
				
				if (!left.isAcc32()) throw new SyntaxException("Target of assignment for '"+func+"' function must be ACC32 in '"+s+"'");
				if (!left.isPlain()) throw new SyntaxException("This assignment operation does not support indirection or modifiers on ACC32 in '"+s+"'");
				// Left side of func must be a CR for all 32-bit two operand instructions
				if (!op1.isCR()) throw new SyntaxException("Left operand for function '"+func+"' must be a CR in '"+s+"'");
				
				// Some instructions have immediate and register forms for <op2> but we allow use of
				// generic instruction names, e.g. "r0 = r1 and 0x1" is really the "andi" instruction, not "and".
				// So we try to infer the correct instruction from the type of <op>.
				func = inferFunc(func, op1, op2);
				
				// Some special cases
				if (func.equals("interp")) {
					if (!op1.isDMIndirect()) throw new SyntaxException("Left operand for function '"+func+"' must be indirect notataion, enclose in parens. '"+s+"'");
				} else {
					if (!op1.isPlain()) throw new SyntaxException("Left operand for function '"+func+"' does not support indirectin or modifiers in '"+s+"'");
				}
				
				return func + SEP + op1.getOpText() + "," + op2.getOpText() + joinRemainder(tokenList, 5, stmt);
			}
			
			// 2 arg functions "acc64 = <cr> <func> <op2>"
			if ((tokenCnt >= 4) && Ops2ArgAcc64Set.contains(tokenList[3].toLowerCase()) ) {
				String func = tokenList[3].toLowerCase();
				if (tokenCnt < 5) throw new SyntaxException("Invalid assignment, missing expected operand after '"+func+"' in '"+s+"'");
				Operand op1 = new Operand(tokenList[2], rnMap);
				Operand op2 = new Operand(tokenList[4], rnMap);
				
				if (!left.isAcc64()) throw new SyntaxException("Target of assignment for '"+func+"' function must be ACC64 in '"+s+"'");
				if (!left.isPlain()) throw new SyntaxException("This assignment operation does not support indirection or modifiers on ACC64 in '"+s+"'");
				
				// Some instructions have immediate and register forms for <op2> but we allow use of
				// generic instruction names, e.g. "r0 = r1 and 0x1" is really the "andi" instruction, not "and".
				// So we try to infer the correct instruction from the type of <op>.
				func = inferFunc(func, op1, op2);
				
				// Left side of func must be a CR for most 64-bit two operand instructions
				if (func.equals("macid") || func.equals("machid")) {
					if (op1.isReg()) throw new SyntaxException("Left operand for function '"+func+"' cannot be a register, immediate constant value is required.");
				}
				else {
					if (!op1.isCR()) throw new SyntaxException("Left operand for function '"+func+"' must be a CR.");
				}
				// Some are indirect memory addressing
				if (func.equals("macrd") || func.equals("macid") || func.equals("machrd") || func.equals("machid")) {
					if (!op2.isDMIndirect()) throw new SyntaxException("Right operand for function '"+func+"' is delay memory indirect constant, must be enclosed in parens.");
				}
				
				return func + SEP + op1.getOpText() + "," + op2.getOpText() + joinRemainder(tokenList, 5, stmt);
			}
			
			// R to R assignments
			if (left.isReg() && right.isReg()) {
				
				String opCode = null;
				
				// Both sides are unmodified and non-indirect, e.g. "cpy_xx" instructions
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && !right.isIndirect()) {
					char codeLeft = left.isCR() ? 'c' : (left.isMR() ? 'm' : 's'); // Must be CR, MR, or SFR
					char codeRight = right.isCR() ? 'c' : (right.isMR() ? 'm' : 's'); // Must be CR, MR, or SFR
					opCode = "cpy_"+codeLeft+codeRight;
				}
				
				// Right side is MR indirect, e.g. cpy_cmx r0=[r5]
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && right.isMRIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of indirect MR assignment must be a CR in '"+s+"'");
					opCode = "cpy_cmx";
				}
				
				// Right side is indirect to delay memory r0=(r5)
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && right.isDMIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of indirect delay memory assignment must be a CR in '"+s+"'");
					opCode = "rddelx";
				}
				
				// Right side is absolute indirect to delay memory r0=@(r5)
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && right.isAbsDMIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of absolute indirect delay memory assignment must be a CR in '"+s+"'");
					opCode = "rddirx";
				}
				
				// Left side is indirect to delay memory (r0)=r5
				if (!left.isModified() && left.isDMIndirect() && !right.isModified() && !right.isIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of indirect delay memory assignment must be a CR in '"+s+"'");
					opCode = "wrdelx";
				}
				
				// Left side is absolute indirect to delay memory @(r0)=r5
				if (!left.isModified() && left.isAbsDMIndirect() && !right.isModified() && !right.isIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of absolute indirect delay memory assignment must be a CR in '"+s+"'");
					opCode = "wrdirx";
				}
				
				if (opCode == null) throw new SyntaxException("Invalid register-to-register assigment statement in '"+s+"'");
				
				return opCode + SEP + left.getOpText() + "," + right.getOpText() + joinRemainder(tokenList, 3, stmt);
			}
			
			// Assignment to CR from non-register source
			if (left.isCR()) {
				String opCode = null;
				
				// 16-bit load of constant to upper part of CR "r5.u = 3829"
				if (left.isModUpper() && !left.isIndirect() && !right.isModified() && !right.isIndirect()) {
					opCode = "wrdld";
				}
				// Load from fixed delay memory address "r5 = (3920)"
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && right.isDMIndirect()) {
					opCode = "rddel";
				}
				if (opCode == null) throw new SyntaxException("Invalid NonRegister-to-CR assigment statement in '"+s+"'");
				return opCode + SEP + left.getOpText() + "," + right.getOpText() + joinRemainder(tokenList, 3, stmt);
			}
			
			// Assignment from CR to non-register source
			if (right.isCR()) {
				// Write fixed delay memory address from CR "(3920) = r5"
				if (!left.isModified() && left.isDMIndirect() && !right.isModified() && !right.isIndirect()) {
					return "wrdel" + SEP + left.getOpText() + "," + right.getOpText() + joinRemainder(tokenList, 3, stmt);
				}
				throw new SyntaxException("Invalid CR-to-NonRegister assigment statement in '"+s+"'");
			}
			
			// If none of the above, we don't recognize this assignment statement
			throw new SyntaxException("Invalid assignment statement, function or operand types are not recognized in '"+s+"'");
			
			
		} // End of assignment statements
			
		
		// If no match above, it is not TOON format so pass it through
		return s;
			
//			if (Ops0InstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
//				// A simple "x = mnemonic" style TOON statements, just reformat to "mnemonic x".
//				if (tokenCnt > 3) throw new SyntaxException("Invalid syntax for TOON '' mnemonic, too many tokens, should be 'x = <instr>'");
//				return tokenList[2] + SEP + tokenList[0] + joinRemainder(tokenList, 3, stmt);
//			}
//			
//			if (CopyInstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
//				// Copy instructions 'target = instr source'. Source can be a const expression with multiple tokens.
//				return tokenList[2] + SEP + tokenList[0] + "," + tokenList[3] + joinRemainder(tokenList, 4, stmt);
//			}
//			
//			if (tokenCnt >= 4) { // All these require at least 4 tokens
//				if (Ops2Acc32InstrSet.contains(tokenList[3])) { // Mnemonic is 4th token
//					// 2 operand instructions that target acc32 'acc32 = x instr y'. Y can be a const express of multiple tokens
//					if (tokenCnt < 5) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
//					if (!tokenList[0].toLowerCase().equals("acc32")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC32: '"+s+"'");
//					return tokenList[3]+SEP+tokenList[2]+","+tokenList[4]+joinRemainder(tokenList, 5, stmt);
//				}
//				
//				if (Ops2Acc64InstrSet.contains(tokenList[3])) { // Mnemonic is 4th token (same as Ops2Acc32 except target is ACC64)
//					// 2 operand instructions that target acc64 'acc64 = x instr y'
//					if (tokenCnt < 5) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
//					if (!tokenList[0].toLowerCase().equals("acc64")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC64: '"+s+"'");
//					return tokenList[3]+SEP+tokenList[2]+","+tokenList[4]+joinRemainder(tokenList, 5, stmt);
//				}
//				
//				if (Ops1Acc32InstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
//					// 1 operand instructions that target acc32 'acc32 = instr y' --> 'instr y'. Assume Y could be multiple tokens.
//					if (tokenCnt < 4) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
//					if (!tokenList[0].toLowerCase().equals("acc32")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC32: '"+s+"'");
//					return tokenList[2]+SEP+tokenList[3]+joinRemainder(tokenList, 4, stmt);
//				}
//				
//				if (Ops1Acc64InstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
//					// 1 operand instructions that target acc64 'acc64 = instr y' --> 'instr y'. Assume Y could be multiple tokens.
//					if (tokenCnt < 4) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
//					if (!tokenList[0].toLowerCase().equals("acc64")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC64: '"+s+"'");
//					return tokenList[2]+SEP+tokenList[3]+joinRemainder(tokenList, 4, stmt);
//				}
//			}
//			
//			// If none of the above has matched, try to interpret "x = y" assignment statements by type inference
//			// Note that 'x = cpy_** y' and 'x = instr' has already been handled if those copy operations have been coded that way.
//			if (tokenCnt == 3) {
//				String left = tokenList[0].toUpperCase();
//				String right = tokenList[2].toUpperCase();
//				
//				// Detect special notation for cpy_cmx instruction, the right operand is inclosed in () to
//				// indicate the indirection.
//				boolean indirect = false;
//				if (right.startsWith("(") && right.endsWith(")")) {
//					indirect = true;
//					right = Util.jsSubstring(right, 1, right.length()-1); // Remove parens
//					tokenList[2] = right; // Use stripped name when generating code
//				}
//				
//				// Targets and sources can by symbolic names for registers, defined by prior ".rn" statements.
//				// If the left or right operands are symbolics we use the resolved actual register name.
//				//TODO: Does the .rn have to occur before its first use??
//				
//				if (rnMap.containsKey(left)) left = rnMap.get(left);
//				if (rnMap.containsKey(right)) right = rnMap.get(right);
//				
//				char targetType = '?'; // Assume core register
//				char sourceType = '?';
//				
//				if (right.matches("MR[0-9]+")) {
//					sourceType = 'm';
//				}
//				else if (CrSet.contains(right)) {
//					sourceType = 'c';
//				}
//				else if (SrfNameSet.contains(right)) {
//					sourceType = 's';
//				}
//				
//				if (left.matches("MR[0-9]+")) {
//					targetType = 'm';
//				}
//				else if (left.equals("ACC32") || left.matches("R[0-9]+") || left.equals("FLAGS")) {
//					targetType = 'c';
//				}
//				else if (SrfNameSet.contains(left)) {
//					targetType = 's';
//				}
//				
//				if (targetType == '?') throw new SyntaxException("Unable to infer type of target (left side) token in '"+s+"'");
//				if (sourceType == '?') throw new SyntaxException("Unable to infer type of source (right side) token in '"+s+"'");
//				
//				// Indirection only allowed on MR=CR assignments
//				if (indirect && ((targetType!='c') || sourceType!='c')) {
//					throw new SyntaxException("Invalid copy operation, indirection can only be used when the target and source are CRs '"+s+"'");
//				}
//				
//				// Only some combinations can be implemented in a single instruction
//				if(
//					(targetType=='c' && (sourceType=='c' || sourceType=='m' || sourceType=='s')) ||
//					(targetType=='m' && sourceType=='c') ||
//					(targetType=='s' && sourceType=='c')
//				) {
//					// Now we can generate an appropriate copy statement
//					return "cpy_" + targetType + (indirect==true?"mx":sourceType) + SEP + tokenList[0]+"," + tokenList[2] + joinRemainder(tokenList, 3, stmt);
//				}
//				throw new SyntaxException("Unsupported copy operation source type '"+sourceType+"' and target type '"+targetType+"' in statement '"+s+"'");
//			}
//		}
	}
	
	/**
	 * Given an instruction f and the right-side operand of "accXX = <cr> <f> <op2>" determine the
	 * correct instruction by inference from f and type of op2. e.g. if f="xor" and type of
	 * op2 is an immediate value, return "xori". If op2 is not of correct type for the
	 * instruction a SyntaxException is thrown, e.g. "acc32 = r0 andi r1".
	 * 
	 * Note this can lead to programming errors, if the user intended to add R0 and R5, but
	 * types:
	 * 
	 * acc32 = r0 add 5
	 * 
	 * This would be an error with strict interpretation of "add" (the assembler would flag this) but
	 * this function will substitute the "andi" instruction and the program will assemble without error.
	 * 
	 * TODO: Add command line "--strictImmed" option to disable this type of inference.
	 * 
	 * (
	 * Note this is also true of pure assignments, "r0 = 5" when intent was "r0 = r5". There is
	 * no way in the TOON syntax for the user to explicitly specify an R-to-R assignment. But this
	 * is true in high level languages as well, so maybe a moot point, e.g. in Java:
	 * 
	 * int v1 = 36;
	 * ...
	 * int othervar = 1; // Opps, meant to write "v1". No syntax error, compiler infers this is a const assignment.
	 * )
	 * 
	 * @param f
	 * @param op2
	 * @return
	 * @throws SyntaxException
	 */
	private static String inferFunc(String f, Operand op1, Operand op2) throws SyntaxException {
		
		f = f.toLowerCase();
		
		// For some operations we allow a generic function to act on an immediate
		// value or a register and we translate to the appropriate instruction.
		if (!op2.isReg()) {
			switch (f) {
			case "add": 	f = "addi"; break;
			case "adds": 	f = "addsi"; break;
			case "or":		f = "ori"; break;
			case "and":		f = "andi"; break;
			case "xor":		f = "xori"; break;
			case "mult":	f = "multri"; break; // Synthetic instruction for consistency
			// 64 bit
			case "macr":	f = "macri"; break; // Synthetic instruction for consistency
			case "machr":	f = "machri"; break;
			}
			// If a register form was specifically given and we don't auto-convert (above) flag an error
			switch (f) {
			case "multrr":
			case "slsr":
			case "srr":
			case "srar":
			case "macrr":
			case "machrr":
				throw new SyntaxException("Right operand '"+op2.getOpText()+"' is not a core register as required by instruction '"+f+"'");
			}
		}
		else {
			// Infer
			switch (f) {
			case "mult": f = "multrr"; break;
			case "sl": 	f = "slr"; break;
			case "sls": f = "slsr"; break;
			case "sr": 	f = "srr"; break;
			case "sra": f = "srar"; break;
			case "macd": f = "macrd"; break;
			case "macr": f = "macrr"; break;
			case "machd": f = "machrd"; break;
			case "machr":	f = "machrr"; break;
			}
			// 2nd operand is a register. If an immediate form was specified, this is an error.
			switch (f) {
			case "addi":
			case "addsi":
			case "ori":
			case "andi":
			case "xori":
			case "multri":
			case "sl":
			case "sls":
			case "sr":
			case "sra":
			case "macri":
			case "macid":
				throw new SyntaxException("Right operand is a register, but instruction '"+f+"' requires immediate (constant) value.");
			}
		}
		
		// Two instruction have reg/immediate value on the left side
		if (f.equals("macid") || f.equals("machid")) {
			// Explicit immediate form
			if (op1.isReg()) throw new SyntaxException("Instruction '"+f+"' requires immedidate (const) left operand, but '"+op1.getOpText()+"' is a register.");
		}
		else if (f.equals("macrd") || f.equals("machrd")) {
			// Explicit register form
			if (!op1.isReg()) throw new SyntaxException("Instruction '"+f+"' requires register left operand, but '"+op1.getOpText()+"' is not register.");
		}
		else if (f.equals("macd")) {
			f = op1.isReg() ? "macrd" : "macid"; // Choose the right instruction based on type of op1
		}
		else if (f.equals("machd")) {
			f = op1.isReg() ? "machrd" : "machid"; // Choose the right instruction based on type of op1
		}
		
		
		return f;
	}
	

	/**
	 * Builds and returns a TOON translator. Process exit codes:
	 * 0 = No errors, output file was written
	 * 1 = Invalid program args
	 * 2 = Syntax error in source file
	 * 3 = Unexpected system error
	 */
	public Toon(boolean annotate) {
		this.annotate = annotate;
	}
	
	public static void main(String[] args) {
		int lineCnt = 0;
		String readLine = null;
		boolean doAnnotation = true;
		
		List<String> argsList = new ArrayList<>(Arrays.asList(args));
		for (int i=0; i<argsList.size(); i++) {
			if (argsList.get(i).equalsIgnoreCase("--noannotate")) {
				doAnnotation = false;
				argsList.remove(i);
			}
		}
		
		
		try {
			if (argsList.size() != 2) {
				System.out.println("Expected 2 file name arguments <inputfile> <outputfile>");
				System.exit(1);
			}
			
			File srcFile= new File(argsList.get(0));
			File outFile= new File(argsList.get(1));
			
			if (!srcFile.exists()) {
				System.out.println("Input file '"+srcFile.getAbsolutePath()+"' not found.");
				System.exit(1);
			}
			
			Toon tooner = new Toon(doAnnotation);
			int errorCnt = 0;
			
			try (BufferedReader reader = Files.newBufferedReader(srcFile.toPath()); 
				BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
				
				readLine = reader.readLine();
				while (readLine != null) {
					lineCnt++;
					// Read source, translate, and write to output file
					try {
						writer.write(tooner.reTOON(readLine));
						writer.newLine();
					}
					catch (SyntaxException se) {
						errorCnt++;
						System.out.println("Error on line "+lineCnt+" '"+readLine+"'");
						System.out.println("  "+se.getMessage());
					}
					readLine = reader.readLine();
				}
			}
			
			System.out.println("Tooning complete with "+errorCnt+" errors, "+lineCnt+" lines written to output file "+outFile.getAbsolutePath());
			System.exit(errorCnt==0?0:2); // Exit code 2 = syntax errors occurred
			
		}
		catch (Throwable t) {
			System.out.println("Unexpected exception processing line "+lineCnt+" '"+(readLine==null?"":readLine)+"'");
			t.printStackTrace(System.out);
			System.exit(3); // Exit code 3 = code failure
		}
		
		// Should never get here
		System.out.println("Unexpected control flow in main.");
		System.exit(3);
	}

}
