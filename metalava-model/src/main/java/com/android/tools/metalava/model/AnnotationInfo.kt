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
    val nullability: Nullability? =
        when {
            isNullableAnnotation(qualifiedName) -> Nullability.NULLABLE
            isNonNullAnnotation(qualifiedName) -> Nullability.NON_NULL
            else -> null
        }

    /**
     * If true then this annotation will cause annotated items (and any contents) to be added to the
     * API.
     *
     * e.g. if this annotation is used on a class then it will also apply (unless overridden by a
     * closer annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    open val show: Boolean
        get() = false

    /** If true then this annotation will cause annotated items to be added to the API. */
    open val showSingle: Boolean
        get() = false

    /** If true then this annotation will cause annotated items to be added to the stubs only. */
    open val showForStubPurposes: Boolean
        get() = false
}

enum class Nullability {
    NULLABLE,
    NON_NULL,
}
