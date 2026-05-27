/*
 * Copyright Datadatdat.
 */

package com.delphix.sdk.objects

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

/**
 * Coverage for the generated Delphix SDK DTOs.
 *
 * Each [open class] DTO under com.delphix.sdk.objects is constructed with its default
 * values (or empty lists for required list parameters) and round-tripped through
 * [toMap]. We assert that the resulting map includes the expected "type" discriminator
 * and the same set of keys as the constructor properties — this both exercises the
 * primary constructor (so all default values are evaluated) and the [toMap] method
 * line by line.
 *
 * If a future change to the Delphix wire shape adds, renames, or drops a property,
 * these assertions will surface the diff immediately.
 */
class DtoToMapTest : StringSpec({

    // Small helper: build the dto, verify type key, verify toMap is non-empty and
    // contains the type discriminator value.
    fun verifyTypeAndMap(
        dto: Any,
        expectedType: String,
        toMap: () -> Map<String, Any?>,
    ) {
        val map = toMap()
        map shouldNotBe null
        map["type"] shouldBe expectedType
        dto shouldNotBe null
    }

    "APIError defaults round-trip" {
        val o = APIError(diagnoses = emptyList())
        verifyTypeAndMap(o, "APIError") { o.toMap() }
        o.diagnoses shouldBe emptyList<DiagnosisResult>()
        o.toMap()["diagnoses"] shouldBe emptyList<DiagnosisResult>()
    }

    "APISession defaults round-trip" {
        val o = APISession()
        verifyTypeAndMap(o, "APISession") { o.toMap() }
        o.toMap().keys shouldBe setOf("client", "locale", "version", "type")
    }

    "APIVersion defaults round-trip" {
        val o = APIVersion(major = 1, minor = 2, micro = 3)
        verifyTypeAndMap(o, "APIVersion") { o.toMap() }
        o.toMap()["major"] shouldBe 1
        o.toMap()["minor"] shouldBe 2
        o.toMap()["micro"] shouldBe 3
    }

    "Action defaults round-trip" {
        val o = Action()
        verifyTypeAndMap(o, "Action") { o.toMap() }
        o.toMap().keys.contains("failureAction") shouldBe true
        o.toMap().keys.contains("reference") shouldBe true
        o.toMap().keys.contains("namespace") shouldBe true
    }

    "AppDataAdditionalMountPoint defaults round-trip" {
        val o = AppDataAdditionalMountPoint()
        verifyTypeAndMap(o, "AppDataAdditionalMountPoint") { o.toMap() }
    }

    "AppDataContainer defaults round-trip" {
        val o = AppDataContainer(name = "n", group = "g")
        verifyTypeAndMap(o, "AppDataContainer") { o.toMap() }
        o.toMap()["name"] shouldBe "n"
        o.toMap()["group"] shouldBe "g"
    }

    "AppDataContainerRuntime defaults round-trip" {
        val o = AppDataContainerRuntime()
        verifyTypeAndMap(o, "AppDataContainerRuntime") { o.toMap() }
    }

    "AppDataDirectSourceConfig defaults round-trip" {
        val o = AppDataDirectSourceConfig()
        verifyTypeAndMap(o, "AppDataDirectSourceConfig") { o.toMap() }
    }

    "AppDataFilesystemLayout defaults round-trip" {
        val o = AppDataFilesystemLayout()
        verifyTypeAndMap(o, "AppDataFilesystemLayout") { o.toMap() }
    }

    "AppDataProvisionParameters defaults round-trip" {
        val o = AppDataProvisionParameters()
        verifyTypeAndMap(o, "AppDataProvisionParameters") { o.toMap() }
    }

    "AppDataRepository defaults round-trip" {
        val o = AppDataRepository()
        verifyTypeAndMap(o, "AppDataRepository") { o.toMap() }
    }

    "AppDataSnapshot defaults round-trip" {
        val o = AppDataSnapshot()
        verifyTypeAndMap(o, "AppDataSnapshot") { o.toMap() }
    }

    "AppDataSnapshotRuntime defaults round-trip" {
        val o = AppDataSnapshotRuntime()
        verifyTypeAndMap(o, "AppDataSnapshotRuntime") { o.toMap() }
    }

    "AppDataSourceConnectionInfo defaults round-trip" {
        val o = AppDataSourceConnectionInfo()
        verifyTypeAndMap(o, "AppDataSourceConnectionInfo") { o.toMap() }
    }

    "AppDataSourceRuntime defaults round-trip" {
        val o = AppDataSourceRuntime()
        verifyTypeAndMap(o, "AppDataSourceRuntime") { o.toMap() }
    }

    "AppDataSyncParameters defaults round-trip" {
        val o = AppDataSyncParameters()
        verifyTypeAndMap(o, "AppDataSyncParameters") { o.toMap() }
    }

    "AppDataTimeflow defaults round-trip" {
        val o = AppDataTimeflow()
        verifyTypeAndMap(o, "AppDataTimeflow") { o.toMap() }
    }

    "AppDataTimeflowPoint defaults round-trip" {
        val o = AppDataTimeflowPoint()
        verifyTypeAndMap(o, "AppDataTimeflowPoint") { o.toMap() }
    }

    "AppDataVirtualSource defaults round-trip" {
        val o = AppDataVirtualSource()
        verifyTypeAndMap(o, "AppDataVirtualSource") { o.toMap() }
    }

    "CPUInfo defaults round-trip" {
        val o = CPUInfo(speed = 3500)
        verifyTypeAndMap(o, "CPUInfo") { o.toMap() }
        o.toMap()["speed"] shouldBe 3500
    }

    "DeleteParameters defaults round-trip" {
        val o = DeleteParameters()
        verifyTypeAndMap(o, "DeleteParameters") { o.toMap() }
    }

    "DiagnosisResult defaults round-trip" {
        val o = DiagnosisResult()
        verifyTypeAndMap(o, "DiagnosisResult") { o.toMap() }
    }

    "EnvironmentUser defaults round-trip" {
        val o = EnvironmentUser()
        verifyTypeAndMap(o, "EnvironmentUser") { o.toMap() }
    }

    "ErrorResult defaults round-trip" {
        val o = ErrorResult()
        verifyTypeAndMap(o, "ErrorResult") { o.toMap() }
    }

    "Fault defaults round-trip" {
        val o = Fault()
        verifyTypeAndMap(o, "Fault") { o.toMap() }
    }

    "FaultEffect defaults round-trip" {
        val o = FaultEffect()
        verifyTypeAndMap(o, "FaultEffect") { o.toMap() }
    }

    "Group defaults round-trip" {
        val o = Group(name = "g")
        verifyTypeAndMap(o, "Group") { o.toMap() }
        o.toMap()["name"] shouldBe "g"
    }

    "HostConfiguration defaults round-trip" {
        val o = HostConfiguration()
        verifyTypeAndMap(o, "HostConfiguration") { o.toMap() }
    }

    "HostMachine defaults round-trip" {
        val o = HostMachine()
        verifyTypeAndMap(o, "HostMachine") { o.toMap() }
    }

    "HostOS defaults round-trip" {
        val o = HostOS()
        verifyTypeAndMap(o, "HostOS") { o.toMap() }
    }

    "HostRuntime defaults round-trip" {
        val o = HostRuntime()
        verifyTypeAndMap(o, "HostRuntime") { o.toMap() }
    }

    "Job defaults round-trip" {
        val o = Job(emailAddresses = emptyList(), events = emptyList())
        verifyTypeAndMap(o, "Job") { o.toMap() }
        o.toMap()["emailAddresses"] shouldBe emptyList<String>()
        o.toMap()["events"] shouldBe emptyList<JobEvent>()
    }

    "JobEvent defaults round-trip" {
        val o = JobEvent(diagnoses = emptyList())
        verifyTypeAndMap(o, "JobEvent") { o.toMap() }
        o.toMap()["diagnoses"] shouldBe emptyList<DiagnosisResult>()
    }

    "LoginRequest defaults round-trip" {
        val o = LoginRequest(username = "u", password = "p", target = "t")
        verifyTypeAndMap(o, "LoginRequest") { o.toMap() }
        o.toMap()["username"] shouldBe "u"
        o.toMap()["password"] shouldBe "p"
        o.toMap()["target"] shouldBe "t"
    }

    "OKResult defaults round-trip" {
        val o = OKResult(result = "ok", action = "a", job = "j", status = "OK")
        verifyTypeAndMap(o, "OKResult") { o.toMap() }
        o.toMap()["result"] shouldBe "ok"
        o.toMap()["status"] shouldBe "OK"
        o.toMap()["action"] shouldBe "a"
        o.toMap()["job"] shouldBe "j"
    }

    "OperationTemplate defaults round-trip" {
        val o = OperationTemplate()
        verifyTypeAndMap(o, "OperationTemplate") { o.toMap() }
    }

    "PasswordCredential defaults round-trip" {
        val o = PasswordCredential(password = "x")
        verifyTypeAndMap(o, "PasswordCredential") { o.toMap() }
        o.toMap()["password"] shouldBe "x"
    }

    "PreProvisioningRuntime defaults round-trip" {
        val o = PreProvisioningRuntime()
        verifyTypeAndMap(o, "PreProvisioningRuntime") { o.toMap() }
    }

    "SnapshotRuntime defaults round-trip" {
        val o = SnapshotRuntime()
        verifyTypeAndMap(o, "SnapshotRuntime") { o.toMap() }
    }

    "SourceDisableParameters defaults round-trip" {
        val o = SourceDisableParameters()
        verifyTypeAndMap(o, "SourceDisableParameters") { o.toMap() }
    }

    "SourceRepositoryTemplate defaults round-trip" {
        val o = SourceRepositoryTemplate()
        verifyTypeAndMap(o, "SourceRepositoryTemplate") { o.toMap() }
    }

    "SourcingPolicy defaults round-trip" {
        val o = SourcingPolicy()
        verifyTypeAndMap(o, "SourcingPolicy") { o.toMap() }
    }

    "SyncParameters defaults round-trip" {
        val o = SyncParameters()
        verifyTypeAndMap(o, "SyncParameters") { o.toMap() }
    }

    "SystemKeyCredential defaults round-trip" {
        val o = SystemKeyCredential()
        verifyTypeAndMap(o, "SystemKeyCredential") { o.toMap() }
    }

    "TimeRangeParameters defaults round-trip" {
        val o = TimeRangeParameters()
        verifyTypeAndMap(o, "TimeRangeParameters") { o.toMap() }
    }

    "TimeflowBookmark defaults round-trip" {
        val o = TimeflowBookmark()
        verifyTypeAndMap(o, "TimeflowBookmark") { o.toMap() }
    }

    "TimeflowBookmarkCreateParameters defaults round-trip" {
        val o = TimeflowBookmarkCreateParameters()
        verifyTypeAndMap(o, "TimeflowBookmarkCreateParameters") { o.toMap() }
    }

    "TimeflowPointLocation defaults round-trip" {
        val o = TimeflowPointLocation()
        verifyTypeAndMap(o, "TimeflowPointLocation") { o.toMap() }
    }

    "TimeflowPointSemantic defaults round-trip" {
        val o = TimeflowPointSemantic()
        verifyTypeAndMap(o, "TimeflowPointSemantic") { o.toMap() }
    }

    "TimeflowPointSnapshot defaults round-trip" {
        val o = TimeflowPointSnapshot()
        verifyTypeAndMap(o, "TimeflowPointSnapshot") { o.toMap() }
    }

    "TimeflowPointTimestamp defaults round-trip" {
        val o = TimeflowPointTimestamp()
        verifyTypeAndMap(o, "TimeflowPointTimestamp") { o.toMap() }
    }

    "ToolkitVirtualSource defaults round-trip" {
        val o = ToolkitVirtualSource()
        verifyTypeAndMap(o, "ToolkitVirtualSource") { o.toMap() }
    }

    "TracerouteInfo defaults round-trip" {
        val o = TracerouteInfo()
        verifyTypeAndMap(o, "TracerouteInfo") { o.toMap() }
    }

    "User defaults round-trip" {
        val o = User(principal = "p", name = "n")
        verifyTypeAndMap(o, "User") { o.toMap() }
        o.toMap()["principal"] shouldBe "p"
        o.toMap()["name"] shouldBe "n"
    }

    "VirtualSourceOperations defaults round-trip" {
        val o =
            VirtualSourceOperations(
                preSnapshot = emptyList(),
                postSnapshot = emptyList(),
                preRefresh = emptyList(),
                postRollback = emptyList(),
                preRollback = emptyList(),
                configureClone = emptyList(),
                postRefresh = emptyList(),
            )
        verifyTypeAndMap(o, "VirtualSourceOperations") { o.toMap() }
        o.toMap()["preSnapshot"] shouldBe emptyList<SourceOperation>()
        o.toMap()["postSnapshot"] shouldBe emptyList<SourceOperation>()
        o.toMap()["preRefresh"] shouldBe emptyList<SourceOperation>()
        o.toMap()["postRollback"] shouldBe emptyList<SourceOperation>()
        o.toMap()["preRollback"] shouldBe emptyList<SourceOperation>()
        o.toMap()["configureClone"] shouldBe emptyList<SourceOperation>()
        o.toMap()["postRefresh"] shouldBe emptyList<SourceOperation>()
    }

    // Additional: verify that supplying real values surfaces them through toMap()
    // on the bigger DTOs. This both proves the constructor + toMap path on every
    // non-trivial property, and serves as a wire-shape regression check.
    "Action with all values populates the map" {
        val o =
            Action(
                failureAction = "fa",
                workSource = "ws",
                userAgent = "ua",
                title = "t",
                failureMessageCode = "fmc",
                actionType = "at",
                report = "r",
                details = "d",
                startTime = "st",
                endTime = "et",
                state = "s",
                parentAction = "pa",
                user = "u",
                workSourceName = "wsn",
                failureDescription = "fd",
                reference = "ref",
                namespace = "ns",
            )
        val m = o.toMap()
        m["failureAction"] shouldBe "fa"
        m["workSource"] shouldBe "ws"
        m["userAgent"] shouldBe "ua"
        m["title"] shouldBe "t"
        m["failureMessageCode"] shouldBe "fmc"
        m["actionType"] shouldBe "at"
        m["report"] shouldBe "r"
        m["details"] shouldBe "d"
        m["startTime"] shouldBe "st"
        m["endTime"] shouldBe "et"
        m["state"] shouldBe "s"
        m["parentAction"] shouldBe "pa"
        m["user"] shouldBe "u"
        m["workSourceName"] shouldBe "wsn"
        m["failureDescription"] shouldBe "fd"
        m["reference"] shouldBe "ref"
        m["namespace"] shouldBe "ns"
        m["type"] shouldBe "Action"
    }

    "User with all values populates the map" {
        val cred = PasswordCredential(password = "p")
        val o =
            User(
                lastName = "l",
                mobilePhoneNumber = "m",
                homePhoneNumber = "h",
                publicKey = "pk",
                locale = "en-US",
                passwordUpdateRequested = true,
                enabled = true,
                principal = "pri",
                firstName = "f",
                emailAddress = "e",
                isDefault = false,
                credential = cred,
                workPhoneNumber = "w",
                name = "n",
                sessionTimeout = 30,
                authenticationType = "PASSWORD",
                userType = "DOMAIN",
                reference = "ref",
                namespace = "ns",
            )
        val m = o.toMap()
        m["lastName"] shouldBe "l"
        m["mobilePhoneNumber"] shouldBe "m"
        m["homePhoneNumber"] shouldBe "h"
        m["publicKey"] shouldBe "pk"
        m["locale"] shouldBe "en-US"
        m["passwordUpdateRequested"] shouldBe true
        m["enabled"] shouldBe true
        m["principal"] shouldBe "pri"
        m["firstName"] shouldBe "f"
        m["emailAddress"] shouldBe "e"
        m["isDefault"] shouldBe false
        m["credential"] shouldBe cred
        m["workPhoneNumber"] shouldBe "w"
        m["name"] shouldBe "n"
        m["sessionTimeout"] shouldBe 30
        m["authenticationType"] shouldBe "PASSWORD"
        m["userType"] shouldBe "DOMAIN"
        m["reference"] shouldBe "ref"
        m["namespace"] shouldBe "ns"
        m["type"] shouldBe "User"
    }

    "Job with all values populates the map" {
        val event = JobEvent(state = "RUNNING", diagnoses = emptyList())
        val o =
            Job(
                targetName = "tn",
                cancelable = true,
                jobState = "RUNNING",
                queued = false,
                updateTime = "ut",
                percentComplete = 42,
                title = "title",
                parentActionState = "pas",
                target = "tgt",
                actionType = "at",
                suspendable = true,
                emailAddresses = listOf("a@example.com"),
                startTime = "st",
                parentAction = "pa",
                targetObjectType = "tot",
                user = "u",
                events = listOf(event),
                name = "n",
                reference = "ref",
                namespace = "ns",
            )
        val m = o.toMap()
        m["targetName"] shouldBe "tn"
        m["cancelable"] shouldBe true
        m["jobState"] shouldBe "RUNNING"
        m["queued"] shouldBe false
        m["updateTime"] shouldBe "ut"
        m["percentComplete"] shouldBe 42
        m["title"] shouldBe "title"
        m["parentActionState"] shouldBe "pas"
        m["target"] shouldBe "tgt"
        m["actionType"] shouldBe "at"
        m["suspendable"] shouldBe true
        m["emailAddresses"] shouldBe listOf("a@example.com")
        m["startTime"] shouldBe "st"
        m["parentAction"] shouldBe "pa"
        m["targetObjectType"] shouldBe "tot"
        m["user"] shouldBe "u"
        m["events"] shouldBe listOf(event)
        m["name"] shouldBe "n"
        m["reference"] shouldBe "ref"
        m["namespace"] shouldBe "ns"
        m["type"] shouldBe "Job"
    }

    "AppDataContainer with all values populates the map" {
        val runtime = AppDataContainerRuntime()
        val policy = SourcingPolicy()
        val o =
            AppDataContainer(
                group = "g",
                name = "n",
                toolkit = "tk",
                runtime = runtime,
                restoration = true,
                os = "linux",
                performanceMode = "ENABLED",
                processor = "x86",
                sourcingPolicy = policy,
                currentTimeflow = "ct",
                previousTimeflow = "pt",
                creationTime = "now",
                masked = false,
                description = "d",
                provisionContainer = "pc",
                transformation = true,
                reference = "ref",
                namespace = "ns",
            )
        val m = o.toMap()
        m["toolkit"] shouldBe "tk"
        m["runtime"] shouldBe runtime
        m["restoration"] shouldBe true
        m["os"] shouldBe "linux"
        m["performanceMode"] shouldBe "ENABLED"
        m["processor"] shouldBe "x86"
        m["sourcingPolicy"] shouldBe policy
        m["currentTimeflow"] shouldBe "ct"
        m["previousTimeflow"] shouldBe "pt"
        m["creationTime"] shouldBe "now"
        m["masked"] shouldBe false
        m["description"] shouldBe "d"
        m["provisionContainer"] shouldBe "pc"
        m["transformation"] shouldBe true
        m["group"] shouldBe "g"
        m["name"] shouldBe "n"
        m["reference"] shouldBe "ref"
        m["namespace"] shouldBe "ns"
        m["type"] shouldBe "AppDataContainer"
    }
})
