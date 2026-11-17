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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.SelectedApiVariantsTestData
import com.android.tools.metalava.model.testing.surfaces.selectedApiVariantsTestData
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * Parameterized tests for [SelectedApi.itemApiVariants] using data from
 * [selectedApiVariantsTestData].
 */
class CommonParameterizedSelectedApiVariantsTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var testData: SelectedApiVariantsTestData

    companion object {
        @JvmStatic @Parameterized.Parameters fun params() = selectedApiVariantsTestData
    }

    @SupportedInputFormats(InputFormat.SIGNATURE)
    @Test
    fun `Test selectedApiVariants`() {
        val testFixture =
            TestFixture(
                apiSurfaceRules = ApiSurfaceRules.create(testData.needsBase),
            )
        runCodebaseTest(inputSet(testData.signatureFiles), testFixture = testFixture) {
            codebase.assertSelectedApiVariants(testData.expectedSelectedApiVariants)
        }
    }
}
