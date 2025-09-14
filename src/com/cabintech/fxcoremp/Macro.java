package com.cabintech.fxcoremp;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
 * TODO: BUG: This source line:
 * $_log(after: $_count(nextmr,get,))
 * fools the processor which sees it as a label, not a macro invocation.
 * 
 * TODO: BUG: Comments in multiline macro definitions attempt substitution, e.g.
 * $macro XXX(abd, def) ++
 * ; This causes an error but should not, this comment should be ignored: ${NoSuchAArg}
 * $endmacro
 * 
 * TODO: For counters, $_count(mycounter,add,xxx) allow xxx to be an expression, not just a numeric literal, e.g.
 * .equ V 12
 * $_count(mycounter,add,V-1)
 * 
 * e.g. implement as of $_count(mycounter,add,$_eval(xxx))
 *
 * TODO: Idea 1 -------------------------------------------------------------------------
 * Support separation of macro inputs and outputs into an assignment, e.g.
 * $macro ABC(in1<=, in2<=, out1=>, out2=>) ++
 *  ...
 * $endmacro
 * 
 * Support this syntax:
 * (out1, out2) = $ABC(in1, in2)
 * 
 * this looks like a mutil-return value function call. Might need to constrain macro definition
 *  - must use in/out designations (maybe this becomes reqmt on all macro definitions)
 *  - maybe require grouping ins and outs
 * 
 * syntax needs to be distinct enough to be easily recognized
 * 
 * this is more high-level language like
 * 
 * note this is just syntatic sugar, still inputs and outputs are declared but not verified
 * 
 * TODO: Idea 2 ---------------------------------------------------------------------------
 * Remove requirement for "++" on multiline macro definitions. Problem is line-by-line gathering
 * uses them to gather all the lines of a macro which are passed as a list to Macro ctor. So
 * higher level code needs to properly distinguish:
 * 
 * TODO: Idea 3 ----------------------------------------------------------------------------
 * Allow macro args to be omitted without using extra ",". So a simple macro with two args
 * $macro XYZ(a, b) ...
 * could be invoked with 2, 1, or zero args. Of course the macro has to be able to handle that.
 * Macro defn would have to explicity allow this, something like
 * $macro XYZ:varargs(a, b)
 * 
 * Multiline macros:
 * 
$macro ABC
line 1
line 2
$endmacro

$macro ABC()
line 1
line 2
$endmacro

$macro ABC(x)
line 1
line 2
$endmacro
 * 
 * and single line macros:
 * 
$macro ABC text1

$macro ABC (text1)

$macro ABC(x) text1((${x}))
 * 
 * TODO: Idea 3 ---------------------------------------------------------------------------
 * Allow multiline macro invocations (or any assembler code) for better readability, e.g. instead of:
 * 
 * $CALC_DIV_TT(rawTTMR<=tapTimeRaw, divTableBase<=DIV_BASE_1, divTT=>tapTime, divIndex=>tapDivIndex)
 * 
 * write:
 * 
 * $CALC_DIV_TT( +
 * 		rawTTMR<=tapTimeRaw, + 
 * 		divTableBase<=DIV_BASE_1, + 
 * 		divTT=>tapTime, +
 * 		divIndex=>tapDivIndex)
 * 
 * Maybe allow this in basic assembler statements? Not sure it is useful for that.
 * Note need to distinguish "++" used to indicate multi-line macros
 * 
 * 
 * -----------------------------------------------------------------------------------------------------
 * 
 * This class represents a macro definition create with the $macro statement.
 * 
 * Syntax 1 (simple): $macro macro-name substitution-text
 * This form of a macro does a simple literal text substitution into the source.
 * 
 * Example:
 * $macro PI 3.14
 * 
 * Syntax 2 (args): $macro macro-name(argName1, argName2, ...) substitution-text
 * This form of a macro does simple substitution after replacing the arguments with the supply
 * invocation values. Argument substitution is not nested or recursive, e.g. if an argument
 * value evaluates to "${...}" then the result is not substituted again. However it can evaluate
 * to a macro invocation "$xxx()" which will be evaluated.
 * 
 * Example:
 * $macro MS_TO_SAMPLES_48K(msec) ((${msec}/1000)/(1/48000))
 * 
 * Syntax 3 (multi-line macro) syntax:
 * $macro macro-name(optional-args) ++
 * substitution line 1
 * substitution line 2
 * ...
 * substitution line n
 * $endmacro
 * 
 * A multiline macro starts with a macro name and the (optional) argument name list, followed
 * by the "++" continuation symbol that signals a multi-line macro. The "++" must be the last
 * non-comment text on the line and comes before any substitution text. All the following lines
 * become the macro substitution text until a line starting with "$endmacro" is found. (The
 * "++" is necessary to distinguish a multi-line macro from a macro that expands to an empty value.)
 * TODO: Can we ignore that corner case and drop the need for the "++"??
 * 
 * $macro SWITCH3(crValue, target0, target1, target2) ++
 * jz			${crValue}, ${target0}
 * cpy_cc		acc32, ${crValue}		
 * addi			acc32, -1			
 * jz			acc32, ${target1}
 * jmp			${target2}
 * $endmacro
 * 
 * Note the argument evaluation syntax "${argname}" is different than the syntax for evaluating
 * a macro "$macroname()"
 *    
 * Macro definitions ($macro statements) and $include statements are not allowed in a macro definition. The
 * definition may include substitution (evaluation) of other macros and of the macro argument values supplied
 * for the evaluation. Since the $macroname() invocation syntax is different than the ${argname} syntax, a macro
 * can have an arg name the the same as a macro used in the definition.
 * 
 * TODO: Could allow $include in a macro defn, but requires handling in the macro expander.
 */


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cabintech.toon.SyntaxException;
import com.cabintech.utils.Util;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;

