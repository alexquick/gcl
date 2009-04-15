package gcl;

/**
 * Define values that specify the MACC machine. Change this to specify names for
 * additional registers and concepts as needed.
 */
interface CodegenConstants { // Addressing mode names in SAM2
	public static final Codegen.Mode DREG = Codegen.Mode.DREG;
	public static final Codegen.Mode DMEM = Codegen.Mode.DMEM;
	public static final Codegen.Mode INDXD = Codegen.Mode.INDXD;
	public static final Codegen.Mode IMMED = Codegen.Mode.IMMED;
	public static final Codegen.Mode IREG = Codegen.Mode.IREG;
	public static final Codegen.Mode IMEM = Codegen.Mode.IMEM;
	public static final Codegen.Mode IINDXD = Codegen.Mode.IINDXD;
	public static final Codegen.Mode PCREL = Codegen.Mode.PCREL;

	// REGISTERS in SAM2
	public static final int MAX_REG = 15;

	public static final int INT_SIZE = 2; // Size of an integer and a register

	// levels in variable expressions
	public static final int STACK_LEVEL = -1;
	public static final int CPU_LEVEL = 0;
	public static final int GLOBAL_LEVEL = 1;
	// When level > 1, the variable IS a procedure local or param.

	public static final int UNUSED = 0;
	// an unused field in a loc. Depends on the mode

	// register usage -- used for memory locations also.
	public static final boolean DIRECT = true; // Register has a value
	public static final boolean INDIRECT = false; // Register has a pointer

	// Register names -- more later
	public static final int STATIC_POINTER = 11;
	public static final int FRAME_POINTER = 12;
	public static final int STACK_POINTER = 13;
	public static final int CONSTANT_BASE = 14;
	public static final int VARIABLE_BASE = 15;

	// Stack Frame Layout byte offsets
	// To be done
}
