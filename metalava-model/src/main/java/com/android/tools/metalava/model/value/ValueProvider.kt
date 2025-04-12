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

/**
 * Allows the creation of [Value] to be deferred until they are requested.
 *
 * This is needed for a number of reasons:
 * * The [Value] model and support for it in the various model implementations is a work in progress
 *   and as such it will not support all values for a while. That may mean it throws an exception or
 *   returns `null` or an invalid [Value]. Inlining that work during the normal [Codebase]
 *   construction would break everything. Deferring the creation ensures that any issues only arise
 *   when they are requested, i.e. testing during development.
 * * The `Psi` model is quite slow and creating [Value]s can be costly as it has to check to
 *   evaluate expressions to see if they are constant. That is not true for `Turbine` as it
 *   evaluates any constant expressions up front.
 * * The `Text` model requires creating a [Value] from a string and while that may not be
 *   particularly expensive it is still wasted time if the [Value] is not needed.
 */
interface ValueProvider {
    /** Get the value, creating it if necessary. */
    val value: Value

    companion object {

        /**
         * Create a [ValueProvider] that will create a [Value] from [text].
         *
         * TODO(b/354633349): Pass in [Codebase] to allow creation of [Value]s which reference items
         *   in it.
         */
        fun fromText(text: String): ValueProvider = FromStringValueProvider(text)

        /** A temporary [ValueProvider] which throws an error when called. */
        val UNSUPPORTED =
            object : ValueProvider {
                override val value: Value
                    get() = throw ValueProviderException("value provider is not yet supported")
            }
    }

    /**
     * A [ValueProvider] that will create a [Value] from a [String].
     *
     * TODO(b/354633349): Implement, using the same parser as the text model uses for
     *   FieldItem.initialValue and MethodItem.defaultValue.
     */
    private class FromStringValueProvider(private val text: String) : ValueProvider {
        override val value: Value
            get() = throw ValueProviderException("Could not parse value from `$text`")
    }
}

/** Like [ValueProvider] but allows a `null` [Value] to be returned. */
interface OptionalValueProvider {
    val optionalValue: Value?

    companion object {
        /** A temporary [OptionalValueProvider] which always returns `null` when called. */
        val NO_VALUE =
            object : OptionalValueProvider {
                override val optionalValue: Value?
                    get() = null
            }
    }
}

/**
 * A special [RuntimeException] that indicates a problem with a [ValueProvider].
 *
 * These exceptions will be ignored by [Value] tests during development of the [Value] model to
 * avoid having to keep updating the baseline files which become a source of conflicts when changed
 * frequently.
 *
 * TODO(b/354633349): Stop ignoring exceptions.
 */
class ValueProviderException(message: String) : RuntimeException(message)
