/*
 * Copyright Datadatdat.
 */

package com.delphix.sdk.objects

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.json.JSONObject

/**
 * Round-trips the JSON-deserialization paths on the data-class DTOs.
 *
 * These cover the [Companion.fromJson] factories and the data class equals/hashCode/copy
 * synthetic methods.
 */
class FromJsonTest : StringSpec({

    "Repository.fromJson parses a complete payload" {
        val payload =
            JSONObject(
                """
                {
                  "type": "PgSQLInstall",
                  "reference": "PGSQL_INSTALL-2",
                  "name": "/usr/pgsql-9.6",
                  "environment": "UNIX_HOST_ENVIRONMENT-4",
                  "linkingEnabled": true,
                  "parameters": "p",
                  "provisioningEnabled": true,
                  "staging": false,
                  "toolkit": "tk",
                  "version": "9.6.11"
                }
                """.trimIndent(),
            )

        val repo = Repository.fromJson(payload)
        repo.type shouldBe "PgSQLInstall"
        repo.reference shouldBe "PGSQL_INSTALL-2"
        repo.name shouldBe "/usr/pgsql-9.6"
        repo.environment shouldBe "UNIX_HOST_ENVIRONMENT-4"
        repo.linkingEnabled shouldBe true
        repo.parameters shouldBe "p"
        repo.provisioningEnabled shouldBe true
        repo.staging shouldBe false
        repo.toolkit shouldBe "tk"
        repo.version shouldBe "9.6.11"
    }

    "Repository.fromJson treats missing optional parameters/toolkit as empty strings" {
        val payload =
            JSONObject(
                """
                {
                  "type": "T",
                  "reference": "R",
                  "name": "N",
                  "environment": "E",
                  "linkingEnabled": false,
                  "provisioningEnabled": false,
                  "staging": true,
                  "version": "1.0"
                }
                """.trimIndent(),
            )

        val repo = Repository.fromJson(payload)
        repo.parameters shouldBe ""
        repo.toolkit shouldBe ""
        repo.linkingEnabled shouldBe false
        repo.provisioningEnabled shouldBe false
        repo.staging shouldBe true
    }

    "Repository data class equality and copy work as expected" {
        val a =
            Repository(
                type = "T",
                reference = "R",
                name = "N",
                environment = "E",
                linkingEnabled = true,
                parameters = "p",
                provisioningEnabled = true,
                staging = false,
                toolkit = "tk",
                version = "v",
            )
        val b = a.copy()
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a.toString().contains("Repository") shouldBe true

        val different = a.copy(name = "different")
        (a == different) shouldBe false
    }

    "Environment.fromJson parses a complete payload" {
        val payload =
            JSONObject(
                """
                {
                  "type": "UnixHostEnvironment",
                  "name": "linuxtarget",
                  "aseHostEnvironmentParameters": "ase",
                  "description": "desc",
                  "enabled": true,
                  "host": "UNIX_HOST-1",
                  "logCollectionEnabled": false,
                  "primaryUser": "USER-1",
                  "reference": "UNIX_HOST_ENVIRONMENT-4"
                }
                """.trimIndent(),
            )

        val env = Environment.fromJson(payload)
        env.type shouldBe "UnixHostEnvironment"
        env.name shouldBe "linuxtarget"
        env.aseHostEnvironmentParameters shouldBe "ase"
        env.description shouldBe "desc"
        env.enabled shouldBe true
        env.host shouldBe "UNIX_HOST-1"
        env.logCollectionEnabled shouldBe false
        env.primaryUser shouldBe "USER-1"
        env.reference shouldBe "UNIX_HOST_ENVIRONMENT-4"
    }

    "Environment.fromJson tolerates an empty JSON payload via opt* defaults" {
        val empty = JSONObject("{}")
        val env = Environment.fromJson(empty)
        env.type shouldBe ""
        env.name shouldBe ""
        env.aseHostEnvironmentParameters shouldBe ""
        env.description shouldBe ""
        env.enabled shouldBe false
        env.host shouldBe ""
        env.logCollectionEnabled shouldBe false
        env.primaryUser shouldBe ""
        env.reference shouldBe ""
    }

    "Environment data class equality and copy work as expected" {
        val a =
            Environment(
                type = "T",
                name = "N",
                aseHostEnvironmentParameters = "a",
                description = "d",
                enabled = true,
                host = "h",
                logCollectionEnabled = true,
                primaryUser = "u",
                reference = "r",
            )
        val b = a.copy()
        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a.toString().contains("Environment") shouldBe true
        (a == a.copy(name = "other")) shouldBe false
        // Component accessors exercise every componentN()
        a.component1() shouldBe "T"
        a.component2() shouldBe "N"
        a.component3() shouldBe "a"
        a.component4() shouldBe "d"
        a.component5() shouldBe true
        a.component6() shouldBe "h"
        a.component7() shouldBe true
        a.component8() shouldBe "u"
        a.component9() shouldBe "r"
    }

    "Repository data class component accessors and inequality" {
        val a =
            Repository(
                type = "T",
                reference = "R",
                name = "N",
                environment = "E",
                linkingEnabled = true,
                parameters = "p",
                provisioningEnabled = true,
                staging = false,
                toolkit = "tk",
                version = "v",
            )
        a.component1() shouldBe "T"
        a.component2() shouldBe "R"
        a.component3() shouldBe "N"
        a.component4() shouldBe "E"
        a.component5() shouldBe true
        a.component6() shouldBe "p"
        a.component7() shouldBe true
        a.component8() shouldBe false
        a.component9() shouldBe "tk"
        a.component10() shouldBe "v"
        // Inequality across every component
        (a == a.copy(type = "X")) shouldBe false
        (a == a.copy(reference = "X")) shouldBe false
        (a == a.copy(name = "X")) shouldBe false
        (a == a.copy(environment = "X")) shouldBe false
        (a == a.copy(linkingEnabled = false)) shouldBe false
        (a == a.copy(parameters = "X")) shouldBe false
        (a == a.copy(provisioningEnabled = false)) shouldBe false
        (a == a.copy(staging = true)) shouldBe false
        (a == a.copy(toolkit = "X")) shouldBe false
        (a == a.copy(version = "X")) shouldBe false
    }
})
