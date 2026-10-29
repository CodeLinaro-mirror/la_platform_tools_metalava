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

import com.android.tools.metalava.model.annotation.AnnotationClass
import com.android.tools.metalava.model.api.SurfaceAnnotationData
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags

/**
 * Encapsulates information that metalava needs to know about a specific annotation type.
 *
 * Instances of [AnnotationInfo] will be shared across [AnnotationItem]s that have the same
 * qualified name and (where applicable) the same attributes. That will allow the information in
 * [AnnotationInfo] to be computed once and then reused whenever needed.
 */
interface AnnotationInfo {

    /** The applicable targets for this annotation */
    val targets: Set<AnnotationTarget>

    /**
     * Determines whether the annotation is nullability related.
     *
     * If this is null then the annotation is not a nullability annotation, otherwise this
     * determines whether it is nullable or non-null.
     */
    val typeNullability: TypeNullability?

    /**
     * The [SurfaceAnnotationData] associated with this annotation, `null` if it is not a surface
     * annotation.
     */
    val surfaceData: SurfaceAnnotationData?

    /**
     * The [ApiFlag] referenced by the annotation.
     *
     * This will be `null` if no [ApiFlags] have been provided or the annotation type is not
     * [ANDROID_FLAGGED_API]. Otherwise, it will be an instance of [ApiFlag].
     */
    val apiFlag: ApiFlag?

    val suppressCompatibility: Boolean

    /**
     * The [AnnotationClass] that provides information about the annotation class of the
     * [AnnotationItem] instance to which this corresponds.
     */
    val annotationClass: AnnotationClass?
}

/** Compute the [TypeNullability], if any, for the annotation with [qualifiedName]. */
internal fun computeTypeNullability(qualifiedName: String): TypeNullability? =
    when {
        isNullableAnnotation(qualifiedName) -> TypeNullability.NULLABLE
        isNonNullAnnotation(qualifiedName) -> TypeNullability.NONNULL
        else -> null
    }
