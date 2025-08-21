package edu.citadel.cprl.ast

import edu.citadel.compiler.ConstraintException
import edu.citadel.cprl.ArrayType
import edu.citadel.cprl.StringType
import edu.citadel.cprl.Token

/**
 * The abstract syntax tree node for a procedure call statement.
 *
 * @constructor Construct a procedure call statement with the procedure
 *              name (an identifier token) and the list of actual parameters
 *              being passed as part of the call.
 */
class ProcedureCallStmt(private val procId: Token, actualParams: List<Expression>) : Statement() {
    // We need a mutable list since, for var parameters,
    // we have to replace variable expressions by variables
    private val actualParams: MutableList<Expression> = actualParams.toMutableList()

    // declaration of the procedure being called
    private lateinit var procDecl: ProcedureDecl   // nonstructural reference

    override fun checkConstraints() {
        try {
            when (val decl = idTable[procId.text]) {
                null -> {
                    val errorMsg = "Procedure \"$procId\" has not been declared."
                    throw error(procId.position, errorMsg)
                }

                !is ProcedureDecl -> {
                    val errorMsg = "Identifier \"$procId\" was not declared as a procedure."
                    throw error(procId.position, errorMsg)
                }

                else
                    -> procDecl = decl
            }

            val formalParams: List<ParameterDecl> = procDecl.formalParams

            // check that numbers of parameters match
            if (actualParams.size != formalParams.size) {
                val errorMsg = "Incorrect number of actual parameters."
                throw error(procId.position, errorMsg)
            }

            // call checkConstraints for each actual parameter
            for (expr in actualParams)
                expr.checkConstraints()

            for (i in actualParams.indices) {
                var expr: Expression = actualParams[i]
                val param: ParameterDecl = formalParams[i]

                // check that parameter types match
                if (!matchTypes(param.type, expr))
                    throw error(expr.position, "Parameter type mismatch.")

                // check that string parameters are not literals
                if (expr.type is StringType && expr is ConstValue) {
                    val errorMsg = "String literals can't be passed as parameters."
                    throw error(expr.position, errorMsg)
                }

                if (param.isVarParam) {
                    if (expr is VariableExpr) {
                        // replace variable expression by a variable
                        expr = Variable(expr)
                        actualParams[i] = expr
                    } else {
                        val errorMsg = "Expression for a var parameter must be a variable."
                        throw error(expr.position, errorMsg)
                    }
                }
            }
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }

    override fun emit() {
        // emit code for actual parameters
        for (expr in actualParams)
            expr.emit()

        emit("CALL ${procDecl.subprogramLabel}")
    }
}
