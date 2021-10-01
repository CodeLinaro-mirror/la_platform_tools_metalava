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

import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import org.jetbrains.kotlin.psi.KtParameter

class KotlinParameterItem(
    override val codebase: PsiBasedCodebase,
    override val element: KtParameter,
    private val name: String,
    override val parameterIndex: Int,
    override val modifiers: KotlinModifierList,
    override var documentation: String,
    private val type: KotlinTypeItem
) : KotlinItem, ParameterItem, DefaultItem() {
    override fun name(): String {
        TODO("Not yet implemented")
    }

    override fun type(): TypeItem {
        TODO("Not yet implemented")
    }

    override fun containingMethod(): MethodItem {
        TODO("Not yet implemented")
    }

    override fun publicName(): String? {
        TODO("Not yet implemented")
    }

    override fun hasDefaultValue(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isDefaultValueKnown(): Boolean {
        TODO("Not yet implemented")
    }

    override fun defaultValue(): String? {
        TODO("Not yet implemented")
    }

    override fun isVarArgs(): Boolean {
        TODO("Not yet implemented")
    }

    override var originallyHidden: Boolean
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override var hidden: Boolean
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override var removed: Boolean
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override var deprecated: Boolean
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override var docOnly: Boolean
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override val synthetic: Boolean
        get() = TODO("Not yet implemented")

    override fun mutableModifiers(): MutableModifierList {
        TODO("Not yet implemented")
    }

    override fun findTagDocumentation(tag: String): String? {
        TODO("Not yet implemented")
    }

    override val sortingRank: Int
        get() = TODO("Not yet implemented")

    override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) {
        TODO("Not yet implemented")
    }

    override val isPackagePrivate: Boolean
        get() = TODO("Not yet implemented")
    override val isPrivate: Boolean
        get() = TODO("Not yet implemented")

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun isCloned(): Boolean {
        TODO("Not yet implemented")
    }
}
