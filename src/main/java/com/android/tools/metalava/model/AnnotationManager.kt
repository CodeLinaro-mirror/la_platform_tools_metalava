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

/** Provides support for managing annotations within Metalava. */
interface AnnotationManager {

    /**
     * Maps an annotation name to the name to be used internally.
     *
     * Annotations that should not be used internally are mapped to null.
     */
    fun normalizeInputName(qualifiedName: String?): String?

    /**
     * Maps an annotation name to the name to be used in signatures/stubs/external annotation files.
     * Annotations that should not be exported are mapped to null.
     */
    fun normalizeOutputName(
        qualifiedName: String?,
        target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE
    ): String?

    /** Get the applicable targets for the annotation */
    fun computeTargets(
        annotation: AnnotationItem,
        classFinder: (String) -> ClassItem?
    ): Set<AnnotationTarget>

    /** Determine how to handle typedef annotations, i.e. annotations like `@IntDef`. */
    val typedefMode: TypedefMode
}
