package edu.citadel.cprl

import edu.citadel.compiler.ErrorHandler
import edu.citadel.compiler.InternalCompilerException
import edu.citadel.compiler.ParserException
import edu.citadel.compiler.Position
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
     */
    fun parseProgram() {
        try {
            parseInitialDecls()
            parseSubprogramDecls()

            if (scanner.symbol != Symbol.EOF) {
                throw error(
                    "Expecting \"${Symbol.procRW}\" or \"${Symbol.funRW}\" but found \"${scanner.token}\" instead."
                )
            }
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(EnumSet.of(Symbol.EOF))
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `initialDecls = { initialDecl } .`
     */
    private fun parseInitialDecls() {
        while (scanner.symbol.isInitialDeclStarter())
            parseInitialDecl()
    }

    /**
     * Parse the following grammar rule:<br>
     * `initialDecl = constDecl | varDecl | typeDecl .`
     */
    private fun parseInitialDecl() {
        when (scanner.symbol) {
            Symbol.constRW -> parseConstDecl()
            Symbol.varRW -> parseVarDecl()
            Symbol.typeRW -> parseTypeDecl()
            else -> throw internalError("Invalid initial declaration")
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `constDecl = "const" constId ":=" literal ";" .`
     */
    private fun parseConstDecl() {
        try {
            match(Symbol.constRW)
            val constId = scanner.token
            match(Symbol.identifier)
            match(Symbol.assign)
            parseLiteral()
            match(Symbol.semicolon)
            idTable.add(constId, IdType.constantId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `literal = intLiteral | charLiteral | stringLiteral | "true" | "false" .`
     */
    private fun parseLiteral() {
        try {
            if (scanner.symbol.isLiteral())
                matchCurrentSymbol()
            else
                throw error("Invalid literal expression.")
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `varDecl = "var" identifiers ":" typeName [ ":=" constValue] ";" .`
     */
    private fun parseVarDecl() {
        try {
            match(Symbol.varRW)
            val identifiers: List<Token> = parseIdentifiers()
            match(Symbol.colon)
            parseTypeName()

            if (scanner.symbol == Symbol.assign) {
                matchCurrentSymbol()
                parseConstValue()
            }

            match(Symbol.semicolon)

            for (identifier in identifiers)
                idTable.add(identifier, IdType.variableId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `identifiers = identifier { "," identifier } .`
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
            recover(EnumSet.of(Symbol.colon))
            return emptyList()   // should never execute
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `typeDecl = arrayTypeDecl | recordTypeDecl | stringTypeDecl .`
     */
    private fun parseTypeDecl() {
        assert(scanner.symbol == Symbol.typeRW)

        try {
            when (scanner.lookahead(4).symbol) {
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
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `arrayTypeDecl = "type" typeId "=" "array" "[" intConstValue "]"
     *                  "of" typeName ";" .`
     */
    private fun parseArrayTypeDecl() {
        try {
            match(Symbol.typeRW)

            val typeId = scanner.token
            match(Symbol.identifier)

            match(Symbol.equals)
            match(Symbol.arrayRW)
            match(Symbol.leftBracket)

            try {
                parseConstValue()
                match(Symbol.rightBracket)
            } catch (e: ParserException) {
                if (scanner.symbol == Symbol.rightBracket) {
                    errorHandler.reportError(e)
                    matchCurrentSymbol()    // treat "[]" as "[intConst]" in this context
                } else
                    throw e
            }

            match(Symbol.ofRW)
            parseTypeName()
            match(Symbol.semicolon)

            idTable.add(typeId, IdType.arrayTypeId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `recordTypeDecl = "type" typeId "=" "record" "{" fieldDecls "}" ";" .`
     */
    private fun parseRecordTypeDecl() {
        try {
            match(Symbol.typeRW)
            val typeId = scanner.token
            match(Symbol.identifier)
            match(Symbol.equals)
            match(Symbol.recordRW)
            match(Symbol.leftBrace)

            try {
                idTable.openScope(ScopeLevel.RECORD)
                parseFieldDecls()
            } finally {
                idTable.closeScope()
            }

            match(Symbol.rightBrace)
            match(Symbol.semicolon)
            idTable.add(typeId, IdType.recordTypeId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `fieldDecls = { fieldDecl } .`
     */
    private fun parseFieldDecls() {
        while (scanner.symbol != Symbol.rightBrace) {
            parseFieldDecl()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `fieldDecl = fieldId ":" typeName ";" .`
     */
    private fun parseFieldDecl() {
        try {
            val fieldId = scanner.token
            match(Symbol.identifier)
            match(Symbol.colon)
            parseTypeName()
            match(Symbol.semicolon)
            idTable.add(fieldId, IdType.fieldId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `stringTypeDecl = "type" typeId "=" "string" "[" intConstValue "]" ";" .`
     */
    private fun parseStringTypeDecl() {
        try {
            match(Symbol.typeRW)
            val typeId = scanner.token
            match(Symbol.identifier)
            match(Symbol.equals)
            match(Symbol.stringRW)
            match(Symbol.leftBracket)
            parseConstValue()
            match(Symbol.rightBracket)
            match(Symbol.semicolon)
            idTable.add(typeId, IdType.stringTypeId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(initialDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `typeName = "Integer" | "Boolean" | "Char" | typeId .`
     */
    private fun parseTypeName() {
        try {
            when (scanner.symbol) {
                Symbol.IntegerRW -> matchCurrentSymbol()
                Symbol.BooleanRW -> matchCurrentSymbol()
                Symbol.CharRW -> matchCurrentSymbol()
                Symbol.identifier -> {
                    val typeId = scanner.token
                    matchCurrentSymbol()
                    val type = idTable[typeId.text]

                    if (type != null) {
                        if (type == IdType.arrayTypeId || type == IdType.recordTypeId || type == IdType.stringTypeId)
                            ;   // empty statement for versions 1 and 2 of Parser
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
    }

    /**
     * Parse the following grammar rule:<br>
     * `subprogramDecls = { subprogramDecl } .`
     */
    private fun parseSubprogramDecls() {
        while (scanner.symbol.isSubprogramDeclStarter()) {
            parseSubprogramDecl()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `subprogramDecl = procedureDecl | functionDecl .`
     */
    private fun parseSubprogramDecl() {
        when (scanner.symbol) {
            Symbol.procRW -> parseProcedureDecl()
            Symbol.funRW -> parseFunctionDecl()
            else -> throw internalError("Invalid subprogram declaration")
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `procedureDecl = "proc" procId "(" [ formalParameters ] ")"
     *                  "{" initialDecls statements "}" .`
     */
    private fun parseProcedureDecl() {
        try {
            match(Symbol.procRW)
            val procId = scanner.token
            match(Symbol.identifier)
            idTable.add(procId, IdType.procedureId)
            match(Symbol.leftParen)

            try {
                idTable.openScope(ScopeLevel.LOCAL)

                if (scanner.symbol.isParameterDeclStarter())
                    parseFormalParameters()

                match(Symbol.rightParen)
                match(Symbol.leftBrace)
                parseInitialDecls()
                parseStatements()
            } finally {
                idTable.closeScope()
            }

            match(Symbol.rightBrace)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(subprogDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `functionDecl = "fun" funcId "(" [ formalParameters ] ")" ":" typeName
     *                 "{" initialDecls statements "}" .`
     */
    private fun parseFunctionDecl() {
        try {
            match(Symbol.funRW)
            val funId = scanner.token
            match(Symbol.identifier)
            idTable.add(funId, IdType.functionId)
            match(Symbol.leftParen)

            try {
                idTable.openScope(ScopeLevel.LOCAL)

                if (scanner.symbol.isParameterDeclStarter())
                    parseFormalParameters()

                match(Symbol.rightParen)
                match(Symbol.colon)
                parseTypeName()
                match(Symbol.leftBrace)
                parseInitialDecls()
                parseStatements()
            } finally {
                idTable.closeScope()
            }

            match(Symbol.rightBrace)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(subprogDeclFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `formalParameters = parameterDecl { "," parameterDecl } .`
     */
    private fun parseFormalParameters() {
        parseParameterDecl()

        while (scanner.symbol == Symbol.comma) {
            matchCurrentSymbol()
            parseParameterDecl()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `parameterDecl = [ "var" ] paramId ":" typeName .`
     */
    private fun parseParameterDecl() {
        try {
            if (scanner.symbol == Symbol.varRW) {
                matchCurrentSymbol()
            }
            val paramId = scanner.token
            match(Symbol.identifier)
            match(Symbol.colon)
            parseTypeName()

            idTable.add(paramId, IdType.variableId)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(EnumSet.of(Symbol.comma, Symbol.rightParen))
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `statements = { statement } .`
     */
    private fun parseStatements() {
        while (scanner.symbol.isStmtStarter()) {
            parseStatement()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `statement = assignmentStmt | procedureCallStmt | compoundStmt | ifStmt
     *            | loopStmt | exitStmt | readStmt | writeStmt | writelnStmt
     *            | returnStmt .`
     */
    private fun parseStatement() {
        // assumes that scanner.getSymbol() can start a statement
        assert(scanner.symbol.isStmtStarter()) { "Invalid statement." }

        try {
            val symbol = scanner.symbol

            if (symbol == Symbol.identifier) {
                // Handle identifiers based on how they are declared,
                // or use the lookahead symbol if not declared.
                val idStr = scanner.text
                val idType = idTable[idStr]

                if (idType != null) {
                    when (idType) {
                        IdType.variableId -> parseAssignmentStmt()
                        IdType.procedureId -> parseProcedureCallStmt()
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
            } else if (symbol == Symbol.leftBrace)
                parseCompoundStmt()
            else if (symbol == Symbol.ifRW)
                parseIfStmt()
            else if (symbol == Symbol.loopRW || symbol == Symbol.whileRW)
                parseLoopStmt()
            else if (symbol == Symbol.exitRW)
                parseExitStmt()
            else if (symbol == Symbol.readRW)
                parseReadStmt()
            else if (symbol == Symbol.writeRW)
                parseWriteStmt()
            else if (symbol == Symbol.writelnRW)
                parseWritelnStmt()
            else if (symbol == Symbol.returnRW)
                parseReturnStmt()
            else throw internalError("Invalid statement.")
        } catch (e: ParserException) {
            errorHandler.reportError(e)

            // Error recovery here is complicated for identifiers since they can both
            // start a statement and appear elsewhere in the statement.  (Consider,
            // for example, an assignment statement or a procedure call statement.)
            // Since the most common error is to declare or reference an identifier
            // incorrectly, we will assume that this is the case and advance to the
            // next semicolon (which hopefully ends the erroneous statement) before
            // performing error recovery.
            scanner.advanceTo(Symbol.semicolon)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `assignmentStmt = variable ":=" expression ";" .`
     */
    private fun parseAssignmentStmt() {
        try {
            parseVariable()

            try {
                match(Symbol.assign)
            } catch (e: ParserException) {
                if (scanner.symbol == Symbol.equals) {
                    errorHandler.reportError(e)
                    matchCurrentSymbol()    // treat "=" as ":=" in this context
                } else
                    throw e
            }

            parseExpression()
            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `compoundStmt = "{" statements "}" .`
    ` */
    private fun parseCompoundStmt() {
        try {
            match(Symbol.leftBrace)
            parseStatements()
            match(Symbol.rightBrace)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `ifStmt = "if" booleanExpr "then" statement  [ "else" statement ] .`
     */
    private fun parseIfStmt() {
        try {
            match(Symbol.ifRW)
            parseExpression()
            match(Symbol.thenRW)
            parseStatement()
            if (scanner.symbol == Symbol.elseRW) {
                matchCurrentSymbol()
                parseStatement()
            }
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `loopStmt = [ "while" booleanExpr ] "loop" statement .`
     */
    private fun parseLoopStmt() {
        try {
            if (scanner.symbol == Symbol.whileRW) {
                matchCurrentSymbol()
                parseExpression()
            }
            match(Symbol.loopRW)
            parseStatement()
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `exitStmt = "exit" [ "when" booleanExpr ] ";" .`
     */
    private fun parseExitStmt() {
        try {
            match(Symbol.exitRW)
            if (scanner.symbol == Symbol.whenRW) {
                matchCurrentSymbol()
                parseExpression()
            }
            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `readStmt = "read" variable ";" .`
     */
    private fun parseReadStmt() {
        try {
            match(Symbol.readRW)
            parseVariable()
            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `writeStmt = "write" expressions ";" .`
     */
    private fun parseWriteStmt() {
        try {
            match(Symbol.writeRW)
            parseExpressions()
            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `expressions = expression { "," expression } .`
     */
    private fun parseExpressions() {
        parseExpression()
        while (scanner.symbol == Symbol.comma) {
            matchCurrentSymbol()
            parseExpression()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `writelnStmt = "writeln" [ expressions ] ";" .`
     */
    private fun parseWritelnStmt() {
        try {
            match(Symbol.writelnRW)

            if (scanner.symbol.isExprStarter())
                parseExpressions()

            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `procedureCallStmt = procId "(" [ actualParameters ] ")" ";" .<br>
     *  actualParameters = expressions .`
     */
    private fun parseProcedureCallStmt() {
        try {
            match(Symbol.identifier)
            match(Symbol.leftParen)

            if (scanner.symbol.isExprStarter())
                parseExpressions()

            match(Symbol.rightParen)
            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `returnStmt = "return" [ expression ] ";" .`
     */
    private fun parseReturnStmt() {
        try {
            match(Symbol.returnRW)

            if (scanner.symbol.isExprStarter())
                parseExpression()

            match(Symbol.semicolon)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(stmtFollowers)
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
     * @throws ParserException if parsing fails.
     * @see .parseVariable
     * @see .parseVariableExpr
     */
    private fun parseVariableCommon() {
        val idToken = scanner.token
        match(Symbol.identifier)
        val idType = idTable[idToken.text]

        if (idType == null) {
            val errorMsg = "Identifier \"$idToken\" has not been declared."
            throw error(idToken.position, errorMsg)
        } else if (idType !== IdType.variableId) {
            val errorMsg = "Identifier \"$idToken\" is not a variable."
            throw error(idToken.position, errorMsg)
        }

        while (scanner.symbol.isSelectorStarter()) {
            if (scanner.symbol == Symbol.leftBracket) {
                // parse index expression
                matchCurrentSymbol()
                parseExpression()
                match(Symbol.rightBracket)
            } else if (scanner.symbol == Symbol.dot) {
                // parse field expression
                matchCurrentSymbol()
                match(Symbol.identifier)
            }
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `variable = ( varId | paramId ) { indexExpr | fieldExpr } .`
     */
    private fun parseVariable() {
        try {
            parseVariableCommon()
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(EnumSet.of(Symbol.assign, Symbol.semicolon))
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `expression = relation { logicalOp relation } .<br>
     *  logicalOp = "and" | "or" .`
     */
    private fun parseExpression() {
        parseRelation()
        while (scanner.symbol.isLogicalOperator()) {
            matchCurrentSymbol()
            parseRelation()
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `relation = simpleExpr [ relationalOp simpleExpr ] .<br>
     *  relationalOp = "=" | "!=" | "<" | "<=" | ">" | ">=" .`
     */
    private fun parseRelation() {
        parseSimpleExpr()
        if (scanner.symbol.isRelationalOperator()) {
            matchCurrentSymbol()
            parseSimpleExpr()
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `simpleExpr = [ signOp ] term { addingOp term } .<br>
     *  signOp = "+" | "-" .<br>
     *  addingOp = "+" | "-" .`
     */
    private fun parseSimpleExpr() {
        if (scanner.symbol.isSignOperator()) {
            matchCurrentSymbol()
        }
        parseTerm()
        while (scanner.symbol.isAddingOperator()) {
            matchCurrentSymbol()
            parseTerm()
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `term = factor { multiplyingOp factor } .<br>
     *  multiplyingOp = "*" | "/" | "mod" .`
     */
    private fun parseTerm() {
        parseFactor()
        while (scanner.symbol.isMultiplyingOperator()) {
            matchCurrentSymbol()
            parseFactor()
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `factor = "not" factor | constValue | variableExpr | functionCallExpr
     *         | "(" expression ")" .`
     */
    private fun parseFactor() {
        try {
            if (scanner.symbol == Symbol.notRW) {
                matchCurrentSymbol()
                parseFactor()
            } else if (scanner.symbol.isLiteral()) {
                // Handle constant literals separately from constant identifiers.
                parseConstValue()
            } else if (scanner.symbol == Symbol.identifier) {
                // Handle identifiers based on how they are declared,
                // or use the lookahead symbol if not declared.
                val idStr = scanner.text
                val idType = idTable[idStr]

                if (idType != null) {
                    try {
                        when (idType) {
                            IdType.constantId -> parseConstValue()
                            IdType.variableId -> parseVariableExpr()
                            IdType.functionId -> parseFunctionCallExpr()
                            else -> throw error(
                                "Identifier \"$idStr\" is not valid as an expression."
                            )
                        }
                    } catch (e : ParserException) {
                        if (idType == IdType.procedureId) {
                            errorHandler.reportError(e)
                            parseFunctionCallExpr()     // treat the procedure call as a function call in this context
                        } else
                            throw e
                    }
                } else {
                    // Make parsing decision using an additional lookahead symbol.
                    if (scanner.lookahead(2).symbol == Symbol.leftParen)
                        parseFunctionCallExpr()
                    else
                        throw error("Identifier \"${scanner.token}\" has not been declared.")
                }
            } else if (scanner.symbol == Symbol.leftParen) {
                matchCurrentSymbol()
                parseExpression()
                match(Symbol.rightParen)
            } else
                throw error("Invalid expression.")
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
        }
    }

    /**
     * Parse the following grammar rule:<br>
     * `constValue = literal | constId .`
     */
    private fun parseConstValue() {
        if (scanner.symbol.isLiteral())
            parseLiteral()
        else if (scanner.symbol == Symbol.identifier)
            matchCurrentSymbol()
        else
            throw error("Invalid constant.")
    }

    /**
     * Parse the following grammar rule:<br>
     * `variableExpr = variable .`
     */
    private fun parseVariableExpr() {
        try {
            parseVariableCommon()
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
        }
    }

    /**
     * Parse the following grammar rules:<br>
     * `functionCallExpr = funcId "(" [ actualParameters ] ")" .<br>
     *  actualParameters = expressions .`
     */
    private fun parseFunctionCallExpr() {
        try {
            match(Symbol.identifier)
            match(Symbol.leftParen)
            if (scanner.symbol.isExprStarter())
                parseExpressions()
            match(Symbol.rightParen)
        } catch (e: ParserException) {
            errorHandler.reportError(e)
            recover(factorFollowers)
        }
    }

    // Utility parsing methods

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
