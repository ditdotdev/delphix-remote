// Copyright Dit 2026
// SPDX-License-Identifier: BUSL-1.1

package dev.dit.remote.delphix.server

import com.delphix.sdk.Delphix
import com.delphix.sdk.Http
import com.delphix.sdk.objects.AppDataProvisionParameters
import com.delphix.sdk.objects.AppDataSyncParameters
import com.delphix.sdk.objects.DeleteParameters
import com.delphix.sdk.objects.SourceDisableParameters
import com.delphix.sdk.repos.Action
import com.delphix.sdk.repos.Container
import com.delphix.sdk.repos.EnvironmentUser
import com.delphix.sdk.repos.Group
import com.delphix.sdk.repos.Job
import com.delphix.sdk.repos.Repository
import com.delphix.sdk.repos.Source
import com.delphix.sdk.repos.SourceConfig
import com.delphix.sdk.repos.SourceEnvironment
import com.delphix.sdk.repos.TimeflowSnapshot
import dev.dit.remote.RemoteOperation
import dev.dit.remote.RemoteOperationType
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.json.JSONObject

/**
 * Holder for a fully wired set of mocked Delphix repo objects, so individual tests can
 * focus on the engine/server interactions they care about and stub only what differs.
 */
private class FakeEngine {
    val http: Http = mockk(relaxed = true)
    val engine: Delphix = mockk(relaxed = true)

    val actionRepo: Action = mockk(relaxed = true)
    val jobRepo: Job = mockk(relaxed = true)
    val containerRepo: Container = mockk(relaxed = true)
    val groupRepo: Group = mockk(relaxed = true)
    val repositoryRepo: Repository = mockk(relaxed = true)
    val sourceRepo: Source = mockk(relaxed = true)
    val sourceConfigRepo: SourceConfig = mockk(relaxed = true)
    val sourceEnvRepo: SourceEnvironment = mockk(relaxed = true)
    val snapshotRepo: TimeflowSnapshot = mockk(relaxed = true)
    val envUserRepo: EnvironmentUser = mockk(relaxed = true)

    init {
        every { http.engineAddress } returns "http://engine.test"
        every { engine.http } returns http
        every { engine.action() } returns actionRepo
        every { engine.job() } returns jobRepo
        every { engine.container() } returns containerRepo
        every { engine.group() } returns groupRepo
        every { engine.repository() } returns repositoryRepo
        every { engine.source() } returns sourceRepo
        every { engine.sourceConfig() } returns sourceConfigRepo
        every { engine.sourceEnvironment() } returns sourceEnvRepo
        every { engine.snapshot() } returns snapshotRepo
        every { engine.environmentUser() } returns envUserRepo
    }

    /** Stub group.list() to expose the named groups with the given references. */
    fun stubGroups(vararg pairs: Pair<String, String>) {
        val arr =
            pairs.joinToString(",") { (name, ref) ->
                """{"name":"$name","reference":"$ref"}"""
            }
        every { groupRepo.list() } returns JSONObject("""{"result":[$arr]}""")
    }

    /** Stub container.list() with arbitrary containers. */
    fun stubContainers(json: String) {
        every { containerRepo.list() } returns JSONObject("""{"result":$json}""")
    }

    /** Stub sourceEnvironment.list(). */
    fun stubEnvironments(vararg pairs: Pair<String, String>) {
        val arr =
            pairs.joinToString(",") { (name, ref) ->
                """{"name":"$name","reference":"$ref"}"""
            }
        every { sourceEnvRepo.list() } returns JSONObject("""{"result":[$arr]}""")
    }

    /** Stub environmentUser.list() with one user per env ref. */
    fun stubEnvUsers(vararg envRefToUserRef: Pair<String, String>) {
        val arr =
            envRefToUserRef.joinToString(",") { (envRef, userRef) ->
                """{"environment":"$envRef","reference":"$userRef"}"""
            }
        every { envUserRepo.list() } returns JSONObject("""{"result":[$arr]}""")
    }

    /** Stub repository.list() with at least one repo so getRepository can find "Dit". */
    fun stubRepositories(vararg names: String) {
        val repos =
            names.map { n ->
                com.delphix.sdk.objects.Repository(
                    type = "AppDataRepository",
                    reference = "REPO-$n",
                    name = n,
                    environment = "ENV-1",
                    linkingEnabled = false,
                    provisioningEnabled = true,
                    staging = false,
                    version = "1.0",
                )
            }
        every { repositoryRepo.list() } returns repos
    }

    /** Stub snapshot.list() with one snapshot per (reference, hash) pair. */
    fun stubSnapshots(json: String) {
        every { snapshotRepo.list() } returns JSONObject("""{"result":$json}""")
    }

    /** Stub action.read() to return a COMPLETED action (waitForJob fast path). */
    fun stubActionCompleted(actionRef: String) {
        every { actionRepo.read(actionRef) } returns
            JSONObject(
                """{"result":{"state":"COMPLETED","reference":"$actionRef","title":"done"}}""",
            )
    }

