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
import java.util.List;
import java.util.Map;
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
						throw new Exception("Macro name '"+m.getName()+"' is already defined. Line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
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
					if (inIf) throw new Exception("Nested $if statements are not supported at line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					
					int endParen = stmtText.indexOf(')');
					if (endParen<0) throw new Exception("Missing closing paren of $if statement at line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
					String conditionExp = Util.jsSubstring(stmtText, 4, endParen).trim();
					String[] expParts = Util.split(conditionExp, "="); // Note if this is "!=" the ! stays with the left operand
					if (expParts.length != 2) throw new Exception("Invalid $if expression at line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
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
						throw new Exception("Invalid $include statement, no file specified");
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
							throw new Exception("Macro name '"+m.getName()+"' is already defined. Line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
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
						throw new Exception("Set statement invalid expression syntax in '"+stmtText+"' . Line "+stmt.getLineNum()+" in file '"+stmt.getFileName()+"'");
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
				throw new Exception("Unterminated macro definition, missing $endmacro.");
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
			t.printStackTrace(System.out);
			System.exit(1);
		}
	}

}
