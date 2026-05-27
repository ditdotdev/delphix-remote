/*
 * Copyright Datadatdat.
 */

package com.delphix.sdk.repos

import com.delphix.sdk.Http
import com.delphix.sdk.objects.DeleteParameters
import com.delphix.sdk.objects.ProvisionParameters
import com.delphix.sdk.objects.SourceDisableParameters
import com.delphix.sdk.objects.SourceEnvironmentCreateParameters
import com.delphix.sdk.objects.SyncParameters
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import com.delphix.sdk.objects.APISession as APISessionObj
import com.delphix.sdk.objects.Container as ContainerObj
import com.delphix.sdk.objects.EnvironmentUser as EnvironmentUserObj
import com.delphix.sdk.objects.Group as GroupObj
import com.delphix.sdk.objects.Host as HostObj
import com.delphix.sdk.objects.Job as JobObj
import com.delphix.sdk.objects.Source as SourceObj
import com.delphix.sdk.objects.SourceConfig as SourceConfigObj
import com.delphix.sdk.objects.SourceEnvironment as SourceEnvironmentObj
import com.delphix.sdk.objects.TimeflowSnapshot as TimeflowSnapshotObj

/**
 * MockWebServer-backed coverage for the generated repository classes. For each repo
 * we exercise every CRUD method, asserting:
 *   - the HTTP method (GET / POST / DELETE)
 *   - the request path (so the repo's `root` is correct)
 *   - the parsed JSON response is surfaced to the caller
 */
