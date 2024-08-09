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

    /** Create a duplicate of this for the specified [Item]. */
    fun duplicate(item: Item): ApiVariantSelectors

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

        override fun duplicate(item: Item): ApiVariantSelectors = this

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

        /**
         * The status of the properties, i.e. whether they have been set/initialized and their value
         * if they have.
         */
        private var flags: Int = 0

        /**
         * Get the value of a property from [flags], initializing it if it has not yet been set.
         *
         * The property is determined by the [setFlag] and [valueFlag] parameters
         *
         * @param setFlag the bit mask in [flags] which indicates whether the associated property's
         *   value has been set.
         * @param valueFlag the bit mask in [flags] containing the value of the associated property
         *   if and only if it has been set, i.e. [setFlag] is set.
         */
        inline fun lazyGet(setFlag: Int, valueFlag: Int, initializer: () -> Boolean): Boolean =
            if ((flags and setFlag) == 0) {
                val result = initializer()
                flags = flags or setFlag
                if (result) flags = flags or valueFlag
                result
            } else (flags and valueFlag) != 0

        /**
         * Set the value of a property in [flags], skipping initializing it that has not already
         * been done.
         *
         * The property is determined by the [setFlag] and [valueFlag] parameters
         *
         * @param setFlag the bit mask in [flags] which indicates whether the associated property's
         *   value has been set.
         * @param valueFlag the bit mask in [flags] in which the value of the associated property
         *   will be stored.
         * @param value the value of the property.
         */
        fun lazySet(setFlag: Int, valueFlag: Int, value: Boolean) {
            flags =
                (flags or setFlag).let { intermediate ->
                    if (value) {
                        intermediate or valueFlag
                    } else {
                        intermediate and valueFlag.inv()
                    }
                }
        }

        override val originallyHidden: Boolean
            get() =
                lazyGet(ORIGINALLY_HIDDEN_HAS_BEEN_SET, ORIGINALLY_HIDDEN_VALUE) {
                    item.documentation.isHidden || item.hasHideAnnotation()
                }

        override var hidden: Boolean
            get() =
                lazyGet(HIDDEN_HAS_BEEN_SET, HIDDEN_VALUE) {
                    originallyHidden && !item.hasShowAnnotation()
                }
            set(value) {
                lazySet(HIDDEN_HAS_BEEN_SET, HIDDEN_VALUE, value)
            }

        override var docOnly: Boolean
            get() = lazyGet(DOCONLY_HAS_BEEN_SET, DOCONLY_VALUE) { item.documentation.isDocOnly }
            set(value) {
                lazySet(DOCONLY_HAS_BEEN_SET, DOCONLY_VALUE, value)
            }

        override var removed: Boolean
            get() = lazyGet(REMOVED_HAS_BEEN_SET, REMOVED_VALUE) { item.documentation.isRemoved }
            set(value) {
                lazySet(REMOVED_HAS_BEEN_SET, REMOVED_VALUE, value)
            }

        override fun duplicate(item: Item): ApiVariantSelectors = Mutable(item)

        companion object {
            /**
             * The number of bits in [flags] that is needed for each property.
             *
             * The bits are as follows:
             * 1. If set then the value of the property has been set, either by the initializer when
             *    first read, or explicitly by the setter, if there is one. If unset then the value
             *    of the property has not yet been set.
             * 2. This is only valid if the preceding bit has been set in which case this is the
             *    value of the property.
             */
            private const val BITS_PER_PROPERTY = 2

            // `originallyHidden` related constants
            private const val ORIGINALLY_HIDDEN_OFFSET: Int = 0
            private const val ORIGINALLY_HIDDEN_HAS_BEEN_SET: Int = 1 shl ORIGINALLY_HIDDEN_OFFSET
            private const val ORIGINALLY_HIDDEN_VALUE: Int = 1 shl (ORIGINALLY_HIDDEN_OFFSET + 1)

            // `hidden` related constants
            private const val HIDDEN_OFFSET: Int = ORIGINALLY_HIDDEN_OFFSET + BITS_PER_PROPERTY
            private const val HIDDEN_HAS_BEEN_SET: Int = 1 shl HIDDEN_OFFSET
            private const val HIDDEN_VALUE: Int = 1 shl (HIDDEN_OFFSET + 1)

            // `docOnly` related constants
            private const val DOCONLY_OFFSET: Int = HIDDEN_OFFSET + BITS_PER_PROPERTY
            private const val DOCONLY_HAS_BEEN_SET: Int = 1 shl DOCONLY_OFFSET
            private const val DOCONLY_VALUE: Int = 1 shl (DOCONLY_OFFSET + 1)

            // `removed` related constants
            private const val REMOVED_OFFSET: Int = DOCONLY_OFFSET + BITS_PER_PROPERTY
            private const val REMOVED_HAS_BEEN_SET: Int = 1 shl REMOVED_OFFSET
            private const val REMOVED_VALUE: Int = 1 shl (REMOVED_OFFSET + 1)
        }
    }
}
