package gcl;

import gcl.Codegen.Location;
import gcl.Mnemonic.SamOp;

import java.util.Enumeration;
import java.util.Vector;
import java.util.Hashtable;
import java.io.PrintWriter;

//-------------------- Semantic Records ---------------------
/**
 * This interface is implemented by all semantic Item classes that represent
 * semantic errors. It permits a simple test to filter out all error objects
 * that appear in various semantic routines. The pattern is 'tag interface'.
 */
interface GeneralError { /* Nothing */
}

/**
 * Root of the semantic "record" hierarchy. All parameters of parsing functions
 * and semantic functions are objects from this set of classes.
 */
abstract class SemanticItem {
	public String toString() {
		return "Unknown semantic item. ";
	}

	/**
	 * Polymorphically guarantee that a SemanticItem is an expression. This is
	 * an example of a soft cast.
	 * 
	 * @return "this" if it is an expression and an ErrorExpression otherwise.
	 */
	public Expression expectExpression(SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.EXPRESSION_REQUIRED);
		return new ErrorExpression("Expression Required");
	}
	
	/**
	 * Polymorphically guarantee that a SemanticItem is a type description. This is
	 * an example of a soft cast.
	 * 
	 * @return "this" if it is an Type and an ErrorExpression otherwise.
	 */
	public TypeDescriptor expectTypeDescriptor(SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.INVALID_TYPE);
		return ErrorType.NO_TYPE;
	}

	
	public int semanticLevel() {
		return level;
	}

	public SemanticItem() {
	}

	public SemanticItem(int level) {
		this.level = level;
	}

	// Note: Only expressions and procedures need semanticLevel, but this is the
	// only common ancestor class.
	private int level = -9; // This value should never appear
}

/**
 * A general semantic error. There are more specific error classes also. Immutable.
 */
class SemanticError extends SemanticItem implements GeneralError {
	public SemanticError(String message) {
		this.message = message;
		CompilerOptions.message(message);
	}

	public Expression expectExpression(SemanticActions.GCLErrorStream err) {
		// Soft cast
		return new ErrorExpression("$ Expression Required");
		// Don't complain on error records. The complaint previously
		// occurred when this object was created.
	}

	public String toString() {
		return message;
	}

	private String message;
}

/**
 * An object to represent a user defined identifer in a gcl program. Immutable.
 */
class Identifier extends SemanticItem {
	private final String MULTIPLE_UNDERSCORE = "__";
	public Identifier(String value) {
		this.value = value;
	}

	public String name() {
		return value;
	}

	public String toString() {
		return value;
	}

	public int hashCode() {
		return value.hashCode();
	}

	public boolean equals(Object o) {
		return (o instanceof Identifier)
				&& value.equals(((Identifier) o).value);
	}
	public boolean isIllegal() {
		return value.contains(MULTIPLE_UNDERSCORE);
	}
	private String value;
}

/** Root of the operator hierarchy */
abstract class Operator extends SemanticItem implements Mnemonic {
	public Operator(String op, SamOp opcode) {
		value = op;
		this.opcode = opcode;
	}
	public String toString() {
		return value;
	}

	public final SamOp opcode() {
		return opcode;
	}
	
	private String value;
	private SamOp opcode;
}
abstract class BinaryOperator extends Operator{
	public BinaryOperator(String op, SamOp opcode) {
		super(op, opcode);
	}
	public Expression operate(Expression left, Expression right, Codegen codegen)
	{
		if (!left.type().isCompatible(right.type()))
		{
			return new ErrorExpression("Type:" +left.type()+" is not compatible with type: " +right.type(),ErrorType.INCOMPATIBLE_TYPES);
		}
		if(left.isConstant() && right.isConstant())
		{
			return performConstantEvaluation((ConstantExpression)left, (ConstantExpression)right);
		}
		return performEvaluation(left, right, codegen);
	}
	public abstract Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right);
	public abstract Expression performEvaluation(Expression left, Expression right, Codegen codegen);
}

abstract class UnaryOperator extends Operator
{
	public static final UnaryOperator NOT = new UnaryOperator("NOT",NOP){
		public Expression performConstantEvaluation(ConstantExpression expr){
			if(expr.type().isCompatible(BooleanType.BOOLEAN_TYPE))
			{
				return new ErrorExpression("Logical NOT can only be applied to Boolean type");
			}
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((expr.value())==1)?0:1));
		}
		public Expression performEvaluation(Expression expr, Codegen codegen){
			Codegen.Location expressionLocation = codegen.buildOperands(expr);
			int reg = codegen.getTemp(1);
			codegen.gen2Address(LD, reg, "#1");
			codegen.gen2Address(IS, reg, expressionLocation);
			codegen.freeTemp(expressionLocation);
			return new VariableExpression(BooleanType.BOOLEAN_TYPE, reg, CodegenConstants.DIRECT); // temporary
		}
	};
	public static final UnaryOperator NEG = new UnaryOperator("NEG",INEG){
		public Expression performConstantEvaluation(ConstantExpression expr){
			if(expr.type().isCompatible(IntegerType.INTEGER_TYPE))
			{
				return new ErrorExpression("Negation can only be applied to numeric types");
			}
			return new ConstantExpression(IntegerType.INTEGER_TYPE, (expr.value() *-1));
		}
		public Expression performEvaluation(Expression expr, Codegen codegen){
			Codegen.Location expressionLocation = codegen.buildOperands(expr);
			int reg = codegen.getTemp(1);
			codegen.gen2Address(INEG, reg, expressionLocation);
			codegen.freeTemp(expressionLocation);
			return new VariableExpression(IntegerType.INTEGER_TYPE, reg, CodegenConstants.DIRECT); // temporary
		}
	};
	public Expression operate(Expression expr, Codegen codegen)
	{
		if(expr.isConstant())
		{
			return performConstantEvaluation((ConstantExpression)expr);
		}
		return performEvaluation(expr, codegen);
	}
	public abstract Expression performConstantEvaluation(ConstantExpression expr);
	public abstract Expression performEvaluation(Expression expr, Codegen codegen);
	
	public UnaryOperator(String op, SamOp opcode) {
		super(op, opcode);
	}
}
/**
 * Relational operators such as = and # Typesafe enumeration pattern as well as
 * immutable
 */
