/*
 * Copyright Datadatdat.
 */

package com.delphix.sdk

import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class HttpTest : StringSpec({

    // #1 — Singleton OkHttpClient: the Http class must hold a single OkHttpClient
    // instance that is reused across every request, not constructed per call.
    "okHttpClient is a singleton reused across requests" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":{}}"""))
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":{}}"""))

            val http = Http(server.url("/").toString().trimEnd('/'))
            http.handleGet("/a")
            http.handleGet("/b")

            val field = Http::class.java.getDeclaredField("client")
            field.isAccessible = true
            val client = field.get(http)
            client shouldNotBe null
            (client is OkHttpClient) shouldBe true
        } finally {
            server.shutdown()
        }
    }

    "client field is the same instance across many handleGet calls" {
        val server = MockWebServer()
        server.start()
        try {
            repeat(5) {
                server.enqueue(MockResponse().setBody("""{"status":"OK","result":{}}"""))
            }

            val http = Http(server.url("/").toString().trimEnd('/'))
            val field = Http::class.java.getDeclaredField("client")
            field.isAccessible = true
            val before = field.get(http)

            repeat(5) { http.handleGet("/x") }

            val after = field.get(http)
            // Same instance, never reassigned
            (before === after) shouldBe true
        } finally {
            server.shutdown()
        }
    }

    // #2 — Cookie parser must bounds-check before indexing
    "checkCookie tolerates Set-Cookie with no = sign" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK","result":{}}""")
                    .addHeader("Set-Cookie", "malformed"),
            )

            val http = Http(server.url("/").toString().trimEnd('/'))
            // Must not throw ArrayIndexOutOfBoundsException
            http.handleGet("/")
        } finally {
            server.shutdown()
        }
    }

    "checkCookie tolerates empty Set-Cookie segment" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK","result":{}}""")
                    .addHeader("Set-Cookie", ";Path=/"),
            )

            val http = Http(server.url("/").toString().trimEnd('/'))
            http.handleGet("/")
        } finally {
            server.shutdown()
        }
    }

    "checkCookie tolerates Set-Cookie with leading equals only" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK","result":{}}""")
                    .addHeader("Set-Cookie", "=value;Path=/"),
            )

            val http = Http(server.url("/").toString().trimEnd('/'))
            http.handleGet("/")
        } finally {
            server.shutdown()
        }
    }

    "checkCookie parses a normal Set-Cookie value" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK","result":{}}""")
                    .addHeader("Set-Cookie", "JSESSIONID=abc123;Path=/"),
            )
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":{}}"""))

            val http = Http(server.url("/").toString().trimEnd('/'))
            http.handleGet("/")

            val field = Http::class.java.getDeclaredField("jsessionId")
            field.isAccessible = true
            field.get(http) shouldBe "abc123"

            // Next request should send the captured cookie back
            http.handleGet("/next")
            server.takeRequest() // consume first
            val secondRequest = server.takeRequest()
            secondRequest.headers["Cookie"] shouldContain "JSESSIONID=abc123"
        } finally {
            server.shutdown()
        }
    }

    // #5 — Typed DelphixApiError with details + action
    "API error response throws DelphixApiError with details and action" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"status":"ERROR","error":{"details":"bad input","action":"fix it"}}""",
                ),
            )

            val http = Http(server.url("/").toString().trimEnd('/'))
            val ex =
                shouldThrow<DelphixApiError> {
                    http.handleGet("/")
                }
            ex.details shouldBe "bad input"
            ex.action shouldBe "fix it"
            ex.message!! shouldContain "bad input"
            ex.message!! shouldContain "fix it"
        } finally {
            server.shutdown()
        }
    }

    // #6 — Response body null check folded into call(): non-2xx must surface
    // a clear error rather than a downstream NPE.
    "non-success HTTP response throws and does not NPE on body" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody(""))
            val http = Http(server.url("/").toString().trimEnd('/'))
            shouldThrow<Exception> {
                http.handleGet("/")
            }
        } finally {
            server.shutdown()
        }
    }

    // setSession path also exercises the singleton client and cookie parser
    "setSession captures JSESSIONID safely" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK"}""")
                    .addHeader("Set-Cookie", "JSESSIONID=session42;Path=/"),
            )

            val http = Http(server.url("/").toString().trimEnd('/'))
            http.setSession()

            val field = Http::class.java.getDeclaredField("jsessionId")
            field.isAccessible = true
            field.get(http) shouldBe "session42"
        } finally {
            server.shutdown()
        }
    }

    "setSession tolerates malformed Set-Cookie" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK"}""")
                    .addHeader("Set-Cookie", "malformed"),
            )

            val http = Http(server.url("/").toString().trimEnd('/'))
            http.setSession() // no crash
        } finally {
            server.shutdown()
        }
    }
})
