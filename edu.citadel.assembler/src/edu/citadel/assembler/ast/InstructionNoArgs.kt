package edu.citadel.assembler.ast

import edu.citadel.assembler.Token
import edu.citadel.compiler.ConstraintException

/**
 * This class serves as a base class for the abstract syntax
 * tree for an assembly language instruction with no arguments.
 *
 * @constructor Construct a no-argument instruction with a
 *              list of labels and an opcode.
 */
abstract class InstructionNoArgs(labels: MutableList<Token>, opcode: Token) : Instruction(labels, opcode) {
    override val argSize: Int
        get() = 0

    override fun checkConstraints() {
        try {
            assertOpcode()
            checkLabels()
        } catch (e: ConstraintException) {
            errorHandler.reportError(e)
        }
    }
}
