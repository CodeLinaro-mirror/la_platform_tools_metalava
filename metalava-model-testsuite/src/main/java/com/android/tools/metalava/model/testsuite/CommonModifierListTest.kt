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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Tests [ModifierList] and [MutableModifierList] functionality. */
@SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
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
            val annotation = AnnotationItem.createMarkerAnnotation(codebase, JAVA_LANG_DEPRECATED)!!

            // Create an empty set of modifiers
            val modifiers = createMutableModifiers(VisibilityLevel.PUBLIC)

            // Create another empty set of modifiers.
            val anotherModifiers = createMutableModifiers(VisibilityLevel.PUBLIC)

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
                AnnotationItem.createAttributesLazily(
                    codebase,
                    FileLocation.UNKNOWN,
                    JAVA_LANG_DEPRECATED
                ) {
                    emptyList()
                }!!
            val modifiers =
                createImmutableModifiers(
                    visibility = VisibilityLevel.PUBLIC,
                    annotations = listOf(annotation),
                )
            assertEquals(
                "ModifierList(flags = [public], annotations = [@java.lang.Deprecated])",
                modifiers.toString()
            )
        }
    }

    @Test
    fun `test equivalentTo()`() {
        assertTrue {
            createImmutableModifiers(VisibilityLevel.PUBLIC)
                .equivalentTo(null, createImmutableModifiers(VisibilityLevel.PUBLIC))
        }
        assertFalse {
            createImmutableModifiers(VisibilityLevel.PRIVATE)
                .equivalentTo(null, createImmutableModifiers(VisibilityLevel.PUBLIC))
        }
    }

    @Test
    fun `test makeEquivalentTo for significant modifier`() {
        fun runTest(base: Boolean, other: Boolean) {
            val baseModifiers = createMutableModifiers(VisibilityLevel.PUBLIC)
            baseModifiers.setFinal(base)
            val otherModifiers = createMutableModifiers(VisibilityLevel.PUBLIC)
            otherModifiers.setFinal(other)

            baseModifiers.makeEquivalentTo(otherModifiers.toImmutable())
            assertTrue(baseModifiers.equivalentTo(owner = null, otherModifiers))
            // Final is significant to equivalence, so after makeEquivalentTo the value should be
            // switched to the value from other.
            assertEquals(baseModifiers.isFinal(), other)
        }

        runTest(base = true, other = true)
        runTest(base = true, other = false)
        runTest(base = false, other = true)
        runTest(base = false, other = false)
    }

    @Test
    fun `test makeEquivalentTo for insignificant modifier`() {
        fun runTest(base: Boolean, other: Boolean) {
            val baseModifiers = createMutableModifiers(VisibilityLevel.PUBLIC)
            baseModifiers.setActual(base)
            val otherModifiers = createMutableModifiers(VisibilityLevel.PUBLIC)
            otherModifiers.setActual(other)

            // Actual is not significant to equivalence, so after makeEquivalentTo the value should
            // be unchanged from the original base.
            baseModifiers.makeEquivalentTo(otherModifiers.toImmutable())
            assertTrue(baseModifiers.equivalentTo(owner = null, otherModifiers))
            assertEquals(baseModifiers.isActual(), base)
        }

        runTest(base = true, other = true)
        runTest(base = true, other = false)
        runTest(base = false, other = true)
        runTest(base = false, other = false)
    }

    @Test
    fun `test makeEquivalentTo for visibility`() {
        val baseModifiers = createMutableModifiers(VisibilityLevel.PUBLIC)
        val otherModifiers = createMutableModifiers(VisibilityLevel.PROTECTED)

        baseModifiers.makeEquivalentTo(otherModifiers.toImmutable())
        assertTrue(baseModifiers.equivalentTo(owner = null, otherModifiers))
        // Visibility is significant to equivalence, so after makeEquivalentTo the value should be
        // changed to the visibility from other.
        assertEquals(baseModifiers.getVisibilityLevel(), VisibilityLevel.PROTECTED)
    }
}
