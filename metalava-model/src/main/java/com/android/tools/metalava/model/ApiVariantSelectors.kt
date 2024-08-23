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

import com.android.tools.metalava.reporter.Issues

/** A factory that will create an [ApiVariantSelectors] for a specific [Item]. */
typealias ApiVariantSelectorsFactory = (Item) -> ApiVariantSelectors

/** Contains properties that select which, if any, variant of an API an [Item] belongs in. */
sealed class ApiVariantSelectors {
    /**
     * Indicates whether the item was explicitly hidden in the source, e.g. via an `@hide` javadoc
     * tag in its [Item.documentation], or a hide annotation directly on the [Item].
     */
    abstract val originallyHidden: Boolean

    /**
     * Indicates whether children of an [Item] should be hidden, i.e. should not be included in ANY
     * API surface variant.
     *
     * Initially set to [originallyHidden] but updated due to inheritance.
     */
    internal abstract val inheritableHidden: Boolean

    /**
     * Indicates whether the [Item] is accessible, and its enclosing classes are accessible.
     *
     * An [Item] is accessible if it is either `public` or `protected`. In Kotlin it is also
     * accessible if it is `internal` as long as it is annotated with `@PublishedApi`. However, that
     * annotation is not treated specially in Metalava, instead it relies on the user to specify
     * `@PublishedApi` as a show annotation and this just assumes that any show annotation is enough
     * to make it accessible.
     */
    abstract val accessible: Boolean

    /**
     * Indicates whether the [Item] should be hidden, i.e. should not be included in ANY API surface
     * variant.
     *
     * Initially set to [inheritableHidden] but updated due to show annotations.
     */
    abstract val hidden: Boolean

    /**
     * Indicates whether the [Item] should be included in the doc only API surface variant.
     *
     * Initially set to `true` if the [Item.documentation] contains `@doconly` but updated due to
     * inheritance.
     */
    abstract val docOnly: Boolean

    /**
     * Indicates whether the [Item] should be in the removed API surface variant.
     *
     * Initially set to `true` if the [Item.documentation] contains `@removed` but updated due to
     * inheritance.
     */
    abstract val removed: Boolean

    /** Determines whether this item will be shown as part of the API or not. */
    abstract val showability: Showability

    /** Create a duplicate of this for the specified [Item]. */
    abstract fun duplicate(item: Item): ApiVariantSelectors

    /**
     * Update the mutable properties of this by inheriting state from the parent selectors, if
     * available.
     */
    abstract fun inheritInto()

    companion object {
        /**
         * An [ApiVariantSelectors] factory that will always return an immutable
         * [ApiVariantSelectors]. It will return `false` for all the properties and throw an error
         * on any attempt to set a property.
         */
        val IMMUTABLE_FACTORY: ApiVariantSelectorsFactory = { Immutable }

        /**
         * An [ApiVariantSelectors] factory that will return a new, mutable, [ApiVariantSelectors]
         * for each [SelectableItem].
         *
         * This cannot be used on an [Item] that is not a [SelectableItem], use [IMMUTABLE_FACTORY]
         * instead.
         */
        val MUTABLE_FACTORY: ApiVariantSelectorsFactory = {
            if (it is SelectableItem) Mutable(it)
            else error("Cannot create Mutable for non-SelectableItem, use Immutable instead")
        }
    }

    /**
     * An immutable [ApiVariantSelectors] that will return `false` for all the properties and fail
     * on any attempt to set the `var` properties.
     */
    @Suppress("ConvertObjectToDataObject") // Requires language level 1.9
    private object Immutable : ApiVariantSelectors() {

        override val originallyHidden: Boolean
            get() = false

        override val inheritableHidden: Boolean
            get() = false

        /**
         * Defaults to `true` as this is used by `Item`s loaded from an API signature file which
         * typically only contains accessible `Item`s. It is possible that it could contain
         * inaccessible `Item`s but at the moment that is not supported.
         */
        override val accessible: Boolean
            get() = true

        override val hidden: Boolean
            get() = false

        override val docOnly: Boolean
            get() = false

        override var removed: Boolean
            get() = false
            set(value) {
                error("Cannot set `removed` to $value")
            }

        override val showability: Showability
            get() = Showability.NO_EFFECT

        override fun duplicate(item: Item): ApiVariantSelectors = this

        override fun inheritInto() = error("Cannot inheritInto() $this")

        override fun toString() = "Immutable"
    }