public class Macro implements Constants {
	
	private String macroName = null;							// Macro name
	private List<MacroParm> argNames = new ArrayList<>();				// List of argument name/direction
	private List<Stmt> macroLines = new ArrayList<Stmt>();		// List of lines (one or more)
	static private Map<String, Object> equMap = new HashMap<>();		// Map of symbols and expressions created by .equ statements
	static Map<String, Number> counterMap = new HashMap<>();	// Map of counter names to current values
	private String sourceFile = null;
	private static ExpressionConfiguration exprConfig = ExpressionConfiguration.builder().decimalPlacesRounding(12).build();
	
	private static int lastUnique = 0; // Last used ${:unique} macro-scope virtual arg value
	
	/**
	 * Creates a Macro from a set of source statements, the first of which is the $macro statement.
	 * @param defStmts
	 */
	public Macro(List<Stmt> defStmts) throws Exception {
		// The first line of the definition must contain the macro name and any arg names
		Stmt stmt1 = defStmts.get(0);
		sourceFile = stmt1.getFileName();
		String line1 = stmt1.getText();
		line1 = Util.jsSubstring(line1, 7).trim(); // Remove "$macro "
		if (line1.endsWith("++")) {
			line1 = Util.jsSubstring(line1, 0, line1.length()-3).trim(); // Remove multi-line continuation marker
		}
		line1 = line1.replaceAll("\t"," ").trim();
		
		// Find end of macro name
		String padded = line1 + " ";
		int afterName=0;
		while (Character.isJavaIdentifierPart(padded.charAt(afterName))) {
			afterName++;
		}
		
		// Find open paren of arg list
		//int blank = line1.indexOf(' ');
		//int paren1 = line1.indexOf('(');
		//int paren2 = line1.indexOf(')');
		//if (paren1 < 0) {
		if (padded.charAt(afterName) != '(') {
			// No opening paren, presume no args and remaining text is macro text
			int i = line1.indexOf(' ');
			if (i < 0) {
				// No spaces, so entire line is the macro name and no args
				macroName = line1.trim();
			}
			else {
				// Macro name followed by macro text
				macroName = Util.jsSubstring(line1, 0, i).trim();
				macroLines.add(new Stmt(Util.jsSubstring(line1,i+1).trim(), stmt1.getLineNum(), stmt1.getFileName()));
			}
		}
		else {
			// Macro has arg list
			int paren1 = line1.indexOf('(');
			int paren2 = line1.indexOf(')');
			if (paren2 < 1 || paren1 > paren2) {
				throw new SyntaxException("Invalid macro definition, missing or invalid argument list.", stmt1);
			}
			macroName = Util.jsSubstring(line1, 0, paren1).trim(); // Up to first paren is macro name
			if (paren2 > paren1+1) { 
				// Non-empty arg list
				String argNameList = Util.jsSubstring(line1, paren1+1, paren2).trim();
				if (argNameList.length() > 0) {
					// Extract comma separated list of arg names
					String names[] = argNameList.split(",");
					for (String name: names) {
						// Check if arg name has direction indicator (prefix or postfix is allowed)
						name = name.trim();
						int dir = DIR_ANY;
						if (name.endsWith(DIR_INOUT_TEXT) || name.startsWith(DIR_INOUT_TEXT)) {
							dir = DIR_INOUT;
							if (name.endsWith(DIR_INOUT_TEXT)) {
								name = Util.jsSubstring(name, 0, name.length()-3);
							}
							else {
								name = Util.jsSubstring(name, 3);
							}
						}
						else if (name.endsWith(DIR_IN_TEXT) || name.startsWith(DIR_IN_TEXT)) {
							dir = DIR_IN;
							if (name.endsWith(DIR_IN_TEXT)) {
								name = Util.jsSubstring(name, 0, name.length()-2);
							}
							else {
								name = Util.jsSubstring(name, 2);
							}
						}
						else if (name.endsWith(DIR_OUT_TEXT) || name.startsWith(DIR_OUT_TEXT)) {
							dir = DIR_OUT;
							if (name.endsWith(DIR_OUT_TEXT)) {
								name = Util.jsSubstring(name, 0, name.length()-2);
							}
							else {
								name = Util.jsSubstring(name, 2);
							}
						}
						//System.out.println("Macro defn: "+name+" direction "+dir);
						argNames.add(new MacroParm(name.trim(), dir));
					}
				}
			}
			// If there is non-blank text after the closing parent, it is the first line of macro text
			// Any "++" continuation symbol has already been removed
			if (line1.length() > paren2+1) {
				macroLines.add(new Stmt(Util.jsSubstring(line1, paren2+1), stmt1.getLineNum(), stmt1.getFileName()));
			}
		}
		
		// Enforce macro names so we can reliably parse them in source lines
		if (macroName.contains("$")) 
			throw new SyntaxException("Macro name cannot contain '$' symbol.", stmt1);
		for (int i=0; i<macroName.length(); i++) {
			char c = macroName.charAt(i);
			if (!Character.isJavaIdentifierPart(c)) throw new SyntaxException("Macro name contains invalid character '"+c+"'.", stmt1);
		}
		
		
		// Any additional statements are just macro text, do some sanity checking
		// now rather than at eval() time.
		for (int i=1; i<defStmts.size(); i++) {
			Stmt s = defStmts.get(i);
			String line = s.getText();
			if (line.startsWith("$macro ")) {
				throw new SyntaxException("Nested macro definition not allowed (maybe missing $endmacro before this?)", s);
			}
			if (line.startsWith("$include ")) {
				throw new SyntaxException("Include not allowed in macro definition.", s);
			}
			macroLines.add(s);
		}
			
	}
	
