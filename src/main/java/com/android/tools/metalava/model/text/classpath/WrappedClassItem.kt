/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.text.classpath

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList

/**
 * A [ClassItem] implementation which is just a wrapper around another variable [ClassItem]. This
 * allows the class to start as an empty stub which is later be swapped out for a PSI class.
 *
 * Defers to its [wrappedItem] for implementations of all methods and properties.
 */
class WrappedClassItem(
    var wrappedItem: ClassItem,
) : ClassItem, DefaultItem() {
    override var emit: Boolean
        get() = wrappedItem.emit
        set(value) {
            wrappedItem.emit = value
        }

    override val codebase: Codebase
        get() = wrappedItem.codebase

    override val modifiers: ModifierList
        get() = wrappedItem.modifiers

    override var originallyHidden: Boolean
        get() = wrappedItem.originallyHidden
        set(value) {
            wrappedItem.originallyHidden = value
        }

    override var hidden: Boolean
        get() = wrappedItem.hidden
        set(value) {
            wrappedItem.hidden = value
        }

    override var removed: Boolean
        get() = wrappedItem.removed
        set(value) {
            wrappedItem.removed = value
        }

    override var deprecated: Boolean
        get() = wrappedItem.deprecated
        set(value) {
            wrappedItem.deprecated = value
        }

    override var docOnly: Boolean
        get() = wrappedItem.docOnly
        set(value) {
            wrappedItem.docOnly = value
        }

    override val synthetic: Boolean
        get() = wrappedItem.synthetic

    override fun mutableModifiers(): MutableModifierList = wrappedItem.mutableModifiers()

    override var documentation: String
        get() = wrappedItem.documentation
        set(value) {
            wrappedItem.documentation = value
        }

    override fun findTagDocumentation(tag: String, value: String?): String? =
        wrappedItem.findTagDocumentation(tag, value)

    override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) {
        wrappedItem.appendDocumentation(comment, tagSection, append)
    }

    override fun equals(other: Any?): Boolean =
        other is WrappedClassItem && wrappedItem == other.wrappedItem

    // The qualified name should stay the same even if the [wrappedItem] is swapped out.
    override fun hashCode(): Int = wrappedItem.qualifiedName().hashCode()

    override fun isCloned(): Boolean = wrappedItem.isCloned()

    override val isTypeParameter: Boolean
        get() = wrappedItem.isTypeParameter

    override var hasPrivateConstructor: Boolean
        get() = wrappedItem.hasPrivateConstructor
        set(value) {
            wrappedItem.hasPrivateConstructor = value
        }

    override var artifact: String?
        get() = wrappedItem.artifact
        set(value) {
            wrappedItem.artifact = value
        }

    override var stubConstructor: ConstructorItem?
        get() = wrappedItem.stubConstructor
        set(value) {
            wrappedItem.stubConstructor = value
        }

    override fun simpleName(): String = wrappedItem.simpleName()

    override fun fullName(): String = wrappedItem.fullName()

    override fun qualifiedName(): String = wrappedItem.qualifiedName()

    override fun isDefined(): Boolean = wrappedItem.isDefined()

    override fun superClass(): ClassItem? = wrappedItem.superClass()

    override fun superClassType(): TypeItem? = wrappedItem.superClassType()

    override fun interfaceTypes(): List<TypeItem> = wrappedItem.interfaceTypes()

    override fun allInterfaces(): Sequence<ClassItem> = wrappedItem.allInterfaces()

    override fun addInnerClass(cls: ClassItem) {
        wrappedItem.addInnerClass(cls)
    }

    override fun innerClasses(): List<ClassItem> = wrappedItem.innerClasses()

    override fun constructors(): List<ConstructorItem> = wrappedItem.constructors()

    override fun hasImplicitDefaultConstructor(): Boolean =
        wrappedItem.hasImplicitDefaultConstructor()

    override fun methods(): List<MethodItem> = wrappedItem.methods()

    override fun properties(): List<PropertyItem> = wrappedItem.properties()

    override fun fields(): List<FieldItem> = wrappedItem.fields()

    override fun isInterface(): Boolean = wrappedItem.isInterface()

    override fun isAnnotationType(): Boolean = wrappedItem.isAnnotationType()

    override fun isEnum(): Boolean = wrappedItem.isEnum()
    override fun containingClass(): ClassItem? = wrappedItem.containingClass()

    override fun containingPackage(): PackageItem = wrappedItem.containingPackage()

    override fun toType(): TypeItem = wrappedItem.toType()

    override fun hasTypeVariables(): Boolean = wrappedItem.hasTypeVariables()

    override fun typeParameterList(): TypeParameterList = wrappedItem.typeParameterList()

    override fun setSuperClass(superClass: ClassItem?, superClassType: TypeItem?) {
        wrappedItem.setSuperClass(superClass, superClassType)
    }

    override fun setInterfaceTypes(interfaceTypes: List<TypeItem>) {
        wrappedItem.setInterfaceTypes(interfaceTypes)
    }

    override fun getRetention(): AnnotationRetention = wrappedItem.getRetention()
}
