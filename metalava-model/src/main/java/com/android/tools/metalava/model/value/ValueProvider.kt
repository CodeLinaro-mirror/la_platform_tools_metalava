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
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

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
}

/** Return a provider for this [Value]. */
fun Value.provider(): ValueProvider = FixedValueProvider(this)

/** A [ValueProvider] that simply returns [value]. */
private class FixedValueProvider(override val value: Value) : ValueProvider

/** Like [ValueProvider] but allows a `null` [Value] to be returned. */
interface OptionalValueProvider {
    val optionalValue: Value?
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

/** A combination of both [ValueProvider] and [OptionalValueProvider]. */
interface CombinedValueProvider : ValueProvider, OptionalValueProvider

/**
 * A [CombinedValueProvider] that provides support to subclasses for caching a [Value] that has been
 * provided.
 *
 * @param valueUseSite the [ValueUseSite] for which this will provide a [Value].
 */
abstract class BaseCachingValueProvider(protected val valueUseSite: ValueUseSite) :
    CombinedValueProvider {
    /** The cached value. */
    private lateinit var _value: Optional<Value>

    /** Get the cached value, calling [provideValue] if it has not yet been cached. */
    private fun cachedValue(): Optional<Value> {
        if (!::_value.isInitialized) {
            val providedValue = provideValue()
            val valueToCache =
                when (valueUseSite) {
                    ValueUseSite.ANNOTATION ->
                        providedValue
                            ?: error(
                                "Provider returned `null` but nulls are not allowed on annotation values"
                            )
                    ValueUseSite.FIELD -> providedValue?.asLiteralValue()
                }

            _value = Optional.ofNullable(valueToCache)
        }
        return _value
    }

    final override val value: Value
        get() = cachedValue().getOrNull() ?: error("No value provided")

    final override val optionalValue: Value?
        get() = cachedValue().getOrNull()

    /** Provide an optional [Value] to be cached. */
    protected abstract fun provideValue(): Value?
}