	@Override
	public String toString() {
		return "Macro '"+macroName+"', "+macroLines.size()+" lines, args "+argNames.toString();
	}
	
	/**
	 * Returns an unmodifiable list of argument names exactly as given on the $macro statement
	 * @return
	 */
	public List<MacroParm> getArgNames() {
		return Collections.unmodifiableList(argNames); 
	}

	/**
	 * Returns the number of args for this macro as defined by the $macro statement.
	 * @return
	 */
	public int getArgCount() {
		return argNames.size();
	}
	
	public String getName() {
		return macroName;
	}
	
	/**
	 * Evaluates the macro and returns it's content with substitution of the given args and
	 * evaluating any macros this macro contains.
	 * @param argValues
	 * @param context
	 * @return
	 */
	public List<String> eval(Map<String,MacroParm> argValues, Stmt context) throws Exception {
		
		// Use case-insensitive map for arg names
		Map<String, MacroParm> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		treeMap.putAll(argValues);

		// All args must be supplied and match definition arg names
		if (treeMap.size() != argNames.size()) {
			throw new SyntaxException("Invocation of macro '"+macroName+"' has incorrect number of args.", context);
		}
		for (MacroParm argName: argNames) {
			MacroParm argValue = treeMap.get(argName.getString()); 
			if (argValue == null) {
				throw new SyntaxException("Invocation of macro '"+macroName+"' missing argument named '"+argName.getString()+"'.", context);
			}
			//v1.1 Args must also match direction (positional args are DIR_ANY and match any direction)
			if (argName.getDirection() != argValue.getDirection()) {
				if (argName.getDirection()!=DIR_ANY && argValue.getDirection()!=DIR_ANY) {
					throw new SyntaxException("IN/OUT direction mismatch on argument '"+argName.getString()+"' of macro '"+macroName+"'.", context);
				}
			}
		}
		
		// Add virtual args
		lastUnique++; // Unique ID at the macro-invocation scope
		treeMap.put(":unique", new MacroParm(lastUnique+"", DIR_ANY));
		treeMap.put(":sourcefile", new MacroParm(sourceFile, DIR_ANY));
		treeMap.put(":sourcefile_root", new MacroParm(FXCoreMPMain.srcFile.getName(), DIR_ANY));
		treeMap.put(":outputfile", new MacroParm(FXCoreMPMain.outFile.getName(), DIR_ANY));
		
		// Do argument substitution on each line of the macro defn
		List<Stmt> genCode = new ArrayList<Stmt>();
		for (Stmt s: macroLines) {
			//TODO: Should only do substitution in statement text, not comments, but using s.getText() here
			// breaks multiline invocations inside a macro definition (WHY?). As is, if a ${...} is in a comment
			// it tries to do the substitution and if it fails flags a syntax error (which is wrong).
			String substText = doArgSubst(s.getFullText(), treeMap, s); 
			Stmt substStmt = new Stmt(substText, s.getLineNum(), s.getFileName());
			genCode.add(substStmt);
		}
		
		// Expand macros (possibly multiple lines)
		return doMacroEval(genCode);
	}
	
