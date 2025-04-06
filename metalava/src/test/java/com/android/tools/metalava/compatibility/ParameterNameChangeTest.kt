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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ParameterNameChangeTest : DriverTest() {

    @Test
    fun `Change formal parameter name class method (Incompatible)`() {
        check(
            expectedIssues =
                """
                    load-api.txt:4: error: Attempted to change parameter name from bread to toast in method test.pkg.Foo.bar [ParameterNameChange]
                """,
            signatureSource =
                """
                    package test.pkg {
                      public class Foo {
                        method public void bar(int toast);
                      }
                    }
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public class Foo {
                        method public void bar(int bread);
                      }
                    }
                """,
        )
    }

    @Test
    fun `Change formal parameter name interface method (Incompatible)`() {
        check(
            expectedIssues =
                """
                    load-api.txt:4: error: Attempted to change parameter name from bread to toast in method test.pkg.Foo.bar [ParameterNameChange]
                """,
            signatureSource =
                """
                    package test.pkg {
                      public interface Foo {
                        method public void bar(int toast);
                      }
                    }
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public interface Foo {
                        method public void bar(int bread);
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Kotlin Parameter Name Change`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/KotlinClass.kt:4: error: Attempted to change parameter name from prevName to newName in method test.pkg.KotlinClass.method1 [ParameterNameChange]
                """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class KotlinClass {
                        ctor public KotlinClass();
                        method public final String? method1(String prevName);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            class KotlinClass {
                                fun method1(newName: String): String? = null
                            }
                        """
                    )
                ),
        )
    }
}
