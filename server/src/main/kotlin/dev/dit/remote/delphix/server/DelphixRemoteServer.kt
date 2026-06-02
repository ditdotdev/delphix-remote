/*
 * Copyright Dit.
 */

package dev.dit.remote.delphix.server

import com.delphix.sdk.Delphix
import com.delphix.sdk.Http
import com.delphix.sdk.objects.AppDataContainer
import com.delphix.sdk.objects.AppDataDirectSourceConfig
import com.delphix.sdk.objects.AppDataProvisionParameters
import com.delphix.sdk.objects.AppDataSyncParameters
import com.delphix.sdk.objects.AppDataVirtualSource
import com.delphix.sdk.objects.DeleteParameters
import com.delphix.sdk.objects.Repository
import com.delphix.sdk.objects.SourceDisableParameters
import com.delphix.sdk.objects.TimeflowPointParameters
import com.delphix.sdk.objects.TimeflowPointSemantic
import com.delphix.sdk.objects.TimeflowPointSnapshot
import dev.dit.remote.RemoteOperation
import dev.dit.remote.RemoteOperationType
import dev.dit.remote.RemoteProgress
import dev.dit.remote.RemoteServerUtil
import dev.dit.remote.rsync.RsyncExecutor
import dev.dit.remote.rsync.RsyncRemote
import dev.dit.shell.CommandExecutor
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * The Delphix remote provider is a proof of concept implementation, dependent on a proprietary plugin that is not
 * currently generally available. As such, this is only loosely documented and tested.
 */
class DelphixRemoteServer : RsyncRemote() {
    internal val util = RemoteServerUtil()

    companion object {
        val log = LoggerFactory.getLogger(DelphixRemoteServer::class.java)

        /**
         * Coerce a JSON-deserialized value into a [Boolean]. `Map<String, Any>`
         * payloads can carry either a real [Boolean] (most parsers) or the
         * literal strings `"true"`/`"false"` (some serializers). Anything else
         * is rejected so a typo like `"yes"` does not silently disable a
         * security control. Mirrors `SshRemoteServer.coerceBoolean` from
         * ssh-remote PR #62 to keep the validation surface consistent across
         * the two providers.
         */
        internal fun coerceBoolean(
            prop: String,
            value: Any,
        ): Boolean =
            when (value) {
                is Boolean -> {
                    value
                }

                is String -> {
                    when (value.lowercase()) {
                        "true" -> {
                            true
                        }

                        "false" -> {
                            false
                        }

                        else -> {
                            throw IllegalArgumentException(
                                "'$prop' must be a boolean (true/false), got '$value'",
                            )
                        }
                    }
                }

                else -> {
                    throw IllegalArgumentException("'$prop' must be a boolean, got ${value::class.simpleName}")
                }
            }
    }

    override fun getProvider(): String {
        return "engine"
    }

    /**
     * Validate remote configuration. Required: (username, address, repository).
     * Optional: (password, knownHostsFile, skipHostCheck).
     *
     * `skipHostCheck` and `knownHostsFile` mirror the ssh-remote properties
     * introduced in ssh-remote PR #62 and govern host-key verification on the
     * rsync data path (see remote-sdk PR #47). Defaults are secure:
     * `StrictHostKeyChecking=yes` against `~/.ssh/known_hosts`.
     */
    override fun validateRemote(remote: Map<String, Any>): Map<String, Any> {
        util.validateFields(
            remote,
            listOf("username", "address", "repository"),
            listOf("password", "knownHostsFile", "skipHostCheck"),
        )
        val validated = remote.toMutableMap()
        val rawSkipHostCheck = remote["skipHostCheck"]
        if (rawSkipHostCheck != null) {
            validated["skipHostCheck"] = coerceBoolean("skipHostCheck", rawSkipHostCheck)
        }
        return validated
    }

    override fun validateParameters(parameters: Map<String, Any>?): Map<String, Any> {
        val params = parameters ?: emptyMap()
        util.validateFields(params, emptyList(), listOf("password"))
        return params
    }

    private fun connect(
        remote: Map<String, Any>,
        params: Map<String, Any>,
    ): Delphix {
        val engine = Delphix(Http("http://${remote["address"]}"))
        if (remote["password"] == null && params["password"] == null) {
            throw IllegalArgumentException("missing password in remote parameters")
        }
        engine.login(remote["username"] as String, (params["password"] ?: remote["password"]).toString())
        return engine
    }

