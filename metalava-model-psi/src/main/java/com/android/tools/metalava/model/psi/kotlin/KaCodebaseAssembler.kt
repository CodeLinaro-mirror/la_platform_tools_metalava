/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.psi.kotlin

import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.KOTLIN_DEPRECATED
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultConstructorItem
import com.android.tools.metalava.model.item.DefaultParameterItem
import com.android.tools.metalava.model.item.DefaultPropertyItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.ParameterDefaultValue
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.PsiFieldItem
import com.android.tools.metalava.model.psi.PsiFileLocation
import com.android.tools.metalava.model.psi.PsiItemDocumentation
import com.android.tools.metalava.model.psi.PsiMethodItem
import com.android.tools.metalava.model.psi.isKotlin
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.ClassObjectValue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Adds items to the [codebase] by using the kotlin analysis API to process elements from the
 * [kaModule] which only have kotlin as a target language.
 */
internal class KaCodebaseAssembler(val codebase: PsiBasedCodebase, val kaModule: KaModule) {
    private val kaTypeItemFactory =
        KaTypeItemFactory(
            codebase,
            this,
            codebase.globalTypeItemFactory.typeParameterScope,
        )
    private val kaValueFactory = KaValueFactory(codebase, this, kaTypeItemFactory)
    private val kaModifierFactory = KaModifierFactory(this)

    /** Analyze the [ktFiles] to add items to the codebase for this [kaModule]. */
    fun assemble(ktFiles: List<KtFile>) {
        analyze(kaModule) {
            val packages = ktFiles.map { it.packageFqName }.toSet().sortedBy { it.asString() }
            for (packageName in packages) {
                val packageSymbol = findPackage(packageName)
                packageSymbol?.let { processPackage(it) }
            }
        }
    }

    /** Analyze the classes of the package as well as any top-level callables. */
    private fun KaSession.processPackage(packageSymbol: KaPackageSymbol) {
        val packageScope = packageSymbol.packageScope
        for (classifierSymbol in packageScope.classifiers) {
            processClassifier(classifierSymbol)
        }
        for (callableSymbol in packageScope.callables) {
            // For top-level callables, find their containing class in the codebase.
            @OptIn(KaExperimentalApi::class)
            val className = callableSymbol.containingJvmClassName ?: continue
            val classItem = codebase.findClass(className) as? DefaultClassItem ?: continue
            val classTypeItemFactory =
                KaTypeItemFactory(codebase, this@KaCodebaseAssembler, classItem)
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }
    }

    /** Analyze the elements of the class. */
    private fun KaSession.processClassifier(classifierSymbol: KaClassifierSymbol) {
        // Skip Java classes, these won't be kotlin-only.
        if (classifierSymbol.psi?.isKotlin() == false) return
        // Skip classes loaded from the classpath.
        if (classifierSymbol.origin == KaSymbolOrigin.LIBRARY) return
        // Skip private classes since these aren't part of the API surface
        if (classifierSymbol.visibility == KaSymbolVisibility.PRIVATE) return
        when (classifierSymbol) {
            is KaNamedClassSymbol -> {
                // Find the class in the codebase.
                val className = classifierSymbol.classId?.asFqNameString() ?: return
                val classItem = codebase.findClass(className) as? DefaultClassItem ?: return
                val classTypeItemFactory =
                    KaTypeItemFactory(codebase, this@KaCodebaseAssembler, classItem)

                // The combined declared member scope contains both static and non-static members.
                val memberScope = classifierSymbol.combinedDeclaredMemberScope
                for (constructorSymbol in memberScope.constructors) {
                    processConstructor(constructorSymbol, classItem, classTypeItemFactory)
                }
                for (callableSymbol in memberScope.callables) {
                    // K1 includes delegate symbols in the combinedDeclaredMemberScope, K2 does not.
                    // Don't add delegate symbols here because they're processed from the
                    // delegatedMemberScope below, and they shouldn't be duplicated for K1.
                    if (callableSymbol.origin != KaSymbolOrigin.DELEGATED) {
                        processCallable(callableSymbol, classItem, classTypeItemFactory)
                    }
                }
                for (nestedClassifierSymbol in memberScope.classifiers) {
                    processClassifier(nestedClassifierSymbol)
                }

                // Process callables defined through a delegate
                val delegateScope = classifierSymbol.delegatedMemberScope
                for (callableSymbol in delegateScope.callables) {
                    processCallable(callableSymbol, classItem, classTypeItemFactory)
                }
            }
            is KaTypeAliasSymbol,
            is KaTypeParameterSymbol,
            is KaAnonymousObjectSymbol -> return
        }
    }

