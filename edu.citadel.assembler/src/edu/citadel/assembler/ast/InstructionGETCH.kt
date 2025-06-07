package edu.citadel.assembler.ast

import edu.citadel.assembler.Symbol
import edu.citadel.assembler.Token
import edu.citadel.cvm.Opcode

/**
 * This class implements the abstract syntax tree for the assembly
 * language instruction GETCH.
 */
class InstructionGETCH(labels: MutableList<Token>, opcode: Token) : InstructionNoArgs(labels, opcode) {
    override fun assertOpcode() = assertOpcode(Symbol.GETCH)

    override fun emit() = emit(Opcode.GETCH)
}