    private fun findInResult(
        result: JSONObject,
        lambda: (JSONObject) -> Boolean,
    ): JSONObject? {
        val objects = result.getJSONArray("result")
        for (i in 0 until objects.length()) {
            val obj = objects.getJSONObject(i)
            if (lambda(obj)) {
                return obj
            }
        }
        return null
    }

    private fun findByName(
        result: JSONObject,
        name: String,
    ): JSONObject? {
        return findInResult(result) { it.getString("name") == name }
    }

    private fun findInGroup(
        engine: Delphix,
        groupName: String,
        name: String,
    ): JSONObject? {
        val group = findByName(engine.group().list(), groupName) ?: return null
        val groupRef = group.getString("reference")

        return findInResult(engine.container().list()) {
            it.getString("name") == name && it.getString("group") == groupRef
        }
    }

    private fun getRepository(engine: Delphix): Repository? {
        for (r in engine.repository().list()) {
            if (r.name == "Dit") {
                return r
            }
        }
        return null
    }

    private fun getEnvUser(
        engine: Delphix,
        env: JSONObject,
    ): JSONObject? {
        return findInResult(engine.environmentUser().list()) {
            it.getString("environment") == env.getString("reference")
        }
    }

    fun waitForJob(
        engine: Delphix,
        result: JSONObject,
    ) {
        val actionResult: JSONObject = engine.action().read(result.getString("action")).getJSONObject("result")
        if (actionResult.optString("state") == "COMPLETED") {
            log.debug("action ${actionResult.getString("reference")} complete")
            println(actionResult.getString("title"))
        } else {
            log.debug("waiting for job ${result.getString("job")}")
            var job: JSONObject = engine.job().read(result.getString("job")).getJSONObject("result")
            while (job.optString("jobState") == "RUNNING") {
                Thread.sleep(5000)
                job = engine.job().read(result.getString("job")).getJSONObject("result")
            }

            if (job.optString("jobState") != "COMPLETED") {
                throw Exception("engine job ${job.getString("reference")} failed")
            }
            log.debug("engine job ${job.getString("reference")} complete")
        }
    }

    fun repoExists(
        engine: Delphix,
        name: String,
    ): Boolean {
        return findInGroup(engine, "repositories", name) != null
    }

    private fun listSnapshots(
        engine: Delphix,
        remote: Map<String, Any>,
    ): List<JSONObject> {
        val name = remote["repository"] as String

        if (!repoExists(engine, name)) {
            return emptyList()
        }

        val ret = ArrayList<JSONObject>()
        val snapshots = engine.snapshot().list().getJSONArray("result")
        for (i in 0 until snapshots.length()) {
            val snapshot = snapshots.getJSONObject(i)
            val properties = snapshot.optJSONObject("metadata")
            if (properties != null && properties.has("repository") && properties.has("hash")) {
                if (properties.getString("repository") == name && properties.getString("hash") != "") {
                    properties.put("reference", snapshot.getString("reference"))
                    ret.add(properties)
                }
            }
        }

        return ret
    }

    override fun getCommit(
        remote: Map<String, Any>,
        parameters: Map<String, Any>,
        commitId: String,
    ): Map<String, Any>? {
        // This is horribly inefficient, but all we can do until we have a better API
        val commits = listCommits(remote, parameters, emptyList())
        return commits.find { it.first == commitId }?.second
    }

    override fun listCommits(
        remote: Map<String, Any>,
        parameters: Map<String, Any>,
        tags: List<Pair<String, String?>>,
    ): List<Pair<String, Map<String, Any>>> {
        val engine = connect(remote, parameters)
        val commits =
            listSnapshots(engine, remote).map {
                it.getString("hash") to it.getJSONObject("metadata").toMap()
            }
        val filtered = commits.filter { util.matchTags(it.second, tags) }
        return util.sortDescending(filtered)
    }

