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

import com.android.tools.metalava.model.text.FORMAT_V6_WITHOUT_JAVA_SEALED_CLASSES
import com.android.tools.metalava.testing.java
import org.junit.Test

class SealedClassTest : DriverTest() {
    @Test
    fun `disallow sealed interface when java-sealed-classes=no`() {
        check(
            format = FORMAT_V6_WITHOUT_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed interface Test {}
                        """
                    )
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public interface Test {
                      }
                    }
                """,
            expectedIssues =
                "src/test/pkg/Test.java:3: error: `sealed` is not currently supported, see b/482391240 for more details. [AddedSealed]",
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `disallow sealed class when java-sealed-classes=no`() {
        check(
            format = FORMAT_V6_WITHOUT_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed class Test {}
                        """
                    )
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """,
            expectedIssues =
                "src/test/pkg/Test.java:3: error: `sealed` is not currently supported, see b/482391240 for more details. [AddedSealed]",
            javaLanguageLevel = "17", // required for sealed classes
        )
    }
}