    /**
     * A mutable [ApiVariantSelectors].
     *
     * [originallyHidden] will be `true` if it's [item]'s documentation contains one of `@hide`,
     * `@pending` or `@suppress` or its [SelectableItem] has a hide annotation associated with it.
     *
     * Unless [hidden] is written before reading then it will default to `true` if
     * [originallyHidden] is `true` and it does not have any show annotations.
     *
     * [docOnly] will be initialized to `true` if it's [item]'s documentation contains `@doconly`.
     *
     * [removed] will be initialized to `true` if it's [item]'s documentation contains `@removed`.
     *
     * This uses bits in [propertyHasBeenSetBits] and [propertyValueBits] to handle lazy
     * initialization and store the value. The main purpose of using bit masks is not primarily
     * performance or to reduce storage (although keeping that down is a factor) but rather to
     * support lazy initialization with optional setters without duplicating lots of complicated
     * code.
     */
    private class Mutable(private val item: SelectableItem) : ApiVariantSelectors() {

        /**
         * Contains a bit for each lazy boolean property indicating whether it has been set, either
         * implicitly during initialization or explicitly via its setter.
         *
         * If a bit is set in here then the corresponding bit in [propertyValueBits] contains the
         * value.
         */
        private var propertyHasBeenSetBits = 0

        /**
         * Contains a bit for each lazy boolean property indicating its value.
         *
         * A bit in here represents the value of the property if and only if the corresponding bit
         * has been set in [propertyHasBeenSetBits]. Otherwise, the value of the bit is undefined.
         */
        private var propertyValueBits = 0

        /**
         * Get the value of a property from [propertyValueBits], initializing it if it has not yet
         * been set.
         *
         * @param propertyBitMask the bit mask in [propertyHasBeenSetBits] and [propertyValueBits]
         *   which indicates whether the associated property's value has been set and if so what its
         *   value is.
         * @param initialValueProvider a lambda which returns the initial value of the property if
         *   it has not yet been set.
         */
        private inline fun lazyGet(propertyBitMask: Int, initialValueProvider: () -> Boolean) =
            // Check to see if the property has been set first before accessing the value.
            if ((propertyHasBeenSetBits and propertyBitMask) == 0) {
                // The property has not been set so get the initial value and store it.
                val result = initialValueProvider()
                // Record that the property has been set.
                propertyHasBeenSetBits = propertyHasBeenSetBits or propertyBitMask
                // Record the value.
                if (result) propertyValueBits = propertyValueBits or propertyBitMask
                // Return the result.
                result
            } else {
                // The property has been set so get its value.
                (propertyValueBits and propertyBitMask) != 0
            }

        /**
         * Like [lazyGet] except that if the flag is not set it will invoke [inheritInto] if it has
         * not already been called. It will then check to see if the property has been set and if it
         * has then the value will be returned. Otherwise, it will invoke the [initialValueProvider]
         * just as [lazyGet] does.
         */
        private inline fun lazyGetAfterInherit(
            propertyBitMask: Int,
            initialValueProvider: () -> Boolean
        ): Boolean {
            if ((propertyHasBeenSetBits and propertyBitMask) == 0) {
                // The property has not been set so first call `inheritInto()` to give it a chance
                // to initialize the property. It will return immediately if it had nothing to do.
                inheritInto()

                // At this point inheritInfo may have been called and may have set the property,
                // but it also may not so check again and if it has not then set it to its initial
                // value.
                return lazyGet(propertyBitMask, initialValueProvider)
            } else {
                // The property has been set so return its value.
                return (propertyValueBits and propertyBitMask) != 0
            }
        }

        /**
         * Set the value of a property in [propertyValueBits], skipping initializing it that has not
         * already been done.
         *
         * @param propertyBitMask the bit mask in [propertyHasBeenSetBits] and [propertyValueBits]
         *   which indicates whether the associated property's value has been set and if so what its
         *   value is.
         * @param value the new value of the property.
         */
        private fun lazySet(propertyBitMask: Int, value: Boolean) {
            // Record that the property has been set.
            propertyHasBeenSetBits = propertyHasBeenSetBits or propertyBitMask
            if (value) {
                // The value is true so set the bit.
                propertyValueBits = propertyValueBits or propertyBitMask
            } else {
                // The value is false so clear the bit.
                propertyValueBits = propertyValueBits and propertyBitMask.inv()
            }
        }

