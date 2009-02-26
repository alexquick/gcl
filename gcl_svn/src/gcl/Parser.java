package gcl;

public class Parser {
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _numeral = 2;
	public static final int _string = 3;
	public static final int maxT = 46;
	public static final int _option1 = 47;
	public static final int _option3 = 48;
	public static final int _option5 = 49;
	public static final int _option6 = 50;
	public static final int _option7 = 51;
	public static final int _option9 = 52;
	public static final int _option10 = 53;

	static final boolean T = true;
	static final boolean x = false;
	static final int minErrDist = 2;

	public Token t;    // last recognized token
	public Token la;   // lookahead token
	int errDist = minErrDist;
	
	private Scanner scanner;
	private Errors errors;

	static final boolean DIRECT = CodegenConstants.DIRECT;
		static final boolean INDIRECT = CodegenConstants.INDIRECT;
		IntegerType integerType = SemanticActions.INTEGER_TYPE;
		BooleanType booleanType = SemanticActions.BOOLEAN_TYPE;
		TypeDescriptor noType = SemanticActions.NO_TYPE;

/*==========================================================*/


	public Parser(Scanner scanner, SemanticActions actions, SemanticActions.GCLErrorStream err) {
		this.scanner = scanner;
		this.semantic = actions;
		errors = new Errors(scanner);
		this.err = err;
	}
	
	private SemanticActions semantic;
	private SemanticActions.GCLErrorStream err;
	
	public Scanner scanner(){
		return scanner;
	}
	
	public Token currentToken(){return t;}

	void SynErr (int n) {
		if (errDist >= minErrDist) errors.SynErr(la.line, la.col, n);
		errDist = 0;
	}

	public void SemErr (String msg) {
		if (errDist >= minErrDist) errors.Error(t.line, t.col, msg);
		errDist = 0;
	}
	
	void Get () {
		for (;;) {
			t = la;
			la = scanner.Scan();
			if (la.kind <= maxT) { ++errDist; break; }

			if (la.kind == 47) {
				CompilerOptions.listCode = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 48) {
				CompilerOptions.optimize = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 49) {
				SymbolTable.dumpAll(); 
			}
			if (la.kind == 50) {
			}
			if (la.kind == 51) {
				CompilerOptions.showMessages = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 52) {
				CompilerOptions.printAllocatedRegisters(); 
			}
			if (la.kind == 53) {
			}
			la = t;
		}
	}
	
	void Expect (int n) {
		if (la.kind==n) Get(); else { SynErr(n); }
	}
	
	boolean StartOf (int s) {
		return set[s][la.kind];
	}
	
	void ExpectWeak (int n, int follow) {
		if (la.kind == n) Get();
		else {
			SynErr(n);
			while (!StartOf(follow)) Get();
		}
	}
	
	boolean WeakSeparator (int n, int syFol, int repFol) {
		boolean[] s = new boolean[maxT+1];
		if (la.kind == n) { Get(); return true; }
		else if (StartOf(repFol)) return false;
		else {
			for (int i=0; i <= maxT; i++) {
				s[i] = set[syFol][i] || set[repFol][i] || set[0][i];
			}
			SynErr(n);
			while (!s[la.kind]) Get();
			return StartOf(syFol);
		}
	}
	
	void gcl() {
		semantic.startCode();  SymbolTable scope = SymbolTable.currentScope(); 
		while (!(la.kind == 0 || la.kind == 4)) {SynErr(47); Get();}
		module(scope);
		while (la.kind == 4) {
			scope = scope.openScope(true); 
			while (!(la.kind == 0 || la.kind == 4)) {SynErr(48); Get();}
			module(scope);
		}
		semantic.finishCode(); 
	}

	void module(SymbolTable scope) {
		Expect(4);
		Expect(1);
		definitionPart(scope);
		if (la.kind == 5) {
			Get();
			scope = scope.openScope(false);
			block(scope);
		}
		Expect(6);
	}

	void definitionPart(SymbolTable scope) {
		while (StartOf(1)) {
			while (!(StartOf(2))) {SynErr(49); Get();}
			definition(scope);
			ExpectWeak(9, 3);
		}
	}

