// Copyright Dit 2026
// SPDX-License-Identifier: BUSL-1.1
package com.delphix.sdk.objects

/**
 * Super schema for all other schemas.
 */
interface TypedObject {
    val type: String // "Object type."

    fun toMap(): Map<String, Any?>
}
