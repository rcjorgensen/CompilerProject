package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.compiler.Position

/**
 * The abstract syntax tree node for a return statement.
 *
 * @constructor Construct a return statement with a reference to the enclosing subprogram
 *              and the expression for the value being returned, which may be null.
 */
class ReturnStmt(
    private val subprogramDecl: SubprogramDecl,   // nonstructural reference
    private val returnExpr: Expression?,
    private val returnPosition: Position,
) : Statement() {
    override fun checkConstraints() {
        try {
            returnExpr?.checkConstraints()

            if (subprogramDecl is FunctionDecl) {
                if (returnExpr == null) {
                    val errorMsg = "A return statement nested within a function must return a value."
                    throw error(returnPosition, errorMsg)
                }

                if (!matchTypes(subprogramDecl.type, returnExpr)) {
                    val errorMsg = "Return expression type does not match function return type."
                    throw error(returnExpr.position, errorMsg)
                }
            } else {
                if (returnExpr != null) {
                    val errorMsg = "Return expression allowed only within functions."
                    throw error(returnExpr.position, errorMsg)
                }
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }

    override fun emit() {
        if (returnExpr != null && subprogramDecl is FunctionDecl) {
            emit("LDLADDR ${subprogramDecl.relAddr}")
            returnExpr.emit()
            emitStoreInst(returnExpr.type)
        }
        emit("RET ${subprogramDecl.paramLength}")
    }
}
