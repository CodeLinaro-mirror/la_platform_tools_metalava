/*
 * Copyright (C) 2017 The Android Open Source Project
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

interface MutableModifierList : BaseModifierList {
    fun setVisibilityLevel(level: VisibilityLevel)

    fun setStatic(static: Boolean)

    fun setAbstract(abstract: Boolean)

    fun setFinal(final: Boolean)

    fun setNative(native: Boolean)

    fun setSynchronized(synchronized: Boolean)

    fun setStrictFp(strictfp: Boolean)

    fun setTransient(transient: Boolean)

    fun setVolatile(volatile: Boolean)

    fun setDefault(default: Boolean)

    fun setDeprecated(deprecated: Boolean)

    fun setSealed(sealed: Boolean)

    fun setFunctional(functional: Boolean)

    fun setInfix(infix: Boolean)

    fun setOperator(operator: Boolean)

    fun setInline(inline: Boolean)

    fun setValue(value: Boolean)

    fun setVarArg(vararg: Boolean)

    fun setData(data: Boolean)

    fun setSuspend(suspend: Boolean)

    fun setCompanion(companion: Boolean)

    fun setExpect(expect: Boolean)

    fun setActual(actual: Boolean)

    fun addAnnotation(annotation: AnnotationItem?) {
        if (annotation != null) mutateAnnotations { add(annotation) }
    }

    /**
     * Mutate the [annotations] list.
     *
     * Provides a [MutableList] of the [annotations] that can be modified by [mutator]. Once the
     * mutator exits the [annotations] list will be updated. The [MutableList] must not be accessed
     * from outside [mutator].
     */
    fun mutateAnnotations(mutator: MutableList<AnnotationItem>.() -> Unit)
}
