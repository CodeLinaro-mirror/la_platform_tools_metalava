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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariantType
import java.io.File
import org.junit.Assert.assertEquals

/** Verify that two signature files match. */
fun assertSignatureFilesMatch(
    expected: String,
    actual: String,
    expectedFormat: FileFormat = FileFormat.LATEST,
    message: String? = null
) {
    val expectedPrepared = prepareSignatureFileForTest(expected, expectedFormat)
    val actualStripped = actual.stripBlankLines()
    assertEquals(message, expectedPrepared, actualStripped)
}

fun String.stripBlankLines() = lines().filter { it.isNotBlank() }.joinToString("\n")

/** Strip comments, trim indent, and add a signature format version header if one is missing */
fun prepareSignatureFileForTest(expectedApi: String, format: FileFormat): String {
    val header = format.header()

    return expectedApi
        .trimIndent()
        .let { if (!it.startsWith(FileFormat.SIGNATURE_FORMAT_PREFIX)) header + it else it }
        .trim()
}

/**
 * Get the [ApiVariantType] for a test signature file with [name].
 *
 * If it contains "removed" then it will be [ApiVariantType.REMOVED] else it will be
 * [ApiVariantType.CORE].
 */
fun apiVariantTypeForTestSignatureFile(name: String) =
    when {
        name.contains("removed") -> ApiVariantType.REMOVED
        else -> ApiVariantType.CORE
    }

/**
 * Check if the test signature file with [name] is for the main API surface.
 *
 * If it contains "base" then it will return `false` as it is for the base API surface, otherwise it
 * will return `true` as it is assumed it is for the main API surface.
 */
private fun isTestSignatureFileForMainApiSurface(name: String) =
    when {
        name.contains("base") -> false
        else -> true
    }

/**
 * Create a list of [SignatureFile]s from [files] suitable for testing.
 *
 * This extracts information from the file name as to the purpose of each [SignatureFile] as
 * follows:
 * * If the name contains `base` then it is assumed to be for the [ApiSurfaces.base], otherwise it
 *   is assumed to be for [ApiSurfaces.main].
 * * If the name contains `removed` then it is assumed to be for the [ApiVariantType.REMOVED],
 *   otherwise it is assumed to be for [ApiVariantType.CORE].
 */
fun SignatureFile.Companion.forTest(files: List<File>) =
    fromFiles(
        files,
        apiVariantTypeChooser = { file -> apiVariantTypeForTestSignatureFile(file.name) },
        forMainApiSurfacePredicate = { _, file -> isTestSignatureFileForMainApiSurface(file.name) }
    )
