package gcl;

public class Parser {
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _numeral = 2;
	public static final int maxT = 30;
	public static final int _option1 = 31;
	public static final int _option3 = 32;
	public static final int _option5 = 33;
	public static final int _option6 = 34;
	public static final int _option7 = 35;
	public static final int _option9 = 36;
	public static final int _option10 = 37;

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

			if (la.kind == 31) {
				CompilerOptions.listCode = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 32) {
				CompilerOptions.optimize = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 33) {
				SymbolTable.dumpAll(); 
			}
			if (la.kind == 34) {
			}
			if (la.kind == 35) {
				CompilerOptions.showMessages = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 36) {
				CompilerOptions.printAllocatedRegisters(); 
			}
			if (la.kind == 37) {
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
		while (!(la.kind == 0 || la.kind == 3)) {SynErr(31); Get();}
		module(scope);
		while (la.kind == 3) {
			scope = scope.openScope(true); 
			while (!(la.kind == 0 || la.kind == 3)) {SynErr(32); Get();}
			module(scope);
		}
		semantic.finishCode(); 
	}

	void module(SymbolTable scope) {
		Expect(3);
		Expect(1);
		definitionPart(scope);
		if (la.kind == 4) {
			Get();
			scope = scope.openScope(false);
			block(scope);
		}
		Expect(5);
	}

	void definitionPart(SymbolTable scope) {
		while (la.kind == 10 || la.kind == 11) {
			while (!(la.kind == 0 || la.kind == 10 || la.kind == 11)) {SynErr(33); Get();}
			definition(scope);
			ExpectWeak(8, 1);
		}
	}

	void block(SymbolTable scope) {
		definitionPart(scope);
		while (!(la.kind == 0 || la.kind == 6)) {SynErr(34); Get();}
		Expect(6);
		statementPart(scope);
		Expect(7);
	}

	void statementPart(SymbolTable scope) {
		while (!(StartOf(2))) {SynErr(35); Get();}
		statement(scope);
		ExpectWeak(8, 3);
		while (StartOf(4)) {
			while (!(StartOf(2))) {SynErr(36); Get();}
			statement(scope);
			ExpectWeak(8, 3);
		}
	}

	void definition(SymbolTable scope) {
		variableDefinition(scope, ParameterKind.NOT_PARAM);
	}

	void statement(SymbolTable scope) {
		if (la.kind == 14) {
			emptyStatement();
		} else if (la.kind == 15) {
			readStatement(scope);
		} else if (la.kind == 16) {
			writeStatement(scope);
		} else if (la.kind == 1) {
			assignStatement(scope);
		} else if (la.kind == 18) {
			ifStatement(scope);
		} else SynErr(37);
	}

