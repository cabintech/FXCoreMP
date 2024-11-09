package com.cabintech.fxcoremp;

import java.util.Stack;

public class SourceContext {
	
	private static class Ctx {
		public String sourceFile = "";
		public int sourceLine = 0;
		public Ctx(String sourceFile) {
			this.sourceFile = sourceFile;
		}
	}
	
	private static Stack<Ctx> contextStack = new Stack<>();

	public SourceContext() {
		
	}
	
	public static void startFile(String fileName) {
		contextStack.push(new Ctx(fileName));
	}
	public static void endFile() {
		contextStack.pop();
	}
	public static void atLine(int lineNum) {
		if (contextStack.size() == 0) {
			System.out.println("Internal error, context stack is unexpectedly empty");
		}
		else {
			contextStack.lastElement().sourceLine = lineNum;
		}
	}
	public static void dumpContext() {
		System.out.println("Source context:");
		if (contextStack.size() == 0) {
			System.out.println("  (none)");
		}
		else for (Ctx ctx: contextStack) {
			System.out.println("  "+ctx.sourceFile+" at line "+ctx.sourceLine);
		}
	}

}
