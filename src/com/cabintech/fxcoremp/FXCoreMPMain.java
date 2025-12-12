package com.cabintech.fxcoremp;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
 * FXCore DSP assembler language macro processor.
 * 
 * Note some of this may be out of date, see the current project documentation at:
 * https://github.com/cabintech/FXCoreMP/wiki
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cabintech.toon.SyntaxException;
import com.cabintech.toon.Toon;
import com.cabintech.utils.SafeMap;
import com.cabintech.utils.Util;
import com.cabintech.utils.Util.FirstAndRemainder;

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
	 * file with all $include files embedded, and all $macro definitions removed. Also processes
	 * $setenv and $ifenv conditional code inclusion.
	 * @param inFile
	 * @param writer
	 * @param includedFiles
	 * @throws Throwable
	 */
	private static void processFilePass1(File inFile, List<Stmt> writer, List<String> includedFiles) throws Throwable {
		
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
//			Stmt multilineStartStmt = null;    // Statement that started a block comment
			List<Stmt> macroLines = new ArrayList<Stmt>();
			List<Stmt> multiLines = new ArrayList<Stmt>();
			for (String inLine: srcLines) {
				boolean omitOutput = false; // Do not write the current statement to the output stream
				lineNum++;
				SourceContext.atLine(lineNum);
				Stmt stmt = new Stmt(inLine, lineNum, inFile.getName());
				
				// Parse out the first word (and remainder) with tabs converted to blanks
				FirstAndRemainder parsed = Util.getFirstAndRemainder(stmt.getText().replaceAll("\t"," "));
				
				// If we are in a multiline comment, just output it and skip all processing. This takes
				// precedence over all other source code processing.
				
				if (blockCommentStartStmt != null) {
					// This line is part of a block comment, ignore it for processing purposes.
					// Do nothing, just output as usual below.
					stmt.setIgnore(true); 
				}
//				
//				else if (multilineStartStmt != null) {
//					multilineStartStmt.append(stmt.getText());
//					stmt.setIgnore(true);
//				}
				
				//-------------------------------------------------------------------
				// IF processing, precludes all other except block comments
				//-------------------------------------------------------------------
				
				else if (parsed.firstWord().equals("$endenv") || parsed.firstWord().equals("$endif")) { // '$endif' is legacy, keeping for backward compatibility
					inIf = false;
					omitOutput = true; // Nothing to output
				}
				
				else if (inIf && ifCondition==false) {
					omitOutput = true; // Skip lines in FALSE $if block
				}
				
				else if (parsed.firstWord().equals("$ifenv") || parsed.firstWord().equals("$if")) { // '$if' is legacy, keeping for backward compatibility
					// Condition section '$ifenv envname=xxx'
					if (inIf) throw new SyntaxException("Nested $ifenv statements are not supported.", stmt);
					
					// Remove parens (legacy syntax) if present
					String conditionExp = parsed.remainder();
					if (conditionExp.startsWith("(") && conditionExp.endsWith(")")) {
						conditionExp = Util.jsSubstring(conditionExp, 1, conditionExp.length()-1);
					}
					
					String[] expParts = Util.split(conditionExp, "="); // Note if this is "!=" the ! stays with the left operand
					if (expParts.length != 2) throw new SyntaxException("Invalid $ifenv expression.", stmt);
					boolean operator = true;
					if (expParts[0].endsWith("!")) {
						operator = false;
						expParts[0] = Util.jsSubstring(expParts[0], 0, expParts[0].length()-1); // Trim off the "!" from left operand
					}
					
					inIf = true;
					ifCondition = envMap.getStr(expParts[0].toLowerCase().trim()).equalsIgnoreCase(expParts[1].trim()); // TRUE if expression matches env setting
					if (!operator) {
						ifCondition = !ifCondition; // Invert for "!=" operator
					}
					omitOutput = true; // Do not output the $if statement itself
				}
				
				//-----------------------------------------
				// $include
				//-----------------------------------------
				
				else if (parsed.firstWord().equals("$include")) {
					String incFileName = parsed.remainder().replace("\"", "");
					if (incFileName.length() == 0) {
						throw new SyntaxException("Invalid $include statement, no file specified.", stmt);
					}
					
					if (!includedFiles.contains(incFileName)) { // Only include a file once
						// Recursive call to process the #include'd file
						includedFiles.add(incFileName);
						File incFile = new File(sourceDir, incFileName);
						writer.add(new Stmt(";--- BEGIN INCLUDE: "+incFileName, stmt.getLineNum(), stmt.getFileName()));
						processFilePass1(incFile, writer, includedFiles);
						writer.add(new Stmt(";--- END INCLUDE: "+incFileName, stmt.getLineNum(), stmt.getFileName()));
					}
					omitOutput = true; // Nothing to write for this input line, just continue with next line
				}
				
				//-------------------------------------------------------------------
				// Macro definition processing
				//-------------------------------------------------------------------
				
				else if (inDefine && parsed.firstWord().equals("$endmacro")) {
					Macro m = new Macro(macroLines);
					String macroName = m.getName();
					if (macroMap.containsKey(macroName)) {
						throw new SyntaxException("Macro name '"+m.getName()+"' is already defined.", stmt);
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
				
				else if (stmt.isContinued()) {
					multiLines.add(stmt);
					omitOutput = true; // Nothing to output until end of multiline
				}
				
				else if (parsed.firstWord().equals("$macro")) { // Start of macro definition
					if (parsed.remainder().endsWith("++")) { 
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
							throw new SyntaxException("Macro name '"+m.getName()+"' is already defined.", stmt);
						}
						macroMap.put(macroName, m);
						omitOutput = true; // Do not output macro definition lines
					}
				}
				
				//-----------------------------------------
				// $set
				//-----------------------------------------
				else if (parsed.firstWord().equals("$setenv") || parsed.firstWord().equals("$set")) { // '$set' is legacy, keeping for backward compatibility
					// Expected format: $set env-var-name=value
					String parts[] = Util.split(parsed.remainder(), "=");
					if (parts.length != 2) {
						throw new SyntaxException("$setenv statement invalid expression syntax", stmt);
					}
					envMap.put(parts[0].trim().toLowerCase(), parts[1].trim()); // Store (or override) in env map
					omitOutput = true; // Do not output the $set statement
				}
				//-----------------------------------------
				// Output the current line
				//-----------------------------------------
				if (!stmt.isContinued() && multiLines.size() > 0) {
					// end of continued (multiline) stmt
					multiLines.add(stmt);
					String mergedText = "";
					for (Stmt s: multiLines) {
						mergedText = mergedText + s.getText() + " ";
					}
					
					// Update the first line of the continued set with the merged statement text
					Stmt line1 = multiLines.get(0);
					line1.replaceText(mergedText);
					
					// Replace the current statement with the merged (line1)
					stmt = line1;
					
					// Reset multiline buffer
					multiLines.clear();
				}
				
				
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
				throw new SyntaxException("Unterminated macro definition, missing $endmacro in file "+ inFile.getAbsolutePath());
			}
			
			SourceContext.endFile();
		}
		catch (Throwable t) {
			System.out.println("Error at line "+lineNum+" in '"+inFile.getAbsolutePath()+"': "+t.getMessage());
			throw t;
		}
		
		
	}

	public static void main(String[] args) {
		
		boolean doAnnotation = false;
		boolean doToon = true;
		boolean doMacro = true;
		boolean toonModeNormal = true;
		
		List<String> argsList = new ArrayList<>(Arrays.asList(args));
		for (int i=0; i<argsList.size(); i++) {
			String arg = argsList.get(i);
			if (arg.equalsIgnoreCase("--annotate")) {
				doAnnotation = false;
				argsList.remove(i--);
				continue;
			}
			
			if (arg.equalsIgnoreCase("--notoon")) { // Run TOON processor
				doToon = false;
				argsList.remove(i--);
				continue;
			}
			
			if (arg.equalsIgnoreCase("--nomacro")) { // Run MACRO processor
				doMacro = false;
				argsList.remove(i--);
				continue;
			}
			
			if (arg.equalsIgnoreCase("--reversetoon")) { // Run asm2toon (no macros)
				toonModeNormal = false;
				argsList.remove(i--);
				continue;
			}
			
			if (arg.startsWith("-E")) { // Env variable for $if
				String v = Util.jsSubstring(arg, 2);
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
				argsList.remove(i--);
				continue;
			}
			
			if (arg.toLowerCase().startsWith("--debug")) { // Debug output level
				String[] parts = Util.split(arg, "=");
				if (parts.length < 2) {
					verbose = "debug";
				} else {
					verbose = parts[1].toLowerCase();
				}
				argsList.remove(i--);
				continue;
			}
			
			if (arg.trim().length()==0) { // Batch files can pass empty args
				argsList.remove(i--);
				continue;
			}
			
		}


		// First 2 args are required
		if (argsList.size() < 2) {
			System.err.println("No input and output files specified");
			System.exit(1);
		}
		
		// Cannot run TOON in reverse and macro
		if (doMacro && doToon && !toonModeNormal) {
			System.err.println("Cannot run macros and reverse TOON.");
			System.exit(1);
		}

		srcFile= new File(argsList.get(0));
		outFile= new File(argsList.get(1));
		
		if (!srcFile.exists()) {
			System.out.println("Input file '"+srcFile.getAbsolutePath()+"' not found.");
			System.exit(1);
		}
		sourceDir = srcFile.getParentFile();
		
		List<Stmt> newSource = new ArrayList<>();
		int syntaxErrors = 0; // Toon syntax errors
		
		Toon tooner = new Toon(doAnnotation); // Create an instance of the TOON translator
		
		try { // Catch any unexpected errors
		
			//---------------------------------------------------------------
			// Macro processing
			//---------------------------------------------------------------
			
			List<String> outSource = new ArrayList<>();;
			List<String> includedFiles = new ArrayList<>();
			try {
				if (doMacro) {
					// Pass 1, $include and $macro statements
					processFilePass1(srcFile, newSource, includedFiles);
					
					// Pass 2, now expand all macro invocations in the source code
					outSource = Macro.doMacroEval(newSource);
				}
				else {
					// No macro step, read raw source for next operation
					outSource = Files.readAllLines(srcFile.toPath());
				}
			}
			catch (SyntaxException se) {
				// Macro processing halts on first error
				syntaxErrors++;
				System.out.println("Macro processing error:");
				System.out.println("  "+se.getMessage());
				System.out.println("  "+se.getStmtMessage());
				doToon = false; // Force skip of TOON processing
			}
				
			//---------------------------------------------------------------
			// TOON processing
			//---------------------------------------------------------------
			
			int pc = 0; // Program (instruction) counter
			if (doToon) {
				int lineCnt = 0;
				List<String> toonOutput = new ArrayList<>();
				boolean inBlockComment = false;
				for (String s: outSource) {
					lineCnt++;
					// Translate TOON (target-of-operation notation) which is not understood by the rest of the tool chain. We
					// only xlate TOON-->Asm when doing macro expansion (never Asm-->TOON).
					try {
						if (toonModeNormal) {
							// Create a Stmt so any errors can have context (e.g. line number, etc)
							Stmt stmt = new Stmt(s, lineCnt, srcFile.getAbsolutePath());
							if (stmt.isBlockCommentEnd()) {
								inBlockComment = false;
							}
							s = inBlockComment ? "; "+s : tooner.toonToAsm(stmt);
							if (stmt.isBlockCommentStart()) {
								inBlockComment = true;
							}
							if (!inBlockComment) {
								//TODO: Improve this ad-hoc way of counting instructions. The Stmt object is probably a better place to
								// know if the statement generates 0, 1, or more machine instructions.
								String t = stmt.getText();
								if ((t.length() > 0) && !t.startsWith(".") && !t.endsWith(":") && !t.equalsIgnoreCase("endif")) { // Count non-empty, non-directive, non-label-only lines
									//System.out.println("PC "+pc+": "+t);
									// Number of physical lines is presumed to be number of FXCore instructions
									// Currently only the 'allpass' TOON statement produces > 1 instruction
									pc = pc + 1 + (int)s.chars().filter(ch -> ch=='\n').count();
								}
							}
						} else {
							s = tooner.asmToToon(s);
						}
					}
					catch (SyntaxException se) {
						syntaxErrors++;
						System.out.println("TOON processing error:");
						System.out.println("  "+se.getMessage());
						System.out.println("  "+se.getStmtMessage());
						// Continue processing the next line
					}
					toonOutput.add(s);
				};
				outSource = toonOutput; // Replace with the TOON results
			}
			
			// Write final results to output file
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
				for (String out : outSource) {
					writer.write(out);
					writer.newLine();
				}
			}
			
			if (Toon.ifStmtStack.size() > 0) { // Unclosed IF statement
				Stmt stmt = Toon.ifStmtStack.getLast().startedAt();
				System.out.println("ERROR: Missing ENDIF to IF statement started at "+stmt.getLineNum()+" in "+stmt.getFileName());
				System.exit(1); // Stop assembly process
			}
			
			// Output warning if using >80% of the instruction storage //TODO: Make this trigger point a program arg '-warnCodePercent=80'
			if (pc>1024) System.out.println("ERROR: Too many instructions, using "+pc+" of 1024 available instructions.");
			else if (pc>819) System.out.println("NOTE: Using "+pc+" of 1024 available instructions ("+(int)((pc/1024.0)*100)+"%).");
			
			Util.info("FXCoreMP processing completed ("+(doMacro?"macros=yes":"macros=no")+", "+(doToon?"toon=yes":"toon=no")+(toonModeNormal?" [TOON-->ASM]":" [ASM-->TOON]")+")");
			Util.info("  FXCore instructions : "+pc+" used of 1024 available");
			Util.info("  Auto assigned MRs   : "+ (Macro.counterMap.containsKey("nextmr") ? (Macro.counterMap.get("nextmr").intValue())+" used of 128 available" : "None"));
			Util.info("  Errors              : "+syntaxErrors);
			Util.info("  Included files      : "+includedFiles.size());
			Util.info("  Macro definitions   : "+macroMap.size()); // +macroMap.keySet().toString());
			Util.info("  Output lines        : "+outSource.size()+" ("+outFile.getAbsolutePath()+")");
			
			// Output warning if using >80% of the instruction storage //TODO: Make this trigger point a program arg '-warnCodePercent=80'
			if (pc>1024) System.out.println("\nERROR: Too many instructions, using "+pc+" of 1024 available instructions.");
			else if (pc>819) System.out.println("\nNOTE: Using "+pc+" of 1024 available instructions ("+(int)((pc/1024.0)*100)+"%).");
			
			if (pc>1024) System.exit(1); // Stop assembly process
			
		}
		catch (Throwable t) {
			// Unexpected
			System.out.println("Unexpected program error:");
			t.printStackTrace(System.out);
			System.exit(3);
		}
		
		if (syntaxErrors > 0) {
			System.exit(2);
		}
		
		System.exit(0);
	}

}
