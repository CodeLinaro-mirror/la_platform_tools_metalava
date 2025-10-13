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

package com.android.tools.metalava.reporter

import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class FileLocationTest {
    @Test
    fun `adjustForLineAndCharOffset - unknown`() {
        // This does not have either line numbers or character positions so
        // adjustForLineAndCharOffset does nothing, just returning the
        val base = FileLocation.UNKNOWN
        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 0, charOffset = 0),
            message = "lineOffset = 0, charOffset = 0,"
        )

        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 0, charOffset = 6),
            message = "lineOffset = 0, charOffset = 6"
        )

        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 2, charOffset = 0),
            message = "lineOffset = 2, charOffset = 0,"
        )

        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 2, charOffset = 4),
            message = "lineOffset = 2, charOffset = 4"
        )
    }

    @Test
    fun `adjustForLineAndCharOffset - line 5, characterPosition = 0`() {
        val base = FileLocation.createLocation(testPath, line = 5)
        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 0, charOffset = 0),
            message = "lineOffset = 0, charOffset = 0,"
        )

        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 0, charOffset = 6),
            message = "lineOffset = 0, charOffset = 6"
        )

        assertEquals(
            FileLocation.createLocation(testPath, line = 7),
            base.adjustForLineAndCharOffset(lineOffset = 2, charOffset = 0),
            message = "lineOffset = 2, charOffset = 0,"
        )

        assertEquals(
            FileLocation.createLocation(testPath, line = 7),
            base.adjustForLineAndCharOffset(lineOffset = 2, charOffset = 4),
            message = "lineOffset = 2, charOffset = 4"
        )
    }

    @Test
    fun `adjustForLineAndCharOffset - line 5, characterPosition = 6`() {
        val base = FileLocation.createLocation(testPath, line = 5, characterPosition = 6)
        assertSame(
            base,
            base.adjustForLineAndCharOffset(lineOffset = 0, charOffset = 0),
            message = "lineOffset = 0, charOffset = 0,"
        )

        assertEquals(
            FileLocation.createLocation(testPath, line = 5, characterPosition = 12),
            base.adjustForLineAndCharOffset(lineOffset = 0, charOffset = 6),
            message = "lineOffset = 0, charOffset = 6"
        )

        assertEquals(
            FileLocation.createLocation(testPath, line = 7, characterPosition = 1),
            base.adjustForLineAndCharOffset(lineOffset = 2, charOffset = 0),
            message = "lineOffset = 2, charOffset = 0,"
        )

        assertEquals(
            FileLocation.createLocation(testPath, line = 7, characterPosition = 5),
            base.adjustForLineAndCharOffset(lineOffset = 2, charOffset = 4),
            message = "lineOffset = 2, charOffset = 4"
        )
    }

    companion object {
        val testPath = Paths.get("path.txt")
    }
}
