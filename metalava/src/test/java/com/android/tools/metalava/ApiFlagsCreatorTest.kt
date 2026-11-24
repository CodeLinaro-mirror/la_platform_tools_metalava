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

import com.android.tools.metalava.config.ApiFlagActionConfig.Mutability.IMMUTABLE
import com.android.tools.metalava.config.ApiFlagActionConfig.Mutability.MUTABLE
import com.android.tools.metalava.config.ApiFlagActionConfig.Status.DISABLED
import com.android.tools.metalava.config.ApiFlagActionConfig.Status.ENABLED
import com.android.tools.metalava.config.ApiFlagConfig
import com.android.tools.metalava.config.ApiFlagsConfig
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
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
                            isExported = true,
                            mutability = MUTABLE,
                            status = DISABLED,
                        ),
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag2",
                            isExported = true,
                            mutability = IMMUTABLE,
                            status = DISABLED,
                        ),
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag3",
                            isExported = true,
                            mutability = MUTABLE,
                            status = ENABLED,
                        ),
                        ApiFlagConfig(
                            pkg = "test.pkg",
                            name = "flag4",
                            isExported = false,
                            mutability = IMMUTABLE,
                            status = ENABLED,
                        ),
                    ),
            )

        val apiFlags = ApiFlagsCreator.createFromConfig(apiFlagsConfig)!!

        val expected =
            listOf(
                ApiFlag("test.pkg.flag1", ApiFlagAction.KEEP),
                ApiFlag("test.pkg.flag2", ApiFlagAction.REVERT),
                ApiFlag("test.pkg.flag3", ApiFlagAction.KEEP),
                ApiFlag("test.pkg.flag4", ApiFlagAction.FINALIZE, false),
            )
        assertEquals(expected, apiFlags.allFlags.toList())
    }

    @Test
    fun `Test null config`() {
        val apiFlags = ApiFlagsCreator.createFromConfig(null)
        assertNull(apiFlags)
    }
}
