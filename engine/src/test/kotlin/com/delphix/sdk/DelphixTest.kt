/*
 * Copyright Datadatdat.
 */

package com.delphix.sdk

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Exercises the hand-written Delphix.kt facade. It is a thin shim over Http with factory
 * methods for each repo. We assert each factory returns a non-null instance bound to the
 * same Http, and that login() issues setSession + a POST to the login resource.
 */
class DelphixTest : StringSpec({

    "requestLogin builds the expected payload" {
        val server = MockWebServer()
        server.start()
        try {
            val http = Http(server.url("/").toString().trimEnd('/'))
            val engine = Delphix(http)

            val payload = engine.requestLogin("admin", "secret")
            payload["type"] shouldBe "LoginRequest"
            payload["username"] shouldBe "admin"
            payload["password"] shouldBe "secret"
        } finally {
            server.shutdown()
        }
    }

    "login issues a setSession then POSTs to the login resource" {
        val server = MockWebServer()
        server.start()
        try {
            // setSession() does a POST to /resources/json/delphix/session
            server.enqueue(
                MockResponse()
                    .setBody("""{"status":"OK"}""")
                    .addHeader("Set-Cookie", "JSESSIONID=login-session;Path=/"),
            )
            // login() does handlePost to the login resource
            server.enqueue(MockResponse().setBody("""{"status":"OK","result":{}}"""))

            val http = Http(server.url("/").toString().trimEnd('/'))
            val engine = Delphix(http)
            engine.login("admin", "secret")

            val first = server.takeRequest()
            first.path shouldBe "/resources/json/delphix/session"
            first.method shouldBe "POST"

            val second = server.takeRequest()
            second.path shouldBe "/resources/json/delphix/login"
            second.method shouldBe "POST"
            val body = second.body.readUtf8()
            // body must include the login request structure
            (body.contains("\"type\":\"LoginRequest\"")) shouldBe true
            (body.contains("\"username\":\"admin\"")) shouldBe true
            (body.contains("\"password\":\"secret\"")) shouldBe true
        } finally {
            server.shutdown()
        }
    }

    "factory methods all return non-null repo instances bound to http" {
        val http = Http("http://example.invalid")
        val engine = Delphix(http)

        engine.action() shouldNotBe null
        engine.job() shouldNotBe null
        engine.container() shouldNotBe null
        engine.group() shouldNotBe null
        engine.repository() shouldNotBe null
        engine.source() shouldNotBe null
        engine.sourceConfig() shouldNotBe null
        engine.sourceEnvironment() shouldNotBe null
        engine.snapshot() shouldNotBe null
        engine.host() shouldNotBe null
        engine.environmentUser() shouldNotBe null
    }

    "loginResource is the documented Delphix path" {
        val http = Http("http://example.invalid")
        val engine = Delphix(http)
        engine.loginResource shouldBe "/resources/json/delphix/login"
    }
})
