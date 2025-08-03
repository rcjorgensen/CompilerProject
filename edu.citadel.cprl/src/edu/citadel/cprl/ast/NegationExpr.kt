package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.Symbol
import edu.citadel.cprl.Token
import edu.citadel.cprl.Type

/**
 * The abstract syntax tree node for a negation expression.  A negation
 * expression is a unary expression where the operator is either "-" or "+".
 * A simple example would be "-x".
 *
 * @constructor Construct a negation expression with the specified
 *              operator ("-" or "+") and operand.
 */
class NegationExpr(operator: Token, operand: Expression) : UnaryExpr(operator, operand) {
    init {
        type = Type.Integer
        assert(operator.symbol == Symbol.minus || operator.symbol == Symbol.plus) { "Operator is not \"-\" or \"+\" operator." }
    }

    override fun checkConstraints() {
        try {
            operand.checkConstraints()

            if (operand.type != Type.Integer) {
                val errorMsg = "Expression following \"-\" operator is not an Integer expression."
                throw error(operand.position, errorMsg)
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }

    override fun emit() {
// ...
    }
}