abstract class RelationalOperator extends BinaryOperator {
	public static final RelationalOperator EQUAL = new RelationalOperator(
			"equal", JEQ){
				public Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right){
					int res = (left.value() == right.value())?1:0;
					return new ConstantExpression(BooleanType.BOOLEAN_TYPE, res);
				}
			};
	public static final RelationalOperator NOT_EQUAL = new RelationalOperator(
			"notequal", JNE){
				public Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right){
					int res = (left.value() != right.value())?1:0;
					return new ConstantExpression(BooleanType.BOOLEAN_TYPE, res);
				}
			};
	public static final RelationalOperator LESS_THAN = new RelationalOperator(
			"lessthan", JLT){
				public Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right){
					int res = (left.value() < right.value())?1:0;
					return new ConstantExpression(BooleanType.BOOLEAN_TYPE, res);
				}
			};
	public static final RelationalOperator GREATER_THAN = new RelationalOperator(
			"greaterthan", JGT){
				public Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right){
					int res = (left.value() > right.value())?1:0;
					return new ConstantExpression(BooleanType.BOOLEAN_TYPE, res);
				}
			};
	public static final RelationalOperator GT_EQUAL = new RelationalOperator(
			"greaterthanequal", JGE){
				public Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right){
					int res = (left.value() >= right.value())?1:0;
					return new ConstantExpression(BooleanType.BOOLEAN_TYPE, res);
				}
			};
	public static final RelationalOperator LT_EQUAL = new RelationalOperator(
			"lessthanequal", JLE){
				public Expression performConstantEvaluation(ConstantExpression left, ConstantExpression right){
					int res = (left.value() <= right.value())?1:0;
					return new ConstantExpression(BooleanType.BOOLEAN_TYPE, res);
				}
	};
	private RelationalOperator(String op, SamOp opcode) {
		super(op, opcode);
	}
	@Override
	public Expression performEvaluation(Expression left, Expression right, Codegen codegen) {
		int booleanreg = codegen.getTemp(1);
		int resultreg = codegen.loadRegister(left);
		Codegen.Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(LD, booleanreg, CodegenConstants.IMMED, CodegenConstants.UNUSED, 1);
		codegen.gen2Address(IC, resultreg, rightLocation);
		codegen.gen1Address(opcode(), CodegenConstants.PCREL, CodegenConstants.UNUSED, 4);
		codegen.gen2Address(LD, booleanreg, CodegenConstants.IMMED, CodegenConstants.UNUSED, 0);
		codegen.freeTemp(CodegenConstants.DREG, resultreg);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(BooleanType.BOOLEAN_TYPE, booleanreg, CodegenConstants.DIRECT); // temporary
	}
}

/**
 * Logical operators such as & and | Typesafe enumeration pattern as well as immutable
 */
abstract class LogicalOperator extends BinaryOperator {
	public static final LogicalOperator AND = new LogicalOperator("and",BA){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((left.value() + right.value())==2)?1:0));
		}
	};
	public static final LogicalOperator OR = new LogicalOperator("or", BO){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((left.value() + right.value())>0)?1:0));
		}
	};
	
	public Expression performEvaluation(Expression left, Expression right, Codegen codegen)
	{
		int reg = codegen.loadRegister(left);
		Codegen.Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(BooleanType.BOOLEAN_TYPE, reg, CodegenConstants.DIRECT); // temporary
	}
	
	private LogicalOperator(String op, SamOp opcode) {
		super(op, opcode);
	}
}


/**
 * Add operators such as + and - Typesafe enumeration pattern as well as
 * immutable
 */
abstract class AddOperator extends BinaryOperator {
	public static final AddOperator PLUS = new AddOperator("plus", IA){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value()+right.value());
		}
	};
	public static final AddOperator MINUS = new AddOperator("minus", IS){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value()-right.value());
		}
	};

	private AddOperator(String op, SamOp opcode) {
		super(op, opcode);
	}

	public Expression performEvaluation(Expression left, Expression right, Codegen codegen)
	{
		int reg = codegen.loadRegister(left);
		Codegen.Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(IntegerType.INTEGER_TYPE, reg, CodegenConstants.DIRECT); // temporary
	}
}

/**
 * Multiply operators such as * and / Typesafe enumeration pattern as well as immutable
 */
abstract class MultiplyOperator extends BinaryOperator {
	public static final MultiplyOperator TIMES = new MultiplyOperator("times",IM){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value()*right.value());
		}
	};
	public static final MultiplyOperator DIVIDE = new MultiplyOperator("divide", ID){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value()*right.value());
		}
	};
	public static final MultiplyOperator MODULUS = new MultiplyOperator("modulo", NOP){
		public Expression performConstantEvaluation(ConstantExpression left,
				ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value()%right.value());
		}
		public Expression performEvaluation(Expression left,
				Expression right, Codegen codegen){
			Codegen.Location rightLocation = codegen.buildOperands(right);
			int l = codegen.loadRegister(left);
			int lc = codegen.getTemp(1);
			codegen.gen2Address(LD, lc, CodegenConstants.DREG, CodegenConstants.UNUSED, l);
			codegen.gen2Address(ID, l, rightLocation);
			codegen.gen2Address(IM, l, rightLocation);
			codegen.gen2Address(IS, lc, CodegenConstants.DREG, CodegenConstants.UNUSED, l);
			codegen.freeTemp(rightLocation);
			codegen.freeTemp(CodegenConstants.DREG, l);
			return new VariableExpression(IntegerType.INTEGER_TYPE, lc, CodegenConstants.DIRECT); // temporary
		}
	};

	private MultiplyOperator(String op, SamOp opcode) {
		super(op, opcode);
	}

	public Expression performEvaluation(Expression left, Expression right, Codegen codegen)
	{
		int reg = codegen.loadRegister(left);
		Codegen.Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(IntegerType.INTEGER_TYPE, reg, CodegenConstants.DIRECT); // temporary
	}
}

/**
 * Root of the expression object hierarchy. Represent integer and boolean expressions.
 */
abstract class Expression extends SemanticItem implements Codegen.MaccSaveable {
	public Expression(TypeDescriptor type, int level) {
		super(level);
		this.type = type;
	}

	/**
	 * Polymorphically determine if an expression needs to be pushed on the run
	 * stack as part of parallel assignment.
	 * 
	 * @return true if the expression could appear as a LHS operand.
	 */
	public boolean needsToBePushed() {
		return false;
	}

	public TypeDescriptor type() {
		return type;
	}

	public Expression expectExpression(SemanticActions.GCLErrorStream err) {
		return this;
	} // soft cast

	public void discard() {
	} // (Function return only) default is to do nothing
	
	public boolean isConstant()
	{
		return false;
	}
	public boolean isError() {
		return false;
	}
	private TypeDescriptor type;
}

/** Used to represent errors where expressions are expected. Immutable. */
class ErrorExpression extends Expression implements GeneralError,
		CodegenConstants {
	public ErrorExpression(String message) {
		super(ErrorType.NO_TYPE, GLOBAL_LEVEL);
		this.message = message;
		CompilerOptions.message(message);
	}
	public ErrorExpression(String message, ErrorType type)
	{
		super(type, GLOBAL_LEVEL);
		this.message = message;
		CompilerOptions.message(message);		
	}
	public String toString() {
		return message;
	}
	public boolean isError()
	{
		return true;
	}
	private String message;
}

/**
 * Constant expressions such as 53 and true. Immutable. Use this for boolean
 * constants also, with 1 for true and 0 for false.
 */
