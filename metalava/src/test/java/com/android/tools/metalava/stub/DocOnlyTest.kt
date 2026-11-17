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

package com.android.tools.metalava.stub

import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.testing.java
import org.junit.Test

class DocOnlyTest : AbstractStubsTest() {

    private val docOnlyClass =
        java(
            """
                package test.pkg;
                import android.annotation.DocOnly;
                @DocOnly
                public class DocOnlyClass {
                    public void method() {}
                }
            """
        )

    private val publicClass =
        java(
            """
                package test.pkg;
                public class PublicClass {
                    public void method() {}
                }
            """
        )

    @Test
    fun `DocOnly class should be omitted from normal stubs`() {
        check(
            apiSurface = KnownApiSurface.PUBLIC,
            sourceFiles =
                arrayOf(
                    docOnlyClass,
                    publicClass,
                ),
            docStubs = false,
            stubPaths =
                arrayOf(
                    "test/pkg/PublicClass.java",
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class PublicClass {
                            public PublicClass() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `DocOnly class should be included in doc stubs`() {
        check(
            apiSurface = KnownApiSurface.PUBLIC,
            sourceFiles =
                arrayOf(
                    docOnlyClass,
                    publicClass,
                ),
            docStubs = true,
            stubPaths =
                arrayOf(
                    "test/pkg/DocOnlyClass.java",
                    "test/pkg/PublicClass.java",
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class PublicClass {
                            public PublicClass() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class DocOnlyClass {
                            public DocOnlyClass() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test @doconly Javadoc tag reports error`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @doconly */
                            public class Test {}
                        """
                    ),
                ),
            expectedIssues =
                """
                    src/test/pkg/Test.java:2: error: Use of '@doconly' is no longer supported, use an <api-surfaces>/<doc-only> configured annotation [UnsupportedDocTag]
                """,
        )
    }
}
