package gcl;

import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;
import java.util.Enumeration;

// --------------------- Codegen ---------------------------------
public class Codegen implements Mnemonic, CodegenConstants {
	Codegen(SemanticActions.GCLErrorStream err) {
		this.err = err;
		init();
	}
	
	void setSemanticLevel(SemanticActions.SemanticLevel currentLevel) {
		this.currentLevel = currentLevel; // gives buildOperands access to the currentLevel.
	}

	public void closeCodefile() {
		codefile.close();
	}
	
	/**
	 * Reserve an address for a global variable
	 * 
	 * @param size the number of bytes to reserve
	 * @return the offset at which it will be stored
	 */
	public int reserveGlobalAddress(int size) {
		int result = variableIndex;
		variableIndex += size;
		return result;
	}

	/**
	 * Get a fresh unused label
	 * 
	 * @return the number of the label.
	 */
	public int getLabel() {
		return ++labelIndex;
	}

	/**
	 * How many bytes are allocated to global variables?
	 * 
	 * @return the size of the block
	 */
	public int variableBlockSize() {
		return variableIndex;
	}

	/**
	 * Get an unused register or register pair -- must later be freed
	 * 
	 * @param size number of registers to obtain (1 or 2)
	 * @return the register number
	 */
	public int getTemp(int size) // size = number of registers needed, 1 or
									// 2.
	{
		int result = 0;
		try {
			if (size == 1) {
				while (!freeRegisters[result]){
					result++;
				}
				freeRegisters[result] = false;
			} else {
				while (!freeRegisters[result] || !freeRegisters[result + 1]){
					result++;
				}
				freeRegisters[result] = false;
				freeRegisters[result + 1] = false;
				pair[result] = true;
			}
		} catch (RuntimeException e) {
			err.semanticError(GCLError.NO_REGISTER_AVAILABLE);
		}
		return result <= MAX_REG ? result : 0;
	}

	/**
	 * Free a register previously allocated. Only DREG and IREG operands are
	 * freed.
	 * 
	 * @param mode the addressing mode used with this register
	 * @param base the register to (possibly) free
	 */
	public void freeTemp(Mode mode, int base) {
		if (mode == DREG || mode == IREG) {
			freeRegisters[base] = true;
			if (pair[base]) {
				freeRegisters[base + 1] = true;
				pair[base] = false;
			}
		}
	}

	/**
	 * Free the register held by a location object
	 * 
	 * @param l the location holding the register
	 */
	public void freeTemp(Location l) {
		freeTemp(l.mode, l.base);
	}

	public void printAllocatedRegisters() {
		boolean old = CompilerOptions.listCode;
		CompilerOptions.listCode = true;
		boolean any = false;
		String which = "  Allocated Registers: ";
		for (int i = 0; i < MAX_REG + 1; ++i){
			if (!freeRegisters[i]) {
				which += (i + " ");
				any = true;
			}
		}
		if (!any){
			CompilerOptions.listCode("All registers free.");
		}
		else{
			CompilerOptions.listCode(which);
		}
		CompilerOptions.listCode("");
		CompilerOptions.listCode = old;
	}

	/**
	 * Tag interface to indicate objects have a representation in the Macc
	 * runtime Used for Expressions and GCLStrings.
	 * 
	 */
	interface MaccSaveable {
	}

	/**
	 * Tag interface to indicate the object behaves like a constant and has a
	 * representation in the C1 block in the Macc runtime. Used for
	 * ConstantExpressions and GCLStrings.
	 * 
	 */
	interface ConstantLike extends MaccSaveable {
	}

	/**
	 * Compute a location object that will point to an expression. This is the
	 * major means of addressing SAM entities.
	 * 
	 * @param semanticItem the expression whose addressability is needed
	 * @param currentlevel the scoping level in which to compute the address
	 * @return a Location object with appropriate mode, base, and displacement
	 */
	public Location buildOperands(MaccSaveable semanticItem) {
		if (semanticItem == null || semanticItem instanceof GeneralError){
			return new Location(DREG, 0, 0);
		}
		if (semanticItem instanceof ConstantLike){
			return constants.insertConstant((ConstantLike) semanticItem);
		}
		Mode mode = DREG;
		int base = -1, displacement = 0;
		VariableExpression variable = (VariableExpression) semanticItem;
		int itsLevel = variable.semanticLevel();
		boolean isDirect = variable.isDirect();
		if (itsLevel == CPU_LEVEL) {
			mode = isDirect ? DREG : IREG;
			base = variable.offset();
			displacement = UNUSED;
		} else if (itsLevel == GLOBAL_LEVEL) {
			mode = isDirect ? INDXD : IINDXD;
			base = VARIABLE_BASE;
			displacement = variable.offset();
		} else if (itsLevel == STACK_LEVEL) // This may be wrong. JB.
		{
			base = variable.offset();
			mode = IREG;
			displacement = UNUSED;
		} else // its level > 1;
		{
			int currentlevel = currentLevel.value();
			// more later for function/procedure blocks
		}
		return new Location(mode, base, displacement);
	}