class ConstantExpression extends Expression implements CodegenConstants,
		Codegen.ConstantLike {
	public ConstantExpression(TypeDescriptor type, int value) {
		super(type, GLOBAL_LEVEL);
		this.value = value;
	}

	public String toString() {
		return "ConstantExpression: " + value + " with type " + type();
	}

	public boolean equals(Object other) {
		return (other instanceof ConstantExpression)
				&& type().baseType().isCompatible(
						((ConstantExpression) other).type().baseType())
				&& ((ConstantExpression) other).value == value;
	}

	public int hashCode() {
		return value * type().baseType().hashCode();
	}
	
	public boolean isConstant()
	{
		return true;
	}
	
	public int value() {
		return value;
	}

	private int value;

	@Override
	public void generateDirective(Codegen codegen) {
		codegen.genIntDirective(value);
	}

	@Override
	public int size() {
		return Codegen.INT_SIZE; 
	}
}

/**
 * Variable expressions such as x and y[3]. Variable here means storable.
 * Objects here are immutable. A level 0 expression is a temporary.
 */
class VariableExpression extends Expression implements CodegenConstants {
	/**
	 * Create a variable expression object
	 * 
	 * @param type the type of this variable
	 * @param scope the nesting level (if >0) or 0 for a register, or -1 for stacktop
	 * @param offset the relative offset of the cells of this variable, or the
	 *            register number if scope is 0
	 * @param direct if false this represents a pointer to the variable
	 */
	public VariableExpression(TypeDescriptor type, int level, int offset,
			boolean direct) {
		super(type, level);
		this.offset = offset;
		this.isDirect = direct;
	}

	/**
	 * Create a temporary expression. The level is 0 and the offset is the
	 * register number
	 * 
	 * @param type the type of this value
	 * @param register the register number in which to hold it
	 * @param direct is the value in the register (true) or a pointer (false)
	 */
	public VariableExpression(TypeDescriptor type, int register, boolean direct) {
		this(type, 0, register, direct);
	}

	public boolean needsToBePushed() { // used by parallel assignment
		return semanticLevel() > CPU_LEVEL
				|| (semanticLevel() == CPU_LEVEL && !isDirect);
	}

	/**
	 * The relative address of the variable. What it is relative to depends on
	 * its scopeLevel. If the level is 1 it is relative to R15.
	 * 
	 * @return the relative offset from its base register.
	 */
	public int offset() {
		return offset;
	}

	public boolean isDirect() {
		return isDirect;
	}

	public void discard(Codegen codegen) // used for function return only
	{
		if (semanticLevel() == STACK_LEVEL) {
			codegen.gen2Address(Mnemonic.IA, STACK_POINTER, IMMED, UNUSED,
					type().size());
		}
	}

	public String toString() {
		return "VariableExpression: level(" + semanticLevel() + ") offset("
				+ offset + ") " + (isDirect ? "direct" : "indirect")
				+ ", with type " + type();
	}

	private int offset; // relative offset of cell or register number
	private boolean isDirect; // if false this is a pointer to a location.
}

/** Carries information needed by the assignment statement */
class AssignRecord extends SemanticItem {
	public void left(Expression left) {
		if (left == null) {
			left = new ErrorExpression("$ Pushing bad lhs in assignment.");
		}
		lhs.addElement(left);
	}

	public void right(Expression right) {
		if (right == null) {
			right = new ErrorExpression("$ Pushing bad rhs in assignment.");
		}
		rhs.addElement(right);
	}

	public Expression left(int index) {
		return lhs.elementAt(index);
	}

	public Expression right(int index) {
		return rhs.elementAt(index);
	}

	/**
	 * Determine whether the assignment statement is legal.
	 * 
	 * @return true if there are the same number of operands on the left and
	 *         right and the types are compatible, etc.
	 */
	public boolean verify(SemanticActions.GCLErrorStream err) { // incomplete. More tests needed.
		boolean result = true;
		if (lhs.size() != rhs.size()) {
			result = false;
			err.semanticError(GCLError.LISTS_MUST_MATCH);
		}
		// more
		return result;
	}

	/**
	 * The number of matched operands of a parallel assignment. In an incorrect
	 * input program the lhs and rhs may not match.
	 * 
	 * @return the min number of lhs, rhs variable expressions.
	 */
	public int size() {
		return Math.min(rhs.size(), lhs.size());
	}

	private Vector<Expression> lhs = new Vector<Expression>(3);
	private Vector<Expression> rhs = new Vector<Expression>(3);
}

/**
 * Used to pass a list of expressions around the parser/semantic area. It is
 * used in the creation of tuple expressions and may be useful elsewhere.
 */
class ExpressionList extends SemanticItem {
	/**
	 * Enter a new expression into the list
	 * 
	 * @param expression the expression to be entered
	 */
	public void enter(Expression expression) {
		elements.addElement(expression);
	}

	/**
	 * Provide an enumeration service over the expressions in the list in the
	 * order they were inserted.
	 * 
	 * @return an enumeration over the expressions.
	 */
	public Enumeration<Expression> elements() {
		return elements.elements();
	}

	private Vector<Expression> elements = new Vector<Expression>();
}

/**
 * Specifies the kind of procedure parameter. The value NOT_PARAM is used for
 * variables that are not parameters. Typesafe enumeration
 */
class ParameterKind extends SemanticItem {
	private ParameterKind() {
	}

	public static final ParameterKind NOT_PARAM = new ParameterKind();
	// more later
}

/** Used to carry information for guarded commands such as if and do */
class GCRecord extends SemanticItem // For guarded command statements if and do.
{
	public GCRecord(int outLabel, int nextLabel) {
		this.outLabel = outLabel;
		this.nextLabel = nextLabel;
		this.firstLabel = nextLabel;
	}

	/**
	 * Mutator for the internal label in an if or do.
	 * 
	 * @param label The new value for this label.
	 */
	public void nextLabel(int label) {
		nextLabel = label;
	}

	/**
	 * Returns the current value of the "internal" label of an if or do.
	 * 
	 * @return the "next" label to appear in a sequence.
	 */
	public int nextLabel() {
		return nextLabel;
	}

	/**
	 * The external label of an if or do statement.
	 * 
	 * @return the external label's numeric value.
	 */
	public int outLabel() {
		return outLabel;
	}
	/**
	 * The first label of an if or do statement.
	 * 
	 * @return the external label's numeric value.
	 */
	public int firstLabel(){
		return firstLabel;
	}
	

	public String toString() {
		return "GCRecord out: " + outLabel + " next: " + nextLabel;
	}

	private int outLabel;
	private int nextLabel;
	private int firstLabel;
}

// --------------------- Types ---------------------------------
/**
 * Root of the type hierarchy. Objects to represent gcl types such as integer
 * and the various array and tuple types. These are immutable after they are
 * locked.
 */
abstract class TypeDescriptor extends SemanticItem implements Cloneable {
	public TypeDescriptor(int size) {
		this.size = size;
	}

	/**
	 * The number of bytes required to store a variable of this type.
	 * 
	 * @return the byte size.
	 */
	public int size() {
		return size;
	}

	/**
	 * Determine if two types are assignment (or other) compatible. This must be
	 * a reflexive, symmetric, and transitive relation.
	 * 
	 * @param other the other type to be compared to this.
	 * @return true if they are compatible.
	 */
	public boolean isCompatible(TypeDescriptor other) { // override this.

		return false;
	}