        override val originallyHidden: Boolean
            get() =
                lazyGet(ORIGINALLY_HIDDEN_BIT_MASK) {
                    // The item is originally hidden if the javadoc contains @hide or similar, or
                    // it is tagged with a hide annotation. That is true even if the hide annotation
                    // is superseded by a show annotation.
                    item.documentation.isHidden || item.hasHideAnnotation()
                }

        override var inheritableHidden: Boolean
            get() =
                lazyGetAfterInherit(INHERITABLE_HIDDEN_BIT_MASK) {
                    // By default, i.e. if the property has not been set, the contents of this item
                    // will be hidden if this item was originally hidden and this item did not have
                    // a show annotation that applies recursively to its contents. Otherwise, the
                    // item's contents will be visible.
                    originallyHidden && !showability.showRecursive()
                }
            set(value) {
                lazySet(INHERITABLE_HIDDEN_BIT_MASK, value)
            }

        override val accessible: Boolean
            get() =
                lazyGet(ACCESSIBLE_BIT_MASK) {
                    when (item) {
                        // Packages are always accessible.
                        is PackageItem -> true
                        else ->
                            // This is accessible if it is public, protected or internal (with show
                            // annotation) and none of its containing classes, if any, are
                            // inaccessible.
                            (item.isPublic ||
                                item.isProtected ||
                                (item.isInternal && showability.show())) &&
                                item.containingClass()?.variantSelectors?.accessible != false
                    }
                }

        override var hidden: Boolean
            get() =
                lazyGetAfterInherit(HIDDEN_BIT_MASK) {
                    // By default, i.e. if the property has not been set, this item will be hidden
                    // if it inherits hidden from its parent (or was originally hidden) and this
                    // item does not have a show annotation of any sort. Otherwise, this item is
                    // visible.
                    inheritableHidden && !showability.show()
                }
            set(value) {
                lazySet(HIDDEN_BIT_MASK, value)
            }

        override val docOnly: Boolean
            get() =
                lazyGet(DOCONLY_BIT_MASK) {
                    (item.parent()?.variantSelectors?.docOnly == true) ||
                        item.documentation.isDocOnly
                }

        override var removed: Boolean
            get() =
                lazyGet(REMOVED_BIT_MASK) {
                    (item.parent()?.variantSelectors?.removed == true) ||
                        item.documentation.isRemoved
                }
            // This is only used for testing.
            set(value) {
                lazySet(REMOVED_BIT_MASK, value)
            }

        /** Cache of [showability]. */
        internal var _showability: Showability? = null

        override val showability: Showability
            get() =
                _showability
                    ?: let {
                        _showability = item.codebase.annotationManager.getShowabilityForItem(item)
                        _showability!!
                    }

        override fun duplicate(item: Item): ApiVariantSelectors = Mutable(item as SelectableItem)

        /**
         * Records whether [inheritInto] was called as it must only be called once.
         *
         * This uses [lazyGet] and [lazySet] to be consistent with other properties and makes it
         * easy to include the information in the [toString] result.
         */
        internal var inheritIntoWasCalled
            get() = lazyGet(INHERIT_INTO_BIT_MASK) { false }
            set(value) {
                lazySet(INHERIT_INTO_BIT_MASK, value)
            }

