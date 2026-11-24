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

package com.android.tools.metalava.model.parser

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.value.DefaultValueParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Unit tests for Java string unescaping and string/char literal value parsing. */
class StringLiteralsTest {
    private val valueParser =
        DefaultValueParser(
            AnnotationContext.DEFAULT_RESOLVE_NULL,
            TypeItemParser.forValueParser(AnnotationContext.DEFAULT_RESOLVE_NULL),
        )

    @Test
    fun `Test javaUnescapeString - plain and empty`() {
        assertEquals("", javaUnescapeString(""))
        assertEquals("module-lib", javaUnescapeString("module-lib"))
    }

    @Test
    fun `Test javaUnescapeString - standard escapes`() {
        assertEquals("\\\t\b\r\n\'\"", javaUnescapeString("""\\\t\b\r\n\'\"'""".dropLast(1)))
    }

    @Test
    fun `Test javaUnescapeString - unicode escapes`() {
        assertEquals(
            "AlO",
            javaUnescapeString("${'\\'}u0041${'\\'}u006c${'\\'}u004F"),
        )
    }

    @Test
    fun `Test javaUnescapeString - invalid unicode hex digit`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                javaUnescapeString("${'\\'}u00g0")
            }
        assertEquals(
            "invalid unicode escape sequence '${'\\'}u00g0' in string literal",
            exception.message,
        )
    }

    @Test
    fun `Test javaUnescapeString - truncated unicode escape`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) { javaUnescapeString("${'\\'}u00") }
        assertEquals(
            "invalid unicode escape sequence '${'\\'}u00' in string literal",
            exception.message,
        )
    }

    @Test
    fun `Test javaUnescapeString - trailing backslash`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) { javaUnescapeString("abc\\") }
        assertEquals(
            "unfinished escape sequence: abc\\",
            exception.message,
        )
    }

    @Test
    fun `Test javaUnescapeString - unknown escape sequence`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) { javaUnescapeString("""a\xb""") }
        assertEquals(
            """invalid escape sequence '\x' in string literal""",
            exception.message,
        )
    }

    @Test
    fun `Test DefaultValueParser - valid string and char literals`() {
        assertEquals(
            literalValue("a\"b\nc"),
            valueParser.parse(null, """"a\"b\nc""""),
        )
        assertEquals(
            literalValue('\''),
            valueParser.parse(null, """'\''"""),
        )
    }

    @Test
    fun `Test DefaultValueParser - unclosed string literal`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                valueParser.parse(null, """"abc""")
            }
        assertEquals(
            "unclosed string literal",
            exception.message,
        )
    }

    @Test
    fun `Test DefaultValueParser - unclosed string literal ending with escaped quote`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                valueParser.parse(null, "\"abc\\\"")
            }
        assertEquals(
            "unclosed string literal",
            exception.message,
        )
    }

    @Test
    fun `Test DefaultValueParser - unclosed char literal ending with escaped quote`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                valueParser.parse(null, """'a\'""")
            }
        assertEquals(
            "unclosed character literal",
            exception.message,
        )
    }

    @Test
    fun `Test parseStringLiteralValue with non-throwing onError`() {
        val errors = mutableListOf<String>()

        assertEquals(
            "public",
            parseStringLiteralValue(""""pub${'\\'}u006cic"""") { errors += it },
        )
        assertEquals(emptyList(), errors)

        assertNull(parseStringLiteralValue(""""unclosed""") { errors += it })
        assertNull(parseStringLiteralValue("\"abc\\\"") { errors += it })
        assertNull(parseStringLiteralValue(""""a\xb"""") { errors += it })
        assertEquals(
            listOf(
                "unclosed string literal",
                "unclosed string literal",
                """invalid escape sequence '\x' in string literal""",
            ),
            errors,
        )
    }
}