	/**
	 * Polymorphically determine the underlying type of this type. Useful mostly
	 * for range types.
	 * 
	 * @return this for non-ranges. The base type for ranges.
	 */
	public TypeDescriptor baseType() {
		return this;
	}

	public Object clone() {
		return this;
	}// Default version. Override in mutable subclasses.

	public String toString() {
		return "Unknown type.";
	}

	private int size = 0; // default size. This varies in subclasses.
}

/** Represents an error where a type is expected. Singleton. Immutable. */
class ErrorType extends TypeDescriptor implements GeneralError {
	private ErrorType() {
		super(0);
	}

	public String toString() {
		return "Error type.";
	}

	public Expression expectExpression() // Soft cast
	{
		return new ErrorExpression("Expression Required");
		// Don't complain on error records. The complaint previously
		// occurred when this object was referenced.
	}

	public static final ErrorType NO_TYPE = new ErrorType();
	public static final ErrorType CONSTANT_MUTATION = new ErrorType();
	public static final ErrorType INCOMPATIBLE_TYPES = new ErrorType();
}

/** Integer type. Created at initialization. Singleton. Immutable. */
class IntegerType extends TypeDescriptor implements CodegenConstants {
	private IntegerType() {
		super(INT_SIZE);
	}

	public String toString() {
		return "integer type.";
	}

	static public final IntegerType INTEGER_TYPE = new IntegerType();

	public boolean isCompatible(TypeDescriptor other) {
		return other != null && other.baseType() instanceof IntegerType;
	}
}

/** Boolean type. Created at initialization. Singleton. Immutable. */
class BooleanType extends TypeDescriptor implements CodegenConstants {
	private BooleanType() {
		super(INT_SIZE);
	}

	public String toString() {
		return "Boolean type.";
	}

	public boolean isCompatible(TypeDescriptor other) {
		return other != null && other.baseType() instanceof BooleanType;
	}

	static public final BooleanType BOOLEAN_TYPE = new BooleanType();

}

/**
 * Use this when you need to build a list of types and know the total size of
 * all of them. Used in creation of tuples.
 */
class TypeList extends SemanticItem {
	/**
	 * Add a new type-name pair to the list and accumulate its size
	 * 
	 * @param aType the type to be added
	 * @param name the name associated with the field
	 */
	public void enter(TypeDescriptor aType, Identifier name) {
		elements.addElement(aType);
		names.addElement(name);
		size += aType.size();
	} // TODO check that the names are distinct.

	/**
	 * Add a new type to the list, using a default name This is used to define
	 * anonymous fields in a tuple value
	 * 
	 * @param aType the type of the entry to be added
	 */
	public void enter(TypeDescriptor aType) {
		enter(aType, new Identifier("none_" + next));
		next++; // unique "names" for anonymous fields.
	}

	/**
	 * The total size of the types in the list
	 * 
	 * @return the sum of the sizes
	 */
	public int size() {
		return size;
	}

	/**
	 * An enumeration service for the types in the list in order of insertion
	 * 
	 * @return an enumeration over the type descriptors.
	 */
	public Enumeration<TypeDescriptor> elements() {
		return elements.elements();
	}

	/**
	 * An enumeration service for the names of the fields
	 * 
	 * @return an enumeration over the identifiers
	 */
	public Enumeration<Identifier> names() {
		return names.elements();
	}

	private Vector<TypeDescriptor> elements = new Vector<TypeDescriptor>(2);
	private Vector<Identifier> names = new Vector<Identifier>(2);
	private int size = 0; // sum of the sizes of the types
	private static int next = 0;
}
/**
 * Represents a range of integers or booleans, etc
 *
 */
class RangeType extends TypeDescriptor {
	TypeDescriptor baseType;
	int lbound;
	int ubound;
	Location boundLocation;
	public RangeType(TypeDescriptor baseType, ConstantExpression lbound, ConstantExpression ubound, Location boundLocation) {
		super(baseType.size());
		this.baseType = baseType;
		this.lbound = lbound.value();
		this.ubound = ubound.value();
		this.boundLocation = boundLocation;
	}
	
	public TypeDescriptor baseType(){
		return baseType;
	}
	
	public boolean isCompatible(TypeDescriptor other) { 
		return baseType.isCompatible(other);
	}
	
	public int lbound()
	{
		return lbound;
	}

	public int ubound()
	{
		return ubound;
	}
	
	public Location boundLocation()
	{
		return boundLocation;
	}
}
/**
 * Represents the various tuple types. Created as needed. These are built
 * incrementally and locked at the end to make them immutable afterwards.
 */
class TupleType extends TypeDescriptor { // mutable
	/**
	 * Create a tuple type from a list of its component types. We will need to
	 * add the "methods" to this later.
	 * 
	 * @param carrier the list of component types
	 */
	public TupleType(TypeList carrier) {
		super(carrier.size());
		methods = SymbolTable.unchained();
		Enumeration<TypeDescriptor> e = carrier.elements();
		Enumeration<Identifier> n = carrier.names();
		int inset = 0;
		while (e.hasMoreElements()) {
			TypeDescriptor t = e.nextElement();
			Identifier id = n.nextElement();
			fields.put(id, new TupleField(inset, t));
			inset += t.size();
			names.addElement(id);
		}
	}

	public String toString() {
		String result = "tupleType:[";
		for (int i = 0; i < fields.size(); ++i) {
			result += fields.get(names.elementAt(i)) + " : "
					+ names.elementAt(i) + ", ";// type);
		}
		result += "] with size: " + size();
		return result;
	}

	/**
	 * Get the number of data fields in this tuple
	 * 
	 * @return the number of fields in this tuple
	 */
	public int fieldCount() {
		return names.size();
	}

	/**
	 * Retrieve a named component type of the tuple. It might throw
	 * NoSuchElementException if the argument is invalid.
	 * 
	 * @param fieldName
	 *            the name of the desired component type
	 * @return the type of the named component
	 */
	public TypeDescriptor getType(Identifier fieldName) { // null return value possible
	
		return fields.get(fieldName).type();
	}

	// TODO must override isCompatible

	private Hashtable<Identifier, TupleField> fields = new Hashtable<Identifier, TupleField>(
			4);
	private Vector<Identifier> names = new Vector<Identifier>(4);
	private SymbolTable methods = null; // later

	private class TupleField {
		public TupleField(int inset, TypeDescriptor type) {
			this.inset = inset;
			this.type = type;
		}

		public String toString() {
			return type.toString();
		}

		public TypeDescriptor type() {
			return type;
		}

		public int inset() {
			return inset;
		}

		private int inset;
		private TypeDescriptor type;
	}
}

// --------------------- Semantic Error Values ----------------------------

/**
 * Represents the various gcl errors User errors represent an error in the input
 * program. They must be reported.
 * <p>
 * Compiler errors represent an error in the compiler itself. They must be
 * fixed. These are used to report errors to the user.
 */
