package gcl;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Compiler for gcl Copyright 1997-2009, Joseph Bergin. All rights reserved.<br>
 * 
 * This is a command line front end to the compiler.<br>
 * There is also a GUI front end called GUICompiler.java that replaces this file
 * in your project. All other files are the same.
 */

public class GCLCompiler {
	public static void main(String args[]) {
		if (args.length < 2) { // System.out.println("Usage: java GCLCompiler
								// inputfilename /cmo");
			// System.exit(1);
			String[] temp = new String[2];
			BufferedReader in = new BufferedReader(new InputStreamReader(
					System.in));
			if (args.length < 1) {
				System.out.println("Enter the input filename");
				try {
					temp[0] = in.readLine();
				} catch (IOException e) {
					System.out.println("Error reading filename.");
				}
			} else{
				temp[0] = args[0];
			}
			System.out.println("Enter the listing filename");
			try {
				temp[1] = in.readLine();
			} catch (IOException e) {
				System.out.println("Error reading filename.");
			}
			args = temp;
		}
		for (int i = 1; i < args.length; ++i) {
			if (args[i].charAt(0) == '/' || args[i].charAt(0) == '-')
				for (int j = 1; j < args[i].length(); ++j)
					switch (Character.toUpperCase(args[i].charAt(j))) {
					case 'C':
						CompilerOptions.listCode = true;
						break;
					case 'O':
						CompilerOptions.optimize = true;
						break;
					case 'M':
						CompilerOptions.showMessages = true;
						break;
					default:
						System.out.println("Invalid option "
								+ args[i].charAt(j) + ": Only CMO allowed\n");
						System.exit(1);
					}
		}
		try {
			FileInputStream aFile = new FileInputStream(args[0]);
			Scanner scanner = new Scanner(aFile, new FileWriter(args[1]));
			out = scanner.outFile();
			err = new SemanticActions.GCLErrorStream(scanner);
			codegen = new Codegen(err);
			CompilerOptions.init(out, codegen);
			SemanticActions actions = new SemanticActions(codegen, err);
			// actions.init(err);
			parser = new Parser(scanner, actions, err);
			// SemanticActions.parser = parser;
			// SemanticActions.err = err;
		} catch (IOException e) {
			System.out.println("File not found. Exiting.");
			System.exit(1);
		}
		try {
			parser.Parse();
		} catch (Throwable any) {
			err.count++;
			String message = any.getMessage();
			if (message != null)
				System.out.println(message);
			any.printStackTrace();
			any.printStackTrace(out);
		} finally {
			System.out.println("  <end of compilation> 	There were "
					+ err.count + " errors detected");
			out.println("  <end of compilation> 	There were " + err.count
					+ " errors detected");
			CompilerOptions.printAllocatedRegisters();
			if (err.count > 0){
				codegen.gen0Address(Mnemonic.HALT);
			}
			codegen.closeCodefile();
			out.close();
			/*
			 * try { // uncomment this if you want to run from a clickable batch
			 * file System.in.read(); // prevent console window from going away }
			 * catch (java.io.IOException e) {}
			 */
		}
	}

	static Parser parser;
	static SemanticActions.GCLErrorStream err;
	static PrintWriter out;
	static Codegen codegen;
}