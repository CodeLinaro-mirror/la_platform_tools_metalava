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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.Assertions.Companion.assertClass
import com.android.tools.metalava.model.Assertions.Companion.assertField
import com.android.tools.metalava.model.Assertions.Companion.assertMethod
import com.android.tools.metalava.model.Assertions.Companion.assertResolvedClass
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.value.annotationValue
import com.android.tools.metalava.model.testing.value.arrayValue
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.testing.value.primitiveValueForKind
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.model.value.LegacyValueFormatter
import com.android.tools.metalava.model.value.LegacyValueFormatter.Settings
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueStringConfiguration
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import kotlin.test.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.runners.Parameterized

/** Tests for [LegacyValueFormatter] that will run it across jars and sources. */
class CommonLegacyValueFormatterTest : BaseModelTest() {

    /** The [Codebase] producer kind. */
    @Parameterized.Parameter(0) lateinit var producerKind: ProducerKind

    companion object {
        /** Run the tests for each [ProducerKind]. */
        @JvmStatic @Parameterized.Parameters fun params() = ProducerKind.entries

        /** Filter the parameters. */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            producerKind: ProducerKind,
        ): Boolean {
            val inputFormat = config.inputFormat

            // Supports all input formats but only Java can produce jars.
            return (inputFormat == InputFormat.JAVA || producerKind != ProducerKind.JAR)
        }

        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /**
         * Java file used in [checkFormatting] to provide context for [LegacyValueFormatter.format].
         */
        private val javaFile =
            java(
                    """
                        package test.pkg;
                        public interface Foo {
                            void method();
                            int FIELD = 1;
                        }
                    """
                )
                .cacheIn(testFileCacheRule)

        /**
         * Fake Java file used in [checkFormatting] when [producerKind] is [ProducerKind.JAR] just
         * to force the [Codebase] to be created so the classes from the jar file can be accessed.
         */
        private val fakeJavaFile =
            java(
                    """
                        package fake;
                        public class Fake {}
                    """
                )
                .cacheIn(testFileCacheRule)

        /**
         * Kotlin file used in [checkFormatting] to provide context for
         * [LegacyValueFormatter.format].
         */
        val kotlinFile =
            kotlin(
                    """
                        package test.pkg
                        interface Foo {
                            fun method()

                            companion object {
                                @JvmField
                                val FIELD = 1
                            }
                        }
                    """
                )
                .cacheIn(testFileCacheRule)

