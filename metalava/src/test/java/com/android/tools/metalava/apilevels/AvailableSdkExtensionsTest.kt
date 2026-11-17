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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.SdkExtension
import kotlin.test.assertEquals
import org.junit.Test

/** Tests for [AvailableSdkExtensions.findSdkExtensionsForFloor]. */
class AvailableSdkExtensionsTest {

    private val availableSdkExtensions =
        AvailableSdkExtensions(
            setOf(
                SdkExtension.fromXmlAttributes(30, "R-ext", "R Extensions", "R"),
                SdkExtension.fromXmlAttributes(31, "S-ext", "S Extensions", "S"),
                SdkExtension.fromXmlAttributes(33, "T-ext", "T Extensions", "TIRAMISU"),
                SdkExtension.fromXmlAttributes(34, "U-ext", "U Extensions", "UPSIDE_DOWN_CAKE"),
                SdkExtension.fromXmlAttributes(
                    1000000,
                    "AD_SERVICES-ext",
                    "Ad Services Extensions",
                    "AD_SERVICES",
                ),
            )
        )

    /** Check that [floor] matches the SDK extensions with [expectedShortNames]. */
    private fun checkFloor(floor: Int, vararg expectedShortNames: String) {
        assertEquals(
            expectedShortNames.toList(),
            availableSdkExtensions.findSdkExtensionsForFloor(floor).map { it.shortname },
        )
    }

    @Test
    fun `Test dessert floor matches that dessert and all later desserts`() {
        checkFloor(30, "R-ext", "S-ext", "T-ext", "U-ext")
        checkFloor(31, "S-ext", "T-ext", "U-ext")
        checkFloor(33, "T-ext", "U-ext")
        checkFloor(34, "U-ext")
    }

    @Test
    fun `Test undeclared dessert floor matches later desserts without error`() {
        // There is no S_V2 (32) extension, so it matches the next declared extensions.
        checkFloor(32, "T-ext", "U-ext")
        // Floors before the first extension match all dessert extensions.
        checkFloor(29, "R-ext", "S-ext", "T-ext", "U-ext")
    }

    @Test
    fun `Test dessert floor after the newest declared extension matches nothing`() {
        checkFloor(35)
        checkFloor(999999)
    }

    @Test
    fun `Test dessert release independent floor only matches itself`() {
        checkFloor(1000000, "AD_SERVICES-ext")
        checkFloor(1000001)
    }
}
