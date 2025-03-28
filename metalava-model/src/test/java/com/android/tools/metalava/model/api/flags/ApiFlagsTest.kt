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

import kotlin.test.assertEquals
import org.junit.Test

class ApiFlagsTest {
    @Test
    fun `Test get`() {
        val apiFlags =
            ApiFlags(
                mapOf(
                    "test.pkg.flag1" to ApiFlag.KEEP_FLAGGED_API,
                    "test.pkg.flag2" to ApiFlag.REVERT_FLAGGED_API,
                    "test.pkg.flag3" to ApiFlag.FINALIZE_FLAGGED_API,
                )
            )

        assertEquals(ApiFlag.KEEP_FLAGGED_API, apiFlags["test.pkg.flag1"])
        assertEquals(ApiFlag.REVERT_FLAGGED_API, apiFlags["test.pkg.flag2"])
        assertEquals(ApiFlag.FINALIZE_FLAGGED_API, apiFlags["test.pkg.flag3"])
        // Unknown flags default to reverting.
        assertEquals(ApiFlag.REVERT_FLAGGED_API, apiFlags["test.pkg.flag4"])
    }
}
