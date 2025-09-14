package com.cabintech.toon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cabintech.fxcoremp.IfStmtRecord;
import com.cabintech.fxcoremp.Stmt;
import com.cabintech.utils.Util;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
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

//TODO: Unknown on left side of assignment produces an unhelpful error message:
//   .rn myName r0
//
//   typoInMyName = r6
//
//   "Invalid CR-to-NonRegister assigment statement."
//   If possible, note that the left operate is not recognized as a helpful hint.

public class Toon {
	
	/**
	 * All ASM opcodes that translate to a simple TOON assignment stmt
	 */
	private Set<String> ToonableCopySet = Set.of(
			"cpy_cm",
			"cpy_mc",
			"cpy_cs",
			"cpy_sc",
			"cpy_cc",
			"cpy_cmx",
			"wrdld",
			"rdacc64u",
			"rdacc64l",
			"sat64",
			"ldacc64u",
			"ldacc64l",
			"rddel",
			"wrdel",
			"rddelx",
			"wrdelx",
			"rddirx",
			"wrdirx",
			"interp"
	);
	
	/**
	 * Operations with 2 operands
	 */
	private Set<String> ToonableOpsSet = Set.of(
			"addi",
			"add",		
			"adds",		
			"addsi",
			"sub",
			"subs",
			"sl",		
			"slr",
			"sls",
			"slsr",
			"sr",		
			"srr",
			"sra",
			"srar",
			"or",		
			"ori",
			"and",		
			"andi",
			"xor",		
			"xori",
			
			"multrr",
			"multri",

			"macrr",
			"macri",
			"macrd",
			"macid",
			"machrr",
			"machri",
			"machrd",
			"machid"
			
	);
	
	// Conditional branching
	private Set<String> ToonableBranchSet = Set.of(
			"jgez", 
			"jneg",
			"jnz",
			"jz",
			"jzc",
			"jmp"
	);

	// Operations with 1 operand
	private Set<String> ToonableUnarySet = Set.of(
			"inv",
			"abs",
			"neg",
			"log2",
			"exp2"
			);

	
	
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
	
	// Map of allowed conditional expression operators for IF statements into target FXCore branch mnemonics.
	private Map<String,String> CondJmpExpr = Map.ofEntries(
			Map.entry("=", "jz"),
			Map.entry(">=", "jgez"),
			Map.entry("<", "jneg"),
			Map.entry("!=", "jnz"), // Can be applied to "0" or "acc32.sign"
			Map.entry("<>", "jnz"),
			// As of 1.4.1 these are no longer documented as synonyms for the conditional
			// comparison symbols, but keeping for compatibility for now
			Map.entry("neg", "jneg"),
			Map.entry("jneg", "jneg"),
			Map.entry("jgez", "jgez"),
			Map.entry("nz", "jnz"),
			Map.entry("gez", "jgez"), 
			Map.entry("jnz", "jnz"),
			Map.entry("z", "jz"),
			Map.entry("jz", "jz"),
			Map.entry("!=acc32.sign", "jzc"),
			Map.entry("zc", "jzc"),
			Map.entry("jzc", "jzc")
			);
	
