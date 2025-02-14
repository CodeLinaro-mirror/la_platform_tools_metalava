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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import junit.framework.TestCase.assertNull
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [ClassItem] that are `enum` classes. */
class CommonAnnotationClassTest : BaseModelTest() {
    @Test
    fun `Test annotation class super class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public @interface Foo {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    annotation class Foo { }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public @interface Foo {
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotationClass = codebase.assertResolvedClass("java.lang.annotation.Annotation")

            assertNull(fooClass.superClassType()?.asClass())
            assertNull(fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(annotationClass), interfaceList)
        }
    }
}
