/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import org.junit.Test

class DeprecatedSurfaceDocTagTest : DriverTest() {
    @Test
    fun `Test @hide`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @hide */
                            public class Test {}
                        """
                    ),
                ),
            extraArguments = errorIssues(Issues.DEPRECATED_SURFACE_DOC_TAG),
            expectedIssues =
                """
                    src/test/pkg/Test.java:2: error: Use of '@hide' to affect the API surface is deprecated [DeprecatedSurfaceDocTag]
                """,
        )
    }
}
