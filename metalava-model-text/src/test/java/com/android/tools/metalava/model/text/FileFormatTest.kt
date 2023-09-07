/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import java.io.LineNumberReader
import java.io.StringReader
import kotlin.test.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FileFormatTest {
    private fun checkParseHeader(
        apiText: String,
        expectedFormat: FileFormat? = null,
        expectedError: String? = null,
        expectedNextLine: String? = null
    ) {
        val reader = LineNumberReader(StringReader(apiText.trimIndent()))
        if (expectedError == null) {
            val format = FileFormat.parseHeader("api.txt", reader)
            assertEquals(expectedFormat, format)
            val nextLine = reader.readLine()
            assertEquals(expectedNextLine, nextLine, "next line mismatch")
        } else {
            assertNull("cannot specify both expectedFormat and expectedError", expectedFormat)
            val e =
                assertThrows(ApiParseException::class.java) {
                    FileFormat.parseHeader("api.txt", reader)
                }
            assertEquals(expectedError, e.message)
        }
    }

    @Test
    fun `Check format parsing, blank line between header and package`() {
        checkParseHeader(
            """
                // Signature format: 2.0

                package test.pkg {
            """,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "",
        )
    }

    @Test
    fun `Check format parsing, comment after header and package`() {
        checkParseHeader(
            """
                // Signature format: 2.0
                // Some manually added comment
            """,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "// Some manually added comment",
        )
    }

    @Test
    fun `Check format parsing (v1)`() {
        checkParseHeader(
            """
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                  }
                }
            """,
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found 'package test.pkg {', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (unknown version)`() {
        checkParseHeader(
            """
                // Signature format: 3.14
                package test.pkg {
                  public class MyTest {
                    ctor public MyTest();
                  }
                }
                """,
            expectedError =
                "api.txt:1: Signature format error - invalid version, found '3.14', expected one of '2.0', '3.0', '4.0', '5.0'",
        )
    }

    @Test
    fun `Check format parsing (v2)`() {
        checkParseHeader(
            """
                // Signature format: 2.0
                package libcore.util {
                  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {
                    method public abstract int from() default java.lang.Integer.MIN_VALUE;
                    method public abstract int to() default java.lang.Integer.MAX_VALUE;
                  }
                }
            """,
            expectedFormat = FileFormat.V2,
            expectedNextLine = "package libcore.util {",
        )
    }

    @Test
    fun `Check format parsing (v3)`() {
        checkParseHeader(
            """
                // Signature format: 3.0
                package androidx.collection {
                  public final class LruCacheKt {
                    ctor public LruCacheKt();
                  }
                }
            """,
            expectedFormat = FileFormat.V3,
            expectedNextLine = "package androidx.collection {",
        )
    }

    @Test
    fun `Check format parsing (v2 non-unix newlines)`() {
        checkParseHeader(
            "" +
                "// Signature format: 2.0\r\n" +
                "package libcore.util {\r\n" +
                "  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {\r\n" +
                "    method public abstract int from() default java.lang.Integer.MIN_VALUE;\r\n" +
                "    method public abstract int to() default java.lang.Integer.MAX_VALUE;\r\n" +
                "  }\r\n" +
                "}\r\n",
            expectedFormat = FileFormat.V2,
            expectedNextLine = "package libcore.util {",
        )
    }

    @Test
    fun `Check format parsing, shortened prefix (v2 non-unix newlines)`() {
        checkParseHeader(
            "" +
                "// Signature for\r\n" +
                "package libcore.util {\r\n" +
                "  @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface NonNull {\r\n" +
                "    method public abstract int from() default java.lang.Integer.MIN_VALUE;\r\n" +
                "    method public abstract int to() default java.lang.Integer.MAX_VALUE;\r\n" +
                "  }\r\n" +
                "}\r\n",
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found '// Signature for', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (invalid)`() {
        checkParseHeader(
            """
                blah blah
            """,
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found 'blah blah', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (blank)`() {
        checkParseHeader("")
    }

    @Test
    fun `Check format parsing (blank - multiple lines)`() {
        checkParseHeader(
            """



            """,
        )
    }

    @Test
    fun `Check format parsing (not blank, multiple lines of white space, then some text)`() {
        checkParseHeader(
            """


                blah blah
            """,
            expectedError =
                "api.txt:1: Signature format error - invalid prefix, found '', expected '// Signature format: '",
        )
    }

    @Test
    fun `Check format parsing (v3 + kotlin-style-nulls=no but no migrating)`() {
        checkParseHeader(
            """
                // Signature format: 3.0
                // - kotlin-style-nulls=no
            """,
            expectedError =
                "api.txt:2: Signature format error - must provide a 'migrating' property when customizing version 3.0",
        )
    }

    @Test
    fun `Check format parsing (v3 + kotlin-style-nulls=no,migrating=test)`() {
        checkParseHeader(
            """
                // Signature format: 3.0
                // - kotlin-style-nulls=no
                // - migrating=test
                
            """,
            expectedFormat = FileFormat.V3.copy(kotlinStyleNulls = false, migrating = "test")
        )
    }

    @Test
    fun `Check format parsing (v2 + kotlin-style-nulls=yes,migrating=test)`() {
        checkParseHeader(
            """
                // Signature format: 2.0
                // - kotlin-style-nulls=yes
                // - migrating=test
            """,
            expectedFormat = FileFormat.V2.copy(kotlinStyleNulls = true, migrating = "test")
        )
    }

    @Test
    fun `Check format parsing (v5) - no properties no package`() {
        checkParseHeader(
            """
                // Signature format: 5.0
            """,
            expectedFormat = FileFormat.V5
        )
    }

    @Test
    fun `Check format parsing (v5) - no properties with package`() {
        checkParseHeader(
            """
                // Signature format: 5.0
                package fred {
            """,
            expectedFormat = FileFormat.V5,
            expectedNextLine = "package fred {",
        )
    }

    @Test
    fun `Check format parsing (v5) - invalid property`() {
        checkParseHeader(
            """
                // Signature format: 5.0
                // - foo=fred
                package fred {
            """,
            expectedError =
                "api.txt:2: Signature format error - unknown format property name `foo`, expected one of 'concise-default-values', 'kotlin-style-nulls', 'migrating', 'overloaded-method-order'"
        )
    }

    @Test
    fun `Check format parsing (v5) - kotlin-style-nulls property`() {
        checkParseHeader(
            """
                // Signature format: 5.0
                // - kotlin-style-nulls=no
                package fred {
            """,
            expectedFormat = FileFormat.V5.copy(kotlinStyleNulls = false),
            expectedNextLine = "package fred {",
        )
    }

    @Test
    fun `Check header (v2)`() {
        assertEquals("// Signature format: 2.0\n", FileFormat.V2.header())
    }

    @Test
    fun `Check header (v2 + kotlin-style-nulls=yes + migrating=test)`() {
        assertEquals(
            """
                // Signature format: 2.0
                // - kotlin-style-nulls=yes
                // - migrating=test

            """
                .trimIndent(),
            FileFormat.V2.copy(kotlinStyleNulls = true, migrating = "test").header()
        )
    }

    @Test
    fun `Check header (v3 + kotlin-style-nulls=no)`() {
        assertEquals(
            """
                // Signature format: 3.0
                // - kotlin-style-nulls=no
                // - migrating=test

            """
                .trimIndent(),
            FileFormat.V3.copy(kotlinStyleNulls = false, migrating = "test").header()
        )
    }

    @Test
    fun `Check header (v2 + overloaded-method-order=source but no migrating)`() {
        assertEquals(
            // The full specifier is only output when migrating is specified.
            "// Signature format: 2.0\n",
            FileFormat.V2.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                )
                .header()
        )
    }

    @Test
    fun `Check no ',' in migrating`() {
        val e =
            assertThrows(IllegalStateException::class.java) {
                @Suppress("UnusedDataClassCopyResult") FileFormat.V2.copy(migrating = "a,b")
            }
        assertEquals(
            """invalid value for property 'migrating': 'a,b' contains at least one invalid character from the set {',', '\n'}""",
            e.message
        )
    }

    @Test
    fun `Check header (v5)`() {
        assertEquals(
            """
                // Signature format: 5.0
            """
                .trimIndent(),
            FileFormat.V5.header().trimIndent(),
        )
    }

    @Test
    fun `Check header (v5 + overloaded-method-order=source)`() {
        assertEquals(
            """
                // Signature format: 5.0
                // - overloaded-method-order=source
            """
                .trimIndent(),
            FileFormat.V5.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                )
                .header()
                .trimIndent()
        )
    }

    @Test
    fun `Check header (v5 + overloaded-method-order=source,migrating=test)`() {
        assertEquals(
            """
                // Signature format: 5.0
                // - migrating=test
                // - overloaded-method-order=source
            """
                .trimIndent(),
            FileFormat.V5.copy(
                    specifiedOverloadedMethodOrder = FileFormat.OverloadedMethodOrder.SOURCE,
                    migrating = "test",
                )
                .header()
                .trimIndent()
        )
    }
}
