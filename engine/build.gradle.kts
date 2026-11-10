// Copyright Dit 2026
// SPDX-License-Identifier: BUSL-1.1

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    kotlin("jvm")
    jacoco
    id("com.vanniktech.maven.publish") version("0.37.0")
}

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlinx")
}

val jar by tasks.getting(Jar::class) {
    archiveBaseName.set("delphix-sdk")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.json:json:20260814")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
}

// Only lint the files we author here. SDK objects/repos are generated.
// The parent project already sets args; we override them with a narrower file list.
tasks.named<JavaExec>("ktlint") {
    setArgs(
        listOf(
            "src/main/kotlin/com/delphix/sdk/Http.kt",
            "src/main/kotlin/com/delphix/sdk/DelphixApiError.kt",
            "src/test/**/*.kt",
        ),
    )
}
tasks.named<JavaExec>("ktlintFormat") {
    setArgs(
        listOf(
            "-F",
            "src/main/kotlin/com/delphix/sdk/Http.kt",
            "src/main/kotlin/com/delphix/sdk/DelphixApiError.kt",
            "src/test/**/*.kt",
        ),
    )
}

// Test configuration
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
    }
}

// Maven publishing configuration
//
// Releases are published to Maven Central (namespace dev.dit) by the
// com.vanniktech.maven.publish plugin, which also supplies the sources jar,
// javadoc jar and signatures that Central requires. Credentials and the
// signing key come from Gradle properties (ORG_GRADLE_PROJECT_* in CI); see
// DEVELOPING.md.
mavenPublishing {
    // Upload as a deployment that waits for a manual "Publish" click in the
    // Central Portal: anything released to Central is permanent.
    publishToMavenCentral()

    // Sign only when a key is supplied, so `publishToMavenLocal` works on a
    // developer machine or pull request build with no key. A release without
    // signatures cannot slip through: Central rejects unsigned deployments.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    // Kotlin sources have no javadoc; Central accepts an empty javadoc jar.
    configureBasedOnAppliedPlugins(
        javadocJar = JavadocJar.Empty(),
        sourcesJar = SourcesJar.Sources(),
    )

    coordinates("dev.dit", "delphix-sdk", version.toString())

    pom {
        name.set("Dit Delphix SDK")
        description.set("Kotlin client for the Delphix engine API, used by the Dit Delphix remote provider.")
        inceptionYear.set("2026")
        url.set("https://github.com/ditdotdev/delphix-remote")
        licenses {
            license {
                name.set("BUSL-1.1")
                url.set("https://github.com/ditdotdev/delphix-remote/blob/master/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ditdotdev")
                name.set("Dit")
                url.set("https://github.com/ditdotdev")
            }
        }
        scm {
            url.set("https://github.com/ditdotdev/delphix-remote")
            connection.set("scm:git:https://github.com/ditdotdev/delphix-remote.git")
            developerConnection.set("scm:git:ssh://git@github.com/ditdotdev/delphix-remote.git")
        }
    }
}
