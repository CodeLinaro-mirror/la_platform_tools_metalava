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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Tests [DefaultModifierList] functionality. */
class CommonModifierListTest : BaseModelTest() {

    /** Just creates a basic [Codebase] for the test to use. */
    private fun runWithCodebase(body: CodebaseContext.() -> Unit) {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo {
                        private Foo() {}
                    }
                """
            ),
            test = body,
        )
    }

    @Test
    fun `test equals() of empty modifiers`() {
        runWithCodebase {
            val annotation =
                DefaultAnnotationItem.create(codebase, JAVA_LANG_DEPRECATED, emptyList())!!

            // Create an empty set of modifiers
            val modifiers = DefaultModifierList(flags = DefaultModifierList.PUBLIC)

            // Create another empty set of modifiers.
            val anotherModifiers = DefaultModifierList(flags = DefaultModifierList.PUBLIC)

            // They compare equal both directly and in their string representation.
            assertEquals(modifiers, anotherModifiers, message = "modifiers before")
            assertEquals(
                modifiers.toString(),
                anotherModifiers.toString(),
                message = "modifiers string representation before"
            )

            // Now add and remove an annotation, after which it should still be empty.
            anotherModifiers.apply {
                // Add and remove the annotations in separate mutations as otherwise it is just
                // testing the standard List behavior.
                mutateAnnotations { add(annotation) }
                mutateAnnotations { remove(annotation) }
            }

            // They should still compare equal both directly and in their string representation but
            // they do not.
            // TODO(b/356548977): Fix this.
            assertEquals(modifiers, anotherModifiers, message = "modifiers")
            assertEquals(
                modifiers.toString(),
                anotherModifiers.toString(),
                message = "modifiers string representation"
            )
        }
    }

    @Test
    fun `test toString()`() {
        runWithCodebase {
            val annotation =
                DefaultAnnotationItem.create(codebase, FileLocation.UNKNOWN, JAVA_LANG_DEPRECATED) {
                    emptyList()
                }!!
            val modifiers =
                DefaultModifierList(
                    flags = DefaultModifierList.PUBLIC,
                    annotations = listOf(annotation),
                )
            assertEquals(
                "ModifierList(flags = 0b100, annotations = [@java.lang.Deprecated])",
                modifiers.toString()
            )
        }
    }

    @Test
    fun `test equivalentTo()`() {
        assertTrue {
            DefaultModifierList(flags = DefaultModifierList.PUBLIC)
                .equivalentTo(null, DefaultModifierList(flags = DefaultModifierList.PUBLIC))
        }
        assertFalse {
            DefaultModifierList(flags = DefaultModifierList.PRIVATE)
                .equivalentTo(null, DefaultModifierList(flags = DefaultModifierList.PUBLIC))
        }
    }
}
