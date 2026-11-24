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

package com.android.tools.metalava.permission

import com.android.tools.metalava.model.ANDROIDX_REQUIRES_PERMISSION
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.StringValue

/**
 * Proxy class bound to an instance of the [ANDROIDX_REQUIRES_PERMISSION] annotation class.
 *
 * The constructor parameters are initialized from their corresponding annotation attribute. The
 * [value], [allOf] [anyOf] parameters are nullable so they will only be set if the attribute is
 * specified.
 *
 * @see bindTo
 */
class RequiresPermissionProxy(
    value: ArrayElementValue?,
    allOf: List<ArrayElementValue>?,
    anyOf: List<ArrayElementValue>?,

    /**
     * `true` if the set of permissions required is conditional, `false` otherwise.
     *
     * If `true` then the documentation will not be automatically updated to include the permission
     * requirements. Instead, it is up to the developer to document the permissions needed for each
     * condition.
     */
    val conditional: Boolean,
) {
    /**
     * The optional list of permission values.
     *
     * Will be `null` if none of [value], [allOf] or [anyOf] are specified.
     */
    private val optionalPermissionValues = value?.let { listOf(it) } ?: allOf ?: anyOf

    /** The list of permissions, either [StringValue] or [FieldReferenceValue]. */
    val permissionValues: List<ArrayElementValue> = optionalPermissionValues.orEmpty()

    /** `true` if any permission in [permissionValues] is needed, `false` if all are needed. */
    val any = anyOf != null

    /** Allow [permissionValues] to be accessed in a destructuring declaration. */
    operator fun component1() = permissionValues

    /** Allow [any] to be accessed in a destructuring declaration. */
    operator fun component2() = any

    /** Allow [conditional] to be accessed in a destructuring declaration. */
    operator fun component3() = conditional

    companion object {
        /**
         * Get an instance of [RequiresPermissionProxy] from [annotationItem], or `null` if it
         * provides no permissions.
         *
         * This must only be called on [ANDROIDX_REQUIRES_PERMISSION] annotations. It is the
         * caller's responsibility to ensure that.
         */
        internal fun from(annotationItem: AnnotationItem, item: Item) =
            annotationItem.bindTo<RequiresPermissionProxy>(item)?.takeIf {
                it.optionalPermissionValues != null
            }
    }
}

/**
 * Get an instance of [RequiresPermissionProxy] from [AnnotationItem], or `null` if it provides no
 * permissions.
 *
 * This must only be called on [ANDROIDX_REQUIRES_PERMISSION] annotations. It is the caller's
 * responsibility to ensure that.
 */
fun AnnotationItem.getRequiresPermissionProxy(item: Item) = RequiresPermissionProxy.from(this, item)
