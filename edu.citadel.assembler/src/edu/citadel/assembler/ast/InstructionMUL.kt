package edu.citadel.assembler.ast

import edu.citadel.assembler.Symbol
import edu.citadel.assembler.Token
import edu.citadel.cvm.Opcode

/**
 * This class implements the abstract syntax tree for the assembly
 * language instruction MUL.
 */
class InstructionMUL(labels: MutableList<Token>, opcode: Token) : InstructionNoArgs(labels, opcode) {
    override fun assertOpcode() = assertOpcode(Symbol.MUL)

    override fun emit() = emit(Opcode.MUL)
}
