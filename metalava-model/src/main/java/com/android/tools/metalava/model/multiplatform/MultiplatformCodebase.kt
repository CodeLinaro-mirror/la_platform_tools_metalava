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

package com.android.tools.metalava.model.multiplatform

import com.android.tools.metalava.model.Codebase

/**
 * A value which differs between source sets of a multiplatform project. This is a mapping from the
 * name of a source set to the value in that source set.
 */
typealias SourceSetDependent<V> = Map<String, V>

/**
 * Models a Kotlin multiplatform project (see https://kotlinlang.org/docs/multiplatform.html).
 *
 * There is a [Codebase] for each source set of the multiplatform project.
 */
class MultiplatformCodebase(sourceSetToCodebase: SourceSetDependent<Codebase>) :
    MultiplatformElement<Codebase>(sourceSetToCodebase)

/**
 * A wrapper for a [SourceSetDependent] map of some element. Provides common functionality for
 * different parts of a multiplatform model.
 */
sealed class MultiplatformElement<E>(protected val sourceSetToElement: SourceSetDependent<E>) {
    /** The source sets which this element exists in. */
    val sourceSets: Set<String>
        get() = sourceSetToElement.keys
}
