package edu.citadel.assembler.ast

import edu.citadel.assembler.Symbol
import edu.citadel.assembler.Token
import edu.citadel.cvm.Opcode

/**
 * This class implements the abstract syntax tree for the assembly
 * language instruction INC.
 */
class InstructionINC(labels: MutableList<Token>, opcode: Token) : InstructionNoArgs(labels, opcode) {
    override fun assertOpcode() = assertOpcode(Symbol.INC)

    override fun emit() = emit(Opcode.INC)
}
