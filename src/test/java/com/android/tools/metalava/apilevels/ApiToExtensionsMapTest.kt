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

import org.junit.Assert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ApiToExtensionsMapTest {
    @Test
    fun `empty input`() {
        val rules = """
            # No rules is a valid (albeit weird).
            R    30
            S    31
            T    33
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertTrue(map.getExtensions("com.foo.Bar").isEmpty())
    }

    @Test
    fun wildcard() {
        val rules = """
            # All APIs will default to extension SDK A.
            A    1

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
            A    1

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
            A      1
            B      2
            FOO    10
            BAR    11

            file.jar    *    A B FOO BAR
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertEquals(map.getExtensions("com.foo.Bar"), setOf("A", "B", "FOO", "BAR"))
    }

    @Test
    fun precedence() {
        val rules = """
            # Multiple classes, and multiple rules with different precedence.
            A    1
            B    2
            C    3
            D    4

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
            A    1
            B    2

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
    fun `declarations and rules can be mixed`() {
        val rules = """
            # SDK declarations and rule lines can be mixed in any order
            A    1
            file.jar    *    A B
            B    2
        """.trimIndent()
        val map = ApiToExtensionsMap.fromString("file.jar", rules)

        assertEquals(map.getExtensions("com.foo.Bar"), setOf("A", "B"))
    }

    @Test
    fun `bad input`() {
        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # using 0 (reserved for the Android platform SDK) as ID
                A    0

                file.jar com.foo.Bar    A
                """.trimIndent()
            )
        }

        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # missing jar file
                A    1

                com.foo.Bar    A
                """.trimIndent()
            )
        }

        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # duplicate rules pattern
                A    1

                file.jar    com.foo.Bar    A
                file.jar    com.foo.Bar    B
                """.trimIndent()
            )
        }

        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # rules refer to a non-declared SDK
                B    2

                file.jar    com.foo.Bar    A
                """.trimIndent()
            )
        }

        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # duplicate numerical ID
                A    1
                B    1

                file.jar    com.foo.Bar    A
                """.trimIndent()
            )
        }
        assertThrows {
            ApiToExtensionsMap.fromString(
                "file.jar",
                """
                # duplicate SDK name
                A    1
                A    2

                file.jar    com.foo.Bar    A
                """.trimIndent()
            )
        }
    }

    @Test
    fun `calculate from xml attribute`() {
        val rules = """
            R      30
            S      31
            T      33
            FOO    1000
            BAR    1001
        """.trimIndent()
        val filter = ApiToExtensionsMap.fromString("file.jar", rules)

        Assert.assertEquals(
            "",
            filter.calculateFromAttr(null, setOf(), 4)
        )

        Assert.assertEquals(
            "30:4",
            filter.calculateFromAttr(null, setOf("R"), 4)
        )

        Assert.assertEquals(
            setOf("30:4", "31:4"),
            filter.calculateFromAttr(null, setOf("R", "S"), 4).split(',').toSet()
        )

        Assert.assertEquals(
            setOf("0:33", "30:4", "31:4"),
            filter.calculateFromAttr(33, setOf("R", "S"), 4).split(',').toSet()
        )

        Assert.assertEquals(
            setOf("0:33", "30:4", "31:4", "1000:4"),
            filter.calculateFromAttr(33, setOf("R", "S", "FOO"), 4).split(',').toSet()
        )

        Assert.assertEquals(
            setOf("0:33", "30:4", "31:4", "1000:4", "1001:4"),
            filter.calculateFromAttr(33, setOf("R", "S", "FOO", "BAR"), 4).split(',').toSet()
        )
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
