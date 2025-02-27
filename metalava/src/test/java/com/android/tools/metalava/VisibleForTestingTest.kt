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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.VisibleForTesting
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/**
 * Test that the `VisibleForTesting` annotation works correctly.
 *
 * e.g. if it has an `otherwise` attribute that is used as the actual visibility of the annotated
 * item.
 */
class VisibleForTestingTest : DriverTest() {
    /** Check the behavior of `VisibleForTesting` annotations. */
    private fun checkVisibleForTesting(
        testFile: TestFile,
        api: String,
    ) {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    testFile,
                    visibleForTestingSource,
                ),
            api = api,
        )
    }

    @Test
    fun `Test VisibleForTesting constants - java`() {
        // Regression test for issue b/118763806.
        checkVisibleForTesting(
            testFile =
                java(
                    """
                        package test.pkg;
                        import androidx.annotation.VisibleForTesting;

                        @SuppressWarnings({"ClassNameDiffersFromFileName", "WeakerAccess"})
                        public class ProductionCodeJava {
                            private ProductionCodeJava() { }

                            @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
                            public void shouldBeProtected() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                            protected void shouldBePrivate1() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                            public void shouldBePrivate2() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                            public void shouldBePackagePrivate() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.NONE)
                            public void shouldBeHidden() {
                            }
                        }
                    """
                ),
            api =
                """
                    package test.pkg {
                      public class ProductionCodeJava {
                        method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) protected void shouldBeProtected();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test VisibleForTesting numbers - java`() {
        // Test what happens when numbers are used for the otherwise value instead of symbols.
        checkVisibleForTesting(
            testFile =
                java(
                    """
                        package test.pkg;
                        import androidx.annotation.VisibleForTesting;

                        @SuppressWarnings({"ClassNameDiffersFromFileName", "WeakerAccess"})
                        public class ProductionCodeJava {
                            private ProductionCodeJava() { }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PROTECTED})
                            public void shouldBeProtected() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PRIVATE})
                            protected void shouldBePrivate1() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PRIVATE})
                            public void shouldBePrivate2() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PACKAGE_PRIVATE})
                            public void shouldBePackagePrivate() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.NONE})
                            public void shouldBeHidden() {
                            }
                        }
                    """
                ),
            api =
                """
                    package test.pkg {
                      public class ProductionCodeJava {
                        method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) protected void shouldBeProtected();
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test VisibleForTesting constants - kotlin`() {
        // Regression test for issue b/118763806.
        checkVisibleForTesting(
            testFile =
                kotlin(
                    """
                        package test.pkg
                        import androidx.annotation.VisibleForTesting

                        open class ProductionCodeKotlin private constructor() {

                            @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
                            fun shouldBeProtected() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                            protected fun shouldBePrivate1() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                            fun shouldBePrivate2() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                            fun shouldBePackagePrivate() {
                            }

                            @VisibleForTesting(otherwise = VisibleForTesting.NONE)
                            fun shouldBeHidden() {
                            }
                        }
                    """
                ),
            api =
                """
                    package test.pkg {
                      public class ProductionCodeKotlin {
                        method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) protected final void shouldBeProtected();
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test VisibleForTesting numbers - kotlin`() {
        // Test what happens when numbers are used for the otherwise value instead of symbols.
        checkVisibleForTesting(
            testFile =
                kotlin(
                    """
                        package test.pkg
                        import androidx.annotation.VisibleForTesting

                        open class ProductionCodeKotlin private constructor() {

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PROTECTED})
                            fun shouldBeProtected() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PRIVATE})
                            protected fun shouldBePrivate1() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PRIVATE})
                            fun shouldBePrivate2() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.PACKAGE_PRIVATE})
                            fun shouldBePackagePrivate() {
                            }

                            @VisibleForTesting(otherwise = ${VisibleForTesting.NONE})
                            fun shouldBeHidden() {
                            }
                        }
                    """
                ),
            api =
                """
                    package test.pkg {
                      public class ProductionCodeKotlin {
                        method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) protected final void shouldBeProtected();
                      }
                    }
                """,
        )
    }
}
