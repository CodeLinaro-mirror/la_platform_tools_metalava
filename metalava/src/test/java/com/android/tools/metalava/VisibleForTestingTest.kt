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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/**
 * Test that the `VisibleForTesting` annotation does not impact API visibility unless the hide and
 * show annotation options are used.
 *
 * Previously if an `otherwise` value was provided that was used at the visibility of the annotated
 * item. e.g. if it has an `otherwise` attribute that is used as the actual visibility of the
 * annotated item.
 *
 * Now by default the annotation has no impact on API visibility, but the `--hide-annotation` and
 * `--show-annotation` options can be used to mostly replicate the old behavior.
 */
class VisibleForTestingTest : DriverTest() {
    /** Check the behavior of `VisibleForTesting` annotations. */
    private fun checkVisibleForTesting(
        format: FileFormat,
        testFile: TestFile,
        api: String,
        useShowAndHideOptions: Boolean,
    ) {
        check(
            format = format,
            sourceFiles =
                arrayOf(
                    testFile,
                    visibleForTestingSource,
                ),
            api = api,
            extraArguments =
                if (useShowAndHideOptions) {
                    arrayOf(
                        "--hide-annotation",
                        "androidx.annotation.VisibleForTesting",
                        "--show-annotation",
                        "androidx.annotation.VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED)",
                        "--show-annotation",
                        "androidx.annotation.VisibleForTesting(otherwise=4)",
                        "--hide",
                        "UnhiddenSystemApi",
                    )
                } else {
                    emptyArray()
                }
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

                            @VisibleForTesting
                            public void defaultOtherwiseShouldBePrivate() {
                            }
                        }
                    """
                ),
            api =
                """
                    package test.pkg {
                      public class ProductionCodeJava {
                        method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) public void shouldBeProtected();
                      }
                    }
                """,
            format = FileFormat.V2,
            useShowAndHideOptions = true
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
                        method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) public void shouldBeProtected();
                      }
                    }
                """,
            format = FileFormat.V2,
            useShowAndHideOptions = true,
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

                            @VisibleForTesting(VisibleForTesting.PROTECTED)
                            fun shouldBeProtected2() {
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
                        method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) public final void shouldBeProtected();
                        method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) public final void shouldBeProtected2();
                      }
                    }
                """,
            format = FileFormat.V4,
            useShowAndHideOptions = true,
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

                            @VisibleForTesting(${VisibleForTesting.PROTECTED})
                            fun shouldBeProtected2() {
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
                        method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) public final void shouldBeProtected();
                        method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) public final void shouldBeProtected2();
                      }
                    }
                """,
            format = FileFormat.V4,
            useShowAndHideOptions = true,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test VisibleForTesting without show and hide annotation options -- kotlin`() {
        checkVisibleForTesting(
            testFile =
                kotlin(
                    """
                    package test.pkg
                    import androidx.annotation.VisibleForTesting

                    open class ProductionCodeKotlin private constructor() {
                        @VisibleForTesting
                        fun withDefaultOtherwise() = Unit

                        @VisibleForTesting(VisibleForTesting.PROTECTED)
                        fun withProtectedOtherwise() = Unit

                        @VisibleForTesting(VisibleForTesting.PRIVATE)
                        fun withPrivateOtherwise() = Unit

                        @VisibleForTesting(VisibleForTesting.PACKAGE_PRIVATE)
                        fun withPackagePrivateOtherwise() = Unit

                        @VisibleForTesting(VisibleForTesting.NONE)
                        fun withNoneOtherwise() = Unit

                        @VisibleForTesting(${VisibleForTesting.PROTECTED})
                        fun withNumericProtectedOtherwise() = Unit

                        @VisibleForTesting(${VisibleForTesting.PRIVATE})
                        fun withNumericPrivateOtherwise() = Unit

                        @VisibleForTesting(${VisibleForTesting.PACKAGE_PRIVATE})
                        fun withNumericPackagePrivateOtherwise() = Unit

                        @VisibleForTesting(${VisibleForTesting.NONE})
                        fun withNumericNoneOtherwise() = Unit
                    }
                    """
                ),
            format = FileFormat.V4,
            api =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class ProductionCodeKotlin {
                    method @VisibleForTesting public final void withDefaultOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.NONE) public final void withNoneOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.NONE}) public final void withNumericNoneOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.PACKAGE_PRIVATE}) public final void withNumericPackagePrivateOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.PRIVATE}) public final void withNumericPrivateOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) public final void withNumericProtectedOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE) public final void withPackagePrivateOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PRIVATE) public final void withPrivateOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) public final void withProtectedOtherwise();
                  }
                }
                """,
            useShowAndHideOptions = false,
        )
    }

    @Test
    fun `Test VisibleForTesting without show and hide annotation options - java`() {
        checkVisibleForTesting(
            testFile =
                java(
                    """
                    package test.pkg;
                    import androidx.annotation.VisibleForTesting;

                    public class ProductionCodeJava {
                        private ProductionCodeJava() { }

                        @VisibleForTesting
                        public void withDefaultOtherwise() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
                        public void withProtectedOtherwise() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
                        public void withPrivateOtherwise() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
                        public void withNoneOtherwise() {
                        }

                        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
                        public void withPackagePrivateOtherwise() {
                        }

                        @VisibleForTesting(otherwise = ${VisibleForTesting.PROTECTED})
                        public void withNumericProtectedOtherwise() {
                        }

                        @VisibleForTesting(otherwise = ${VisibleForTesting.PRIVATE})
                        public void withNumericPrivateOtherwise() {
                        }

                        @VisibleForTesting(otherwise = ${VisibleForTesting.PACKAGE_PRIVATE})
                        public void withNumericPackagePrivateOtherwise() {
                        }

                        @VisibleForTesting(otherwise = ${VisibleForTesting.NONE})
                        public void withNumericNoneOtherwise() {
                        }
                    }
                    """
                ),
            format = FileFormat.V4,
            api =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class ProductionCodeJava {
                    method @VisibleForTesting public void withDefaultOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.NONE) public void withNoneOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.NONE}) public void withNumericNoneOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.PACKAGE_PRIVATE}) public void withNumericPackagePrivateOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.PRIVATE}) public void withNumericPrivateOtherwise();
                    method @VisibleForTesting(otherwise=${VisibleForTesting.PROTECTED}) public void withNumericProtectedOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE) public void withPackagePrivateOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PRIVATE) public void withPrivateOtherwise();
                    method @VisibleForTesting(otherwise=androidx.annotation.VisibleForTesting.PROTECTED) public void withProtectedOtherwise();
                  }
                }
                """,
            useShowAndHideOptions = false,
        )
    }

    companion object {
        /**
         * Defines the numeric values of the symbols used in tests that use numbers instead of
         * symbols.
         */
        // TODO(b/387992791): Use a real VisibleForTesting annotation.
        private interface VisibleForTesting {
            companion object {
                const val PRIVATE = 2
                const val PACKAGE_PRIVATE = 3
                const val PROTECTED = 4
                const val NONE = 5
            }
        }
    }
}
