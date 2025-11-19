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

import com.android.tools.metalava.model.ANDROIDX_COMPOSABLE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.JVM_NAME
import com.android.tools.metalava.model.KOTLIN_DEPRECATED
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultConstructorItem
import com.android.tools.metalava.model.item.DefaultMethodItem
import com.android.tools.metalava.model.item.DefaultParameterItem
import com.android.tools.metalava.model.item.DefaultPropertyItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.PsiFieldItem
import com.android.tools.metalava.model.psi.PsiFileLocation
import com.android.tools.metalava.model.psi.PsiItemDocumentation
import com.android.tools.metalava.model.psi.PsiMethodItem
import com.android.tools.metalava.model.psi.isKotlin
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.reporter.FileLocation
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Adds items to the [codebase] by using the kotlin analysis API to process elements from the
 * [PsiBasedCodebase.mainAnalysisModule] which only have kotlin as a target language.
 */
internal class KaCodebaseAssembler(
    ktFiles: List<KtFile>,
    val codebase: PsiBasedCodebase,
) {
    // TODO(b/407735063): analyze all modules for KMP projects
    private val mainModule =
        codebase.mainAnalysisModule
            ?: error("No main analysis module found for project with Kotlin files")

    private val mainModuleProcessor = KaModuleProcessor(mainModule, codebase)

    /** All packages to analyze from the input files. */
    private val packages = ktFiles.map { it.packageFqName }.toSet().sortedBy { it.asString() }

    /** Analyze the [ktFiles] to add type aliases to the codebase for the [mainModule]. */
    fun createTypeAliases() {
        mainModuleProcessor.createTypeAliases(packages)
    }

    /**
     * Analyze the [ktFiles] to add items to the codebase for the [mainModule] (except type aliases,
     * which are added by [createTypeAliases]).
     */
    fun assemble() {
        mainModuleProcessor.assemble(packages)
    }
}

/**
 * Processor for a single [kaModule] (a regular project has just one module, a KMP projects has
 * several like androidMain, commonMain, etc.) to update the [codebase] based on the kotlin APIs in
 * the module.
 */
internal class KaModuleProcessor(val kaModule: KaModule, val codebase: PsiBasedCodebase) {
    private val kaTypeItemFactory =
        KaTypeItemFactory(
            codebase,
            this,
            codebase.globalTypeItemFactory.typeParameterScope,
        )
    private val kaValueFactory = KaValueFactory(codebase, this, kaTypeItemFactory)
    private val kaModifierFactory = KaModifierFactory(this)

    /** Analyze the [packages] to add type aliases to the codebase for this [kaModule]. */
    fun createTypeAliases(packages: List<FqName>) {
        analyze(kaModule) {
            for (packageName in packages) {
                findPackage(packageName)?.let { packageSymbol ->
                    val packageScope = packageSymbol.packageScope
                    for (typeAliasSymbol in
                        packageScope.classifiers.filterIsInstance<KaTypeAliasSymbol>()) {
                        processTypeAlias(typeAliasSymbol)
                    }
                }
            }
        }
    }

    /**
     * Analyze the [packages] to add items to the codebase for this [kaModule] (except type aliases,
     * which are added by [createTypeAliases]).
     */
    fun assemble(packages: List<FqName>) {
        analyze(kaModule) {
            for (packageName in packages) {
                val packageSymbol = findPackage(packageName)
                packageSymbol?.let { processPackage(it) }
            }
        }
    }

    /** Analyze the classes of the package as well as any top-level callables. */
    private fun KaSession.processPackage(packageSymbol: KaPackageSymbol) {
        val packageScope = packageSymbol.packageScope
        for (classifierSymbol in packageScope.classifiers.filterIsInstance<KaNamedClassSymbol>()) {
            processNamedClass(classifierSymbol)
        }
        for (callableSymbol in packageScope.callables) {
            // For top-level callables, find their containing class in the codebase.
            @OptIn(KaExperimentalApi::class)
            val className = callableSymbol.containingJvmClassName ?: continue
            val classItem = codebase.findClass(className) as? DefaultClassItem ?: continue
            val classTypeItemFactory =
                KaTypeItemFactory(codebase, this@KaModuleProcessor, classItem)
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }
    }

