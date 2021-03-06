package gcl;

public class Parser {
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _numeral = 2;
	public static final int _string = 3;
	public static final int maxT = 58;
	public static final int _option1 = 59;
	public static final int _option3 = 60;
	public static final int _option5 = 61;
	public static final int _option6 = 62;
	public static final int _option7 = 63;
	public static final int _option9 = 64;
	public static final int _option10 = 65;

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

			if (la.kind == 59) {
				CompilerOptions.listCode = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 60) {
				CompilerOptions.optimize = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 61) {
				SymbolTable.dumpAll(); 
			}
			if (la.kind == 62) {
			}
			if (la.kind == 63) {
				CompilerOptions.showMessages = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 64) {
				CompilerOptions.printAllocatedRegisters(); 
			}
			if (la.kind == 65) {
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
		while (!(la.kind == 0 || la.kind == 4)) {SynErr(59); Get();}
		module(scope);
		while (la.kind == 4) {
			scope = scope.openScope(true); 
			while (!(la.kind == 0 || la.kind == 4)) {SynErr(60); Get();}
			module(scope);
		}
		semantic.finishCode(); 
	}

	void module(SymbolTable scope) {
		Expect(4);
		Expect(1);
		semantic.declareModule(scope, new Identifier(currentToken().spelling())); 
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
			while (!(StartOf(2))) {SynErr(61); Get();}
			definition(scope);
			ExpectWeak(9, 3);
		}
		semantic.assertProceduresDefined(scope);
	}

	void block(SymbolTable scope) {
		definitionPart(scope);
		while (!(la.kind == 0 || la.kind == 7)) {SynErr(62); Get();}
		Expect(7);
		semantic.doLink(); 
		statementPart(scope);
		Expect(8);
	}

	void statementPart(SymbolTable scope) {
		while (!(StartOf(4))) {SynErr(63); Get();}
		statement(scope);
		ExpectWeak(9, 5);
		while (StartOf(6)) {
			while (!(StartOf(4))) {SynErr(64); Get();}
			statement(scope);
			ExpectWeak(9, 5);
		}
	}

	void definition(SymbolTable scope) {
		TypeDescriptor type =null; Identifier id =null; Expression expression =null; SemanticItem result = null;
		if (StartOf(7)) {
			variableDefinition(scope, ParameterKind.NOT_A_PARAMETER);
		} else if (la.kind == 10) {
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling());
			Expect(11);
			expression = expression(scope);
			semantic.declareConstant(scope, id, expression); 
		} else if (la.kind == 12) {
			Get();
			type = type(scope);
			Expect(1);
			id = new Identifier(currentToken().spelling());
			semantic.declareType(scope, id, type); 
		} else if (la.kind == 13) {
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling());
			SemanticItem containingSemanticItem = scope.lookupIdentifier(id).semanticRecord();
			
			Expect(14);
			Expect(1);
			id = new Identifier(currentToken().spelling());
			Procedure procedure = semantic.startProcedureDefinition(id, containingSemanticItem);
			if(procedure == null){
				scope = SymbolTable.unchained();
			}else{
				scope = procedure.scope();
			}
			
			block(scope);
			semantic.endProcedureDefinition(procedure);
			
		} else SynErr(65);
	}

	void statement(SymbolTable scope) {
		switch (la.kind) {
		case 28: {
			emptyStatement();
			break;
		}
		case 29: {
			readStatement(scope);
			break;
		}
		case 30: {
			writeStatement(scope);
			break;
		}
		case 1: case 57: {
			variableOperationStatement(scope);
			break;
		}
		case 37: {
			ifStatement(scope);
			break;
		}
		case 39: {
			doStatement(scope);
			break;
		}
		case 34: {
			forStatement(scope);
			break;
		}
		case 33: {
			returnStatement();
			break;
		}
		default: SynErr(66); break;
		}
	}

	void variableDefinition(SymbolTable scope, ParameterKind kindOfParam) {
		TypeDescriptor type; Identifier id; 
		type = type(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling());
		semantic.declareVariable(scope, type, id, kindOfParam);
		
		while (la.kind == 15) {
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
		while (la.kind == 42) {
			Get();
			right = andExpr(scope);
			left = semantic.evaluateBinaryOperator(left,LogicalOperator.OR,right);
		}
		return left;
	}

	TypeDescriptor  type(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType;
		if (la.kind == 1 || la.kind == 16 || la.kind == 17) {
			result = typeSymbol(scope);
			if (la.kind == 25 || la.kind == 27) {
				if (la.kind == 25) {
					result = rangeType(result, scope);
				} else {
					result = arrayType(result, scope);
				}
			}
		} else if (la.kind == 18) {
			result = tupleType(scope);
		} else SynErr(67);
		return result;
	}

	TypeDescriptor  typeSymbol(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType; SemanticItem item=null;
		if (la.kind == 16) {
			Get();
			result = integerType; 
		} else if (la.kind == 17) {
			Get();
			result = booleanType; 
		} else if (la.kind == 1) {
			item = qualifiedIdentifier(scope);
			result = item.expectTypeDescriptor(GCLCompiler.err); 
		} else SynErr(68);
		return result;
	}

	TypeDescriptor  rangeType(TypeDescriptor baseType, SymbolTable scope) {
		TypeDescriptor  result;
		Expression lbound = null; Expression ubound=null;
		Expect(25);
		Expect(19);
		lbound = expression(scope);
		Expect(26);
		ubound = expression(scope);
		Expect(20);
		result = semantic.createRange(baseType, lbound, ubound);
		return result;
	}

	TypeDescriptor  arrayType(TypeDescriptor baseType, SymbolTable scope) {
		TypeDescriptor  result;
		TypeDescriptor range = null; result = noType; 
		ArrayCarrier carrier = new ArrayCarrier(baseType); 
		Expect(27);
		Expect(19);
		range = typeSymbol(scope);
		Expect(20);
		carrier.push(range);
		while (la.kind == 19) {
			Get();
			range = typeSymbol(scope);
			Expect(20);
			carrier.push(range);
		}
		result = carrier.buildType();
		return result;
	}

	TupleType  tupleType(SymbolTable scope) {
		TupleType  result;
		TupleCarrier carrier = new TupleCarrier(); 
		Expect(18);
		Expect(19);
		if (la.kind == 13) {
			procedures(carrier, scope);
		} else if (la.kind == 1 || la.kind == 16 || la.kind == 17) {
			fieldsAndProcedures(carrier, scope);
		} else SynErr(69);
		Expect(20);
		result = new TupleType(carrier); 
		return result;
	}

	SemanticItem  qualifiedIdentifier(SymbolTable scope) {
		SemanticItem  result;
		Expect(1);
		Identifier id = new Identifier(currentToken().spelling()); 
		result = semantic.semanticValue(scope, id); 
		if (la.kind == 6) {
			Get();
			Expect(1);
			Identifier newId = new Identifier(currentToken().spelling());
			result = semantic.semanticValue(scope, newId, result); 
		}
		return result;
	}

	void procedures(TupleCarrier carrier, SymbolTable scope) {
		procedureDeclaration(carrier, scope);
		while (la.kind == 15) {
			Get();
			procedureDeclaration(carrier, scope);
		}
	}

	void fieldsAndProcedures(TupleCarrier carrier, SymbolTable scope) {
		TypeDescriptor type; Identifier id;
		type = typeSymbol(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		if (la.kind == 15) {
			moreFieldsAndProcedures(carrier, scope);
		}
	}

	void moreFieldsAndProcedures(TupleCarrier carrier, SymbolTable scope) {
		TypeDescriptor type; Identifier id;
		Expect(15);
		if (la.kind == 1 || la.kind == 16 || la.kind == 17) {
			type = typeSymbol(scope);
			Expect(1);
			id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		} else if (la.kind == 13) {
			procedures(carrier, scope);
		} else SynErr(70);
		if (la.kind == 15) {
			moreFieldsAndProcedures(carrier, scope);
		}
	}

	void procedureDeclaration(TupleCarrier carrier, SymbolTable scope) {
		Procedure procedure = null;
		Expect(13);
		Expect(1);
		Identifier id = new Identifier(currentToken().spelling());
		procedure = semantic.startProcedureDeclaration(scope, id); 
		parameterList(procedure);
		semantic.endProcedureDeclaration(); carrier.enterProcedure(procedure, id); 
	}

	void parameterList(Procedure procedure) {
		Expect(21);
		if (la.kind == 23 || la.kind == 24) {
			parameterDefinition(procedure);
			while (la.kind == 9) {
				Get();
				parameterDefinition(procedure);
			}
		}
		Expect(22);
	}

	void parameterDefinition(Procedure procedure) {
		if (la.kind == 23) {
			Get();
			variableDefinition(procedure.scope(), ParameterKind.REFERENCE_PARAMETER);
		} else if (la.kind == 24) {
			Get();
			variableDefinition(procedure.scope(), ParameterKind.VALUE_PARAMETER);
		} else SynErr(71);
	}

	void emptyStatement() {
		Expect(28);
	}

	void readStatement(SymbolTable scope) {
		Expression exp; 
		Expect(29);
		exp = variableAccess(scope);
		semantic.readVariable(exp); 
		while (la.kind == 15) {
			Get();
			exp = variableAccess(scope);
			semantic.readVariable(exp); 
		}
	}

	void writeStatement(SymbolTable scope) {
		Expect(30);
		writeItem(scope);
		while (la.kind == 15) {
			Get();
			writeItem(scope);
		}
		semantic.genEol(); 
	}

	void variableOperationStatement(SymbolTable scope) {
		Expression exp; 
		exp = variableAccess(scope);
		if (la.kind == 15 || la.kind == 31) {
			assignStatement(exp, scope);
		} else if (la.kind == 32) {
			procedureInvocation(exp, scope);
		} else SynErr(72);
	}

	void ifStatement(SymbolTable scope) {
		GCRecord ifRecord; 
		Expect(37);
		ifRecord = semantic.startIf(); 
		guardedCommandList(scope, ifRecord );
		Expect(38);
		semantic.endIf(ifRecord); 
	}

	void doStatement(SymbolTable scope) {
		GCRecord doRecord; 
		Expect(39);
		doRecord = semantic.startDo(); 
		guardedCommandList(scope, doRecord );
		Expect(40);
		semantic.endDo(doRecord); 
	}

	void forStatement(SymbolTable scope) {
		GCRecord forallRecord; Expression control;  
		Expect(34);
		control = variableAccess(scope);
		Expect(35);
		forallRecord = semantic.beginFor(control); 
		statementPart(scope);
		Expect(36);
		semantic.endFor(control, forallRecord); 
	}

	void returnStatement() {
		Expect(33);
		semantic.doReturn(); 
	}

	Expression  variableAccess(SymbolTable scope) {
		Expression  result;
		SemanticItem workValue; result = null;
		if (la.kind == 1) {
			workValue = qualifiedIdentifier(scope);
			result = workValue.expectExpression(err); 
		} else if (la.kind == 57) {
			Get();
			result = semantic.resolveThis();
		} else SynErr(73);
		if (la.kind == 14 || la.kind == 19) {
			while (la.kind == 14 || la.kind == 19) {
				result = subscriptsAndComponents(scope, result);
			}
		}
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
		} else SynErr(74);
	}

	void assignStatement(Expression exp, SymbolTable scope) {
		AssignRecord expressions = new AssignRecord();
		expressions.left(exp);
		while (la.kind == 15) {
			Get();
			exp = variableAccess(scope);
			expressions.left(exp); 
		}
		Expect(31);
		exp = expression(scope);
		expressions.right(exp); 
		while (la.kind == 15) {
			Get();
			exp = expression(scope);
			expressions.right(exp); 
		}
		semantic.parallelAssign(expressions); 
	}

	void procedureInvocation(Expression tupleExpression, SymbolTable scope) {
		Identifier procedureName; ExpressionList arguments = new ExpressionList(); Expression argument;
		Expect(32);
		Expect(1);
		procedureName = new Identifier(currentToken().spelling());
		Expect(21);
		if (StartOf(8)) {
			argument = expression(scope);
			arguments.enter(argument);
			while (la.kind == 15) {
				Get();
				argument = expression(scope);
				arguments.enter(argument);
			}
		}
		Expect(22);
		semantic.callProcedure(tupleExpression, procedureName, arguments);
	}

	void guardedCommandList(SymbolTable scope, GCRecord record) {
		guardedCommand(scope, record);
		while (la.kind == 41) {
			Get();
			guardedCommand(scope, record);
		}
	}

	void guardedCommand(SymbolTable scope, GCRecord  record ) {
		Expression expr; 
		expr = expression(scope);
		semantic.ifTest(expr, record); 
		Expect(35);
		statementPart(scope);
		semantic.elseIf(record); 
	}

	Expression  andExpr(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = relationalExpr(scope);
		while (la.kind == 43) {
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
			if (la.kind == 44) {
				Get();
			}
			left = term(scope);
		} else if (la.kind == 45) {
			Get();
			left = term(scope);
			left = semantic.evaluateUnaryOperator(left, UnaryOperator.NEG); 
		} else SynErr(75);
		while (la.kind == 44 || la.kind == 45) {
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
		case 49: {
			Get();
			op = RelationalOperator.NOT_EQUAL; 
			break;
		}
		case 50: {
			Get();
			op = RelationalOperator.LESS_THAN; 
			break;
		}
		case 51: {
			Get();
			op = RelationalOperator.GREATER_THAN; 
			break;
		}
		case 52: {
			Get();
			op = RelationalOperator.LT_EQUAL; 
			break;
		}
		case 53: {
			Get();
			op = RelationalOperator.GT_EQUAL; 
			break;
		}
		default: SynErr(76); break;
		}
		return op;
	}

	Expression  term(SymbolTable scope) {
		Expression  left;
		Expression right; MultiplyOperator op; 
		left = factor(scope);
		while (la.kind == 54 || la.kind == 55 || la.kind == 56) {
			op = multiplyOperator();
			right = factor(scope);
			left = semantic.evaluateBinaryOperator(left, op, right); 
		}
		return left;
	}

	AddOperator  addOperator() {
		AddOperator  op;
		op = null; 
		if (la.kind == 44) {
			Get();
			op = AddOperator.PLUS; 
		} else if (la.kind == 45) {
			Get();
			op = AddOperator.MINUS; 
		} else SynErr(77);
		return op;
	}

	Expression  factor(SymbolTable scope) {
		Expression  result;
		result = null; 
		switch (la.kind) {
		case 1: case 57: {
			result = variableAccess(scope);
			break;
		}
		case 2: {
			Get();
			result = new ConstantExpression (integerType, Integer.parseInt(currentToken().spelling()));
			
			break;
		}
		case 21: {
			Get();
			result = expression(scope);
			Expect(22);
			break;
		}
		case 46: {
			Get();
			result = new ConstantExpression(booleanType, 1); 
			break;
		}
		case 47: {
			Get();
			result = new ConstantExpression(booleanType, 0); 
			break;
		}
		case 48: {
			Get();
			result = factor(scope);
			result = semantic.evaluateUnaryOperator(result, UnaryOperator.NOT);
			break;
		}
		case 19: {
			Expression exp;
			ExpressionList tupleFields = new ExpressionList();
			
			Get();
			exp = expression(scope);
			tupleFields.enter(exp); 
			while (la.kind == 15) {
				Get();
				exp = expression(scope);
				tupleFields.enter(exp); 
			}
			Expect(20);
			result = semantic.buildTuple(tupleFields); 
			break;
		}
		default: SynErr(78); break;
		}
		return result;
	}

	MultiplyOperator  multiplyOperator() {
		MultiplyOperator  op;
		op = null; 
		if (la.kind == 54) {
			Get();
			op = MultiplyOperator.TIMES; 
		} else if (la.kind == 55) {
			Get();
			op = MultiplyOperator.DIVIDE; 
		} else if (la.kind == 56) {
			Get();
			op = MultiplyOperator.MODULUS; 
		} else SynErr(79);
		return op;
	}

	Expression  subscriptsAndComponents(SymbolTable scope, Expression workValue) {
		Expression  result;
		Identifier id; result = null;
		if (la.kind == 14) {
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling()); result = semantic.extractTupleField(workValue, id);
		} else if (la.kind == 19) {
			Get();
			result = expression(scope);
			Expect(20);
			result = semantic.extractArrayElement(workValue, result); 
		} else SynErr(80);
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
		{T,T,x,x, T,x,x,T, x,x,T,x, T,T,x,x, T,T,T,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, x,T,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x},
		{x,T,x,x, x,x,x,x, x,x,T,x, T,T,x,x, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, x,x,x,x, x,x,T,x, T,T,x,x, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, T,T,T,T, x,x,T,x, T,T,x,x, T,T,T,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, x,T,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x},
		{T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, x,T,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x},
		{T,T,x,x, T,x,x,T, T,x,T,x, T,T,x,x, T,T,T,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, T,T,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, x,T,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,T, T,x,x,x, x,x,x,x, x,T,x,x},
		{x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,T,T, T,T,x,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,T,T, T,x,x,x, x,x,x,x, x,T,x,x}

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
			case 13: s = "\"procedure\" expected"; break;
			case 14: s = "\"@\" expected"; break;
			case 15: s = "\",\" expected"; break;
			case 16: s = "\"integer\" expected"; break;
			case 17: s = "\"Boolean\" expected"; break;
			case 18: s = "\"tuple\" expected"; break;
			case 19: s = "\"[\" expected"; break;
			case 20: s = "\"]\" expected"; break;
			case 21: s = "\"(\" expected"; break;
			case 22: s = "\")\" expected"; break;
			case 23: s = "\"reference\" expected"; break;
			case 24: s = "\"value\" expected"; break;
			case 25: s = "\"range\" expected"; break;
			case 26: s = "\"..\" expected"; break;
			case 27: s = "\"array\" expected"; break;
			case 28: s = "\"skip\" expected"; break;
			case 29: s = "\"read\" expected"; break;
			case 30: s = "\"write\" expected"; break;
			case 31: s = "\":=\" expected"; break;
			case 32: s = "\"!\" expected"; break;
			case 33: s = "\"return\" expected"; break;
			case 34: s = "\"forall\" expected"; break;
			case 35: s = "\"->\" expected"; break;
			case 36: s = "\"llarof\" expected"; break;
			case 37: s = "\"if\" expected"; break;
			case 38: s = "\"fi\" expected"; break;
			case 39: s = "\"do\" expected"; break;
			case 40: s = "\"od\" expected"; break;
			case 41: s = "\"[]\" expected"; break;
			case 42: s = "\"|\" expected"; break;
			case 43: s = "\"&\" expected"; break;
			case 44: s = "\"+\" expected"; break;
			case 45: s = "\"-\" expected"; break;
			case 46: s = "\"true\" expected"; break;
			case 47: s = "\"false\" expected"; break;
			case 48: s = "\"~\" expected"; break;
			case 49: s = "\"#\" expected"; break;
			case 50: s = "\"<\" expected"; break;
			case 51: s = "\">\" expected"; break;
			case 52: s = "\"<=\" expected"; break;
			case 53: s = "\">=\" expected"; break;
			case 54: s = "\"*\" expected"; break;
			case 55: s = "\"/\" expected"; break;
			case 56: s = "\"\\\\\" expected"; break;
			case 57: s = "\"this\" expected"; break;
			case 58: s = "??? expected"; break;
			case 59: s = "this symbol not expected in gcl"; break;
			case 60: s = "this symbol not expected in gcl"; break;
			case 61: s = "this symbol not expected in definitionPart"; break;
			case 62: s = "this symbol not expected in block"; break;
			case 63: s = "this symbol not expected in statementPart"; break;
			case 64: s = "this symbol not expected in statementPart"; break;
			case 65: s = "invalid definition"; break;
			case 66: s = "invalid statement"; break;
			case 67: s = "invalid type"; break;
			case 68: s = "invalid typeSymbol"; break;
			case 69: s = "invalid tupleType"; break;
			case 70: s = "invalid moreFieldsAndProcedures"; break;
			case 71: s = "invalid parameterDefinition"; break;
			case 72: s = "invalid variableOperationStatement"; break;
			case 73: s = "invalid variableAccess"; break;
			case 74: s = "invalid writeItem"; break;
			case 75: s = "invalid simpleExpr"; break;
			case 76: s = "invalid relationalOperator"; break;
			case 77: s = "invalid addOperator"; break;
			case 78: s = "invalid factor"; break;
			case 79: s = "invalid multiplyOperator"; break;
			case 80: s = "invalid subscriptsAndComponents"; break;
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


