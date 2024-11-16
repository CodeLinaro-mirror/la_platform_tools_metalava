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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Assert.assertThrows

class SignatureFileTest : BaseTextCodebaseTest() {

    /** Get an empty [SignatureFile] for [path]. */
    private fun signatureFile(path: String): SignatureFile {
        val inputFile = signature(path, "").createFile(temporaryFolder.root)
        return SignatureFile.forTest(listOf(inputFile))[0]
    }

    /**
     * Check that the [ApiVariant] obtained from [apiSurfaces] for [path] has the expected
     * [ApiVariant.surface] (as returned by [expectedVariantType]) and the expected [ApiVariantType]
     * (as specified by [expectedVariantType]).
     */
    private fun checkApiVariantFor(
        path: String,
        expectedSurfaceGetter: (ApiSurfaces) -> ApiSurface,
        expectedVariantType: ApiVariantType,
        apiSurfaces: ApiSurfaces = ApiSurfaces.create(needsBase = true),
    ) {
        val signatureFile = signatureFile(path)
        val apiVariant = signatureFile.apiVariantFor(apiSurfaces)
        val expectedSurface = expectedSurfaceGetter(apiSurfaces)
        assertSame(expectedSurface, apiVariant.surface, "expected matching surface")
        assertSame(expectedVariantType, apiVariant.type, "expected matching type")
    }

    @Test
    fun `Test apiVariantFor base-current`() {
        checkApiVariantFor(
            "base-current.txt",
            { it.base!! },
            ApiVariantType.CORE,
        )
    }

    @Test
    fun `Test apiVariantFor base-removed`() {
        checkApiVariantFor(
            "base-removed.txt",
            { it.base!! },
            ApiVariantType.REMOVED,
        )
    }

    @Test
    fun `Test apiVariantFor current`() {
        checkApiVariantFor(
            "current.txt",
            { it.main },
            ApiVariantType.CORE,
        )
    }

    @Test
    fun `Test apiVariantFor removed`() {
        checkApiVariantFor(
            "removed.txt",
            { it.main },
            ApiVariantType.REMOVED,
        )
    }

    @Test
    fun `Test apiVariantFor base-current input no base ApiSurface`() {
        // Make sure that an error is thrown if the signature file requires a base ApiSurface but
        // the apiSurfaces does not provide one.
        val exception =
            assertThrows(IllegalStateException::class.java) {
                checkApiVariantFor(
                    "base-removed.txt",
                    { error("should not be called") },
                    ApiVariantType.CORE,
                    apiSurfaces = ApiSurfaces.DEFAULT,
                )
            }

        val message = cleanupString(exception.message!!)
        assertEquals(
            "TESTROOT/base-removed.txt expects a base API surface to be available but it is not",
            message
        )
    }
}
