/*
 * Copyright (C) 2025 The Android Open Source Project
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

import java.io.File
import kotlin.test.assertEquals

/**
 * Assert that the file contents match the [expectedContents].
 *
 * See [String.assertSignatureContents] for how the comparison is performed.
 */
fun File.assertSignatureContents(expectedContents: String, message: String? = null) {
    readText().assertSignatureContents(expectedContents, message)
}

/**
 * Assert that the string contents match the [expectedContents].
 *
 * This has blank lines removed and the trailing new line removed and [expectedContents] has
 * [String.trimIndent] called on before comparing.
 */
fun String.assertSignatureContents(expectedContents: String, message: String? = null) {
    val contents = replace("\n\n", "\n").trimEnd()
    val expected = expectedContents.trimIndent()
    assertEquals(expected, contents, message)
}
