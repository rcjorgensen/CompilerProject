package edu.citadel.cprl

import edu.citadel.cprl.ast.FieldDecl

/**
 * This class encapsulates the language concept of a record type
 * in the programming language CPRL.
 *
 * @constructor Construct a record type with the specified type name,
 *              list of field declarations, and size.
 */
class RecordType(typeName: String, fieldDecls: List<FieldDecl>) : Type(typeName, fieldDecls.sumOf { it.size }) {
    // Use a hash map for efficient lookup of field names.
    private var fieldNameMap = HashMap<String, FieldDecl>()

    init {
        var nextOffset = 0
        for (fieldDecl in fieldDecls) {
            fieldNameMap[fieldDecl.idToken.text] = fieldDecl
            fieldDecl.offset = nextOffset
            nextOffset += fieldDecl.size
        }
    }

    /**
     * Returns the field declaration associated with the identifier string.
     * Returns null if the identifier string is not found.
     */
    operator fun get(idStr: String): FieldDecl? = fieldNameMap[idStr]
}