	void block(SymbolTable scope) {
		definitionPart(scope);
		while (!(la.kind == 0 || la.kind == 7)) {SynErr(50); Get();}
		Expect(7);
		statementPart(scope);
		Expect(8);
	}

	void statementPart(SymbolTable scope) {
		while (!(StartOf(4))) {SynErr(51); Get();}
		statement(scope);
		ExpectWeak(9, 5);
		while (StartOf(6)) {
			while (!(StartOf(4))) {SynErr(52); Get();}
			statement(scope);
			ExpectWeak(9, 5);
		}
	}

	void definition(SymbolTable scope) {
		if (StartOf(7)) {
			variableDefinition(scope, ParameterKind.NOT_PARAM);
		} else if (la.kind == 10) {
			Identifier id;Expression expression;
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling());
			Expect(11);
			expression = expression(scope);
			semantic.declareConstant(scope, id, expression); 
		} else if (la.kind == 12) {
			TypeDescriptor type; Identifier id; 
			Get();
			type = type(scope);
			Expect(1);
			semantic.declareType(scope, id, type); 
		} else SynErr(53);
	}

	void statement(SymbolTable scope) {
		switch (la.kind) {
		case 19: {
			emptyStatement();
			break;
		}
		case 20: {
			readStatement(scope);
			break;
		}
		case 21: {
			writeStatement(scope);
			break;
		}
		case 1: {
			assignStatement(scope);
			break;
		}
		case 23: {
			ifStatement(scope);
			break;
		}
		case 25: {
			doStatement(scope);
			break;
		}
		default: SynErr(54); break;
		}
	}

	void variableDefinition(SymbolTable scope, ParameterKind kindOfParam) {
		TypeDescriptor type; Identifier id; 
		type = type(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling());
		semantic.declareVariable(scope, type, id, kindOfParam);
		
		while (la.kind == 13) {
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling());
			semantic.declareVariable(scope, type, id, kindOfParam);
			
		}
	}

	Expression  expression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = andExpr(scope);
		while (la.kind == 29) {
			Get();
			right = andExpr(scope);
			left = semantic.evaluateBinaryOperator(left,LogicalOperator.OR,right);
		}
		return left;
	}

	TypeDescriptor  type(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType; 
		if (la.kind == 1 || la.kind == 14 || la.kind == 15) {
			result = typeSymbol(scope);
		} else if (la.kind == 16) {
			result = tupleType(scope);
		} else SynErr(55);
		return result;
	}

	TypeDescriptor  typeSymbol(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType;
		if (la.kind == 14) {
			Get();
			result = integerType; 
		} else if (la.kind == 15) {
			Get();
			result = booleanType; 
		} else if (la.kind == 1) {
			Get();
			Identifier id; id = new Identifier(currentToken().spelling()); result = (TypeDescriptor)semantic.semanticValue(scope, id).expectTypeDescriptor(null);
		} else SynErr(56);
		return result;
	}

	TupleType  tupleType(SymbolTable scope) {
		TupleType  result;
		TypeDescriptor type; Identifier id; 
		TypeList carrier = new TypeList(); 
		Expect(16);
		Expect(17);
		type = typeSymbol(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		while (la.kind == 13) {
			Get();
			type = typeSymbol(scope);
			Expect(1);
			id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		}
		Expect(18);
		result = new TupleType(carrier); 
		return result;
	}

	void emptyStatement() {
		Expect(19);
	}

	void readStatement(SymbolTable scope) {
		Expression exp; 
		Expect(20);
		exp = variableAccess(scope);
		semantic.readVariable(exp); 
		while (la.kind == 13) {
			Get();
			exp = variableAccess(scope);
			semantic.readVariable(exp); 
		}
	}

	void writeStatement(SymbolTable scope) {
		Expect(21);
		writeItem(scope);
		while (la.kind == 13) {
			Get();
			writeItem(scope);
		}
		semantic.genEol(); 
	}

	void assignStatement(SymbolTable scope) {
		AssignRecord expressions = new AssignRecord(); Expression exp; 
		exp = variableAccess(scope);
		expressions.left(exp); 
		while (la.kind == 13) {
			Get();
			exp = variableAccess(scope);
			expressions.left(exp); 
		}
		Expect(22);
		exp = expression(scope);
		expressions.right(exp); 
		while (la.kind == 13) {
			Get();
			exp = expression(scope);
			expressions.right(exp); 
		}
		semantic.parallelAssign(expressions); 
	}

	void ifStatement(SymbolTable scope) {
		GCRecord ifRecord; 
		Expect(23);
		ifRecord = semantic.startIf(); 
		guardedCommandList(scope, ifRecord );
		Expect(24);
		semantic.endIf(ifRecord); 
	}

	void doStatement(SymbolTable scope) {
		GCRecord doRecord; 
		Expect(25);
		doRecord = semantic.startDo(); 
		guardedCommandList(scope, doRecord );
		Expect(26);
		semantic.endDo(doRecord); 
	}

	Expression  variableAccess(SymbolTable scope) {
		Expression  result;
		SemanticItem workValue; 
		workValue = qualifiedIdentifier(scope);
		result = workValue.expectExpression(err); 
		return result;
	}

	void writeItem(SymbolTable scope) {
		Expression exp; 
		if (StartOf(8)) {
			exp = expression(scope);
			semantic.writeExpression(exp); 
		} else if (la.kind == 3) {
			Get();
			semantic.writeString(currentToken().spelling()); 
		} else SynErr(57);
	}

	void guardedCommandList(SymbolTable scope, GCRecord record) {
		guardedCommand(scope, record);
		while (la.kind == 27) {
			Get();
			guardedCommand(scope, record);
		}
	}

	void guardedCommand(SymbolTable scope, GCRecord  record ) {
		Expression expr; 
		expr = expression(scope);
		semantic.ifTest(expr, record); 
		Expect(28);
		statementPart(scope);
		semantic.elseIf(record); 
	}

	Expression  andExpr(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = relationalExpr(scope);
		while (la.kind == 30) {
			Get();
			right = relationalExpr(scope);
			left = semantic.evaluateBinaryOperator(left, LogicalOperator.AND,right);
		}
		return left;
	}

	Expression  relationalExpr(SymbolTable scope) {
		Expression  left;
		Expression right; RelationalOperator op; 
		left = simpleExpr(scope);
		if (StartOf(9)) {
			op = relationalOperator();
			right = simpleExpr(scope);
			left = semantic.evaluateBinaryOperator(left, op, right); 
		}
		return left;
	}

	Expression  simpleExpr(SymbolTable scope) {
		Expression  left;
		Expression right; AddOperator op; left = null; 
		if (StartOf(10)) {
			if (la.kind == 31) {
				Get();
			}
			left = term(scope);
		} else if (la.kind == 32) {
			Get();
			left = term(scope);
			left = semantic.evaluateUnaryOperator(left, UnaryOperator.NEG); 
		} else SynErr(58);
		while (la.kind == 31 || la.kind == 32) {
			op = addOperator();
			right = term(scope);
			left = semantic.evaluateBinaryOperator(left, op, right); 
		}
		return left;
	}

	RelationalOperator  relationalOperator() {
		RelationalOperator  op;
		op = null; 
		switch (la.kind) {
		case 11: {
			Get();
			op = RelationalOperator.EQUAL; 
			break;
		}
		case 38: {
			Get();
			op = RelationalOperator.NOT_EQUAL; 
			break;
		}
		case 39: {
			Get();
			op = RelationalOperator.LESS_THAN; 
			break;
		}
		case 40: {
			Get();
			op = RelationalOperator.GREATER_THAN; 
			break;
		}
		case 41: {
			Get();
			op = RelationalOperator.LT_EQUAL; 
			break;
		}
		case 42: {
			Get();
			op = RelationalOperator.GT_EQUAL; 
			break;
		}
		default: SynErr(59); break;
		}
		return op;
	}

	Expression  term(SymbolTable scope) {
		Expression  left;
		Expression right; MultiplyOperator op; 
		left = factor(scope);
		while (la.kind == 43 || la.kind == 44 || la.kind == 45) {
			op = multiplyOperator();
			right = factor(scope);
			left = semantic.evaluateBinaryOperator(left, op, right); 
		}
		return left;
	}

	AddOperator  addOperator() {
		AddOperator  op;
		op = null; 
		if (la.kind == 31) {
			Get();
			op = AddOperator.PLUS; 
		} else if (la.kind == 32) {
			Get();
			op = AddOperator.MINUS; 
		} else SynErr(60);
		return op;
	}

	Expression  factor(SymbolTable scope) {
		Expression  result;
		result = null; 
		switch (la.kind) {
		case 1: {
			result = variableAccess(scope);
			break;
		}
		case 2: {
			Get();
			result = new ConstantExpression (integerType, Integer.parseInt(currentToken().spelling()));
			
			break;
		}
		case 33: {
			Get();
			result = expression(scope);
			Expect(34);
			break;
		}
		case 35: {
			Get();
			result = new ConstantExpression(booleanType, 1); 
			break;
		}
		case 36: {
			Get();
			result = new ConstantExpression(booleanType, 0); 
			break;
		}
		case 37: {
			Get();
			result = factor(scope);
			result = semantic.evaluateUnaryOperator(result, UnaryOperator.NOT);
			break;
		}
		case 17: {
			Expression exp;
			ExpressionList tupleFields = new ExpressionList();
			
			Get();
			exp = expression(scope);
			tupleFields.enter(exp); 
			while (la.kind == 13) {
				Get();
				exp = expression(scope);
				tupleFields.enter(exp); 
			}
			Expect(18);
			result = semantic.buildTuple(tupleFields); 
			break;
		}
		default: SynErr(61); break;
		}
		return result;
	}

	MultiplyOperator  multiplyOperator() {
		MultiplyOperator  op;
		op = null; 
		if (la.kind == 43) {
			Get();
			op = MultiplyOperator.TIMES; 
		} else if (la.kind == 44) {
			Get();
			op = MultiplyOperator.DIVIDE; 
		} else if (la.kind == 45) {
			Get();
			op = MultiplyOperator.MODULUS; 
		} else SynErr(62);
		return op;
	}

	SemanticItem  qualifiedIdentifier(SymbolTable scope) {
		SemanticItem  result;
		Expect(1);
		Identifier id = new Identifier(currentToken().spelling()); 
		result = semantic.semanticValue(scope, id); 
		return result;
	}



	public void Parse() {
		la = new Token();
		la.val = "";		
		Get();
		gcl();

		Expect(0);
	}

	private boolean[][] set = {
		{T,T,x,x, T,x,x,T, x,x,T,x, T,x,T,T, T,x,x,T, T,T,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,T,x, T,x,T,T, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, x,x,x,x, x,x,T,x, T,x,T,T, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, T,T,T,T, x,x,T,x, T,x,T,T, T,x,x,T, T,T,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, T,x,x,T, T,x,T,x, T,x,T,T, T,x,x,T, T,T,x,T, T,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,x,T, T,T,x,x, x,x,x,x, x,x,x,x},
		{x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,T,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,T,x,T, T,T,x,x, x,x,x,x, x,x,x,x}

	};
} // end Parser


