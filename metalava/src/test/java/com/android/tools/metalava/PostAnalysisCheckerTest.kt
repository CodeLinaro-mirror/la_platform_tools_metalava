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

import com.android.tools.metalava.cli.common.TestEnvironment
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Assert.assertThrows
import org.junit.Test

/** Verify the behavior of the [TestEnvironment.postAnalysisChecker]. */
class PostAnalysisCheckerTest : DriverTest() {

    @Test
    fun `Test post analysis check test failure fails the check method`() {
        val exception =
            assertThrows(AssertionError::class.java) {
                check(
                    sourceFiles =
                        arrayOf(
                            java(
                                """
                                package test.pkg;
                                public class Foo {
                                }
                            """
                            ),
                        ),
                ) {
                    fail("Check failure")
                }
            }

        assertIs<AssertionError>(exception)
        assertEquals("Check failure", exception.message)
    }

    @Test
    fun `Test post analysis check test passes`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                            }
                        """
                    ),
                ),
        ) {
            // Check that the options is provided.
            assertTrue(options.showUnannotated, message = "options.showUnannotated")

            // Check that the codebase is provided.
            assertEquals(
                1,
                codebase.getTopLevelClassesFromSource().count(),
                message = "top level classes count"
            )
        }
    }
}
