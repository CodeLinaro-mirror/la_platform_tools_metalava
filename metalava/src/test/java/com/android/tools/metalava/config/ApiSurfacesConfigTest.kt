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

package com.android.tools.metalava.config

import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiSurfacesConfigTest {
    @Test
    fun `Test invalid extends`() {
        val apiSurfacesConfig =
            ApiSurfacesConfig(
                apiSurfaceList =
                    listOf(
                        ApiSurfaceConfig(name = "public"),
                        ApiSurfaceConfig(name = "system", extends = "other")
                    ),
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { apiSurfacesConfig.orderedSurfaces }
        assertEquals(
            "Surface `system` extends an unknown surface `other`, expected one of `public`, `system`",
            exception.message
        )
    }

    /**
     * Check that [ApiSurfacesConfig.contributesTo] returns the expected result.
     *
     * @param name The name of the surface whose contributes are to be checked.
     * @param expectedSurfaces The list of expected surface names.
     */
    private fun ApiSurfacesConfig.assertContributesTo(
        name: String,
        expectedSurfaces: List<String>
    ) {
        val surfaceConfig = getByNameOrError(name) { "unknown `$it`" }
        assertEquals(
            expectedSurfaces,
            contributesTo(surfaceConfig).map { it.name },
            "contributions to $name"
        )
    }

    @Test
    fun `Test contributesTo`() {
        val apiSurfacesConfig =
            ApiSurfacesConfig(
                apiSurfaceList =
                    listOf(
                        ApiSurfaceConfig(name = "test", extends = "system"),
                        ApiSurfaceConfig(name = "module-lib", extends = "system"),
                        ApiSurfaceConfig(name = "public"),
                        ApiSurfaceConfig(name = "system", extends = "public"),
                    ),
            )

        apiSurfacesConfig.assertContributesTo(
            "public",
            listOf(
                "public",
            )
        )

        apiSurfacesConfig.assertContributesTo(
            "system",
            listOf(
                "public",
                "system",
            )
        )

        apiSurfacesConfig.assertContributesTo(
            "test",
            listOf(
                "public",
                "system",
                "test",
            )
        )

        apiSurfacesConfig.assertContributesTo(
            "module-lib",
            listOf(
                "public",
                "system",
                "module-lib",
            )
        )
    }
}
