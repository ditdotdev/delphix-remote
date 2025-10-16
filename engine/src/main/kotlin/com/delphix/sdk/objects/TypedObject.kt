/**
 */
package com.delphix.sdk.objects

/**
 * Super schema for all other schemas.
 */
interface TypedObject {
    val type: String // "Object type."

    fun toMap(): Map<String, Any?>
}
