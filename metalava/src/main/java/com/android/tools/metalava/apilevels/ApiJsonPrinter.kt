/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.apilevels

import com.google.gson.GsonBuilder
import java.io.PrintWriter

/** Handles converting an [Api] to a JSON version history file. */
internal class ApiJsonPrinter : ApiPrinter {
    override fun print(api: Api, writer: PrintWriter) {
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val json = api.toJson()
        gson.toJson(json, writer)
    }

    override fun toString() = "JSON"

    private fun Api.toJson() = classes.map { it.toJson() }

    private fun ApiClass.toJson() =
        toJson("class") +
            mapOf(
                "methods" to methods.map { it.toJson("method") },
                "fields" to fields.map { it.toJson("field") }
            )

    private fun ApiElement.toJson(elementType: String) =
        mapOf(
            elementType to name,
            "addedIn" to since.toString(),
            "deprecatedIn" to deprecatedIn?.toString(),
        )
}
