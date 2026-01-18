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

package com.android.tools.metalava.model.testsuite.documentation

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.value.ValueExample
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Common tests for tags that handle all references. */
class CommonParameterizedDocReferenceTest : BaseModelTest() {

    /** Set of tags that handle the references. */
    enum class TestTagType(
        /**
         * The prefix for issues that are reported with the link test. Needed because each tag type
         * has a different prefix before the reference which results in a different character
         * position being reported.
         */
        internal val issuePrefix: String,
    ) {
        LINK(issuePrefix = "MAIN_SRC/src/test/pkg/Test.java:3:12: ") {
            override fun commentForReference(reference: String, linkLabel: String?) =
                "/** {@link ${referenceAndLabel(reference, linkLabel)}} */\n"
        },
        LINKPLAIN(issuePrefix = "MAIN_SRC/src/test/pkg/Test.java:3:17: ") {
            override fun commentForReference(reference: String, linkLabel: String?) =
                "/** {@linkplain ${referenceAndLabel(reference, linkLabel)}} */\n"
        },
        SEE(issuePrefix = "MAIN_SRC/src/test/pkg/Test.java:3:10: ") {
            override fun commentForReference(reference: String, linkLabel: String?) =
                "/** @see ${referenceAndLabel(reference, linkLabel)} */\n"
        };

        /** Determine whether a link label is expected. */
        private fun requiresLinkLabel(linkLabel: String?) =
            when {
                linkLabel == null -> false
                // Ignore link labels for @see references. That matches the Psi specific resolving
                // behavior.
                this == SEE -> false
                else -> true
            }

        /** Combine [reference] and the optional [linkLabel]. */
        protected fun referenceAndLabel(reference: String, linkLabel: String?) = buildString {
            append(reference)
            if (requiresLinkLabel(linkLabel)) {
                append(" ")
                append(linkLabel)
            }
        }

        /** Construct a comment containing the [reference] with the optional [linkLabel]. */
        abstract fun commentForReference(reference: String, linkLabel: String?): String

        override fun toString() = "@${name.lowercase()}"
    }

    @Parameterized.Parameter(0) lateinit var testTagType: TestTagType

    @Parameterized.Parameter(1) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        /**
         * The reference to resolve.
         *
         * Defaults to [name].
         */
        val reference: String = name,

        /** The expected resolved reference of resolving [reference]. */
        val expectedResolvedReference: String,

        /**
         * The expected label that will be added, `null` if it does not add a label.
         *
         * Defaults to [reference] with `#` replaced with `.`.
         */
        val expectedLinkLabel: String? = reference.replace('#', '.'),

        /** The expected issues that will be reported. */
        val expectedIssues: String = "",
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                // Class references
                TestParams(
                    name = "String",
                    expectedResolvedReference = "java.lang.String",
                ),
                TestParams(
                    name = "java.lang.String",
                    expectedResolvedReference = "java.lang.String",
                    expectedLinkLabel = null,
                ),

                // Package references
                TestParams(
                    name = "java.lang",
                    expectedResolvedReference = "java.lang",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "test.pkg",
                    expectedResolvedReference = "test.pkg",
                    expectedLinkLabel = null,
                ),

                // Type parameter reference
                TestParams(
                    name = "T",
                    expectedResolvedReference = "T",
                    expectedLinkLabel = null,
                ),

                // Reference a member of the current class.
                //
                // They look as though they are not changed at all. However, they are resolved to
                // their fully qualified reference and then simplified to the shortest reference
                // needed to resolve to the qualified reference without any imports. For these that
                // means they are effectively unchanged.
                TestParams(
                    name = "#field",
                    expectedResolvedReference = "#field",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#Test",
                    // TODO(b/447588621): Resolve it to a constructor, e.g. `#Test()`
                    expectedResolvedReference = "#Test",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#Test()",
                    expectedResolvedReference = "#Test()",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#Test(int)",
                    expectedResolvedReference = "#Test(int)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#Test(int p)",
                    expectedResolvedReference = "#Test(int p)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#noParamsMethod",
                    expectedResolvedReference = "#noParamsMethod",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#noParamsMethod()",
                    expectedResolvedReference = "#noParamsMethod()",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#intMethod(int)",
                    expectedResolvedReference = "#intMethod(int)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "#intMethod(int p)",
                    expectedResolvedReference = "#intMethod(int p)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "Test", // Reference self.
                    expectedResolvedReference = "test.pkg.Test",
                ),
                TestParams(
                    name = "Test.Nested",
                    expectedResolvedReference = "test.pkg.Test.Nested",
                ),

