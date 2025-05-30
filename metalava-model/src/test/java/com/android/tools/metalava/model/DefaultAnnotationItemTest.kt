/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.testing.value.arrayValue
import com.android.tools.metalava.model.testing.value.fieldReferenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAnnotationItemTest : Assertions {

    private fun createDefaultAnnotationItem(source: String) =
        DefaultAnnotationItem.createFromSource(AnnotationContext.DEFAULT_RESOLVE_NULL, source)
            ?: error("Could not create annotation from: '$source'")

    @Test
    fun testSimple() {
        val annotation = createDefaultAnnotationItem("@androidx.annotation.Nullable")
        assertEquals("@androidx.annotation.Nullable", annotation.toSource())
        assertEquals("androidx.annotation.Nullable", annotation.qualifiedName)
        assertTrue(annotation.attributes.isEmpty())
    }

    @Test
    fun testIntRange() {
        val annotation =
            createDefaultAnnotationItem("@androidx.annotation.IntRange(from = 20, to = 40)")
        assertEquals("@androidx.annotation.IntRange(from=20, to=40)", annotation.toSource())
        assertEquals("androidx.annotation.IntRange", annotation.qualifiedName)
        assertEquals(2, annotation.attributes.size)
        assertEquals("from", annotation.assertAttribute("from").name)
        assertEquals("20", annotation.assertAttribute("from").value.toValueString())
        assertEquals("to", annotation.assertAttribute("to").name)
        assertEquals("40", annotation.assertAttribute("to").value.toValueString())
    }

    @Test
    fun testIntDef() {
        val annotation =
            createDefaultAnnotationItem(
                "@androidx.annotation.IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})"
            )
        assertEquals(
            "@androidx.annotation.IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})",
            annotation.toSource()
        )
        assertEquals("androidx.annotation.IntDef", annotation.qualifiedName)
        assertEquals(1, annotation.attributes.size)
        val attribute = annotation.assertAttribute("value")
        assertEquals("value", attribute.name)
        assertEquals(
            "{STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT}",
            attribute.value.toValueString()
        )

        assertEquals(
            arrayValue(
                fieldReferenceValue("", "STYLE_NORMAL"),
                fieldReferenceValue("", "STYLE_NO_TITLE"),
                fieldReferenceValue("", "STYLE_NO_FRAME"),
                fieldReferenceValue("", "STYLE_NO_INPUT"),
            ),
            attribute.value
        )
    }
}
