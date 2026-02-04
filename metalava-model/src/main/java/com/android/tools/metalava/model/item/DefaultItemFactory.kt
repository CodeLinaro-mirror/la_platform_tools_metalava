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
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.value.OptionalValueProvider
import com.android.tools.metalava.reporter.FileLocation

/** A factory for creating [Item] instances suitable for use by many models. */
class DefaultItemFactory(
    /** The [DefaultCodebase] to which returned [Item]s will belong. */
    private val codebase: DefaultCodebase,

    /** The default language for [Item]s created by this. */
    private val defaultSourceLanguage: SourceLanguage,

    /** The default [ApiVariantSelectorsFactory] for [Item]s created by this. */
    private val defaultVariantSelectorsFactory: ApiVariantSelectorsFactory,
) {
    /** Create a [PackageItem]. */
    fun createPackageItem(
        fileLocation: FileLocation,
        sourceFile: SourceFile?,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        qualifiedName: String,
        containingPackage: PackageItem?,
        overviewDocumentation: ResourceFile?,
        targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
    ): PackageItem {
        return DefaultPackageItem(
            codebase,
            fileLocation,
            sourceFile,
            // Treat all packages as being Java as Kotlin does not currently provide an equivalent
            // to `package-info.java`.
            SourceLanguage.JAVA,
            targetLanguages,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            qualifiedName,
            containingPackage,
            overviewDocumentation,
        )
    }

    /** Create a [ClassItem]. */
    fun createClassItem(
        fileLocation: FileLocation,
        sourceLanguage: SourceLanguage = defaultSourceLanguage,
        targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory = ItemDocumentation.NONE_FACTORY,
        source: SourceFile? = null,
        classKind: ClassKind,
        containingClass: ClassItem?,
        containingPackage: PackageItem,
        qualifiedName: String = "",
        typeParameterList: TypeParameterList,
        origin: ClassOrigin,
        superClassType: ClassTypeItem?,
        interfaceTypes: List<ClassTypeItem>,
        optionalAliasedType: TypeItem? = null,
        isFileFacade: Boolean = false,
    ) =
        DefaultClassItem(
            codebase,
            fileLocation,
            sourceLanguage,
            targetLanguages,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            source,
            classKind,
            containingClass,
            containingPackage,
            qualifiedName,
            typeParameterList,
            origin,
            superClassType,
            interfaceTypes,
            isFileFacade = isFileFacade,
            optionalAliasedType = optionalAliasedType,
        )

    /** Create a [ConstructorItem]. */
    fun createConstructorItem(
        fileLocation: FileLocation,
        sourceLanguage: SourceLanguage = defaultSourceLanguage,
        targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
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
        isPrimary: Boolean = false,
    ): ConstructorItem =
        DefaultConstructorItem(
            codebase,
            fileLocation,
            sourceLanguage,
            targetLanguages,
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
            isPrimary,
        )

    /** Create a [FieldItem]. */
    fun createFieldItem(
        fileLocation: FileLocation,
        sourceLanguage: SourceLanguage = defaultSourceLanguage,
        targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        name: String,
        containingClass: ClassItem,
        type: TypeItem,
        isEnumConstant: Boolean,
        constantValueProvider: OptionalValueProvider?,
    ): FieldItem =
        DefaultFieldItem(
            codebase,
            fileLocation,
            sourceLanguage,
            targetLanguages,
            defaultVariantSelectorsFactory,
            modifiers,
            documentationFactory,
            name,
            containingClass,
            type,
            isEnumConstant,
            constantValueProvider,
        )

    /** Create a [MethodItem]. */
    fun createMethodItem(
        fileLocation: FileLocation,
        sourceLanguage: SourceLanguage = defaultSourceLanguage,
        targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
        modifiers: BaseModifierList,
        documentationFactory: ItemDocumentationFactory,
        name: String,
        containingClass: ClassItem,
        typeParameterList: TypeParameterList,
        returnType: TypeItem,
        parameterItemsFactory: ParameterItemsFactory,
        throwsTypes: List<ExceptionTypeItem>,
        callableBodyFactory: CallableBodyFactory = CallableBody.UNAVAILABLE_FACTORY,
        defaultValueProvider: OptionalValueProvider?,
        isExtensionMethod: Boolean,
    ): MethodItem =
        DefaultMethodItem(
            codebase,
            fileLocation,
            sourceLanguage,
            targetLanguages,
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
            defaultValueProvider,
            isExtensionMethod,
        )

    /** Create a [ParameterItem]. */
    fun createParameterItem(
        fileLocation: FileLocation,
        sourceLanguage: SourceLanguage = defaultSourceLanguage,
        modifiers: BaseModifierList,
        name: String,
        publicName: String?,
        containingCallable: CallableItem,
        parameterIndex: Int,
        type: TypeItem,
        hasDefaultValue: Boolean,
    ): ParameterItem =
        DefaultParameterItem(
            codebase,
            fileLocation,
            sourceLanguage,
            modifiers,
            name,
            publicName,
            containingCallable,
            parameterIndex,
            type,
            hasDefaultValue,
        )

    /** Create a [PropertyItem]. */
    fun createPropertyItem(
        fileLocation: FileLocation,
        sourceLanguage: SourceLanguage = defaultSourceLanguage,
        documentationFactory: ItemDocumentationFactory = ItemDocumentation.NONE_FACTORY,
        modifiers: BaseModifierList,
        name: String,
        containingClass: ClassItem,
        type: TypeItem,
        receiver: TypeItem?,
        typeParameterList: TypeParameterList,
        setterVisibility: VisibilityLevel?,
        getter: MethodItem? = null,
        setter: MethodItem? = null,
        constructorParameter: ParameterItem? = null,
        backingField: FieldItem? = null,
    ): PropertyItem =
        DefaultPropertyItem(
            codebase,
            fileLocation,
            sourceLanguage,
            documentationFactory,
            defaultVariantSelectorsFactory,
            modifiers,
            name,
            containingClass,
            type,
            getter,
            setter,
            constructorParameter,
            backingField,
            receiver,
            typeParameterList,
            setterVisibility,
        )

    /** Create a [ClassItem] which is a typealias. */
    fun createTypeAliasItem(
        fileLocation: FileLocation,
        modifiers: BaseModifierList,
        qualifiedName: String,
        containingPackage: PackageItem,
        aliasedType: TypeItem,
        typeParameterList: TypeParameterList,
        origin: ClassOrigin,
        documentationFactory: ItemDocumentationFactory = ItemDocumentation.NONE_FACTORY,
    ): DefaultClassItem =
        DefaultClassItem.createTypeAlias(
            codebase,
            fileLocation,
            modifiers,
            documentationFactory,
            defaultVariantSelectorsFactory,
            aliasedType,
            qualifiedName,
            typeParameterList,
            containingPackage,
            origin,
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
            modifiers,
            name,
            isReified,
        )
}
