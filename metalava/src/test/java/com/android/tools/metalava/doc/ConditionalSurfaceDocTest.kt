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

package com.android.tools.metalava.doc

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.xml
import org.junit.Test

/** End-to-end tests for `@if (surface("name"))` across configured API surfaces. */
class ConditionalSurfaceDocTest : DriverTest() {
    companion object {
        private val HIERARCHY_SOURCES =
            arrayOf(
                java(
                    """
                        package test.pkg;

                        /**
                         * {@if (surface("public")) {Public. } else {NoPublic. }}{@if (surface("system")) {System. } else {NoSystem. }}{@if (surface("module-lib")) {ModuleLib. } else {NoModuleLib. }}{@if (surface("test")) {Test.} else {NoTest.}}
                         *
                         * @if (surface("system")) {
                         *     :@see SystemHelper
                         * }
                         */
                        public class Foo {
                        }
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        import android.annotation.Hide;
                        import android.annotation.SystemApi;

                        @Hide
                        @SystemApi
                        public class SystemHelper {
                        }
                    """,
                ),
            )
    }

    private fun checkSurfaceDoc(
        apiSurface: KnownApiSurface,
        expectedFooComment: String,
        expectedIssues: String = "",
        includeSystemHelperStub: Boolean = false,
    ) {
        val expectedStubs = buildList {
            add(
                java(
                    """
                        package test.pkg;
                        ${expectedFooComment.trimIndent().replace("\n", "\n                        ")}
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo {
                        public Foo() { throw new RuntimeException("Stub!"); }
                        }
                    """,
                ),
            )
            if (includeSystemHelperStub) {
                add(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class SystemHelper {
                            public SystemHelper() { throw new RuntimeException("Stub!"); }
                            }
                        """,
                    ),
                )
            }
        }
        check(
            apiSurface = apiSurface,
            extraArguments = errorIssues(Issues.HIDDEN_DOC_REFERENCE),
            sourceFiles = HIERARCHY_SOURCES,
            docStubs = true,
            expectedIssues = expectedIssues,
            expectedStubFiles = expectedStubs.toTypedArray(),
        )
    }

    @Test
    fun `Test surface() when generating public`() {
        checkSurfaceDoc(
            apiSurface = KnownApiSurface.PUBLIC,
            expectedFooComment =
                """
                    /** Public. NoSystem. NoModuleLib. NoTest. */
                """,
        )
    }

    @Test
    fun `Test surface() when generating system`() {
        checkSurfaceDoc(
            apiSurface = KnownApiSurface.SYSTEM,
            includeSystemHelperStub = true,
            expectedFooComment =
                """
                    /**
                     * Public. System. NoModuleLib. NoTest.
                     * @see test.pkg.SystemHelper SystemHelper
                     */
                """,
        )
    }

    @Test
    fun `Test surface() when generating module-lib`() {
        checkSurfaceDoc(
            apiSurface = KnownApiSurface.MODULE_LIB,
            includeSystemHelperStub = true,
            expectedFooComment =
                """
                    /**
                     * Public. System. ModuleLib. NoTest.
                     * @see test.pkg.SystemHelper SystemHelper
                     */
                """,
        )
    }

    @Test
    fun `Test surface() when generating test`() {
        checkSurfaceDoc(
            apiSurface = KnownApiSurface.TEST,
            includeSystemHelperStub = true,
            expectedFooComment =
                """
                    /**
                     * Public. System. NoModuleLib. Test.
                     * @see test.pkg.SystemHelper SystemHelper
                     */
                """,
        )
    }

    @Test
    fun `Test surface() when generating standalone surface extending public`() {
        val standaloneSurface =
            KnownApiSurface(
                "restricted",
                xml(
                    "config-standalone.xml",
                    """
                        <config xmlns="http://www.google.com/tools/metalava/config">
                            <api-surfaces>
                                <api-surface name="public">
                                    <selection-criteria unannotated="show"/>
                                </api-surface>
                                <api-surface name="restricted" extends="public" contents="standalone">
                                    <selection-criteria>
                                        <annotation-rule pattern="test.pkg.RestrictedApi"/>
                                    </selection-criteria>
                                </api-surface>
                            </api-surfaces>
                        </config>
                    """,
                ),
            )

        check(
            apiSurface = standaloneSurface,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /**
                             * {@if (surface("public")) {Public. } else {NoPublic. }}{@if (surface("restricted")) {Restricted. } else {NoRestricted. }}{@if (surface("other")) {Other.} else {NoOther.}}
                             */
                            public class Foo {
                            }
                        """,
                    ),
                ),
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** Public. Restricted. NoOther. */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            }
                        """,
                    ),
                ),
        )
    }
}
