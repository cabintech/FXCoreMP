package com.cabintech.fxcoremp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-process FXCore source code before running it through the CPP (C preprocessor).
 * @author Mark
 *
 */

public class PreCCNoMacros {
	
	private static File sourceDir = null;

	public PreCCNoMacros() {
		// TODO Auto-generated constructor stub
	}
	
	private static void processFile(File inFile, BufferedWriter writer, List<String> includedFiles) throws Throwable {
		//System.out.println("PreCPP: Processing file "+inFile.getName());
		
		if (!inFile.exists()) {
			throw new IOException("Input file '"+inFile.getAbsolutePath()+"' not found.");
		}
		
		int lineNum = 0;
		// Process the input file one line at a time
		try (BufferedReader reader = Files.newBufferedReader(inFile.toPath())) {
			String inputLine = "";
			boolean inDefine = false;
			//List<String> defLines = new ArrayList<String>();
			while ((inputLine = reader.readLine()) != null) {
				lineNum++;
				inputLine = inputLine.trim();
				String inputNoCmnt = inputLine;
				// very simplistic
				{
					int i = inputNoCmnt.indexOf(';');
					if (i>0) {
						inputNoCmnt = inputNoCmnt.substring(0,i);
					}
					else if (i==0) {
						inputNoCmnt = "";
					}
					
					i = inputNoCmnt.indexOf("//");
					if (i>0) {
						inputNoCmnt = inputNoCmnt.substring(0,i);
					}
					else if (i==0) {
						inputNoCmnt = "";
					}
				}
				
				
				//-----------------------------------------
				// $include
				//-----------------------------------------
				if (inputLine.startsWith("$include ")) {
					if (inputNoCmnt.length() < 10) {
						throw new Exception("Invalid $include statement, no file specified");
					}
					String incFileName = inputNoCmnt.substring(9).replace("\"", "").trim();
					if (includedFiles.contains(incFileName)) {
						inputLine = "; Skipped already included file: "+incFileName;
					}
					else {
						// Recursive call to process the #include'd file
						includedFiles.add(incFileName);
						File incFile = new File(sourceDir, incFileName);
						processFile(incFile, writer, includedFiles);
					}
					continue; // Nothing to write for this input line, just continue with next line
				}
				
				//-----------------------------------------
				// $define
				//-----------------------------------------
				else if (inputLine.startsWith("$macro ")) { // Macro definition
					if (inDefine) {
						throw new Exception("Nested macro definitions not supported: '"+inputLine+"'");
					}
					if (inputLine.endsWith("++")) { 
						// Start of multi-line #define
						inDefine = true;
						inputLine = inputLine.substring(0, inputLine.length()-3)+"\\\\"; // Remove '++' and add special line ending
						//defLines.clear();
						//defLines.add(inputLine.substring(inputLine.length()-3));  // Add first line of macro
						//continue; // Do not output this line (yet)
					}
					else {
						// Single line macro definition, just output normally
					}
				}
				else if (inDefine && inputLine.length()==0) { // End of multi-line #define
					inDefine = false;
					//for (String defLine : defLines) { // Emit the macro defn with special line endings
					//	writer.write(defLine+"\\\\"); // Double back-slash
					//	writer.newLine();
					//}
				}
				else if (inDefine) {
					inputLine = inputLine + "\\\\"; // Add special line ending
					//defLines.add(inputLine);
					//continue; // Do not output this line
				}
				
				//-----------------------------------------
				// Output the current line
				//-----------------------------------------

				writer.write(inputLine);
				writer.newLine();
			}
		}
		catch (Throwable t) {
			System.out.println("Error at line "+lineNum+" in '"+inFile.getAbsolutePath()+"': "+t.getMessage());
			throw t;
		}
		
		
	}

	public static void main(String[] args) {

		if (args.length < 2) {
			System.err.println("No input and output files specified");
			System.exit(1);
		}
		
		File outFile= new File(args[1]);
		File srcFile= new File(args[0]);
		if (!srcFile.exists()) {
			System.out.println("Input file '"+srcFile.getAbsolutePath()+"' not found.");
			System.exit(1);
		}
		sourceDir = srcFile.getParentFile();
		
		// Process the input file one line at a time
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
			processFile(srcFile, writer, new ArrayList<String>());
		}
		catch (Throwable t) {
			t.printStackTrace(System.out);
			System.exit(1);
		}
	}

}
