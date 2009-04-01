package gcl;

import java.io.PrintWriter;

/**
 * Define variables to turn the pragmas on and off. Change this only to add new
 * compiler options (pragmas).
 */
class CompilerOptions {
	public static boolean listCode = true;
	public static boolean optimize = false;
	public static boolean showSymbolTable = false;
	public static boolean showMessages = true;

	private static PrintWriter out; // Set by main to the Scanner's listing file.
	private static Codegen codegen; // set by main

	public static void listCode(String s) {
		if (listCode) {
			out.println(s);
		}
	}

	public static void message(String s) {
		if (showMessages) {
			codegen.genCodeComment(s);
		}
	}

	public static void genHalt() {
		codegen.gen0Address(Mnemonic.HALT);
	}

	public static void printAllocatedRegisters() {
		codegen.printAllocatedRegisters();
	}

	public static void init(PrintWriter outFile, Codegen cg) {
		out = outFile;
		codegen = cg;
	}

}
