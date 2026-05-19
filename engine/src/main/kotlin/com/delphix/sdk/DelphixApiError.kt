/*
 * Copyright Datadatdat.
 */

package com.delphix.sdk

/**
 * Typed exception thrown by [Http] when the Delphix engine returns an API error response
 * (i.e. `status == "ERROR"` in the response body).
 *
 * Preserves the structured `details` and `action` fields from the engine's APIError payload
 * so callers can inspect or surface them programmatically instead of parsing a stringified
 * concatenation.
 */
class DelphixApiError(
    val details: String,
    val action: String,
) : Exception("$details $action")
