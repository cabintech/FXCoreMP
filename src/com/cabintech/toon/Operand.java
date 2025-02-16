package com.cabintech.toon;

import java.util.Map;

import com.cabintech.utils.Util;

/**
 * @author Mark McMillan
 * Copyright (c) Cabintech Global LLC
 * 
 * This object represents a TOON statement operand. It may be the left or right side
 * of an assignment, or either side of an expression. 
 *
 */
public class Operand {
	
	private String text = "";					// Raw text value of the operand
	private String opText = "";					// Operand text with any indirect, prefix, or postfix indicators removed
	private String resolvedName = null;			// Operand name resolved with any prior ".rn" rename statements
	private boolean isDMIndirect = false;		// This is a delay memory indirect operand (x)
	private boolean isAbsDMIndirect = false;  	// This is an absolute delay memory indirect operand #(x)
	private boolean isMRIndirect = false;		// This is a memory register indirect operand [x]
	private boolean isModUpper = false; 		// Has ".U" postfix modifier
	private boolean isModLower = false; 		// Has ".L" postfix modifier
	private boolean isModSat = false; 			// Has ".SAT" postfix modifier
	private boolean isAcc32 = false;			// This operand is ACC32
	private boolean isAcc64 = false;			// This operand is ACC64
	private boolean isCR = false;				// This operand is a core register (Rnn, ACC32, FLAGS). Does not include ACC64.
	private boolean isMR = false;				// This operand is a memory register (MRnnn)


	private boolean isSFR = false;

	public Operand(String text, Map<String,String> rnMap) {
		// Examine the text and determine any special operand syntax
		this.text = text; // Preserve original text as-is
		opText = text.toUpperCase(); // By default, operand is the original text

		// Delay memory indirect "(x)"
		if (opText.startsWith("(") && text.endsWith(")")) {
			isDMIndirect = true;
			opText = Util.jsSubstring(opText, 1, opText.length()-1); // Remove parens
		}
		
		// Delay memory absolute indirect "#(x)"
		else if (opText.startsWith("#(") && text.endsWith(")")) {
			isAbsDMIndirect = true;
			opText = Util.jsSubstring(opText, 2, opText.length()-1); // Remove # and parens
		}
		
		// Memory register indirect "[x]"
		else if (opText.startsWith("[") && text.endsWith("]")) {
			isMRIndirect = true;
			opText = Util.jsSubstring(opText, 1, opText.length()-1); // Remove parens
		}
		
		// Upper postfix modifier
		else if (opText.endsWith(".U")) {
			isModUpper = true;
			opText = Util.jsSubstring(opText, 0, opText.length()-2); // Remove postfix
		}
		
		// Lower postfix modifier
		else if (opText.endsWith(".L")) {
			isModLower = true;
			opText = Util.jsSubstring(opText, 0, opText.length()-2); // Remove postfix
		}
		
		// Saturation postfix modifier
		else if (opText.endsWith(".SAT")) {
			isModSat = true;
			opText = Util.jsSubstring(opText, 0, opText.length()-4); // Remove postfix
		}
		
		// See if the operand is a renamed (.rn) symbol
		if (rnMap.containsKey(opText)) {
			resolvedName = rnMap.get(opText); // Translate any renamed (.rn) symbol
		}
		
		// Determine if operand is a register type (using resolved name)
		String rn = getResolvedName();
		isMR = rn.matches("MR[0-9]+");
		isCR = rn.equals("ACC32") || rn.matches("R[0-9]+") || rn.equals("FLAGS");
		isAcc32 = rn.equals("ACC32");
		isAcc64 = rn.equals("ACC64");
		isSFR = Toon.SrfNameSet.contains(rn.toLowerCase());
		
	}
	
	/**
	 * Returns TRUE if this operand is a CR, MR, or SFR. If this operand is anything
	 * else, including ACC64 then FALSE is returned.
	 * @return
	 */
	public boolean isReg() {
		return isMR || isCR || isSFR;
	}

	/**
	 * Returns TRUE if this operand has any type of indirection.
	 * @return
	 */
	public boolean isIndirect() {
		return isAbsDMIndirect || isDMIndirect || isMRIndirect;
	}
	
	/**
	 * Returns TRUE if this operand is modified with a .U, .L or .SAT postfix.
	 * @return
	 */
	public boolean isModified() {
		return isModLower || isModUpper || isModSat;
	}
	
	/**
	 * Returns TRUE if this operand has no indirection and no modifiers.
	 * @return
	 */
	public boolean isPlain() {
		return !isIndirect() && !isModified();
	}
	
	/**
	 * Returns the original full operand text
	 * @return
	 */
	public String getText() {
		return text;
	}
	
	/**
	 * Returns the operand text with all indicators and modifiers removed
	 * @return
	 */
	public String getOpText() {
		return opText;
	}
	
	
	/**
	 * Returns the operand text after being resolved by any ".rn" rename statements. If this
	 * operand was not resolved by a ".rn" this returns the same as getOpText().
	 * @return
	 */
	public String getResolvedName() {
		return resolvedName==null ? getOpText() : resolvedName;
	}	

	/**
	 * Returns if the syntax of this operand indicates an indirect reference to delay memory, based on the address generation unit (AGU).
	 * "(operand)"
	 * @return
	 */
	public boolean isDMIndirect() {
		return isDMIndirect;
	}

	/**
	 * Returns if the syntax of this operand indicates an indirect absolute reference to delay memory (no use of the address generation unit (AGU)).
	 * "#(operand)"
	 * @return
	 */
	public boolean isAbsDMIndirect() {
		return isAbsDMIndirect;
	}

	/**
	 * Returns if the syntax of this operand indicates an indirect reference to a memory register.
	 * "[operand]"
	 * @return
	 */
	public boolean isMRIndirect() {
		return isMRIndirect;
	}

	/**
	 * Returns if the syntax of this operand indicates an "upper" modifier).
	 * "operand.U"
	 * @return
	 */
	public boolean isModUpper() {
		return isModUpper;
	}

	/**
	 * Returns if the syntax of this operand indicates an "lower" modifier).
	 * "operand.L"
	 * @return
	 */
	public boolean isModLower() {
		return isModLower;
	}

	/**
	 * Returns if the syntax of this operand indicates a "saturation" modifier).
	 * "operand.SAT"
	 * @return
	 */
	public boolean isModSat() {
		return isModSat;
	}

	/**
	 * Returns true if this operand is ACC32
	 * @return
	 */
	public boolean isAcc32() {
		return isAcc32;
	}

	/**
	 * Returns true if this operand is ACC64
	 * @return
	 */
	public boolean isAcc64() {
		return isAcc64;
	}

	/**
	 * Returns true if this operand is Core Register (acc32, flags, cr0-cr15)
	 * @return
	 */
	public boolean isCR() {
		return isCR;
	}

	/**
	 * Returns true if this operand is Memory Register (mr0 - mr127)
	 * @return
	 */
	public boolean isMR() {
		return isMR;
	}

	/**
	 * Returns true if this operand is Special Function Register
	 * @return
	 */
	public boolean isSFR() {
		return isSFR;
	}
}
