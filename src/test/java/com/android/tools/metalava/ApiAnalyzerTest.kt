/*
 * Copyright (C) 2020 The Android Open Source Project
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

import org.junit.Test

class ApiAnalyzerTest : DriverTest() {
    @Test
    fun `private companion object inside an interface`() {
        check(
            compatibilityMode = false,
            expectedIssues = """
                src/test/pkg/MyInterface.kt:4: error: Do not use private companion objects inside interfaces as these become public if targeting Java 8 or older. [PrivateCompanion]
            """,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg

                        interface MyInterface {
                            private companion object
                        }
                    """
                )
            )
        )
    }
}