package edu.citadel.cprl

/**
 * This class encapsulates the language concept of an array type
 * in the programming language CPRL.
 *
 * @constructor Construct an array type with the specified type name, number
 *              of elements, and the type of elements contained in the array.
 */
class ArrayType(typeName: String, numElements: Int, val elementType: Type) :
    Type(typeName, numElements * elementType.size)