abstract class GCLError {
	// The following are user errors. Report them.
	static final GCLError INTEGER_REQUIRED = new Value(1,
			"ERROR -> Integer type required. ");
	static final GCLError ALREADY_DEFINED = new Value(2,
			"ERROR -> The item is already defined. ");
	static final GCLError NAME_NOT_DEFINED = new Value(3,
			"ERROR -> The name is not defined. ");
	static final GCLError TYPE_REQUIRED = new Value(4,
			"ERROR -> TypeReference name required. ");
	static final GCLError LISTS_MUST_MATCH = new Value(5,
			"ERROR -> List lengths must be the same. ");
	static final GCLError NOT_VARIABLE = new Value(6,
			"ERROR -> The Left Hand Side is not a variable access. ");
	static final GCLError EXPRESSION_REQUIRED = new Value(7,
			"ERROR -> Expression required. ");
    static final GCLError INCOMPATIBLE_TYPES = new Value(8,
    		"ERROR -> Operand types are not equivalent. ");
    static final GCLError ILLEGAL_IDENTIFIER = new Value(9,
			"ERROR -> Identifer is not legal. ");
    static final GCLError INVALID_TYPE = new Value(10,
			"ERROR -> Invalid type identifier. ");
	// The following are compiler errors. Repair them.

	static final GCLError ILLEGAL_LOAD = new Value(92,
			"COMPILER ERROR -> The expression is null. ");
	static final GCLError NOT_A_POINTER = new Value(92,
			"COMPILER ERROR -> LoadPointer saw a non-pointer. ");
	static final GCLError ILLEGAL_MODE = new Value(93,
			"COMPILER ERROR -> Sam mode out of range. ");
	static final GCLError NO_REGISTER_AVAILABLE = new Value(94,
			"COMPILER ERROR -> There is no available register. ");
	static final GCLError ILLEGAL_LOAD_ADDRESS = new Value(95,
			"COMPILER ERROR -> Attempt to LoadAddress not a variable. ");
	static final GCLError ILLEGAL_LOAD_SIZE = new Value(96,
			"COMPILER ERROR -> Attempt to load value with size > 4 bytes. ");
	static final GCLError UNKNOWN_ENTRY = new Value(97,
			"COMPILER ERROR -> An unknown entry was found. ");

	// More of each kind of error as you go along building the language.

	public abstract int value();

	public abstract String message();

	static class Value extends GCLError {
		private Value(int value, String msg) {
			this.message = msg;
			this.value = value;
		}

		public int value() {
			return value;
		}

		public String message() {
			return message;
		}

		private int value;
		private String message;
	}
} // end GCLError

// ---------------------GCLString-----------------------------------------
class GCLString implements Codegen.ConstantLike{
	String samString;
	int size;
	public GCLString(String wrappedString)
	{
		StringBuffer workingCopy = new StringBuffer(wrappedString.substring(1, wrappedString.length()-1));
		//StringBuffer workingCopy = new StringBuffer();
		//workingCopy.append(wrappedString.subSequence(1, wrappedString.length()-1));
		
		//escape
		for(int i = 0; i < workingCopy.length(); i++)
		{
			char current = workingCopy.charAt(i);
			if(current == ':' || current == '"'){
				workingCopy.insert(i, ':');
				i++;
			}
		}
		
		//requote
		samString = workingCopy.append('"').insert(0, '"').toString();
		
		//get size
		if(wrappedString.length() % 2 == 0){
			this.size = wrappedString.length() ;
		}
		else{
			this.size = wrappedString.length() - 1;
		}
	}
	@Override
	public void generateDirective(Codegen codegen) {
		codegen.genStringDirective(samString);
	}
	@Override
	public int size() {
		return size;
	}
	
}

// --------------------- SemanticActions ---------------------------------

public class SemanticActions implements Mnemonic, CodegenConstants {

	SemanticActions(Codegen codeGenerator, GCLErrorStream err) {
		this.codegen = codeGenerator;
		codegen.setSemanticLevel(currentLevel());
		this.err = err;
		init();
	}

	private Codegen codegen;

	/** Used to produce messages when an error occurs */
	static class GCLErrorStream extends Errors { // Errors is defined in Parser
		GCLErrorStream(Scanner scanner) {
			super(scanner);
		}

		void semanticError(GCLError errNum) {
			PrintWriter out = scanner.outFile();
			out.print("At ");
			semanticError(errNum.value(), scanner.currentToken().line(), scanner.currentToken().column());
			out.println(errNum.message());
			out.println();
			CompilerOptions.genHalt();
		}

		void semanticError(GCLError errNum, String extra) {
			scanner.outFile().println(extra);
			semanticError(errNum);
		}
	} // end GCLErrorStream

	/***************************************************************
	 * Auxiliary Determine if a symboltable entry can safely be
	 * redefined at this point. Only one definition is legal in a given scope.
	 * 
	 * @param entry a symbol table entry to be checked.
	 * @return true if it is ok to redefine this entry at this point.
	 */
	private boolean OKToRedefine(SymbolTable.Entry entry) {
		return false; // more later
	}

	/***************************************************************************
	 * Auxiliary Report that the identifier is already
	 * defined in this scope if it is. Called from most declarations.
	 * 
	 * @param ID an Identifier
	 * @param scope the symbol table used to find the identifier.
	 **************************************************************************/
	private void complainIfDefinedHere(SymbolTable scope, Identifier id) {
		SymbolTable.Entry entry = scope.lookupIdentifier(id);
		if (entry != null && !OKToRedefine(entry)) {
			err.semanticError(GCLError.ALREADY_DEFINED);
		}
	}
	/***************************************************************************
	 * Auxiliary Report to notify that id is illegal (p____, etc.)
	 * 
	 * @param ID an Identifier
	 **************************************************************************/
	private void complainIfIllegalIdentifier(Identifier id) {
		if(id.isIllegal())
		{
			err.semanticError(GCLError.ILLEGAL_IDENTIFIER);
		}
	}

	/***************************************************************************
	 * auxiliary moveBlock moves a block (using blocktransfer)
	 * from source to dest. Both source and destination refer to expr entries .
	 **************************************************************************/
	private void moveBlock(Expression source, Expression destination) {
		if (source instanceof ErrorExpression) {
			return;
		}
		if (destination instanceof ErrorExpression) {
			return;
		}
		int size = source.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		Codegen.Location sourceLocation = codegen.buildOperands(source);
		codegen.gen2Address(LDA, reg, sourceLocation);
		codegen.gen2Address(LD, reg + 1, IMMED, UNUSED, size);
		sourceLocation = codegen.buildOperands(destination);
		codegen.gen2Address(BKT, reg, sourceLocation);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(sourceLocation);
	}

