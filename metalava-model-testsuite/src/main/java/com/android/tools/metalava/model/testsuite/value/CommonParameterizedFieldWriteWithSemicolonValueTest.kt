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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.Assertions.Companion.assertField
import com.android.tools.metalava.model.testsuite.value.TestClassCreator.Companion.FIELD_NAME
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.NO_INITIAL_FIELD_VALUE
import com.android.tools.metalava.model.testsuite.value.ValueUseSite.FIELD_WRITE_WITH_SEMICOLON
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.ClassRule
import org.junit.runners.Parameterized

/** Run parameterized tests for [FIELD_WRITE_WITH_SEMICOLON]. */
class CommonParameterizedFieldWriteWithSemicolonValueTest :
    BaseCommonParameterizedValueTest(
        testFileCacheRule.cache,
        testJarFile,
        FIELD_WRITE_WITH_SEMICOLON,
        legacySourceGetter = {
            val field = testClassItem.assertField(FIELD_NAME)

            // Print the field with semicolon.
            val stringWriter = StringWriter()
            PrintWriter(stringWriter).use { writer -> field.writeValueWithSemicolon(writer) }
            val withSemicolon = stringWriter.toString()

            // Extract the value from the " = ...; // ...." string.
            if (withSemicolon == ";") NO_INITIAL_FIELD_VALUE
            else withSemicolon.substringAfter(" = ").substringBefore(";")
        },
    ) {
    companion object : BaseCompanion(FIELD_WRITE_WITH_SEMICOLON) {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /** Supply the list of test cases as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters fun params() = testParameters
    }
}
