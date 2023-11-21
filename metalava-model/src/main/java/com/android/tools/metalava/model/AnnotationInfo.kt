/*
 * Copyright (C) 2023 The Android Open Source Project
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

/**
 * Encapsulates information that metalava needs to know about a specific annotation type.
 *
 * Instances of [AnnotationInfo] will be shared across [AnnotationItem]s that have the same
 * qualified name and (where applicable) the same attributes. That will allow the information in
 * [AnnotationInfo] to be computed once and then reused whenever needed.
 *
 * This class just sets the properties that can be determined simply by looking at the
 * [qualifiedName]. Any other properties are set to the default, usually `false`. Subclasses can
 * change that behavior.
 */
open class AnnotationInfo(
    /** The fully qualified and normalized name of the annotation class. */
    val qualifiedName: String,
) {

    /**
     * Determines whether the annotation is nullability related.
     *
     * If this is null then the annotation is not a nullability annotation, otherwise this
     * determines whether it is nullable or non-null.
     */
    internal val nullability: Nullability? =
        when {
            isNullableAnnotation(qualifiedName) -> Nullability.NULLABLE
            isNonNullAnnotation(qualifiedName) -> Nullability.NON_NULL
            else -> null
        }

    /**
     * Determines whether this annotation affects whether the annotated item is shown or hidden and
     * if so how.
     */
    open val showability: Showability
        get() = Showability.NO_EFFECT

    open val suppressCompatibility: Boolean
        get() = false
}

internal enum class Nullability {
    NULLABLE,
    NON_NULL,
}

/**
 * The set of possible effects on whether an `Item` is part of an API.
 *
 * They are in order from the lowest priority to the highest priority, see [highestPriority].
 */
enum class ShowOrHide(private val show: Boolean?) {
    /** No effect either way. */
    NO_EFFECT(show = null),

    /** Hide an item from the API. */
    HIDE(show = false),

    /** Show an item as part of the API. */
    SHOW(show = true),

    /**
     * Hide an unstable API.
     *
     * API items could have show annotations so in order to hide them this has to come after [SHOW]
     * so it can override any show annotations.
     */
    HIDE_UNSTABLE_API(show = false),
    ;

    /** Return true if this shows an `Item` as part of the API. */
    fun show(): Boolean = show == true

    /** Return true if this hides an `Item` from the API. */
    fun hide(): Boolean = show == false

    /** Return the highest priority between this and another [ShowOrHide]. */
    fun highestPriority(other: ShowOrHide): ShowOrHide = maxOf(this, other)
}

/** Available ways in which an annotation can affect whether, and if so how, an item is shown. */
data class Showability(
    /**
     * If true then the annotated item will be shown as part of the API, unless overridden in some
     * way.
     *
     * Is `true` for annotations that match `--show-annotation`, or `--show-single-annotation`, but
     * not `--show-for-stub-purposes-annotation`, and items that are annotated with such an
     * annotation that is not overridden in some way.
     *
     * If false then the annotated item will NOT be shown as part of the API, unless overridden in
     * some way.
     *
     * If null then this has no effect on whether an annotated item will be shown or not.
     */
    private val show: ShowOrHide,

    /**
     * If true then the annotated item will recursively affect enclosed items, unless overridden by
     * a closer annotation.
     *
     * Is `true` for annotations that match `--show-annotation`, but not `--show-single-annotation`,
     * or `--show-for-stub-purposes-annotation`, and items that are annotated with such an
     * annotation that is not overridden in some way.
     */
    private val recursive: ShowOrHide,

    /**
     * If true then the annotated item will only be included in stubs of the API, otherwise it can
     * appear in all representations of the API, e.g. signature files.
     *
     * Is `true` for annotations that match ``--show-for-stub-purposes-annotation`, and items that
     * are annotated with such an annotation that is not overridden in some way.
     */
    private val forStubsOnly: Boolean,
) {
    /**
     * Check whether the annotated item should be considered part of the API or not.
     *
     * Returns `true` if the item is annotated with a `--show-annotation`,
     * `--show-single-annotation`, or `--show-for-stub-purposes-annotation`.
     */
    fun show() = show.show() || forStubsOnly

    /**
     * Check whether the annotated item should only be considered part of the API when generating
     * stubs.
     *
     * Returns `true` if the item is annotated with a `--show-for-stub-purposes-annotation`. Such
     * items will be part of an API surface that the API being generated extends.
     */
    fun showForStubsOnly() = forStubsOnly

    /**
     * Check whether the annotations on this item only affect the current `Item`.
     *
     * Returns `true` if they do, `false` if they can also affect nested `Item`s.
     */
    fun showNonRecursive() = show.show() && !recursive.show() && !forStubsOnly

    /**
     * Check whether the annotated item should be hidden from the API.
     *
     * Returns `true` if the annotation matches an `--hide-annotation`.
     */
    fun hide() = show.hide()

    /**
     * Check whether the annotated item is part of an unstable API that needs to be hidden.
     *
     * Returns `true` if the annotation matches `--hide-annotation android.annotation.FlaggedApi` or
     * if this is on an item then when the item is annotated with such an annotation or is a method
     * that overrides such an item or is contained within a class that is annotated with such an
     * annotation.
     */
    fun hideUnstableApi() = show == ShowOrHide.HIDE_UNSTABLE_API

    /** Combine this with [other] to produce a combination [Showability]. */
    fun combineWith(other: Showability): Showability {
        // Show wins over not showing.
        val newShow = show.highestPriority(other.show)

        // Recursive wins over not recursive.
        val newRecursive = recursive.highestPriority(other.recursive)

        // For everything wins over only for stubs.
        val forStubsOnly = !newShow.show() && (forStubsOnly || other.forStubsOnly)

        return Showability(newShow, newRecursive, forStubsOnly)
    }

    companion object {
        /** The annotation does not affect whether an annotated item is shown. */
        val NO_EFFECT =
            Showability(
                show = ShowOrHide.NO_EFFECT,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = false
            )
    }
}
