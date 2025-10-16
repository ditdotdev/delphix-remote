/**
 */
package com.delphix.sdk.objects

/**
 * A dummy schema that is used to represent JSON that is a valid Draft v4 schema.
 */
interface SchemaDraftV4 {
    val type: String // "Object type."

    fun toMap(): Map<String, Any?>
}
