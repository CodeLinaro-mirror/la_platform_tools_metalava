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

package com.android.tools.metalava

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.xml
import org.junit.Test

/** Test different show annotations. */
class ShowabilityTest : DriverTest() {

    companion object {
        /**
         * An annotation that will hide the annotated item and all its contents unless they are
         * themselves annotated with a show annotation.
         */
        private val recursiveHide =
            java(
                """
                    package test.annotation;

                    public @interface RecursiveHide {}
                """
            )

        /** An annotation that will show the annotated item but does not affect its contents. */
        private val nonRecursiveShow =
            java(
                """
                    package test.annotation;

                    public @interface NonRecursiveShow {}
                """
            )

        private val NON_RECURSIVE_SHOW =
            KnownApiSurface(
                "non-recursive",
                xml(
                    "non-recursive.xml",
                    """
                        <config xmlns="http://www.google.com/tools/metalava/config"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                            <api-surfaces>
                                <api-surface name="non-recursive">
                                    <selection-criteria>
                                        <annotation-rule pattern="test.annotation.RecursiveHide" effect="hide"/>
                                        <annotation-rule pattern="test.annotation.NonRecursiveShow" recursive="false"/>
                                    </selection-criteria>
                                </api-surface>
                            </api-surfaces>
                        </config>
                    """
                ),
            )
    }

    @Test
    fun `Recursive hide and non-recursive show - show first`() {
        check(
            apiSurface = NON_RECURSIVE_SHOW,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.NonRecursiveShow
                            @test.annotation.RecursiveHide
                            public class Foo {
                                public void foo() {}

                                @test.annotation.NonRecursiveShow
                                public void bar() {}
                            }
                        """
                    ),
                    nonRecursiveShow,
                    recursiveHide,
                ),
            format = FileFormat.V2,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void bar();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Recursive hide and non-recursive show - hide first`() {
        check(
            apiSurface = NON_RECURSIVE_SHOW,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.RecursiveHide
                            @test.annotation.NonRecursiveShow
                            public class Foo {
                                public void foo() {}

                                @test.annotation.NonRecursiveShow
                                public void bar() {}
                            }
                        """
                    ),
                    nonRecursiveShow,
                    recursiveHide,
                ),
            format = FileFormat.V2,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void bar();
                      }
                    }
                """,
        )
    }

    @Test
    @RequiresCapabilities(Capability.KOTLIN)
    fun `Type alias with show annotation in hidden package`() {
        check(
            apiSurface = NON_RECURSIVE_SHOW,
            sourceFiles =
                arrayOf(
                    java(
                        "test/pkg/package-info.java",
                        """
                        @RecursiveHide
                        package test.pkg;
                        import test.annotation.RecursiveHide;
                        """,
                    ),
                    kotlin(
                        """
                        package test.pkg
                        import test.annotation.NonRecursiveShow
                        @NonRecursiveShow
                        typealias Foo = String
                        """
                    ),
                    nonRecursiveShow,
                    recursiveHide,
                ),
            expectedApiSignature =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                }
                """,
        )
    }
}
