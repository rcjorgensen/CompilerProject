package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.StringType
import edu.citadel.cprl.Type

/**
 * The abstract syntax tree node for a read statement.
 *
 * @constructor Construct a read statement with the specified variable
 *              for storing the input.
 */
class ReadStmt(private val variable: Variable) : Statement() {
    override fun checkConstraints() {
        // input is limited to integers, characters, and strings
        try {
            variable.checkConstraints()

            if (variable.type != Type.Integer && variable.type != Type.Char && variable.type !is StringType
            ) {
                val errorMsg = "Input supported only for integers, characters, and strings.";
                throw error(variable.position, errorMsg)
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }

    override fun emit() {
        variable.emit()

        when (val type = variable.type) {
            is StringType -> emit("GETSTR ${type.capacity}")
            Type.Integer -> emit("GETINT")
            else   // type must be Char
                -> emit("GETCH")
        }
    }
}