	/***************************************************************************
	 * auxiliary moveBlock moves a block (using blocktransfer)
	 * from source to dest. Source refers to an expr entry. mode, base, and
	 * displacement give the dest.
	 **************************************************************************/
	private void moveBlock(Expression source, Codegen.Mode mode, int base,
			int displacement) {
		if (source instanceof ErrorExpression) {
			return;
		}
		int size = source.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		Codegen.Location sourceLocation = codegen.buildOperands(source);
		codegen.gen2Address(LDA, reg, sourceLocation);
		codegen.gen2Address(LD, reg + 1, IMMED, UNUSED, size);
		codegen.gen2Address(BKT, reg, mode, base, displacement);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(sourceLocation);
	}

	/***************************************************************************
	 * auxiliary moveBlock moves a block (using blocktransfer)
	 * from source to destination. Source is given by mode, base, displacement
	 * and destination refers to an expr entry .
	 **************************************************************************/
	private void moveBlock(Codegen.Mode mode, int base, int displacement,
			Expression destination) {
		if (destination instanceof ErrorExpression) {
			return;
		}
		int size = destination.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		if (mode == IREG) {// already have an address
			codegen.gen2Address(LD, reg, DREG, base, UNUSED);
		} else {
			codegen.gen2Address(LDA, reg, mode, base, displacement);
		}
		codegen.gen2Address(LD, reg + 1, IMMED, UNUSED, size);
		Codegen.Location destinationLocation = codegen
				.buildOperands(destination);
		codegen.gen2Address(BKT, reg, destinationLocation);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(destinationLocation);
	}

	/**
	 * auxiliary Push an expression onto the run time stack
	 * 
	 * @param source the expression to be pushed
	 */
	private void pushExpression(Expression source) {
		if (source.type().size() == INT_SIZE) {
			codegen.gen2Address(IS, STACK_POINTER, IMMED, UNUSED, INT_SIZE);
			int reg = codegen.loadRegister(source);
			codegen.gen2Address(STO, reg, IREG, STACK_POINTER, UNUSED);
			codegen.freeTemp(DREG, reg);
		} else {
			int size = source.type().size();
			codegen.gen2Address(IS, STACK_POINTER, IMMED, UNUSED, size);
			moveBlock(source, IREG, STACK_POINTER, UNUSED);
		}
	}

	/**
	 * **************** auxiliary Pop an expression from the run time stack into
	 * a given destination
	 * 
	 * @param destination
	 *            the destination for the pop
	 */
	private void popExpression(Expression destination) {
		if (destination.type().size() == INT_SIZE) {
			int reg = codegen.getTemp(1);
			codegen.gen2Address(LD, reg, IREG, STACK_POINTER, UNUSED);
			Codegen.Location destinationLocation = codegen
					.buildOperands(destination);
			codegen.gen2Address(STO, reg, destinationLocation);
			codegen.gen2Address(IA, STACK_POINTER, IMMED, UNUSED, INT_SIZE);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(destinationLocation);
		} else { // blockmove
			moveBlock(IREG, STACK_POINTER, UNUSED, destination);
			codegen.gen2Address(IA, STACK_POINTER, IMMED, UNUSED, destination
					.type().size());
		}
	}

	/**
	 * auxiliary Move the value of an expression from its
	 * source to a destination
	 * 
	 * @param source the source of the expression
	 * @param destination the destination to which to move the value
	 */
	private void simpleMove(Expression source, Expression destination) {
		if (destination.type().size() == INT_SIZE) {
			int reg = codegen.loadRegister(source);
			Codegen.Location destinationLocation = codegen .buildOperands(destination);
			codegen.gen2Address(STO, reg, destinationLocation);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(destinationLocation);
		} else {
			moveBlock(source, destination);
		}
	}

	/**
	 * **************** auxiliary Move the value of an expression from a source
	 * to a destination
	 * 
	 * @param source the source of the move
	 * @param mode the mode of the destination's location
	 * @param base the base of the destination location
	 * @param displacement the displacement of the destination location
	 */
	private void simpleMove(Expression source, Codegen.Mode mode, int base,
			int displacement) {
		if (source.type().size() == INT_SIZE) {
			int reg = codegen.loadRegister(source);
			codegen.gen2Address(STO, reg, mode, base, displacement);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(mode, base);
		} else {
			moveBlock(source, mode, base, displacement);
		}
	}

	/***************************************************************************
	 * Transform an identifier into the semantic item that it represents
	 * 
	 * @param scope the current scope
	 * @param ID and identifer to be transformed
	 * @return the semantic item that the identifier represents.
	 */
	SemanticItem semanticValue(SymbolTable scope, Identifier id) {
		SymbolTable.Entry symbol = scope.lookupIdentifier(id);
		if (symbol == null) {
			err.semanticError(GCLError.NAME_NOT_DEFINED);
			return new SemanticError("Identifier not found in symbol table.");
		} else {
			return symbol.semanticRecord();
		}
	}

	/***************************************************************************
	 * Generate code for an assignment. Copy the RHS expressions to the
	 * corresponding LHS variables.
	 * 
	 * @param expressions an assignment record with two expr vectors (RHSs, LHSs )
	 **************************************************************************/
	void parallelAssign(AssignRecord expressions) {
		int i;
		// part 1. checks and optimizations
		if (!expressions.verify(err)) {
			return;
		}
		int entries = expressions.size(); // number of entries to process
		if (CompilerOptions.optimize && entries == 1) { // whatever
		} // optimizations possible
		// part 2. pushing except consts, temps, and stackvariables
		for (i = 0; i < entries; ++i) {
			Expression rightExpression = expressions.right(i);
			if (rightExpression.needsToBePushed()) {
				pushExpression(rightExpression);
			}
		}
		// part 3. popping the items pushed in part 2 & copying the rest
		for (i = entries - 1; i >= 0; --i) {
			Expression rightExpression = expressions.right(i);
			Expression leftExpression = expressions.left(i);
			if(leftExpression.isConstant())
			{
				this.err.semanticError(GCLError.NOT_VARIABLE);
			}
			if(leftExpression.type() instanceof RangeType){
				RangeType range = (RangeType)leftExpression.type();
				if(rightExpression.isConstant())
				{
					int value = ((ConstantExpression)rightExpression).value();
					if(range.lbound() > value || range.ubound() < value)
					{
						this.err.semanticError(GCLError.INCOMPATIBLE_TYPES, "Cannot assign constant to range when constant is out of range bounds");
					}
				}
				else
				{
					checkRange(range, rightExpression);
				}
			}
			else{
				if(!leftExpression.type().isCompatible(rightExpression.type()))
				{
					// if either are errors, there's no sense in grumbling again
					if(leftExpression.isError() || rightExpression.isError())
					{
						this.err.semanticError(GCLError.INCOMPATIBLE_TYPES);
					}
				}
			}
			if (rightExpression.needsToBePushed()) {
				popExpression(leftExpression);
			} else { // the item wasn't pushed, so normal copy
				simpleMove(rightExpression, leftExpression);
			}
		}
	}

