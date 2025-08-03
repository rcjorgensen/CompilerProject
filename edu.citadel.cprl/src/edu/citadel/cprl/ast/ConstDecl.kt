package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.Symbol
import edu.citadel.cprl.Token
import edu.citadel.cprl.Type

/**
 * The abstract syntax tree node for a constant declaration.
 *
 * @constructor Construct a constant declaration with its identifier, type, and literal.
 */
class ConstDecl(identifier: Token, constType: Type, val literal: Token) : InitialDecl(identifier, constType) {
    override fun checkConstraints() {
        try {
            if (literal.symbol == Symbol.intLiteral) {
                try {
                    literal.text.toInt()
                } catch (_: NumberFormatException) {
                    // set the literal's value to a valid value in order to prevent additional error messages
                    val errorMsg = "The number \"${literal.text}\" cannot be converted to an integer in CPRL."
                    throw error(literal.position, errorMsg)
                }
            }
        } catch (e: ConstraintException) {
            literal.text = "1"
            errorHandler.reportError(e)
        }
    }
}
