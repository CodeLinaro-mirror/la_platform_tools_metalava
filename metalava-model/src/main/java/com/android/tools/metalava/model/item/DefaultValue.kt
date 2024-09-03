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
import com.android.tools.metalava.model.FixedDefaultValue
import com.android.tools.metalava.model.ParameterItem

/**
 * A lamda that given a [ParameterItem] will create a [DefaultValue] for it.
 *
 * This is called from within the constructor of the [ParameterItem] and should not access any
 * properties of [ParameterItem] as they may not have been initialized. This should just store a
 * reference for later use.
 */
typealias DefaultValueFactory = (ParameterItem) -> DefaultValue

/**
 * Represents a parameter's default value.
 *
 * TODO: Investigate this abstraction to see if it matches what we need. It is a little confusing as
 *   [hasDefaultValue] and [isDefaultValueKnown] seem like they should be different but are
 *   implemented in Psi and Turbine to be identical.
 */
interface DefaultValue {

    /** A [DefaultValue] to use for a parameter that has no default value. */
    @Suppress("ConvertObjectToDataObject") // Requires language level 1.9
    object NONE : DefaultValue {
        override fun hasDefaultValue() = false

        override fun isDefaultValueKnown() = false

        override fun value() = error("cannot call on NONE DefaultValue")

        /** This is suitable for use by [parameter] as it has no model or codebase dependencies. */
        override fun duplicate(parameter: ParameterItem) = this

        /** This is suitable for use in the snapshot as it has no model or codebase dependencies. */
        override fun snapshot(parameter: ParameterItem) = this

        override fun toString() = "NONE"
    }

    /**
     * A [DefaultValue] to use for a parameter that has a default value but its actual value is not
     * known.
     */
    @Suppress("ConvertObjectToDataObject") // Requires language level 1.9
    object UNKNOWN : DefaultValue {
        override fun hasDefaultValue() = true

        override fun isDefaultValueKnown() = false

        override fun value() = error("cannot call on UNKNOWN DefaultValue")

        /** This is suitable for use by [parameter] as it has no model or codebase dependencies. */
        override fun duplicate(parameter: ParameterItem) = this

        /** This is suitable for use in the snapshot as it has no model or codebase dependencies. */
        override fun snapshot(parameter: ParameterItem) = this

        override fun toString() = "UNKNOWN"
    }

    companion object {
        /** Get a [DefaultValue] wrapper around a fixed [String] value. */
        fun fixedDefaultValue(value: String?): DefaultValue {
            return FixedDefaultValue(value)
        }
    }

    /**
     * Returns whether this parameter has a default value. In Kotlin, this is supported directly; in
     * Java, it's supported via a special annotation, {@literal @DefaultValue("source"). This does
     * not necessarily imply that the default value is accessible, and we know the body of the
     * default value.
     *
     * @see isDefaultValueKnown
     */
    fun hasDefaultValue(): Boolean

    /**
     * Returns whether this parameter has an accessible default value that we plan to keep. This is
     * a superset of [hasDefaultValue] - if we are not writing the default values to the signature
     * file, then the default value might not be available, even though the parameter does have a
     * default.
     *
     * @see hasDefaultValue
     */
    fun isDefaultValueKnown(): Boolean

    /**
     * Returns the default value.
     *
     * **This method should only be called if [isDefaultValueKnown] returned true!** (This is
     * necessary since the null return value is a valid default value separate from no default value
     * specified.)
     *
     * The default value is the source string literal representation of the value, e.g. strings
     * would be surrounded by quotes, Booleans are the strings "true" or "false", and so on.
     */
    fun value(): String?

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
            !isDefaultValueKnown() -> UNKNOWN
            else -> fixedDefaultValue(value())
        }
}
