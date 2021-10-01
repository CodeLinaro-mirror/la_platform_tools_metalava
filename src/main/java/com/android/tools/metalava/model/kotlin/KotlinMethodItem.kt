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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinMethodItem(
    override val codebase: PsiBasedCodebase,
    override val element: KtNamedFunction,
    override val modifiers: KotlinModifierList = KotlinModifierList(codebase),
    override var documentation: String = element.docComment?.toString().orEmpty()
) : KotlinItem, MethodItem, DefaultItem() {
    override fun name(): String {
        TODO("Not yet implemented")
    }

    override fun containingClass(): ClassItem {
        TODO("Not yet implemented")
    }

    override fun isConstructor(): Boolean {
        TODO("Not yet implemented")
    }

    override fun returnType(): TypeItem? {
        TODO("Not yet implemented")
    }

    override fun parameters(): List<ParameterItem> {
        TODO("Not yet implemented")
    }

    override fun isExtensionMethod(): Boolean {
        TODO("Not yet implemented")
    }

    override fun superMethods(): List<MethodItem> {
        TODO("Not yet implemented")
    }

    override fun typeParameterList(): TypeParameterList {
        TODO("Not yet implemented")
    }

    override fun throwsTypes(): List<ClassItem> {
        TODO("Not yet implemented")
    }

    override var inheritedMethod: Boolean
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }
    override var originallyHidden: Boolean
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }
    override var hidden: Boolean
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }
    override var removed: Boolean
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }
    override var deprecated: Boolean
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }
    override var docOnly: Boolean
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }
    override val synthetic: Boolean
        get() = TODO("Not yet implemented")

    override fun mutableModifiers(): MutableModifierList {
        TODO("Not yet implemented")
    }

    override fun findTagDocumentation(tag: String): String? {
        TODO("Not yet implemented")
    }

    override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun isCloned(): Boolean {
        TODO("Not yet implemented")
    }

    override var inheritedFrom: ClassItem?
        get() = TODO("Not yet implemented")
        set(_) { TODO("Not yet implemented") }

    override fun duplicate(targetContainingClass: ClassItem): MethodItem {
        TODO("Not yet implemented")
    }
}