	/***************************************************************************
	 * Generate code to read into an integer variable. (Must be an assignable
	 * variable)
	 * 
	 * @param expression
	 *            (integer variable) expression
	 **************************************************************************/
	void readVariable(Expression expression) {
		if (expression instanceof GeneralError) {
			return;
		}
		if (!expression.type().isCompatible(INTEGER_TYPE)) {
			err.semanticError(GCLError.INTEGER_REQUIRED, "   while Reading");
			return;
		}
		Codegen.Location expressionLocation = codegen.buildOperands(expression);
		codegen.gen1Address(RDI, expressionLocation);
		if(expression.type() instanceof RangeType)
		{
			checkRange(((RangeType)expression.type()), expression);
		}
	}
    
	/***************************************************************************
	 * Generate code to runtime range check a range
	 * 
	 * @param expression (range) expression
	 **************************************************************************/
	void checkRange(RangeType range, Expression expression) {
		int register = codegen.loadRegister(expression);
		codegen.gen2Address(TRNG, register, range.boundLocation());
		codegen.freeTemp(DREG, register);
	}

	/***************************************************************************
	 * Generate code to write an integer expression.
	 * 
	 * @param expression (integer) expression
	 **************************************************************************/
	void writeExpression(Expression expression) {
		if (expression instanceof GeneralError) {
			return;
		}
		if (!expression.type().isCompatible(INTEGER_TYPE)) {
			err.semanticError(GCLError.INTEGER_REQUIRED, "   while Writing");
			return;
		}
		Codegen.Location expressionLocation = codegen.buildOperands(expression);
		codegen.gen1Address(WRI, expressionLocation);
		codegen.freeTemp(expressionLocation);
	}

	/***************************************************************************
	 * Generate code to write a string literal
	 * 
	 * @param java string captured from scanner
	 **************************************************************************/
	void writeString(String str){
		if(!(str.length() == 2)) //ignore empty strings
		{
			GCLString gclString = new GCLString(str);
			Location stringLocation = codegen.buildOperands(gclString);
			codegen.gen1Address(WRST, stringLocation);
		}
	}
	/***************************************************************************
	 * Generate code to write an end of line mark.
	 **************************************************************************/
	void genEol() {
		codegen.gen0Address(WRNL);
	}

	/***************************************************************************
	 * Generate code to operate a given binary operator on two operands
	 * 
	 * @param left an expression 
	 * @param op an operator
	 * @param right an expression 
	 * @return result expression 
	 **************************************************************************/
	Expression evaluateBinaryOperator(Expression left, BinaryOperator op, Expression right) {
		Expression ret = op.operate(left, right, codegen);
		if(ret instanceof ErrorExpression)
		{
			this.err.semanticError(GCLError.INCOMPATIBLE_TYPES);
		}
		return ret;
	}
	
	Expression evaluateUnaryOperator(Expression ex, UnaryOperator op)
	{
		Expression ret = op.operate(ex, codegen);
		if(ret instanceof ErrorExpression)
		{
			this.err.semanticError(GCLError.INCOMPATIBLE_TYPES);
		}
		return ret;
	}
	
	/***************************************************************************
	 * Create a label record with the outlabel for an IF statement.
	 * 
	 * @return GCRecord entry with two label slots for this statement.
	 **************************************************************************/
	GCRecord startIf() {
		return new GCRecord(codegen.getLabel(), 0);
	}

	/***************************************************************************
	 * Generate the final label for an IF. (Halt of we fall through to here).
	 * 
	 * @param entry GCRecord holding the labels for this statement.
	 **************************************************************************/
	void endIf(GCRecord entry) {
		codegen.gen0Address(HALT);
		codegen.genLabel('J', entry.outLabel());
	}
	/***************************************************************************
	 * Create a marker to denote top of iteration loop
	 * 
	 * @return GCRecord entry with two label slots for this statement.
	 **************************************************************************/
	GCRecord startDo() {
		GCRecord record = new GCRecord(codegen.getLabel(),codegen.getLabel());
		codegen.genLabel('J', record.nextLabel());
		return record;
	}

	/***************************************************************************
	 * Generate the final label and iteration logic for an DO. 
	 * (repeat if we fall through to here, continue otherwise).
	 * 
	 * @param entry GCRecord holding the labels for this statement.
	 **************************************************************************/
	void endDo(GCRecord entry) {
		codegen.gen1Address(JMP, PCREL, UNUSED, 4);
		codegen.genLabel('J', entry.outLabel());
		codegen.genJumpLabel(JMP,'J',entry.firstLabel());
	}
	
	/***************************************************************************
	 * Create a marker to denote top of an iteration loop
	 * 
	 * @return GCRecord entry with two label slots for this statement.
	 **************************************************************************/
	GCRecord beginFor(Expression control) {
		if(!(control.type() instanceof RangeType))
		{
			err.semanticError(GCLError.INCOMPATIBLE_TYPES, "Cannot use forall to iterate over non-range types");
			return null;
		}
		Location boundLocation = ((RangeType)control.type()).boundLocation();
		int reg = codegen.loadRegister(control);
		Location controlLocation = codegen.buildOperands(control);
		codegen.gen2Address(LD, reg, boundLocation);
		codegen.gen2Address(STO, reg, controlLocation);
		
		GCRecord record = new GCRecord(codegen.getLabel(), codegen.getLabel());
		codegen.genLabel('F', record.nextLabel());
		
		//clean up
		codegen.freeTemp(controlLocation);
		codegen.freeTemp(boundLocation);
		codegen.freeTemp(DREG, reg);
		
		return record;
	}

	void endFor(Expression control, GCRecord forallRecord) {
		if(forallRecord == null){
			return;
		}
		
		int endLabel = codegen.getLabel();
		Location controlLocation = codegen.buildOperands(control);
		Location boundLocation = ((RangeType)control.type()).boundLocation();
		int reg = codegen.loadRegister(control);
		
		codegen.gen2Address(IC, reg, new Location(boundLocation,+control.type().size()));
		codegen.genJumpLabel(JEQ, 'X', endLabel);
		codegen.gen2Address(IA, reg, "#1");
		codegen.gen2Address(STO, reg, controlLocation);
		codegen.genJumpLabel(JMP, 'F', forallRecord.nextLabel());
		codegen.genLabel('X', endLabel);
		
		codegen.freeTemp(controlLocation);
		codegen.freeTemp(boundLocation);
		codegen.freeTemp(DREG, reg);
	}
	
	/***************************************************************************
	 * If the expr represents true, jump to the next else part.
	 * 
	 * @param expression Expression to be tested: must be boolean
	 * @param entry GCRecord with the associated labels. This is updated
	 **************************************************************************/
	void ifTest(Expression expression, GCRecord entry) {
		int resultreg = codegen.loadRegister(expression);
		int nextElse = codegen.getLabel();
		entry.nextLabel(nextElse);
		codegen.gen2Address(IC, resultreg, IMMED, UNUSED, 1);
		codegen.genJumpLabel(JNE, 'J', nextElse);
		codegen.freeTemp(DREG, resultreg);
	}

	/***************************************************************************
	 * Generate a jump to the out label and insert the next else label.
	 * 
	 * @param entry GCRecord with the labels
	 **************************************************************************/
	void elseIf(GCRecord entry) {
		codegen.genJumpLabel(JMP, 'J', entry.outLabel());
		codegen.genLabel('J', entry.nextLabel());
	}