	/**
	 * Load a value into a register (or say which register if already loaded).
	 * 
	 * @param expression  the expression to be loaded
	 * @param currentlevel the scoping level from which to compute the address
	 * @return the register that was used in the load
	 */
	public int loadRegister(Expression expression) {
		if (expression == null) {
			err.semanticError(GCLError.ILLEGAL_LOAD);
			return 0;
		}
		int size = expression.type().size() / 2; // number of registers
													// needed
		if (size > 2) {
			err.semanticError(GCLError.ILLEGAL_LOAD_SIZE);
			return -1;
		}
		Location loc = buildOperands(expression);
		int reg;
		if (loc.mode == DREG){
			reg = loc.base;
		}
		else if (loc.mode == IREG) {
			reg = getTemp(size);
			gen2Address(LD, reg, loc);
			if (size == 2){
				gen2Address(LD, reg + 1, INDXD, loc.base, 2);
			}
			freeTemp(loc);
		} else {
			reg = getTemp(size);
			gen2Address(LD, reg, loc);
			if (size == 2){
				gen2Address(LD, reg + 1, INDXD, loc.base, loc.displacement + 2);
			}
		}
		return reg;
	}

	/**
	 * Load a value's address into a register, or say which if already loaded.
	 * 
	 * @param semanticItem the expression whose address is to be loaded
	 * @param currentlevel the scoping level from which to compute the address
	 * @return the register that was used in the load
	 */
	public int loadAddress(Expression expression) {
		if (expression == null) {
			err.semanticError(GCLError.ILLEGAL_LOAD);
			return 0;
		}
		if (!(expression instanceof VariableExpression)) {
			err.semanticError(GCLError.ILLEGAL_LOAD_ADDRESS);
			return -1;
		}
		Location loc = buildOperands(expression);
		int reg;
		VariableExpression copy = (VariableExpression) expression;
		if (loc.mode == IREG) {
			reg = loc.base;
		} else if (!copy.isDirect() && copy.semanticLevel() == 0)
			reg = loc.base;
		else {
			reg = getTemp(1);
			gen2Address(LDA, reg, loc);
		}
		return reg;
	}

	/**
	 * Load an already existing pointer into a register if it isn't already in a
	 * register. It is an error if the expression doesn't represent a pointer:
	 * i.e. "indirect".
	 * 
	 * @param expression the pointer expression to be loaded
	 * @param currentLevel the the scoping level from which to compute the address
	 * @return the register that holds a copy of the pointer
	 */
	public int loadPointer(Expression expression) {
		if (expression == null) {
			err.semanticError(GCLError.ILLEGAL_LOAD);
			return 0;
		}
		Location loc = buildOperands(expression);
		int reg = 0;
		if (loc.mode == IREG) {
			reg = loc.base; // already loaded
		} else if (loc.mode == IMEM) {
			reg = getTemp(1);
			gen2Address(LD, reg, DMEM, loc.base, loc.displacement);
			freeTemp(loc);
		} else if (loc.mode == IINDXD) {
			reg = getTemp(1);
			gen2Address(LD, reg, INDXD, loc.base, loc.displacement);
			freeTemp(loc);
		} else {
			err.semanticError(GCLError.NOT_A_POINTER);
		}
		return reg;
	}

	/**
	 * Guarantee all code has been written before we quit or at another key point
	 */
	public void flushcode() {/* Nothing in this version */
	}

	// This actually writes the codefile and optionally to the listing.
	private void writeFiles(String what) {
		codefile.println(what);
		CompilerOptions.listCode("$   " + what);
	}

	/**
	 * Generate a 0 address instruction such as HALT
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 */
	public void gen0Address(SamOp opcode) {
		writeFiles(opcode.samCode());
	}

	/**
	 * Generate a 1 address instruction such as WRI
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param mode a valid addressing mode for the operand of the instruction
	 * @param base a register for the operand
	 * @param displacement an offset for the operand
	 */
	public void gen1Address(SamOp opcode, Mode mode, int base, int displacement) {
		writeFiles(opcode.samCode() + mode.address(base, displacement));
	}

	/**
	 * Generate a 1 address instruction such as WRI
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param l a location giving the operand of the instruction
	 */
	public void gen1Address(SamOp opcode, Location l) {
		gen1Address(opcode, l.mode, l.base, l.displacement);
	}