	// Map of allowed condition expression operators for IF statements, into negation of FXCore branch mnemonics.
	private Map<String,String> CondJmpNegate = Map.ofEntries(
			Map.entry("=", "jnz"),
			Map.entry(">=", "jneg"),
			Map.entry("<", "jgez"),
			Map.entry("!=", "jz"), // Can be applied to "0" or "acc32.sign"
			Map.entry("<>", "jz")
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
	private Map<String,String> rnMap = new HashMap<>();
	public static Deque<IfStmtRecord> ifStmtStack = new ArrayDeque<>();
	private int ifCounter = 1;
	
	private boolean annotate = true; // Write original TOON statements as comments in each line of output. 
	
	private static String SEP1 = "\t\t";   // Separator between output instruction and first operand
	private static String SEP2 = "\t\t\t"; // Separator between output statement text and comment
	private static String SEP3 = "\t\t"; // Separator before '=' in generated TOON statements (asmToToon)
	
//	/**
//	 * Return a concatenation of all the elements of 'tokens' starting
//	 * at fromIndex, followed by any comment string from the stmt. This is
//	 * used to insure that the re-generated statement includes everything
//	 * following the TOON blank-delimited tokens. E.g. TOON stmt:
//	 * 
//	 * acc32 = r0 addi 31 + 4 ; my comment
//	 * 
//	 * This will parse as 7 tokens, but the base code will output:
//	 * 
//	 * addi r0,31
//	 * 
//	 * so this is used to append the remainder to make:
//	 * 
//	 * addi r0,31 + 4 ; my comment
//	 * 
//	 * @param tokens
//	 * @param fromIndex
//	 * @param stmt
//	 * @return
//	 */
//	private String joinRemainder(String[] tokens, int fromIndex, Stmt stmt) {
//		StringBuffer s = new StringBuffer();
//		for (int i=fromIndex; i < tokens.length; i++) {
//			s.append(" " + tokens[i]);
//		}
//		s.append(SEP);
//		
//		// Insert original stmt text as a comment for debugging */
//		if (annotate) {
//			s.append(" /* ");
//			s.append(stmt.getText());
//			s.append(" */");
//		}
//		
//		// Add original comment, if any
//		if (stmt.getComment().length() > 0) {
//			s.append(SEP+stmt.getComment());
//		}
//		return s.toString();
//	}
	
	/**
	 * Build statement into from all required parts. The label and comments will be taken from the
	 * given Stmt object. Between them will be all the (vararg) parts and all remainder tokens (blanks between)
	 * 
	 * stmt.getLabel(): parts[0]+parts[1]+...+tokens[remainder1]+tokens[remainder+1]...+stmt.getComment()
	 * 
	 * 'tokens' may be null
	 * 
	 * @param tokens
	 * @param remainderIndex
	 * @param stmt
	 * @return
	 */
	private String rebuildStatement(String stmtText, Stmt stmt, String[] tokens, int remainderIndex) {
		StringBuffer s = new StringBuffer();
		s.append(stmt.getLabel()); // Start with label, if any (includes ': ' separator)
		
		// Add generated part of the statement
		s.append(stmtText);
		
		// Add additional tokens starting at remainderIndex. These are left over from the original
		// statement text when it was white-space tokenized and these trailing tokens need to be
		// restored, e.g. "acc32 = r1 xor 8 + 2". The extra tokens would be "+" and "2"
		if (tokens != null) {
			for (int i=remainderIndex; i < tokens.length; i++) {
				s.append(" " + tokens[i]);
			}
			s.append(SEP2);
		}
		
		// Insert original stmt text as a comment for debugging */
		if (annotate) {
			s.append(" /* ");
			s.append(stmt.getText());
			s.append(" */ ");
		}
		
		// Add original comment, if any
		if (stmt.getComment().length() > 0) {
			s.append(SEP1 + stmt.getComment());
		}
		
		return s.toString();
	}
	
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
	public String toonToAsm(Stmt stmt) throws Exception {
		
		//TODO: Make a custom splitter to better handle operators adjacent to operands
		// acc66+=r0 macrr r1
		// if r0>=0 goto label
		
		//TODO: Handle multiline block comments (see macro processor)
		
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
				if (parts.length != 2) throw new SyntaxException("Invalid assignment syntax.", stmt);
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
			return stmt.getFullText(); // Nothing else to do with this statement, leave it unmodified
		}
		
		if (tokenCnt < 1) return stmt.getFullText(); // All statements have > 1 tokens, return anything else unmodified
		
		// IF conditional branch //TODO: This precludes a symbol named 'if', e.g. 'if = r0'.
		if (tokenList[0].equalsIgnoreCase("if")) {
			if (tokenCnt < 3) throw new SyntaxException("Invalid IF statement syntax, expected at least 3 tokens but found only "+tokenCnt+".", stmt);
			
			// See if the condition is a single token we recognize. Note right side can only be "0".
			String condToken = tokenList[2].toLowerCase();
			if (condToken.endsWith("0")) condToken = Util.jsSubstring(condToken, 0, condToken.length()-1); // Remove any trailing 0 char, like "<=0"
			
			String cond = CondJmpExpr.get(condToken); // Lookup the condition token
			if (cond == null) {
				throw new SyntaxException("Invalid IF statement syntax, condition '"+tokenList[2]+"' not recognized.", stmt);
			}
			
			// https://github.com/cabintech/FXCoreMP/issues/12
			if (tokenCnt == 4) {
				// Start of an IF block "if <core-reg> <cond>" (implies execute following code if the condition is TRUE, branch if FALSE)
				
				cond = CondJmpNegate.get(condToken); // Lookup the NEGATION condition token
				if (cond == null) {
					throw new SyntaxException("Invalid IF statement syntax, condition '"+tokenList[2]+"' not recognized.", stmt);
				}
				
				if (tokenList[3].equalsIgnoreCase("acc32.sign")) {
					if (!cond.equals("jnz")) {
						throw new SyntaxException("Invalid IF statement condition, the only test allowed for acc32.sign is '='.", stmt);
					}
					cond = "jzc"; // Special opcode for this test-and-branch
				}

				
				// Generate target labels
				String elseLabel = "_else_"+ifCounter;
				String endLabel = "_endif_"+ifCounter;
				ifCounter++;
				
				// Create a record of this IF statement
				IfStmtRecord ifRecord = new IfStmtRecord(stmt, condToken, elseLabel, endLabel);
				
				// Put it on a stack of nestable IF statements
				ifStmtStack.add(ifRecord);
				
				// Generate conditional brand to ELSE target (which will be same as ENDIF target if no ELSE is supplied)
				return rebuildStatement(cond + SEP1 + tokenList[1] + "," + elseLabel, stmt, tokenList, 4);
			}
			
			
			// 'IF <core-reg> <cond> <label>'
			
			//TODO: Write proper lexical tokenizer, this works on all valid conditions but fails to
			// flag some invalid ones like "if r6 <=0 0 goto xxx" and cannot parse a single token
			// like "if r6<0 goto xxx"
			
			// If next symbol is "0", ignore it
			int labelIndex = 3;  // Index of GOTO label
			if (tokenList[labelIndex].equals("0")) { // Skip over "0" if present, e.g. "acc32 >= 0 goto xxx"
				labelIndex++;
			}
			else if (tokenList[labelIndex].equalsIgnoreCase("acc32.sign")) { // Skip over "acc32.sign" if present, e.g. "r0 != acc32.sign goto xxx"
				labelIndex++;
				cond = "jzc"; // Different opcode
			} 
			
			Operand target = new Operand(tokenList[1], rnMap);
			if (!target.isCR()) throw new SyntaxException("Invalid IF statement syntax, operand '"+tokenList[1]+"' must be a CR.", stmt);
			
			// Skip optional 'goto' token
			if (tokenList[labelIndex].equalsIgnoreCase("goto")) {
				labelIndex++; // Label is next token after GOTO
			}
			
			// Generate FXCore assembler format (use original symbol, if any, for the register) for readability of the generated code
			return rebuildStatement(cond + SEP1 + tokenList[1] + "," + tokenList[labelIndex], stmt, tokenList, labelIndex+1);
		}
		
		if (tokenList[0].equalsIgnoreCase("else")) { // IF/ELSE/ENDIF https://github.com/cabintech/FXCoreMP/issues/12
			if (tokenCnt > 1) throw new SyntaxException("Invalid ELSE statement syntax, expected a single token but found "+tokenCnt+".", stmt);
			
			if (ifStmtStack.size() == 0) { // No active IF block
				throw new SyntaxException("Invalid ELSE statement, there is no enclosing IF statement.", stmt);
			}
			
			
			IfStmtRecord ifRecord = ifStmtStack.getLast(); // Most recent IF statement
			ifRecord.elseTaken().set(true); // Note the ELSE label has been generated
			
			// Generate 2 lines: a branch around the ELSE clause, and then the ELSE label for subsequent code.
			return rebuildStatement("jmp "+ifRecord.endLabel()+"\n"+ifRecord.elseLabel() + ":" + SEP1 , stmt, tokenList, 1);
		}
		
		if (tokenList[0].equalsIgnoreCase("endif")) { // IF/ELSE/ENDIF https://github.com/cabintech/FXCoreMP/issues/12
			if (tokenCnt > 1) throw new SyntaxException("Invalid ENDIF statement syntax, expected a single token but found "+tokenCnt+".", stmt);
			
			if (ifStmtStack.size() == 0) { // No active IF block
				throw new SyntaxException("Invalid ENDIF statement, there is no enclosing IF statement.", stmt);
			}
			
			String s = "";
			IfStmtRecord ifRecord = ifStmtStack.removeLast(); // Get (and remove) this IF statement from the stack
			if (!ifRecord.isElseTaken()) {
				// No ELSE has been generated, build it now. Must be on a line by itself, assembler does not understand "label1: label2:".
				s = ifRecord.elseLabel()+":\n";
			}
			ifRecord.elseTaken().set(true); // Note the ELSE label has been generated
			return rebuildStatement(s + ifRecord.endLabel() + ":" + SEP1 , stmt, tokenList, 1);
		}
		
		// Unconditional branch
		if (tokenList[0].toUpperCase().equals("JMP") || tokenList[0].toUpperCase().equals("GOTO")) {
			return rebuildStatement("jmp" + SEP1 + tokenList[1], stmt, tokenList, 2);
		}
		
		// Assignment style TOON statements 'x = ..."
		if (tokenList[1].equals("=") || tokenList[1].equals("+=")) {
			if (tokenCnt < 3) throw new SyntaxException("Invalid TOON instruction format, missing right side of assignment.", stmt);
			
			Operand left = new Operand(tokenList[0], rnMap);
			Operand right = new Operand(tokenList[2], rnMap);
			
			// "+=" only supported for certain ACC64 assignments, it is for decoration only and not required
			if (tokenList[1].equals("+=")) {
				if (!left.isAcc64()) throw new SyntaxException("Summing operator '+=' is only valid for ACC64 assignments.", stmt);
				if (!left.isPlain()) throw new SyntaxException("Indirect and modifications on ACC64 is not valid for summing operator '+=' assignments.", stmt);
				if (!Ops2ArgAcc64Set.contains(tokenList[3].toLowerCase())) throw new SyntaxException("Summing operator is not valid for '"+tokenList[3]+"' instruction.", stmt);
				tokenList[1] = "="; // Treat like a regular assignment
			}
			
			// Acc64 assignments from 32 bit registers "acc64.u = r5"
			if (left.isAcc64() && tokenCnt==3) {
				if (!right.isCR()) throw new SyntaxException("Right side of ACC64 assignment must be a CR.", stmt);
				char UL = left.isModLower() ? 'l' : (left.isModUpper() ? 'u' : '?');
				if (UL=='?') throw new SyntaxException("ACC64 must have .U or .L (Upper/Lower) postfix.", stmt);
				return rebuildStatement("ldacc64" + UL + SEP1 + right.getOpText(), stmt, tokenList, 3);
			}
			// 32-bit register assignment from Acc64 "r5 = acc64.l"
			if (right.isAcc64() && tokenCnt==3) {
				if (!left.isCR()) throw new SyntaxException("Left side of ACC64 assignment must be a CR.", stmt);
				String opCode = right.isModLower() ? "rdacc64l" : (right.isModUpper() ? "rdacc64u" : (right.isModSat() ? "sat64" : null));
				if (opCode == null) throw new SyntaxException("ACC64 must have .U or .L (Upper/Lower) or .SAT (Saturated) postfix.", stmt);
				return rebuildStatement(opCode + SEP1 + left.getOpText(), stmt, tokenList, 3);
			}
			
			
			// ACC32 operations (implicit assignment)
			
			// 1 arg functions "acc32 = <func> <cr>"
			if ((tokenCnt >= 3) && Ops1ArgAcc32Set.contains(tokenList[2].toLowerCase())) {
				if (tokenCnt < 4) throw new SyntaxException("Invalid assignment, missing expected operand after '"+tokenList[2]+"'.", stmt);
				right = new Operand(tokenList[3], rnMap);
				
				if (!left.isAcc32()) throw new SyntaxException("Target of assignment for '"+tokenList[2]+"' function must be ACC32.", stmt);
				// Special case, "ACC32 = INTERP (cr+const)" - right side is indirect sum of CR and a constant
				if (tokenList[2].toLowerCase().equals("interp")) {
					if (!right.isDMIndirect()) throw new SyntaxException("INTERP operand must be indirect '(CR)' or '(CR+constant)'. Expected parens not found.", stmt);
					String is = right.getOpText();
					String parts[] = Util.split(is, "\\+", 2);
					if (parts.length < 1)  throw new SyntaxException("INTERP operand must be of the form '(CR)' or '(CR+constant)'.", stmt);
					Operand p1 = new Operand(parts[0], rnMap);
					Operand p2 = new Operand("0", rnMap); // Assume constant is zero unless specified
					if (parts.length == 2) { // Second part was specified
						p2 = new Operand(parts[1], rnMap);
					}
					if (!p1.isCR()) throw new SyntaxException("INTERP operand must be of the form '(CR)' or '(CR+constant)'. CR not found.", stmt);
					if (p2.isReg()) throw new SyntaxException("INTERP operand must be of the form '(CR)' or '(CR+constant)'. Constant not found.", stmt);
					// Looks like a valid INTERP statement
					return rebuildStatement("interp" + SEP1 + p1.getOpText() + "," + p2.getOpText(), stmt, tokenList, 4);
				}
				if (!left.isPlain() || !right.isPlain()) throw new SyntaxException("This assignment operation does not support indirection or modifiers.", stmt);
				if (!right.isCR()) throw new SyntaxException("Source for assignment operation must be a CR.", stmt);
				return rebuildStatement(tokenList[2] + SEP1 + right.getOpText(), stmt, tokenList, 4);
			}
			
			// 2 arg functions "acc32 = <cr> <func> <op2>"
			if ((tokenCnt >= 4) && Ops2ArgAcc32Set.contains(tokenList[3].toLowerCase()) ) {
				String func = tokenList[3].toLowerCase();
				if (tokenCnt < 5) throw new SyntaxException("Invalid assignment, missing expected operand after '"+func+"'.", stmt);
				Operand op1 = new Operand(tokenList[2], rnMap);
				Operand op2 = new Operand(tokenList[4], rnMap);
				
				if (!left.isAcc32()) throw new SyntaxException("Target of assignment for '"+func+"' function must be ACC32.", stmt);
				if (!left.isPlain()) throw new SyntaxException("This assignment operation does not support indirection or modifiers on ACC32.", stmt);
				// Left side of func must be a CR for all 32-bit two operand instructions
				if (!op1.isCR()) throw new SyntaxException("Left operand for function '"+func+"' must be a CR.", stmt);
				
				// Some instructions have immediate and register forms for <op2> but we allow use of
				// generic instruction names, e.g. "r0 = r1 and 0x1" is really the "andi" instruction, not "and".
				// So we try to infer the correct instruction from the type of <op>.
				func = inferFunc(func, op1, op2, stmt);
				
				// Some special cases
				if (func.equals("interp")) {
					if (!op1.isDMIndirect()) throw new SyntaxException("Left operand for function '"+func+"' must be indirect notataion, enclose in parens.", stmt);
				} else {
					if (!op1.isPlain()) throw new SyntaxException("Left operand for function '"+func+"' does not support indirectin or modifiers.", stmt);
				}
				
				return rebuildStatement(func + SEP1 + op1.getOpText() + "," + op2.getOpText(), stmt, tokenList, 5);
			}
			
			// 2 arg functions "acc64 = <cr> <func> <op2>"
			if ((tokenCnt >= 4) && Ops2ArgAcc64Set.contains(tokenList[3].toLowerCase()) ) {
				String func = tokenList[3].toLowerCase();
				if (tokenCnt < 5) throw new SyntaxException("Invalid assignment, missing expected operand after '"+func+"'.", stmt);
				Operand op1 = new Operand(tokenList[2], rnMap);
				Operand op2 = new Operand(tokenList[4], rnMap);
				
				if (!left.isAcc64()) throw new SyntaxException("Target of assignment for '"+func+"' function must be ACC64.", stmt);
				if (!left.isPlain()) throw new SyntaxException("This assignment operation does not support indirection or modifiers on ACC64.", stmt);
				
				// Some instructions have immediate and register forms for <op2> but we allow use of
				// generic instruction names, e.g. "r0 = r1 and 0x1" is really the "andi" instruction, not "and".
				// So we try to infer the correct instruction from the type of <op>.
				func = inferFunc(func, op1, op2, stmt);
				
				// Left side of func must be a CR for most 64-bit two operand instructions
				if (func.equals("macid") || func.equals("machid")) {
					if (op1.isReg()) throw new SyntaxException("Left operand for function '"+func+"' cannot be a register, immediate constant value is required.", stmt);
				}
				else {
					if (!op1.isCR()) throw new SyntaxException("Left operand for function '"+func+"' must be a CR.", stmt);
				}
				// Some are indirect memory addressing
				if (func.equals("macrd") || func.equals("macid") || func.equals("machrd") || func.equals("machid")) {
					if (!op2.isDMIndirect()) throw new SyntaxException("Right operand for function '"+func+"' is delay memory indirect constant, must be enclosed in parens.", stmt);
				}
				
				return rebuildStatement(func + SEP1 + op1.getOpText() + "," + op2.getOpText(), stmt, tokenList, 5);
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
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of indirect MR assignment must be a CR.", stmt);
					opCode = "cpy_cmx";
				}
				
				// Right side is indirect to delay memory r0=(r5)
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && right.isDMIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of indirect delay memory assignment must be a CR.", stmt);
					opCode = "rddelx";
				}
				
				// Right side is absolute indirect to delay memory r0=#(r5)
				if (!left.isModified() && !left.isIndirect() && !right.isModified() && right.isAbsDMIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of absolute indirect delay memory assignment must be a CR.", stmt);
					opCode = "rddirx";
				}
				
				// Left side is indirect to delay memory (r0)=r5
				if (!left.isModified() && left.isDMIndirect() && !right.isModified() && !right.isIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of indirect delay memory assignment must be a CR.", stmt);
					opCode = "wrdelx";
				}
				
				// Left side is absolute indirect to delay memory #(r0)=r5
				if (!left.isModified() && left.isAbsDMIndirect() && !right.isModified() && !right.isIndirect()) {
					if (!left.isCR() || !right.isCR()) throw new SyntaxException("Left and right sides of absolute indirect delay memory assignment must be a CR.", stmt);
					opCode = "wrdirx";
				}
				
				if (opCode == null) throw new SyntaxException("Invalid register-to-register assigment statement.", stmt);
				
				return rebuildStatement(opCode + SEP1 + left.getOpText() + "," + right.getOpText(), stmt, tokenList, 3);
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
				// Special case of assigning zero to ACC32 "acc32 = 0"
				if (right.getText().equals("0")) {
					if (!left.isAcc32()) {
						throw new SyntaxException("Assigment of zero is supported only for ACC32.", stmt);
					}
					// Generate "xor acc32,acc32"
					opCode = "xor";
					right = left; // Both are ACC32
				}
				if (opCode == null) throw new SyntaxException("Invalid NonRegister-to-CR assigment statement.", stmt);
				return rebuildStatement(opCode + SEP1 + left.getOpText() + "," + right.getOpText(), stmt, tokenList, 3);
			}
			
