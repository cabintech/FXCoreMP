package com.cabintech.fxcoremp;

/**
 * Represents a macro definition create with the $macro statement.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Macro {
	
	private String macroName = null;							// Macro name
	private List<String> argNames = new ArrayList<String>();	// List of argument names
	private List<Stmt> macroLines = new ArrayList<Stmt>();		// List of lines (one or more)
	
	private static int lastUnique = 0; // Last used ${:unique} macro-scope virtual arg value
	
	/**
	 * Creates a Macro from a set of source statements, the first of which is the $macro statement.
	 * @param defStmts
	 */
	public Macro(List<Stmt> defStmts) throws Exception {
		// The first line of the definition must contain the macro name and any arg names
		Stmt stmt1 = defStmts.get(0);
		String line1 = stmt1.getText();
		line1 = Util.jsSubstring(line1, 7).trim(); // Remove "$macro "
		if (line1.endsWith("++")) {
			line1 = Util.jsSubstring(line1, 0, line1.length()-3).trim(); // Remove multi-line continuation marker
		}
		line1 = line1.replaceAll("\t"," ").trim();
		// Find open paren of arg list
		int paren1 = line1.indexOf('(');
		int paren2 = line1.indexOf(')');
		if (paren1 < 0) {
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
			if (paren2 < 1 || paren1 > paren2) {
				throw new Exception("Invalid macro definition, missing or invalid argument list.");
			}
			macroName = Util.jsSubstring(line1, 0, paren1).trim(); // Up to first paren is macro name
			if (paren2 > paren1+1) { 
				// Non-empty arg list
				String argNameList = Util.jsSubstring(line1, paren1+1, paren2).trim();
				if (argNameList.length() > 0) {
					// Extract comma separated list of arg names
					String names[] = argNameList.split(",");
					for (String name: names) {
						argNames.add(name.trim());
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
		if (macroName.contains("$")) throw new Exception("Macro name cannot contain '$' symbol. Line "+stmt1.getLineNum()+" in file '"+stmt1.getFileName());
		for (int i=0; i<macroName.length(); i++) {
			char c = macroName.charAt(i);
			if (!Character.isJavaIdentifierPart(c)) throw new Exception("Macro name contains invalid character '"+c+"'. Line "+stmt1.getLineNum()+" in file '"+stmt1.getFileName());
		}
		
		
		// Any additional statements are just macro text, do some sanity checking
		// now rather than at eval() time.
		for (int i=1; i<defStmts.size(); i++) {
			Stmt s = defStmts.get(i);
			String line = s.getText();
			if (line.startsWith("$macro ")) {
				throw new Exception("Nested macro definition at line "+s.getLineNum()+" in file '"+s.getFileName()+"'. Maybe missing $endmacro before this?");
			}
			if (line.startsWith("$include ")) {
				throw new Exception("Include not allowed in macro definition, line "+s.getLineNum()+" in file '"+s.getFileName()+"'");
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
	public List<String> getArgNames() {
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
	public List<String> eval(Map<String,String> argValues, Stmt context) throws Exception {
		
		// Use case-insensitive map for arg names
		Map<String, String> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		treeMap.putAll(argValues);

		// All args must be supplied and match definition arg names
		if (treeMap.size() != argNames.size()) {
			throw new Exception("Invocation of macro '"+macroName+"' has incorrect number of args.");
		}
		for (String name: argNames) {
			if (!treeMap.containsKey(name)) {
				throw new Exception("Invocation of macro '"+macroName+"' missing argument named '"+name+"'.");
			}
		}
		
		// Add virtual args
		lastUnique++; // Unique ID at the macro-invocation scope
		treeMap.put(":unique", lastUnique+"");
		
		// Do argument substitution on each line of the macro defn
		List<Stmt> genCode = new ArrayList<Stmt>();
		for (Stmt s: macroLines) {
			String substText = doArgSubst(s.getFullText(), treeMap);
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
	private String doArgSubst(String text, Map<String,String> argMap) throws Exception {
		
		// Crude, but since there are only a few args this is ok.
		//TODO: Does not handle whitespace like "${ myargname }"
		//TODO: This is case sensitive
		for (String name: argNames) {
			text = text.replace("${"+name+"}", argMap.get(name));
		}
		
		// Virtual args (pre-defined substitutions)
		text = text.replace("${:unique}", argMap.get(":unique"));
		
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
		for (Stmt stmt: sourceLines) {
			
//OBSOLETE -- older macro invocation syntax			
//			
//			// Macro form: ${mac-name(args)}
//			while (text.contains("${")) {
//				// Evaluate nested substitutions inside->out
//				int end = text.indexOf("}");
//				int start = Util.jsSubstring(text,0,end).lastIndexOf("${");
//				if (end < 0 || end < start) {
//					throw new Exception("Invalid macro invocation, mismatched braces ${} '"+text+"'");
//				}
//				String evalText = Util.jsSubstring(text, start+2, end).trim(); // Everything between '${' and '}'
//				List<String> macExpanded = Macro.evalMacroInvocation(evalText);
//				// The first line of expansion replaces the macro invocation in the current line. Any additional lines are added immediately following
//				if (macExpanded.size() == 0) {
//					text = text.substring(0, start) + text.substring(end+1); // Macro expanded to nothing
//				}
//				else if (macExpanded.size() == 1) {
//					text = text.substring(0, start) + macExpanded.get(0) + text.substring(end+1); // Single string result replaced macro invocation
//				}
//				else {
//					// Any multi-line expansion replaces the source line without any farther nested expansion
//					text = ""; // Current line is replaced
//					expanded.addAll(macExpanded);
//				}
//			}
			
			// Macro form: $mac-name<white-space> or $mac-name() or $mac-name(args)
			
			String text = stmt.getText(); // Get trimmed text with comments removed
			
			text = text + " "; // Insure line ends in white space to simplify indexing
			int start = text.lastIndexOf('$'); // Right-to-left scanning will insure we process nested macros inside-out
			if (start < 0) {
				// No macros to expand, copy full statement to output and continue with next stmt
				expanded.add(stmt.getFullText());
				continue;
			}
			
			while (start >= 0) {
				// We expect the macro name is next, it ends at first non-identifier char
				String macroName = "";
				char c = ' ';
				int nameEnd = start+1;
				
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
				if (macroName.length()<1) throw new Exception("Missing or invalid macro name in '"+text+"'. Line "+stmt.getLineNum()+" in "+stmt.getFileName());
				
				// c is the first char after the macro name
				
				int end = 0; // Index of end of the macro invocation text
				String args[] = new String[0];
				if (c == '(') {
					// The macro invocation has an arg list. Since we are evaluating macros inside-out, the arg
					// list has no macros in it, just literal text.
					String argListText = extractArgList(text, nameEnd);
					if (argListText == null) throw new Exception("Invalid macro argument list '"+text+"'. Line "+stmt.getLineNum()+" in "+stmt.getFileName());
					args = Util.split(argListText, ",");
					end = nameEnd + argListText.length() + 2; // start + name + args + parens + $ char
				}
				else {
					// No-arg invocation
					end = nameEnd; // End is right after the macro name
				}
				
				// Expand the macro
				List<String> macExpanded = Macro.evalMacroInvocation(macroName, args, stmt);
				
				// The first line of expansion replaces the macro invocation in the current line. Any additional lines are added immediately following
				if (macExpanded.size() == 0) {
					text = Util.jsSubstring(text, 0, start) + Util.jsSubstring(text, end); // Macro expanded to nothing
					if (stmt.getComment().length() > 0) text = text + " " + stmt.getComment();
				}
				else if (macExpanded.size() == 1) {
					text = Util.jsSubstring(text, 0, start) + macExpanded.get(0) + Util.jsSubstring(text, end); // Single string result replaced macro invocation
					if (stmt.getComment().length() > 0) text = text + " " + stmt.getComment();
				}
				else {
					// Any multi-line expansion replaces the source line without any farther nested expansion
					text = ""; // Current line is replaced
					expanded.addAll(macExpanded);
				}

				// Scan (leftward) for more macro invocations
				start = text.lastIndexOf('$');

			}
			
			
			// Now that this line is fully expanded, add it to the output
			expanded.add(text);
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
	 * Given a macro invocation like "MacroName(arg1, arg2, arg3)" return the evaluation of the
	 * named macro with the given arg values. The arg values must be simple comma delimited literal text with no embedded
	 * substitutions. This assumes the surrounding ${...} has already been removed.
	 * 
	 * TODO: Assuming simple comma-delimited arg values. May need to handle arg values with commas in them? 
	 * 
	 * @param evalText
	 * @return
	 */
	public static List<String> evalMacroInvocation(String evalText) throws Exception {
	
		evalText = evalText.trim();
		int openParen = evalText.indexOf('(');
		int closingParen = evalText.lastIndexOf(')');
		String macroName = "";
		String macroArgs = "";
		
		try {
			if (openParen < 0) {
				// No opening paren means zero args for this invocation. The text is just the macro name.
				macroName = evalText;
			}
			else {
				// Sanity check
				if (closingParen!=evalText.length()-1) {
					throw new Exception("Invalid arg list format on macro invocation '"+evalText+"'");
				}
				macroName = Util.jsSubstring(evalText, 0, openParen);
				macroArgs = Util.jsSubstring(evalText, openParen+1, closingParen);
			}
			
			// Find macro to be evaluated
			Macro m = FXCoreMPMain.macroMap.get(macroName);
			if (m == null) {
				throw new Exception("No definition found for macro name '"+macroName+"' from '"+evalText+"'");
			}
	
			String arglist[] = Util.split(macroArgs.trim(), ",");
			Map<String,String> argNameValueMap = new HashMap<>();
			if (macroArgs.indexOf('=') < 0) {
				// No equal signs in the args, so assume positional MACRO(value0, value1, ...) match each arg, in order, to macro definition arg names
				if (arglist.length != m.getArgNames().size()) {
					throw new Exception("Number of positional arguments ("+arglist.length+") does not match macro definition ("+m.getArgNames().size()+").");
				}
				for (int i=0; i<arglist.length; i++) {
					String argName = m.getArgNames().get(i);
					argNameValueMap.put(argName, arglist[i].trim());
				}
			}
			else {
				// Named args
				for (String argSpec: arglist) {
					if (argSpec.trim().length()==0) continue; // Skip any blank specs
					// Each specification is of the form "argname=argvalue".
					String namevalue[] = argSpec.split("=");
					if (namevalue.length < 2 || namevalue[0].trim().length()==0 || namevalue[1].trim().length()==0) {
						throw new Exception("Invalid argName=argValue specification '"+argSpec+"' in '"+evalText+"'");
					}
					// Add to arg map
					argNameValueMap.put(namevalue[0].trim(), namevalue[1].trim());
				}
			}
			
			return m.eval(argNameValueMap, null);
		}
		catch (Throwable t) {
			System.out.println("Failed while evaluating: '"+evalText+"'");
			throw t;
		}
		
	}

	
	/**
	 * Given a macro name and list of argument specifications, return the evaluation of the
	 * named macro with the given arg values. The arg values must be simple comma delimited literal text with no embedded
	 * substitutions. Arg specs can be named arguments 'argname1=value1, argname2=value2' or positional 'value1, value2'.
	 * 
	 * @param evalText
	 * @return
	 */
	public static List<String> evalMacroInvocation(String macroName, String[] args, Stmt stmt) throws Exception {
	
			
		// Find macro to be evaluated
		Macro m = FXCoreMPMain.macroMap.get(macroName);
		if (m == null) {
			throw new Exception("No definition found for macro '"+macroName+"' in '"+stmt.getFullText()+"'. Line "+stmt.getLineNum()+" in "+stmt.getFileName());
		}
		
		// Verify number of args
		if (args.length != m.getArgCount()) {
			throw new Exception("Number of arguments ("+args.length+") does not match macro definition ("+m.getArgNames().size()+") of macro '"+macroName+"'. Line "+stmt.getLineNum()+" in "+stmt.getFileName());
		}

		// Build map of arg names to values
		Map<String,String> argNameValueMap = new HashMap<>();

		int numPosArgs = 0;
		int numNamArgs = 0;
		for (int argNum=0; argNum<args.length; argNum++) {
			String argText = args[argNum];
			// Support named and positional args. All args must be one or the other but not mixed.
			if (argText.indexOf('=') < 0) {
				// No equal signs in the arg, so assume positional MACRO(value0, value1, ...) match each arg, in order, to macro definition arg names
				if (numNamArgs > 0) throw new Exception("The form of the '"+macroName+"' macro arguments appear have both named an positional styles which is not allowed. Line "+stmt.getLineNum()+" in "+stmt.getFileName());
				String argName = m.getArgNames().get(argNum);	// Name from macro definition
				argNameValueMap.put(argName, argText.trim()); 	// Value is the argument text
			}
			else {
				// Named argument of the form 'argname=argvalue'.
				if (numPosArgs > 0) throw new Exception("The form of the '"+macroName+"' macro arguments appear have both named an positional styles which is not allowed. Line "+stmt.getLineNum()+" in "+stmt.getFileName());
				int eqPos = argText.indexOf('=');
				String argName = Util.jsSubstring(argText, 0, eqPos).trim(); // Name is left of equal
				String argValue= Util.jsSubstring(argText, eqPos+1).trim();  // Value is right of equal (can be empty string)
				if (argName.length()==0) throw new Exception("Invalid argName=argValue specification '"+argText+"' in invocation of macro '"+macroName+"'. Line"+stmt.getLineNum()+" in "+stmt.getFileName());
				argNameValueMap.put(argName, argValue);
			}
		}

		// Invoke the macro to evaluate itself with the given arguments
		return m.eval(argNameValueMap, null);
	}
		
}
