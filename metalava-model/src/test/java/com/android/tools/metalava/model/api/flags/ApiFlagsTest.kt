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

package com.android.tools.metalava.model.api.flags

import com.android.tools.metalava.model.api.flags.ApiFlagAction.*
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class ApiFlagsTest {
    @Test
    fun `Test get`() {
        val apiFlag1 = ApiFlag("test.pkg.flag1", KEEP)
        val apiFlag2 = ApiFlag("test.pkg.flag2", REVERT)
        val apiFlag3 = ApiFlag("test.pkg.flag3", FINALIZE)
        val apiFlags =
            ApiFlags(
                listOf(
                    apiFlag1,
                    apiFlag2,
                    apiFlag3,
                )
            )

        assertSame(apiFlag1, apiFlags["test.pkg.flag1"])
        assertSame(apiFlag2, apiFlags["test.pkg.flag2"])
        assertSame(apiFlag3, apiFlags["test.pkg.flag3"])
        // Unknown flags default to reverting.
        val unknownFlag = apiFlags["test.pkg.flag4"]
        assertEquals(
            ApiFlag("test.pkg.flag4", REVERT, isExported = true, isKnown = false),
            unknownFlag
        )

        // Make sure that getting the unknown flag again returns the same instance.
        assertSame(unknownFlag, apiFlags["test.pkg.flag4"])
    }

    @Test
    fun `Test with unknown action`() {
        // Treat unknown flags as being kept.
        val apiFlags = ApiFlags(emptyList(), unknownFlagAction = KEEP)
        val unknownFlag = apiFlags["test.pkg.unknown"]
        assertEquals(
            ApiFlag("test.pkg.unknown", KEEP, isExported = true, isKnown = false),
            unknownFlag
        )
    }
}
