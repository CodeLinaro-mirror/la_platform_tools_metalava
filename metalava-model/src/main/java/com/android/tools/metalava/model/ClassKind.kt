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

/**
 * The kind of class.
 *
 * Corresponds to similarly named values in [javax.lang.model.element.ElementKind].
 *
 * @param supportsInitializerBlock `true` if the class kind supports initializer blocks, e.g. `{
 *   field = 0; }` or `static { FIELD = 0; }`.
 */
enum class ClassKind(
    val supportsInitializerBlock: Boolean,
) {
    /** An interface. */
    INTERFACE(
        supportsInitializerBlock = false,
    ),

    /** An enum class. */
    ENUM(
        supportsInitializerBlock = true,
    ),

    /** An annotation class. */
    ANNOTATION_TYPE(
        supportsInitializerBlock = false,
    ),

    /** A normal class. */
    CLASS(
        supportsInitializerBlock = true,
    ),
}
