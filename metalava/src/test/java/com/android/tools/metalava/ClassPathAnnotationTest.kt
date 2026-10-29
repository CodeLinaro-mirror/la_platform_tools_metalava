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

package com.android.tools.metalava

import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import kotlin.test.assertTrue
import org.junit.ClassRule
import org.junit.Test

/**
 * A test to show the behavior of annotations where either the annotation class or constants it uses
 * are loaded from the class path.
 */
class ClassPathAnnotationTest : DriverTest() {
    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        private val jar =
            jarFromSources(
                    "test.jar",
                    KnownSourceFiles.systemApiSource,
                    java(
                        """
                            package test.jar;
                            import android.annotation.SystemApi;
                            public class Constants {
                                @SystemApi
                                public static final String STRING_CONSTANT = "Constant";
                            }
                        """
                    ),
                    java(
                        """
                            package test.jar;
                            import android.annotation.SystemApi;
                            @SystemApi
                            public @interface SystemAnnotation {
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)
    }

    @Test
    fun `test constant from classpath with --api-surfaces`() {
        check(
            // Public API in a hierarchy with system so system API will be hidden.
            apiSurface = KnownApiSurface.PUBLIC,
            classpath = arrayOf(jar),
            sourceFiles =
                arrayOf(
                    requiresPermissionSource,
                    java(
                        """
                            package test.pkg;
                            import android.annotation.RequiresPermission;
                            public class Test {
                                private Test() {}
                                @RequiresPermission(test.jar.Constants.STRING_CONSTANT)
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Test {
                        method @RequiresPermission("Constant") public void method();
                      }
                    }
                """,
        ) {
            codebase ?: error("No code base")
            val field =
                codebase.assertResolvedClass("test.jar.Constants").assertField("STRING_CONSTANT")
            assertTrue(field.selectedApi.itemApiVariants.isEmpty())
        }
    }

    @Test
    fun `test constant from classpath with --hide-annotation`() {
        // Replicates behavior of AndroidX RestrictTo annotation usage which does apply to items on
        // the class path.
        check(
            // Public API in a hierarchy with system so system API will be hidden.
            extraArguments =
                arrayOf(
                    *KnownApiSurface.PUBLIC.commandLineOptions.toTypedArray(),
                    // Explicitly hide the system api annotations.
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                ),
            classpath = arrayOf(jar),
            sourceFiles =
                arrayOf(
                    requiresPermissionSource,
                    java(
                        """
                            package test.pkg;
                            import android.annotation.RequiresPermission;
                            public class Test {
                                private Test() {}
                                @RequiresPermission(test.jar.Constants.STRING_CONSTANT)
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Test {
                        method @RequiresPermission("Constant") public void method();
                      }
                    }
                """,
        ) {
            codebase ?: error("No code base")
            val field =
                codebase.assertResolvedClass("test.jar.Constants").assertField("STRING_CONSTANT")
            assertTrue(field.selectedApi.itemApiVariants.isEmpty())
        }
    }

    @Test
    fun `test annotation from classpath with --api-surfaces`() {
        check(
            // Use system so system API will be used.
            apiSurface = KnownApiSurface.SYSTEM,
            classpath = arrayOf(jar),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SystemApi;
                            import test.jar.SystemAnnotation;

                            @SystemAnnotation
                            @SystemApi
                            public class Test {
                                private Test() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @test.jar.SystemAnnotation public class Test {
                      }
                    }
                """,
        )
    }
}
