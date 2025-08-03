package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException

/**
 * The abstract syntax tree node for a compound statement.
 *
 * @property statements the list of statements in the compound statement
 */
class CompoundStmt(val statements: List<Statement>) : Statement() {
    override fun checkConstraints() {
        try {
            for (statement in statements)
                statement.checkConstraints()
        } catch (e: ConstraintException) {
            errorHandler.reportError(e);
        }
    }

    override fun emit() {
// ...
    }
}
