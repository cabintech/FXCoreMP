package com.cabintech.fxcoremp;

public class Arg implements Constants {
	
	String string = "";
	int dir = DIR_ANY;

	public Arg(String string, int direction) {
		this.string = string;
		this.dir = direction;
	}
	
	public String getString() { return string; }
	public int getDirection() { return dir; }

}
