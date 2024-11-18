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

package com.android.tools.metalava

import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.ANDROID_TEST_API
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.testing.java
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

@Suppress("JavadocDeclaration")
class ApiSurfacesTest : DriverTest() {

    /** Encapsulate the data to check in the lambda supplied to [checkApiSurfaces], */
    private class ApiSurfacesContext(val apiSurfaces: ApiSurfaces)

    /**
     * Check the API surfaces that are configured based off the [arguments].
     *
     * @param arguments the command line arguments to supply.
     * @param checker the lambda that is invoked on [ApiSurfacesContext] and which checks its
     *   [ApiSurfacesContext.apiSurfaces] property to make sure that the [ApiSurfaces] were
     *   configured as expected.
     */
    private fun checkApiSurfaces(
        vararg arguments: String,
        checker: ApiSurfacesContext.() -> Unit,
    ) {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                            }
                        """
                    ),
                ),
            extraArguments = arguments,
            postAnalysisChecker = {
                val apiSurfaces = options.apiSurfaces
                val context = ApiSurfacesContext(apiSurfaces)
                context.checker()
            },
        )
    }

    private fun ApiSurfaces.assertBaseWasNotCreated() {
        assertNull(base, message = "base")
        assertNull(main.extends, message = "main.extends")
    }

    private fun ApiSurfaces.assertBaseWasCreated() {
        assertNotNull(base, message = "base")
        assertSame(base, main.extends, message = "main.extends")
    }

    @Test
    fun `Test generating public API does not need to track the base API surface`() {
        checkApiSurfaces {
            // The public API surface does not extend another API surface so there is no need to
            // track the base API surface.
            apiSurfaces.assertBaseWasNotCreated()
        }
    }

    /**
     * This is equivalent to the restricted API surface in AndroidX. That is effectively an
     * extension of the public (unannotated API) but unlike Android it does not just write the delta
     * to the `*restricted.txt` signature files it writes the whole API. It does that by specifying
     * `--show-unannotated` (to include the public API) alongside the `--show-annotation` (to
     * include the restricted extensions).
     */
    @Test
    fun `Test generating system + public API does not need to track the base API surface`() {
        checkApiSurfaces(
            // Do not make system a delta on top of public by including public APIs.
            ARG_SHOW_UNANNOTATED,
            // Include system APIs.
            ARG_SHOW_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            // The system API surface that includes public does not extend public so there is no
            // need to track the base API surface.
            apiSurfaces.assertBaseWasNotCreated()
        }
    }

    @Test
    fun `Test generating system API as delta on public does need to track the base API surface`() {
        checkApiSurfaces(
            // Include system API only, no ARG_SHOW_UNANNOTATED means no public API.
            ARG_SHOW_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            // The system API surface that extends public does need to track the base API surface.
            apiSurfaces.assertBaseWasCreated()
        }
    }

    @Test
    fun `Test generating test API as delta on system does need to track the base API surface`() {
        checkApiSurfaces(
            // Include test APIs only, no ARG_SHOW_UNANNOTATED means no public API.
            ARG_SHOW_ANNOTATION,
            ANDROID_TEST_API,
            // Include system APIs only for stubs which always have to be complete.
            ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            apiSurfaces.assertBaseWasCreated()
        }
    }

    @Test
    fun `Test previously released codebases`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Test {
                                public int field;
                                /** @removed */
                                public int removed;
                                public void foo(int p) {}
                                public static class Nested {
                                }
                                /** @removed */
                                public static class Removed {
                                }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleasedList =
                listOf(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Test {
                            ctor public Test();
                            field public int field;
                            method public void foo(int);
                          }
                          public static class Test.Nested {
                            ctor public Test.Nested();
                          }
                        }
                    """
                ),
            checkCompatibilityRemovedApiReleasedList =
                listOf(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Test {
                            field public int removed;
                          }
                          public static class Test.Removed {
                            ctor public Test.Removed();
                          }
                        }
                    """
                ),
        ) {
            val previouslyReleasedCodebase = options.previouslyReleasedCodebase!!

            previouslyReleasedCodebase.assertSelectedApiVariants(
                """
                    package test.pkg - ApiVariantSet[main(CR)]
                      class test.pkg.Test - ApiVariantSet[main(CR)]
                        constructor test.pkg.Test() - ApiVariantSet[main(C)]
                        method test.pkg.Test.foo(int) - ApiVariantSet[main(C)]
                        field test.pkg.Test.field - ApiVariantSet[main(C)]
                        field test.pkg.Test.removed - ApiVariantSet[main(R)]
                        class test.pkg.Test.Nested - ApiVariantSet[main(C)]
                          constructor test.pkg.Test.Nested() - ApiVariantSet[main(C)]
                        class test.pkg.Test.Removed - ApiVariantSet[main(R)]
                          constructor test.pkg.Test.Removed() - ApiVariantSet[main(R)]
                """
            )
        }
    }
}
