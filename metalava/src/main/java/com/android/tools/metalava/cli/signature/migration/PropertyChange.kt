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

package com.android.tools.metalava.cli.signature.migration

import com.android.tools.metalava.model.text.CustomizableProperty
import com.android.tools.metalava.model.text.FileFormat

/** Represents a change in [property] from [oldValue] to [newValue]. */
data class PropertyChange<T>(
    val property: CustomizableProperty<T>,
    val oldValue: T,
    val newValue: T,
) {
    /** Set [property] in [builder] to [oldValue]. */
    fun setOldValueIn(builder: FileFormat.Builder) {
        builder[property] = oldValue
    }

    /** Set [property] in [builder] to [newValue]. */
    fun setNewValueIn(builder: FileFormat.Builder) {
        builder[property] = newValue
    }

    /** Format this [T] as a [String] for use in [describe]. */
    private fun T.asString() =
        (this ?: property.defaultValue)?.let { value -> property.valueToString(value) }
            ?: "<not-set>"

    /** Describe this change. */
    fun describe() =
        "Change '${property.propertyName}' from '${oldValue.asString()}' to '${newValue.asString()}'"
}
