package edu.citadel.cprl

import edu.citadel.compiler.ErrorHandler
import edu.citadel.compiler.InternalCompilerException
import edu.citadel.compiler.ParserException
import edu.citadel.compiler.Position
import edu.citadel.cprl.ast.*
import java.util.*

/**
 * This class uses recursive descent to perform syntax analysis of
 * the CPRL source language.
 *
 * @constructor Construct a parser with the specified scanner,
 *              identifier table, and error handler.
 */
class Parser(
    private val scanner: Scanner,
    private val idTable: IdTable,
    private val errorHandler: ErrorHandler,
) {
    private val loopContext = LoopContext()
    private val subprogramContext = SubprogramContext()

    /** Symbols that can follow a statement. */
    private val stmtFollowers = EnumSet.of(
        Symbol.identifier, Symbol.ifRW, Symbol.elseRW, Symbol.whileRW, Symbol.loopRW, Symbol.exitRW, Symbol.readRW,
        Symbol.writeRW, Symbol.writelnRW, Symbol.leftBrace, Symbol.rightBrace, Symbol.returnRW
    )

    /** Symbols that can follow a subprogram declaration. */
    private val subprogDeclFollowers = EnumSet.of(
        Symbol.procRW, Symbol.funRW, Symbol.EOF
    )

    /** Symbols that can follow a factor. */
    private val factorFollowers = EnumSet.of(
        Symbol.semicolon, Symbol.loopRW, Symbol.thenRW, Symbol.rightParen,
        Symbol.andRW, Symbol.orRW, Symbol.equals, Symbol.notEqual,
        Symbol.lessThan, Symbol.lessOrEqual, Symbol.greaterThan, Symbol.greaterOrEqual,
        Symbol.plus, Symbol.minus, Symbol.times, Symbol.divide,
        Symbol.modRW, Symbol.rightBracket, Symbol.comma
    )

    /** Symbols that can follow an initial declaration (computed property).
     *  Set is computed dynamically based on the scope level. */
    private val initialDeclFollowers: Set<Symbol>
        get() {
            // An initial declaration can always be followed by another
            // initial declaration, regardless of the scope level.
            val followers = EnumSet.of(Symbol.constRW, Symbol.varRW, Symbol.typeRW)

            if (idTable.scopeLevel == ScopeLevel.GLOBAL)
                followers.addAll(EnumSet.of(Symbol.procRW, Symbol.funRW))
            else {
                followers.addAll(stmtFollowers)
                followers.remove(Symbol.elseRW)
            }

            return followers
        }

    /**
     * Parse the following grammar rule:<br>
     * `program = initialDecls subprogramDecls .`
     *
     * @return the parsed program.  Returns a program with an empty list
     *         of initial declarations and an empty list of subprogram
     *         declarations if parsing fails.
     */
    fun parseProgram(): Program {
        try {
            val initialDecls = parseInitialDecls()
            val subprogDecls = parseSubprogramDecls()

            if (scanner.symbol != Symbol.EOF) {
                val errorMsg =
                    "Expecting \"${Symbol.procRW}\" or \"${Symbol.funRW}\" but found \"${scanner.token}\" instead."
                throw error(errorMsg)
            }

            return Program(initialDecls, subprogDecls)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(EnumSet.of(Symbol.EOF))
            return Program()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `initialDecls = { initialDecl } .`
     *
     * @return the list of initial declarations.
     */
    private fun parseInitialDecls(): List<InitialDecl> {
        val initialDecls = ArrayList<InitialDecl>(10)

        while (scanner.symbol.isInitialDeclStarter())
            initialDecls.add(parseInitialDecl())

        return initialDecls
    }

    /**
     * Parse the following grammar rule:<br>
     * `initialDecl = constDecl |  varDecl | typeDecl.`
     *
     * @return the parsed initial declaration.  Returns an
     *         empty initial declaration if parsing fails.
     */
    private fun parseInitialDecl(): InitialDecl {
        return when (scanner.symbol) {
            Symbol.constRW -> parseConstDecl()
            Symbol.varRW -> parseVarDecl()
            Symbol.typeRW -> parseTypeDecl()
            else -> throw internalError("Invalid initial declaration")
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `constDecl = "const" constId ":=" literal ";" .`
     *
     * @return the parsed constant declaration.  Returns an
     *         empty initial declaration if parsing fails.
     */
    private fun parseConstDecl(): InitialDecl {
        try {
            match(Symbol.constRW)
            val constId = scanner.token
            match(Symbol.identifier)
            match(Symbol.assign)
            val literal = parseLiteral()
            match(Symbol.semicolon)
            val constDecl = ConstDecl(constId, Type.typeOf(literal), literal)

            idTable.add(constDecl)
            return constDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
            return EmptyInitialDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `literal = intLiteral | charLiteral | stringLiteral | "true" | "false" .`
     *
     * @return the parsed literal token.  Returns a default token if parsing fails.
     */
    private fun parseLiteral(): Token {
        try {
            if (scanner.symbol.isLiteral()) {
                val literal = scanner.token
                matchCurrentSymbol()
                return literal
            } else
                throw error("Invalid literal expression.")
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
            return Token()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `varDecl = "var" identifiers ":" typeName [ ":=" constValue] ";" .`
     *
     * @return the parsed variable declaration.  Returns an
     *         empty initial declaration if parsing fails.
     */
    private fun parseVarDecl(): InitialDecl {
        try {
            match(Symbol.varRW)
            val identifiers = parseIdentifiers()
            match(Symbol.colon)
            val varType = parseTypeName()

            var initialValue: ConstValue? = null
            if (scanner.symbol == Symbol.assign) {
                matchCurrentSymbol()
                val constValue = parseConstValue()
                if (constValue is ConstValue)
                    initialValue = constValue
            }

            match(Symbol.semicolon)
            val varDecl = VarDecl(identifiers, varType, initialValue, idTable.scopeLevel)

            for (decl in varDecl.singleVarDecls)
                idTable.add(decl)

            return varDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
            return EmptyInitialDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `identifiers = identifier { "," identifier } .`
     *
     * @return the list of identifier tokens.  Returns an empty list if parsing fails.
     */
    private fun parseIdentifiers(): List<Token> {
        try {
            val identifiers = ArrayList<Token>(10)
            var idToken = scanner.token
            match(Symbol.identifier)
            identifiers.add(idToken)

            while (scanner.symbol == Symbol.comma) {
                matchCurrentSymbol()
                idToken = scanner.token
                match(Symbol.identifier)
                identifiers.add(idToken)
            }

            return identifiers
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(setOf(Symbol.colon))
            return emptyList()   // should never execute
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `typeDecl = arrayTypeDecl | recordTypeDecl | stringTypeDecl .`
     *
     * @return the parsed type declaration.  Returns an
     *         empty initial declaration if parsing fails.
     */
    private fun parseTypeDecl(): InitialDecl {
        assert(scanner.symbol == Symbol.typeRW)

        try {
            return when (scanner.lookahead(4).symbol) {
                Symbol.arrayRW -> parseArrayTypeDecl()
                Symbol.recordRW -> parseRecordTypeDecl()
                Symbol.stringRW -> parseStringTypeDecl()
                else -> {
                    val errorPos = scanner.lookahead(4).position
                    throw error(errorPos, "Invalid type declaration.")
                }
            }
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            matchCurrentSymbol()
            recover(initialDeclFollowers)
            return EmptyInitialDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `arrayTypeDecl = "type" typeId "=" "array" "[" intConstValue "]"
     *                  "of" typeName ";" .`
     *
     * @return the parsed array type declaration.  Returns
     *         an empty initial declaration if parsing fails.
     */
    private fun parseArrayTypeDecl(): InitialDecl {
        try {
            match(Symbol.typeRW)

            val typeId = scanner.token
            match(Symbol.identifier)

            match(Symbol.equals)
            match(Symbol.arrayRW)
            match(Symbol.leftBracket)

            var numElements: ConstValue
            try {
                numElements = parseIntConstValue()
                match(Symbol.rightBracket)
            } catch (e: ParserException) {
                if (scanner.symbol == Symbol.rightBracket) {
                    errorHandler.reportError(e)
                    matchCurrentSymbol()    // treat "[]" as "[intConst]" in this context
                    return EmptyInitialDecl
                } else
                    throw e
            }

            match(Symbol.ofRW)
            val elementType = parseTypeName()
            match(Symbol.semicolon)

            val arrayTypeDecl = ArrayTypeDecl(typeId, elementType, numElements)

            idTable.add(arrayTypeDecl)

            return arrayTypeDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
            return EmptyInitialDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `recordTypeDecl = "type" typeId "=" "record" "{" fieldDecls "}" ";" .`
     *
     * @return the parsed record type declaration.  Returns
     *         an empty initial declaration if parsing fails.
     */
    private fun parseRecordTypeDecl(): InitialDecl {
        try {
            match(Symbol.typeRW)
            val typeId = scanner.token
            match(Symbol.identifier)
            match(Symbol.equals)
            match(Symbol.recordRW)
            match(Symbol.leftBrace)

            val fieldDecls: List<FieldDecl>
            try {
                idTable.openScope(ScopeLevel.RECORD)
                fieldDecls = parseFieldDecls()
            } finally {
                idTable.closeScope()
            }

            match(Symbol.rightBrace)
            match(Symbol.semicolon)

            val typeDecl = RecordTypeDecl(typeId, fieldDecls)
            idTable.add(typeDecl)
            return typeDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
            return EmptyInitialDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `fieldDecls = { fieldDecl } .`
     *
     * @return a (possibly empty) list of field declarations.
     */
    private fun parseFieldDecls(): List<FieldDecl> {
        val fieldDecls = ArrayList<FieldDecl>(10)
        while (scanner.symbol != Symbol.rightBrace) {
            val fieldDecl = parseFieldDecl()
            if (fieldDecl != null) {
                fieldDecls.add(fieldDecl)
            }
        }
        return fieldDecls
    }

    /**
     * Parse the following grammar rule:<br>
     * `fieldDecl = fieldId ":" typeName ";" .`
     *
     * @return the parsed field declaration.  Returns null if parsing fails.
     */
    private fun parseFieldDecl(): FieldDecl? {
        try {
            val fieldId = scanner.token
            match(Symbol.identifier)
            match(Symbol.colon)
            val type = parseTypeName()
            match(Symbol.semicolon)

            val fieldDecl = FieldDecl(fieldId, type)
            idTable.add(fieldDecl)

            return fieldDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
            return null
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `stringTypeDecl = "type" typeId "=" "string" "[" intConstValue "]" ";" .`
     *
     * @return the parsed string type declaration.  Returns
     *         an empty initial declaration if parsing fails.
     */
    private fun parseStringTypeDecl(): InitialDecl {
        try {
            match(Symbol.typeRW)
            val typeId = scanner.token
            match(Symbol.identifier)
            match(Symbol.equals)
            match(Symbol.stringRW)
            match(Symbol.leftBracket)
            val capacity = parseIntConstValue()
            match(Symbol.rightBracket)
            match(Symbol.semicolon)

            val stringTypeDecl = StringTypeDecl(typeId, capacity)
            idTable.add(stringTypeDecl)

            return stringTypeDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
            return EmptyInitialDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `typeName = "Integer" | "Boolean" | "Char" | typeId .`
     *
     * @return the parsed named type.  Returns `Type.UNKNOWN` if parsing fails.
     */
    private fun parseTypeName(): Type {
        var type = Type.UNKNOWN

        try {
            when (scanner.symbol) {
                Symbol.IntegerRW -> {
                    type = Type.Integer
                    matchCurrentSymbol()
                }

                Symbol.BooleanRW -> {
                    type = Type.Boolean
                    matchCurrentSymbol()
                }

                Symbol.CharRW -> {
                    type = Type.Char
                    matchCurrentSymbol()
                }

                Symbol.identifier -> {
                    val typeId = scanner.token
                    matchCurrentSymbol()
                    val decl = idTable[typeId.text]

                    if (decl != null) {
                        if (decl is ArrayTypeDecl || decl is RecordTypeDecl || decl is StringTypeDecl)
                            type = decl.type
                        else {
                            val errorMsg = "Identifier \"$typeId\" is not a valid type name."
                            throw error(typeId.position, errorMsg)
                        }
                    } else {
                        val errorMsg = "Identifier \"$typeId\" has not been declared."
                        throw error(typeId.position, errorMsg)
                    }
                }

                else -> throw error("Invalid type name.")
            }
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(
                EnumSet.of(
                    Symbol.semicolon,
                    Symbol.comma,
                    Symbol.rightParen,
                    Symbol.leftBrace
                )
            )
        }

        return type
    }

    /**
     * Parse the following grammar rule:<br>
     * `subprogramDecls = { subprogramDecl } .`
     *
     * @return the list of subprogram declarations.
     */
    private fun parseSubprogramDecls(): List<SubprogramDecl> {
        val subprogramDecls = ArrayList<SubprogramDecl>(10)
        while (scanner.symbol.isSubprogramDeclStarter()) {
            subprogramDecls.add(parseSubprogramDecl())
        }
        return subprogramDecls
    }

    /**
     * Parse the following grammar rule:<br>
     * `subprogramDecl = procedureDecl | functionDecl .`
     *
     * @return the parsed subprogram declaration.  Returns an
     *         empty subprogram declaration if parsing fails.
     */
    private fun parseSubprogramDecl(): SubprogramDecl {
        return when (scanner.symbol) {
            Symbol.procRW -> parseProcedureDecl()
            Symbol.funRW -> parseFunctionDecl()
            else -> throw internalError("Invalid subprogram declaration")
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `procedureDecl = "proc" procId "(" [ formalParameters } ")"
     *                  "{" initialDecls statements "}" .`
     *
     * @return the parsed procedure declaration.  Returns an
     *         empty subprogram declaration if parsing fails.
     */
    private fun parseProcedureDecl(): SubprogramDecl {
        try {
            match(Symbol.procRW)
            val procId = scanner.token
            match(Symbol.identifier)

            val procDecl = ProcedureDecl(procId)

            idTable.add(procDecl)
            match(Symbol.leftParen)

            try {
                idTable.openScope(ScopeLevel.LOCAL)

                if (scanner.symbol.isParameterDeclStarter())
                    procDecl.formalParams = parseFormalParameters()

                match(Symbol.rightParen)
                match(Symbol.leftBrace)

                procDecl.initialDecls = parseInitialDecls()

                subprogramContext.beginSubprogramDecl(procDecl)
                procDecl.statements = parseStatements()
                subprogramContext.endSubprogramDecl()
            } finally {
                idTable.closeScope()
            }

            match(Symbol.rightBrace)
            return procDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(subprogDeclFollowers)
            return EmptySubprogramDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `functionDecl = "fun" funId "(" [ formalParameters ] ")" ":" typeName
     *                 "{" initialDecls statements "}" .`
     *
     * @return the parsed function declaration.  Returns an
     *         empty subprogram declaration if parsing fails.
     */
    private fun parseFunctionDecl(): SubprogramDecl {
        try {
            match(Symbol.funRW)
            val funcId = scanner.token
            match(Symbol.identifier)

            val funcDecl = FunctionDecl(funcId)

            idTable.add(funcDecl)
            match(Symbol.leftParen)

            try {
                idTable.openScope(ScopeLevel.LOCAL)

                if (scanner.symbol.isParameterDeclStarter())
                    funcDecl.formalParams = parseFormalParameters()

                match(Symbol.rightParen)
                match(Symbol.colon)

                funcDecl.type = parseTypeName()

                match(Symbol.leftBrace)

                funcDecl.initialDecls = parseInitialDecls()

                subprogramContext.beginSubprogramDecl(funcDecl)
                funcDecl.statements = parseStatements()
                subprogramContext.endSubprogramDecl()
            } finally {
                idTable.closeScope()
            }

            match(Symbol.rightBrace)
            return funcDecl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(subprogDeclFollowers)
            return EmptySubprogramDecl
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `formalParameters = parameterDecl { "," parameterDecl } .`
     *
     * @return a list of formal parameter declarations.
     */
    private fun parseFormalParameters(): List<ParameterDecl> {
        val paramDecls = ArrayList<ParameterDecl>(10)

        val decl = parseParameterDecl()
        if (decl != null) {
            paramDecls.add(decl)
        }

        while (scanner.symbol == Symbol.comma) {
            matchCurrentSymbol()
            val decl = parseParameterDecl()
            if (decl != null) {
                paramDecls.add(decl)
            }
        }

        return paramDecls
    }

    /**
     * Parse the following grammar rule:<br>
     * `parameterDecl = [ "var" ] paramId ":" typeName .`
     *
     * @return the parsed parameter declaration.  Returns null if parsing fails.
     */
    private fun parseParameterDecl(): ParameterDecl? {
        try {
            var isVarParam = false
            if (scanner.symbol == Symbol.varRW) {
                matchCurrentSymbol()
                isVarParam = true
            }
            val paramId = scanner.token
            match(Symbol.identifier)
            match(Symbol.colon)
            val type = parseTypeName()

            val decl = ParameterDecl(paramId, type, isVarParam)
            idTable.add(decl)
            return decl
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(EnumSet.of(Symbol.comma, Symbol.rightParen))
            return null
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `statements = { statement } .`
     *
     * @return a list of statements.
     */
    private fun parseStatements(): List<Statement> {
        val statements = ArrayList<Statement>(10)
        while (scanner.symbol.isStmtStarter()) {
            statements.add(parseStatement())
        }
        return statements
    }

    /**
     * Parse the following grammar rule:<br>
     * `statement = assignmentStmt | procedureCallStmt | compoundStmt | ifStmt
     *            | loopStmt | exitStmt | readStmt | writeStmt | writelnStmt
     *            | returnStmt .`
     *
     * @return the parsed statement.  Returns an empty statement if parsing fails.
     */
    private fun parseStatement(): Statement {
        // assumes that scanner.symbol can start a statement
        assert(scanner.symbol.isStmtStarter()) { "Invalid statement." }

        try {
            val stmt: Statement
            val symbol = scanner.symbol

            when (symbol) {
                Symbol.identifier -> {
                    // Handle identifiers based on how they are declared,
                    // or use the lookahead symbol if not declared.
                    val idStr = scanner.text
                    val decl = idTable[idStr]

                    stmt = if (decl != null) {
                        when (decl) {
                            is VariableDecl -> parseAssignmentStmt()
                            is ProcedureDecl -> parseProcedureCallStmt()
                            else -> throw error("Identifier \"$idStr\" cannot start a statement.")
                        }
                    } else {
                        // make parsing decision using lookahead symbol
                        val symbol2 = scanner.lookahead(2).symbol
                        when (symbol2) {
                            Symbol.leftParen -> parseProcedureCallStmt()
                            in setOf(Symbol.assign, Symbol.leftBracket, Symbol.dot) -> parseAssignmentStmt()
                            else -> throw error("Invalid statement.")
                        }
                    }
                }

                Symbol.leftBrace -> stmt = parseCompoundStmt()
                Symbol.ifRW -> stmt = parseIfStmt()
                Symbol.loopRW, Symbol.whileRW -> stmt = parseLoopStmt()
                Symbol.exitRW -> stmt = parseExitStmt()
                Symbol.readRW -> stmt = parseReadStmt()
                Symbol.writeRW -> stmt = parseWriteStmt()
                Symbol.writelnRW -> stmt = parseWritelnStmt()
                Symbol.returnRW -> stmt = parseReturnStmt()
                else -> throw internalError("Invalid statement.")
            }

            return stmt
        } catch (e: ParserException) {
            errorHandler.reportError(e)

            // Error recovery here is complicated for identifiers since they can both
            // start a statement and appear elsewhere in the statement.  (Consider,
            // for example, an assignment statement or a procedure call statement.)
            // Since the most common error is to declare or reference an identifier
            // incorrectly, we will assume that this is the case and advance to the
            // next semicolon (which hopefully ends the erroneous statement) before
            // performing error recovery.
            scanner.advanceTo(EnumSet.of(Symbol.semicolon, Symbol.rightBrace))
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `assignmentStmt = variable ":=" expression ";" .`
     *
     * @return the parsed assignment statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseAssignmentStmt(): Statement {
        try {
            val variable = parseVariable()

            val assignPosition = scanner.position
            try {
                match(Symbol.assign)
            } catch (e: ParserException) {
                if (scanner.symbol == Symbol.equals) {
                    errorHandler.reportError(e)
                    matchCurrentSymbol()    // treat "=" as ":=" in this context
                } else
                    throw e
            }

            val expression = parseExpression()
            match(Symbol.semicolon)

            return if (variable == null)
                EmptyStatement
            else
                AssignmentStmt(variable, expression, assignPosition)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `compoundStmt = "{" statements "}" .`
     *
     * @return the parsed compound statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseCompoundStmt(): Statement {
        try {
            match(Symbol.leftBrace)
            val statements = parseStatements()
            match(Symbol.rightBrace)

            return CompoundStmt(statements)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `ifStmt = "if" booleanExpr "then" statement  [ "else" statement ] .`
     *
     * @return the parsed if statement.  Returns an
     *         empty statement if parsing fails.
     */
    private fun parseIfStmt(): Statement {
        try {
            match(Symbol.ifRW)
            val booleanExpr = parseExpression()
            match(Symbol.thenRW)
            val thenStmt = parseStatement()
            var elseStmt: Statement? = null
            if (scanner.symbol == Symbol.elseRW) {
                matchCurrentSymbol()
                elseStmt = parseStatement()
            }

            return IfStmt(booleanExpr, thenStmt, elseStmt)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `loopStmt = [ "while" booleanExpr ] "loop" statement .`
     *
     * @return the parsed loop statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseLoopStmt(): Statement {
        try {
            val stmt = LoopStmt()
            if (scanner.symbol == Symbol.whileRW) {
                matchCurrentSymbol()
                stmt.whileExpr = parseExpression()
            }
            match(Symbol.loopRW)
            loopContext.beginLoop(stmt)
            stmt.statement = parseStatement()
            loopContext.endLoop()

            return stmt
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `exitStmt = "exit" [ "when" booleanExpr ] ";" .`
     *
     * @return the parsed exit statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseExitStmt(): Statement {
        try {
            val exitPosition = scanner.position

            match(Symbol.exitRW)

            var whenExpr: Expression? = null
            if (scanner.symbol == Symbol.whenRW) {
                matchCurrentSymbol()
                whenExpr = parseExpression()
            }

            val loopStmt =
                loopContext.loopStmt ?: throw error(exitPosition, "Exit statement is not nested within a loop.")
            match(Symbol.semicolon)

            return ExitStmt(whenExpr, loopStmt)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `readStmt = "read" variable ";" .`
     *
     * @return the parsed read statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseReadStmt(): Statement {
        try {
            match(Symbol.readRW)
            val variable = parseVariable()
            match(Symbol.semicolon)

            return if (variable == null) EmptyStatement else ReadStmt(variable)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `writeStmt = "write" expressions ";" .`
     *
     * @return the parsed write statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseWriteStmt(): Statement {
        try {
            match(Symbol.writeRW)
            val expressions = parseExpressions()
            match(Symbol.semicolon)

            return OutputStmt(expressions)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `expressions = expression { "," expression } .`
     *
     * @return a list of expressions.
     */
    private fun parseExpressions(): List<Expression> {
        val expressions = ArrayList<Expression>(10)
        expressions.add(parseExpression())

        while (scanner.symbol == Symbol.comma) {
            matchCurrentSymbol()
            expressions.add(parseExpression())
        }

        return expressions
    }

    /**
     * Parse the following grammar rule:<br>
     * `writelnStmt = "writeln" [ expressions ] ";" .`
     *
     * @return the parsed writeln statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseWritelnStmt(): Statement {
        try {
            match(Symbol.writelnRW)

            val expressions =
                if (scanner.symbol.isExprStarter()) parseExpressions()
                else emptyList()

            match(Symbol.semicolon)

            return OutputStmt(expressions, isWriteln = true)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `procedureCallStmt = procId "(" [ actualParameters ] ")" ";" .<br>
     *  actualParameters = expressions .`
     *
     * @return the parsed procedure call statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseProcedureCallStmt(): Statement {
        try {
            val procId = scanner.token
            match(Symbol.identifier)
            match(Symbol.leftParen)

            val params =
                if (scanner.symbol.isExprStarter()) parseExpressions()
                else emptyList()

            match(Symbol.rightParen)
            match(Symbol.semicolon)

            return ProcedureCallStmt(procId, params)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `returnStmt = "return" [ expression ] ";" .`
     *
     * @return the parsed return statement.  Returns
     *         an empty statement if parsing fails.
     */
    private fun parseReturnStmt(): Statement {
        try {
            val returnPosition = scanner.position
            match(Symbol.returnRW)

            val returnExpr = if (scanner.symbol.isExprStarter()) parseExpression()
            else null

            val subprogramDecl = subprogramContext.subprogramDecl ?: throw error(
                returnPosition,
                "Return statement is not nested within a subprogram declaration."
            )

            match(Symbol.semicolon)
            return ReturnStmt(subprogramDecl, returnExpr, returnPosition)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
            return EmptyStatement
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `variable = ( varId | paramId ) { indexExpr | fieldExpr } .<br>
     *  indexExpr = "[" expression "]" .<br>
     *  fieldExpr = "." fieldId .</code>`
     * <br>
     * This method provides common logic for methods `parseVariable()` and
     * `parseVariableExpr()`.  The method does not handle any parser exceptions but
     * throws them back to the calling method where they can be handled appropriately.
     *
     * @return the parsed variable.
     * @throws ParserException if parsing fails.
     * @see .parseVariable
     * @see .parseVariableExpr
     */
    private fun parseVariableCommon(): Variable {
        val idToken = scanner.token
        match(Symbol.identifier)
        val decl = idTable[idToken.text]

        if (decl == null) {
            val errorMsg = "Identifier \"$idToken\" has not been declared."
            throw error(idToken.position, errorMsg)
        } else if (decl !is VariableDecl) {
            val errorMsg = "Identifier \"$idToken\" is not a variable."
            throw error(idToken.position, errorMsg)
        }

        val variableDecl = decl as VariableDecl

        val selectorExprs = ArrayList<Expression>(5)
        while (scanner.symbol.isSelectorStarter()) {
            if (scanner.symbol == Symbol.leftBracket) {
                // parse index expression
                matchCurrentSymbol()
                selectorExprs.add(parseExpression())
                match(Symbol.rightBracket)
            } else if (scanner.symbol == Symbol.dot) {
                // parse field expression
                matchCurrentSymbol()
                val fieldId = scanner.token
                match(Symbol.identifier)
                selectorExprs.add(FieldExpr(fieldId))
            }
        }

        return Variable(variableDecl, idToken.position, selectorExprs)
    }

    /**
     * Parse the following grammar rule:<br>
     * `variable = ( varId | paramId ) { indexExpr | fieldExpr } .
     *
     * @return the parsed variable.  Returns null if parsing fails.
     */
    private fun parseVariable(): Variable? {
        try {
            return parseVariableCommon()
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(setOf(Symbol.assign, Symbol.semicolon))
            return null
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `expression = relation { logicalOp relation } .<br>
     *  logicalOp = "and" | "or" . `
     *
     * @return the parsed expression.
     */
    private fun parseExpression(): Expression {
        var expr = parseRelation()

        while (scanner.symbol.isLogicalOperator()) {
            val operator = scanner.token
            matchCurrentSymbol()
            expr = LogicalExpr(expr, operator, parseRelation())
        }

        return expr
    }

    /**
     * Parse the following grammar rules:<br>
     * `relation = simpleExpr [ relationalOp simpleExpr ] .<br>
     *  relationalOp = "=" | "!=" | "<" | "<=" | ">" | ">=" .`
     *
     * @return the parsed relational expression.
     */
    private fun parseRelation(): Expression {
        val expr = parseSimpleExpr()
        if (scanner.symbol.isRelationalOperator()) {
            val operator = scanner.token
            matchCurrentSymbol()
            val rightOperand = parseSimpleExpr()

            return RelationalExpr(expr, operator, rightOperand)
        }

        return expr
    }

    /**
     * Parse the following grammar rules:<br>
     * `simpleExpr = [ signOp ] term { addingOp term } .<br>
     *  signOp = "+" | "-" .<br>
     *  addingOp = "+" | "-" .`
     *
     * @return the parsed simple expression.
     */
    private fun parseSimpleExpr(): Expression {
        var signOperator: Token? = null
        if (scanner.symbol.isSignOperator()) {
            signOperator = scanner.token
            matchCurrentSymbol()
        }

        var expr: Expression = parseTerm()

        if (signOperator != null) {
            expr = NegationExpr(signOperator, expr)
        }

        while (scanner.symbol.isAddingOperator()) {
            val operator = scanner.token
            matchCurrentSymbol()
            val rightOperand = parseTerm()
            expr = AddingExpr(expr, operator, rightOperand)
        }

        return expr
    }

    /**
     * Parse the following grammar rules:<br>
     * `term = factor { multiplyingOp factor } .<br>
     *  multiplyingOp = "*" | "/" | "mod" .`
     *
     * @return the parsed term expression.
     */
    private fun parseTerm(): Expression {
        var expr: Expression = parseFactor()

        while (scanner.symbol.isMultiplyingOperator()) {
            val operator = scanner.token
            matchCurrentSymbol()
            val rightOperand = parseFactor()
            expr = MultiplyingExpr(expr, operator, rightOperand)
        }

        return expr
    }

    /**
     * Parse the following grammar rule:<br>
     * `factor = "not" factor | constValue | variableExpr | functionCallExpr
     *         | "(" expression ")" .`
     *
     * @return the parsed factor expression. Returns
     *         an empty expression if parsing fails.
     */
    private fun parseFactor(): Expression {
        try {
            val expr: Expression

            if (scanner.symbol == Symbol.notRW) {
                val operator = scanner.token
                matchCurrentSymbol()
                expr = NotExpr(operator, parseFactor())
            } else if (scanner.symbol.isLiteral()) {
                // Handle constant literals separately from constant identifiers.
                expr = parseConstValue()
            } else if (scanner.symbol == Symbol.identifier) {
                // Handle identifiers based on how they are declared,
                // or use the lookahead symbol if not declared.
                val idStr = scanner.text
                val decl = idTable[idStr]

                if (decl != null) {
                    expr = when (decl) {
                        is ConstDecl -> parseConstValue()
                        is VariableDecl -> parseVariableExpr()
                        is FunctionDecl -> parseFunctionCallExpr()
                        else -> {
                            val errorPos = scanner.position
                            val errorMsg = "Identifier \"$idStr\" is not valid as an expression."

                            // special handling when procedure call is used as a function call
                            if (decl is ProcedureDecl) {
                                scanner.advance()
                                if (scanner.symbol == Symbol.leftParen) {
                                    scanner.advanceTo(Symbol.rightParen)
                                    scanner.advance()   // advance past the right paren
                                }
                            }

                            throw error(errorPos, errorMsg)
                        }
                    }
                } else {
                    // Make parsing decision using an additional lookahead symbol.
                    if (scanner.lookahead(2).symbol == Symbol.leftParen)
                        expr = parseFunctionCallExpr()
                    else
                        throw error("Identifier \"${scanner.token}\" has not been declared.")
                }
            } else if (scanner.symbol == Symbol.leftParen) {
                matchCurrentSymbol()
                expr = parseExpression()
                match(Symbol.rightParen)
            } else
                throw error("Invalid expression.")

            return expr
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
            return EmptyExpression
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `constValue = literal | constId .`
     *
     * @return the parsed constant value.  Returns
     *         an empty expression if parsing fails.
     */
    private fun parseConstValue(): Expression {
        try {
            if (scanner.symbol.isLiteral()) {
                val literal = parseLiteral()
                return ConstValue(literal)
            } else if (scanner.symbol == Symbol.identifier) {
                val idToken = scanner.token
                matchCurrentSymbol()

                val decl = idTable[idToken.text]

                if (decl == null) {
                    val errorMsg = "Identifier \"$idToken\" has not been declared."
                    throw error(idToken.position, errorMsg)
                } else if (decl !is ConstDecl) {
                    val errorMsg = "Identifier \"$idToken\" was not declared as a constant."
                    throw error(idToken.position, errorMsg)
                }

                return ConstValue(idToken, decl)
            } else
                throw error("Invalid constant.")
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
            return EmptyExpression
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `variableExpr = variable .`
     *
     * @return the parsed variable expression.  Returns
     *         an empty expression if parsing fails.
     */
    private fun parseVariableExpr(): Expression {
        try {
            val variable = parseVariableCommon()
            return VariableExpr(variable)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
            return EmptyExpression
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `functionCallExpr = funId "(" [ actualParameters ] ")" .<br>
     *  actualParameters = expressions .`
     *
     * @return the parsed function call expression.  Returns
     *         an empty expression if parsing fails.
     */
    private fun parseFunctionCallExpr(): Expression {
        try {
            val funId = scanner.token
            match(Symbol.identifier)
            match(Symbol.leftParen)

            var actualParams: List<Expression> = ArrayList<Expression>()
            if (scanner.symbol.isExprStarter())
                actualParams = parseExpressions()

            match(Symbol.rightParen)
            return FunctionCallExpr(funId, actualParams)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
            return EmptyExpression
        }
    }

    // Utility parsing methods

    /*
     * Wrapper around method parseConstValue() that always
     * returns a valid constant value.
     */
    private fun parseIntConstValue(): ConstValue {
        var intConstValue = parseConstValue()

        if (intConstValue is EmptyExpression) {
            // Error has already been reported.  Create default value and continue.
            val token = Token(Symbol.intLiteral, Position(), "1")
            intConstValue = ConstValue(token)
        }

        return intConstValue as ConstValue
    }

    /**
     * Check that the current scanner symbol is the expected symbol.  If it
     * is, then advance the scanner.  Otherwise, throw a ParserException.
     */
    private fun match(expectedSymbol: Symbol) {
        if (scanner.symbol == expectedSymbol)
            scanner.advance()
        else {
            val errorMsg = "Expecting \"$expectedSymbol\" but found \"${scanner.token}\" instead."
            throw error(errorMsg)
        }
    }

    /**
     * Advance the scanner.  This method represents an unconditional match
     * with the current scanner symbol.
     */
    private fun matchCurrentSymbol() = scanner.advance()

    /**
     * Advance the scanner until the current symbol is one
     * of the symbols in the specified set of followers.
     */
    private fun recover(followers: Set<Symbol>) = scanner.advanceTo(followers)

    /**
     * Create a parser exception with the specified error message and
     * the current scanner position.
     */
    private fun error(errorMsg: String): ParserException = error(scanner.position, errorMsg)

    /**
     * Create a parser exception with the specified error position
     * and error message.
     */
    private fun error(errorPos: Position, errorMsg: String) = ParserException(errorPos, errorMsg)

    /**
     * Create an internal compiler exception with the specified error
     * message and the current scanner position.
     */
    private fun internalError(errorMsg: String) = InternalCompilerException(scanner.position, errorMsg)
}
