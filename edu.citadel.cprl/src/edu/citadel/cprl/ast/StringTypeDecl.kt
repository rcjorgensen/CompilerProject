package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.StringType
import edu.citadel.cprl.Token
import edu.citadel.cprl.Type

/**
 * The abstract syntax tree node for a string type declaration.
 *
 * @constructor Construct a string type declaration with the specified
 *              type name and capacity.
 *
 * @param typeId the identifier token containing the string type name
 * @property capacity the maximum number of characters in the string
 */
class StringTypeDecl(typeId: Token, val capacity: ConstValue) :
    InitialDecl(typeId, StringType(typeId.text, capacity.intValue)) {
    override fun checkConstraints() {
        try {
            capacity.checkConstraints()

            if (capacity.type != Type.Integer) {
                val errorMsg = "String capacity must have type Integer."
                throw error(capacity.position, errorMsg)
            }

            if (capacity.intValue < 1) {
                val errorMsg = "String capacity must be a positive integer."
                throw error(capacity.position, errorMsg)
            }

            if (capacity.intValue > 512) {
                val errorMsg = "String capacity cannot be greater than 512."
                throw error(capacity.position, errorMsg)
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }
}
