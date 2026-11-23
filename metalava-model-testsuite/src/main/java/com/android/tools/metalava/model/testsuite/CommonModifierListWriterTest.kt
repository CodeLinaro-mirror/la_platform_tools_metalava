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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.AnnotationFormatter
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [ModifierListWriter]. */
@SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
class CommonModifierListWriterTest : BaseModelTest() {

    companion object {
        private val defaultConfig =
            ModifierListWriter.Config(
                target = AnnotationTarget.SIGNATURE_FILE,
                annotationFormatter = AnnotationFormatter.normalizingFormatter(),
                runtimeAnnotationsOnly = false,
                skipNullnessAnnotations = true,
                normalizeFinal = false,
                normalizeAbstract = false,
            )

        private val javaSealedClassesDisabledConfig = defaultConfig

        private val javaSealedClassesEnabledConfig =
            defaultConfig.copy(
                javaSealedClasses = true,
            )
    }

    private fun Item.writeKeywords(config: ModifierListWriter.Config = defaultConfig): String {
        val stringWriter = StringWriter()
        val writer =
            ModifierListWriter(
                writer = stringWriter,
                config = config,
            )
        writer.writeKeywords(this)
        return stringWriter.toString().trimEnd()
    }

    @Test
    fun `modifiers public`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        method public void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}

                        public void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers public final method in open class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        method public final void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}

                        public final void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public final", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers public explicitly final method in final class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public final void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public final class Test {
                        private Test() {}

                        public final void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers public explicitly final method in final class - normalized`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public final void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public final class Test {
                        private Test() {}

                        public final void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals(
                "public",
                methodItem.writeKeywords(
                    defaultConfig.copy(
                        normalizeFinal = true,
                    )
                )
            )
        }
    }

    @Test
    fun `modifiers public implicitly final method in final class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public final class Test {
                        private Test() {}

                        public void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers record class - javaRecordClasses=false`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public record Test() {
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "16", // required for records
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            assertEquals("public final", testClass.writeKeywords())
        }
    }

    @Test
    fun `modifiers record class - javaRecordClasses=true`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public record Test() {
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "16", // required for records
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            assertEquals(
                "public",
                testClass.writeKeywords(
                    defaultConfig.copy(
                        javaRecordClasses = true,
                    )
                )
            )
        }
    }

    /** Check handling of enum method with `abstract` keyword. */
    private fun checkAbstractEnumMethod(normalizeAbstract: Boolean, expectedKeywords: String) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public enum Test {
                        VALUE {
                           public void method() {}
                        },
                        ;

                        public abstract void method();
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals(
                expectedKeywords,
                methodItem.writeKeywords(
                    defaultConfig.copy(
                        normalizeAbstract = normalizeAbstract,
                    )
                )
            )
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test abstract modifier on enum class method - not normalized`() {
        checkAbstractEnumMethod(
            normalizeAbstract = false,
            expectedKeywords = "public abstract",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test abstract modifier on enum class method - normalized`() {
        checkAbstractEnumMethod(
            normalizeAbstract = true,
            expectedKeywords = "public",
        )
    }

    /** Check handling of annotation method with `abstract` keyword. */
    private fun checkAbstractAnnotationMethod(
        normalizeAbstract: Boolean,
        expectedKeywords: String
    ) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public @interface Test {
                        abstract void method();
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals(
                expectedKeywords,
                methodItem.writeKeywords(
                    defaultConfig.copy(
                        normalizeAbstract = normalizeAbstract,
                    )
                )
            )
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test abstract modifier on annotation class method - not normalized`() {
        checkAbstractAnnotationMethod(
            normalizeAbstract = false,
            expectedKeywords = "public abstract",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test abstract modifier on annotation class method - normalized`() {
        checkAbstractAnnotationMethod(
            normalizeAbstract = true,
            expectedKeywords = "public",
        )
    }

    private fun checkJavaSealedKeywords(
        className: String,
        config: ModifierListWriter.Config,
        expectedKeywords: String,
        expectedIssues: String = "",
    ) {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public sealed interface Sealed {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public non-sealed interface Subclass extends Sealed {}
                    """
                ),
                java(
                    """
                        package test.pkg;

                        non-sealed interface PackagePrivate extends Sealed {}
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17", // required for sealed classes
                ),
        ) {
            val testClass = codebase.assertClass(className)

            assertEquals(expectedKeywords, testClass.writeKeywords(config))

            assertAndRemoveReportedIssues(expectedIssues)
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test sealed modifier - java class - javaSealedClasses=false`() {
        checkJavaSealedKeywords(
            className = "test.pkg.Sealed",
            config = javaSealedClassesDisabledConfig,
            expectedKeywords = "public",
            expectedIssues =
                "MAIN_SRC/src/test/pkg/Sealed.java:3: error: `sealed` is not currently supported, see b/482391240 for more details. [AddedSealed]",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test sealed modifier - java class - javaSealedClasses=true`() {
        checkJavaSealedKeywords(
            className = "test.pkg.Sealed",
            config = javaSealedClassesEnabledConfig,
            expectedKeywords = "public sealed non-exhaustive",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test non-sealed modifier - java class - javaSealedClasses=false`() {
        checkJavaSealedKeywords(
            className = "test.pkg.Subclass",
            config = javaSealedClassesDisabledConfig,
            expectedKeywords = "public",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test non-sealed modifier - java class - javaSealedClasses=true`() {
        checkJavaSealedKeywords(
            className = "test.pkg.Subclass",
            config = javaSealedClassesEnabledConfig,
            expectedKeywords = "public non-sealed",
        )
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test sealed modifier - kotlin class`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    sealed interface Test {
                        private class InnerClass : Test
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            assertEquals("public sealed nonexhaustive", testClass.writeKeywords())
        }
    }
}
