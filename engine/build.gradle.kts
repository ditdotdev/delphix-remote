/*
 * Copyright Datadatdat.
 */

plugins {
    kotlin("jvm")
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
    implementation("org.json:json:20251224")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}

// Skip ktlint for generated SDK objects
tasks.named("ktlint") {
    enabled = false
}
tasks.named("ktlintFormat") {
    enabled = false
}

// Maven publishing configuration
val mavenBucket = when(project.hasProperty("mavenBucket")) {
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
