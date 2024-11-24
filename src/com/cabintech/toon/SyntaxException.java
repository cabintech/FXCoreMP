package com.cabintech.toon;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
 * This class represents a TOON language syntax error. It has no features beyond that of
 * the Exception class. The TOON processor with throw this type when it detects a syntax
 * error.
 *
 */
public class SyntaxException extends Exception {

	private static final long serialVersionUID = 1L;

	public SyntaxException() {
		super();
		// TODO Auto-generated constructor stub
	}

	public SyntaxException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
		// TODO Auto-generated constructor stub
	}

	public SyntaxException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	public SyntaxException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public SyntaxException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}

	
}