        override fun inheritInto() {
            // This must only be called once.
            if (inheritIntoWasCalled) return
            inheritIntoWasCalled = true

            // PackageItem behaves quite differently to the other Item types so do it first.
            if (item is PackageItem) {
                showability.let { showability ->
                    when {
                        showability.show() -> inheritableHidden = false
                        showability.hide() -> inheritableHidden = true
                    }
                }
                val containingPackageSelectors =
                    item.containingPackage()?.variantSelectors ?: return
                if (containingPackageSelectors.inheritableHidden) {
                    inheritableHidden = true
                }
                return
            }

            // Inheritance is only done on a few Item types, ignore the rest.
            if (item !is ClassItem && item !is CallableItem && item !is FieldItem) return

            if (item is ClassItem) {
                // Workaround: we're pulling in .aidl files from .jar files. These are
                // marked @hide, but since we only see the .class files we don't know that.
                if (
                    item.simpleName().startsWith("I") &&
                        item.isFromClassPath() &&
                        item.interfaceTypes().any { it.qualifiedName == "android.os.IInterface" }
                ) {
                    hidden = true
                    return
                }
            }

            if (showability.show()) {
                // If the showability is recursive then set inheritableHidden to false, that will
                // unhide any contents of this item too, unless they hide themselves.
                if (showability.showRecursive()) {
                    inheritableHidden = false
                }
                // Whether the showability is recursive or not a show annotation of any sort will
                // always unhide this item.
                hidden = false

                if (item is ClassItem) {
                    // Make containing package non-hidden if it contains a show-annotation class.
                    val containingPackageSelectors = item.containingPackage().variantSelectors
                    // Only unhide the package, do not affect anything that might inherit from that
                    // package.
                    (containingPackageSelectors as Mutable).hidden = false
                }

                if (item.containingClass() != null) {
                    ensureParentVisible()
                }
            } else if (showability.hide()) {
                inheritableHidden = true
            } else {
                val containingClassSelectors = item.containingClass()?.variantSelectors
                if (containingClassSelectors != null) {
                    if (item is FieldItem) {
                        if (
                            containingClassSelectors.originallyHidden &&
                                containingClassSelectors.showability.showNonRecursive()
                        ) {
                            // This is a member in a class that was hidden but then unhidden; but it
                            // was
                            // unhidden by a non-recursive (single) show annotation, so don't
                            // inherit
                            // the show annotation into this item.
                            inheritableHidden = true
                        }
                    } else if (containingClassSelectors.inheritableHidden) {
                        inheritableHidden = true
                    }
                } else if (item is ClassItem) {
                    // This will only be executed for top level classes, i.e. containing class is
                    // null. They inherit their properties from the containing package.
                    val containingPackageSelectors = item.containingPackage().variantSelectors
                    if (containingPackageSelectors.inheritableHidden) {
                        inheritableHidden = true
                    }
                }
            }
        }

        /**
         * Ensure that the parents of a visible [Item], i.e. one whose [Item.hidden] property is
         * `false` are themselves visible.
         *
         * Note: This will only be called when [item] is a class, constructor, method or field. In
         * particular, it does not apply to [PackageItem]s as they are completely separate from one
         * another, i.e. you do not need to have package `abc.qrs` be visible in order to have
         * `abc.qrs.xyz` be visible.
         */
        private fun ensureParentVisible() {
            val parent = item.parent() ?: return

            // If the parent is not hidden then everything is fine.
            if (!parent.hidden) {
                return
            }

            // Otherwise, find a show annotation to blame it on and report the issue.
            item.modifiers.findAnnotation(AnnotationItem::isShowAnnotation)?.let {
                violatingAnnotation ->
                item.codebase.reporter.report(
                    Issues.SHOWING_MEMBER_IN_HIDDEN_CLASS,
                    item,
                    "Attempting to unhide ${item.describe()}, but surrounding ${parent.describe()} is " +
                        "hidden and should also be annotated with $violatingAnnotation"
                )
            }
        }

        override fun toString(): String {
            return buildString {
                append(item.describe())
                append(" {\n")
                for ((bitPosition, propertyName) in propertyNamePerBit.withIndex()) {
                    val bitMask = 1 shl bitPosition
                    append("    ")
                    append(propertyName)
                    append("=")
                    if ((propertyHasBeenSetBits and bitMask) == 0) {
                        append("<not-set>")
                    } else if ((propertyValueBits and bitMask) == 0) {
                        append("false")
                    } else {
                        append("true")
                    }
                    append(",\n")
                }
                append("    showability=")
                if (_showability == null) {
                    append("<not-set>")
                } else {
                    append(_showability)
                }
                append(",\n")
                append("}")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Mutable) return false

            if (item != other.item) return false
            if (propertyHasBeenSetBits != other.propertyHasBeenSetBits) return false
            if (propertyValueBits != other.propertyValueBits) return false
            if (_showability != other._showability) return false

            return true
        }

        override fun hashCode(): Int {
            var result = item.hashCode()
            result = 31 * result + propertyHasBeenSetBits
            result = 31 * result + propertyValueBits
            result = 31 * result + _showability.hashCode()
            return result
        }

