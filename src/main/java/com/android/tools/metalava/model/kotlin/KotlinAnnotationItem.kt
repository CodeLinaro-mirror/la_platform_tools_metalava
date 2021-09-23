/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava.model.kotlin

import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import org.jetbrains.kotlin.psi.KtAnnotation

class KotlinAnnotationItem private constructor(
    override val codebase: PsiBasedCodebase,
    val ktAnnotation: KtAnnotation,
    private val originalName: String?
) : DefaultAnnotationItem(codebase) {
    override fun qualifiedName(): String? {
        TODO("Not yet implemented")
    }

    override fun originalName(): String? {
        TODO("Not yet implemented")
    }

    override fun toSource(target: AnnotationTarget, showDefaultAttrs: Boolean): String {
        TODO("Not yet implemented")
    }

    override fun attributes(): List<AnnotationAttribute> {
        TODO("Not yet implemented")
    }
}
