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
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.StringValue
import com.android.tools.metalava.model.value.asBoolean

/** Encapsulate information extracted from an [ANDROIDX_REQUIRES_PERMISSION] attribute. */
data class RequiresPermissionInfo(
    /** The list of permissions, either [StringValue] or [FieldReferenceValue]. */
    val permissionValues: List<ArrayElementValue>,

    /** `true` if any permission in [permissionValues] is needed, `false` if all are needed. */
    val any: Boolean,

    /**
     * `true` if the set of permissions required is conditional, `false` otherwise.
     *
     * If `true` then the documentation will not be automatically updated to include the permission
     * requirements. Instead, it is up to the developer to document the permissions needed for each
     * condition.
     */
    val conditional: Boolean,
) {
    companion object {
        internal fun from(annotationItem: AnnotationItem): RequiresPermissionInfo? {
            var permissionsAttribute: AnnotationAttribute? = null
            var any = false
            var conditional = false
            for (attribute in annotationItem.attributes) {
                when (attribute.name) {
                    "value",
                    "allOf" -> {
                        permissionsAttribute = attribute
                    }
                    "anyOf" -> {
                        any = true
                        permissionsAttribute = attribute
                    }
                    "conditional" -> conditional = attribute.value.asBoolean() == true
                }
            }

            return if (permissionsAttribute == null) {
                null
            } else {
                RequiresPermissionInfo(permissionsAttribute.value.asFlatList(), any, conditional)
            }
        }
    }
}

/**
 * Get an instance of [RequiresPermissionInfo] from [AnnotationItem], or `null` if the annotation is
 * not a `RequiresPermission` annotation.
 */
fun AnnotationItem.getRequiresPermissionInfo() =
    if (qualifiedName == ANDROIDX_REQUIRES_PERMISSION) RequiresPermissionInfo.from(this) else null
