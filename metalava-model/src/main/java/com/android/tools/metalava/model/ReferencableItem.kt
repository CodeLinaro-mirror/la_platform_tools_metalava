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

package com.android.tools.metalava.model

/**
 * Effectively forms a union type of all referencable [Item]s that extend this.
 *
 * Currently, the union contains:
 * * [PackageItem]
 * * [ClassItem]
 * * [TypeParameterItem]
 * * [FieldItem]
 * * [ReferencableCallableItem]
 *
 * and also [InvalidReferencableItem] for reporting errors.
 */
sealed interface ReferencableItem

/** Provides details about an invalid reference and why it could not be resolved. */
data class InvalidReferencableItem(val message: String) : ReferencableItem

/**
 * Effectively forms a union type of all referencable [Item]s that extend this.
 *
 * Currently, the union contains:
 * * [ReferencableMethodSet]
 *
 * and also [InvalidReferencableItem] for reporting errors.
 */
sealed interface ReferencableCallableItem : ReferencableItem

/**
 * The set of all [MethodItem]s in [ClassItem] called [name].
 *
 * It is the responsibility of the recipient to resolve this to a specific [MethodItem], if
 * possible, using additional information that it has available, e.g. parameter types.
 *
 * An alternative solution would have been to just return a [MethodItem] in the set to represent the
 * whole set but that would be confusing to callers.
 */
data class ReferencableMethodSet(
    /** The [ClassItem] that contains the methods. */
    val containingClass: ClassItem,

    /** The name of the methods in the set. */
    val name: String,
) : ReferencableCallableItem
