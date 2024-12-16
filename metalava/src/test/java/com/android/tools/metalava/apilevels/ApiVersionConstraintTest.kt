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

package com.android.tools.metalava.apilevels

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Verifies the constraints on [ApiVersion] properties. */
@RunWith(Parameterized::class)
class ApiVersionConstraintTest {

    data class TestData(
        val name: String,
        val major: Int,
        val minor: Int? = null,
        val patch: Int? = null,
        val preReleaseQuality: String? = null,
        val expectedError: String,
    ) {
        override fun toString() = name
    }

    @Parameterized.Parameter(0) lateinit var testData: TestData

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() =
            listOf(
                TestData(
                    name = "invalid major",
                    major = -1,
                    expectedError = "major must be greater than or equal to 0 but was -1",
                ),
                TestData(
                    name = "invalid minor",
                    major = 0,
                    minor = -2,
                    expectedError = "minor must be greater than or equal to 0 but was -2",
                ),
                TestData(
                    name = "missing minor",
                    major = 0,
                    patch = 0,
                    expectedError = "patch (0) was specified without also specifying minor",
                ),
                TestData(
                    name = "invalid patch",
                    major = 0,
                    minor = 1,
                    patch = -3,
                    expectedError = "patch must be greater than or equal to 0 but was -3",
                ),
                TestData(
                    name = "missing patch",
                    major = 0,
                    preReleaseQuality = "alpha01",
                    expectedError =
                        "preReleaseQuality (alpha01) was specified without also specifying patch",
                ),
            )
    }

    @Test
    fun testConstraints() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                ApiVersion(
                    testData.major,
                    testData.minor,
                    testData.patch,
                    testData.preReleaseQuality,
                )
            }

        assertThat(exception.message).isEqualTo(testData.expectedError)
    }
}
