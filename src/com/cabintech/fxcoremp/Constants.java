package com.cabintech.fxcoremp;

/**
 * Common constants for the FXCoreMP project code.
 * @author Mark McMillan
 * 
 * Copyright (c) Cabintech Global LLC
 */

//v1.1 - Added optional 'direction' indicators on macro arguments
//       Added optional TOON statement syntax (Target Of Operation Notation)

public interface Constants {

	public static final int DIR_ANY = 0;
	public static final int DIR_IN = 1;
	public static final int DIR_OUT = 2;
	public static final int DIR_INOUT = 3;
	
	public static final String DIR_ANY_TEXT   = "=";
	public static final String DIR_IN_TEXT    = "<=";
	public static final String DIR_OUT_TEXT   = "=>";
	public static final String DIR_INOUT_TEXT = "<=>";
	
}
