package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.Symbol
import edu.citadel.cprl.Token
import edu.citadel.cprl.Type

/**
 * The abstract syntax tree node for a multiplying expression.  A multiplying
 * expression is a binary expression where the operator is a multiplying
 * operator such as "*", "/", or "mod".  A simple example would be "5*x".
 *
 * @constructor Construct a multiplying expression with the operator
 *              ("*", "/", or "mod") and the two operands.
 */
class MultiplyingExpr(leftOperand: Expression, operator: Token, rightOperand: Expression) :
    BinaryExpr(leftOperand, operator, rightOperand) {
    /**
     * Initialize the type of the expression to Integer.
     */
    init {
        type = Type.Integer
        assert(operator.symbol.isMultiplyingOperator())
        { "MultiplyingExpr : operator is not a multiplying operator." }
    }

    override fun checkConstraints() {
        try {
            leftOperand.checkConstraints()
            rightOperand.checkConstraints()

            if (leftOperand.type != Type.Integer) {
                val errorMsg = "Left operand for expression should have type Integer."
                throw error(leftOperand.position, errorMsg)
            }

            if (rightOperand.type != Type.Integer) {
                val errorMsg = "Right operand for expression should have type Integer."
                throw error(rightOperand.position, errorMsg)
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }

    override fun emit() {
        leftOperand.emit()
        rightOperand.emit()
        if (operator.symbol == Symbol.times) {
            emit("MUL")
        } else if (operator.symbol == Symbol.divide) {
            emit("DIV")
        } else if (operator.symbol == Symbol.modRW) {
            emit("MOD")
        }
    }
}
