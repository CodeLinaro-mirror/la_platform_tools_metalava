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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.annotation.AnnotationClass
import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.value.FieldReferenceValue

class DefaultAnnotationClass(private val classItem: ClassItem) : AnnotationClass {
    override val retention by lazy(LazyThreadSafetyMode.NONE) { findRetention(classItem) }

    override val defaults by
        lazy(LazyThreadSafetyMode.NONE) {
            val nameToValue =
                classItem
                    .methods()
                    .mapNotNull {
                        val value = it.defaultValue ?: return@mapNotNull null
                        val name = it.name()
                        name to value
                    }
                    .toMap()
            if (nameToValue.isEmpty()) AnnotationDefaults.EMPTY else AnnotationDefaults(nameToValue)
        }

    companion object {
        /** Looks up the retention policy for the given class */
        private fun findRetention(cls: ClassItem): AnnotationRetention {
            val modifiers = cls.modifiers
            val annotation = modifiers.findAnnotation(AnnotationItem::isRetention)
            val value = annotation?.findAttribute(ANNOTATION_ATTR_VALUE)?.value
            val fieldName =
                (value as? FieldReferenceValue)?.fieldName
                    ?: return AnnotationRetention.getDefault(cls)
            return AnnotationRetention.valueOf(fieldName)
        }
    }
}
