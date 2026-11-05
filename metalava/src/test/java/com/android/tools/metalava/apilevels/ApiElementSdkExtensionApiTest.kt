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

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Tests for [ApiElement.sdkExtensionApi] and how [ApiHistoryUpdater] updates it. */
class ApiElementSdkExtensionApiTest {

    private val api33 = ApiHistoryUpdater.forApiVersion(ApiVersion.fromLevel(33))
    private val api34 = ApiHistoryUpdater.forApiVersion(ApiVersion.fromLevel(34))

    /** Create an [ApiHistoryUpdater] for the SDK extension version [extVersion]. */
    private fun extUpdater(extVersion: Int) =
        ApiHistoryUpdater.forExtVersion(
            nextSdkVersion = ApiVersion.fromLevel(35),
            extVersion = ExtVersion.fromLevel(extVersion),
            module = "module",
            isLatestExtVersion = false,
        )

    @Test
    fun `Test unannotated element has no floor`() {
        val element = ApiElement("foo()V")
        api33.updateSdkExtensionApi(element, null)
        extUpdater(10).updateSdkExtensionApi(element, null)
        assertNull(element.sdkExtensionApi)
    }

    @Test
    fun `Test null floor does not clear an existing floor`() {
        val element = ApiElement("foo()V")
        api34.updateSdkExtensionApi(element, 33)
        api34.updateSdkExtensionApi(element, null)
        assertEquals(33, element.sdkExtensionApi)
    }

    @Test
    fun `Test floor from a later version replaces an earlier floor`() {
        // Versions are loaded from oldest to newest, so the floor from the newest version is used.
        val element = ApiElement("foo()V")
        api33.updateSdkExtensionApi(element, 33)
        api34.updateSdkExtensionApi(element, 34)
        assertEquals(34, element.sdkExtensionApi)

        extUpdater(10).updateSdkExtensionApi(element, 31)
        assertEquals(31, element.sdkExtensionApi)

        extUpdater(11).updateSdkExtensionApi(element, 33)
        assertEquals(33, element.sdkExtensionApi)
    }

    @Test
    fun `Test enum methods do not have an explicit floor`() {
        val api = Api(useInternalNames = true)
        api34.update(api)
        val enumClass = api.updateClass("test/pkg/MyEnum", api34, false, isEnum = true)
        api34.updateSdkExtensionApi(enumClass, 33)
        api.clean()

        // The compiler generated enum methods do not have an explicit floor so inherit the floor
        // of their class when generating the `sdks` attribute, like any other member.
        val methods = enumClass.methods.associate { it.name to it.sdkExtensionApi }
        assertEquals(
            mapOf(
                "valueOf(Ljava/lang/String;)Ltest/pkg/MyEnum;" to null,
                "values()[Ltest/pkg/MyEnum;" to null,
            ),
            methods,
        )
    }
}
