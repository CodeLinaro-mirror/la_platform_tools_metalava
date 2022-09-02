/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.metalava.apilevels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ApiToExtensionsMapTest {
    @Test
    fun `empty input`() {
        val rules = """
            # No rules is a valid (albeit weird).
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertTrue(map.getExtensions("com.foo.Bar").isEmpty())
    }

    @Test
    fun wildcard() {
        val rules = """
            # All APIs will default to extension SDK A.

            file.jar    *    A
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertEquals(map.getExtensions("com.foo.Bar"), setOf("A"))
        assertEquals(map.getExtensions("com.foo.SomeOtherBar"), setOf("A"))
    }

    @Test
    fun `single class`() {
        val rules = """
            # A single class. The class, any internal classes, and any methods are allowed;
            # everything else is denied.

            file.jar    com.foo.Bar    A
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertEquals(map.getExtensions("com.foo.Bar"), setOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar#FIELD"), setOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar#method"), setOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar\$Inner"), setOf("A"))
        assertEquals(map.getExtensions("com.foo.Bar\$Inner\$InnerInner"), setOf("A"))

        val clazz = ApiClass("com/foo/Bar", 1, false)
        val method = ApiElement("method(Ljava.lang.String;I)V", 2, false)
        assertEquals(map.getExtensions(clazz), setOf("A"))
        assertEquals(map.getExtensions(clazz, method), setOf("A"))

        assertTrue(map.getExtensions("com.foo.SomeOtherClass").isEmpty())
    }

    @Test
    fun `multiple extensions`() {
        val rules = """
            # Any number of white space separated extension SDKs may be listed.

            file.jar    *    A B FOO BAR
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertEquals(map.getExtensions("com.foo.Bar"), setOf("A", "B", "FOO", "BAR"))
    }

    @Test
    fun precedence() {
        val rules = """
            # Multiple classes, and multiple rules with different precedence.

            file.jar    *              A
            file.jar    com.foo.Bar    B
            file.jar    com.foo.Bar${'$'}Inner#method    C
            file.jar    com.bar.Foo    D
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertEquals(map.getExtensions("anything"), setOf("A"))

        assertEquals(map.getExtensions("com.foo.Bar"), setOf("B"))
        assertEquals(map.getExtensions("com.foo.Bar#FIELD"), setOf("B"))
        assertEquals(map.getExtensions("com.foo.Bar\$Inner"), setOf("B"))

        assertEquals(map.getExtensions("com.foo.Bar\$Inner#method"), setOf("C"))

        assertEquals(map.getExtensions("com.bar.Foo"), setOf("D"))
        assertEquals(map.getExtensions("com.bar.Foo#FIELD"), setOf("D"))
    }

    @Test
    fun `multiple jar files`() {
        val rules = """
            # The allow list will only consider patterns that are marked with the given jar file

            a.jar    *    A
            b.jar    *    B
        """.trimIndent()
        val allowListA = ApiToExtensionsMap.fromString("a.jar", rules)
        val allowListB = ApiToExtensionsMap.fromString("b.jar", rules)
        val allowListC = ApiToExtensionsMap.fromString("c.jar", rules)

        assertEquals(allowListA.getExtensions("anything"), setOf("A"))
        assertEquals(allowListB.getExtensions("anything"), setOf("B"))
        assertTrue(allowListC.getExtensions("anything").isEmpty())
    }

    @Test
    fun `bad input`() {
        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # missing jar file
                com.foo.Bar    A
                """.trimIndent()
            )
        }

        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # duplicate pattern
                file.jar    com.foo.Bar    A
                file.jar    com.foo.Bar    B
                """.trimIndent()
            )
        }
    }

    private fun assertThrows(expr: () -> Unit) {
        try {
            expr()
        } catch (e: Exception) {
            return
        }
        fail("expression did not throw exception")
    }
}
