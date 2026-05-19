/**
 * Copyright (c) 2019 by Delphix. All rights reserved.
 */

package com.delphix.sdk

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class Http(
    val engineAddress: String,
    private val debug: Boolean = false,
    private val versionMajor: Int = 1,
    private val versionMinor: Int = 7,
    private val versionMicro: Int = 0,
    private val timeout: Long = 60,
    private val timeoutUnit: TimeUnit = TimeUnit.MINUTES,
) {
    private val sessionResource: String = "/resources/json/delphix/session"
    private var jsessionId: String = ""

    // Single OkHttpClient instance reused across every request. OkHttp's connection
    // pool and thread pools are per-client, so constructing a builder per call (as
    // the previous implementation did) defeated keep-alive and leaked resources.
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .readTimeout(timeout, timeoutUnit)
            .build()

    private fun call(request: Request): ResponseBody {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected Code: $response")
        }
        checkCookie(response)
        // OkHttp 5 returns a non-nullable ResponseBody. The earlier review noted a
        // theoretical NPE risk here (#6); that risk does not exist with the current
        // okhttp version, but we keep the body access on its own line so future
        // upgrades that re-introduce nullability surface clearly.
        return response.body
    }

    private fun validateResponse(response: JSONObject) {
        if (debug) println(response)
        if (response.get("status") == "ERROR") {
            val error = response.getJSONObject("error")
            val details = error.get("details").toString()
            val action = error.get("action").toString()
            throw DelphixApiError(details, action)
        }
    }

    private fun requestSessions(): Map<String, Any> {
        val version = mapOf("type" to "APIVersion", "major" to versionMajor, "minor" to versionMinor, "micro" to versionMicro)
        return mapOf("type" to "APISession", "version" to version)
    }

    /**
     * Parse the `JSESSIONID` value out of the `Set-Cookie` header, tolerating malformed
     * input. The previous implementation did `cookies[0].split("=")[1]`, which crashed
     * with [IndexOutOfBoundsException] on any header that didn't contain an `=` (or that
     * led with a `;`). Now we bounds-check before indexing and silently ignore malformed
     * headers — they cannot legitimately establish a session anyway.
     */
    private fun checkCookie(r: Response) {
        val cookieDough = r.header("Set-Cookie")
        if (cookieDough.isNullOrEmpty()) {
            return
        }
        val first = cookieDough.split(";").firstOrNull() ?: return
        val eq = first.indexOf('=')
        if (eq <= 0 || eq == first.length - 1) {
            // No '=', or empty name, or empty value — not a usable cookie.
            return
        }
        jsessionId = first.substring(eq + 1)
    }

    fun setSession() {
        val json = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = JSONObject(requestSessions()).toString().toRequestBody(json)
        val request =
            Request.Builder()
                .url("$engineAddress$sessionResource")
                .post(requestBody)
                .build()
        val response = client.newCall(request).execute()
        checkCookie(response)
    }

    fun handleGet(url: String): JSONObject {
        if (debug) println(url)
        val request =
            Request.Builder()
                .url("$engineAddress$url")
                .addHeader("Cookie", "JSESSIONID=$jsessionId")
                .build()
        val response = call(request).asJsonObject()
        validateResponse(response)
        return response
    }

    fun handlePost(
        url: String,
        data: Map<String, Any?>,
    ): JSONObject {
        if (debug) println(url)
        val json = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = JSONObject(data).toString().toRequestBody(json)
        val request =
            Request.Builder()
                .url("$engineAddress$url")
                .addHeader("Cookie", "JSESSIONID=$jsessionId")
                .post(requestBody)
                .build()
        val response = call(request).asJsonObject()
        validateResponse(response)
        return response
    }

    fun handleDelete(url: String): JSONObject {
        if (debug) println(url)
        val request =
            Request.Builder()
                .url("$engineAddress$url")
                .addHeader("Cookie", "JSESSIONID=$jsessionId")
                .delete()
                .build()
        val response = call(request).asJsonObject()
        validateResponse(response)
        return response
    }

    companion object {
        fun ResponseBody.asString(): String {
            return this.string()
        }

        fun ResponseBody.asJsonObject(): JSONObject {
            return JSONObject(this.asString())
        }
    }
}
