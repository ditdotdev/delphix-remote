/*
 * Copyright Datadatdat.
 */

package com.datadatdat.remote.delphix.server

import com.datadatdat.remote.RemoteOperation
import com.datadatdat.remote.RemoteOperationType
import com.delphix.sdk.Delphix
import com.delphix.sdk.Http
import com.delphix.sdk.repos.Source
import com.delphix.sdk.repos.SourceConfig
import io.kotlintest.TestCaseOrder
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import org.json.JSONObject
import kotlin.IllegalArgumentException

class DelphixRemoteServerTest : StringSpec() {
    @SpyK
    var client = DelphixRemoteServer()

    override fun testCaseOrder() = TestCaseOrder.Random

    init {
        "get provider returns engine" {
            client.getProvider() shouldBe "engine"
        }

        "validate remote succeeds with only required properties" {
            val result = client.validateRemote(mapOf("address" to "host", "username" to "admin", "repository" to "repo"))
            result["address"] shouldBe "host"
            result["username"] shouldBe "admin"
            result["repository"] shouldBe "repo"
        }

        "validate remote succeeds with all properties" {
            val result =
                client.validateRemote(
                    mapOf(
                        "address" to "host",
                        "username" to "admin",
                        "repository" to "repo",
                        "password" to "password",
                    ),
                )
            result["address"] shouldBe "host"
            result["username"] shouldBe "admin"
            result["repository"] shouldBe "repo"
            result["password"] shouldBe "password"
        }

        "validate remote fails with missing required property" {
            shouldThrow<IllegalArgumentException> {
                client.validateRemote(mapOf("address" to "host", "username" to "admin"))
            }
        }

        "validate remote fails with invalid property" {
            shouldThrow<IllegalArgumentException> {
                client.validateRemote(mapOf("address" to "host", "username" to "admin", "repository" to "repo", "foo" to "bar"))
            }
        }

        "validate parameters succeeds with empty properties" {
            val result = client.validateParameters(emptyMap())
            result.size shouldBe 0
        }

        "validate parameters succeeds with password" {
            val result = client.validateParameters(mapOf("password" to "password"))
            result["password"] shouldBe "password"
        }

        "validate parameters fails with invalid property" {
            shouldThrow<IllegalArgumentException> {
                client.validateRemote(mapOf("foo" to "bar"))
            }
        }

        // ---------------------------------------------------------------
        // #4 — null-safe replacements for !! in DelphixRemoteServer
        // ---------------------------------------------------------------

        "getParameters throws IllegalStateException with engine address when no source matches container" {
            val http = mockk<Http>(relaxed = true)
            every { http.engineAddress } returns "http://engine.example"
            val engine = mockk<Delphix>(relaxed = true)
            every { engine.http } returns http

            val sourceRepo = mockk<Source>()
            every { engine.source() } returns sourceRepo
            // source list returns an empty result array — no match for the container
            every { sourceRepo.list() } returns JSONObject("""{"result":[]}""")

            val ex =
                shouldThrow<IllegalStateException> {
                    client.getParameters(engine, "container-ref-missing")
                }
            ex.message!! shouldContain "container-ref-missing"
            ex.message!! shouldContain "http://engine.example"
        }

        "buildSource throws IllegalArgumentException when PUSH operation has no commit" {
            val operation =
                RemoteOperation(
                    { _, _, _ -> },
                    mapOf("repository" to "repo"),
                    emptyMap(),
                    "op-id-123",
                    "commit-id-1",
                    // commit is null
                    null,
                    RemoteOperationType.PUSH,
                )
            val ex =
                shouldThrow<IllegalArgumentException> {
                    client.buildSource(operation)
                }
            ex.message!! shouldContain "op-id-123"
            ex.message!! shouldContain "commit metadata"
        }

        "buildSource succeeds for PUSH with commit metadata" {
            val operation =
                RemoteOperation(
                    { _, _, _ -> },
                    mapOf("repository" to "repo"),
                    emptyMap(),
                    "op-id-456",
                    "commit-id-2",
                    mapOf("key" to "value"),
                    RemoteOperationType.PUSH,
                )
            val source = client.buildSource(operation)
            source.name shouldBe "op-id-456"
        }

        "buildSource succeeds for PULL without commit metadata" {
            val operation =
                RemoteOperation(
                    { _, _, _ -> },
                    mapOf("repository" to "repo"),
                    emptyMap(),
                    "op-id-789",
                    "commit-id-3",
                    null,
                    RemoteOperationType.PULL,
                )
            val source = client.buildSource(operation)
            source.name shouldBe "op-id-789"
        }

        "syncDataEnd throws IllegalStateException when source for sync container is missing" {
            val http = mockk<Http>(relaxed = true)
            every { http.engineAddress } returns "http://engine.example"
            val engine = mockk<Delphix>(relaxed = true)
            every { engine.http } returns http

            val sourceRepo = mockk<Source>(relaxed = true)
            every { engine.source() } returns sourceRepo
            every { sourceRepo.list() } returns JSONObject("""{"result":[]}""")

            // container().sync(...) must return an action result, and waitForJob inspects it.
            // Simpler: stub action/job read to return a COMPLETED state so waitForJob returns.
            val container = mockk<com.delphix.sdk.repos.Container>(relaxed = true)
            every { engine.container() } returns container
            every { container.sync(any(), any()) } returns
                JSONObject(
                    """{"action":"a-1","job":"j-1","result":"r-1"}""",
                )
            val action = mockk<com.delphix.sdk.repos.Action>(relaxed = true)
            every { engine.action() } returns action
            every { action.read("a-1") } returns
                JSONObject(
                    """{"result":{"state":"COMPLETED","reference":"a-1","title":"done"}}""",
                )

            val opData =
                DelphixRemoteServer.EngineOperation(
                    engine = engine,
                    operationRef = "missing-container",
                    sshAddress = "1.2.3.4",
                    sshUser = "u",
                    sshKey = "k",
                )
            val operation =
                RemoteOperation(
                    { _, _, _ -> },
                    mapOf("repository" to "repo", "address" to "1.2.3.4"),
                    emptyMap(),
                    "op-x",
                    "commit-z",
                    mapOf("k" to "v"),
                    RemoteOperationType.PUSH,
                )

            val ex =
                shouldThrow<IllegalStateException> {
                    client.syncDataEnd(operation, opData, isSuccessful = true)
                }
            ex.message!! shouldContain "missing-container"
            ex.message!! shouldContain "http://engine.example"
        }

        "getParameters succeeds when a matching source exists" {
            val http = mockk<Http>(relaxed = true)
            every { http.engineAddress } returns "http://engine.example"
            val engine = mockk<Delphix>(relaxed = true)
            every { engine.http } returns http

            val sourceRepo = mockk<Source>()
            every { engine.source() } returns sourceRepo
            every { sourceRepo.list() } returns
                JSONObject(
                    """{"result":[{"container":"my-container","config":"my-config"}]}""",
                )

            val sourceConfigRepo = mockk<SourceConfig>()
            every { engine.sourceConfig() } returns sourceConfigRepo
            every { sourceConfigRepo.read("my-config") } returns
                JSONObject(
                    """{"result":{"parameters":{"sshUser":"u","sshKey":"k"}}}""",
                )

            val params = client.getParameters(engine, "my-container")
            params.getString("sshUser") shouldBe "u"
            params.getString("sshKey") shouldBe "k"
        }
    }
}
