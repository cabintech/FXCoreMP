package com.cabintech.toon;

import com.cabintech.fxcoremp.Stmt;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
 * This class represents a TOON language syntax error. It has no features beyond that of
 * the Exception class. The TOON processor with throw this type when it detects a syntax
 * error.
 * 
 * If a Stmt is included on the ctor, it will be accessible for later formation of more usable output messages.
 *
 */
public class SyntaxException extends Exception {

	private static final long serialVersionUID = 1L;
	private Stmt stmt = null;

	public SyntaxException() {
		super();
	}

	public SyntaxException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public SyntaxException(String message, Throwable cause) {
		super(message, cause);
	}

	public SyntaxException(String message) {
		super(message);
	}

	public SyntaxException(Throwable cause) {
		super(cause);
	}
	
	//--- Ctors with Stmt object
	
	public SyntaxException(String message, Stmt stmt) {
		super(message);
		this.stmt = stmt;
	}

	public SyntaxException(String message, Throwable cause, Stmt stmt) {
		super(message, cause);
		this.stmt = stmt;
	}

	public Stmt getStmt() {
		return stmt;
	}
	
	/**
	 * Returns the context of this exception with respect to a supplied Stmt.
	 * @return
	 */
	public String getStmtMessage() {
		return stmt==null ? "No source information available" : "Line "+stmt.getLineNum()+" ("+stmt.getFileName()+"): "+stmt.getFullText();
	}
	
}
