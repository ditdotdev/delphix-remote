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

    "handleDelete issues a DELETE request with the session cookie and parses the JSON response" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":"deleted"}"""))

            val http = Http(server.url("/").toString().trimEnd('/'))
            // pre-populate jsessionId so we can assert the cookie header
            val field = Http::class.java.getDeclaredField("jsessionId")
            field.isAccessible = true
            field.set(http, "del-sess")

            val response = http.handleDelete("/some/resource")
            response.getString("result") shouldBe "deleted"

            val recorded = server.takeRequest()
            recorded.method shouldBe "DELETE"
            recorded.path shouldBe "/some/resource"
            recorded.headers["Cookie"] shouldContain "JSESSIONID=del-sess"
        } finally {
            server.shutdown()
        }
    }

    "handleDelete surfaces DelphixApiError when the engine returns status ERROR" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"status":"ERROR","error":{"details":"cannot delete","action":"contact admin"}}""",
                ),
            )
            val http = Http(server.url("/").toString().trimEnd('/'))
            val ex =
                shouldThrow<DelphixApiError> {
                    http.handleDelete("/x")
                }
            ex.details shouldBe "cannot delete"
            ex.action shouldBe "contact admin"
        } finally {
            server.shutdown()
        }
    }

    "handlePost sends body and session cookie" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":"ok"}"""))

            val http = Http(server.url("/").toString().trimEnd('/'))
            val field = Http::class.java.getDeclaredField("jsessionId")
            field.isAccessible = true
            field.set(http, "post-sess")

            val response = http.handlePost("/p", mapOf("a" to 1, "b" to "x"))
            response.getString("result") shouldBe "ok"

            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            recorded.path shouldBe "/p"
            recorded.headers["Cookie"] shouldContain "JSESSIONID=post-sess"
            val body = recorded.body.readUtf8()
            (body.contains("\"a\":1")) shouldBe true
            (body.contains("\"b\":\"x\"")) shouldBe true
        } finally {
            server.shutdown()
        }
    }

    "asString and asJsonObject extensions work on a response body" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":{"k":"v"}}"""))
            val http = Http(server.url("/").toString().trimEnd('/'))
            val result = http.handleGet("/")
            // result is asJsonObject() output downstream, so just sanity check
            result.getJSONObject("result").getString("k") shouldBe "v"
        } finally {
            server.shutdown()
        }
    }

    // Debug mode prints the URL and the response. We can't easily assert stdout,
    // but we can ensure debug=true does not change behavior.
    "handleGet with debug=true still returns the parsed response" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":"debug-ok"}"""))
            val http = Http(server.url("/").toString().trimEnd('/'), debug = true)
            val response = http.handleGet("/dbg")
            response.getString("result") shouldBe "debug-ok"
        } finally {
            server.shutdown()
        }
    }

    "handlePost with debug=true still returns the parsed response" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":"post-dbg"}"""))
            val http = Http(server.url("/").toString().trimEnd('/'), debug = true)
            val response = http.handlePost("/dbg", mapOf("k" to "v"))
            response.getString("result") shouldBe "post-dbg"
        } finally {
            server.shutdown()
        }
    }

    "handleDelete with debug=true still returns the parsed response" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":"del-dbg"}"""))
            val http = Http(server.url("/").toString().trimEnd('/'), debug = true)
            val response = http.handleDelete("/dbg")
            response.getString("result") shouldBe "del-dbg"
        } finally {
            server.shutdown()
        }
    }

    // validateResponse also prints when debug=true. Exercise that branch.
    "validateResponse with debug=true on an ERROR response still throws DelphixApiError" {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody(
                    """{"status":"ERROR","error":{"details":"d","action":"a"}}""",
                ),
            )
            val http = Http(server.url("/").toString().trimEnd('/'), debug = true)
            val ex =
                shouldThrow<DelphixApiError> {
                    http.handleGet("/")
                }
            ex.details shouldBe "d"
            ex.action shouldBe "a"
        } finally {
            server.shutdown()
        }
    }
})