    /** Analyze the elements of the class. */
    private fun KaSession.processNamedClass(classifierSymbol: KaNamedClassSymbol) {
        // Skip Java classes, these won't be kotlin-only.
        if (classifierSymbol.psi?.isKotlin() == false) return
        // Skip classes loaded from the classpath.
        if (classifierSymbol.origin == KaSymbolOrigin.LIBRARY) return
        // Skip private classes since these aren't part of the API surface
        if (classifierSymbol.visibility == KaSymbolVisibility.PRIVATE) return

        // Find the class in the codebase.
        val className = classifierSymbol.classId?.asFqNameString() ?: return
        val classItem = codebase.findClass(className) as? DefaultClassItem ?: return
        val classTypeItemFactory = KaTypeItemFactory(codebase, this@KaModuleProcessor, classItem)

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
        for (nestedClassifierSymbol in
            memberScope.classifiers.filterIsInstance<KaNamedClassSymbol>()) {
            processNamedClass(nestedClassifierSymbol)
        }

        // Process callables defined through a delegate
        val delegateScope = classifierSymbol.delegatedMemberScope
        for (callableSymbol in delegateScope.callables) {
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }
    }

    private fun KaClassifierSymbol.classOrigin(): ClassOrigin {
        return when (origin) {
            KaSymbolOrigin.LIBRARY,
            KaSymbolOrigin.JAVA_LIBRARY -> ClassOrigin.CLASS_PATH
            else -> ClassOrigin.COMMAND_LINE
        }
    }

    /** Creates a [DefaultTypeAliasItem] from the [typeAlias]. */
    private fun processTypeAlias(typeAlias: KaTypeAliasSymbol) {
        val qualifiedName = typeAlias.classId?.asFqNameString() ?: return
        val packageName = qualifiedName.substringBeforeLast(".")
        val containingPackage = codebase.findOrCreatePackage(packageName)

        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                kaTypeItemFactory,
                "for type alias $qualifiedName",
                typeAlias.typeParameters,
            )

