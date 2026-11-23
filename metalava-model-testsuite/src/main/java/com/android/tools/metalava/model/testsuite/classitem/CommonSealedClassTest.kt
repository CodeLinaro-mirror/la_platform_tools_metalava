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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CommonSealedClassTest : BaseModelTest() {
    @Test
    fun `sealed class - basic`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed class SealedClass {
                      }
                      public static final class SealedClass.SubclassA extends test.pkg.SealedClass {
                      }
                      public static final class SealedClass.SubclassB extends test.pkg.SealedClass {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public sealed class SealedClass {
                        private SealedClass() {}
                        public static final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                        public static final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    }
               """
            ),
            kotlin(
                """
                    package test.pkg

                    sealed class SealedClass private constructor() {
                        class SubclassA private constructor() : SealedClass() {}
                        class SubclassB private constructor() : SealedClass() {}
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertTrue(testClass.modifiers.isSealed())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `sealed class - non-sealed - not kotlin`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed class SealedClass {
                      }
                      public static final class SealedClass.SubclassA extends test.pkg.SealedClass {
                      }
                      public static non-sealed class SealedClass.SubclassB extends test.pkg.SealedClass {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public sealed class SealedClass {
                        private SealedClass() {}
                        public static final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                        public static non-sealed class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    }
               """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertTrue(testClass.modifiers.isSealed())

            val subclassB = codebase.assertClass("test.pkg.SealedClass.SubclassB")
            assertTrue(subclassB.modifiers.isNonSealed())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `Test update permits`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Test
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            // Make sure that it is empty to begin with.
            assertEquals(emptyList(), testClass.permitTypes, message = "before setting")

            // Set it to a non-empty list and make sure it works.
            val skeletonClassItem = testClass as SkeletonClassItem
            skeletonClassItem.permitTypes = listOf(classTypeItem("test.pkg.Subclass"))
            assertEquals(
                listOf(classTypeItem("test.pkg.Subclass")),
                testClass.permitTypes,
                message = "after setting"
            )

            // Freeze the class and make sure permit types cannot be set.
            skeletonClassItem.freeze()
            val exception =
                assertThrows(IllegalStateException::class.java) {
                    skeletonClassItem.permitTypes = emptyList()
                }
            assertEquals(
                "Cannot modify frozen class test.pkg.Test",
                exception.message,
                message = "exception message"
            )
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `sealed class - explicit permits - not kotlin`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed class SealedClass permits test.pkg.SubclassB test.pkg.SubclassA {
                          }
                          public static final class SubclassA extends test.pkg.SealedClass {
                          }
                          public static final class SubclassB extends test.pkg.SealedClass {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass permits SubclassB, SubclassA {
                            private SealedClass() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            val permits = testClass.permitTypes
            assertEquals(
                listOf(classTypeItem("test.pkg.SubclassA"), classTypeItem("test.pkg.SubclassB")),
                permits
            )
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `sealed class - implicit permits`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public sealed class SealedClass {
                          }
                          public final class SubclassA extends test.pkg.SealedClass {
                          }
                          public final class SubclassB extends test.pkg.SealedClass {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {
                            private SealedClass() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass private constructor()
                        class SubclassA private constructor(): SealedClass
                        class SubclassB private constructor(): SealedClass
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            val permits = testClass.permitTypes
            assertEquals(
                listOf(classTypeItem("test.pkg.SubclassA"), classTypeItem("test.pkg.SubclassB")),
                permits
            )
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `sealed class - nested subclasses - implicit permits`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public sealed class SealedClass {
                      }
                      public static final class SealedClass.SubclassA extends test.pkg.SealedClass {
                      }
                      public static final class SealedClass.SubclassB extends test.pkg.SealedClass {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public sealed class SealedClass {
                        private SealedClass() {}

                        public static final class SubclassA extends SealedClass {
                            private SubclassA() {}
                        }

                        public static final class SubclassB extends SealedClass {
                            private SubclassB() {}
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    sealed class SealedClass private constructor() {
                        class SubclassA private constructor(): SealedClass
                        class SubclassB private constructor(): SealedClass
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            val permits = testClass.permitTypes
            val sealedClassType = classTypeItem("test.pkg.SealedClass")
            assertEquals(
                listOf(
                    classTypeItem(
                        "test.pkg.SealedClass.SubclassA",
                        outerClassType = sealedClassType,
                    ),
                    classTypeItem(
                        "test.pkg.SealedClass.SubclassB",
                        outerClassType = sealedClassType,
                    ),
                ),
                permits
            )
        }
    }

    /**
     * This test is to make sure that older signature files without any exhaustivity modifiers are
     * read as non-exhaustive.
     */
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `modifiers show nonexhaustive when no exhaustivity modifier is present in signature`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed class SealedClass {
                          }
                          public sealed interface SealedInterface {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {}
                        final class PrivateChildClass extends SealedClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public sealed interface SealedInterface {}
                        final class PrivateInterfaceImplementor implements SealedInterface {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass {}
                        private class PrivateChildClass : SealedClass()
                        sealed interface SealedInterface {}
                        private class PrivateInterfaceImplementor : SealedInterface
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertFalse(testClass.modifiers.isExhaustive())

            val testInterface = codebase.assertClass("test.pkg.SealedInterface")
            assertFalse(testInterface.modifiers.isExhaustive())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `modifiers show exhaustive when class or interface is marked as exhaustive in signature`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed exhaustive class SealedClass {
                          }
                          public sealed exhaustive interface SealedInterface {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public sealed interface SealedInterface {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass {}
                        sealed interface SealedInterface {}
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertTrue(testClass.modifiers.isExhaustive())

            val testInterface = codebase.assertClass("test.pkg.SealedInterface")
            assertTrue(testInterface.modifiers.isExhaustive())
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `modifiers show nonexhaustive when class or interface is marked as nonexhaustive in signature`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public abstract sealed nonexhaustive class SealedClass {
                          }
                          public sealed nonexhaustive interface SealedInterface {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {}
                        final class PrivateChildClass extends SealedClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public sealed interface SealedInterface {}
                        final class PrivateInterfaceImplementor implements SealedInterface {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        sealed class SealedClass {}
                        private class PrivateChildClass : SealedClass()

                        sealed interface SealedInterface
                        private class PrivateInterfaceImplementor : SealedInterface
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertFalse(testClass.modifiers.isExhaustive())
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `sealed concrete class - explicit public constructor`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {
                            public SealedClass(int i) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {super(1);}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
            // This does not run in Kotlin as there is no way to have a sealed concrete class in
            // Kotlin as Kotlin treats all sealed classes as abstract.
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            testClass.assertConstructor(listOf("int")).also { constructor ->
                assertEquals(
                    VisibilityLevel.PUBLIC,
                    constructor.modifiers.getVisibilityLevel(),
                )
            }
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `sealed concrete class - explicit protected constructor`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed class SealedClass {
                            protected SealedClass(int i) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {super(1);}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
            // This does not run in Kotlin as there is no way to have a sealed concrete class in
            // Kotlin as Kotlin treats all sealed classes as abstract.
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            // In Kotlin a sealed class is always abstract which means it cannot be instantiated
            // directly so the visibility of the constructor is irrelevant. They are treated as
            // being PRIVATE preventing them from being tracked in the signature file.
            val expectedVisibility =
                if (inputFormat == InputFormat.KOTLIN) VisibilityLevel.PRIVATE
                else VisibilityLevel.PROTECTED

            testClass.assertConstructor(listOf("int")).also { constructor ->
                assertEquals(
                    expectedVisibility,
                    constructor.modifiers.getVisibilityLevel(),
                )
            }
        }
    }

    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `sealed abstract class - explicit public constructor`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public abstract sealed class SealedClass {
                            public SealedClass(int i) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {super(1);}
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        sealed class SealedClass(a: Int)
                        class SubclassA : SealedClass(1)
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            testClass.assertConstructor(listOf("int")).also { constructor ->
                assertEquals(
                    VisibilityLevel.PRIVATE,
                    constructor.modifiers.getVisibilityLevel(),
                )
            }
        }
    }

    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `sealed abstract class - explicit protected constructor`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public abstract sealed class SealedClass {
                            protected SealedClass(int i) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class SubclassA extends SealedClass {
                            private SubclassA() {super(1);}
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        sealed class SealedClass protected constructor(a: Int)
                        class SubclassA : SealedClass(1)
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")

            testClass.assertConstructor(listOf("int")).also { constructor ->
                assertEquals(
                    VisibilityLevel.PRIVATE,
                    constructor.modifiers.getVisibilityLevel(),
                )
            }
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `class with published internal constructor`() {
        val apiSurfaces = ApiSurfaces.create()
        val rulesByName =
            mapOf(
                "main" to
                    listOf(
                        SurfaceSelectionRule.unannotated,
                        SurfaceSelectionRule.createAnnotationRule("kotlin.PublishedApi"),
                    )
            )
        val apiSurfaceRules = ApiSurfaceRules(apiSurfaces, rulesByName)

        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg

                    open class Foo @PublishedApi internal constructor()
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuSA3w9Y9yFLEtl7OZxx9y" +
                        "IWvhlqt6s1Zwz5cSFpcSznb6JJQze1vi4awGTh6bZ5JpCno/df81r/OJkXh0" +
                        "ST51jqPPrJLid7bp5+b+/vil5j/DA4nbLU2Ct1yuN+eFeF+W6Qh485mzX2jd" +
                        "rov9YfFbfPnkDtpP9ppV3LR9822FnCvsKz2XdNX1RXdesb24c8e05awpD9tP" +
                        "KCik+PhnHfL6e59tecul6KdTDfYc5t0uJe1nYy1Q/MCSoZFNb+PnDdIvNzHP" +
                        "LuXjjE+/7HTilE7K0ZsmKYzcZqc3vxS0XpDvzWh779fMBlHbdDul7/7nvt6X" +
                        "l7C+mHpLsOXqrpBHvytuCC223zM1InXtvG2Vlx4ov/zhtOaflLZyu1KY8Mql" +
                        "U/WmePGaG4WWrf1QbiW31dXXKjs1MfdP+hFxg+q+o27vrKbVb9CZVaywu7x9" +
                        "6f688hDdX8UXD7t3t4YzOz3J/bOl4uzZvfOfRxyc1qseVtoen3eoe6NQsqmb" +
                        "4FKJ7Ij+j3zSvhOOqd0zn3jxRPYU001S/7hB0VhvvWdCHSMDAxsTvmiUBkYj" +
                        "PDnlJmbm6WXnl+Rk5sXn5qeU5qTC4zM5ISEhDYiTGi4kLDiy4CgDOKmEy1de" +
                        "EQKaIgFOKoxMIgwIW5CTESjRogKCSRjdOGTvgNIeAnQAMZ6UiG4QspulUQzy" +
                        "Z2IgKiwCvFnZQOqZgfAVkN7MBOIBAJR8IpyzAwAA"
                ),
            testFixture = TestFixture(apiSurfaceRules = apiSurfaceRules),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val constructor = fooClass.constructors().single()

            // The constructor is exposed in the main API surface because it is annotated with
            // @PublishedApi which is configured as a show annotation.
            assertTrue(constructor.selectedApi.itemApiVariants.isNotEmpty())

            // Because the constructor is exposed in the API, the class is not effectively sealed.
            assertFalse(fooClass.isEffectivelySealed())
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `sealed abstract class constructor with value class parameter`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    sealed class SealedClass(val iv: IntValue)
                    """
                )
            )
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            testClass.assertConstructor(listOf("test.pkg.IntValue")).also { constructor ->
                assertEquals(constructor.targetLanguages, TargetLanguageSet.KOTLIN_ONLY)
                assertEquals(
                    VisibilityLevel.PRIVATE,
                    constructor.modifiers.getVisibilityLevel(),
                )
            }
        }
    }
}
