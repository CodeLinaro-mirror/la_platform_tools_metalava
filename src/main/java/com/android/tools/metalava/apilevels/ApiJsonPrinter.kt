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
import java.io.File
import java.io.PrintStream

/**
 * Handles converting an [Api] to a JSON version history file.
 *
 * Note: this currently doesn't need to be a class, but will have state added in the following CL.
 */
internal class ApiJsonPrinter {
    /**
     * Writes the [api] as JSON to the [outputFile]
     */
    fun print(api: Api, outputFile: File) {
        val gson = GsonBuilder().create()
        val json = api.toJson()
        val printStream = PrintStream(outputFile)
        gson.toJson(json, printStream)
    }

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
            "deprecatedIn" to if (isDeprecated) {
                deprecatedIn.toString()
            } else {
                null
            }
        )
}
