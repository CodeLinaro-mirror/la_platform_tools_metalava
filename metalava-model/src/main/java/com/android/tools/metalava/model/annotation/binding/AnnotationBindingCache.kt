/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.annotation.binding

import com.android.tools.metalava.model.annotation.AnnotationDefaults
import kotlin.reflect.KClass

/**
 * A cache from [Key] to [AnnotationBindingFactory].
 *
 * Creating an instance of [AnnotationBindingFactory] can be expensive so this allows that cost to
 * be amortized over many uses.
 */
internal class AnnotationBindingCache {
    /**
     * The key that uniquely identifies the [AnnotationBindingFactory].
     *
     * It takes into account the [kClass] to which an annotation will be bound and the [defaults]
     * that apply to the annotation.
     */
    private data class Key<T : Any>(
        val kClass: KClass<T>,
        val defaults: AnnotationDefaults?,
    )

    /** Map from [Key] to [AnnotationBinding]. */
    private val classMap = hashMapOf<Key<*>, AnnotationBinding<*>>()

    /** Get an [AnnotationBindingFactory] for [kClass] with [defaults]. */
    fun <T : Any> bindingFactoryFor(
        kClass: KClass<T>,
        defaults: AnnotationDefaults?,
    ): AnnotationBindingFactory<T> {
        val key = Key(kClass, defaults)
        val annotationBinding =
            classMap.computeIfAbsent(key) { key ->
                AnnotationBinding(
                    key.kClass,
                    key.defaults,
                )
            }
        @Suppress("UNCHECKED_CAST") return annotationBinding as AnnotationBinding<T>
    }
}