	/**
	 * Do macro argument substitution of the form ${arg-name}. There is no nested
	 * substitution, this is simple replacement of the macro with the value of a 
	 * macro argument. This is a single pass substitution, the result of an argument
	 * substitution cannot be another argument substitution (but it may be a macro
	 * invocation, which will be evaluated later).
	 * 
	 * @param text
	 * @param argMap
	 * @return
	 * @throws Exception
	 */
	private String doArgSubst(String text, Map<String,MacroParm> argMap, Stmt stmt) throws Exception {
		
		// Crude, but since there are only a few args this is ok.
		//TODO: Does not handle whitespace like "${ myargname }"
		//TODO: This is case sensitive
		for (MacroParm argName: argNames) {
			String name = argName.getString();
			text = Util.replaceAll(text, "${"+name+"}", argMap.get(name).getString());
		}
		
		// Virtual args (pre-defined substitutions)
		text = text.replace("${:unique}", argMap.get(":unique").getString());
		text = text.replace("${:sourcefile}", argMap.get(":sourcefile").getString());
		text = text.replace("${:sourcefile_root}", argMap.get(":sourcefile_root").getString());
		text = text.replace("${:outputfile}", argMap.get(":outputfile").getString());
		
		// If there are any unresolved macro args then it is an error
		int i = text.indexOf("${");
		if (i >= 0) {
			String argName = "<unknown>";
			int j = text.indexOf("}", i);
			if (j > i) {
				argName = Util.jsSubstring(text, i+2, j);
			}
			throw new SyntaxException("Macro argument named '"+argName+"' is used but does not appear in the macro argument list.", stmt);
		}
		
		return text;
	}
		
