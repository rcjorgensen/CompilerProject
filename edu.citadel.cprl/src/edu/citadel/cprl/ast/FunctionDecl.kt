package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.ArrayType
import edu.citadel.cprl.Token

/**
 * The abstract syntax tree node for a function declaration.
 *
 * @constructor Construct a function declaration with its name (an identifier).
 */
class FunctionDecl(funcId: Token) : SubprogramDecl(funcId) {
    /**
     * The relative address of the function return value.
     */
    val relAddr: Int
        get() = -type.size - paramLength

    override fun checkConstraints() {
        try {
            super.checkConstraints()

            for (paramDecl in formalParams) {
                if (paramDecl.isVarParam && paramDecl.type !is ArrayType) {
                    val errorMsg = "A function cannot have var parameters."
                    throw error(paramDecl.position, errorMsg)
                }
            }

            if (!hasReturnStmt(statements)) {
                val errorMsg = "A function must have at least one return statement."
                throw error(position, errorMsg)
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }

    /**
     * Returns true if the specified list of statements contains at least one
     * return statement.
     *
     * @param statements  the list of statements to check for a return statement.
     *                    If any of the statements in the list contains nested
     *                    statements (e.g., an if statement, a compound statement,
     *                    or a loop statement), then the nested statements are
     *                    also checked for a return statement.
     */
    private fun hasReturnStmt(statements: List<Statement>): Boolean {
        // Check that we have at least one return statement.
        for (statement in statements) {
            if (hasReturnStmt(statement))
                return true
        }

        return false
    }

    /**
     * Returns true if the specified statement is a return statement or contains
     * at least one return statement.
     *
     * @param statement the statement to check for a return statement.  If the
     *                  statement contains nested statements (e.g., an if statement,
     *                  a compound statement, or a loop statement), then the nested
     *                  statements are also checked for a return statement.
     */
    private fun hasReturnStmt(statement: Statement): Boolean {
        return when (statement) {
            is ReturnStmt -> true
            is IfStmt -> hasReturnStmt(statement.thenStmt) || statement.elseStmt != null && hasReturnStmt(statement.elseStmt)
            is CompoundStmt -> statement.statements.any { statement -> hasReturnStmt(statement) }
            is LoopStmt -> hasReturnStmt(statement.statement)
            else -> false
        }
    }

    override fun emit() {
        setRelativeAddresses()

        emitLabel(subprogramLabel)

        if (varLength > 0)
            emit("PROC $varLength")

        for (decl in initialDecls)
            decl.emit()

        for (statement in statements)
            statement.emit()
    }
}