class ReposTest : StringSpec({

    fun newServer() = MockWebServer().apply { start() }

    fun http(server: MockWebServer): Http = Http(server.url("/").toString().trimEnd('/'))

    fun okJson(body: String = """{"status":"OK","result":{}}""") = MockResponse().setBody(body)

    "APISession repo: read issues GET and create issues POST" {
        val server = newServer()
        try {
            server.enqueue(okJson("""{"status":"OK","result":{"r":1}}"""))
            server.enqueue(okJson("""{"status":"OK","result":{"r":2}}"""))

            val repo = APISession(http(server))
            repo.root shouldBe "/resources/json/delphix/session"
            repo.read("CURRENT").getJSONObject("result").getInt("r") shouldBe 1

            val payload = APISessionObj(client = "test")
            repo.create(payload).getJSONObject("result").getInt("r") shouldBe 2

            val first = server.takeRequest()
            first.method shouldBe "GET"
            first.path shouldBe "/resources/json/delphix/session/CURRENT"
            val second = server.takeRequest()
            second.method shouldBe "POST"
            second.path shouldBe "/resources/json/delphix/session"
            second.body.readUtf8().contains("\"type\":\"APISession\"") shouldBe true
        } finally {
            server.shutdown()
        }
    }

    "Action repo: list and read both issue GET" {
        val server = newServer()
        try {
            server.enqueue(okJson())
            server.enqueue(okJson())

            val repo = Action(http(server))
            repo.root shouldBe "/resources/json/delphix/action"
            repo.list() shouldNotBe null
            repo.read("ACTION-1") shouldNotBe null

            server.takeRequest().path shouldBe "/resources/json/delphix/action"
            server.takeRequest().path shouldBe "/resources/json/delphix/action/ACTION-1"
        } finally {
            server.shutdown()
        }
    }

    "Container repo: every method hits the expected endpoint" {
        val server = newServer()
        try {
            repeat(6) { server.enqueue(okJson()) }

            val repo = Container(http(server))
            repo.root shouldBe "/resources/json/delphix/database"

            repo.list()
            repo.read("C-1")
            repo.update("C-1", com.delphix.sdk.objects.AppDataContainer())
            repo.delete("C-1", DeleteParameters())
            repo.provision("C-1", PStub())
            repo.sync("C-1", SyncParameters())

            val r1 = server.takeRequest()
            r1.method shouldBe "GET"
            r1.path shouldBe "/resources/json/delphix/database"

            val r2 = server.takeRequest()
            r2.method shouldBe "GET"
            r2.path shouldBe "/resources/json/delphix/database/C-1"

            val r3 = server.takeRequest()
            r3.method shouldBe "POST"
            r3.path shouldBe "/resources/json/delphix/database/C-1"

            val r4 = server.takeRequest()
            r4.method shouldBe "POST"
            r4.path shouldBe "/resources/json/delphix/database/C-1/delete"

            val r5 = server.takeRequest()
            r5.method shouldBe "POST"
            r5.path shouldBe "/resources/json/delphix/database/C-1"

            val r6 = server.takeRequest()
            r6.method shouldBe "POST"
            r6.path shouldBe "/resources/json/delphix/database/C-1/sync"
        } finally {
            server.shutdown()
        }
    }

    "EnvironmentUser repo: list, read, create, update, delete" {
        val server = newServer()
        try {
            repeat(5) { server.enqueue(okJson()) }

            val repo = EnvironmentUser(http(server))
            repo.root shouldBe "/resources/json/delphix/environment/user"

            repo.list()
            repo.read("EU-1")
            repo.create(EnvironmentUserObj())
            repo.update("EU-1", EnvironmentUserObj())
            repo.delete("EU-1")

            server.takeRequest().method shouldBe "GET"
            server.takeRequest().method shouldBe "GET"
            server.takeRequest().method shouldBe "POST"
            server.takeRequest().method shouldBe "POST"
            // delete is a POST with empty body in this repo
            val del = server.takeRequest()
            del.method shouldBe "POST"
            del.path shouldBe "/resources/json/delphix/environment/user/EU-1"
        } finally {
            server.shutdown()
        }
    }

    "Group repo: list, read, create, update, delete" {
        val server = newServer()
        try {
            repeat(5) { server.enqueue(okJson()) }

            val repo = Group(http(server))
            repo.root shouldBe "/resources/json/delphix/group"

            repo.list()
            repo.read("G-1")
            repo.create(GroupObj(name = "g"))
            repo.update("G-1", GroupObj(name = "g2"))
            repo.delete("G-1")

            server.takeRequest().path shouldBe "/resources/json/delphix/group"
            server.takeRequest().path shouldBe "/resources/json/delphix/group/G-1"
            val create = server.takeRequest()
            create.method shouldBe "POST"
            create.path shouldBe "/resources/json/delphix/group"
            create.body.readUtf8().contains("\"name\":\"g\"") shouldBe true
            val update = server.takeRequest()
            update.method shouldBe "POST"
            update.path shouldBe "/resources/json/delphix/group/G-1"
            val del = server.takeRequest()
            del.method shouldBe "POST"
            del.path shouldBe "/resources/json/delphix/group/G-1"
        } finally {
            server.shutdown()
        }
    }

    "Host repo: list, read, update" {
        val server = newServer()
        try {
            repeat(3) { server.enqueue(okJson()) }

            val repo = Host(http(server))
            repo.root shouldBe "/resources/json/delphix/host"

            repo.list()
            repo.read("H-1")
            repo.update("H-1", HStub())

            server.takeRequest().path shouldBe "/resources/json/delphix/host"
            server.takeRequest().path shouldBe "/resources/json/delphix/host/H-1"
            val u = server.takeRequest()
            u.method shouldBe "POST"
            u.path shouldBe "/resources/json/delphix/host/H-1"
        } finally {
            server.shutdown()
        }
    }

    "Job repo: list, read, update" {
        val server = newServer()
        try {
            repeat(3) { server.enqueue(okJson()) }

            val repo = Job(http(server))
            repo.root shouldBe "/resources/json/delphix/job"

            repo.list()
            repo.read("J-1")
            repo.update("J-1", JobObj(emailAddresses = emptyList(), events = emptyList()))

            server.takeRequest().path shouldBe "/resources/json/delphix/job"
            server.takeRequest().path shouldBe "/resources/json/delphix/job/J-1"
            val u = server.takeRequest()
            u.method shouldBe "POST"
            u.path shouldBe "/resources/json/delphix/job/J-1"
        } finally {
            server.shutdown()
        }
    }

    "Repository repo: list parses items via fromJson and get parses a single item" {
        val server = newServer()
        try {
            val listBody =
                """
                {"status":"OK","result":[
                  {"type":"T","reference":"R1","name":"N1","environment":"E","linkingEnabled":true,"provisioningEnabled":true,"staging":false,"version":"v"},
                  {"type":"T","reference":"R2","name":"N2","environment":"E","linkingEnabled":false,"provisioningEnabled":false,"staging":true,"version":"v"}
                ]}
                """.trimIndent()
            server.enqueue(MockResponse().setBody(listBody))
            val singleBody =
                """
                {"status":"OK","result":{
                  "type":"T","reference":"R3","name":"N3","environment":"E",
                  "linkingEnabled":true,"provisioningEnabled":true,"staging":false,"version":"v"
                }}
                """.trimIndent()
            server.enqueue(MockResponse().setBody(singleBody))

            val repo = Repository(http(server))
            repo.resource shouldBe "/resources/json/delphix/repository"

            val list = repo.list()
            list.size shouldBe 2
            list[0].reference shouldBe "R1"
            list[1].reference shouldBe "R2"
            list[1].staging shouldBe true

            val single = repo.get("R3")
            single.reference shouldBe "R3"
            single.name shouldBe "N3"

            server.takeRequest().path shouldBe "/resources/json/delphix/repository"
            server.takeRequest().path shouldBe "/resources/json/delphix/repository/R3"
        } finally {
            server.shutdown()
        }
    }

    "Source repo: list, read, update, disable" {
        val server = newServer()
        try {
            repeat(4) { server.enqueue(okJson()) }

            val repo = Source(http(server))
            repo.root shouldBe "/resources/json/delphix/source"

            repo.list()
            repo.read("S-1")
            repo.update("S-1", SStub())
            repo.disable("S-1", SourceDisableParameters())

            server.takeRequest().path shouldBe "/resources/json/delphix/source"
            server.takeRequest().path shouldBe "/resources/json/delphix/source/S-1"
            val u = server.takeRequest()
            u.method shouldBe "POST"
            u.path shouldBe "/resources/json/delphix/source/S-1"
            val d = server.takeRequest()
            d.method shouldBe "POST"
            d.path shouldBe "/resources/json/delphix/source/S-1/disable"
        } finally {
            server.shutdown()
        }
    }

    "SourceConfig repo: list, read, create, update, delete" {
        val server = newServer()
        try {
            repeat(5) { server.enqueue(okJson()) }

            val repo = SourceConfig(http(server))
            repo.root shouldBe "/resources/json/delphix/sourceconfig"

            repo.list()
            repo.read("SC-1")
            repo.create(SCStub())
            repo.update("SC-1", SCStub())
            repo.delete("SC-1")

            server.takeRequest().path shouldBe "/resources/json/delphix/sourceconfig"
            server.takeRequest().path shouldBe "/resources/json/delphix/sourceconfig/SC-1"
            val c = server.takeRequest()
            c.method shouldBe "POST"
            c.path shouldBe "/resources/json/delphix/sourceconfig"
            val u = server.takeRequest()
            u.method shouldBe "POST"
            u.path shouldBe "/resources/json/delphix/sourceconfig/SC-1"
            val d = server.takeRequest()
            d.method shouldBe "POST"
            d.path shouldBe "/resources/json/delphix/sourceconfig/SC-1"
        } finally {
            server.shutdown()
        }
    }

    "SourceEnvironment repo: list, read, create, update, delete uses HTTP DELETE" {
        val server = newServer()
        try {
            repeat(5) { server.enqueue(okJson()) }

            val repo = SourceEnvironment(http(server))
            repo.root shouldBe "/resources/json/delphix/environment"

            repo.list()
            repo.read("E-1")
            repo.create(SECreateStub())
            repo.update("E-1", SEStub())
            repo.delete("E-1")

            server.takeRequest().path shouldBe "/resources/json/delphix/environment"
            server.takeRequest().path shouldBe "/resources/json/delphix/environment/E-1"
            val c = server.takeRequest()
            c.method shouldBe "POST"
            c.path shouldBe "/resources/json/delphix/environment"
            val u = server.takeRequest()
            u.method shouldBe "POST"
            u.path shouldBe "/resources/json/delphix/environment/E-1"
            val d = server.takeRequest()
            d.method shouldBe "DELETE"
            d.path shouldBe "/resources/json/delphix/environment/E-1"
        } finally {
            server.shutdown()
        }
    }

    "TimeflowSnapshot repo: list, read, update, delete" {
        val server = newServer()
        try {
            repeat(4) { server.enqueue(okJson()) }

            val repo = TimeflowSnapshot(http(server))
            repo.root shouldBe "/resources/json/delphix/snapshot"

            repo.list()
            repo.read("T-1")
            repo.update("T-1", TSStub())
            repo.delete("T-1")

            server.takeRequest().path shouldBe "/resources/json/delphix/snapshot"
            server.takeRequest().path shouldBe "/resources/json/delphix/snapshot/T-1"
            val u = server.takeRequest()
            u.method shouldBe "POST"
            u.path shouldBe "/resources/json/delphix/snapshot/T-1"
            val d = server.takeRequest()
            d.method shouldBe "POST"
            d.path shouldBe "/resources/json/delphix/snapshot/T-1"
        } finally {
            server.shutdown()
        }
    }
})

