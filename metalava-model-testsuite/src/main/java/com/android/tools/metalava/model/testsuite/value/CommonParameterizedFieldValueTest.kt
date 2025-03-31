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
import com.android.tools.metalava.model.testsuite.value.ValueUseSite.FIELD_VALUE
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import kotlin.test.assertNotNull
import org.junit.ClassRule
import org.junit.runners.Parameterized

/** Run parameterized tests for [FIELD_VALUE]. */
class CommonParameterizedFieldValueTest :
    BaseCommonParameterizedValueTest(
        testFileCacheRule.cache,
        testJarFile,
        FIELD_VALUE,
        legacySourceGetter = {
            val field = testClassItem.assertField(FIELD_NAME)
            val fieldValue = assertNotNull(field.legacyFieldValue, "No field value")
            fieldValue.initialValue(true)?.toString() ?: NO_INITIAL_FIELD_VALUE
        },
    ) {
    companion object : BaseCompanion(FIELD_VALUE) {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /** Supply the list of test cases as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters fun params() = testParameters
    }
}
