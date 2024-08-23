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

import com.android.tools.metalava.model.item.DefaultValue

/** Encapsulates information about a fixed default value. */
internal class FixedDefaultValue(private val value: String?) : DefaultValue {

    /** This is always true as the text model will use [DefaultValue.NONE] for no value. */
    override fun hasDefaultValue() = true

    /** This is always true as the text model will use [DefaultValue.UNKNOWN] for no value. */
    override fun isDefaultValueKnown() = true

    override fun value() = value

    override fun toString() = "DefaultValue($value)"
}
