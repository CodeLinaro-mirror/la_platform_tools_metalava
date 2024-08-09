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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.reporter.FileLocation

internal open class TurbineClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    source: SourceFile?,
    classKind: ClassKind,
    containingClass: ClassItem?,
    qualifiedName: String,
    simpleName: String,
    fullName: String,
    typeParameterList: TypeParameterList,
) :
    DefaultClassItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.JAVA,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        source = source,
        classKind = classKind,
        containingClass = containingClass,
        qualifiedName = qualifiedName,
        simpleName = simpleName,
        fullName = fullName,
        typeParameterList = typeParameterList,
    ) {

    override var hasPrivateConstructor: Boolean = false

    override var stubConstructor: ConstructorItem? = null

    internal lateinit var containingPackage: PackageItem

    internal lateinit var fields: List<FieldItem>

    internal lateinit var methods: MutableList<MethodItem>

    internal lateinit var constructors: List<ConstructorItem>

    internal var hasImplicitDefaultConstructor = false

    private var retention: AnnotationRetention? = null

    override fun constructors(): List<ConstructorItem> = constructors

    override fun containingPackage(): PackageItem =
        containingClass()?.containingPackage() ?: containingPackage

    override fun fields(): List<FieldItem> = fields

    override fun getRetention(): AnnotationRetention {
        retention?.let {
            return it
        }

        if (!isAnnotationType()) {
            error("getRetention() should only be called on annotation classes")
        }

        retention = ClassItem.findRetention(this)
        return retention!!
    }

    override fun hasImplicitDefaultConstructor(): Boolean = hasImplicitDefaultConstructor

    override fun createDefaultConstructor(): ConstructorItem {
        return TurbineConstructorItem.createDefaultConstructor(codebase, this)
    }

    override fun methods(): List<MethodItem> = methods

    /**
     * [PropertyItem]s are kotlin specific and it is unlikely that Turbine will ever support Kotlin
     * so just return an empty list.
     */
    override fun properties(): List<PropertyItem> = emptyList()

    override fun addMethod(method: MethodItem) {
        methods.add(method)
    }
}
