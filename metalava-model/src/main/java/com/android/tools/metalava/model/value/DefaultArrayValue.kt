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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.Codebase

internal class DefaultArrayValue(
    override val elements: List<ArrayElementValue>,
    private val wasUnwrappedInSource: Boolean,
) : DefaultValue(), ArrayValue {
    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        @Suppress("DEPRECATION")
        if (
            wasUnwrappedInSource &&
                configuration.singleArrayElementFormat == SingleArrayElementFormat.SOURCE
        ) {
            configuration.appendNestedValueTo(builder, elements[0])
        } else super.appendValueStringTo(builder, configuration)
    }

    override fun snapshot(targetCodebase: Codebase): ArrayValue {
        if (elements.isEmpty()) return this
        val snapshotElements = elements.map { it.snapshot(targetCodebase) }
        return Value.createArrayValue(snapshotElements, wasUnwrappedInSource)
    }
}
