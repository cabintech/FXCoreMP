package com.cabintech.fxcoremp;

/**
 * FXCore DSP assembler language macro processor.
 * 
 * This processor implements a macro-substitution language for the FXCore DSP assembler similar to
 * how the C/C++ preprocessor adds macro capabilities to C and C++ language files. Before the development
 * of this processor, an attempt was made to leverage the C preprocessor for this purpose.
 * 
 * The syntax of this macro languge leverages the fact that the FXCore assembly language is farily simple
 * Syntactically. It does not use the "$" character for any purpose, so it is safe and easy to use it for
 * macro syntax (similar to how "#" is used by the C preprocessor).
 * 
 * There are two types of substitution done in the macro processing. When a macro is evaluated, any arguments
 * supplied on the evaluation are substituted into the macro text using the ${arg-name} syntax. This fully
 * delimited syntax avoid any ambiguity about where the argument name ends, so it is possible for two
 * arguments to be directly adjoining with no ambiguity (unlike the C preprocessor where you have to jump through
 * syntactic hoops to concatenate substituted values). 
 * 
 * Macro definitions are substituted (evaluated) when the appear in any of the forms:
 * $macro-name
 * $macro-name()
 * $macro-name(arg1, arg2, ...)
 * 
 * The first 2 are both evaluations of a macro with no arguments, but the second form may be needed if the
 * character immediately following is a valid macro naming character. E.g. if the intent is to invoke a
 * "CHANNEL_NUM" macro and have the result adjacent to the text "ABC" like this:
 * 
 * $CHANNEL_NUMABC
 * 
 * This would not work because the macro name would be interpreted as "CHANNEL_NUMABC" and no such macro
 * would be found. In this case the empty parens can be used to separate the macro invocation from surrounding
 * text:
 * 
 * $CHANNNEL_NUM()ABC
 * 
 * With this syntax, the substitution would be done as expected and the result would immediately prefix "ABC".
 * 
 * This processor supports both named and positional
 * arguments (but only one form should be used in a given invocation). Positional argument are good for simple
 * cases where the arguments are obvious, such as:
 * 
 * $DIVIDE(x,y)
 * 
 * However, when there is a long list of arguments and especially when multiple registers are passed as the
 * values, the code becomes unclear:
 * 
 * $CALC_DELAY(r0, r6, r8, r9, r12)
 * 
 * That code requires you to examine the macro definition to know what that is doing. So in that case it is
 * better to write the invocation as
 * 
 * $CALC_DELAY(buffer_base=r0, offset=r6, cv=r8, tempReg1=r9, tempReg2=r12)
 * 
 * Now it is clear what each register supplied to the macro is being used for. With a named argument list like
 * this, order is not important and they can be written in whatever provides the best source code clarity. The
 * following is identical to the invocation above:
 * 
 * $CALC_DELAY(tempReg1=r9, tempReg2=r12, offset=r6, buffer_base=r0,  cv=r8)
 * 
 * Note the processor allows mixing positional and named arguments but it is strongly discouraged (and it may
 * disallow such usage in the future as it can lead to ambiguous argument values).
 * 
 * 
 * 
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pre-process FXCore source code before running it through the CPP (C preprocessor).
 * @author Mark
 *
 */

public class FXCoreMPMain {
	
	private static File sourceDir = null;
	
	// Global list of defined macros, case-insensitive mapped by name
	public static Map<String,Macro> macroMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	
	public static String verbose = ""; // 'info' or 'debug' for cmdline output
	
	// Env variables specified on cmd line, used by $if statements
	public static SafeMap envMap = new SafeMap();
	
	public static SourceContext context = new SourceContext();
	
	public static File srcFile = null;
	public static File outFile = null;


