package com.cabintech.fxcoremp;

/**
* @author Mark McMillan
* Copyright (c) Cabintech Global LLC
* 
* This class represents a macro parameter (definition argument, or invocation argument value). It
* encapsulates the value of the argument and any indicated direction (DIR_XXXX).
*/


public class MacroParm implements Constants {
	
	String string = "";
	int dir = DIR_ANY;

	public MacroParm(String string, int direction) {
		this.string = string;
		this.dir = direction;
	}
	
	public String getString() { return string; }
	public int getDirection() { return dir; }

}
