package com.cabintech.fxcoremp;

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
