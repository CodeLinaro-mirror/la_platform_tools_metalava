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

import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LineMapTest {
    @Test
    fun `Test single line`() {
        val text = "hello"
        val lineMap = LineMap.create(text)

        assertEquals(1, lineMap.lineNumber(0), message = "lineNumber(0)")
        assertEquals(0, lineMap.lineOffset(0), message = "lineOffset(0)")
        assertEquals(1, lineMap.characterPosition(0), message = "characterPosition(0)")
        assertEquals(0, lineMap.characterOffset(0), message = "characterOffset(0)")

        assertEquals(1, lineMap.lineNumber(4), message = "lineNumber(4)")
        assertEquals(0, lineMap.lineOffset(4), message = "lineOffset(4)")
        assertEquals(5, lineMap.characterPosition(4), message = "characterPosition(4)")
        assertEquals(4, lineMap.characterOffset(4), message = "characterOffset(4)")

        // EOF offset
        assertEquals(1, lineMap.lineNumber(5), message = "lineNumber(5)")
        assertEquals(0, lineMap.lineOffset(5), message = "lineOffset(5)")
        assertEquals(6, lineMap.characterPosition(5), message = "characterPosition(5)")
        assertEquals(5, lineMap.characterOffset(5), message = "characterOffset(5)")
    }

    @Test
    fun `Test multiple lines with LF, CRLF, and CR`() {
        // Line 1: "ab\n" (indices 0..2, line 2 starts at 3)
        // Line 2: "cd\r\n" (indices 3..6, line 3 starts at 7)
        // Line 3: "ef\r" (indices 7..9, line 4 starts at 10)
        // Line 4: "gh" (indices 10..11, EOF at 12)
        val text = "ab\ncd\r\nef\rgh"
        val lineMap = LineMap.create(text)

        // Line 1
        assertEquals(1, lineMap.lineNumber(0), message = "lineNumber(0)")
        assertEquals(1, lineMap.characterPosition(0), message = "characterPosition(0)")
        assertEquals(1, lineMap.lineNumber(2), message = "lineNumber(2)") // '\n'
        assertEquals(3, lineMap.characterPosition(2), message = "characterPosition(2)")

        // Line 2
        assertEquals(2, lineMap.lineNumber(3), message = "lineNumber(3)")
        assertEquals(1, lineMap.characterPosition(3), message = "characterPosition(3)")
        assertEquals(2, lineMap.lineNumber(5), message = "lineNumber(5)") // '\r'
        assertEquals(3, lineMap.characterPosition(5), message = "characterPosition(5)")
        assertEquals(2, lineMap.lineNumber(6), message = "lineNumber(6)") // '\n'
        assertEquals(4, lineMap.characterPosition(6), message = "characterPosition(6)")

        // Line 3
        assertEquals(3, lineMap.lineNumber(7), message = "lineNumber(7)")
        assertEquals(1, lineMap.characterPosition(7), message = "characterPosition(7)")
        assertEquals(3, lineMap.lineNumber(9), message = "lineNumber(9)") // '\r'
        assertEquals(3, lineMap.characterPosition(9), message = "characterPosition(9)")

        // Line 4
        assertEquals(4, lineMap.lineNumber(10), message = "lineNumber(10)")
        assertEquals(1, lineMap.characterPosition(10), message = "characterPosition(10)")
        assertEquals(4, lineMap.lineNumber(12), message = "lineNumber(12)") // EOF
        assertEquals(3, lineMap.characterPosition(12), message = "characterPosition(12)")
    }

    @Test
    fun `Test out of range`() {
        val text = "hello"
        val lineMap = LineMap.create(text)

        assertThrows(IllegalArgumentException::class.java) { lineMap.lineNumber(-1) }
        assertThrows(IllegalArgumentException::class.java) { lineMap.lineNumber(text.length + 1) }
    }

    @Test
    fun `Test fileLocation`() {
        val text = "line1\n  line2"
        val lineMap = LineMap.create(text)
        val path = Path.of("/test/api.txt")
        val index = text.indexOf("line2")

        val locWithoutChar = lineMap.fileLocation(path, index)
        assertEquals(path, locWithoutChar.path, message = "locWithoutChar.path")
        assertEquals(2, locWithoutChar.line, message = "locWithoutChar.line")
        assertEquals(
            -1,
            locWithoutChar.characterPosition,
            message = "locWithoutChar.characterPosition",
        )
        assertEquals(
            "/test/api.txt:2",
            locWithoutChar.toString(),
            message = "locWithoutChar.toString()",
        )

        val locWithChar = lineMap.fileLocation(path, index, includeCharacterPosition = true)
        assertEquals(path, locWithChar.path, message = "locWithChar.path")
        assertEquals(2, locWithChar.line, message = "locWithChar.line")
        assertEquals(
            3,
            locWithChar.characterPosition,
            message = "locWithChar.characterPosition",
        )
        assertEquals(
            "/test/api.txt:2:3",
            locWithChar.toString(),
            message = "locWithChar.toString()",
        )
    }

    @Test
    fun `Test many lines resizing`() {
        val lines = (1..200).joinToString("\n") { "line$it" }
        val lineMap = LineMap.create(lines)
        val lastLineIndex = lines.lastIndexOf("line200")
        assertEquals(
            200,
            lineMap.lineNumber(lastLineIndex),
            message = "lineNumber(lastLineIndex)",
        )
        assertEquals(
            1,
            lineMap.characterPosition(lastLineIndex),
            message = "characterPosition(lastLineIndex)",
        )
    }
}
