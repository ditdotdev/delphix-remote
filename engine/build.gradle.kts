/*
 * Copyright Datadatdat.
 */

plugins {
    kotlin("jvm")
    jacoco
    `maven-publish`
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
    implementation("org.json:json:20260522")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
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
val mavenBucket = when (project.hasProperty("mavenBucket")) {
    true -> project.property("mavenBucket")
    false -> "datadatdat-maven"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.datadatdat"
            artifactId = "delphix-sdk"

            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "datadatdat"
            url = uri("s3://$mavenBucket")
            authentication {
                create<AwsImAuthentication>("awsIm")
            }
        }
    }
}
