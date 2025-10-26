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

package com.android.tools.metalava.model.source.doc

import kotlin.test.assertEquals
import org.junit.Test

class StringUtilsTest {
    private fun checkContainsWord(
        text: String,
        word: String,
        expectedResult: Boolean,
        message: String
    ) {
        assertEquals(expectedResult, text.containsWord(word), message)

        // Confirm that it behaves just like `\b....\b`.
        val regex = Regex("""\b\Q$word\E\b""")
        var found = regex.find(text, 0) != null
        assertEquals(expectedResult, found, message)
    }

    @Test
    fun containsWord() {
        checkContainsWord(
            "blah",
            "blah",
            expectedResult = true,
            message = "whole string",
        )
        checkContainsWord(
            "blah - at the start",
            "blah",
            expectedResult = true,
            message = "at the start",
        )
        checkContainsWord(
            "at the end - blah",
            "blah",
            expectedResult = true,
            message = "at the end",
        )
        checkContainsWord(
            "'blah'",
            "blah",
            expectedResult = true,
            message = "in single quotes",
        )
        checkContainsWord(
            "prefix-blah-suffix",
            "blah",
            expectedResult = true,
            message = "dashes",
        )
        checkContainsWord(
            "rumbling bling",
            "bling",
            expectedResult = true,
            message = "second instance",
        )

        checkContainsWord(
            "beginning",
            "begin",
            expectedResult = false,
            message = "start of bigger word",
        )
        checkContainsWord(
            "rumbling",
            "bling",
            expectedResult = false,
            message = "end of bigger word",
        )
        checkContainsWord(
            "long",
            "on",
            expectedResult = false,
            message = "middle of bigger word",
        )
        checkContainsWord(
            "prefix_blah_suffix",
            "blah",
            expectedResult = false,
            message = "underscores",
        )
    }
}
