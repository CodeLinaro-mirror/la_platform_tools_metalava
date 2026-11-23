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

package com.android.tools.metalava.model.testing.api

import com.android.tools.metalava.model.api.ApiSurfaceSelector
import kotlin.test.assertEquals

/**
 * Assert that the state of this [ApiSurfaceSelector] is as expected.
 *
 * @param expectedMatcherState the expected toString of the underlying annotation matcher.
 * @param expectedShowUnannotated the expected value of [ApiSurfaceSelector.showUnannotated].
 * @param expectedUnannotatedSurfaceName the expected name of
 *   [ApiSurfaceSelector.unannotatedApiSurface].
 */
fun ApiSurfaceSelector.assertState(
    expectedMatcherState: String,
    expectedShowUnannotated: Boolean,
    expectedUnannotatedSurfaceName: String?,
) {
    assertEquals(expectedMatcherState.trimIndent(), matcher.toString(), message = "matcher state")
    assertEquals(expectedShowUnannotated, showUnannotated, message = "show unannotated")
    assertEquals(
        expectedUnannotatedSurfaceName,
        unannotatedApiSurface?.name,
        message = "unannotated surface name"
    )
}
