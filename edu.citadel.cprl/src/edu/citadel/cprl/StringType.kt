package edu.citadel.cprl

/**
 * This class encapsulates the language concept of a string type in
 * the programming language CPRL.  A string is essentially a record
 * with two components as follows:
 *   `record { length : Integer, data : array[capacity] of Char }`
 *
 * @constructor Construct a string type with the specified type name and capacity.
 */
class StringType(typeName: String, val capacity: Int) : Type(typeName, Integer.size + capacity * Char.size)
{
    /**
     * Construct a string type with the specified capacity.  The type name
     * is "string[capacity]".  This constructor is used for string literals.
     */
    constructor(capacity: Int) : this("string[$capacity]", capacity)
}
