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
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import org.jetbrains.kotlin.types.KotlinType
import java.util.function.Predicate

class KotlinTypeItem private constructor(
    private val codebase: PsiBasedCodebase,
    var ktType: KotlinType
) : TypeItem {
    override fun toTypeString(
        outerAnnotations: Boolean,
        innerAnnotations: Boolean,
        erased: Boolean,
        kotlinStyleNulls: Boolean,
        context: Item?,
        filter: Predicate<Item>?
    ): String {
        TODO("Not yet implemented")
    }

    override fun toErasedTypeString(context: Item?): String {
        TODO("Not yet implemented")
    }

    override fun arrayDimensions(): Int {
        TODO("Not yet implemented")
    }

    override fun asClass(): ClassItem? {
        TODO("Not yet implemented")
    }

    override val primitive: Boolean
        get() = TODO("Not yet implemented")

    override fun typeArgumentClasses(): List<ClassItem> {
        TODO("Not yet implemented")
    }

    override fun convertType(replacementMap: Map<String, String>?, owner: Item?): TypeItem {
        TODO("Not yet implemented")
    }

    override fun asTypeParameter(context: MemberItem?): TypeParameterItem? {
        TODO("Not yet implemented")
    }

    override fun markRecent() {
        TODO("Not yet implemented")
    }

    override fun scrubAnnotations() {
        TODO("Not yet implemented")
    }
}
