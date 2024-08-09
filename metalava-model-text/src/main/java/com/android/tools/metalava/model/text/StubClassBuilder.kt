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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultModifierList

/**
 * A builder for stub classes, i.e. [TextClassItem]s fabricated because [ApiFile] has no definition
 * of the class but a [TextClassItem] is still needed.
 */
internal class StubClassBuilder(
    val codebase: TextCodebase,
    val qualifiedName: String,
    private val fullName: String,
    private val containingClass: ClassItem?,
) {
    /** The default [ClassKind] can be modified. */
    var classKind = ClassKind.CLASS

    /** The modifiers are set to `public` because otherwise there is no point in creating it. */
    val modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC)

    var superClassType: ClassTypeItem? = null

    private fun build(): TextClassItem =
        TextClassItem(
                codebase = codebase,
                modifiers = modifiers,
                classKind = classKind,
                qualifiedName = qualifiedName,
                fullName = fullName,
                containingClass = containingClass,
            )
            .also { item -> item.setSuperClassType(superClassType) }

    companion object {
        /**
         * Create a [TextClassItem] in the specified [codebase] and with the specific
         * [qualifiedName], after applying the specified mutator.
         */
        fun build(
            codebase: TextCodebase,
            qualifiedName: String,
            fullName: String,
            containingClass: ClassItem?,
            mutator: StubClassBuilder.() -> Unit
        ): TextClassItem {
            val builder =
                StubClassBuilder(
                    codebase = codebase,
                    qualifiedName = qualifiedName,
                    fullName = fullName,
                    containingClass = containingClass,
                )
            builder.mutator()
            return builder.build()
        }
    }
}
