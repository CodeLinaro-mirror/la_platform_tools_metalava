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
import com.android.tools.metalava.model.AnnotationUse
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.annotation.AnnotationClass
import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.Value
import java.lang.annotation.ElementType
import kotlin.getValue

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

    override val annotationUse by
        lazy(LazyThreadSafetyMode.NONE) {
            classItem.computeAnnotationUse() ?: AnnotationUse.DECLARATION_ONLY
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

/** Name of the [java.lang.annotation.Target] class. */
private val JAVA_ANNOTATION_TARGET_NAME = java.lang.annotation.Target::class.java.name

/** Name of the [kotlin.annotation.Target] class. */
private val KOTLIN_ANNOTATION_TARGET_NAME = Target::class.java.name

/**
 * Compute the [AnnotationUse] for this [ClassItem] which is assumed to be an annotation class by
 * finding the [JAVA_ANNOTATION_TARGET_NAME] or [KOTLIN_ANNOTATION_TARGET_NAME] [AnnotationItem] and
 * getting the [AnnotationUse] from that.
 *
 * If no [AnnotationUse] can be found then returns null.
 */
private fun ClassItem.computeAnnotationUse() =
    modifiers
        .annotations()
        .mapNotNull {
            when (it.qualifiedName) {
                JAVA_ANNOTATION_TARGET_NAME -> {
                    // The java.lang.annotation.Target stores the ElementTypes in the default value
                    // attribute so get its value and compute the AnnotationUse.
                    it.findAttribute(ANNOTATION_ATTR_VALUE)?.value?.computeAnnotationUse()
                }
                KOTLIN_ANNOTATION_TARGET_NAME -> {
                    // The kotlin.annotation.Target stored the AnnotationTargets in the
                    // "allowedTargets" attribute so get its value and compute the AnnotationUse.
                    it.findAttribute("allowedTargets")?.value?.computeAnnotationUse()
                }
                else -> null
            }
        }
        .fold(null, ::combineAnnotationUse)

/**
 * Combine two optional [AnnotationUse] instances into one.
 *
 * If either is null then return the other, otherwise use [AnnotationUse.combineWith] to combine
 * them.
 */
private fun combineAnnotationUse(result: AnnotationUse?, next: AnnotationUse?) =
    result?.combineWith(next) ?: next

/**
 * Compute the [AnnotationUse] from this [Value] which is assumed to be either a
 * [java.lang.annotation.ElementType] or [kotlin.annotation.AnnotationTarget] instance, or an array
 * of them.
 *
 * Gets the combined [AnnotationUse] from them.
 */
private fun Value.computeAnnotationUse(): AnnotationUse? =
    when (this) {
        is ArrayValue -> {
            // Combine the AnnotationUse for each
            elements.fold(null) { accumulator, value ->
                combineAnnotationUse(accumulator, value.computeAnnotationUse())
            }
        }
        is FieldReferenceValue -> {
            when (this) {
                JAVA_TYPE_USE_ELEMENT_TYPE,
                KOTLIN_TYPE_ANNOTATION_TARGET -> AnnotationUse.TYPE_ONLY
                else -> AnnotationUse.DECLARATION_ONLY
            }
        }
        else -> null
    }

/**
 * A [FieldReferenceValue] to [java.lang.annotation.ElementType.TYPE_USE].
 *
 * Only for use in [Value.computeAnnotationUse].
 */
private val JAVA_TYPE_USE_ELEMENT_TYPE =
    Value.createFieldReferenceValue(
        ClassResolver.RETURN_NULL,
        ElementType::class.java.name,
        ElementType.TYPE_USE.name
    )

/**
 * A [FieldReferenceValue] to [kotlin.annotation.AnnotationTarget.TYPE].
 *
 * Only for use in [Value.computeAnnotationUse].
 */
private val KOTLIN_TYPE_ANNOTATION_TARGET =
    Value.createFieldReferenceValue(
        ClassResolver.RETURN_NULL,
        AnnotationTarget::class.java.name,
        AnnotationTarget.TYPE.name
    )
