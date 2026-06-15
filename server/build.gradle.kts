/*
 * Copyright Dit.
 */

plugins {
    kotlin("jvm")
    jacoco
    "com.github.ben-manes.versions"
    `maven-publish`

}

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven {
        name = "dit"
        url = uri("https://dit-maven.s3.amazonaws.com")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("dev.dit:remote-sdk:1.9.7")
    implementation("dev.dit:command-executor:1.9.8")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.slf4j:slf4j-api:2.0.18")
    implementation(project(path = ":engine", configuration = "default"))
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.4.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
}

// Jar configuration
group = "dev.dit"
version = when(project.hasProperty("version")) {
    true -> project.property("version")!!
    false -> "latest"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val jar by tasks.getting(Jar::class) {
    archiveBaseName.set("delphix-remote")
}

// Maven publishing configuration
val mavenBucket = when(project.hasProperty("mavenBucket")) {
    true -> project.property("mavenBucket")
    false -> "dit-maven"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.dit"
            artifactId = "delphix-remote-server"

            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "dit"
            url = uri("s3://$mavenBucket")
            authentication {
                create<AwsImAuthentication>("awsIm")
            }
        }
    }
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