// --- Test-only stub implementations of interfaces that the repos accept as payload ---

/**
 * A minimal [ContainerObj] implementation used to satisfy the Container repo's
 * generic-typed update signature. We pass an AppDataContainer instance in the actual
 * test (it satisfies the contract); these stubs cover the rarer interface-typed slots.
 */
private class HStub : HostObj {
    override val privilegeElevationProfile: String? = null
    override val sshPort: Int? = null
    override val hostRuntime: com.delphix.sdk.objects.HostRuntime? = null
    override val address: String? = null
    override val hostConfiguration: com.delphix.sdk.objects.HostConfiguration? = null
    override val dateAdded: String? = null
    override val name: String? = "h"
    override val reference: String? = "h-ref"
    override val namespace: String? = null
    override val type: String = "Host"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type, "name" to name)
}

private class SStub : SourceObj {
    override val virtual: Boolean? = false
    override val description: String? = "d"
    override val config: String? = "c"
    override val staging: Boolean? = false
    override val restoration: Boolean? = false
    override val linked: Boolean? = false
    override val status: String? = "RUNNING"
    override val runtime: com.delphix.sdk.objects.SourceRuntime? = null
    override val container: String? = null
    override val name: String? = "s"
    override val reference: String? = "s-ref"
    override val namespace: String? = null
    override val type: String = "Source"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type, "name" to name)
}

