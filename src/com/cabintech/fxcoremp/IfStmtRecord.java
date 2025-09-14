package com.cabintech.fxcoremp;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Representation of an IF/ELSE/ENDIF structure
 */
public record IfStmtRecord(
		Stmt startedAt,
		String condition,
		String elseLabel,
		String endLabel,
		AtomicBoolean elseTaken		// TRUE if an ELSE label has been generated 
		) {

	/**
	 * Use this ctor ('elseTaken' always starts as FALSE)
	 * @param startedAt
	 * @param condition
	 * @param elseLabel
	 * @param endLabel
	 */
	public IfStmtRecord(Stmt startedAt, String condition, String elseLabel, String endLabel) {
		this(startedAt, condition, elseLabel, endLabel, new AtomicBoolean(false));
	}
	
	/**
	 * Convenience method
	 * @return
	 */
	public boolean isElseTaken() {
		return elseTaken.get();
	}
}