    /**
     * Whether to create a constructor item in the [containingClass] based on the
     * [constructorSymbol].
     */
    private fun KaSession.shouldGenerateConstructor(
        constructorSymbol: KaConstructorSymbol,
        containingClass: ClassItem,
    ): Boolean {
        // Value class primary constructors are always kotlin only.
        if (constructorSymbol.isPrimary && containingClass.modifiers.isValue()) return true
        // If a constructor has a corresponding UElement it generally shouldn't be created as kotlin
        // only, but with K1 value class types weren't handled differently from other types so there
        // might be a UElement for a constructor using a value class type even though it should be
        // kotlin only.
        if (constructorSymbol.existsAsUElement() && !hasValueClassTypeParameter(constructorSymbol))
            return false
        // Deprecation level hidden items can't be resolved from source.
        if (constructorSymbol.isDeprecatedHidden()) return false
        // Items are generated for actual constructors, and aren't needed for expects.
        if (constructorSymbol.isExpect) return false
        return true
    }

    /**
     * Constructs a constructor from the [constructorSymbol] and adds it to the [containingClass].
     */
    private fun KaSession.processConstructor(
        constructorSymbol: KaConstructorSymbol,
        containingClass: DefaultClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory,
    ) {
        if (!shouldGenerateConstructor(constructorSymbol, containingClass)) return

        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                enclosingTypeItemFactory,
                "for constructor ${containingClass.simpleName()}",
                constructorSymbol.typeParameters
            )

