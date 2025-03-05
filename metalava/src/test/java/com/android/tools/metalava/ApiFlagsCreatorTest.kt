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
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.api.flags.ApiFlag
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiFlagsCreatorTest {
    @Test
    fun `Test creation from config and --revert-annotation values`() {
        val apiFlagsConfig = ApiFlagsConfig(flags = emptyList())

        val exception =
            assertThrows(IllegalStateException::class.java) {
                ApiFlagsCreator.create(listOf(ANDROID_FLAGGED_API), apiFlagsConfig)
            }

        assertEquals(
            "Cannot provide non-empty revertAnnotations and non-null apiFlags",
            exception.message
        )
    }

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

        val apiFlags = ApiFlagsCreator.create(emptyList(), apiFlagsConfig)

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
    fun `Test empty --revert-annotation value list`() {
        val apiFlags = ApiFlagsCreator.create(emptyList(), null)
        assertNull(apiFlags)
    }

    @Test
    fun `Test invalid --revert-annotation value`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                ApiFlagsCreator.create(listOf(ANDROID_SYSTEM_API), null)
            }
        assertEquals(
            "Unexpected --revert-annotation: android.annotation.SystemApi",
            exception.message
        )
    }

    @Test
    fun `Test revert all`() {
        val apiFlags = ApiFlagsCreator.create(listOf(ANDROID_FLAGGED_API), null)
        assertNotNull(apiFlags)
        assertEquals(emptyMap(), apiFlags.byQualifiedName)
    }

    @Test
    fun `Test finalize some, revert others`() {
        val flagName = "test.pkg.flags.flag_name"
        val apiFlags =
            ApiFlagsCreator.create(
                listOf(ANDROID_FLAGGED_API, """!$ANDROID_FLAGGED_API("$flagName")"""),
                null,
            )
        assertNotNull(apiFlags)
        assertEquals(ApiFlag.FINALIZE_FLAGGED_API, apiFlags[flagName])
        assertEquals(ApiFlag.REVERT_FLAGGED_API, apiFlags["test.pkg.flags.other_flag_name"])
    }
}
