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

package com.android.tools.metalava.model.annotation

import com.android.tools.metalava.model.value.Value

/** The set of defaults for an annotation. */
class AnnotationDefaults(private val defaultsByName: Map<String, Value>) {
    /**
     * Apply the defaults to [nameToValue].
     *
     * Returns a [Map] that includes [nameToValue] plus a default [Value] for any name in
     * [defaultsByName] that does not have a [Value] in [nameToValue].
     */
    fun apply(nameToValue: Map<String, Value>): Map<String, Value> {
        if (defaultsByName.isEmpty()) return nameToValue

        return buildMap {
            putAll(nameToValue)
            for ((name, value) in defaultsByName) {
                if (name !in this) put(name, value)
            }
        }
    }

    companion object {
        val EMPTY = AnnotationDefaults(emptyMap())
    }
}
