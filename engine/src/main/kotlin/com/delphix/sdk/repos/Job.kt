/**
 * Copyright (c) 2019 by Delphix. All rights reserved.
 */

package com.delphix.sdk.repos

import org.json.JSONObject
import com.delphix.sdk.Http as Http

/**
 * Represents a job object.
 */
class Job(
    var http: Http,
) {
    val root: String = "/resources/json/delphix/job"

    /**
     * Returns a list of jobs in the system. Jobs are listed in start time order.
     */
    fun list(): JSONObject {
        return http.handleGet("$root")
    }

    /**
     * Retrieve the specified Job object.
     */
    fun read(ref: String): JSONObject {
        return http.handleGet("$root/$ref")
    }

    /**
     * Update the specified Job object.
     */
    fun update(
        ref: String,
        payload: com.delphix.sdk.objects.Job,
    ): JSONObject {
        return http.handlePost("$root/$ref", payload.toMap())
    }
}