        val modifiers = kaModifierFactory.createForDeclaration(constructorSymbol)
        val constructorItem =
            DefaultConstructorItem(
                codebase = codebase,
                fileLocation = PsiFileLocation.fromPsiElement(constructorSymbol.psi),
                sourceLanguage = SourceLanguage.KOTLIN,
                targetLanguages = TargetLanguageSet.KOTLIN_ONLY,
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
                name = containingClass.simpleName(),
                containingClass = containingClass,
                typeParameterList = typeParameterListAndFactory.typeParameterList,
                returnType = containingClass.type(),
                parameterItemsFactory = { callableItem ->
                    parameterList(
                        constructorSymbol.valueParameters,
                        callableItem,
                        typeParameterListAndFactory.factory,
                        MethodFingerprint(
                            containingClass.simpleName(),
                            constructorSymbol.valueParameters.count()
                        )
                    )
                },
                throwsTypes = throwsTypesFromModifiers(modifiers),
                callableBodyFactory = CallableBody.UNAVAILABLE_FACTORY,
                implicitConstructor = false,
                isPrimary = constructorSymbol.isPrimary,
            )
        containingClass.addConstructor(constructorItem)
    }

    /** Processes a [KaCallableSymbol], which could be a property or function. */
    private fun KaSession.processCallable(
        callableSymbol: KaCallableSymbol,
        containingClass: DefaultClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory,
    ) {
        // Skip callables loaded from the classpath.
        if (callableSymbol.origin == KaSymbolOrigin.LIBRARY) return
        // TODO(b/421201575): currently, private properties need to be processed in order to reset
        //  the visibility of the property accessors due to a uast bug for value class types

        when (callableSymbol) {
            is KaPropertySymbol ->
                processProperty(callableSymbol, containingClass, enclosingTypeItemFactory)
            // TODO(b/421201575): process functions
            else -> return
        }
    }

    /** Constructs a property from the [propertySymbol] and adds it to the [containingClass]. */
    private fun KaSession.processProperty(
        propertySymbol: KaPropertySymbol,
        containingClass: DefaultClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory
    ) {
        // Skip creating enum entry properties, which exist for all enums.
        if (
            containingClass.isEnum() &&
                propertySymbol.name.identifier == "entries" &&
                propertySymbol.receiverType == null
        )
            return

        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                enclosingTypeItemFactory,
                "for property ${propertySymbol.name}",
                propertySymbol.typeParameters
            )
        val typeFactory = typeParameterListAndFactory.factory
        // Find the type of the property, and check that it aligns with the type it is overriding,
        // if applicable (a primitive type overriding a variable type should be boxed).
        val type =
            typeFactory.handleOverrideBoxing(
                typeFactory.getGeneralType(propertySymbol.returnType),
                propertySymbol.allOverriddenSymbols.map { it.returnType },
            )

        val receiverType = propertySymbol.receiverType?.let { typeFactory.getGeneralType(it) }

        // Private properties currently still need to be processed when they use a value class type
        // to reset incorrect nullability on the accessors from psi. But other private properties
        // can be skipped since they aren't part of the API surface.
        if (
            propertySymbol.visibility == KaSymbolVisibility.PRIVATE &&
                !type.isValueClassType() &&
                receiverType?.isValueClassType() != true
        )
            return

        // To find the accessors of the property, use the inlined type if this property has a value
        // class type. This is needed for now because the property accessors are being created with
        // psi, which inlines the type.
        val typeForAccessor = typeFactory.inlineTypeIfNeeded(propertySymbol.returnType, type)
        val possiblyInlinedReceiverType =
            receiverType?.let {
                typeFactory.inlineTypeIfNeeded(propertySymbol.receiverType!!, receiverType)
            }
        // Similar to above, but due to b/385148821, if a property is an extension on a value class
        // type or is deprecated level hidden, the psi accessors drop the receiver entirely, so only
        // use the receiver type to find accessors if it is not a value class type or hidden.
        val receiverTypeForAccessor =
            if (receiverType?.isValueClassType() == true || propertySymbol.isDeprecatedHidden()) {
                null
            } else {
                possiblyInlinedReceiverType
            }

        val getter =
            propertySymbol.getter?.let {
                // javaGetterName does not work for annotation property accessors, which should have
                // the same name as the property
                val getterName =
                    if (containingClass.isAnnotationType()) {
                        propertySymbol.name.identifier
                    } else {
                        @OptIn(KaExperimentalApi::class) propertySymbol.javaGetterName.identifier
                    }
                findAccessor(
                    getterName,
                    containingClass,
                    typeForAccessor,
                    receiverTypeForAccessor,
                    isGetter = true,
                    it.visibility,
                )
            }
        val setter =
            propertySymbol.setter?.let {
                findAccessor(
                    @OptIn(KaExperimentalApi::class) propertySymbol.javaSetterName!!.identifier,
                    containingClass,
                    typeForAccessor,
                    receiverTypeForAccessor,
                    isGetter = false,
                    it.visibility,
                )
            }

        val backingField =
            if (propertySymbol.hasBackingField) {
                containingClass.findField(propertySymbol.name.identifier) as? PsiFieldItem
            } else {
                null
            }

        val constructorParameter =
            if (propertySymbol.isFromPrimaryConstructor) {
                containingClass
                    .constructors()
                    .filter { it.isPrimary }
                    // For a source constructor with @JvmOverloads, there may be multiple
                    // constructor items labeled as primary. Find the overload with all parameters,
                    // so that it is guaranteed to include the property parameter.
                    .maxByOrNull { it.parameters().size }
                    ?.parameters()
                    ?.firstOrNull { it.name() == propertySymbol.name.identifier }
                    as? DefaultParameterItem
            } else {
                null
            }

        val propertyItem =
            DefaultPropertyItem(
                codebase = codebase,
                fileLocation = PsiFileLocation.fromPsiElement(propertySymbol.psi),
                sourceLanguage = SourceLanguage.KOTLIN,
                documentationFactory = propertySymbol.getDocumentation(),
                variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
                modifiers =
                    kaModifierFactory.createForProperty(
                        propertySymbol,
                        containingClass,
                        getter,
                        setter
                    ),
                name = propertySymbol.name.identifier,
                containingClass = containingClass,
                type = type,
                getter = getter,
                setter = setter,
                constructorParameter = constructorParameter,
                backingField = backingField,
                receiver = receiverType,
                typeParameterList = typeParameterListAndFactory.typeParameterList,
            )
        getter?.property = propertyItem
        setter?.property = propertyItem
        backingField?.property = propertyItem
        constructorParameter?.property = propertyItem
        containingClass.addProperty(propertyItem)
    }

    /** Converts the [kaParameters] to [ParameterItem]s for the [containingCallable]. */
    private fun parameterList(
        kaParameters: List<KaValueParameterSymbol>,
        containingCallable: CallableItem,
        enclosingTypeItemFactory: KaTypeItemFactory,
        fingerprint: MethodFingerprint,
    ): List<ParameterItem> {
        return kaParameters.mapIndexed { index, parameterSymbol ->
            val type =
                enclosingTypeItemFactory.getMethodParameterType(
                    underlyingParameterType = parameterSymbol.returnType,
                    itemAnnotations = containingCallable.modifiers.annotations(),
                    fingerprint = fingerprint,
                    parameterIndex = index,
                    isVarArg = parameterSymbol.isVararg,
                )

            DefaultParameterItem(
                codebase = codebase,
                fileLocation = PsiFileLocation.fromPsiElement(parameterSymbol.psi),
                sourceLanguage = SourceLanguage.KOTLIN,
                modifiers = kaModifierFactory.createForParameter(parameterSymbol),
                name = parameterSymbol.name.identifier,
                publicNameProvider = { parameterSymbol.name.identifierOrNullIfSpecial },
                containingCallable = containingCallable,
                parameterIndex = index,
                type = type,
                defaultValueFactory = {
                    if (parameterSymbol.hasDefaultValue) {
                        ParameterDefaultValue.UNKNOWN
                    } else {
                        ParameterDefaultValue.NONE
                    }
                },
            )
        }
    }

    /** Finds any exception types listed with the @Throws annotation. */
    private fun throwsTypesFromModifiers(modifiers: MutableModifierList): List<ExceptionTypeItem> {
        return modifiers
            .annotations()
            .filter { annotationItem ->
                annotationItem.qualifiedName == "kotlin.Throws" ||
                    annotationItem.qualifiedName == "kotlin.jvm.Throws"
            }
            .flatMap { annotationItem ->
                annotationItem.attributes
                    .singleOrNull { annotationAttribute ->
                        annotationAttribute.name == "exceptionClasses"
                    }
                    ?.value
                    ?.let { exceptionClasses ->
                        (exceptionClasses as? ArrayValue)?.elements?.mapNotNull { exceptionClass ->
                            (exceptionClass as? ClassObjectValue)?.typeItem as? ExceptionTypeItem
                        }
                    } ?: emptyList()
            }
    }

    /** Checks whether an element is deprecated with [DeprecationLevel.HIDDEN]. */
    private fun KaAnnotated.isDeprecatedHidden(): Boolean {
        return annotations.any { kaAnnotation ->
            kaAnnotation.classId?.asFqNameString() == KOTLIN_DEPRECATED &&
                (kaAnnotation.arguments
                        .singleOrNull { it.name.identifierOrNullIfSpecial == "level" }
                        ?.expression as? KaAnnotationValue.EnumEntryValue)
                    ?.callableId
                    ?.callableName
                    ?.identifierOrNullIfSpecial == "HIDDEN"
        }
    }

    /** Creates documentation for the symbol through psi, if possible. */
    private fun KaSymbol.getDocumentation(): ItemDocumentationFactory {
        return psi?.let { PsiItemDocumentation.factory(it, codebase) }
            ?: ItemDocumentation.NONE_FACTORY
    }

    /**
     * Finds a property accessor with the given [name] in the [containingClass], based on the
     * [propertyType] and [receiverType].
     */
    private fun findAccessor(
        name: String,
        containingClass: ClassItem,
        propertyType: TypeItem,
        receiverType: TypeItem?,
        isGetter: Boolean,
        visibility: KaSymbolVisibility,
    ): PsiMethodItem? {
        val parameters =
            listOfNotNull(
                    // Both the getter and setter have the receiver as the first parameter
                    receiverType,
                    // The setter also has the property type as a parameter
                    if (isGetter) {
                        null
                    } else {
                        propertyType
                    }
                )
                // Compare types by erased string to work around differences like `List<String>` vs
                // `List<? extends String>` that can exist in the two representations.
                .map { it.toErasedTypeString() }

        return containingClass.methods().firstOrNull { methodItem ->
            // Find a method with the right name, but if the property is internal, the accessor name
            // will be mangled with a `$`
            (methodItem.name() == name ||
                (visibility == KaSymbolVisibility.INTERNAL &&
                    methodItem.name().startsWith("$name\$"))) &&
                methodItem.isKotlinProperty() &&
                // Due to value class type inlining, some accessors might end up with identical
                // signatures. Pick one for each matching property.
                methodItem.property == null &&
                methodItem.parameters().map { it.type().toErasedTypeString() } == parameters
        } as? PsiMethodItem
    }

    /**
     * Creates a list of type parameters from the [typeParameterSymbols] and a type factory based on
     * the [enclosingTypeItemFactory].
     */
    private fun typeParameterListAndFactory(
        enclosingTypeItemFactory: KaTypeItemFactory,
        scopeDescription: String,
        typeParameterSymbols: List<KaTypeParameterSymbol>,
    ): TypeParameterListAndFactory<KaTypeItemFactory> {
        return DefaultTypeParameterList.createTypeParameterItemsAndFactory(
            enclosingTypeItemFactory,
            scopeDescription,
            typeParameterSymbols,
            // Construct type parameter items from the symbols
            { typeParameterSymbol ->
                DefaultTypeParameterItem(
                    codebase,
                    kaModifierFactory.createForDeclaration(typeParameterSymbol),
                    typeParameterSymbol.name.identifier,
                    typeParameterSymbol.isReified,
                )
            },
            // Get the bounds of the type parameter from the symbols
            { typeItemFactory, typeParameterSymbol ->
                typeParameterSymbol.upperBounds.map { typeItemFactory.getBoundsType(it) }
            },
        )
    }

    /** Creates an annotation from the [kaAnnotation], if possible. */
    fun createAnnotation(kaAnnotation: KaAnnotation): AnnotationItem? {
        val qualifiedName = kaAnnotation.classId?.asFqNameString() ?: return null
        return AnnotationItem.createAttributesLazily(
            codebase,
            fileLocation = PsiFileLocation.fromPsiElement(kaAnnotation.psi),
            originalName = qualifiedName,
            attributesGetter = {
                // Find the psi attributes as well, if they exist, because for inlined constant
                // values they have information about the field used that the analysis api does not.
                val psiAttributes =
                    (kaAnnotation.psi as? KtAnnotationEntry)?.valueArguments ?: emptyList()
                kaAnnotation.arguments.mapIndexed { i, kaNamedAnnotationValue ->
                    val psiAttribute = psiAttributes.getOrNull(i)
                    AnnotationAttribute.createLazyAttribute(
                        name = kaNamedAnnotationValue.name.identifier,
                        valueProvider =
                            kaValueFactory.providerForAnnotationValue(
                                kaNamedAnnotationValue.expression,
                                psiAttribute,
                            )
                    )
                }
            }
        )
    }

    /**
     * Checks if there are any UElements corresponding to the symbol. If there are, this symbol
     * usually shouldn't have a kotlin-only item generated from it.
     */
    private fun KaSymbol.existsAsUElement() =
        (psi as? KtElement)?.toLightElements()?.isNotEmpty() == true

    /**
     * Checks if the [kaType] represents a value class. Value classes generally cannot be used from
     * java.
     */
    private fun KaSession.isValueClassType(kaType: KaType) =
        (kaType.expandedSymbol as? KaNamedClassSymbol)?.isInline == true

    /**
     * Checks if any of the parameters of the [functionSymbol] are value class types. If they are,
     * there should be a kotlin-only item generated from the function, since value class types can't
     * generally be used from java.
     */
    private fun KaSession.hasValueClassTypeParameter(functionSymbol: KaFunctionSymbol) =
        functionSymbol.valueParameters.any { isValueClassType(it.returnType) }

    companion object {
        /** Adds kotlin-only elements to the [codebase] by analyzing the [ktFiles]. */
        fun assembleFromKotlin(ktFiles: List<KtFile>, codebase: PsiBasedCodebase) {
            if (ktFiles.isEmpty()) return
            // TODO(b/407735063): analyze all modules for KMP projects
            val analysisModule =
                codebase.mainAnalysisModule
                    ?: error("No main analysis module found for project with Kotlin files")
            val assembler = KaCodebaseAssembler(codebase, analysisModule)
            assembler.assemble(ktFiles)
        }
    }
}