class Errors {
	public int count = 0;
	public String errMsgFormat = "-- line {0} col {1}: {2}";
	Scanner scanner;
	
	public Errors(Scanner scanner)
	{
		this.scanner = scanner;
	}

	private void printMsg(int line, int column, String msg) {
		StringBuffer b = new StringBuffer(errMsgFormat);
		int pos = b.indexOf("{0}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, line); }
		pos = b.indexOf("{1}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, column); }
		pos = b.indexOf("{2}");
		if (pos >= 0) b.replace(pos, pos+3, msg);
		scanner.outFile().println(b.toString());
	}
	
	public void SynErr (int line, int col, int n) {
			String s;
			switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "identifier expected"; break;
			case 2: s = "numeral expected"; break;
			case 3: s = "string expected"; break;
			case 4: s = "\"module\" expected"; break;
			case 5: s = "\"private\" expected"; break;
			case 6: s = "\".\" expected"; break;
			case 7: s = "\"begin\" expected"; break;
			case 8: s = "\"end\" expected"; break;
			case 9: s = "\";\" expected"; break;
			case 10: s = "\"constant\" expected"; break;
			case 11: s = "\"=\" expected"; break;
			case 12: s = "\"typedefinition\" expected"; break;
			case 13: s = "\",\" expected"; break;
			case 14: s = "\"integer\" expected"; break;
			case 15: s = "\"Boolean\" expected"; break;
			case 16: s = "\"tuple\" expected"; break;
			case 17: s = "\"[\" expected"; break;
			case 18: s = "\"]\" expected"; break;
			case 19: s = "\"skip\" expected"; break;
			case 20: s = "\"read\" expected"; break;
			case 21: s = "\"write\" expected"; break;
			case 22: s = "\":=\" expected"; break;
			case 23: s = "\"if\" expected"; break;
			case 24: s = "\"fi\" expected"; break;
			case 25: s = "\"do\" expected"; break;
			case 26: s = "\"od\" expected"; break;
			case 27: s = "\"[]\" expected"; break;
			case 28: s = "\"->\" expected"; break;
			case 29: s = "\"|\" expected"; break;
			case 30: s = "\"&\" expected"; break;
			case 31: s = "\"+\" expected"; break;
			case 32: s = "\"-\" expected"; break;
			case 33: s = "\"(\" expected"; break;
			case 34: s = "\")\" expected"; break;
			case 35: s = "\"true\" expected"; break;
			case 36: s = "\"false\" expected"; break;
			case 37: s = "\"~\" expected"; break;
			case 38: s = "\"#\" expected"; break;
			case 39: s = "\"<\" expected"; break;
			case 40: s = "\">\" expected"; break;
			case 41: s = "\"<=\" expected"; break;
			case 42: s = "\">=\" expected"; break;
			case 43: s = "\"*\" expected"; break;
			case 44: s = "\"/\" expected"; break;
			case 45: s = "\"\\\\\" expected"; break;
			case 46: s = "??? expected"; break;
			case 47: s = "this symbol not expected in gcl"; break;
			case 48: s = "this symbol not expected in gcl"; break;
			case 49: s = "this symbol not expected in definitionPart"; break;
			case 50: s = "this symbol not expected in block"; break;
			case 51: s = "this symbol not expected in statementPart"; break;
			case 52: s = "this symbol not expected in statementPart"; break;
			case 53: s = "invalid definition"; break;
			case 54: s = "invalid statement"; break;
			case 55: s = "invalid type"; break;
			case 56: s = "invalid typeSymbol"; break;
			case 57: s = "invalid writeItem"; break;
			case 58: s = "invalid simpleExpr"; break;
			case 59: s = "invalid relationalOperator"; break;
			case 60: s = "invalid addOperator"; break;
			case 61: s = "invalid factor"; break;
			case 62: s = "invalid multiplyOperator"; break;
				default: s = "error " + n; break;
			}
			printMsg(line, col, s);
			count++;
	}

	public void SemErr (int line, int col, int n) {
		printMsg(line, col, "error " + n);
		count++;
	}

	void semanticError(int n){
		SemErr(scanner.t.line, scanner.t.col, n); 
	}

	void semanticError(int n, int line, int col){
		SemErr(line, col, n);
	}

	void semanticError(int n, int line, int col, String message){
		scanner.outFile().print(message + ": ");
		semanticError(n, line, col);
	}

	void semanticError(int n, String message){
		scanner.outFile().print(message + ": ");
		semanticError(n);
	}

	public void Error (int line, int col, String s) {	
		printMsg(line, col, s);
		count++;
	}

	public void Exception (String s) {
		scanner.outFile().println(s); 
		System.exit(1);
	}
} // Errors

class FatalError extends RuntimeException {
	public static final long serialVersionUID = 1L;
	public FatalError(String s) { super(s); }
}