    /**
     * Adding a remote shouldn't really be performing operations on the remote. We don't have
     * the ability to report progress or do any long-running operations. But for now this lets
     * us make forward progress. Once we have a few providers we can decide how we want to deal
     * with this (or declare that al remotes must be created out of band of the CLI).
     */
    fun createRepo(
        engine: Delphix,
        operation: RemoteOperation,
    ) {
        val name = operation.remote["repository"] as String

        operation.updateProgress(RemoteProgress.START, "Creating remote repository", null)

        val ditEnvironment =
            findByName(engine.sourceEnvironment().list(), "dit")
                ?: throw IllegalStateException("engine not properly configured for dit")
        val ditSource =
            findInGroup(engine, "master", "dit")
                ?: throw IllegalStateException("engine not properly configured for dit")
        val repositoryGroup =
            findByName(engine.group().list(), "repositories")
                ?: throw IllegalStateException("engine not properly configured for dit")
        val sourceRepository =
            getRepository(engine)
                ?: throw IllegalStateException("engine not properly configured for dit")
        val ditUser =
            getEnvUser(engine, ditEnvironment)
                ?: throw IllegalStateException("engine not properly configured for dit")

        val container = AppDataContainer(name = name, group = repositoryGroup.getString("reference"))
        val timeflowPoint =
            TimeflowPointSemantic(
                container = ditSource.getString("reference"),
                location = "LATEST_SNAPSHOT",
            )
        val source =
            AppDataVirtualSource(
                name = name,
                additionalMountPoints = ArrayList(),
                parameters = mapOf("operationId" to name),
            )
        val sourceConfig =
            AppDataDirectSourceConfig(
                repository = sourceRepository.reference,
                environmentUser = ditUser.getString("reference"),
                name = name,
                linkingEnabled = false,
                path = "",
            )
        val provisionParams =
            AppDataProvisionParameters(
                container = container,
                timeflowPointParameters = timeflowPoint,
                source = source,
                sourceConfig = sourceConfig,
            )

        log.info("provisioning VDB on ${engine.http.engineAddress}")
        log.info(provisionParams.toMap().toString())
        val response = engine.container().provision("provision", provisionParams)
        val containerRef = response.getString("result")
        waitForJob(engine, response)

        val createdSource =
            findInResult(engine.source().list()) {
                it.getString("container") == containerRef
            }

        log.info("disabling VDB on ${engine.http.engineAddress}")
        val sourceToDisable =
            createdSource
                ?: throw IllegalStateException(
                    "engine did not return a source for newly provisioned container '$containerRef'",
                )
        val disableResponse =
            engine.source().disable(
                sourceToDisable.getString("reference"),
                SourceDisableParameters(),
            )
        waitForJob(engine, disableResponse)

        operation.updateProgress(RemoteProgress.END, null, null)
    }

    private fun buildContainer(
        engine: Delphix,
        operation: RemoteOperation,
    ): AppDataContainer {
        val operationsGroup =
            findByName(engine.group().list(), "operations")
                ?: throw IllegalStateException("engine not properly configured for dit")
        return AppDataContainer(
            name = operation.operationId,
            group = operationsGroup.getString("reference"),
        )
    }

    private fun buildSourceConfig(
        engine: Delphix,
        operation: RemoteOperation,
    ): AppDataDirectSourceConfig {
        val sourceRepository =
            getRepository(engine)
                ?: throw IllegalStateException("engine not properly configured for dit")
        val ditEnvironment =
            findByName(engine.sourceEnvironment().list(), "dit")
                ?: throw IllegalStateException("engine not properly configured for dit")
        val ditUser =
            getEnvUser(engine, ditEnvironment)
                ?: throw IllegalStateException("engine not properly configured for dit")
        return AppDataDirectSourceConfig(
            repository = sourceRepository.reference,
            environmentUser = ditUser.getString("reference"),
            name = operation.operationId,
            linkingEnabled = false,
            path = "",
        )
    }

    private fun buildTimeflowPoint(
        engine: Delphix,
        operation: RemoteOperation,
    ): TimeflowPointParameters {
        val repoName = operation.remote["repository"] as String

        if (operation.type == RemoteOperationType.PUSH) {
            val repo =
                findInGroup(engine, "repositories", repoName)
                    ?: throw IllegalStateException("no such repository '$repoName'")
            return TimeflowPointSemantic(
                container = repo.getString("reference"),
                location = "LATEST_SNAPSHOT",
            )
        } else {
            val snapshots = listSnapshots(engine, operation.remote)
            val snapshot =
                snapshots.find { it -> it.getString("hash") == operation.commitId }
                    ?: throw IllegalStateException("no such commit '${operation.commitId}'")
            return TimeflowPointSnapshot(snapshot.getString("reference"))
        }
    }

    internal fun buildSource(operation: RemoteOperation): AppDataVirtualSource {
        val parameters: MutableMap<String, Any> = HashMap()
        parameters["operationId"] = operation.operationId
        parameters["operationType"] = operation.type
        parameters["repository"] = operation.remote["repository"] as String
        if (operation.type == RemoteOperationType.PUSH) {
            parameters["hash"] = operation.commitId
            val commit =
                operation.commit
                    ?: throw IllegalArgumentException(
                        "PUSH operation '${operation.operationId}' is missing commit metadata",
                    )
            parameters["metadata"] = commit.toMutableMap()
        }

        return AppDataVirtualSource(
            name = operation.operationId,
            additionalMountPoints = ArrayList(),
            parameters = parameters,
        )
    }

