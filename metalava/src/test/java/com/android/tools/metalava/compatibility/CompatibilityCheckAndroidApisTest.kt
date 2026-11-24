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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.testing.getAndroidTxt
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runners.Parameterized

class CompatibilityCheckAndroidApisTest(
    private val apiLevelCheck: ApiLevelCheck,
) : DriverTest() {

    data class ApiLevelCheck(
        val apiLevel: Int,
        val expectedIssues: String,
        val extraArgs: List<String>,
        val disabled: Boolean = false,
    ) {
        override fun toString(): String = "${apiLevel - 1} to $apiLevel"
    }

    companion object {
        fun hide(vararg issues: Issue) = hiddenIssues(*issues).toList()

        /** Data for each api version to check. */
        private val data =
            listOf(
                ApiLevelCheck(
                    27,
                    "",
                    hide(
                        Issues.ADDED_CLASS,
                        Issues.ADDED_FIELD,
                        Issues.ADDED_FINAL,
                        Issues.ADDED_INTERFACE,
                        Issues.ADDED_METHOD,
                        Issues.ADDED_PACKAGE,
                        Issues.CHANGED_ABSTRACT,
                        Issues.CHANGED_DEPRECATED,
                        Issues.CHANGED_THROWS,
                        Issues.REMOVED_METHOD,
                    ),
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun testParameters() = data
    }

    @Test
    fun `Test All Android API levels`() {
        // Checks API across Android SDK versions and makes sure the results are
        // intentional (to help shake out bugs in the API compatibility checker)

        // Temporary let block to keep the same indent as before when this was looping over the data
        // itself.
        let {
            if (apiLevelCheck.disabled) {
                throw AssumptionViolatedException("Test disabled")
            }

            val apiLevel = apiLevelCheck.apiLevel
            val expectedIssues = apiLevelCheck.expectedIssues
            val extraArgs = apiLevelCheck.extraArgs.toTypedArray()

            val current = getAndroidTxt(apiLevel)
            val previous = getAndroidTxt(apiLevel - 1)

            check(
                extraArguments = extraArgs,
                expectedIssues = expectedIssues,
                checkCompatibilityApiReleased = previous.readText(),
                signatureSource = current.readText(),
                skipEmitPackages = emptyList(),
            )
        }
    }
}