        companion object {
            // `originallyHidden` related constants
            private const val ORIGINALLY_HIDDEN_BIT_POSITION: Int = 0
            private const val ORIGINALLY_HIDDEN_BIT_MASK: Int = 1 shl ORIGINALLY_HIDDEN_BIT_POSITION

            // `inheritableHidden` related constants
            private const val INHERITABLE_HIDDEN_BIT_POSITION: Int =
                ORIGINALLY_HIDDEN_BIT_POSITION + 1
            private const val INHERITABLE_HIDDEN_BIT_MASK: Int =
                1 shl INHERITABLE_HIDDEN_BIT_POSITION

            // `hidden` related constants
            private const val HIDDEN_BIT_POSITION: Int = INHERITABLE_HIDDEN_BIT_POSITION + 1
            private const val HIDDEN_BIT_MASK: Int = 1 shl HIDDEN_BIT_POSITION

            // `accessible` related constants
            private const val ACCESSIBLE_BIT_POSITION: Int = HIDDEN_BIT_POSITION + 1
            private const val ACCESSIBLE_BIT_MASK: Int = 1 shl ACCESSIBLE_BIT_POSITION

            // `docOnly` related constants
            private const val DOCONLY_BIT_POSITION: Int = ACCESSIBLE_BIT_POSITION + 1
            private const val DOCONLY_BIT_MASK: Int = 1 shl DOCONLY_BIT_POSITION

            // `removed` related constants
            private const val REMOVED_BIT_POSITION: Int = DOCONLY_BIT_POSITION + 1
            private const val REMOVED_BIT_MASK: Int = 1 shl REMOVED_BIT_POSITION

            /**
             * Bit mask in [propertyHasBeenSetBits] that indicates whether [inheritInto] has been
             * called.
             */
            private const val INHERIT_INTO_BIT_POSITION = REMOVED_BIT_POSITION + 1
            private const val INHERIT_INTO_BIT_MASK = 1 shl INHERIT_INTO_BIT_POSITION

            /** The count of the number of bits used. */
            private const val COUNT_BITS_USED = INHERIT_INTO_BIT_POSITION + 1

            /** Map from bit to the associated property name, used in toString() */
            private val propertyNamePerBit =
                Array(COUNT_BITS_USED) { "" }
                    .also { array ->
                        array[ORIGINALLY_HIDDEN_BIT_POSITION] = "originallyHidden"
                        array[INHERITABLE_HIDDEN_BIT_POSITION] = "inheritableHidden"
                        array[HIDDEN_BIT_POSITION] = "hidden"
                        array[ACCESSIBLE_BIT_POSITION] = "accessible"
                        array[DOCONLY_BIT_POSITION] = "docOnly"
                        array[REMOVED_BIT_POSITION] = "removed"
                        array[INHERIT_INTO_BIT_POSITION] = "inheritIntoWasCalled"
                    }
        }
    }

    /**
     * Encapsulates the expected state of a [Mutable] instance.
     *
     * A data class was chosen for this because the nature of the [Mutable] class is such that
     * generally, once a property has been set it is not changed (not strictly true for packages).
     * Tests will typically, test the state, make a change (e.g. get the value of a property), check
     * the new state and so on. The [copy] method generated for data classes makes it easy to
     * incrementally modify the state without having to repeat all the previous changes.
     *
     * For `var` properties in [Mutable] each corresponding optional parameter will have no effect
     * if `null` but otherwise will be used to set the corresponding `var` property in the returned
     * object.
     *
     * The `val` properties like [originallyHidden] cannot be set to a specific value. So, all that
     * this can do is force it to be initialized. That means that the [ApiVariantSelectors] returned
     * from [createSelectorsforTesting] will only verify whether it is set or not-set as expected.
     * It cannot test if the value is expected. That will need to be done by the caller.
     */
    data class TestableSelectorsState(
        val item: SelectableItem,
        val originallyHidden: Boolean? = null,
        val inheritIntoWasCalled: Boolean = false,
        val inheritableHidden: Boolean? = null,
        val hidden: Boolean? = null,
        val docOnly: Boolean? = null,
        val removed: Boolean? = null,
        val showability: Showability? = null,
    ) {

        /**
         * Create a [Mutable] instance whose state matches this that can be used as the expected
         * state in a test.
         */
        fun createSelectorsforTesting(): ApiVariantSelectors =
            Mutable(item).also { selectors ->
                // If originally hidden is set then force it to be initialized.
                originallyHidden?.let {
                    // It is expected to be set so force it to be initialized.
                    selectors.originallyHidden
                }
                if (inheritIntoWasCalled) selectors.inheritIntoWasCalled = true
                inheritableHidden?.let { selectors.inheritableHidden = it }
                hidden?.let { selectors.hidden = it }
                docOnly?.let {
                    // It is expected to be set so force it to be initialized.
                    selectors.docOnly
                }
                removed?.let { selectors.removed = it }
                showability?.let { selectors._showability = it }
            }
    }
}
