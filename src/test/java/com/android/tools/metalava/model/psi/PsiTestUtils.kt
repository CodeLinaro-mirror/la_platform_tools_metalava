/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ENV_VAR_METALAVA_TESTS_RUNNING
import com.android.tools.metalava.Options
import com.android.tools.metalava.options
import com.android.tools.metalava.parseSources
import com.android.tools.metalava.tempDirectory
import com.intellij.openapi.util.Disposer
import java.io.File
import kotlin.test.assertNotNull

inline fun testCodebase(
    vararg sources: TestFile,
    action: (PsiBasedCodebase) -> Unit
) {
    // TODO(b/198440244): Remove parameterization
    for (enableKotlinPsi in arrayOf(true, false)) {
        tempDirectory { tempDirectory ->
            val codebase = createTestCodebase(tempDirectory, enableKotlinPsi, *sources)
            try {
                action(codebase)
            } finally {
                destroyTestCodebase(codebase)
            }
        }
    }
}

fun createTestCodebase(
    directory: File,
    enableKotlinPsi: Boolean,
    vararg sources: TestFile
): PsiBasedCodebase {
    System.setProperty(ENV_VAR_METALAVA_TESTS_RUNNING, SdkConstants.VALUE_TRUE)
    Disposer.setDebugMode(true)
    options = Options(emptyArray())

    return parseSources(
        sources = sources.map { it.createFile(directory) },
        description = "Test ${if (enableKotlinPsi) "Kotlin PSI" else "UAST"} Codebase",
        enableKotlinPsi = enableKotlinPsi
    )
}

fun destroyTestCodebase(codebase: PsiBasedCodebase) {
    codebase.dispose()

    UastEnvironment.disposeApplicationEnvironment()
    Disposer.assertIsEmpty(true)
}

fun PsiBasedCodebase.assertClass(qualifiedName: String): PsiClassItem {
    val classItem = this.findClass(qualifiedName)
    assertNotNull(classItem) { "Expected $qualifiedName to be defined" }
    return classItem
}
