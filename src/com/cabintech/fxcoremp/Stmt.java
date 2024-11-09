package com.cabintech.fxcoremp;

public class Stmt {

	private String fullText = null;
	private String text = null;
	private String cmnt = "";
	private int lineNum;
	private String fileName;
	
	public Stmt(String line, int lineNum, String fileName) {

		this.lineNum = lineNum;
		this.fileName = fileName;
		this.fullText = line;
		
		// very simplistic comment removal
		int i = fullText.indexOf(';');
		if (i>=0) {
			text = Util.jsSubstring(fullText, 0, i);
			cmnt = Util.jsSubstring(fullText, i);
		}
		else { // No ; comment
			i = fullText.indexOf("//");
			if (i>=0) {
				text = Util.jsSubstring(fullText, 0, i);
				cmnt = Util.jsSubstring(fullText, i);
			}
			else { // No comments of any kind
				text = fullText;
				cmnt = "";
			}
		}
		
		text = text.trim();
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
}
