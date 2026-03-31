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

package com.android.tools.metalava.model.testsuite.jar

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.ClassRule
import org.junit.Test

class CommonJarSupportTest : BaseModelTest() {
    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        private val testJar =
            jarFromSources(
                    "doubly-nested-test.jar",
                    java(
                        """
                            package test.pkg;
                            public class Test {
                                private Test() {}
                                public class Nested {
                                    private Nested() {}
                                    public class DoublyNested {
                                        private DoublyNested() {}
                                    }
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public @interface Anno {
                                int attribute();
                            }
                        """
                    )
                )
                .cacheIn(testFileCacheRule)
    }

    @RequiresCapabilities(Capability.CLASS_PATH_RESOLVER)
    @Test
    fun `Test classpath resolve - no java lang`() {
        runJarSupportTest {
            val resolver = jarSupport.getClassPathResolver(listOf(testJar.toFile()))
            resolver.assertResolvedClass("test.pkg.Test")
        }
    }

    @RequiresCapabilities(Capability.CLASS_PATH_RESOLVER)
    @Test
    fun `Test classpath resolve - with java lang`() {
        runJarSupportTest {
            val resolver =
                jarSupport.getClassPathResolver(
                    listOf(
                        getAndroidJar(),
                        testJar.toFile(),
                    )
                )
            resolver.assertResolvedClass("test.pkg.Test")
        }
    }

    @RequiresCapabilities(Capability.LOAD_JAR)
    @Test
    fun `Test load jar - no java lang`() {
        runJarSupportTest {
            val codebase = jarSupport.loadFromJar(testJar.toFile(), emptyList())
            codebase.assertClass("test.pkg.Test")
            codebase.assertClass("test.pkg.Test.Nested")
            codebase.assertClass("test.pkg.Test.Nested.DoublyNested")
        }
    }

    @RequiresCapabilities(Capability.LOAD_JAR)
    @Test
    fun `Test load jar - with java lang`() {
        runJarSupportTest {
            val codebase = jarSupport.loadFromJar(testJar.toFile(), listOf(getAndroidJar()))
            codebase.assertClass("test.pkg.Test")
            codebase.assertClass("test.pkg.Test.Nested")
            codebase.assertClass("test.pkg.Test.Nested.DoublyNested")
        }
    }

    @RequiresCapabilities(Capability.LOAD_JAR, Capability.KOTLIN)
    @Test
    fun `Test load jar - check annotation properties`() {
        runJarSupportTest {
            val codebase = jarSupport.loadFromJar(testJar.toFile(), emptyList())

            // Make sure that the annotation loaded from the jar does not have Kotlin properties for
            // the attributes added.
            val annotation = codebase.assertClass("test.pkg.Anno")
            assertEquals(emptyList(), annotation.properties())
        }
    }
}
