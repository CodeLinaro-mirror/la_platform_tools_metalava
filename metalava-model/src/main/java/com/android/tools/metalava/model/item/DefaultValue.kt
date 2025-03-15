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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ParameterItem

/**
 * A lamda that given a [ParameterItem] will create a [DefaultValue] for it.
 *
 * This is called from within the constructor of the [ParameterItem] and should not access any
 * properties of [ParameterItem] as they may not have been initialized. This should just store a
 * reference for later use.
 */
typealias DefaultValueFactory = (ParameterItem) -> DefaultValue

/** Indicates whether a parameter has a default value. */
interface DefaultValue {

    /** A [DefaultValue] to use for a parameter that has no default value. */
    data object NONE : DefaultValue {
        override fun hasDefaultValue() = false

        /** This is suitable for use by [parameter] as it has no model or codebase dependencies. */
        override fun duplicate(parameter: ParameterItem) = this

        /** This is suitable for use in the snapshot as it has no model or codebase dependencies. */
        override fun snapshot(parameter: ParameterItem) = this
    }

    /**
     * A [DefaultValue] to use for a parameter that has a default value but its actual value is not
     * known.
     */
    data object UNKNOWN : DefaultValue {
        override fun hasDefaultValue() = true

        /** This is suitable for use by [parameter] as it has no model or codebase dependencies. */
        override fun duplicate(parameter: ParameterItem) = this

        /** This is suitable for use in the snapshot as it has no model or codebase dependencies. */
        override fun snapshot(parameter: ParameterItem) = this
    }

    /**
     * Returns whether this parameter has a default value.
     *
     * This is only supported in Kotlin.
     */
    fun hasDefaultValue(): Boolean

    /**
     * Return a duplicate of this instance to use by [parameter] which will be in the same type of
     * [Codebase] as this.
     */
    fun duplicate(parameter: ParameterItem): DefaultValue

    /**
     * Creates a snapshot of this.
     *
     * The default implementation assumes that this is either dependent on a model or the codebase
     * and so creates a new [DefaultValue] based on the functions above.
     */
    fun snapshot(parameter: ParameterItem) =
        when {
            !hasDefaultValue() -> NONE
            else -> UNKNOWN
        }
}
