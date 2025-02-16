package com.cabintech.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 */

import com.cabintech.fxcoremp.FXCoreMPMain;

public class Util {

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
		return split(s, regex, 0);
	}
	
	public static void info(String info) {
		if (FXCoreMPMain.verbose.equals("info") || FXCoreMPMain.verbose.equals("debug")) {
			System.out.println(info);
		}
	}

	public static void debu(String info) {
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
}
