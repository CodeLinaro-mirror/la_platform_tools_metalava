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

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinClassItem(
    override val codebase: PsiBasedCodebase,
    override val element: KtClassOrObject,
    override val modifiers: KotlinModifierList = KotlinModifierList(codebase),
    override var documentation: String = element.docComment?.toString().orEmpty()
) : KotlinItem, ClassItem, DefaultItem() {
    lateinit var containingPackage: PackageItem

    override fun simpleName(): String = element.name!!.toString()

    override fun fullName(): String {
        if (isTopLevelClass()) {
            return simpleName()
        }

        TODO("Implement fullName() for inner classes")
    }

    override fun qualifiedName(): String = element.fqName!!.asString()

    override fun isTopLevelClass(): Boolean = element.isTopLevel()

    override fun isInnerClass(): Boolean = !element.isTopLevel()

    override fun isDefined(): Boolean {
        TODO("Not yet implemented")
    }

    override fun superClass(): ClassItem? {
        TODO("Not yet implemented")
    }

    override fun superClassType(): TypeItem? {
        TODO("Not yet implemented")
    }

    override fun interfaceTypes(): List<TypeItem> {
        TODO("Not yet implemented")
    }

    override fun allInterfaces(): Sequence<ClassItem> {
        TODO("Not yet implemented")
    }

    override fun innerClasses(): List<ClassItem> = emptyList()

    override fun constructors(): List<ConstructorItem> = emptyList()

    override fun hasImplicitDefaultConstructor(): Boolean {
        TODO("Not yet implemented")
    }

    override fun methods(): List<MethodItem> = emptyList()

    override fun properties(): List<PropertyItem> = emptyList()

    override fun fields(): List<FieldItem> = emptyList()

    override fun isInterface(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isAnnotationType(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEnum(): Boolean = (element as? KtClass)?.isEnum() ?: false

    override fun containingClass(): ClassItem? {
        TODO("Not yet implemented")
    }

    override fun containingPackage(): PackageItem = containingPackage

    override fun toType(): TypeItem {
        TODO("Not yet implemented")
    }

    override fun hasTypeVariables(): Boolean {
        TODO("Not yet implemented")
    }

    override fun typeParameterList(): TypeParameterList {
        TODO("Not yet implemented")
    }

    override fun setSuperClass(superClass: ClassItem?, superClassType: TypeItem?) {
        TODO("Not yet implemented")
    }

    override fun setInterfaceTypes(interfaceTypes: List<TypeItem>) {
        TODO("Not yet implemented")
    }

    override val isTypeParameter: Boolean
        get() = TODO("Not yet implemented")
    override var hasPrivateConstructor: Boolean
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override var artifact: String?
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
    override fun getRetention(): AnnotationRetention {
        TODO("Not yet implemented")
    }

    override var stubConstructor: ConstructorItem?
        get() = TODO("Not yet implemented")
        set(value) { TODO(value.toString()) }
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
}