	/***************************************************************************
	 * Create a tuple from a list of expressions Both the type and the value
	 * must be created.
	 * 
	 * @param tupleFields an expression list with the fields of the tuple
	 * @return an expression representing the tuple value as a whole.
	 **************************************************************************/
	Expression buildTuple(ExpressionList tupleFields) {
		Enumeration<Expression> elements = tupleFields.elements();
		TypeList types = new TypeList();
		int address = codegen.variableBlockSize(); // beginning of the tuple
		while (elements.hasMoreElements()) {
			Expression field = elements.nextElement();
			TypeDescriptor aType = field.type();
			types.enter(aType);
			int size = aType.size();
			int where = codegen.reserveGlobalAddress(size);
			CompilerOptions.message("Tuple component of size " + size + " at "
					+ where);
			// Now bring all the components together into a contiguous block
			simpleMove(field, INDXD, VARIABLE_BASE, where);
		}
		TupleType tupleType = new TupleType(types);
		return new VariableExpression(tupleType, GLOBAL_LEVEL, address, DIRECT);
	}

	/***************************************************************************
	 * Enter the identifier into the symbol table, marking it as a variable of
	 * the given type. This method handles global variables as well as local
	 * variables and procedure parameters.
	 * 
	 * @param scope the current symbol table
	 * @param type the type to be of the variable being defined
	 * @param ID identifier to be defined
	 * @param procParam the kind of procedure param it is (if any).
	 **************************************************************************/
	void declareVariable(SymbolTable scope, TypeDescriptor type, Identifier id,
			ParameterKind procParam) {
		complainIfDefinedHere(scope, id);
		complainIfIllegalIdentifier(id);
		VariableExpression expr = null;
		if (currentLevel().isGlobal()) { // Global variable
			int addressOffset = codegen.reserveGlobalAddress(type.size());
			expr = new VariableExpression(type, currentLevel().value(),
					addressOffset, DIRECT);
		} else { // may be param or local
			// more later --
		}
		SymbolTable.Entry variable = scope.newEntry("variable", id, expr);
		CompilerOptions.message("Entering: " + variable);
	}
	/***************************************************************************
	 * Enter the identifier into the symbol table, marking it as a constant of
	 * the given type. This method handles global variables as well as local
	 * variables and procedure parameters.
	 * 
	 * @param scope the current symbol table
	 * @param ID identifier to be defined
	 * @param expression the expression that gives this constant value
	 **************************************************************************/
	void declareConstant(SymbolTable scope, Identifier id,
			Expression expression) {
		complainIfDefinedHere(scope, id);
		complainIfIllegalIdentifier(id);
		SymbolTable.Entry variable = scope.newEntry("constant", id, expression);
		CompilerOptions.message("Declaring Constant: " + variable);
	}
	
	/***************************************************************************
	 * Enter the typedef identifier into the symbol table, marking it as a 
	 * type constant of the given type
	 * 
	 * @param scope the current symbol table
	 * @param ID identifier to be defined
	 * @param type the type which this type constant refers to
	 **************************************************************************/
	void declareType(SymbolTable scope, Identifier id,
			TypeDescriptor type) {
		scope.newEntry("type", id, type);
		CompilerOptions.message("Declaring type constant: " + id + " as " + type);
	}
	
	/***************************************************************************
	 * Set up the registers and other run time initializations.
	 **************************************************************************/
	void startCode() {
		codegen.genCodePreamble();
	}
	
	/***************************************************************************
	 * Create a range TypeDescriptor from the given baseType, lower bound and upper bound
	 * 
	 * @param baseType the type from which the range is based
	 * @param lbound the lower bound of the range
	 * @param ubound the upper bound of the range
	 **************************************************************************/
	public TypeDescriptor createRange(TypeDescriptor baseType, Expression lbound,
			Expression ubound) {
		//check bound and baseType type compatibility
		if(!ubound.type().isCompatible(lbound.type()))
		{
			err.semanticError(GCLError.INCOMPATIBLE_TYPES, "The bounds of this range must be of compatible types");
			return NO_TYPE;
		}
		if(!baseType.isCompatible(ubound.type()))
		{
			err.semanticError(GCLError.INCOMPATIBLE_TYPES, "The base type of this range must be compatible with the bound types");
			return NO_TYPE;
		}
		
		//make sure the bounds are constant expressions
		if(!ubound.isConstant() || !lbound.isConstant())
		{
			err.semanticError(GCLError.INCOMPATIBLE_TYPES,  "The bounds of this range must be constant expressions");
		}
		Location boundLocation = codegen.buildOperands(lbound);
		codegen.buildOperands(ubound);
		return new RangeType(baseType, (ConstantExpression)lbound, (ConstantExpression)ubound, boundLocation);
	}
	
	/***************************************************************************
	 * Write out the temination code, Including constant defs and global
	 * variables.
	 **************************************************************************/
	void finishCode() {
		codegen.genCodePostamble();
	}

	/**
	 * Get a reference to the object that maintains the current semantic
	 * (procedure nesting) level.
	 * 
	 * @return the current semantic level object.
	 */
	SemanticLevel currentLevel() {
		return currentLevel;
	}

	/**
	 * Objects of this class represent the semantic level at which the compiler
	 * is currently translating. The global level is the level of modules. Level
	 * 2 is the level of procedures. Level 3... are the levels of nested
	 * procedures. Each item declared at a level is tagged with the level number
	 * at its declaration. These numbers are used by the compiler to set up the
	 * runtime so that non-local variables (and other items) can be found at
	 * runtime.
	 */
	static class SemanticLevel {
		private int currentLevel = GLOBAL_LEVEL;// Never less than one. Current

		// procedure nest level

		/**
		 * The semantic level's integer value
		 * 
		 * @return the semantic level as an int. Never less than one.
		 */
		public int value() {
			return currentLevel;
		}

		/**
		 * Determine if the semantic level represents the global (i.e. 1) level.
		 * 
		 * @return true if the level is global. False if it is procedural at any
		 *         level.
		 */
		public boolean isGlobal() {
			return currentLevel == GLOBAL_LEVEL;
		}

		private void increment() {
			currentLevel++;
		}

		private void decrement() {
			currentLevel--;
		}

		private SemanticLevel() {
		// nothing. 
		}
		// can create a new object only within the containing class
	}

	// Data declarations for SemanticActions
	static final IntegerType INTEGER_TYPE = IntegerType.INTEGER_TYPE;
	static final BooleanType BOOLEAN_TYPE = BooleanType.BOOLEAN_TYPE;
	static final TypeDescriptor NO_TYPE = ErrorType.NO_TYPE;
	private SemanticLevel currentLevel = new SemanticLevel();

	private GCLErrorStream err = null;

	GCLErrorStream err() {
		return err;
	}

	public void init() {
		currentLevel = new SemanticLevel();
		codegen.setSemanticLevel(currentLevel());
	}
}// SemanticActions