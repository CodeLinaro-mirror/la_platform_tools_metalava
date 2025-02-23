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

package com.android.tools.metalava.jar

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.reporter.ThrowingReporter
import com.android.tools.metalava.testing.java
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class StandaloneJarCodebaseLoaderTest : DriverTest() {
    private val jarCodebaseLoader by lazy {
        StandaloneJarCodebaseLoader.create(
            disableStderrDumping = false,
            ProgressTracker(),
            ThrowingReporter.INSTANCE,
            sourceModelProvider = codebaseCreatorConfig.creator,
        )
    }

    @Test
    fun `Test jar loader freezes codebase`() {
        lateinit var jarFile: File
        buildFileStructure {
            jarFile =
                jar(
                    "test.jar",
                    java(
                        """
                            package test.pkg;
                            public class Foo {}
                        """
                    )
                )
        }

        val codebase = jarCodebaseLoader.loadFromJarFile(jarFile)
        val fooClass = codebase.assertClass("test.pkg.Foo")
        val exception =
            assertThrows(IllegalStateException::class.java) {
                fooClass.mutateModifiers { fail("should not reach here") }
            }
        assertEquals("Cannot modify frozen class test.pkg.Foo", exception.message)
    }

    @Test
    fun `Test jar loader does not freeze codebase`() {
        lateinit var jarFile: File
        buildFileStructure {
            jarFile =
                jar(
                    "test.jar",
                    java(
                        """
                            package test.pkg;
                            public class Foo {}
                        """
                    )
                )
        }

        val codebase = jarCodebaseLoader.loadFromJarFile(jarFile, freezeCodebase = false)
        val fooClass = codebase.assertClass("test.pkg.Foo")
        assertFalse(fooClass.modifiers.isFinal(), message = "isFinal before mutation")
        fooClass.mutateModifiers { setFinal(true) }
        assertTrue(fooClass.modifiers.isFinal(), message = "isFinal after mutation")
    }

    @Test
    fun `Test loaded jar depends on class path`() {
        lateinit var classPathJarFile: File
        lateinit var jarFile: File
        buildFileStructure {
            classPathJarFile =
                jar(
                    "classpath.jar",
                    java(
                        """
                            package test.pkg;
                            public class Bar {}
                        """
                    ),
                )

            // This will fail to compile if the class path is not provided.
            jarFile =
                jar(
                    "test.jar",
                    java(
                        """
                            package test.pkg;
                            public class Foo extends Bar {}
                        """
                    ),
                    classPath = listOf(classPathJarFile),
                )
        }

        jarCodebaseLoader.loadFromJarFile(jarFile).let { codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertEquals(
                "test.pkg.Bar",
                fooClass.superClassType().toString(),
                message = "no classpath, super class type"
            )
            assertNull(fooClass.superClass(), message = "no classpath, no super class")
        }

        jarCodebaseLoader.loadFromJarFile(jarFile, classPath = listOf(classPathJarFile)).let {
            codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertEquals(
                "test.pkg.Bar",
                fooClass.superClassType().toString(),
                message = "classpath, super class type"
            )
            codebase.assertResolvedClass("test.pkg.Bar")
        }
    }
}