			// Assignment from CR to non-register source
			if (right.isCR()) {
				// Write fixed delay memory address from CR "(3920) = r5"
				if (!left.isModified() && left.isDMIndirect() && !right.isModified() && !right.isIndirect()) {
					return rebuildStatement("wrdel" + SEP1 + left.getOpText() + "," + right.getOpText(), stmt, tokenList, 3);
				}
				throw new SyntaxException("Invalid CR-to-NonRegister assignment statement.", stmt);
			}
			
			// If none of the above, we don't recognize this assignment statement
			throw new SyntaxException("Invalid assignment statement, function or operand types are not recognized.", stmt);
			
			
		} // End of assignment statements
			
		
		// If no match above, it is not TOON format so pass it through
		return stmt.getFullText();
			
	}

	/**
	 * Returns the given source statement 's' as-is, or if it is recognized as an FXCore assembler
	 * statement, an equivalent TOON statement is returned. This assumes valid assembler code, it is
	 * not intended to validate the incoming assembler. The input may contain TOON statements which
	 * will be passed through unchanged.
	 * @param s
	 * @return
	 * @throws Exception
	 */
	public String asmToToon(String s) throws Exception {
		
		// Assembler statements are:
		// [<label>]:<optionalWS><opcode><reqdWS><op1><optionalWS>[,<optioalWS><op2>]
		
		Stmt stmt = new Stmt(s, 0, ""); // Comments and label are removed and text is trimmed
		
		// Now we have
		// <opcode> <op1>[,<op2>]
		
		String[] tokenList = Util.split(stmt.getText(), "\\p{Space}+", 2); // Extract opcode which must be whitespace delimited + remainder
		int tokenCnt = tokenList.length;
		if (tokenCnt < 2) return s; // Not anything we recognize, pass it through with no changes
		
		// Extract parts
		String opcode = tokenList[0].trim().toLowerCase();
		String opList[] = Util.split(tokenList[1].trim(), ",", 2); // Separate operands
		String op1 = opList.length > 0 ? opList[0].trim() : "";
		String op2 = opList.length > 1 ? opList[1].trim() : "";
		
		// Copy assignments (no operator)
		if (ToonableCopySet.contains(opcode)) {
			// Special cases for most that are not CPY_XX
			switch (opcode) {
			case "cpy_cmx":
				op2 = "[" + op2 + "]"; // Right operand is special syntax for indirect MR access
				break;
			case "wrdld": 
				op1 = op1 + ".U"; // Left operand gets .U postfix
				break;
			case "rdacc64u":
				op2 = "ACC64.U";
				break;
			case "rdacc64l":
				op2 = "ACC64.L";
				break;
			case "ldacc64u":
				op2 = op1;
				op1 = "ACC64.U";
				break;
			case "ldacc64l":
				op2 = op1;
				op1 = "ACC64.L";
				break;
			case "sat64":
				op2 = "ACC64.SAT";
				break;
			case "rddel": // Right operand delay-memory indirect
			case "rddelx":
			case "rddirx":
				op2 = "(" + op2 + ")";
				if (opcode.equals("rddirx")) op2 = "#"+op2; // Absolute indirect
				break;
			case "wrdel": // Left operand delay-memory indirect
			case "wrdelx":
			case "wrdirx":
				op1 = "(" + op1 + ")";
				if (opcode.equals("wrdirx")) op1 = "#"+op1; // Absolute indirect
				break;
			case "interp": // CR+const indirect
				if (op2.equals("0")) {
					op2 = "INTERP (" + op1 + ")"; // Generate short-hand with no constant specified
				}
				else {
					op2 = "INTERP (" + op1 + "+" + op2 + ")";
				}
				op1 = "ACC32"; // Left side is fixed
			}
			
			return rebuildStatement(op1 + SEP3 + "= " + op2, stmt, null, 0);
		}
		
		// Operational assignments with one operand
		if (ToonableUnarySet.contains(opcode)) {
			return rebuildStatement("ACC32" + SEP3 + "= "+ opcode + " " + op1, stmt, null, 0);
		}
		
		// Operational assignments with two operands
		if (ToonableOpsSet.contains(opcode)) {
			String target = "ACC32"; // Most common case
			String equals = "=";
			
			// Use generics where TOON supports them
			switch (opcode) {
			case "andi": opcode = "and"; break;
			case "ori" : opcode = "or"; break;
			case "xori": opcode = "xor"; break;
			case "slr" : opcode = "sl"; break;
			case "slsr": opcode = "sls"; break;
			case "srr" : opcode = "sr"; break;
			case "srar": opcode = "sra"; break;
			case "addi": opcode = "add"; break;
			case "addsi": opcode = "adds"; break;
			case "multrr": opcode = "mult"; break;
			case "multri": opcode = "mult"; break;
			case "macrr": opcode = "macr"; break;
			case "macri": opcode = "macr"; break;
			case "macrd": opcode = "macd"; break;
			case "macid": opcode = "macd"; break;
			case "machrr": opcode = "machr"; break;
			case "machri": opcode = "machr"; break;
			case "machrd": opcode = "machd"; break;
			case "machid": opcode = "machd"; break;
			}
			
			// Targets 64 bit accumulator
			switch (opcode) {
			case "macrr":
			case "macri":
			case "macr":
			case "macrd":
			case "macid":
			case "macd":
			case "machrr":
			case "machri":
			case "machr":
			case "machrd":
			case "machid":
			case "machd":
				target = "ACC64";
				equals = "+=";
			}
			
			// Indirect addressing
			switch (opcode) {
			case "macrd":
			case "macid":
			case "macd":
			case "machrd":
			case "machid":
			case "machd":
				op2 = "(" + op2 + ")";
			}
			
			return rebuildStatement(target + SEP3 + equals + " " + op1 + " " + opcode + " " + op2, stmt, null, 0);
		}
		
		if (ToonableBranchSet.contains(opcode)) {
			
			switch (opcode) {
			case "jgez": opcode = ">=0"; break;  
			case "jneg": opcode = "<0"; break;
			case "jnz":  opcode = "!=0"; break;
			case "jz":   opcode = "=0"; break;
			case "jzc":  opcode = "!=ACC32.SIGN"; break;
			}
			
			if (opcode.equals("jmp")) { // No condition
				return rebuildStatement("GOTO "+op1, stmt, null, 0);
			}
			
			return rebuildStatement("IF " + op1 + " " + opcode + " GOTO " + op2, stmt, null, 0);
		}
		
		// If no match above, it is not an assembler statement we recognize, so pass it through
		return s;
			
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
	private static String inferFunc(String f, Operand op1, Operand op2, Stmt context) throws SyntaxException {
		
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
				throw new SyntaxException("Right operand '"+op2.getOpText()+"' is not a core register as required by instruction '"+f+"'", context);
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
				throw new SyntaxException("Right operand is a register, but instruction '"+f+"' requires immediate (constant) value.", context);
			}
		}
		
		// Two instruction have reg/immediate value on the left side
		if (f.equals("macid") || f.equals("machid")) {
			// Explicit immediate form
			if (op1.isReg()) throw new SyntaxException("Instruction '"+f+"' requires immedidate (const) left operand, but '"+op1.getOpText()+"' is a register.", context);
		}
		else if (f.equals("macrd") || f.equals("machrd")) {
			// Explicit register form
			if (!op1.isReg()) throw new SyntaxException("Instruction '"+f+"' requires register left operand, but '"+op1.getOpText()+"' is not register.", context);
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
	
}
