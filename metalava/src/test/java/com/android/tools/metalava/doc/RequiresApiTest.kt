/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.doc

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.requiresApiSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Test handling of `RequiresApi` annotation in [DocAnalyzer]. */
class RequiresApiTest : DriverTest() {
    @Test
    fun `Check RequiresApi handling`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.RequiresApi;
                            @RequiresApi(value = 21)
                            public class MyClass1 {
                            }
                        """
                    ),
                    requiresApiSource
                ),
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 21 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class MyClass1 {
                            public MyClass1() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Check RequiresApi handling of full version code`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.RequiresApi;
                            // Full encoding for S
                            @RequiresApi(value = 3100000)
                            public class MyClass1 {
                            // Full encoding for BAKLAVA_1
                            @RequiresApi(value = 3600001)
                            public void foo() {}
                            }
                        """
                    ),
                    requiresApiSource
                ),
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        // TODO(b/424435764): Should decode 3100000 to 31.0 and 3600001 to 36.1.
                        """
                            package test.pkg;
                            /** @apiSince 31.0 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class MyClass1 {
                            public MyClass1() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 36.1 */
                            public void foo() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
        )
    }
}