        DefaultClassItem.createTypeAlias(
            codebase = codebase,
            fileLocation = PsiFileLocation.fromPsiElement(typeAlias.psi),
            modifiers = kaModifierFactory.createForDeclaration(typeAlias),
            documentationFactory = ItemDocumentation.NONE_FACTORY,
            variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
            aliasedType =
                typeParameterListAndFactory.factory.getGeneralType(typeAlias.expandedType),
            qualifiedName = qualifiedName,
            typeParameterList = typeParameterListAndFactory.typeParameterList,
            containingPackage = containingPackage,
            origin = typeAlias.classOrigin(),
        )
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
                        kaReceiverParameter = null,
                        isSuspend = false,
                        returnType = containingClass.type(),
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
            is KaNamedFunctionSymbol ->
                processFunction(callableSymbol, containingClass, enclosingTypeItemFactory)
            else -> return
        }
    }

    /**
     * Whether to create a method item based on the [functionSymbol].
     *
     * If this condition is updated, the one in PsiCodebaseAssembler determining which methods not
     * to create needs to be updated too.
     */
    private fun KaSession.shouldGenerateMethod(functionSymbol: KaNamedFunctionSymbol): Boolean {
        // Don't generate hidden functions since they cannot be resolved from source.
        if (functionSymbol.isDeprecatedHidden()) return false
        // For an expect/actual function, there are separate KaNamedFunctionSymbols for the expect
        // and actual. Only create a MethodItem based on the actual.
        if (functionSymbol.isExpect) return false
        // Generate delegate functions.
        if (functionSymbol.origin == KaSymbolOrigin.DELEGATED) return true
        // Skip generated equals and hashCode methods, when they aren't implemented in source.
        if (
            functionSymbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED &&
                functionSymbol.name.identifierOrNullIfSpecial?.let { name ->
                    name == "equals" || name == "hashCode"
                } ?: false
        )
            return false

        // Composable APIs will have a different signature in bytecode than in source, so the source
        // signature should be generated here as kotlin-only.
        if (functionSymbol.annotations.any { it.classId?.asFqNameString() == ANDROIDX_COMPOSABLE })
            return true

        // Generate functions annotated with JvmName.
        if (functionSymbol.annotations.any { it.classId?.asFqNameString() == JVM_NAME }) return true

        // If a constructor has a corresponding UElement it generally shouldn't be created as kotlin
        // only, but with K1 value class types weren't handled differently from other types so there
        // might be a UElement for a constructor using a value class type even though it should be
        // kotlin only.
        if (
            functionSymbol.existsAsUElement() &&
                !hasValueClassTypeParameter(functionSymbol) &&
                !isValueClassType(functionSymbol.returnType) &&
                functionSymbol.receiverType?.let { isValueClassType(it) } != true
        )
            return false

        return true
    }

    /** Constructs a method from the [functionSymbol] and adds it to the [containingClass]. */
    private fun KaSession.processFunction(
        functionSymbol: KaNamedFunctionSymbol,
        containingClass: DefaultClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory
    ) {
        if (!shouldGenerateMethod(functionSymbol)) return

        val name = functionSymbol.name.identifier
        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                enclosingTypeItemFactory,
                "for method $name",
                functionSymbol.typeParameters
            )

        // Create the jvm signature of the method: in addition to the regular parameters, if this is
        // an extension function a parameter is added for the receiver, and if this is a suspend
        // function a parameter is added for the continuation.
        val parameterCount =
            functionSymbol.valueParameters.size +
                (if (functionSymbol.receiverParameter != null) 1 else 0) +
                (if (functionSymbol.isSuspend) 1 else 0)
        val fingerprint = MethodFingerprint(name, parameterCount)

        val originalReturnType =
            typeParameterListAndFactory.factory.getMethodReturnType(
                functionSymbol.returnType,
                emptyList(),
                fingerprint,
                containingClass.isAnnotationType()
            )
        // For suspend functions, the jvm signature will have a nullable object return type (the
        // source return type is used for the generated continuation parameter).
        val returnType =
            if (functionSymbol.isSuspend) {
                typeParameterListAndFactory.factory.createObjectTypeItem()
            } else {
                originalReturnType
            }

        val targetLanguages =
            if (functionSymbol.origin == KaSymbolOrigin.DELEGATED) {
                // Note: it could be possible for there to be a method from a delegate that is not
                // accessible from Java, for instance if it used a value class type. However, it has
                // been difficult to find a reliable way of telling if the delegate method can be
                // used from Java without special casing certain situations (it should be possible
                // to do by looking at the psi of the KaNamedFunctionSymbol or by checking the super
                // methods metalava has created for the methodItem created below, but those aren't
                // working when using mapped kotlin collections types).
                TargetLanguageSet.ALL
            } else {
                TargetLanguageSet.KOTLIN_ONLY
            }

        val modifiers = kaModifierFactory.createForFunction(functionSymbol, containingClass)
        val methodItem =
            DefaultMethodItem(
                codebase = codebase,
                fileLocation = PsiFileLocation.fromPsiElement(functionSymbol.psi),
                sourceLanguage = SourceLanguage.KOTLIN,
                targetLanguages = targetLanguages,
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterListAndFactory.typeParameterList,
                returnType = returnType,
                parameterItemsFactory = { callableItem ->
                    parameterList(
                        functionSymbol.valueParameters,
                        callableItem,
                        typeParameterListAndFactory.factory,
                        functionSymbol.receiverParameter,
                        functionSymbol.isSuspend,
                        originalReturnType,
                        fingerprint,
                    )
                },
                throwsTypes = throwsTypesFromModifiers(modifiers),
                callableBodyFactory = CallableBody.UNAVAILABLE_FACTORY,
                // The default value provider is only used for annotation value accessors, but those
                // won't be generated here since they'll be usable from Java.
                defaultValueProvider = null,
                isExtensionMethod = functionSymbol.receiverParameter != null,
            )

        // It is possible that a method using JvmName has the same signature in Java and Kotlin, so
        // check that there isn't already a method with a matching signature. If there is, make sure
        // it is marked as usable from Kotlin, and don't add the duplicate method.
        val jvmName = methodItem.findJvmNameFromAnnotation()
        if (jvmName != null) {
            val existingMethod =
                methodItem.containingClass().methods().firstOrNull {
                    it.name() == methodItem.name() &&
                        it.name() == jvmName &&
                        it.returnType().toErasedTypeString() ==
                            methodItem.returnType().toErasedTypeString() &&
                        it.parameters().size == methodItem.parameters().size &&
                        it.parameters().zip(methodItem.parameters()).all { (p1, p2) ->
                            p1.type().toErasedTypeString() == p2.type().toErasedTypeString()
                        }
                }
            if (existingMethod != null) {
                existingMethod.targetLanguages += TargetLanguage.KOTLIN
                return
            }
        }

        containingClass.addMethod(methodItem)
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

        // Don't generate deprecation level hidden properties, which can't be used from source.
        if (propertySymbol.isDeprecatedHidden()) return

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

        // If a property is defined in a companion object, the backing field will be found in the
        // containing class of the companion, not the companion itself.
        val backingField =
            if (propertySymbol.hasBackingField) {
                val classWithField =
                    if (containingClass.modifiers.isCompanion()) {
                        containingClass.containingClass()!!
                    } else {
                        containingClass
                    }
                classWithField.findField(propertySymbol.name.identifier) as? PsiFieldItem
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

        val modifiers =
            kaModifierFactory.createForProperty(
                propertySymbol,
                containingClass,
            )
        kaModifierFactory.updatePropertyAccessors(modifiers, getter, setter, backingField)
        val propertyItem =
            DefaultPropertyItem(
                codebase = codebase,
                fileLocation = PsiFileLocation.fromPsiElement(propertySymbol.psi),
                sourceLanguage = SourceLanguage.KOTLIN,
                documentationFactory = propertySymbol.getDocumentation(),
                variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
                modifiers = modifiers,
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
        kaReceiverParameter: KaReceiverParameterSymbol?,
        isSuspend: Boolean,
        returnType: TypeItem,
        fingerprint: MethodFingerprint,
    ): List<ParameterItem> {
        // If there is a receiver, convert it to a parameter item.
        val receiverParameter =
            kaReceiverParameter?.let {
                val type =
                    enclosingTypeItemFactory.getMethodParameterType(
                        underlyingParameterType = it.returnType,
                        itemAnnotations = containingCallable.modifiers.annotations(),
                        fingerprint = fingerprint,
                        parameterIndex = 0,
                        isVarArg = false,
                    )

                DefaultParameterItem(
                    codebase = codebase,
                    fileLocation = PsiFileLocation.fromPsiElement(it.psi),
                    sourceLanguage = SourceLanguage.KOTLIN,
                    modifiers = kaModifierFactory.createForReceiverParameter(it),
                    name = "receiver",
                    publicName = null,
                    containingCallable = containingCallable,
                    parameterIndex = 0,
                    type = type,
                    hasDefaultValue = false,
                )
            }
        val regularParameters =
            kaParameters.mapIndexed { sourceIndex, parameterSymbol ->
                // If there is a receiver, it becomes the first parameter, so shift the index of all
                // other parameters
                val index = if (receiverParameter != null) 1 + sourceIndex else sourceIndex
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
                    modifiers = kaModifierFactory.createForValueParameter(parameterSymbol),
                    name = parameterSymbol.name.identifier,
                    publicName = parameterSymbol.name.identifierOrNullIfSpecial,
                    containingCallable = containingCallable,
                    parameterIndex = index,
                    type = type,
                    hasDefaultValue = parameterSymbol.hasDefaultValue,
                )
            }

        // If this is a suspend function, there is an extra continuation parameter added to the end.
        val continuationParameter =
            if (isSuspend) {
                val index = regularParameters.size + (receiverParameter?.let { 1 } ?: 0)
                DefaultParameterItem(
                    codebase = codebase,
                    fileLocation = FileLocation.UNKNOWN,
                    sourceLanguage = SourceLanguage.KOTLIN,
                    modifiers =
                        createImmutableModifiers(VisibilityLevel.PACKAGE_PRIVATE, emptyList()),
                    name = "\$completion",
                    publicName = null,
                    containingCallable = containingCallable,
                    parameterIndex = index,
                    type = enclosingTypeItemFactory.createContinuationType(returnType),
                    hasDefaultValue = false,
                )
            } else {
                null
            }

        return listOfNotNull(receiverParameter) +
            regularParameters +
            listOfNotNull(continuationParameter)
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
}
