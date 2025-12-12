package com.cabintech.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 */

import com.cabintech.fxcoremp.FXCoreMPMain;

public class Util {
	
	/**
	 * Holds the results of parsing an input string and extracting 2 groups
	 * of characters - the first 'word' (defined as anything that is not
	 * blank or parens, see pattern below), and everything remaining in the
	 * input after that first word.
	 */
	public record FirstAndRemainder (
		String firstWord,
		String remainder
	) {}
	
    // Regex pattern to extract 2 groups from a string:
    // Group 1: The first word (not space, not parentheses)
    // Group 2: The rest of the string
    private static final Pattern PATTERN_FIRST_AND_REMAINDER = Pattern.compile("^\\s*([^ ()=]+)(.*)", Pattern.DOTALL);

	private Util() {
	}
	
	/**
	 * A substring implementation emulating Javascript substring method. This
	 * always returns a result and never throws index-out-of-bounds exceptions.
	 * If an index is < 0 it is assumed zero, if > string.length-1 it is assumed
	 * to be string.length-1. If they are equal an empty string is returned, if
	 * index2>index1 they are swapped.
	 * @param s
	 * @param index1
	 * @param index2
	 * @return
	 */
	public static final String jsSubstring(String s, int index1, int index2) {
		index1 = Math.min(s.length(), Math.max(0, index1)); // Bound 0 to string len 
		index2 = Math.min(s.length(), Math.max(0, index2)); // Bound 0 to string len 
		
		if (index1==index2) return "";
		
		// Guaranteed to return a result (not throw)
		if (index1>index2) {
			return s.substring(index2, index1);
		}
		return s.substring(index1, index2);
	}
	
	public static final String jsSubstring(String s, int index1) {
		return jsSubstring(s, index1, s.length());
	}
	
	public static String[] split(String s, String regex, int maxParts) {
		if (s==null || (s.trim().length() == 0)) return new String[0]; // Unlike String.split(), return zero elements on empty input
		return s.split(regex, maxParts);
		
	}
	/**
	 * Same as String.split() but smarter handling of empty input.
	 * @param s
	 * @param regex
	 * @return
	 */
	public static String[] split(String s, String regex) {
		return split(s, regex, -1);
	}
	
	public static void info(String info) {
		if (FXCoreMPMain.verbose.equals("info") || FXCoreMPMain.verbose.equals("debug")) {
			System.out.println(info);
		}
	}

	public static void debug(String info) {
		if (FXCoreMPMain.verbose.equals("debug")) {
			System.out.println(info);
		}
	}
	
	/**
	 * Case-insensative string replacement.
	 * From https://stackoverflow.com/questions/5054995/how-to-replace-case-insensitive-literal-substrings-in-java
	 * @param source
	 * @param target
	 * @param replacement
	 * @return
	 */
	public static String replaceAll(String source, String target, String replacement) {
		return Pattern.compile(target, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(source)
		.replaceAll(Matcher.quoteReplacement(replacement)); 
	}
	
    /**
     * Splits the input string into the first word and the remainder. The first word
     * is folded to lower case. The remainder, if any, is trimmed.
     *
     * @param inputString The string to analyze.
     * @return A String array where index 0 is the first word, and index 1 is the remainder. 
     * Returns {"", ""} if no word is found.
     */
    public static FirstAndRemainder getFirstAndRemainder(String inputString) {
    	return getFirstAndRemainder(inputString, true);
    }
    
    /**
     * Splits the input string into the first word and the remainder, optionally
     * folding the first word to lower case. The remainder, if any, is trimmed.
     *
     * @param inputString The string to analyze.
     * @return A String array where index 0 is the first word, and index 1 is the remainder. 
     * Returns {"", ""} if no word is found.
     */
    public static FirstAndRemainder getFirstAndRemainder(String inputString, boolean foldFirstWord) {
        // Default return for null or empty input
        if (inputString == null || inputString.isEmpty()) {
            return new FirstAndRemainder("", "");
        }

        Matcher matcher = PATTERN_FIRST_AND_REMAINDER.matcher(inputString);
        
        if (matcher.matches()) {
            // Group 1: The First Word
            String firstWord = matcher.group(1);
            
            // Group 2: The Remainder of the string (including the separator)
            String remainder = matcher.group(2).trim(); // Clean up leading/trailing spaces on the remainder

            return new FirstAndRemainder (foldFirstWord ? firstWord.toLowerCase() : firstWord, remainder);
        } else {
            return new FirstAndRemainder("", "");
        }
    }

    // --- Example usage of getFirstAndRemainder() ---
    public static void main(String[] args) {
        String input1 = "  $if(condition) { body }";
        FirstAndRemainder result1 = getFirstAndRemainder(input1);
        System.out.println("Input: '" + input1 + "'");
        System.out.println("  Word: '" + result1.firstWord + "'");
        System.out.println("  Remainder: '" + result1.remainder + "'");
        // Output: Word: '$if', Remainder: '(condition) { body }'
        
        System.out.println("---");
        
        String input2 = "   variable=value; // Comment";
        FirstAndRemainder result2 = getFirstAndRemainder(input2);
        System.out.println("Input: '" + input2 + "'");
        System.out.println("  Word: '" + result2.firstWord + "'");
        System.out.println("  Remainder: '" + result2.remainder + "'");
        // Output: Word: 'variable=value;', Remainder: '// Comment'
        
        System.out.println("---");
        
        String input3 = "onlyWord";
        FirstAndRemainder result3 = getFirstAndRemainder(input3);
        System.out.println("Input: '" + input3 + "'");
        System.out.println("  Word: '" + result3.firstWord + "'");
        System.out.println("  Remainder: '" + result3.remainder + "'");
        // Output: Word: 'onlyWord', Remainder: ''
    }
	
}