	void variableDefinition(SymbolTable scope, ParameterKind kindOfParam) {
		TypeDescriptor type; Identifier id; 
		type = type(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling());
		semantic.declareVariable(scope, type, id, kindOfParam);
		
		while (la.kind == 9) {
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling());
			semantic.declareVariable(scope, type, id, kindOfParam);
			
		}
	}

	TypeDescriptor  type(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType; 
		if (la.kind == 10) {
			result = typeSymbol();
		} else if (la.kind == 11) {
			result = tupleType(scope);
		} else SynErr(38);
		return result;
	}

	TypeDescriptor  typeSymbol() {
		TypeDescriptor  result;
		Expect(10);
		result = integerType; 
		return result;
	}

	TupleType  tupleType(SymbolTable scope) {
		TupleType  result;
		TypeDescriptor type; Identifier id; 
		TypeList carrier = new TypeList(); 
		Expect(11);
		Expect(12);
		type = typeSymbol();
		Expect(1);
		id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		while (la.kind == 9) {
			Get();
			type = typeSymbol();
			Expect(1);
			id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		}
		Expect(13);
		result = new TupleType(carrier); 
		return result;
	}

	void emptyStatement() {
		Expect(14);
	}

	void readStatement(SymbolTable scope) {
		Expression exp; 
		Expect(15);
		exp = variableAccess(scope);
		semantic.readVariable(exp); 
		while (la.kind == 9) {
			Get();
			exp = variableAccess(scope);
			semantic.readVariable(exp); 
		}
	}

	void writeStatement(SymbolTable scope) {
		Expression exp; 
		Expect(16);
		exp = expression(scope);
		semantic.writeExpression(exp); 
		while (la.kind == 9) {
			Get();
			exp = expression(scope);
			semantic.writeExpression(exp); 
		}
		semantic.genEol(); 
	}

	void assignStatement(SymbolTable scope) {
		AssignRecord expressions = new AssignRecord(); Expression exp; 
		exp = variableAccess(scope);
		expressions.left(exp); 
		while (la.kind == 9) {
			Get();
			exp = variableAccess(scope);
			expressions.left(exp); 
		}
		Expect(17);
		exp = expression(scope);
		expressions.right(exp); 
		while (la.kind == 9) {
			Get();
			exp = expression(scope);
			expressions.right(exp); 
		}
		semantic.parallelAssign(expressions); 
	}

	void ifStatement(SymbolTable scope) {
		GCRecord ifRecord; 
		Expect(18);
		ifRecord = semantic.startIf(); 
		guardedCommandList(scope, ifRecord );
		Expect(19);
		semantic.endIf(ifRecord); 
	}

	Expression  variableAccess(SymbolTable scope) {
		Expression  result;
		SemanticItem workValue; 
		workValue = qualifiedIdentifier(scope);
		result = workValue.expectExpression(err); 
		return result;
	}

	Expression  expression(SymbolTable scope) {
		Expression  left;
		left = relationalExpr(scope);
		return left;
	}

	void guardedCommandList(SymbolTable scope, GCRecord ifRecord) {
		guardedCommand(scope, ifRecord);
		while (la.kind == 20) {
			Get();
			guardedCommand(scope, ifRecord);
		}
	}

	void guardedCommand(SymbolTable scope, GCRecord  ifRecord ) {
		Expression expr; 
		expr = expression(scope);
		semantic.ifTest(expr, ifRecord); 
		Expect(21);
		statementPart(scope);
		semantic.elseIf(ifRecord); 
	}

	Expression  relationalExpr(SymbolTable scope) {
		Expression  left;
		Expression right; RelationalOperator op; 
		left = simpleExpr(scope);
		if (la.kind == 26 || la.kind == 27) {
			op = relationalOperator();
			right = simpleExpr(scope);
			left = semantic.compareExpression(left, op, right); 
		}
		return left;
	}

	Expression  simpleExpr(SymbolTable scope) {
		Expression  left;
		Expression right; AddOperator op; left = null; 
		if (StartOf(5)) {
			if (la.kind == 22) {
				Get();
			}
			left = term(scope);
		} else if (la.kind == 23) {
			Get();
			left = term(scope);
			left = semantic.negateExpression(left); 
		} else SynErr(39);
		while (la.kind == 22 || la.kind == 23) {
			op = addOperator();
			right = term(scope);
			left = semantic.addExpression(left, op, right); 
		}
		return left;
	}

	RelationalOperator  relationalOperator() {
		RelationalOperator  op;
		op = null; 
		if (la.kind == 26) {
			Get();
			op = RelationalOperator.EQUAL; 
		} else if (la.kind == 27) {
			Get();
			op = RelationalOperator.NOT_EQUAL; 
		} else SynErr(40);
		return op;
	}

	Expression  term(SymbolTable scope) {
		Expression  left;
		Expression right; MultiplyOperator op; 
		left = factor(scope);
		while (la.kind == 28 || la.kind == 29) {
			op = multiplyOperator();
			right = factor(scope);
			left = semantic.multiplyExpression(left, op, right); 
		}
		return left;
	}

	AddOperator  addOperator() {
		AddOperator  op;
		op = null; 
		if (la.kind == 22) {
			Get();
			op = AddOperator.PLUS; 
		} else if (la.kind == 23) {
			Get();
			op = AddOperator.MINUS; 
		} else SynErr(41);
		return op;
	}

	Expression  factor(SymbolTable scope) {
		Expression  result;
		result = null; 
		if (la.kind == 1) {
			result = variableAccess(scope);
		} else if (la.kind == 2) {
			Get();
			result = new ConstantExpression (integerType, Integer.parseInt(currentToken().spelling()));
			
		} else if (la.kind == 24) {
			Get();
			result = expression(scope);
			Expect(25);
		} else if (la.kind == 12) {
			Expression exp;
			ExpressionList tupleFields = new ExpressionList();
			
			Get();
			exp = expression(scope);
			tupleFields.enter(exp); 
			while (la.kind == 9) {
				Get();
				exp = expression(scope);
				tupleFields.enter(exp); 
			}
			Expect(13);
			result = semantic.buildTuple(tupleFields); 
		} else SynErr(42);
		return result;
	}

	MultiplyOperator  multiplyOperator() {
		MultiplyOperator  op;
		op = null; 
		if (la.kind == 28) {
			Get();
			op = MultiplyOperator.TIMES; 
		} else if (la.kind == 29) {
			Get();
			op = MultiplyOperator.DIVIDE; 
		} else SynErr(43);
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
		{T,T,x,T, x,x,T,x, x,x,T,T, x,x,T,T, T,x,T,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,T, T,T,T,x, x,x,T,T, x,x,T,T, T,x,T,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,x,T,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,T, x,x,T,T, x,x,T,T, x,x,T,T, T,x,T,T, T,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,x,T,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,T,x, T,x,x,x, x,x,x,x}

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
			case 3: s = "\"module\" expected"; break;
			case 4: s = "\"private\" expected"; break;
			case 5: s = "\".\" expected"; break;
			case 6: s = "\"begin\" expected"; break;
			case 7: s = "\"end\" expected"; break;
			case 8: s = "\";\" expected"; break;
			case 9: s = "\",\" expected"; break;
			case 10: s = "\"integer\" expected"; break;
			case 11: s = "\"tuple\" expected"; break;
			case 12: s = "\"[\" expected"; break;
			case 13: s = "\"]\" expected"; break;
			case 14: s = "\"skip\" expected"; break;
			case 15: s = "\"read\" expected"; break;
			case 16: s = "\"write\" expected"; break;
			case 17: s = "\":=\" expected"; break;
			case 18: s = "\"if\" expected"; break;
			case 19: s = "\"fi\" expected"; break;
			case 20: s = "\"[]\" expected"; break;
			case 21: s = "\"->\" expected"; break;
			case 22: s = "\"+\" expected"; break;
			case 23: s = "\"-\" expected"; break;
			case 24: s = "\"(\" expected"; break;
			case 25: s = "\")\" expected"; break;
			case 26: s = "\"=\" expected"; break;
			case 27: s = "\"#\" expected"; break;
			case 28: s = "\"*\" expected"; break;
			case 29: s = "\"/\" expected"; break;
			case 30: s = "??? expected"; break;
			case 31: s = "this symbol not expected in gcl"; break;
			case 32: s = "this symbol not expected in gcl"; break;
			case 33: s = "this symbol not expected in definitionPart"; break;
			case 34: s = "this symbol not expected in block"; break;
			case 35: s = "this symbol not expected in statementPart"; break;
			case 36: s = "this symbol not expected in statementPart"; break;
			case 37: s = "invalid statement"; break;
			case 38: s = "invalid type"; break;
			case 39: s = "invalid simpleExpr"; break;
			case 40: s = "invalid relationalOperator"; break;
			case 41: s = "invalid addOperator"; break;
			case 42: s = "invalid factor"; break;
			case 43: s = "invalid multiplyOperator"; break;
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


