package com.cabintech.fxcoremp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
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
	private Set<String> Ops2Acc32InstrSet = Set.of(
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
			"interp",
			"multrr",
			"multri"
			);
	
	// 1 operand instructions that target ACC32 'acc32 = instr x'
	private Set<String> Ops1Acc32InstrSet = Set.of(
			"inv",
			"abs",
			"neg",
			"log2",
			"exp2"
			);
	
	// 1 operand instructions that target ACC64 'acc64 = instr x'
	private Set<String> Ops1Acc64InstrSet = Set.of(
			"ldacc64u",
			"ldacc64l");

	// 2 operand instructions that target ACC64 'acc64 = x instr y'
	private Set<String> Ops2Acc64InstrSet = Set.of(
			"macri",
			"macrd",
			"machrr",
			"machri",
			"machrd",
			"machid"
			);

	// Copy instructions 'target = op source'
	private Set<String> CopyInstrSet = Set.of(
			"cpy_cc",
			"cpy_cm",
			"cpy_cs",
			"cpy_mc",
			"cpy_sc",
			"cpy_cmx",
			"rddel",
			"wrdel",
			"rddelx",
			"wddelx",
			"wrdld");

	
	// 0 operand instructions 'target = instr'
	private Set<String> Ops0InstrSet = Set.of(
			"rdacc64u",
			"rdacc64l",
			"sat64");
	
	// Conditional jump 'label instr x'
	private Set<String> CondJmpInstr = Set.of(
			"jgez",
			"jneg",
			"jnz",
			"jz",
			"jzc");
	
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
	
	// All FXCore core registers
	private Set<String> CrSet = Set.of(
			"R0",
			"R1",
			"R2",
			"R3",
			"R4",
			"R5",
			"R6",
			"R7",
			"R8",
			"R9",
			"R10",
			"R11",
			"R12",
			"R13",
			"R14",
			"R15",
			"ACC32",
			"FLAGS"
			);
	
	// All FXCore Special Function Registers
	private Set<String> SrfNameSet = Set.of(
			"IN0",
			"IN1",
			"IN2",
			"IN3",
			"OUT0",
			"OUT1",
			"OUT2",
			"OUT3",
			"PIN",
			"SWITCH",
			"POT0_K",
			"POT1_K",
			"POT2_K",
			"POT3_K",
			"POT4_K",
			"POT5_K",
			"POT0",
			"POT1",
			"POT2",
			"POT3",
			"POT4",
			"POT5",
			"POT0_SMTH",
			"POT1_SMTH",
			"POT2_SMTH",
			"POT3_SMTH",
			"POT4_SMTH",
			"POT5_SMTH",
			"LFO0_F",
			"LFO1_F",
			"LFO2_F",
			"LFO3_F",
			"RAMP0_F",
			"RAMP1_F",
			"LFO0_S",
			"LFO0_C",
			"LFO1_S",
			"LFO1_C",
			"LFO2_S",
			"LFO2_C",
			"LFO3_S",
			"LFO3_C",
			"RAMP0_R",
			"RAMP1_R",
			"MAXTEMPO",
			"TAPTEMPO",
			"SAMPLECNT",
			"NOISE",
			"BOOTSTAT",
			"TAPSTKRLD",
			"TAPDBRLD",
			"SWDBRLD",
			"PRGDBRLD",
			"OFLRLD"	
			);
	
	// Track assembler .rn statements so we can determine the type of assignment operands 'x = y'.
	private  Map<String,String> rnMap = new HashMap<>();
	
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
	private static String joinRemainder(String[] tokens, int fromIndex, Stmt stmt) {
		StringBuffer s = new StringBuffer();
		for (int i=fromIndex; i < tokens.length; i++) {
			s.append(" " + tokens[i]);
		}
		
		// Insert original stmt text as a comment for debugging */
		s.append(SEP);
		s.append(" /* ");
		s.append(stmt.getText());
		s.append(" */");
		
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
		
		Stmt stmt = new Stmt(s, 0, ""); // Use this to remove comments (TODO: redundant with earlier processing...)
		
		String[] tokenList = Util.split(stmt.getText(), "\\p{Space}+"); // Tokenize on white space including tabs
		int tokenCnt = tokenList.length;
		
		// TOON statements can be written without whitespace around the assignment operator 'a= b'.
		// Any '=' in the first 2 tokens needs to be made a separate token
		for (int tokenNum = 0; tokenNum <= Math.min(1, tokenCnt-1); tokenNum++) {
			if (tokenList[tokenNum].equals("=")) continue;
			
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
		}
		
		if (tokenCnt < 2) return s; // All TOON statements have > 2 tokens
		
		// 1st token can be IF for conditional branch //TODO: This precludes a symbol named 'if', e.g. 'if = r0'.
		if (tokenList[0].equalsIgnoreCase("if")) {
			if (tokenCnt < 4) throw new SyntaxException("Invalid IF statement syntax, expected 4 tokens but found only "+tokenCnt+" in '"+s+"'");
			// 'IF <core-reg> <cond> <label>'
			// For now the conditional expression must be space-separated, cannot write 'if r6>=0'
			
			String instr = CondJmpExpr.get(tokenList[2]);
			if (instr == null) throw new SyntaxException("Invalid IF statement syntax, condition '"+tokenList[2]+"' not recognized in '"+s+"'");
			
			// Validate the operand is a core register, the only allowed type for cond branching
			String cr = tokenList[1].toUpperCase();
			if (rnMap.containsKey(cr)) cr = rnMap.get(cr); // Translate any renamed (.rn) symbol

			if (!CrSet.contains(cr)) {
				throw new SyntaxException("Invalid IF statement syntax, operand '"+tokenList[1]+"' is not a core register in '"+s+"'");
			}
			
			// Remove optional 'goto' token
			int labelIndex = 3;  // If no GOTO token
			if (tokenList[3].equalsIgnoreCase("goto")) {
				labelIndex = 4; // Label is next token after GOTO
			}
			
			// Generate FXCore assembler format (use original symbol, if any, for the register) for readability of the generated code
			return instr+SEP+tokenList[1]+","+tokenList[labelIndex]+joinRemainder(tokenList, labelIndex+1, stmt);
		}
		
		// 2nd token can be a JMP mnemonic 'x jmp label' --> jmp x,label
		if (CondJmpInstr.contains(tokenList[1])) {
			if (tokenCnt > 3) throw new SyntaxException("TOON syntax error, too many tokens, jump statements expect 'Rx <jmp> label'");
			return tokenList[1] + " " + tokenList[0] + "," + tokenList[2] + joinRemainder(tokenList, 3, stmt);
		}
		
		// Assignment style TOON statements 'x = ..."
		if (tokenList[1].equals("=")) {
			if (tokenCnt < 3) throw new SyntaxException("Invalid TOON instruction format, missing right side of assignment: '"+s+"'");
			
			if (Ops0InstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
				// A simple "x = mnemonic" style TOON statements, just reformat to "mnemonic x".
				if (tokenCnt > 3) throw new SyntaxException("Invalid syntax for TOON '' mnemonic, too many tokens, should be 'x = <instr>'");
				return tokenList[2] + SEP + tokenList[0] + joinRemainder(tokenList, 3, stmt);
			}
			
			if (CopyInstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
				// Copy instructions 'target = instr source'. Source can be a const expression with multiple tokens.
				return tokenList[2] + SEP + tokenList[0] + "," + tokenList[3] + joinRemainder(tokenList, 4, stmt);
			}
			
			if (tokenCnt >= 4) { // All these require at least 4 tokens
				if (Ops2Acc32InstrSet.contains(tokenList[3])) { // Mnemonic is 4th token
					// 2 operand instructions that target acc32 'acc32 = x instr y'. Y can be a const express of multiple tokens
					if (tokenCnt < 5) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
					if (!tokenList[0].toLowerCase().equals("acc32")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC32: '"+s+"'");
					return tokenList[3]+SEP+tokenList[2]+","+tokenList[4]+joinRemainder(tokenList, 5, stmt);
				}
				
				if (Ops2Acc64InstrSet.contains(tokenList[3])) { // Mnemonic is 4th token (same as Ops2Acc32 except target is ACC64)
					// 2 operand instructions that target acc64 'acc64 = x instr y'
					if (tokenCnt < 5) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
					if (!tokenList[0].toLowerCase().equals("acc64")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC64: '"+s+"'");
					return tokenList[3]+SEP+tokenList[2]+","+tokenList[4]+joinRemainder(tokenList, 5, stmt);
				}
				
				if (Ops1Acc32InstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
					// 1 operand instructions that target acc32 'acc32 = instr y' --> 'instr y'. Assume Y could be multiple tokens.
					if (tokenCnt < 4) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
					if (!tokenList[0].toLowerCase().equals("acc32")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC32: '"+s+"'");
					return tokenList[2]+SEP+tokenList[3]+joinRemainder(tokenList, 4, stmt);
				}
				
				if (Ops1Acc64InstrSet.contains(tokenList[2])) { // Mnemonic is 3rd token
					// 1 operand instructions that target acc64 'acc64 = instr y' --> 'instr y'. Assume Y could be multiple tokens.
					if (tokenCnt < 4) throw new SyntaxException("TOON syntax error, missing one or more tokens in: '"+s+"'");
					if (!tokenList[0].toLowerCase().equals("acc64")) throw new SyntaxException("Invalid target on left side of assignment, target of this operation is ACC64: '"+s+"'");
					return tokenList[2]+SEP+tokenList[3]+joinRemainder(tokenList, 4, stmt);
				}
			}
			
			// If none of the above has matched, try to interpret "x = y" assignment statements by type inference
			// Note that 'x = cpy_** y' and 'x = instr' has already been handled.
			if (tokenCnt == 3) {
				String left = tokenList[0].toUpperCase();
				String right = tokenList[2].toUpperCase();
				
				// Targets and sources can by symbolic names for registers, defined by prior ".rn" statements.
				// If the left or right operands are symbolics we use the resolved actual register name.
				//TODO: Does the .rn have to occur before its first use??
				
				if (rnMap.containsKey(left)) left = rnMap.get(left);
				if (rnMap.containsKey(right)) right = rnMap.get(right);
				
				char targetType = '?'; // Assume core register
				char sourceType = '?';
				
				if (right.matches("MR[0-9]+")) {
					sourceType = 'm';
				}
				else if (CrSet.contains(right)) {
					sourceType = 'c';
				}
				else if (SrfNameSet.contains(right)) {
					sourceType = 's';
				}
				
				if (left.matches("MR[0-9]+")) {
					targetType = 'm';
				}
				else if (left.equals("ACC32") || left.matches("R[0-9]+") || left.equals("FLAGS")) {
					targetType = 'c';
				}
				else if (SrfNameSet.contains(left)) {
					targetType = 's';
				}
				
				if (targetType == '?') throw new SyntaxException("Unable to infer type of target (left side) token in '"+s+"'");
				if (sourceType == '?') throw new SyntaxException("Unable to infer type of source (right side) token in '"+s+"'");
				
				// Only some combinations can be implemented in a single instruction
				if(
					(targetType=='c' && (sourceType=='c' || sourceType=='m' || sourceType=='s')) ||
					(targetType=='m' && sourceType=='c') ||
					(targetType=='s' && sourceType=='c')
				) {
					// Now we can generate an appropriate copy statement
					return "cpy_"+targetType+sourceType+SEP+tokenList[0]+","+tokenList[2]+joinRemainder(tokenList, 3, stmt);
				}
				throw new SyntaxException("Unsupported copy operation source type '"+sourceType+"' and target type '"+targetType+"' in statement '"+s+"'");
			}
		}
		
		// If no match above, it is not TOON format so pass it through (or TOON format is same as assembler format, e.g. JMP instruction)
		return s;
	}
	

	/**
	 * Builds and returns a TOON translator. Process exit codes:
	 * 0 = No errors, output file was written
	 * 1 = Invalid program args
	 * 2 = Syntax error in source file
	 * 3 = Unexpected system error
	 */
	public Toon() {
	}
	
	public static void main(String[] args) {
		int lineCnt = 0;
		try {
			if (args.length != 2) {
				System.out.println("Expected 2 file name arguments <inputfile> <outputfile>");
				System.exit(1);
			}
			
			File srcFile= new File(args[0]);
			File outFile= new File(args[1]);
			
			if (!srcFile.exists()) {
				System.out.println("Input file '"+srcFile.getAbsolutePath()+"' not found.");
				System.exit(1);
			}
			
			Toon tooner = new Toon();
			
			try (BufferedReader reader = Files.newBufferedReader(srcFile.toPath()); 
				BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
				
				String readLine = reader.readLine();
				while (readLine != null) {
					lineCnt++;
					// Read source, translate, and write to output file
					writer.write(tooner.reTOON(readLine));
					writer.newLine();
					readLine = reader.readLine();
				}
			}
			
			System.out.println("Tooning complete, "+lineCnt+" lines written to output file "+outFile.getAbsolutePath());
			
		}
		catch (SyntaxException se) {
			System.out.println("Error on input line "+lineCnt);
			System.out.println(se.getMessage());
			System.exit(2);
		}
		catch (Throwable t) {
			System.out.println("Unexpected exception:");
			t.printStackTrace(System.out);
			System.exit(3);
		}
		
		System.exit(0);
	}

}
