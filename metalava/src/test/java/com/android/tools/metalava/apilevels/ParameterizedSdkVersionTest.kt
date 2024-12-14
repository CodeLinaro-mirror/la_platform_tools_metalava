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
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedSdkVersionTest {

    data class TestData(
        val input: String,
        val expectedValid: Boolean = true,
        val expectedString: String = input,
        val expectedIncremented: String,
    ) {
        override fun toString() = input
    }

    @Parameterized.Parameter(0) lateinit var testData: TestData

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() =
            listOf(
                TestData(
                    input = "0",
                    expectedValid = false,
                    expectedIncremented = "1",
                ),
                TestData(
                    input = "1",
                    expectedIncremented = "2",
                ),
                TestData(
                    input = "01",
                    expectedString = "1",
                    expectedIncremented = "2",
                ),
                TestData(
                    input = "0.0",
                    expectedIncremented = "1.0",
                ),
                TestData(
                    input = "0.00",
                    expectedString = "0.0",
                    expectedIncremented = "1.0",
                ),
                TestData(
                    input = "0.0.0",
                    expectedIncremented = "1.0.0",
                ),
                TestData(
                    input = "0.1",
                    expectedIncremented = "1.1",
                ),
                TestData(
                    input = "1.0",
                    expectedIncremented = "2.0",
                ),
                TestData(
                    input = "1.0.0",
                    expectedIncremented = "2.0.0",
                ),
                TestData(
                    input = "1.2.3-good",
                    expectedIncremented = "2.2.3-good",
                ),
            )
    }

    /** Get an [SdkVersion] from [text]. */
    private fun getSdkVersionFromString(text: String) = SdkVersion.fromString(text)

    @Test
    fun test() {
        val version = getSdkVersionFromString(testData.input)

        assertThat(version.isValid).isEqualTo(testData.expectedValid)
        assertThat(version.toString()).isEqualTo(testData.expectedString)

        val incrementedVersion = version + 1
        val expectedIncrementedVersion = getSdkVersionFromString(testData.expectedIncremented)
        assertThat(incrementedVersion).isEqualTo(expectedIncrementedVersion)
    }
}
