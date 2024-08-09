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

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A factory that will create an [ApiVariantSelectors] for a specific [Item]. */
typealias ApiVariantSelectorsFactory = (Item) -> ApiVariantSelectors

/** Contains properties that select which, if any, variant of an API an [Item] belongs in. */
sealed interface ApiVariantSelectors {
    /**
     * Indicates whether the item was explicitly hidden in the source, e.g. via an `@hide` javadoc
     * tag in its [Item.documentation], or a hide annotation directly on the [Item].
     */
    val originallyHidden: Boolean

    /**
     * Indicates whether the [Item] should be hidden, i.e. should not be included in ANY API surface
     * variant.
     *
     * Initially set to [originallyHidden] but updated due to inheritance.
     */
    var hidden: Boolean

    /**
     * Indicates whether the [Item] should be included in the doc only API surface variant.
     *
     * Initially set to `true` if the [Item.documentation] contains `@doconly` but updated due to
     * inheritance.
     */
    var docOnly: Boolean

    /**
     * Indicates whether the [Item] should be in the removed API surface variant.
     *
     * Initially set to `true` if the [Item.documentation] contains `@removed` but updated due to
     * inheritance.
     */
    var removed: Boolean

    companion object {
        /**
         * An [ApiVariantSelectors] factory that will always return an immutable
         * [ApiVariantSelectors]. It will return `false` for all the properties and throw an error
         * on any attempt to set a property.
         */
        val IMMUTABLE_FACTORY: ApiVariantSelectorsFactory = { Immutable }

        /**
         * An [ApiVariantSelectors] factory that will return a new, mutable, [ApiVariantSelectors]
         * for each [Item].
         */
        val MUTABLE_FACTORY: ApiVariantSelectorsFactory = { Mutable(it) }
    }

    /**
     * An immutable [ApiVariantSelectors] that will return `false` for all the properties and fail
     * on any attempt to set the `var` properties.
     */
    @Suppress("ConvertObjectToDataObject") // Requires language level 1.9
    private object Immutable : ApiVariantSelectors {

        override val originallyHidden: Boolean
            get() = false

        override var hidden: Boolean
            get() = false
            set(value) {
                error("Cannot set `hidden` to $value")
            }

        override var docOnly: Boolean
            get() = false
            set(value) {
                error("Cannot set `docOnly` to $value")
            }

        override var removed: Boolean
            get() = false
            set(value) {
                error("Cannot set `removed` to $value")
            }

        override fun toString() = "Immutable"
    }

    /**
     * A mutable [ApiVariantSelectors].
     *
     * [originallyHidden] will be `true` if it's [item]'s documentation contains one of `@hide`,
     * `@pending` or `@suppress` or its [Item] has a hide annotation associated with it.
     *
     * Unless [hidden] is written before reading then it will default to `true` if
     * [originallyHidden] is `true` and it does not have any show annotations.
     *
     * [docOnly] will be initialized to `true` if it's [item]'s documentation contains `@doconly`.
     *
     * [removed] will be initialized to `true` if it's [item]'s documentation contains `@removed`.
     */
    private class Mutable(private val item: Item) : ApiVariantSelectors {

        override val originallyHidden by
            lazy(LazyThreadSafetyMode.NONE) {
                val documentation = item.documentation
                documentation.contains('@') &&
                    (documentation.contains("@hide") ||
                        documentation.contains("@pending") ||
                        // KDoc:
                        documentation.contains("@suppress")) || item.hasHideAnnotation()
            }

        override var hidden: Boolean by LazyDelegate {
            originallyHidden && !item.hasShowAnnotation()
        }

        override var docOnly = item.documentation.contains("@doconly")

        override var removed = item.documentation.contains("@removed")
    }
}

// a property with a lazily calculated default value
internal class LazyDelegate<T>(val defaultValueProvider: () -> T) : ReadWriteProperty<Any, T> {
    private var currentValue: T? = null

    override operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        currentValue = value
    }

    override operator fun getValue(thisRef: Any, property: KProperty<*>): T {
        if (currentValue == null) {
            currentValue = defaultValueProvider()
        }

        return currentValue!!
    }
}
