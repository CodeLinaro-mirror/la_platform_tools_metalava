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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableBodyFactory
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

/**
 * A lambda that when passed the [Item] will return the public name, or null if there is not one.
 */
typealias PublicNameProvider = (Item) -> String?

/** A factory for creating [Item] instances suitable for use by many models. */
class DefaultItemFactory(
    /** The [DefaultCodebase] to which returned [Item]s will belong. */
    private val codebase: DefaultCodebase,

    /** The default language for [Item]s created by this. */
    private val defaultItemLanguage: ItemLanguage,

    /** The default [ApiVariantSelectorsFactory] for [Item]s created by this. */
    private val defaultVariantSelectorsFactory: ApiVariantSelectorsFactory,
) {
    /** Create a [PackageItem]. */
    fun createPackageItem(
        fileLocation: FileLocation,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        qualifiedName: String,
        containingPackage: PackageItem?,
        overviewDocumentation: ResourceFile?,
    ): DefaultPackageItem {
        return DefaultPackageItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            qualifiedName,
            containingPackage,
            overviewDocumentation,
        )
    }

    /** Create a [ConstructorItem]. */
    fun createClassItem(
        fileLocation: FileLocation,
        itemLanguage: ItemLanguage = defaultItemLanguage,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory = ItemDocumentation.NONE_FACTORY,
        source: SourceFile? = null,
        classKind: ClassKind,
        containingClass: ClassItem?,
        containingPackage: PackageItem,
        qualifiedName: String = "",
        typeParameterList: TypeParameterList,
        isFromClassPath: Boolean,
        superClassType: ClassTypeItem?,
        interfaceTypes: List<ClassTypeItem>,
    ) =
        DefaultClassItem(
            codebase,
            fileLocation,
            itemLanguage,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            source,
            classKind,
            containingClass,
            containingPackage,
            qualifiedName,
            typeParameterList,
            isFromClassPath,
            superClassType,
            interfaceTypes,
        )

    /** Create a [ConstructorItem]. */
    fun createConstructorItem(
        fileLocation: FileLocation,
        itemLanguage: ItemLanguage = defaultItemLanguage,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        name: String,
        containingClass: ClassItem,
        typeParameterList: TypeParameterList,
        returnType: ClassTypeItem,
        parameterItemsFactory: ParameterItemsFactory,
        throwsTypes: List<ExceptionTypeItem>,
        callableBodyFactory: CallableBodyFactory = CallableBody.UNAVAILABLE_FACTORY,
        implicitConstructor: Boolean,
    ): ConstructorItem =
        DefaultConstructorItem(
            codebase,
            fileLocation,
            itemLanguage,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            name,
            containingClass,
            typeParameterList,
            returnType,
            parameterItemsFactory,
            throwsTypes,
            callableBodyFactory,
            implicitConstructor,
        )

    /** Create a [FieldItem]. */
    fun createFieldItem(
        fileLocation: FileLocation,
        itemLanguage: ItemLanguage = defaultItemLanguage,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        name: String,
        containingClass: ClassItem,
        type: TypeItem,
        isEnumConstant: Boolean,
        fieldValue: FieldValue?,
    ): FieldItem =
        DefaultFieldItem(
            codebase,
            fileLocation,
            itemLanguage,
            defaultVariantSelectorsFactory,
            modifiers,
            documentationFactory,
            name,
            containingClass,
            type,
            isEnumConstant,
            fieldValue,
        )

    /** Create a [MethodItem]. */
    fun createMethodItem(
        fileLocation: FileLocation,
        itemLanguage: ItemLanguage = defaultItemLanguage,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        name: String,
        containingClass: ClassItem,
        typeParameterList: TypeParameterList,
        returnType: TypeItem,
        parameterItemsFactory: ParameterItemsFactory,
        throwsTypes: List<ExceptionTypeItem>,
        callableBodyFactory: CallableBodyFactory = CallableBody.UNAVAILABLE_FACTORY,
        annotationDefault: String,
    ): MethodItem =
        DefaultMethodItem(
            codebase,
            fileLocation,
            itemLanguage,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            name,
            containingClass,
            typeParameterList,
            returnType,
            parameterItemsFactory,
            throwsTypes,
            callableBodyFactory,
            annotationDefault,
        )

    /** Create a [ParameterItem]. */
    fun createParameterItem(
        fileLocation: FileLocation,
        itemLanguage: ItemLanguage = defaultItemLanguage,
        modifiers: BaseModifierList,
        name: String,
        publicNameProvider: PublicNameProvider,
        containingCallable: CallableItem,
        parameterIndex: Int,
        type: TypeItem,
        defaultValueFactory: DefaultValueFactory,
    ): ParameterItem =
        DefaultParameterItem(
            codebase,
            fileLocation,
            itemLanguage,
            modifiers,
            name,
            publicNameProvider,
            containingCallable,
            parameterIndex,
            type,
            defaultValueFactory,
        )

    /** Create a [PropertyItem]. */
    fun createPropertyItem(
        fileLocation: FileLocation,
        itemLanguage: ItemLanguage = defaultItemLanguage,
        documentationFactory: ItemDocumentationFactory = ItemDocumentation.NONE_FACTORY,
        modifiers: BaseModifierList,
        name: String,
        containingClass: ClassItem,
        type: TypeItem,
    ): PropertyItem =
        DefaultPropertyItem(
            codebase,
            fileLocation,
            itemLanguage,
            documentationFactory,
            defaultVariantSelectorsFactory,
            modifiers,
            name,
            containingClass,
            type,
        )

    /**
     * Create a [DefaultTypeParameterItem].
     *
     * This returns [DefaultTypeParameterItem] because access is needed to its
     * [DefaultTypeParameterItem.bounds] after creation as full creation is a two stage process due
     * to cyclical dependencies between [DefaultTypeParameterItem] in a type parameters list.
     *
     * TODO(b/351410134): Provide support in this factory for two stage initialization.
     */
    fun createTypeParameterItem(
        modifiers: BaseModifierList,
        name: String,
        isReified: Boolean,
    ) =
        DefaultTypeParameterItem(
            codebase,
            defaultItemLanguage,
            modifiers,
            name,
            isReified,
        )
}