	/**
	 * Expand all macro invocations in the source lines, returns expanded code. All macro
	 * definitions have been removed from the source and included files have been expanded
	 * in-place.
	 * 
	 * @param expanded
	 * @return
	 * @throws Exception
	 */
	public static List<String> doMacroEval(List<Stmt> sourceLines) throws Exception {
		
		List<String> expanded = new ArrayList<>(); // Expanded macro lines output of this method
		List<Stmt> multiLines = new ArrayList<Stmt>();

		for (Stmt stmt: sourceLines) {
			
			if (stmt.isIgnore()) {
				// Do not process this line (part of a block comment), just copy it to the output
				expanded.add(stmt.getFullText());
				continue;
			}
			
			if (stmt.isContinued()) {
				multiLines.add(stmt);
				continue;
			}
			
			if (!stmt.isContinued() && multiLines.size() > 0) {
				// List line of multiline, collapse them all into one
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
			
			// Macro form: $mac-name<white-space> or $mac-name() or $mac-name(args)
			
			String text = stmt.getText(); // Get trimmed text with comments removed
			text = text + " "; // Insure line ends in white space to simplify indexing
			
			int start = text.lastIndexOf('$'); // Right-to-left scanning will insure we process nested macros inside-out
//			if (start < 0) {
//				// No macros to expand, copy full statement to output and continue with next stmt
//				expanded.add(stmt.getFullText());
//				continue;
//			}
			
			boolean expansionOccured = false;
			while (start >= 0) {
				// We expect the macro name is next, it ends at first non-identifier char
				String macroName = "";
				char c = ' ';
				int nameEnd = start+1;
				expansionOccured = true; // At least one expansion has been done
				
				// Build up macro name until non-valid identifier char. Since we added a blank to
				// the end we will always find a non-valid char before the end of the text.
				
				for (; nameEnd<text.length(); nameEnd++) { 
					c = text.charAt(nameEnd);
					if (Character.isJavaIdentifierPart(c) && c!='$') { // '$' is never in a macro name
						macroName = macroName + c;
						continue; // Keep moving over the macro name
					}
					else {
						break; // Stop at first non-identifier char
					}
				}
				if (macroName.length()<1) {
					throw new SyntaxException("Missing or invalid macro name in '"+text+"'.", stmt);
				}
				
				// c is the first char after the macro name
				
				int end = 0; // Index of end of the macro invocation text
				String args[] = new String[0];
				String rawArgs = "";
				if (c == '(') {
					// The macro invocation has an arg list. Since we are evaluating macros inside-out, the arg
					// list has no macros in it, just literal text.
					rawArgs = extractArgList(text, nameEnd);
					if (rawArgs == null) throw new SyntaxException("Invalid macro argument list, missing closing paren: '"+text+"'.", stmt);
					args = Util.split(rawArgs, ",");
					end = nameEnd + rawArgs.length() + 2; // start + name + args + parens + $ char
				}
				else {
					// No-arg invocation
					end = nameEnd; // End is right after the macro name
				}
				
				// Expand the macro
				List<String> macExpanded = Macro.evalMacroInvocation(macroName, args, rawArgs, stmt);
				
				// The first line of expansion replaces the macro invocation in the current line. Any additional lines are added immediately following
				if (macExpanded.size() == 0) {
					text = Util.jsSubstring(text, 0, start) + Util.jsSubstring(text, end); // Macro expanded to nothing
					if (stmt.getComment().length() > 0) text = text + " " + stmt.getComment();
				}
				else if (macExpanded.size() == 1) {
					text = Util.jsSubstring(text, 0, start) + macExpanded.get(0) + Util.jsSubstring(text, end); // Single string result replaced macro invocation
					//if (stmt.getComment().length() > 0) text = text + " " + stmt.getComment();
				}
				else {
					// Any multi-line expansion replaces the source line without any farther nested expansion
					text = ""; // Current line is replaced
					expanded.add(";--- BEGIN MACRO: "+macroName+" "+stmt.getComment());
					expanded.addAll(macExpanded);
					expanded.add(";--- END MACRO: "+macroName);
				}

				// Scan (leftward) for more macro invocations
				start = text.lastIndexOf('$');

			}
			
			// After macros have been expanded, keep track of assembler .equ statements that give symbolic names to expressions
			// so those symbolic names can be used in $_eval() expressions.
			String[] tokenList = Util.split(text, "\\p{Space}+", 3); // Tokenize on white space including tabs
			if (tokenList.length == 3 && tokenList[0].equalsIgnoreCase(".equ")) {
				// Syntax: .equ symbolic-name expression
				// Try to evaluate the expression now so it can be used in subsequent expressions since
				// our $_eval() function does not operate recursively. If we cannot evaluate it (FXCore assembler
				// EQU expressions may not match the capability of our expression evaluator), then just store
				// it as the raw string and hope it is not used in an $_eval() expression. 
				String symbol = tokenList[1].toUpperCase();
				String exprStr = tokenList[2].toUpperCase();
				try {
					Object value = new Expression(exprStr, exprConfig).withValues(equMap).evaluate().getValue();
					equMap.put(symbol, value);
				}
				catch (Throwable t) {
					// If for any reason the expression cannot be evaluated, just store it as its string representation
					equMap.put(symbol, exprStr);
				}
			}

			if (!expansionOccured) {
				expanded.add(stmt.getFullText()); // Did nothing here, copy full text to output
			}
			else {
				expanded.add(text.trim()); // Output expanded line
			}
		}

		return expanded;
	}
	
	/**
	 * Extract the arg list from "macroname(arg1, arg2)" where 'from' is the
	 * index of the opening paren. Although there are no nested macro invocations
	 * in the args, there could be parens as part of literal arguments.e.g.
	 * 
	 * $ADD(27, (8+4))
	 *  
	 * Any parens used in arg values me must be balanced or we cannot reliably 
	 * find the end of the arg list (e.g. the closing paren).
	 * 
	 * Returns null if the parens are missing or unbalanced.
	 * @param text
	 * @param from
	 * @return
	 */
	private static String extractArgList(String text, int from) {
		int nestLevel = 0;
		String argStr = "";
		from++; // Skip opening paren
		
		// Loop until we find a matching closing paren, or end of string (which is an error and returns null)
		while (true) {
			if (text.charAt(from) == ')') {
				if (nestLevel == 0) { // Found matching closing paren
					return argStr; 
				}
				nestLevel--; // Go up one nesting level
			}
			else if (text.charAt(from) == '(') {
				nestLevel++; // Go down one nesting level
			}
			if (from == text.length()-1) { // Reached end of text without finding matched closing paren
				return null; // Error
			}
			argStr = argStr + text.charAt(from); // Add to arg string
			from++; // Move to next char
		}
	}
	
	/**
	 * Given a built-in function name, returns the evaluation of the function with the given args. Note that
	 * any macros in the args have already been expanded so the args are simple strings.
	 * @param funcName
	 * @param args
	 * @param stmt
	 * @return
	 * @throws Exception
	 */
	public static List<String> evalBuiltInFunction(String funcName, String[] args, String rawArgs, Stmt stmt) throws Exception {
		
		List<String> result = new ArrayList<>();
		
		switch (funcName.toLowerCase()) {
		case "_log":
			// Syntax: $_log(arg1,arg2,...), result (evaluation) is always empty
			// Note an macros in the args have already been expanded, so they are just simple strings
			// We treat all the args as a single output. The parser has broken them into seperate args[]
			// elements if there were any commas. We output a single re-constructed string that includes
			// those commas, so from a users point of view it is a single argument.
			System.out.println("Log macro: "+String.join(",", args));
			break;
		case "_count":
			// Syntax: $_count(name, [add,set,get], value)
			
			if (args.length < 2) throw new SyntaxException("Expected 2 or 3 arguments for _count() built-in macro but found only "+args.length+".", stmt);
			String counterName = args[0].trim().toLowerCase();
			String operation = args[1].trim().toLowerCase();
			String parameter = "0";
			if (!operation.equals("get")) { // GET does not require 3rd arg, all others do
				if (args.length < 3) throw new SyntaxException("Expected 3rd argument for _count() built-in macro but found only "+args.length+".", stmt);
				parameter = args[2].trim();
			}
			if (!counterMap.containsKey(counterName)) { // New counter never seen before, create and init a new one
				counterMap.put(counterName, Integer.valueOf(0));
			}
			
			Number currentValue = counterMap.get(counterName);
			Number parmValue = null; 
			if (parameter.indexOf('.') < 0) // Haha, cannot use ternary "?" operator for this, see https://stackoverflow.com/questions/25230171/unexpected-type-resulting-from-the-ternary-operator
				parmValue = Integer.decode(parameter);
			else 
				parmValue = Double.valueOf(parameter);
			
			switch (operation) {
			case "add":
				result.add(currentValue.toString()); // Only diff with INC is that ADD returns the current value (before adding, e.g. postfix behavior)
			case "inc":
				if ((currentValue instanceof Integer) && (parmValue instanceof Integer)) { // Integer add
					currentValue = currentValue.intValue() + parmValue.intValue();
				} else {
					currentValue = currentValue.doubleValue() + parmValue.doubleValue(); // Floating point add
				}
				counterMap.put(counterName, currentValue);
				break;
			case "set":
				// Set counter to specified value, no result
				counterMap.put(counterName, parmValue);
				break;
			case "get":
				// Just return current value
				result.add(currentValue.toString());
				break;
			default:
				throw new SyntaxException("Count operation '"+operation+"' is not recognized, must be one of GET,SET,ADD,INC.", stmt);
			}
			break; // _count
			
		case "_eval":
			//if (args.length != 1) {
			//	throw new SyntaxException("Expected 1 arg for _eval() function, found "+args.length+" args.", stmt);
			//}
			// Evaluate a math expression (using https://github.com/ezylang/EvalEx). The expression is the entire
			// macro argument (rawArgs), not the comma-delimited args[] array.
			try {
				Expression expr = new Expression(rawArgs, exprConfig);
				Object value = expr.withValues(equMap).evaluate().getValue();
				// By default BigDecimal results use exponent notation, we never want that.
				String valueStr = value instanceof BigDecimal ? ((BigDecimal)value).toPlainString() : value.toString();
				result.add(valueStr);
			}
			catch (Throwable t) {
				throw new SyntaxException("_eval() failed: "+t.getMessage(), stmt);
			}
//			
//			String expr = args[0];
//			String parts[] = Util.split(expr, "\\+"); // Split on + - * / %
//			if (parts.length != 2) {
//				throw new SyntaxException("Expression '"+expr+"' not recognized in _eval() function.", stmt);
//			}
//			try {
//				double left = Double.parseDouble(parts[0].trim());
//				double right= Double.parseDouble(parts[1].trim());
//				double value = 0.0;
//				String operator = "+"; // parts[1].trim();
//				switch (operator) {
//				case "+":
//					value = left + right;
//					break;
//				case "-":
//					value = left - right;
//					break;
//				case "*":
//					value = left * right;
//					break;
//				case "/":
//					value = left / right;
//					break;
//				case "%":
//					value = left % right;
//					break;
//				}
//				String s = Double.toString(value);
//				if (s.endsWith(".0")) s = Util.jsSubstring(s, 0, s.length()-2);
//				result.add(s);
//			}
//			catch (Throwable t) {
//				throw new SyntaxException("Invalid numeric value in _eval() expression '"+expr+"'.", stmt);
//			}
			break; // _eval
			
		default:
			throw new SyntaxException("Build-in function named '"+funcName+"' is not recognized.", stmt);
		}
		
		return result;
	}
	
	/**
	 * Given a macro name and list of argument specifications, return the evaluation of the
	 * named macro with the given arg values. The arg values must be simple comma delimited literal text with no embedded
	 * substitutions. MacroParm specs can be named arguments 'argname1=value1, argname2=value2' or positional 'value1, value2'.
	 * 
	 * @param evalText
	 * @return
	 */
	public static List<String> evalMacroInvocation(String macroName, String[] args, String rawArgs, Stmt stmt) throws Exception {
		
		// Built in functions have the same syntax as macros but start with underscore
		if (macroName.startsWith("_")) {
			return evalBuiltInFunction(macroName, args, rawArgs, stmt);
		}
			
		// Find macro to be evaluated
		Macro m = FXCoreMPMain.macroMap.get(macroName);
		if (m == null) {
			throw new SyntaxException("No definition found for macro '"+macroName+"'.", stmt);
		}
		
		// Verify number of args
		if (args.length != m.getArgCount()) {
			throw new SyntaxException("Number of arguments ("+args.length+") does not match macro definition ("+m.getArgNames().size()+") of macro '"+macroName+"'.", stmt);
		}

		// Build map of arg names to values
		Map<String,MacroParm> argNameValueMap = new HashMap<>();

		int numPosArgs = 0;
		int numNamArgs = 0;
		for (int argNum=0; argNum<args.length; argNum++) {
			String argText = args[argNum].trim();
			// Support named and positional args. All args must be one or the other but not mixed.
			if ((argText.indexOf('=') < 0) ||
					argText.startsWith("<=>") ||
					argText.startsWith("<=") ||
					argText.startsWith("=>") ||
					argText.startsWith("=")
				) 
				{
				// No equal sign or arg starts with a direction indicator, so this is positional MACRO(value0, value1, ...) match each arg, in order, to macro definition arg names
				if (numNamArgs > 0) throw new SyntaxException("The form of the '"+macroName+"' macro arguments appear have both named an positional styles which is not allowed.", stmt);
				String defName = m.getArgNames().get(argNum).getString();		// Name from macro definition
				int defDir = m.getArgNames().get(argNum).getDirection();
				int argDir = DIR_ANY;
				if (argText.startsWith("<=>")) {
					argDir = DIR_INOUT;
					argText = Util.jsSubstring(argText, 3);
				} else if (argText.startsWith("<=")) {
					argDir = DIR_IN;
					argText = Util.jsSubstring(argText, 2);
				} else if (argText.startsWith("=>")) {
					argDir = DIR_OUT;
					argText = Util.jsSubstring(argText, 2);
				} else if (argText.startsWith("=")) {
					argDir = DIR_ANY;
					argText = Util.jsSubstring(argText, 1);
				}
				// If macro defn and invocation arg have direction indicators, make sure they match
				if ((argDir!=DIR_ANY) && (defDir!=DIR_ANY) && (argDir != defDir)) 
					throw new SyntaxException("Direction indicator of '"+argText.trim()+"' does not match '"+defName+"' argument of '"+macroName+"' macro definition.", stmt);
				
				argNameValueMap.put(defName, new MacroParm(argText.trim(), argDir));	// Value is the argument text, positional args are always DIR_ANY
			}
			else {
				// Named argument of the form 'argname=argvalue'.
				if (numPosArgs > 0) throw new SyntaxException("The form of the '"+macroName+"' macro arguments appear have both named an positional styles which is not allowed.", stmt);
				
				//v1.1 Allow in/out direction indicators
				int dir = DIR_INOUT;
				String namevalue[] = Util.split(argText, "<=>");
				if (namevalue.length < 2) {
					dir = DIR_IN;
					namevalue = Util.split(argText, "<=");
					if (namevalue.length < 2) {
						dir = DIR_OUT;
						namevalue = Util.split(argText, "=>");
						if (namevalue.length < 2) {
							dir = DIR_ANY;
							namevalue = Util.split(argText, "=");
						}
					}
				}
				
				if (namevalue.length < 2 || namevalue[0].trim().length()==0 || namevalue[1].trim().length()==0) {
					throw new SyntaxException("Invalid argName=argValue specification '"+argText+"'.", stmt);
				}
				
				String argName = namevalue[0].trim(); // Name is left of equal
				String argValue= namevalue[1].trim(); // Value is right of equal
				
				//System.out.println(argName + " is direction " + dir);
				argNameValueMap.put(argName, new MacroParm(argValue, dir));
			}
		}

		// Invoke the macro to evaluate itself with the given arguments
		return m.eval(argNameValueMap, null);
	}

//--------- Expression evaluator test	
//	public static void main(String[] args) {
//		Map<String,Object> m = new HashMap<>();
//		m.put("X", "3");
//		m.put("Y", "X+2");
//		try {
//			Expression expr = new Expression("Y+5", exprConfig);
//			System.out.println(expr.withValues(m).evaluate().getStringValue()+"");
//		}
//		catch (Throwable t) {
//			System.out.println("eval failed: "+t.getMessage());
//		}
//		
//	}
	
	public static void main(String[] args) {
		try {
			Expression expr = new Expression("3");
			System.out.println(expr.evaluate().getValue()+"");
			expr = new Expression("512");
			System.out.println(expr.evaluate().getValue()+"");
			expr = new Expression("40");  // <--- "4E+1"
			System.out.println(expr.evaluate().getValue()+"");
			expr = new Expression("41");
			System.out.println(expr.evaluate().getValue()+"");
			expr = new Expression("57");
			System.out.println(expr.evaluate().getValue()+"");
			expr = new Expression("60");  // <-- "6E+1"
			System.out.println(expr.evaluate().getValue()+"");
		}
		catch (Throwable t) {
			System.out.println("eval failed: "+t.getMessage());
		}
		
	}
		
}