	/**
	 * Generate a 2 address instruction such as IA
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param reg the register representing the first operand
	 * @param mode  a valid addressing mode for the second operand of the
	 *            instruction
	 * @param base a register for the second operand
	 * @param displacement an offset for the second operand
	 */
	public void gen2Address(SamOp opcode, int reg, Mode mode, int base,
			int displacement) {
		writeFiles(opcode.samCode() + 'R' + reg + ", "
				+ mode.address(base, displacement));
	}

	/**
	 * Generate a 2 address instruction such as IA
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param reg the register representing the first operand
	 * @param l  a location giving the second operand of the instruction
	 */
	public void gen2Address(SamOp opcode, int reg, Location l) {
		gen2Address(opcode, reg, l.mode, l.base, l.displacement);
	}

	/**
	 * Generate a two address instruction with a DMEM (label) for the second
	 * operand.
	 * 
	 * @param opcode the operation code as defined in Mnemnonic
	 * @param reg the register representing the first operand
	 * @param dmemLocation a string representing the second operand's label
	 */
	public void gen2Address(SamOp opcode, int reg, String dmemLocation) {
		writeFiles(opcode.samCode() + 'R' + reg + ", " + dmemLocation);
	}

	/**
	 * Generate a jump to a label
	 * 
	 * @param opcode the jump instruction
	 * @param prefix a one character prefix character
	 * @param offset  the integer value of the label
	 */
	public void genJumpLabel(SamOp opcode, char prefix, int offset) {
		writeFiles(opcode.samCode() + prefix + offset);
	}

	/**
	 * Generate a label
	 * 
	 * @param prefix the one character prefix
	 * @param offset the integer value of the label
	 */
	public void genLabel(char prefix, int offset) {
		writeFiles(LABEL_DIRECTIVE.samCode() + prefix + offset);
	}

	/**
	 * Generate a SAM pseudo op INT
	 * 
	 * @param value the value of the integer
	 */
	public void genIntDirective(int value) {
		writeFiles(INT_DIRECTIVE.samCode() + " " + value);
	}

	/**
	 * Generate a SAM pseudo op SKIP
	 * 
	 * @param number  of bytes to skip
	 */
	public void genSkipDirective(int value) {
		writeFiles(SKIP_DIRECTIVE.samCode() + " " + value);
	}

	/** Generate the startup allocation code */
	public void genCodePreamble() {
		gen2Address(LDA, VARIABLE_BASE, "V1");
		gen2Address(LDA, CONSTANT_BASE, "C1");
		gen2Address(LD, STACK_POINTER, IMMED, 0, 16000);
	}

	/** Generate the end code, including the C1 and V1 blocks */
	public void genCodePostamble() {
		gen0Address(HALT);
		genLabel('C', 1);
		constants.genConstBlock();
		genLabel('V', 1);
		genSkipDirective(variableBlockSize());
		flushcode();
	}

	/**
	 * Comment the code arbitrarily -- usually for debugging
	 * 
	 * @param s the comment
	 */
	public void genCodeComment(String s) {
		writeFiles("% " + s); // % is the SAM comment character
	}

	private PrintWriter codefile;
	private int labelIndex = 0; // Last label issued.
	private boolean[] freeRegisters = null;
	private boolean[] pair = null;
	private int variableIndex = 0; // Address offset of next global allocated.
	private ConstantStorage constants = null;
	private SemanticActions.GCLErrorStream err = null; // SemanticActions.err;
	private SemanticActions.SemanticLevel currentLevel;

	public final void init() {
		try {
			codefile = new PrintWriter(new FileOutputStream("codefile"));
		} catch (IOException e) {
			boolean old = CompilerOptions.listCode;
			CompilerOptions.listCode = true;
			CompilerOptions.listCode("File error creating codefile: " + e);
			CompilerOptions.listCode = old;
			System.exit(1);
		}
		freeRegisters = new boolean[MAX_REG + 1];
		pair = new boolean[MAX_REG + 1];
		for (int i = 0; i < MAX_REG; ++i) {
			freeRegisters[i] = true;
			pair[i] = false;
		}
		freeRegisters[STACK_POINTER] = false;
		freeRegisters[CONSTANT_BASE] = false;
		freeRegisters[VARIABLE_BASE] = false;
		variableIndex = 0;
		labelIndex = 0;
		constants = new ConstantStorage(this);

	}

	// --------------------- SAM addressing modes ---------------------------
	abstract static class Mode {
		public static final Mode DREG = new DREG();
		public static final Mode DMEM = new DMEM();
		public static final Mode INDXD = new INDXD();
		public static final Mode IMMED = new IMMED();
		public static final Mode IREG = new IREG();
		public static final Mode IMEM = new IMEM();
		public static final Mode IINDXD = new IINDXD();
		public static final Mode PCREL = new PCREL();

		public abstract String address(int base, int displaement);