private class SCStub : SourceConfigObj {
    override val repository: String? = "r"
    override val discovered: Boolean? = false
    override val environmentUser: String? = "eu"
    override val linkingEnabled: Boolean? = false
    override val name: String? = "sc"
    override val reference: String? = "sc-ref"
    override val namespace: String? = null
    override val type: String = "SourceConfig"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type, "name" to name)
}

private class SEStub : SourceEnvironmentObj {
    override val primaryUser: String? = "u"
    override val description: String? = "d"
    override val enabled: Boolean? = true
    override val name: String? = "e"
    override val reference: String? = "e-ref"
    override val namespace: String? = null
    override val type: String = "SourceEnvironment"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type, "name" to name)
}

private class SECreateStub : SourceEnvironmentCreateParameters {
    override val primaryUser: com.delphix.sdk.objects.EnvironmentUser? = null
    override val type: String = "HostEnvironmentCreateParameters"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type)
}

private class TSStub : TimeflowSnapshotObj {
    override val container: String? = null
    override val temporary: Boolean? = null
    override val firstChangePoint: com.delphix.sdk.objects.TimeflowPoint? = null
    override val missingNonLoggedData: Boolean? = null
    override val creationTime: String? = null
    override val latestChangePoint: com.delphix.sdk.objects.TimeflowPoint? = null
    override val timezone: String? = null
    override val runtime: com.delphix.sdk.objects.SnapshotRuntime? = null
    override val consistency: String? = null
    override val timeflow: String? = null
    override val version: String? = null
    override val retention: Int? = null
    override val name: String? = "ts"
    override val reference: String? = "ts-ref"
    override val namespace: String? = null
    override val type: String = "TimeflowSnapshot"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type)
}

private class PStub : ProvisionParameters {
    override val timeflowPointParameters: com.delphix.sdk.objects.TimeflowPointParameters? = null
    override val maskingJob: String? = null
    override val container: ContainerObj? = null
    override val sourceConfig: SourceConfigObj? = null
    override val source: SourceObj? = null
    override val type: String = "ProvisionParameters"

    override fun toMap(): Map<String, Any?> = mapOf("type" to type)
}
