/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.testing.surfaces.SelectedApiVariantsTestData
import com.android.tools.metalava.model.testing.surfaces.selectedApiVariantsTestData
import com.android.tools.metalava.model.text.apiVariantTypeForTestSignatureFile
import com.android.tools.metalava.testing.createFiles
import org.junit.Test
import org.junit.runners.Parameterized

class ParameterizedSelectedApiVariantsTest : DriverTest() {

    @Parameterized.Parameter(0) lateinit var testData: SelectedApiVariantsTestData

    companion object {
        @JvmStatic @Parameterized.Parameters fun params() = selectedApiVariantsTestData
    }

    @Test
    fun `Test previously released codebases`() {
        // Split the released and removed files into separate lists to pass to the
        // --check-compatibility:... options.
        val (previouslyReleasedApi, previouslyRemovedApi) =
            testData.signatureFiles
                .createFiles(temporaryFolder.newFolder())
                .map { it.path }
                .partition { apiVariantTypeForTestSignatureFile(it) != ApiVariantType.REMOVED }

        // If the test needs a base ApiSurface then add --show-annotation SystemApi to create one.
        val extraArguments =
            if (testData.needsBase) arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_API)
            else emptyArray()

        check(
            extraArguments = extraArguments,
            // Although this test is only check the selectedApiVariants state it must provide source
            // files as otherwise the compatibility check will fail as it will compare the API
            // loaded from the signature files against an empty Codebase and report that items have
            // been removed from the API.
            sourceFiles = testData.javaSourceFiles.toTypedArray(),
            checkCompatibilityApiReleasedList = previouslyReleasedApi,
            checkCompatibilityRemovedApiReleasedList = previouslyRemovedApi,
        ) {
            val previouslyReleasedCodebase = options.previouslyReleasedCodebase!!
            previouslyReleasedCodebase.assertSelectedApiVariants(
                testData.expectedSelectedApiVariants
            )
        }
    }
}