                // The # is optional when referencing members of the current class. The following
                // tests verify the behavior. Note, the result must have a leading # as that will
                // ensure consistent behavior in tools that consume generated documentation stubs
                // and may not handle a missing # correctly.
                TestParams(
                    name = "field",
                    expectedResolvedReference = "#field",
                    expectedLinkLabel = null,
                ),

                // Use invalid reference without a #. It will work but will be reported as an issue.
                TestParams(
                    name = "Other.field",
                    expectedResolvedReference = "test.pkg.Other#field",
                    expectedIssues =
                        "warning: Malformed reference `Other.field`, missing '#', should be 'Other#field (ErrorWhenNew) [MalformedDocReference]",
                ),

                // Invalid reference qualifiers
                TestParams(
                    name = "Unknown#field",
                    expectedResolvedReference = "Unknown#field",
                    expectedLinkLabel = null,
                    expectedIssues =
                        "warning: Could not resolve 'Unknown' in 'class test.pkg.Test' (ErrorWhenNew) [UnresolvedLink]",
                ),
                TestParams(
                    name = "Imported.field.other#member",
                    expectedResolvedReference = "Imported.field.other#member",
                    expectedLinkLabel = null,
                    expectedIssues =
                        "warning: Could not resolve 'Imported.field.other' as could not find a package or class called 'field' in 'class another.pkg.Imported' (ErrorWhenNew) [UnresolvedLink]",
                ),

                // Reference a member of another class in the same package.
                TestParams(
                    name = "Other#field",
                    expectedResolvedReference = "test.pkg.Other#field",
                ),
                /* TODO(b/447588621): uncomment and fix flaky behavior.
                TestParams(
                    name = "Other#Other",
                    // TODO(b/447588621): Resolve it to a constructor, e.g. `test.pkg.Other#Other()`
                    expectedResolvedReference = "test.pkg.Other#Other",
                ),
                */
                TestParams(
                    name = "Other#Other()",
                    expectedResolvedReference = "test.pkg.Other#Other()",
                ),
                TestParams(
                    name = "Other#Other(int)",
                    expectedResolvedReference = "test.pkg.Other#Other(int)",
                ),
                TestParams(
                    name = "Other#Other(int p)",
                    expectedResolvedReference = "test.pkg.Other#Other(int p)",
                ),
                TestParams(
                    name = "Other#noParamsMethod",
                    expectedResolvedReference = "test.pkg.Other#noParamsMethod",
                ),
                TestParams(
                    name = "Other#noParamsMethod()",
                    expectedResolvedReference = "test.pkg.Other#noParamsMethod()",
                ),
                TestParams(
                    name = "Other#intMethod(int)",
                    expectedResolvedReference = "test.pkg.Other#intMethod(int)",
                ),
                TestParams(
                    name = "Other#intMethod(int p)",
                    expectedResolvedReference = "test.pkg.Other#intMethod(int p)",
                ),
                TestParams(
                    name = "Other.Nested",
                    expectedResolvedReference = "test.pkg.Other.Nested",
                ),

