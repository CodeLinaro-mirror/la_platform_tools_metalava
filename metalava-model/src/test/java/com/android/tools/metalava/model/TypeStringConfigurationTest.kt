/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model

import kotlin.test.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeStringConfigurationTest {
    @Test
    fun `Test isDefault on DEFAULT`() {
        // Make sure that isDefault is `true` on DEFAULT.
        assertTrue(TypeStringConfiguration.DEFAULT.isDefault)
    }

    @Test
    fun `Test isDefault on TypeStringConfiguration()`() {
        assertTrue(TypeStringConfiguration().isDefault)
    }

    @Test
    fun `Test isDefault on TypeStringConfiguration(annotations=true)`() {
        assertFalse(TypeStringConfiguration(annotations = true).isDefault)
    }
}