    fun getParameters(
        engine: Delphix,
        containerRef: String,
    ): JSONObject {
        val source =
            findInResult(engine.source().list()) {
                it.getString("container") == containerRef
            }
                ?: throw IllegalStateException(
                    "no source found for container '$containerRef' on engine ${engine.http.engineAddress}",
                )
        val config = engine.sourceConfig().read(source.getString("config")).getJSONObject("result")
        val obj = config.getJSONObject("parameters")
        return obj
    }

    class EngineOperation(
        val engine: Delphix,
        val operationRef: String,
        val sshAddress: String,
        val sshUser: String,
        val sshKey: String,
    )

    override fun syncDataStart(operation: RemoteOperation): Any? {
        val engine = connect(operation.remote, operation.parameters)

        val name = operation.remote["repository"] as String
        if (!repoExists(engine, name)) {
            if (operation.type == RemoteOperationType.PULL) {
                throw IllegalStateException("no such repository '$name'")
            } else {
                createRepo(engine, operation)
            }
        }

        operation.updateProgress(RemoteProgress.START, "Creating remote endpoint", null)
        val timeflowPoint = buildTimeflowPoint(engine, operation)

        var params =
            AppDataProvisionParameters(
                container = buildContainer(engine, operation),
                timeflowPointParameters = timeflowPoint,
                source = buildSource(operation),
                sourceConfig = buildSourceConfig(engine, operation),
            )

        var response = engine.container().provision("provision", params)
        val operationRef = response.getString("result")

        waitForJob(engine, response)
        operation.updateProgress(RemoteProgress.END, null, null)

        val resultParams = getParameters(engine, operationRef)

        return EngineOperation(
            engine,
            operationRef,
            operation.remote["address"] as String,
            resultParams.getString("sshUser"),
            resultParams.getString("sshKey"),
        )
    }

    override fun syncDataEnd(
        operation: RemoteOperation,
        operationData: Any?,
        isSuccessful: Boolean,
    ) {
        val data = operationData as EngineOperation
        operation.updateProgress(RemoteProgress.START, "Removing remote endpoint", null)
        if (operation.type == RemoteOperationType.PUSH && isSuccessful) {
            var response = data.engine.container().sync(data.operationRef, AppDataSyncParameters())
            waitForJob(data.engine, response)

            val source =
                findInResult(data.engine.source().list()) {
                    it.getString("container") == data.operationRef
                }
                    ?: throw IllegalStateException(
                        "no source found for container '${data.operationRef}' on engine " +
                            "${data.engine.http.engineAddress} while ending sync",
                    )
            response =
                data.engine.source().disable(
                    source.getString("reference"),
                    SourceDisableParameters(),
                )
            waitForJob(data.engine, response)
        } else {
            val response = data.engine.container().delete(data.operationRef, DeleteParameters())
            waitForJob(data.engine, response)
        }
        operation.updateProgress(RemoteProgress.END, null, null)
    }

    override fun getRemotePath(
        operation: RemoteOperation,
        operationData: Any?,
        volume: String,
    ): String {
        val data = operationData as EngineOperation
        return "${data.sshUser}@${data.sshAddress}:data/$volume"
    }

    override fun getRsync(
        operation: RemoteOperation,
        operationData: Any?,
        src: String,
        dst: String,
        executor: CommandExecutor,
    ): RsyncExecutor {
        val data = operationData as EngineOperation
        // Thread skipHostCheck / knownHostsFile from the validated remote map
        // through to the SDK so the rsync data-path SSH invocation honors the
        // operator's host-key policy (issue #51 / remote-sdk #46 / ssh-remote
        // #62). validateRemote has already coerced skipHostCheck to a Boolean.
        val rawSkipHostCheck = operation.remote["skipHostCheck"]
        val skipHostCheck = rawSkipHostCheck != null && coerceBoolean("skipHostCheck", rawSkipHostCheck)
        val knownHostsFile = operation.remote["knownHostsFile"]?.toString()
        return RsyncExecutor(
            operation.updateProgress,
            8022,
            null,
            data.sshKey,
            "$src/",
            "$dst/",
            skipHostCheck = skipHostCheck,
            knownHostsFile = knownHostsFile,
        )
    }

    override fun pushMetadata(
        operation: RemoteOperation,
        commit: Map<String, Any>,
        isUpdate: Boolean,
    ) {
        // Our metadata is created at the time the snapshot is created, and can't be updated
        if (isUpdate) {
            throw IllegalStateException("commit metadata cannot be updated for engine remotes")
        }
    }
}
