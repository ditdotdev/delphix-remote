/**
 */
package com.delphix.sdk.objects

/**
 * A dummy schema that is used to represent JSON.
 */
interface Json {
    val type: String // "Object type."

    fun toMap(): Map<String, Any?>
}
