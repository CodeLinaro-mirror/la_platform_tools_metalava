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

package com.android.tools.metalava

import com.android.tools.metalava.config.ApiFlagConfig
import com.android.tools.metalava.config.ApiFlagConfig.Mutability.IMMUTABLE
import com.android.tools.metalava.config.ApiFlagConfig.Mutability.MUTABLE
import com.android.tools.metalava.config.ApiFlagConfig.Status.DISABLED
import com.android.tools.metalava.config.ApiFlagConfig.Status.ENABLED
import com.android.tools.metalava.config.ApiFlagsConfig
import com.android.tools.metalava.model.api.flags.ApiFlag
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ApiFlagsCreatorTest {
    @Test
    fun `Test creation from config`() {
        val apiFlagsConfig =
            ApiFlagsConfig(
                flags =
                    listOf(
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag1",
                            mutability = MUTABLE,
                            status = DISABLED
                        ),
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag2",
                            mutability = IMMUTABLE,
                            status = DISABLED
                        ),
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag3",
                            mutability = MUTABLE,
                            status = ENABLED
                        ),
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag4",
                            mutability = IMMUTABLE,
                            status = ENABLED
                        ),
                    ),
            )

        val apiFlags = ApiFlagsCreator.createFromConfig(apiFlagsConfig)

        val expected =
            mapOf(
                "test.pkg.flag1" to ApiFlag.KEEP_FLAGGED_API,
                // No test.pkg.flag2 as that is disabled and ApiFlags will default to disabled.
                "test.pkg.flag3" to ApiFlag.KEEP_FLAGGED_API,
                "test.pkg.flag4" to ApiFlag.FINALIZE_FLAGGED_API,
            )
        assertEquals(expected, apiFlags!!.byQualifiedName)
    }

    @Test
    fun `Test null config`() {
        val apiFlags = ApiFlagsCreator.createFromConfig(null)
        assertNull(apiFlags)
    }
}