        /**
         * Signature file used in [checkFormatting] to provide context for
         * [LegacyValueFormatter.format].
         */
        private val signatureFile =
            signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Foo {
                            method public void method();
                            field public static final int FIELD = 1;
                          }
                        }
                    """
                )
                .cacheIn(testFileCacheRule)

        /**
         * Jar file used in [checkFormatting] when [producerKind] is [ProducerKind.JAR] to provide
         * context for [LegacyValueFormatter.format].
         */
        private val jarFile = jarFromSources("test.jar", javaFile).cacheIn(testFileCacheRule)

        /** Shared value for use in the tests. */
        val DOUBLE_NAN = Value.createLiteralValue(null, Double.NaN)
    }

    /** Provides access to the information needed when formatting. */
    private class FormattingContext(
        delegate: CodebaseContext,
        producerKind: ProducerKind,
    ) : CodebaseContext by delegate {
        /** The [ClassItem] that will provide the context. */
        private val classItem =
            // Classes loaded from the jar file have to be resolved, while classes loaded from
            // sources do not.
            if (producerKind == ProducerKind.JAR) codebase.assertResolvedClass("test.pkg.Foo")
            else codebase.assertClass("test.pkg.Foo")

        /** A field that can used for the context in [LegacyValueFormatter.format]. */
        val field
            get() = classItem.assertField("FIELD")

        /** A method that can used for the context in [LegacyValueFormatter.format]. */
        val method
            get() = classItem.assertMethod("method", "")
    }

    /**
     * Check the formatting.
     *
     * Runs [body] on a [FormattingContext] that abstracts away the
     */
    private fun checkFormatting(body: FormattingContext.() -> Unit) {
        val additionalClassPath =
            when (producerKind) {
                ProducerKind.JAR -> listOf(jarFile.createFile(temporaryFolder.root))
                else -> emptyList()
            }

        val testFixture = TestFixture(additionalClassPath = additionalClassPath)

        val testFile =
            when (inputFormat) {
                InputFormat.SIGNATURE -> signatureFile
                InputFormat.JAVA -> if (producerKind == ProducerKind.JAR) fakeJavaFile else javaFile
                InputFormat.KOTLIN -> kotlinFile
            }

        runCodebaseTest(
            testFile,
            testFixture = testFixture,
        ) {
            FormattingContext(this, producerKind).body()
        }
    }

    @Test
    fun `Test replacement values - replaces`() {
        checkFormatting {
            val settings =
                Settings(
                    stringReplacement =
                        mapOf(
                            DOUBLE_NAN to "NOT_A_NUMBER",
                        )
                )
            val formatter = LegacyValueFormatter(settings)
            val actual = formatter.format(DOUBLE_NAN, field)
            assertEquals("NOT_A_NUMBER", actual)
        }
    }

    @Test
    fun `Test replacement values in array - replaces`() {
        checkFormatting {
            val settings =
                Settings(
                    stringReplacement =
                        mapOf(
                            DOUBLE_NAN to "NOT_A_NUMBER",
                        )
                )
            val formatter = LegacyValueFormatter(settings)
            val actual = formatter.format(arrayValue(DOUBLE_NAN), field)
            assertEquals("{NOT_A_NUMBER}", actual)
        }
    }

    @Test
    fun `Test replacement values in annotation - replaces`() {
        checkFormatting {
            val settings =
                Settings(
                    stringReplacement =
                        mapOf(
                            DOUBLE_NAN to "NOT_A_NUMBER",
                        )
                )
            val formatter = LegacyValueFormatter(settings)
            val actual =
                formatter.format(
                    annotationValue(
                        "test.pkg.Anno",
                        "other" to DOUBLE_NAN,
                    ),
                    field
                )
            assertEquals("@test.pkg.Anno(other = NOT_A_NUMBER)", actual)
        }
    }

    @Test
    fun `Test replacement values - does not replace`() {
        checkFormatting {
            val settings =
                Settings(
                    stringReplacement =
                        mapOf(
                            DOUBLE_NAN to "NOT_A_NUMBER",
                        )
                )
            val formatter = LegacyValueFormatter(settings)
            val actual = formatter.format(Value.createLiteralValue(null, 3.0), method)
            assertEquals("3.0", actual)
        }
    }

    @Test
    fun `Test jar specific settings`() {
        checkFormatting {
            val sourceSettings = Settings(stringReplacement = mapOf(DOUBLE_NAN to "Source"))
            val jarSettings = Settings(stringReplacement = mapOf(DOUBLE_NAN to "Jar"))
            val formatter =
                LegacyValueFormatter(
                    javaSettings = sourceSettings,
                    jarSettings = jarSettings,
                )
            val actual = formatter.format(DOUBLE_NAN, method)
            val expected = if (producerKind == ProducerKind.JAR) "Jar" else "Source"
            assertEquals(expected, actual)
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test kotlin specific settings`() {
        checkFormatting {
            val javaSettings = Settings(stringReplacement = mapOf(DOUBLE_NAN to "Java"))
            val kotlinSettings = Settings(stringReplacement = mapOf(DOUBLE_NAN to "Kotlin"))

            val formatter =
                LegacyValueFormatter(
                    javaSettings = javaSettings,
                    kotlinSettings = kotlinSettings,
                )
            val actual = formatter.format(DOUBLE_NAN, method)
            val expected = if (inputFormat == InputFormat.JAVA) "Java" else "Kotlin"
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `Test char - useDoubleQuotesForChar = false`() {
        checkFormatting {
            val javaSettings = Settings(useDoubleQuotesForChar = false)
            val formatter = LegacyValueFormatter(javaSettings = javaSettings)
            val actual = formatter.format(literalValue('a'), method)
            assertEquals("'a'", actual)
        }
    }

    @Test
    fun `Test char - useDoubleQuotesForChar = true`() {
        checkFormatting {
            val javaSettings = Settings(useDoubleQuotesForChar = true)
            val formatter = LegacyValueFormatter(javaSettings = javaSettings)
            val actual = formatter.format(literalValue('a'), method)
            assertEquals("\"a\"", actual)
        }
    }

    @Test
    fun `Test char - dropLongAndFloatTypeSuffix = false`() {
        checkFormatting {
            val javaSettings =
                Settings(
                    valueStringConfiguration =
                        ValueStringConfiguration(
                            treatAsIntIfOriginallySpecifiedAsInt = true,
                        ),
                    dropLongAndFloatTypeSuffix = false,
                )
            val formatter = LegacyValueFormatter(javaSettings = javaSettings)
            fun assertFormattedValue(expected: String, value: Value) {
                assertEquals(expected, formatter.format(value, method), message = value.toString())
            }
            assertFormattedValue("10L", literalValue(10L))
            assertFormattedValue("10", primitiveValueForKind(Primitive.LONG, 10))
            assertFormattedValue("2.3f", literalValue(2.3f))
            assertFormattedValue("10", primitiveValueForKind(Primitive.FLOAT, 10))
        }
    }

    @Test
    fun `Test char - dropLongAndFloatTypeSuffix = true`() {
        checkFormatting {
            val javaSettings =
                Settings(
                    valueStringConfiguration =
                        ValueStringConfiguration(
                            treatAsIntIfOriginallySpecifiedAsInt = true,
                        ),
                    dropLongAndFloatTypeSuffix = true,
                )
            val formatter = LegacyValueFormatter(javaSettings = javaSettings)
            fun assertFormattedValue(expected: String, value: Value) {
                assertEquals(expected, formatter.format(value, method), message = value.toString())
            }
            assertFormattedValue("10", literalValue(10L))
            assertFormattedValue("10", primitiveValueForKind(Primitive.LONG, 10))
            assertFormattedValue("2.3", literalValue(2.3f))
            assertFormattedValue("10", primitiveValueForKind(Primitive.FLOAT, 10))
        }
    }
}
