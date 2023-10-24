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
     * Determines whether this annotation affects whether the annotated item is shown and if so how
     * it is shown.
     */
    open val showability: Showability
        get() = Showability.NO_EFFECT

    /**
     * If true then this annotation will cause annotated items to be hidden from the API.
     *
     * This is true if this annotation is explicitly specified as a hide annotation, or is annotated
     * with a meta hide annotation, see [hideMeta].
     */
    open val hide: Boolean
        get() = false

    open val suppressCompatibility: Boolean
        get() = false
}

internal enum class Nullability {
    NULLABLE,
    NON_NULL,
}

/** Available ways in which an annotation can affect whether, and if so how, an item is shown. */
data class Showability(
    /**
     * If true then the annotated item will be shown as part of the API, unless overridden in some
     * way.
     */
    private val show: Boolean,
    /**
     * If true then the annotated item will recursively affect enclosed items, unless overridden by
     * a closer annotation.
     */
    private val recursive: Boolean,
    /**
     * If true then the annotated item will only be included in stubs of the API, otherwise it can
     * appear in all representations of the API, e.g. signature files.
     */
    private val forStubsOnly: Boolean,
) {
    fun show() = show

    fun showForStubsOnly() = forStubsOnly

    fun showNonRecursive() = show && !recursive

    companion object {
        /** The annotation does not affect whether an annotated item is shown. */
        val NO_EFFECT = Showability(show = false, recursive = false, forStubsOnly = false)
    }
}
