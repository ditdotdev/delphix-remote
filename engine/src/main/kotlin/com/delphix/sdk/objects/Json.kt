// Copyright Dit 2026
// SPDX-License-Identifier: BUSL-1.1
package com.delphix.sdk.objects

/**
 * A dummy schema that is used to represent JSON.
 */
interface Json {
    val type: String // "Object type."

    fun toMap(): Map<String, Any?>
}