		private Mode(int samCode) {
			this.samCode = samCode;
		}

		private int samCode() {
			return samCode;
		}

		private int samCode;

		private static class DREG extends Mode {
			public String address(int base, int displacement) {
				return ("R" + base);
			}

			private DREG() {
				super(0);
			}
		}

		private static class DMEM extends Mode {
			public String address(int base, int displacement) {
				return (displacement >= 0 ? "+" : "") + displacement;
			}

			private DMEM() {
				super(1);
			}
		}

		private static class INDXD extends Mode {
			public String address(int base, int displacement) {
				return (displacement >= 0 ? "+" : "") + displacement + "(R"
						+ base + ')';
			}

			private INDXD() {
				super(2);
			}
		}

		private static class IMMED extends Mode {
			public String address(int base, int displacement) {
				return ("#" + displacement);
			}

			private IMMED() {
				super(3);
			}
		}

		private static class IREG extends Mode {
			public String address(int base, int displacement) {
				return ("*R" + base);
			}

			private IREG() {
				super(4);
			}
		}

		private static class IMEM extends Mode {
			public String address(int base, int displacement) {
				return ((displacement >= 0 ? "*+" : "*") + displacement); //needed?
			}

			private IMEM() {
				super(5);
			}
		}

		private static class IINDXD extends Mode {
			public String address(int base, int displacement) {
				return (displacement >= 0 ? "*+" : "*") + displacement + "(R"
						+ base + ')';
			}

			private IINDXD() {
				super(6);
			}
		}

		private static class PCREL extends Mode {
			public String address(int base, int displacement) {
				return ("&" + displacement);
			}

			private PCREL() {
				super(7);
			}
		}
	}

	// --------------------- machine locations ---------------------------------
	static class Location { // Encapsulate a SAM address in cpu or memory. Immutable
		private Mode mode = null;
		private int base = -99;
		private int displacement = -99;

		public Location(Mode mode, int base, int displacement) {
			this.mode = mode;
			this.base = base;
			this.displacement = displacement;
		}

		// Note: No accessors, but this is an inner class
		public boolean equals(Object obj) {
			try {
				Location other = (Location) obj;
				return (other.mode == mode && other.base == base && other.displacement == displacement);
			} catch (ClassCastException e) {
				return false;
			}
		}

		public int hashCode() {
			return mode.samCode() * 11 + base * 17 + displacement;
		}
	}

	// --------------------- Constant table ------ inner ------------------
	static class SamConstant { // A constant to be put into the C1 block. Immutable
		private int offset; // Offset in the C1 block
		private ConstantLike item; // value of the constant

		SamConstant(int offset, ConstantLike item) {
			this.offset = offset;
			this.item = item;
		}

		/**
		 * The SemanticItem at this offset in the constant block.
		 * 
		 * @return the semantic item (ConstantExpression or StringItem)
		 */
		public ConstantLike item() {
			return item;
		}

		public String toString() {
			return "A const cell at offset: " + offset + ": " + item.toString();
		}
	}

	static class ConstantStorage implements Mnemonic { // The storage for C1 block constants
		private int constantOffset = 0;
		private Codegen codegen;

		ConstantStorage(Codegen cg) {
			this.codegen = cg;
			storage = new Stack<SamConstant>();
		}

		public Location insertConstant(ConstantLike semanticItem) {
			Mode mode = Codegen.INDXD;
			int base = Codegen.CONSTANT_BASE;
			int displacement = lookup(semanticItem);
			if (displacement < 0) {
				displacement = constantOffset;
				// assume integer or boolean constant
				storage.push(new SamConstant(displacement, semanticItem));
				//TODO: handle strings
				constantOffset += ((Expression) semanticItem).type().size();
			}
			return new Location(mode, base, displacement);
		}

		public void genConstBlock() { // Generate the C1 block data		
			Enumeration<SamConstant> elements = elements();
			while (elements.hasMoreElements()) {
				ConstantLike temp = (elements.nextElement()).item();
				ConstantExpression constant = (ConstantExpression) temp;
				//TODO: not only ints, do strings as well
				codegen.genIntDirective(constant.value());
			}
		}

		public int lookup(ConstantLike semanticItem) {
			return -1; // not implemented -- used for optimization of constants
		}

		/**
		 * Return an enumeration over all of the Sam Constants in the order in
		 * which they were created.
		 * 
		 * @return an enumeration over all the sam constants
		 */
		private static Enumeration<SamConstant> elements() {
			return storage.elements();
		}

		// This class also serves as a warehouse for all SamConstant objects.
		private static Stack<SamConstant> storage = null;

		static final void init(){ // needed to reinitialize between runs of GUICompiler	
			storage = new Stack<SamConstant>();
		}
	}
}