	public FXCoreMPMain() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * Pass 1 processes all $include and $macro definition statements and returns the input
	 * file with all $include files embedded, and all $macro definitions removed.
	 * @param inFile
	 * @param writer
	 * @param includedFiles
	 * @throws Throwable
	 */
	private static void processFilePass1(File inFile, List<Stmt> writer, List<String> includedFiles) throws Throwable {
		Util.info("PreCPP: Processing pass 1 on file "+inFile.getName());
		
		if (!inFile.exists()) {
			throw new IOException("Input file '"+inFile.getAbsolutePath()+"' not found.");
		}
		SourceContext.startFile(inFile.getName());
		
		int lineNum = 0;
		// Process the input file one line at a time
		try (BufferedReader reader = Files.newBufferedReader(inFile.toPath())) {
			List<String> srcLines = new ArrayList<>();
			
			String readLine = reader.readLine();
			while (readLine != null) {
				srcLines.add(readLine);
				readLine = reader.readLine();
			}
			
			// Add a blank line at the end to insure any multi-line macro at the end
			// of the file is terminated
			srcLines.add("");
			
			boolean inDefine = false;
			boolean inIf = false;
			boolean ifCondition = false;
			Stmt blockCommentStartStmt = null; // Statement that started a block comment
			List<Stmt> macroLines = new ArrayList<Stmt>();
			for (String inLine: srcLines) {
				boolean omitOutput = false; // Do not write the current statement to the output stream
				lineNum++;
				SourceContext.atLine(lineNum);
				Stmt stmt = new Stmt(inLine, lineNum, inFile.getName());
				String stmtText = stmt.getText().replaceAll("\t"," ").trim(); // Comments removed, tabs converted to spaces, trimmed
				
				// If we are in a multiline comment, just output it and skip all processing. This takes
				// precedence over all other source code processing.
				
				if (blockCommentStartStmt != null) {
					// This line is part of a block comment, ignore it for processing purposes.
					// Do nothing, just output as usual below.
					stmt.setIgnore(true); 
				}
				
				else if (inDefine && stmtText.startsWith("$endmacro")) {
					Macro m = new Macro(macroLines);
					String macroName = m.getName();
					if (macroMap.containsKey(macroName)) {
						throw new SyntaxException("Macro name '"+m.getName()+"' is already defined. Line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					}
					macroMap.put(macroName, m);
					inDefine = false;
					macroLines.clear();
					omitOutput = true; // Nothing to output for this line
				}
				
				// Accumulating lines of a multi-line macro?
				else if (inDefine) {
					macroLines.add(stmt);
					omitOutput = true; // Nothing to output for a macro definition
				}
				
				else if (stmtText.startsWith("$endif")) { // End of a $if condition
					inIf = false;
					omitOutput = true; // Nothing to output
				}
				
				else if (inIf && ifCondition==false) {
					omitOutput = true; // Skip lines in FALSE $if block
				}
				
				else if (stmtText.startsWith("$if(")) {
					// Condition section '$if(envname=xxx)'
					if (inIf) throw new SyntaxException("Nested $if statements are not supported at line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					
					int endParen = stmtText.indexOf(')');
					if (endParen<0) throw new SyntaxException("Missing closing paren of $if statement at line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					String conditionExp = Util.jsSubstring(stmtText, 4, endParen).trim();
					String[] expParts = Util.split(conditionExp, "="); // Note if this is "!=" the ! stays with the left operand
					if (expParts.length != 2) throw new SyntaxException("Invalid $if expression at line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					boolean operator = true;
					if (expParts[0].endsWith("!")) {
						operator = false;
						expParts[0] = Util.jsSubstring(expParts[0], 0, expParts[0].length()-1); // Trim off the "!" from left operand
					}
					
					inIf = true;
					ifCondition = envMap.getStr(expParts[0].toLowerCase()).equalsIgnoreCase(expParts[1]); // TRUE if expression matches env setting
					if (!operator) {
						ifCondition = !ifCondition; // Invert for "!=" operator
					}
					omitOutput = true; // Do not output the $if statement itself
				}
				
				//-----------------------------------------
				// $include
				//-----------------------------------------
				else if (stmtText.startsWith("$include ")) {
					if (stmtText.length() < 10) {
						throw new SyntaxException("Invalid $include statement, no file specified");
					}
					String incFileName = stmtText.substring(9).replace("\"", "").trim();
					if (!includedFiles.contains(incFileName)) { // Only include a file once
						// Recursive call to process the #include'd file
						includedFiles.add(incFileName);
						File incFile = new File(sourceDir, incFileName);
						processFilePass1(incFile, writer, includedFiles);
					}
					omitOutput = true; // Nothing to write for this input line, just continue with next line
				}
				
				//-----------------------------------------
				// $macro
				//-----------------------------------------
				else if (stmtText.startsWith("$macro ")) { // Start of macro definition
					if (stmtText.endsWith("++")) { 
						// Start of multi-line macro definition
						macroLines.clear();
						inDefine = true;
						macroLines.add(stmt);  // Add first line of macro
						omitOutput = true; // Do not output macro definition lines
					}
					else {
						// Single line macro definition
						macroLines.clear();
						inDefine = false;
						macroLines.add(stmt); // Add first and only line to macro
						Macro m = new Macro(macroLines);
						String macroName = m.getName();
						if (macroMap.containsKey(macroName)) {
							throw new SyntaxException("Macro name '"+m.getName()+"' is already defined. Line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
						}
						macroMap.put(macroName, m);
						omitOutput = true; // Do not output macro definition lines
					}
				}
				
				//-----------------------------------------
				// $set
				//-----------------------------------------
				else if (stmtText.startsWith("$set ")) { // Set env value
					// Expected format: $set env-var-name=value
					String expr = Util.jsSubstring(stmtText, 5);
					String parts[] = Util.split(expr, "=");
					if (parts.length != 2) {
						throw new SyntaxException("Set statement invalid expression syntax in '"+stmtText+"' . Line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					}
					envMap.put(parts[0].trim().toLowerCase(), parts[1].trim()); // Store (or override) in env map
					omitOutput = true; // Do not output the $set statement
				}
				//-----------------------------------------
				// Output the current line
				//-----------------------------------------
				
				// If this line ends a block comment, output the comment ender and then process (output)
				// the rest of this line normally.
				if (stmt.isBlockCommentEnd()) {
					blockCommentStartStmt = null; // Leaving multi-line comment block
					Stmt cmtEnd = new Stmt(stmt.getComment(), lineNum, inFile.getName(), false);
					cmtEnd.setIgnore(true);
					stmt.removeComment(); // Original comment starter is now a duplicate, so remove it
					writer.add(cmtEnd); // Emit block comment end as an ignored line
				}
				
				// Output the current line unless it is to be omitted
				if (!omitOutput) writer.add(stmt);
				
				// If this line began a block comment, process and output the non-comment part normally, 
				// then output the comment starter, and skip all future lines until we find the end-block marker.
				if (stmt.isBlockCommentStart()) {
					blockCommentStartStmt = stmt; // Note we are in a multi-line block comment section
					Stmt cmtStart = new Stmt(stmt.getComment(), lineNum, inFile.getName(), false);
					cmtStart.setIgnore(true);
					stmt.removeComment(); // Original comment ender is now a duplicate, so remove it
					writer.add(cmtStart); // Emit block comment start as an ignored line
				}
			}
			
			if (inDefine) {
				throw new SyntaxException("Unterminated macro definition, missing $endmacro.");
			}
			
			SourceContext.endFile();
		}
		catch (Throwable t) {
			System.out.println("Error at line "+lineNum+" in '"+inFile.getAbsolutePath()+"': "+t.getMessage());
			throw t;
		}
		
		
	}

	public static void main(String[] args) {

		// First 2 args are required
		if (args.length < 2) {
			System.err.println("No input and output files specified");
			System.exit(1);
		}

		srcFile= new File(args[0]);
		outFile= new File(args[1]);
		
		for (int i=2; i<args.length; i++) {
			if (args[i].startsWith("-E")) { // Env variable for $if
				String v = Util.jsSubstring(args[i], 2);
				if (v.length()==0) continue; // Skip empty -E arg
				String vs[] = Util.split(v, "=");
				if (vs.length != 2) {
					System.err.println("Invalid -E cmd arg, expecting '-Ename=true|false'");
					System.exit(1);
				}
				if (!vs[1].equals("true") && !vs[1].equals("false")) {
					System.err.println("Invalid -E cmd arg, value must be true or false");
					System.exit(1);
				}
				envMap.put(vs[0].toLowerCase(), vs[1]);
			}
			else if (args[i].startsWith("-D")) { // Debug output level
				verbose = Util.jsSubstring(args[i], 2);
			}
		}
		
		if (!srcFile.exists()) {
			System.out.println("Input file '"+srcFile.getAbsolutePath()+"' not found.");
			System.exit(1);
		}
		sourceDir = srcFile.getParentFile();
		
		List<Stmt> newSource = new ArrayList<>();
		
		// Process the input file one line at a time
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
			
			// Pass 1, $include and $macro statements
			List<String> includedFiles = new ArrayList<>();
			processFilePass1(srcFile, newSource, includedFiles);
			
			// Now expand all macro invocations in the source code
			List<String> outSource = Macro.doMacroEval(newSource);
			
			for (String s: outSource) {
				// Reformat TOON (target-of-operation notation) "xxx = opcode x,y" which are FYI only and not understood by the rest of the tool chain
				s = reTOON(s);
//				String parts[] = Util.split(s, "=", 3);
//				if ((parts.length >= 3) && (parts[1].equals("="))) {
//					s = parts[2]; // Discard first two symbols, keep the rest of the line
//				}
				writer.write(s);
				writer.newLine();
			};
			
			Util.info("PreCPP Completed");
			Util.info("  Included files:     "+includedFiles.size());
			Util.info("  Macro definitions:  "+macroMap.size());
			Util.info("  Output lines:       "+newSource.size()+" ("+outFile.getAbsolutePath()+")");
			
			
//			System.out.println("---Macros---");
//			for (Macro m: macroMap.values()) {
//				System.out.print(m);
//				if (m.getArgCount()==0) {
//					System.out.println(" :: '"+m.eval(new HashMap<String,String>(), dummyStmt)+"'");
//				}
//				else {
//					System.out.println("");
//				}
//			}
			
//			// Test some expansions
//			System.out.println("");
//			
//			Map<String,String> argValues = new HashMap<String,String>();
//			List<String> expandedMacro = null;
//			
//			argValues.put("mr","mr47");
//			argValues.put("sfr", "SWITCHES");
//			List<String> expandedMacro = macroMap.get("COPY_SFR_TO_MR").eval(argValues, dummyStmt);
//			System.out.println("Expanded macro text COPY_SFR_TO_MR ("+expandedMacro.size()+" lines):");
//			for (String s: expandedMacro) {
//				System.out.println(s);
//			}
//			System.out.println("");
//
//			argValues = new HashMap<String,String>();
//			argValues.put("msec","300");
//			expandedMacro = macroMap.get("MS_TO_SAMPLES_48K").eval(argValues, dummyStmt);
//			System.out.println("Expanded macro text MS_TO_SAMPLES_48K ("+expandedMacro.size()+" lines):");
//			for (String s: expandedMacro) {
//				System.out.println(s);
//			}
//			System.out.println("");
//
//			argValues = new HashMap<String,String>();
//			argValues.put("msb","300");
//			argValues.put("b2","200");
//			argValues.put("b1","100");
//			argValues.put("lsb","0");
//			expandedMacro = macroMap.get("WORD32_BYTES").eval(argValues, dummyStmt);
//			System.out.println("Expanded macro text WORD32_BYTES ("+expandedMacro.size()+" lines):");
//			for (String s: expandedMacro) {
//				System.out.println(s);
//			}
//			System.out.println("");
//			
//			// Test evaluation
//			String e = "SWITCH3(CrValue=r0, target0=b, target1=c, target2=d)";
//			List<String> result = Macro.evalMacroInvocation(e);
//			System.out.println("Evaluation of: "+e);
//			result.forEach((s)->System.out.println(s));
//			System.out.println("");
//
//			argValues = new HashMap<String,String>();
//			argValues.put("crValue","r0");
//			argValues.put("target0","${WORD32_WORD16(msword=512, lsword=37)}");
//			argValues.put("target1","c");
//			argValues.put("target2","d");
//			expandedMacro = macroMap.get("SWITCH3").eval(argValues, dummyStmt);
//			System.out.println("Expanded macro text SWITCH3 ("+expandedMacro.size()+" lines):");
//			for (String s: expandedMacro) {
//				System.out.println(s);
//			}
//			System.out.println("");
		}
		catch (Throwable t) {
			if (t instanceof SyntaxException) {
				// Just output the error message, no stack trace
				System.out.println(t.getMessage());
			}
			else {
				// Anything else is unexpected
				System.out.println("Unexpected program error:");
				t.printStackTrace(System.out);
			}
			System.exit(1);
		}
	}
	
	// 2 operand instructions that target ACC32 'acc32 = x instr y'
	private static Set<String> Ops2Acc32InstrSet = Set.of(
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
	private static Set<String> Ops1Acc32InstrSet = Set.of(
			"inv",
			"abs",
			"neg",
			"log2",
			"exp2"
			);
	
	// 1 operand instructions that target ACC64 'acc64 = instr x'
	private static Set<String> Ops1Acc64InstrSet = Set.of(
			"ldacc64u",
			"ldacc64l");

	// 2 operand instructions that target ACC64 'acc64 = x instr y'
	private static Set<String> Ops2Acc64InstrSet = Set.of(
			"macri",
			"macrd",
			"machrr",
			"machri",
			"machrd",
			"machid"
			);

	// Copy instructions 'target = op source'
	private static Set<String> CopyInstrSet = Set.of(
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
	private static Set<String> Ops0InstrSet = Set.of(
			"rdacc64u",
			"rdacc64l",
			"sat64");
	
	// Conditional jump 'label instr x'
	private static Set<String> CondJmpInstr = Set.of(
			"jgez",
			"jneg",
			"jnz",
			"jz",
			"jzc");
	
//	private static Set<String> CrSet = Set.of(
//			"r0",
//			"r1",
//			"r2",
//			"r3",
//			"r4",
//			"r5",
//			"r6",
//			"r7",
//			"r8",
//			"r9",
//			"r10",
//			"r11",
//			"r12",
//			"r13",
//			"r14",
//			"r15",
//			"acc32",
//			"flags"
//			);
	
	private static Set<String> SrfNameSet = Set.of(
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
	private static Map<String,String> rnMap = new HashMap<>();
	
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

	private static String reTOON(String s) throws Exception {
		
		Stmt stmt = new Stmt(s, 0, ""); // Use this to remove comments (TODO: redundant with earlier processing...)
		
		String[] tokenList = Util.split(stmt.getText(), "\\p{Space}+"); // Tokenize on white space including tabs
		int tokenCnt = tokenList.length;
		
		// TOON statements may not have whitespace around the assignment operator
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
				else if (right.equals("ACC32") || right.matches("R[0-9]+") || right.equals("FLAGS")) {
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

}