                // Reference a member of an imported class.
                TestParams(
                    name = "Imported#field",
                    expectedResolvedReference = "another.pkg.Imported#field",
                ),
                /* TODO(b/447588621): uncomment and fix flaky behavior.
                TestParams(
                    name = "Imported#Imported",
                    // TODO(b/447588621): Resolve it to a constructor, e.g. `another.pkg.Imported#Imported()`
                    expectedResolvedReference = "another.pkg.Imported#Imported",
                ),
                */
                TestParams(
                    name = "Imported#Imported()",
                    expectedResolvedReference = "another.pkg.Imported#Imported()",
                ),
                TestParams(
                    name = "Imported#Imported(int)",
                    expectedResolvedReference = "another.pkg.Imported#Imported(int)",
                ),
                TestParams(
                    name = "Imported#Imported(int p)",
                    expectedResolvedReference = "another.pkg.Imported#Imported(int p)",
                ),
                TestParams(
                    name = "Imported#noParamsMethod",
                    expectedResolvedReference = "another.pkg.Imported#noParamsMethod",
                ),
                TestParams(
                    name = "Imported#noParamsMethod()",
                    expectedResolvedReference = "another.pkg.Imported#noParamsMethod()",
                ),
                TestParams(
                    name = "Imported#intMethod(int)",
                    expectedResolvedReference = "another.pkg.Imported#intMethod(int)",
                ),
                TestParams(
                    name = "Imported#intMethod(int p)",
                    expectedResolvedReference = "another.pkg.Imported#intMethod(int p)",
                ),
                TestParams(
                    name = "Imported.Nested",
                    expectedResolvedReference = "another.pkg.Imported.Nested",
                ),

                // Reference a member of a fully qualified class.
                TestParams(
                    name = "other.pkg.Another#field",
                    expectedResolvedReference = "other.pkg.Another#field",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#Another",
                    // TODO(b/447588621): Resolve it to a constructor, e.g.
                    // `other.pkg.Another#Another()`
                    expectedResolvedReference = "other.pkg.Another#Another",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#Another()",
                    expectedResolvedReference = "other.pkg.Another#Another()",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#Another(int)",
                    expectedResolvedReference = "other.pkg.Another#Another(int)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#Another(int p)",
                    expectedResolvedReference = "other.pkg.Another#Another(int p)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#noParamsMethod",
                    expectedResolvedReference = "other.pkg.Another#noParamsMethod",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#noParamsMethod()",
                    expectedResolvedReference = "other.pkg.Another#noParamsMethod()",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#intMethod(int)",
                    expectedResolvedReference = "other.pkg.Another#intMethod(int)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another#intMethod(int p)",
                    expectedResolvedReference = "other.pkg.Another#intMethod(int p)",
                    expectedLinkLabel = null,
                ),
                TestParams(
                    name = "other.pkg.Another.Nested",
                    expectedResolvedReference = "other.pkg.Another.Nested",
                    expectedLinkLabel = null,
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        /** Compute the cross product of [TestTagType] and [params]. */
        fun params() =
            TestTagType.entries.flatMap { testTagType ->
                params.map { p -> arrayOf(testTagType, p) }
            }
    }

    @Test
    fun `Documentation text`() {
        val comment = testTagType.commentForReference(params.reference, null)
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import another.pkg.Imported;
                        ${comment}
                        public class Test<T> {
                            public int field;
                            public Test() {}
                            public Test(int p) {}
                            public void noParamsMethod() {}
                            public void intMethod(int p) {}

                            public class Nested {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Other {
                            public int field;
                            public Other() {}
                            public Other(int p) {}
                            public void noParamsMethod() {}
                            public void intMethod(int p) {}

                            public class Nested {}
                        }
                    """
                ),
                java(
                    """
                        package other.pkg;
                        public class Another {
                            public int field;
                            public Another() {}
                            public Another(int p) {}
                            public void noParamsMethod() {}
                            public void intMethod(int p) {}

                            public class Nested {}
                        }
                    """
                ),
                java(
                    """
                        package another.pkg;
                        public class Imported {
                            public int field;
                            public Imported() {}
                            public Imported(int p) {}
                            public void noParamsMethod() {}
                            public void intMethod(int p) {}

                            public class Nested {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val expectedComment =
                testTagType.commentForReference(
                    params.expectedResolvedReference,
                    params.expectedLinkLabel
                )
            testClass.assertPrintedDocumentation(expectedComment)

            // Verify that the reported issues, if any, are expected.
            // First, remove the tag type specific prefixes.
            val reportedIssues = removeReportedIssues().replace(testTagType.issuePrefix, "")
            // Then, check the expected issues.
            assertEquals(params.expectedIssues, reportedIssues)
        }
    }
}