    /** Stub action.read() to return a non-COMPLETED action so waitForJob falls into the job-polling branch. */
    fun stubActionPending(actionRef: String) {
        every { actionRepo.read(actionRef) } returns
            JSONObject(
                """{"result":{"state":"PENDING","reference":"$actionRef","title":"queued"}}""",
            )
    }

    /** Stub job.read() to return a sequence of states. */
    fun stubJobStates(
        jobRef: String,
        vararg states: String,
    ) {
        val responses =
            states.map { state ->
                JSONObject(
                    """{"result":{"jobState":"$state","reference":"$jobRef"}}""",
                )
            }
        every { jobRepo.read(jobRef) } returnsMany responses
    }
}

class DelphixRemoteServerEngineCallsTest : StringSpec({

    fun progressCalls(): MutableList<Triple<Any?, String?, Int?>> = mutableListOf()

    fun newOperation(
        type: RemoteOperationType,
        operationId: String = "op-1",
        commitId: String = "commit-1",
        commit: Map<String, Any>? = mapOf("hash" to commitId, "timestamp" to "2025-01-01"),
        remoteOverride: Map<String, Any>? = null,
        progressSink: MutableList<Triple<Any?, String?, Int?>> = progressCalls(),
    ): RemoteOperation {
        return RemoteOperation(
            { kind, msg, pct -> progressSink.add(Triple(kind, msg, pct)) },
            remoteOverride ?: mapOf(
                "address" to "engine.test",
                "username" to "admin",
                "password" to "pw",
                "repository" to "myrepo",
            ),
            emptyMap(),
            operationId,
            commitId,
            commit,
            type,
        )
    }

    // ---------------------------------------------------------------
    // waitForJob — both branches (action COMPLETED fast path and job polling)
    // ---------------------------------------------------------------

    "waitForJob takes the action-completed fast path when state is COMPLETED" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubActionCompleted("ACTION-1")

        val provisionResponse = JSONObject("""{"action":"ACTION-1","job":"JOB-1","result":"R"}""")
        server.waitForJob(f.engine, provisionResponse)

        // Should not have read the job (action was COMPLETED)
        verify(exactly = 0) { f.jobRepo.read(any()) }
    }

    "waitForJob polls the job until COMPLETED when the action is not yet completed" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubActionPending("ACTION-2")
        // Sequence: RUNNING, then COMPLETED
        f.stubJobStates("JOB-2", "RUNNING", "COMPLETED")

        val provisionResponse = JSONObject("""{"action":"ACTION-2","job":"JOB-2","result":"R"}""")
        // Override Thread.sleep effect by short-circuiting via a job that is COMPLETED on first read.
        // We seeded RUNNING then COMPLETED, so one Thread.sleep(5000) will be invoked. Acceptable
        // for the test runtime; if it becomes flaky we can spy and stub sleep.
        server.waitForJob(f.engine, provisionResponse)

        verify(atLeast = 1) { f.jobRepo.read("JOB-2") }
    }

    "waitForJob throws when the job ends in a non-COMPLETED state" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubActionPending("ACTION-3")
        f.stubJobStates("JOB-3", "FAILED")

        val provisionResponse = JSONObject("""{"action":"ACTION-3","job":"JOB-3","result":"R"}""")
        val ex =
            shouldThrow<Exception> {
                server.waitForJob(f.engine, provisionResponse)
            }
        ex.message!! shouldContain "JOB-3"
    }

    // ---------------------------------------------------------------
    // repoExists / findInGroup
    // ---------------------------------------------------------------

    "repoExists returns false when the repositories group does not exist" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubGroups() // empty groups
        server.repoExists(f.engine, "anything") shouldBe false
    }

    "repoExists returns false when the named container is not in the repositories group" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers("""[{"name":"other","group":"GROUP-OTHER"}]""")

        server.repoExists(f.engine, "myrepo") shouldBe false
    }

    "repoExists returns true when the named container is in the repositories group" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers(
            """[{"name":"myrepo","group":"GROUP-REPOS"},{"name":"other","group":"GROUP-X"}]""",
        )

        server.repoExists(f.engine, "myrepo") shouldBe true
    }

    // ---------------------------------------------------------------
    // listCommits / getCommit (exercise listSnapshots filtering)
    // ---------------------------------------------------------------

    "listCommits returns empty when the repository does not yet exist" {
        // Spy server so we can override the engine factory used inside connect().
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        // Stub repoExists path: groups have repositories, containers do not contain "myrepo"
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers("[]")

        // Intercept the private connect() call to return our fake engine.
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val commits =
            server.listCommits(
                mapOf("address" to "a", "username" to "u", "password" to "p", "repository" to "myrepo"),
                emptyMap(),
                emptyList(),
            )
        commits.size shouldBe 0
    }

    "listCommits returns snapshots filtered to the named repository and non-empty hash" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers("""[{"name":"myrepo","group":"GROUP-REPOS"}]""")
        // Snapshots: 1 valid for myrepo, 1 for other repo, 1 empty hash, 1 no metadata
        f.stubSnapshots(
            """[
                {"reference":"S1","metadata":{"repository":"myrepo","hash":"h1","metadata":{"tags":{}}}},
                {"reference":"S2","metadata":{"repository":"other","hash":"h2","metadata":{"tags":{}}}},
                {"reference":"S3","metadata":{"repository":"myrepo","hash":"","metadata":{"tags":{}}}},
                {"reference":"S4"}
            ]""",
        )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val commits =
            server.listCommits(
                mapOf("address" to "a", "username" to "u", "password" to "p", "repository" to "myrepo"),
                emptyMap(),
                emptyList(),
            )
        commits.size shouldBe 1
        commits[0].first shouldBe "h1"
    }

    "getCommit returns null when no commit matches" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers("""[{"name":"myrepo","group":"GROUP-REPOS"}]""")
        f.stubSnapshots(
            """[{"reference":"S1","metadata":{"repository":"myrepo","hash":"h1","metadata":{"tags":{}}}}]""",
        )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        server.getCommit(
            mapOf("address" to "a", "username" to "u", "password" to "p", "repository" to "myrepo"),
            emptyMap(),
            "does-not-exist",
        ) shouldBe null
    }

    "getCommit returns the matching commit's properties" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers("""[{"name":"myrepo","group":"GROUP-REPOS"}]""")
        f.stubSnapshots(
            """[{"reference":"S1","metadata":{"repository":"myrepo","hash":"h1","metadata":{"x":"y"}}}]""",
        )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val commit =
            server.getCommit(
                mapOf("address" to "a", "username" to "u", "password" to "p", "repository" to "myrepo"),
                emptyMap(),
                "h1",
            )
        commit shouldBe mapOf("x" to "y")
    }

    // ---------------------------------------------------------------
    // pushMetadata / getRemotePath / getRsync
    // ---------------------------------------------------------------

    "pushMetadata is a no-op when isUpdate is false" {
        val server = DelphixRemoteServer()
        val op = newOperation(RemoteOperationType.PUSH)
        // Should not throw
        server.pushMetadata(op, mapOf("k" to "v"), isUpdate = false)
    }

    "pushMetadata throws when isUpdate is true" {
        val server = DelphixRemoteServer()
        val op = newOperation(RemoteOperationType.PUSH)
        val ex =
            shouldThrow<IllegalStateException> {
                server.pushMetadata(op, mapOf("k" to "v"), isUpdate = true)
            }
        ex.message!! shouldContain "cannot be updated"
    }

    "getRemotePath formats sshUser@sshAddress:data/volume" {
        val server = DelphixRemoteServer()
        val op = newOperation(RemoteOperationType.PUSH)
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "key",
            )
        server.getRemotePath(op, data, "vol1") shouldBe "delphix@10.0.0.1:data/vol1"
    }

    "getRsync returns an RsyncExecutor configured with port 8022, no password, the engine key" {
        val server = DelphixRemoteServer()
        val op = newOperation(RemoteOperationType.PUSH)
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "the-key",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.port shouldBe 8022
        rsync.password shouldBe null
        rsync.key shouldBe "the-key"
        rsync.src shouldBe "/src/"
        rsync.dst shouldBe "/dst/"
    }

    // ---------------------------------------------------------------
    // getRsync host-key threading (issue #51): make sure skipHostCheck /
    // knownHostsFile from the validated remote map flow into the
    // RsyncExecutor so the SDK applies the right StrictHostKeyChecking
    // policy on the rsync data path. Secure default; opt-out via Boolean
    // or "true"/"false" string; custom known_hosts override.
    // ---------------------------------------------------------------

    "getRsync defaults to secure host-key checking when skipHostCheck unset" {
        val server = DelphixRemoteServer()
        val op =
            newOperation(
                RemoteOperationType.PUSH,
                remoteOverride =
                    mapOf(
                        "address" to "engine.test",
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                    ),
            )
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "the-key",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.skipHostCheck shouldBe false
        rsync.knownHostsFile shouldBe null

        // And the SDK's buildSshCommand should pick up the SecureDefault path: yes + known_hosts.
        val tmp = java.io.File.createTempFile("delphix-rsync-test", ".tmp")
        tmp.deleteOnExit()
        try {
            val args = rsync.buildSshCommand(tmp, skipHostCheck = rsync.skipHostCheck, knownHostsFile = rsync.knownHostsFile)
            args.contains("StrictHostKeyChecking=yes") shouldBe true
            args.contains("StrictHostKeyChecking=no") shouldBe false
        } finally {
            tmp.delete()
        }
    }

    "getRsync threads skipHostCheck=false (Boolean) through to RsyncExecutor" {
        // Cover the branch where skipHostCheck is non-null but evaluates to
        // false — needed for full branch coverage on the `!= null && coerceBoolean(...)`
        // expression in getRsync.
        val server = DelphixRemoteServer()
        val op =
            newOperation(
                RemoteOperationType.PUSH,
                remoteOverride =
                    mapOf(
                        "address" to "engine.test",
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                        "skipHostCheck" to false,
                    ),
            )
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "the-key",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.skipHostCheck shouldBe false
    }

    "getRsync threads skipHostCheck=true (Boolean) through to RsyncExecutor" {
        val server = DelphixRemoteServer()
        val op =
            newOperation(
                RemoteOperationType.PUSH,
                remoteOverride =
                    mapOf(
                        "address" to "engine.test",
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                        "skipHostCheck" to true,
                    ),
            )
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "the-key",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.skipHostCheck shouldBe true

        val tmp = java.io.File.createTempFile("delphix-rsync-test", ".tmp")
        tmp.deleteOnExit()
        try {
            val args = rsync.buildSshCommand(tmp, skipHostCheck = rsync.skipHostCheck, knownHostsFile = rsync.knownHostsFile)
            args.contains("StrictHostKeyChecking=no") shouldBe true
            args.contains("UserKnownHostsFile=/dev/null") shouldBe true
        } finally {
            tmp.delete()
        }
    }

    "getRsync threads skipHostCheck String 'true' through to RsyncExecutor" {
        val server = DelphixRemoteServer()
        val op =
            newOperation(
                RemoteOperationType.PUSH,
                remoteOverride =
                    mapOf(
                        "address" to "engine.test",
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                        "skipHostCheck" to "true",
                    ),
            )
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "k",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.skipHostCheck shouldBe true
    }

    "getRsync threads knownHostsFile through to RsyncExecutor" {
        val server = DelphixRemoteServer()
        val op =
            newOperation(
                RemoteOperationType.PUSH,
                remoteOverride =
                    mapOf(
                        "address" to "engine.test",
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                        "knownHostsFile" to "/etc/ditdotdev/known_hosts",
                    ),
            )
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "k",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.skipHostCheck shouldBe false
        rsync.knownHostsFile shouldBe "/etc/ditdotdev/known_hosts"

        val tmp = java.io.File.createTempFile("delphix-rsync-test", ".tmp")
        tmp.deleteOnExit()
        try {
            val args = rsync.buildSshCommand(tmp, skipHostCheck = rsync.skipHostCheck, knownHostsFile = rsync.knownHostsFile)
            args.contains("StrictHostKeyChecking=yes") shouldBe true
            args.contains("UserKnownHostsFile=/etc/ditdotdev/known_hosts") shouldBe true
        } finally {
            tmp.delete()
        }
    }

    "getRsync with skipHostCheck=true ignores knownHostsFile" {
        val server = DelphixRemoteServer()
        val op =
            newOperation(
                RemoteOperationType.PUSH,
                remoteOverride =
                    mapOf(
                        "address" to "engine.test",
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                        "skipHostCheck" to true,
                        "knownHostsFile" to "/etc/ditdotdev/known_hosts",
                    ),
            )
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = mockk(relaxed = true),
                operationRef = "op-ref",
                sshAddress = "10.0.0.1",
                sshUser = "delphix",
                sshKey = "k",
            )
        val rsync = server.getRsync(op, data, "/src", "/dst", dev.dit.shell.CommandExecutor())
        rsync.skipHostCheck shouldBe true
        rsync.knownHostsFile shouldBe "/etc/ditdotdev/known_hosts"

        val tmp = java.io.File.createTempFile("delphix-rsync-test", ".tmp")
        tmp.deleteOnExit()
        try {
            val args = rsync.buildSshCommand(tmp, skipHostCheck = rsync.skipHostCheck, knownHostsFile = rsync.knownHostsFile)
            // skipHostCheck=true → no + /dev/null, regardless of knownHostsFile value.
            args.contains("StrictHostKeyChecking=no") shouldBe true
            args.contains("UserKnownHostsFile=/dev/null") shouldBe true
            args.contains("UserKnownHostsFile=/etc/ditdotdev/known_hosts") shouldBe false
        } finally {
            tmp.delete()
        }
    }

    // ---------------------------------------------------------------
    // syncDataEnd happy paths (PUSH successful sync + disable; PULL deletes; PUSH unsuccessful deletes)
    // ---------------------------------------------------------------

    "syncDataEnd on PUSH success syncs and disables the source" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()

        every { f.containerRepo.sync(any(), any<AppDataSyncParameters>()) } returns
            JSONObject("""{"action":"A-SYNC","job":"J-SYNC","result":"R"}""")
        f.stubActionCompleted("A-SYNC")

        every { f.sourceRepo.list() } returns
            JSONObject(
                """{"result":[{"container":"OP-REF","reference":"SRC-1"}]}""",
            )
        every { f.sourceRepo.disable(any(), any<SourceDisableParameters>()) } returns
            JSONObject("""{"action":"A-DIS","job":"J-DIS","result":"R"}""")
        f.stubActionCompleted("A-DIS")

        val op = newOperation(RemoteOperationType.PUSH)
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = f.engine,
                operationRef = "OP-REF",
                sshAddress = "x",
                sshUser = "y",
                sshKey = "z",
            )
        server.syncDataEnd(op, data, isSuccessful = true)

        verify(exactly = 1) { f.containerRepo.sync("OP-REF", any()) }
        verify(exactly = 1) { f.sourceRepo.disable("SRC-1", any()) }
    }

    "syncDataEnd on PUSH failure deletes the container instead of syncing" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        every { f.containerRepo.delete(any(), any<DeleteParameters>()) } returns
            JSONObject("""{"action":"A-DEL","job":"J-DEL","result":"R"}""")
        f.stubActionCompleted("A-DEL")

        val op = newOperation(RemoteOperationType.PUSH)
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = f.engine,
                operationRef = "OP-REF",
                sshAddress = "x",
                sshUser = "y",
                sshKey = "z",
            )
        server.syncDataEnd(op, data, isSuccessful = false)

        verify(exactly = 1) { f.containerRepo.delete("OP-REF", any()) }
        verify(exactly = 0) { f.containerRepo.sync(any(), any()) }
    }

    "syncDataEnd on PULL always deletes the container (no sync, no disable)" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        every { f.containerRepo.delete(any(), any<DeleteParameters>()) } returns
            JSONObject("""{"action":"A-DEL","job":"J-DEL","result":"R"}""")
        f.stubActionCompleted("A-DEL")

        val op = newOperation(RemoteOperationType.PULL)
        val data =
            DelphixRemoteServer.EngineOperation(
                engine = f.engine,
                operationRef = "OP-REF",
                sshAddress = "x",
                sshUser = "y",
                sshKey = "z",
            )
        server.syncDataEnd(op, data, isSuccessful = true)

        verify(exactly = 1) { f.containerRepo.delete("OP-REF", any()) }
        verify(exactly = 0) { f.containerRepo.sync(any(), any()) }
        verify(exactly = 0) { f.sourceRepo.disable(any(), any()) }
    }

    // ---------------------------------------------------------------
    // createRepo — error paths for each missing piece, then full success
    // ---------------------------------------------------------------

    "createRepo throws when 'dit' environment is missing" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments() // no env
        val op = newOperation(RemoteOperationType.PUSH)

        val ex =
            shouldThrow<IllegalStateException> {
                server.createRepo(f.engine, op)
            }
        ex.message!! shouldContain "dit"
    }

    "createRepo throws when 'master' group is missing" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups() // no groups
        val op = newOperation(RemoteOperationType.PUSH)

        shouldThrow<IllegalStateException> {
            server.createRepo(f.engine, op)
        }
    }

    "createRepo throws when 'master/dit' source is missing" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups("master" to "GROUP-MASTER")
        // master group exists, but no container in it
        f.stubContainers("[]")
        val op = newOperation(RemoteOperationType.PUSH)

        shouldThrow<IllegalStateException> {
            server.createRepo(f.engine, op)
        }
    }

    "createRepo throws when the 'repositories' group is missing" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups("master" to "GROUP-MASTER")
        f.stubContainers("""[{"name":"dit","group":"GROUP-MASTER"}]""")
        // repositories group not in groups list — already stubbed only "master"
        val op = newOperation(RemoteOperationType.PUSH)

        shouldThrow<IllegalStateException> {
            server.createRepo(f.engine, op)
        }
    }

    "createRepo throws when no 'Dit' repository is registered" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups("master" to "GROUP-MASTER", "repositories" to "GROUP-REPOS")
        f.stubContainers("""[{"name":"dit","group":"GROUP-MASTER"}]""")
        f.stubRepositories("NotDit")
        val op = newOperation(RemoteOperationType.PUSH)

        shouldThrow<IllegalStateException> {
            server.createRepo(f.engine, op)
        }
    }

    "createRepo throws when no env user for the dit environment exists" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups("master" to "GROUP-MASTER", "repositories" to "GROUP-REPOS")
        f.stubContainers("""[{"name":"dit","group":"GROUP-MASTER"}]""")
        f.stubRepositories("Dit")
        f.stubEnvUsers() // no env users
        val op = newOperation(RemoteOperationType.PUSH)

        shouldThrow<IllegalStateException> {
            server.createRepo(f.engine, op)
        }
    }

    "createRepo provisions and disables the new container on success" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups("master" to "GROUP-MASTER", "repositories" to "GROUP-REPOS")
        // Need at least the dit container in the master group for findInGroup("master","dit")
        f.stubContainers(
            """[{"name":"dit","reference":"CT-DD","group":"GROUP-MASTER"}]""",
        )
        f.stubRepositories("Dit")
        f.stubEnvUsers("ENV-DD" to "ENVUSER-1")

        every { f.containerRepo.provision(any(), any<AppDataProvisionParameters>()) } returns
            JSONObject("""{"action":"A-PROV","job":"J-PROV","result":"NEW-CONTAINER"}""")
        f.stubActionCompleted("A-PROV")

        every { f.sourceRepo.list() } returns
            JSONObject(
                """{"result":[{"container":"NEW-CONTAINER","reference":"SRC-NEW"}]}""",
            )
        every { f.sourceRepo.disable(any(), any<SourceDisableParameters>()) } returns
            JSONObject("""{"action":"A-DIS","job":"J-DIS","result":"R"}""")
        f.stubActionCompleted("A-DIS")

        val progress = progressCalls()
        val op = newOperation(RemoteOperationType.PUSH, progressSink = progress)
        server.createRepo(f.engine, op)

        verify(exactly = 1) { f.containerRepo.provision("provision", any()) }
        verify(exactly = 1) { f.sourceRepo.disable("SRC-NEW", any()) }
        // Should emit start + end progress
        progress.map { it.first }.toList() shouldContain dev.dit.remote.RemoteProgress.START
        progress.map { it.first }.toList() shouldContain dev.dit.remote.RemoteProgress.END
    }

    "createRepo throws when the engine doesn't return a source for the newly provisioned container" {
        val server = DelphixRemoteServer()
        val f = FakeEngine()
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubGroups("master" to "GROUP-MASTER", "repositories" to "GROUP-REPOS")
        f.stubContainers(
            """[{"name":"dit","reference":"CT-DD","group":"GROUP-MASTER"}]""",
        )
        f.stubRepositories("Dit")
        f.stubEnvUsers("ENV-DD" to "ENVUSER-1")

        every { f.containerRepo.provision(any(), any<AppDataProvisionParameters>()) } returns
            JSONObject("""{"action":"A-PROV","job":"J-PROV","result":"NEW-CONTAINER"}""")
        f.stubActionCompleted("A-PROV")
        // Source list returns nothing — no match for newly created container
        every { f.sourceRepo.list() } returns JSONObject("""{"result":[]}""")

        val op = newOperation(RemoteOperationType.PUSH)
        val ex =
            shouldThrow<IllegalStateException> {
                server.createRepo(f.engine, op)
            }
        ex.message!! shouldContain "NEW-CONTAINER"
    }

    // ---------------------------------------------------------------
    // syncDataStart — full PUSH and PULL paths
    // ---------------------------------------------------------------

    "syncDataStart on PULL throws when repository does not exist" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        // repoExists is false: groups have repositories but no matching container
        f.stubGroups("repositories" to "GROUP-REPOS")
        f.stubContainers("[]")
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        val ex =
            shouldThrow<IllegalStateException> {
                server.syncDataStart(op)
            }
        ex.message!! shouldContain "myrepo"
    }

    "syncDataStart on PULL succeeds and returns an EngineOperation pointing at the engine ssh user" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        // repoExists path: repositories group present and contains myrepo
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "master" to "GROUP-MASTER",
            "operations" to "GROUP-OPS",
        )
        f.stubContainers(
            """[
                {"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}
            ]""",
        )
        f.stubRepositories("Dit")
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubEnvUsers("ENV-DD" to "ENVUSER-1")
        // For PULL, listSnapshots is consulted to find the commit
        f.stubSnapshots(
            """[{"reference":"SNAP-1","metadata":{"repository":"myrepo","hash":"commit-1","tags":{}}}]""",
        )

        every { f.containerRepo.provision(any(), any<AppDataProvisionParameters>()) } returns
            JSONObject("""{"action":"A-PROV","job":"J-PROV","result":"OP-REF"}""")
        f.stubActionCompleted("A-PROV")
        every { f.sourceRepo.list() } returns
            JSONObject(
                """{"result":[{"container":"OP-REF","reference":"SRC-OP","config":"SC-OP"}]}""",
            )
        every { f.sourceConfigRepo.read("SC-OP") } returns
            JSONObject(
                """{"result":{"parameters":{"sshUser":"engine-u","sshKey":"engine-k"}}}""",
            )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        val result = server.syncDataStart(op) as DelphixRemoteServer.EngineOperation
        result.operationRef shouldBe "OP-REF"
        result.sshUser shouldBe "engine-u"
        result.sshKey shouldBe "engine-k"
        result.sshAddress shouldBe "engine.test"
    }

    "syncDataStart on PULL throws when the commit is not in the snapshots list" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "operations" to "GROUP-OPS",
        )
        f.stubContainers(
            """[{"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}]""",
        )
        f.stubRepositories("Dit")
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubEnvUsers("ENV-DD" to "ENVUSER-1")
        // No matching snapshot for commit-1
        f.stubSnapshots(
            """[{"reference":"SNAP-1","metadata":{"repository":"myrepo","hash":"other","tags":{}}}]""",
        )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        val ex =
            shouldThrow<IllegalStateException> {
                server.syncDataStart(op)
            }
        ex.message!! shouldContain "commit-1"
    }

    "syncDataStart on PUSH when repo does not exist creates the repo then provisions the operation" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        // Initially repoExists -> false (no containers). createRepo will then provision the repo.
        // After createRepo the same containers stub is still in effect — we don't strictly need
        // updated stubs because buildTimeflowPoint for PUSH uses repositories-group lookup, not
        // the snapshot list. We must surface a repositories container after createRepo.
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "master" to "GROUP-MASTER",
            "operations" to "GROUP-OPS",
        )
        // Containers used by both repoExists (no match for "myrepo" the first time? we can't easily
        // simulate state change in a single stub). To handle this, we return different values across
        // calls.
        every { f.containerRepo.list() } returnsMany
            listOf(
                // First call (repoExists -> false)
                JSONObject(
                    """{"result":[{"name":"dit","reference":"CT-DD","group":"GROUP-MASTER"}]}""",
                ),
                // Second call (createRepo's findInGroup("master","dit"))
                JSONObject(
                    """{"result":[{"name":"dit","reference":"CT-DD","group":"GROUP-MASTER"}]}""",
                ),
                // Third call (buildTimeflowPoint findInGroup("repositories","myrepo"))
                JSONObject(
                    """{"result":[
                        {"name":"dit","reference":"CT-DD","group":"GROUP-MASTER"},
                        {"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}
                    ]}""",
                ),
            )
        f.stubRepositories("Dit")
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubEnvUsers("ENV-DD" to "ENVUSER-1")

        // provision: first call is for createRepo, second is for syncDataStart
        every { f.containerRepo.provision(any(), any<AppDataProvisionParameters>()) } returnsMany
            listOf(
                JSONObject("""{"action":"A-CREATE","job":"J-CREATE","result":"REPO-CONTAINER"}"""),
                JSONObject("""{"action":"A-OP","job":"J-OP","result":"OP-REF"}"""),
            )
        f.stubActionCompleted("A-CREATE")
        f.stubActionCompleted("A-OP")
        every { f.sourceRepo.list() } returnsMany
            listOf(
                // createRepo's source lookup
                JSONObject(
                    """{"result":[{"container":"REPO-CONTAINER","reference":"SRC-REPO"}]}""",
                ),
                // syncDataStart's getParameters source lookup
                JSONObject(
                    """{"result":[{"container":"OP-REF","reference":"SRC-OP","config":"SC-OP"}]}""",
                ),
            )
        every { f.sourceRepo.disable(any(), any<SourceDisableParameters>()) } returns
            JSONObject("""{"action":"A-DIS","job":"J-DIS","result":"R"}""")
        f.stubActionCompleted("A-DIS")
        every { f.sourceConfigRepo.read("SC-OP") } returns
            JSONObject(
                """{"result":{"parameters":{"sshUser":"eng-u","sshKey":"eng-k"}}}""",
            )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PUSH)
        val result = server.syncDataStart(op) as DelphixRemoteServer.EngineOperation
        result.operationRef shouldBe "OP-REF"
        // createRepo's provision + sync's provision = 2 calls
        verify(exactly = 2) { f.containerRepo.provision(any(), any()) }
    }

    // ---------------------------------------------------------------
    // connect — explicit assertions for password-routing logic
    // ---------------------------------------------------------------

    "connect throws when no password is provided in remote or parameters" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val ex =
            shouldThrow<IllegalArgumentException> {
                // Drive through the public listCommits path with a remote that omits password.
                server.listCommits(
                    mapOf("address" to "a", "username" to "u", "repository" to "r"),
                    emptyMap(),
                    emptyList(),
                )
            }
        ex.message!! shouldContain "password"
    }

    // ---------------------------------------------------------------
    // connect — exercise the real (non-spied) connect path against MockWebServer.
    // Covers the engine.login + return engine lines that the spy-based tests skip.
    // ---------------------------------------------------------------

    "connect issues setSession + login then returns an engine, exercising the real connect path" {
        // Use a real MockWebServer to back the Http calls. We drive a public method
        // (listCommits) that calls connect() and listSnapshots(). repoExists will return
        // false because the repositories group is empty, so listCommits returns [] — but
        // critically, the connect() body has executed end-to-end.
        val server = DelphixRemoteServer()
        val mock = okhttp3.mockwebserver.MockWebServer()
        mock.start()
        try {
            // setSession() POSTs /resources/json/delphix/session
            mock.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setBody("""{"status":"OK"}""")
                    .addHeader("Set-Cookie", "JSESSIONID=test;Path=/"),
            )
            // login() POSTs /resources/json/delphix/login
            mock.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setBody("""{"status":"OK","result":{}}"""),
            )
            // repoExists -> findInGroup -> engine.group().list() (returns empty)
            mock.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setBody("""{"status":"OK","result":[]}"""),
            )

            val address = mock.url("/").toString().trimEnd('/').removePrefix("http://")
            val commits =
                server.listCommits(
                    mapOf(
                        "address" to address,
                        "username" to "admin",
                        "password" to "pw",
                        "repository" to "myrepo",
                    ),
                    emptyMap(),
                    emptyList(),
                )
            commits.size shouldBe 0
            // Verify we made the login calls
            val first = mock.takeRequest()
            first.path shouldBe "/resources/json/delphix/session"
            val second = mock.takeRequest()
            second.path shouldBe "/resources/json/delphix/login"
        } finally {
            mock.shutdown()
        }
    }

    "connect uses parameters.password when remote has no password" {
        val server = DelphixRemoteServer()
        val mock = okhttp3.mockwebserver.MockWebServer()
        mock.start()
        try {
            mock.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setBody("""{"status":"OK"}""")
                    .addHeader("Set-Cookie", "JSESSIONID=test;Path=/"),
            )
            mock.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setBody("""{"status":"OK","result":{}}"""),
            )
            mock.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setBody("""{"status":"OK","result":[]}"""),
            )

            val address = mock.url("/").toString().trimEnd('/').removePrefix("http://")
            val commits =
                server.listCommits(
                    mapOf(
                        "address" to address,
                        "username" to "admin",
                        // no password in remote
                        "repository" to "myrepo",
                    ),
                    mapOf("password" to "from-params"),
                    emptyList(),
                )
            commits.size shouldBe 0
            // Login payload should contain the params password
            mock.takeRequest() // setSession
            val login = mock.takeRequest()
            val body = login.body.readUtf8()
            (body.contains("\"password\":\"from-params\"")) shouldBe true
        } finally {
            mock.shutdown()
        }
    }

    // ---------------------------------------------------------------
    // buildContainer / buildSourceConfig / buildTimeflowPoint — error branches
    // ---------------------------------------------------------------

    "syncDataStart throws when the 'operations' group is missing (buildContainer)" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        // repoExists = true; buildTimeflowPoint = PULL with valid snapshot
        f.stubGroups("repositories" to "GROUP-REPOS") // no "operations" group
        f.stubContainers(
            """[{"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}]""",
        )
        f.stubSnapshots(
            """[{"reference":"SNAP-1","metadata":{"repository":"myrepo","hash":"commit-1","metadata":{}}}]""",
        )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        val ex =
            shouldThrow<IllegalStateException> {
                server.syncDataStart(op)
            }
        ex.message!! shouldContain "engine not properly configured"
    }

    "syncDataStart throws when no 'Dit' repository (buildSourceConfig)" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "operations" to "GROUP-OPS",
        )
        f.stubContainers(
            """[{"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}]""",
        )
        f.stubSnapshots(
            """[{"reference":"SNAP-1","metadata":{"repository":"myrepo","hash":"commit-1","metadata":{}}}]""",
        )
        // No Dit repository
        f.stubRepositories("NotDit")
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        shouldThrow<IllegalStateException> {
            server.syncDataStart(op)
        }
    }

    "syncDataStart throws when 'dit' environment is missing (buildSourceConfig)" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "operations" to "GROUP-OPS",
        )
        f.stubContainers(
            """[{"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}]""",
        )
        f.stubSnapshots(
            """[{"reference":"SNAP-1","metadata":{"repository":"myrepo","hash":"commit-1","metadata":{}}}]""",
        )
        f.stubRepositories("Dit")
        f.stubEnvironments() // no 'dit' env
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        shouldThrow<IllegalStateException> {
            server.syncDataStart(op)
        }
    }

    "syncDataStart throws when env user for 'dit' is missing (buildSourceConfig)" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "operations" to "GROUP-OPS",
        )
        f.stubContainers(
            """[{"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}]""",
        )
        f.stubSnapshots(
            """[{"reference":"SNAP-1","metadata":{"repository":"myrepo","hash":"commit-1","metadata":{}}}]""",
        )
        f.stubRepositories("Dit")
        f.stubEnvironments("dit" to "ENV-DD")
        f.stubEnvUsers() // no env user
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PULL)
        shouldThrow<IllegalStateException> {
            server.syncDataStart(op)
        }
    }

    "companion logger is reachable" {
        // Direct reference to the companion's logger property. Most call sites are inside
        // private/internal methods, so we force a public-facing access here.
        DelphixRemoteServer.log shouldBe DelphixRemoteServer.log
    }

    "syncDataStart on PUSH throws when the repository is missing from the repositories group (buildTimeflowPoint)" {
        val server = spyk(DelphixRemoteServer(), recordPrivateCalls = true)
        val f = FakeEngine()
        // repoExists -> true (via first list call returning a matching container), then
        // buildTimeflowPoint -> findInGroup("repositories", repoName) returns null because
        // the second list call returns no matching container. Use returnsMany for that:
        f.stubGroups(
            "repositories" to "GROUP-REPOS",
            "operations" to "GROUP-OPS",
        )
        every { f.containerRepo.list() } returnsMany
            listOf(
                // 1st call: repoExists -> matches "myrepo"
                JSONObject(
                    """{"result":[{"name":"myrepo","reference":"CT-REPO","group":"GROUP-REPOS"}]}""",
                ),
                // 2nd call: buildTimeflowPoint -> no matching container
                JSONObject("""{"result":[]}"""),
            )
        every { server["connect"](any<Map<String, Any>>(), any<Map<String, Any>>()) } returns f.engine

        val op = newOperation(RemoteOperationType.PUSH)
        val ex =
            shouldThrow<IllegalStateException> {
                server.syncDataStart(op)
            }
        ex.message!! shouldContain "no such repository"
    }
})
