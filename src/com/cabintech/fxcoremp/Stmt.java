package com.cabintech.fxcoremp;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
 * This class represents a single assembler (or TOON) statement. Upon construction a line of text
 * is parsed and comments are separated out. The getText() method returns just the non-comment
 * parts of the input source line.
 */

import com.cabintech.utils.Util;

public class Stmt {

	private String fullText = null;
	private String text = null;
	private String label = "";
	private String cmnt = "";
	private int lineNum;
	private String fileName;
	private boolean isBlockCommentStart = false;
	private boolean isBlockCommentEnd = false;
	private boolean ignore = false; // Ignore this statement for any processing purposes
	
	public Stmt(String line, int lineNum, String fileName) {
		this(line, lineNum, fileName, true); // Default is to parse the input line
	}
	
	public Stmt(String line, int lineNum, String fileName, boolean parse) {

		this.lineNum = lineNum;
		this.fileName = fileName;
		this.fullText = line;
		
		text = fullText.trim(); // By default the statement is all the raw (trimmed) text with no comment
		cmnt = "";
		
		if (!parse) {
			return; // Do no processing of this line, this is a way to carry a simple string as a Stmt object
		}
		
		// very simplistic comment removal
		
		// First look for C-style block comments, the assembler makes them the highest priority,
		// then can even start or end inside a line comment and must be honored. E.g.
		//    some text ; more text /* start of comment
		//    ...more comment...
		//    */
		
		int i = text.indexOf("/*"); // Marks start of comment in any context it occurs
		int j = text.indexOf("*/"); // May start and end on the same line
		if (i>=0) {
			// A block comment starts on this line
			if (j>0) {
				// and ends on this line, so just remove it
				cmnt = Util.jsSubstring(text, i, j+2);
				text = Util.jsSubstring(text, 0, i) + Util.jsSubstring(fullText, j+2); 
			}
			else {
				// and ends on some future line. Remove all to the right of it and flag this as start of a block comment
				cmnt = Util.jsSubstring(text, i);
				text = Util.jsSubstring(text, 0, i); // There may be text before this that should be processed normally
				isBlockCommentStart = true;
			}
		}
		else {
			// No block start, but could be a block end
			if (j>=0) {
				cmnt = Util.jsSubstring(text, 0, j+2);
				text = Util.jsSubstring(text, j+2); // Keep everything after the end marker, could be code there
				isBlockCommentEnd = true;
			}
		}
		
		// Now look for line comments, assembler supports ";" and "//"
		
		i = text.indexOf(';');
		if (i>=0) {
			if (!isBlockCommentEnd && !isBlockCommentStart) cmnt = Util.jsSubstring(text, i);
			text = Util.jsSubstring(text, 0, i);
		}
		else { // No ; comment
			i = text.indexOf("//"); // C-style line comment
			if (i>=0) {
				if (!isBlockCommentEnd && !isBlockCommentStart) cmnt = Util.jsSubstring(text, i);
				text = Util.jsSubstring(text, 0, i);
			}
			else { // No line comment
			}
		}
		
		// Now look for leading "label:"
		
		text = text.trim();
		int labIndex = text.indexOf(':');
		if (labIndex > 0) {
			// It is only a label if there is no whitespace before the ':'
			String potentialLabel = Util.jsSubstring(text, 0, labIndex); // Get possible label text
			if (!potentialLabel.contains(" ") && !potentialLabel.contains("\t")) {
				// Looks like a label
				label = potentialLabel + ": "; // Store with trailing colon and a single space
				text = Util.jsSubstring(text, labIndex+1).trim(); // Remainder is the statement
			}
		}
	}
	
	/**
	 * Returns the statement text without any comment data, trimed of leading/trailing whitespace
	 * @return
	 */
	public String getText() {
		return text;
	}
	
	/**
	 * Returns the full original statement text including whitespace and comments
	 * @return
	 */
	public String getFullText() {
		return fullText;
	}
	
	public int getLineNum() {
		return lineNum;
	}

	public String getFileName() {
		return fileName;
	}
	
	public String getComment() {
		return cmnt;
	}
	
	/**
	 * If this statement has a leading "label:" the
	 * label text (including trailing colon and a single space) is returned.
	 * Otherwise an empty string is returned.
	 * @return
	 */
	public String getLabel() {
		return label;
	}
	
	public boolean isBlockCommentStart() {
		return isBlockCommentStart;
	}
	
	public boolean isBlockCommentEnd() {
		return isBlockCommentEnd;
	}
	
	public void setIgnore(boolean ignore) {
		this.ignore = ignore;
	}
	
	public boolean isIgnore() {
		return ignore;
	}
	
	public void removeComment() {
		cmnt = "";
		fullText = text;
	}
}
