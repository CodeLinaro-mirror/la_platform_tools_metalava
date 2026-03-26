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
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.JVM_NAME
import com.android.tools.metalava.model.KOTLIN_DEPRECATED
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.WellKnownTypes
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.PackageInfo
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.PsiFileLocation
import com.android.tools.metalava.model.psi.createItemDocumentation
import com.android.tools.metalava.model.psi.isKotlin
import com.android.tools.metalava.model.source.toItemDocumentationFactory
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeParameterListAndFactory
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import java.io.File
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertyAccessorSymbol
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
import org.jetbrains.kotlin.name.ClassId
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
    /**
     * When creating a regular [Codebase], only the main analysis module is processed. All modules
     * are analyzed when running [assembleMultiplatform].
     */
    private val mainModule =
        codebase.mainAnalysisModule
            ?: error("No main analysis module found for project with Kotlin files")

    private val mainModuleProcessor = KaModuleProcessor(mainModule, codebase)

    /** All packages to analyze from the input files. */
    private val packages = ktFiles.map { it.packageFqName }.toSet().sortedBy { it.asString() }

    /**
     * Add type aliases to the codebase for the [mainModule].
     *
     * If [allPackages] is provided, that is the set of packages which will be processed. If it is
     * not provided, the packages represented by [ktFiles] will be processed.
     */
    fun createTypeAliases(allPackages: Set<String>?) {
        mainModuleProcessor.createTypeAliases(allPackages?.map { FqName(it) } ?: packages)
    }

    /**
     * Analyze the [ktFiles] to add items to the codebase for the [mainModule] (except type aliases,
     * which are added by [createTypeAliases]).
     */
    fun assemble() {
        mainModuleProcessor.assemble(packages)
    }

    /**
     * Searches for a class named [qualifiedName] within the context of the main analysis module for
     * the project.
     */
    fun findClassInModule(finder: JavaPsiFacade, qualifiedName: String): PsiClass? {
        return analyze(mainModule) { finder.findClass(qualifiedName, analysisScope) }
    }

    /**
     * Analyzes the [classItem] to find any Kotlin properties (which can't be found through the psi
     * directly) and add them to the class definition.
     */
    fun addPropertiesToClassFromClasspath(classItem: SkeletonClassItem) {
        mainModuleProcessor.addPropertiesToClassFromClasspath(classItem)
    }

    companion object {
        /**
         * Creates a [MultiplatformCodebase], with one [Codebase] created for each source set from
         * the list of [modules] which is common (does not depend on other modules) or a leaf (not
         * depended on by any other module).
         */
        fun assembleMultiplatform(
            modules: List<KaSourceModule>,
            location: File,
            config: Codebase.Config,
        ): MultiplatformCodebase {
            val commonModules = modules.filter { it.directDependsOnDependencies.isEmpty() }
            val leafModules =
                modules.filter { potentialEdgeModule ->
                    modules.none { potentialEdgeModule in it.directDependsOnDependencies }
                }
            return MultiplatformCodebase(
                (commonModules + leafModules).associateBy(
                    { kaModule -> kaModule.name },
                    { kaModule ->
                        val processor =
                            KaModuleProcessor(kaModule) { assembler ->
                                DefaultCodebase(
                                    location = location,
                                    description = "Codebase for source set ${kaModule.name}",
                                    preFiltered = false,
                                    config = config,
                                    trustedApi = false,
                                    supportsDocumentation = false,
                                    assembler = assembler,
                                )
                            }
                        processor.assemble()
                        processor.codebase
                    }
                ),
            )
        }
    }
}

/**
 * Processor for a single [kaModule] (a regular project has just one module, a KMP projects has
 * several like androidMain, commonMain, etc.) to update the [codebase] based on the kotlin APIs in
 * the module.
 *
 * If [codebase] is a [PsiBasedCodebase], certain operations like finding documentation and field
 * reference values is done through the codebase.
 */
internal class KaModuleProcessor
private constructor(
    val kaModule: KaModule,
    codebaseInitializer: (CodebaseAssembler) -> DefaultCodebase,
    val psiCodebase: PsiBasedCodebase?
) : DefaultCodebaseAssembler() {
    constructor(
        kaModule: KaModule,
        psiCodebase: PsiBasedCodebase
    ) : this(kaModule, { psiCodebase }, psiCodebase)

    constructor(
        kaModule: KaModule,
        codebaseInitializer: (CodebaseAssembler) -> DefaultCodebase
    ) : this(kaModule, codebaseInitializer, psiCodebase = null)

    override val codebase = codebaseInitializer(this)

    /**
     * If this is true, the [KaModuleProcessor] is being used to add Kotlin-only elements to a
     * [PsiBasedCodebase]. If it is false, the processor is generating a complete codebase for a
     * source set of a multiplatform project.
     */
    private val addingToPsiCodebase: Boolean = psiCodebase != null

    private val kaTypeItemFactory =
        KaTypeItemFactory(
            codebase,
            this,
            TypeParameterScope.empty,
            addingToPsiCodebase,
        )
    private val kaValueFactory = KaValueFactory(this, kaTypeItemFactory)
    private val kaModifierFactory = KaModifierFactory(this)

    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            defaultSourceLanguage = SourceLanguage.KOTLIN,
            defaultVariantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY
        )

    override fun getPackageInfoFromUnderlyingModel(packageName: String) = PackageInfo.NO_COMMENT

    override fun isValidPackage(packageName: String) =
        analyze(kaModule) { findPackage(FqName(packageName)) != null }

    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        // The search in a KaModule uses a ClassId, where packages are separated by "/" instead of
        // ".". Class names are separated by "." for nested classes, but first try to find the class
        // as top level.
        val classIdString = qualifiedName.replace('.', '/')
        val classItem =
            analyze(kaModule) {
                findClassLike(ClassId.fromString(classIdString))?.let { kaClassLikeSymbol ->
                    val packageQualifiedName = qualifiedName.substringBeforeLast('.')
                    val containingPackage = codebase.findOrCreatePackage(packageQualifiedName)
                    when (kaClassLikeSymbol) {
                        is KaNamedClassSymbol ->
                            processNamedClass(
                                kaClassLikeSymbol,
                                containingPackage,
                                containingClass = null,
                                processIfClasspath = true
                            )
                        is KaTypeAliasSymbol ->
                            processTypeAlias(kaClassLikeSymbol, containingPackage)
                        else -> null
                    }
                }
            }
        // Return the top level class item if found.
        if (classItem != null) {
            return classItem
        }

        // See if this might be a nested class. If there are no qualifiers it can't be.
        if (!qualifiedName.contains('.')) {
            return null
        }
        // If a top level class was not found, try searching for this as a nested class. Attempt to
        // create the containing class and locate the nested class inside of it.
        val possibleContainingClassName = qualifiedName.substringBeforeLast('.')
        val possibleContainingClass =
            createClassFromUnderlyingModel(possibleContainingClassName) ?: return null
        return possibleContainingClass.nestedClasses().firstOrNull {
            it.qualifiedName() == qualifiedName
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.allPackages(): Sequence<KaPackageSymbol> {
        fun childPackages(packageSymbol: KaPackageSymbol): Sequence<KaPackageSymbol> {
            return sequenceOf(packageSymbol) +
                packageSymbol.packageScope.getPackageSymbols().flatMap { childPackages(it) }
        }

        return childPackages(rootPackageSymbol)
    }

    /** Analyze all packages from [allPackageNames] to add type aliases to the codebase. */
    fun createTypeAliases(allPackageNames: List<FqName>) {
        analyze(kaModule) {
            for (packageName in allPackageNames) {
                findPackage(packageName)?.let { packageSymbol ->
                    val packageItem = codebase.findOrCreatePackage(packageName.asString())
                    val packageScope = packageSymbol.packageScope
                    for (typeAliasSymbol in
                        packageScope.classifiers.filterIsInstance<KaTypeAliasSymbol>()) {
                        processTypeAlias(typeAliasSymbol, packageItem)
                    }
                }
            }
        }
    }

    /**
     * Analyze the [KaModule] to add items to the codebase for this [kaModule] (except type aliases,
     * which are added by [createTypeAliases]).
     *
     * If [packageNames] is provided, specifically processes those packages, otherwise processes all
     * packages in the module (which includes packages from the classpath).
     */
    fun assemble(packageNames: List<FqName>? = null) {
        analyze(kaModule) {
            val packages =
                packageNames?.mapNotNull { findPackage(it) }?.asSequence() ?: allPackages()
            for (packageSymbol in packages) {
                processPackage(packageSymbol)
            }
        }
    }

    /**
     * Both expect and actual symbols for functions, constructors, and properties are present in the
     * [KaModule]s with actual symbols.
     *
     * If this is a common module, the expect symbols should be used in codebase creation, but if
     * both expects and actuals are present, the expect symbols should not be included in the
     * codebase.
     */
    private fun <T : KaDeclarationSymbol> KaSession.filterExpects(
        symbols: Sequence<T>
    ): Sequence<T> {
        if (addingToPsiCodebase) return symbols.filter { !it.isExpect }
        val actuals = symbols.filter { it.isActual }
        @OptIn(KaExperimentalApi::class)
        // List all the expects that would be present in [symbols]
        val expectsForActuals = actuals.flatMap { it.getExpectsForActual() }
        // Return only non-expects or expects not present in [expectsForActuals]
        return symbols.filter { !it.isExpect || it !in expectsForActuals }
    }

    /** Analyze the classes of the package as well as any top-level callables. */
    private fun KaSession.processPackage(packageSymbol: KaPackageSymbol) {
        // Ensure the package has been created
        val packageItem = codebase.findOrCreatePackage(packageSymbol.fqName.asString())
        val packageScope = packageSymbol.packageScope
        for (classifierSymbol in packageScope.classifiers) {
            when (classifierSymbol) {
                is KaNamedClassSymbol -> processNamedClass(classifierSymbol, packageItem)
                is KaTypeAliasSymbol -> {
                    // When adding Kotlin-only elements to a PsiBasedCodebase, all typealiases will
                    // already have been processed in a separate step through [createTypealiases]
                    // (in order to inline typealias usages from psi).
                    if (!addingToPsiCodebase) {
                        processTypeAlias(classifierSymbol, packageItem)
                    }
                }
                // These symbols don't need to be processed.
                is KaAnonymousObjectSymbol,
                is KaTypeParameterSymbol -> {}
            }
        }

        // Only process top level functions and properties from sources, not from the classpath.
        for (callableSymbol in
            filterExpects(packageScope.callables.filter { it.origin != KaSymbolOrigin.LIBRARY })) {
            // For top-level callables, find their containing class in the codebase.
            val classItem =
                if (addingToPsiCodebase) {
                    @OptIn(KaExperimentalApi::class)
                    val className = callableSymbol.containingJvmClassName ?: continue
                    codebase.findClassInCodebase(className) ?: continue
                } else {
                    findOrCreateFacadeClass(packageItem)
                }
            val classTypeItemFactory =
                KaTypeItemFactory(codebase, this@KaModuleProcessor, classItem, addingToPsiCodebase)
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }
    }

    /** Analyze the elements of the class. */
    private fun KaSession.processNamedClass(
        classifierSymbol: KaNamedClassSymbol,
        containingPackage: PackageItem,
        containingClass: SkeletonClassItem? = null,
        processIfClasspath: Boolean = false,
    ): SkeletonClassItem? {
        // When adding to a psi codebase, skip Java classes as they won't be kotlin-only.
        if (addingToPsiCodebase && classifierSymbol.psi?.isKotlin() == false) return null
        // Skip classes loaded from the classpath.
        if (!processIfClasspath && classifierSymbol.origin == KaSymbolOrigin.LIBRARY) return null
        // Skip private classes since these aren't part of the API surface
        if (!processIfClasspath && classifierSymbol.visibility == KaSymbolVisibility.PRIVATE)
            return null

        // Find the class in the codebase.
        val className = classifierSymbol.classId?.asFqNameString() ?: return null
        val classItem =
            if (addingToPsiCodebase) {
                // When adding Kotlin-only elements to a PsiBasedCodebase, don't create any new
                // classes. Some classes won't have been generated in the psi assembly because they
                // don't have API visibility, so they shouldn't be created here.
                codebase.findClassInCodebase(className) ?: return null
            } else {
                findOrCreateClass(classifierSymbol, containingPackage, containingClass, className)
            }
        val classTypeItemFactory =
            KaTypeItemFactory(
                codebase,
                this@KaModuleProcessor,
                classItem,
                addingToPsiCodebase,
            )

        // The combined declared member scope contains both static and non-static members.
        val memberScope = classifierSymbol.combinedDeclaredMemberScope
        for (constructorSymbol in filterExpects(memberScope.constructors)) {
            processConstructor(constructorSymbol, classItem, classTypeItemFactory)
        }
        for (callableSymbol in filterExpects(memberScope.callables)) {
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }
        for (nestedClassifierSymbol in
            memberScope.classifiers.filterIsInstance<KaNamedClassSymbol>()) {
            processNamedClass(
                nestedClassifierSymbol,
                classItem.containingPackage(),
                classItem,
                processIfClasspath = processIfClasspath
            )
        }

        // Process callables defined through a delegate
        val delegateScope = classifierSymbol.delegatedMemberScope
        for (callableSymbol in filterExpects(delegateScope.callables)) {
            processCallable(callableSymbol, classItem, classTypeItemFactory)
        }

        return classItem
    }

    /**
     * Searches for a class named [qualifiedName] in the codebase, creating one based on the
     * [classifierSymbol] if one is not found.
     */
    private fun KaSession.findOrCreateClass(
        classifierSymbol: KaNamedClassSymbol,
        containingPackage: PackageItem,
        containingClass: ClassItem?,
        qualifiedName: String,
    ): SkeletonClassItem {
        codebase.findClassInCodebase(qualifiedName)?.let {
            return it
        }

        // If this is a nested class, nest the type item factory in scope of the outer class,
        // otherwise use the default factory for the codebase.
        val enclosingTypeItemFactory =
            containingClass?.let {
                KaTypeItemFactory(codebase, this@KaModuleProcessor, it, addingToPsiCodebase)
            } ?: kaTypeItemFactory

        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                enclosingTypeItemFactory,
                "for class $qualifiedName",
                classifierSymbol.typeParameters,
            )

        val (superClassType, interfaceTypes) =
            superTypes(classifierSymbol, typeParameterListAndFactory.factory)
        val origin = classifierSymbol.classOrigin()

        val classItem =
            itemFactory.createClassItem(
                fileLocation = PsiFileLocation.fromPsiElement(classifierSymbol.psi),
                targetLanguages = TargetLanguageSet.KOTLIN_ONLY,
                modifiers = kaModifierFactory.createForClass(classifierSymbol),
                source = null,
                classKind = classifierSymbol.getClassKind(),
                containingClass = containingClass,
                containingPackage = containingPackage,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameterListAndFactory.typeParameterList,
                origin = classifierSymbol.classOrigin(),
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
            )
        if (containingClass == null && origin != ClassOrigin.CLASS_PATH) {
            codebase.addTopLevelClassFromSource(classItem)
        }
        return classItem
    }

    private fun KaNamedClassSymbol.getClassKind(): ClassKind {
        return when (classKind) {
            // Metalava does not treat Kotlin objects differently from classes.
            KaClassKind.CLASS,
            KaClassKind.OBJECT,
            KaClassKind.COMPANION_OBJECT,
            KaClassKind.ANONYMOUS_OBJECT -> ClassKind.CLASS
            KaClassKind.ENUM_CLASS -> ClassKind.ENUM
            KaClassKind.ANNOTATION_CLASS -> ClassKind.ANNOTATION_TYPE
            KaClassKind.INTERFACE -> ClassKind.INTERFACE
        }
    }

    /**
     * Returns a pair of the super class type of this class, if there is one, and a list of any
     * interface types of the class.
     */
    private fun KaSession.superTypes(
        classifierSymbol: KaNamedClassSymbol,
        typeFactory: KaTypeItemFactory,
    ): Pair<ClassTypeItem?, List<ClassTypeItem>> {
        var superClassType: ClassTypeItem? = null
        val interfaceTypes = mutableListOf<ClassTypeItem>()
        for (superType in classifierSymbol.superTypes) {
            // Expand any typealiases.
            val superTypeSymbol = superType.expandedSymbol ?: continue
            // Check whether this is an interface or superclass.
            if (superTypeSymbol.classKind == KaClassKind.INTERFACE) {
                interfaceTypes.add(typeFactory.getInterfaceType(superType))
            } else {
                superClassType = typeFactory.getSuperClassType(superType)
            }
        }
        return superClassType to interfaceTypes
    }

    private fun KaClassifierSymbol.classOrigin(): ClassOrigin {
        return when (origin) {
            KaSymbolOrigin.LIBRARY,
            KaSymbolOrigin.JAVA_LIBRARY -> ClassOrigin.CLASS_PATH
            else -> ClassOrigin.COMMAND_LINE
        }
    }

    /**
     * Finds or creates a fake facade class to hold the top level functions and properties of a
     * package, for use when creating a multiplatform codebase.
     *
     * Facade classes are only created for the JVM, but in order to support top level functions and
     * properties in the [Codebase] model this creates a fake class to hold the package-level items.
     */
    private fun findOrCreateFacadeClass(containingPackage: PackageItem): SkeletonClassItem {
        // Create a fake class name to contain the top level items.
        val qualifiedName =
            containingPackage.qualifiedName() + ".${ClassItem.TOP_LEVEL_DECLARATION_FACADE_NAME}"
        codebase.findClassInCodebase(qualifiedName)?.let {
            return it
        }
        val classItem =
            itemFactory.createClassItem(
                fileLocation = FileLocation.UNKNOWN,
                targetLanguages = TargetLanguageSet.KOTLIN_ONLY,
                modifiers = createMutableModifiers(VisibilityLevel.PUBLIC),
                source = null,
                classKind = ClassKind.CLASS,
                containingPackage = containingPackage,
                containingClass = null,
                qualifiedName = qualifiedName,
                typeParameterList = TypeParameterList.NONE,
                // Top level functions and properties are loaded from sources, not the classpath.
                origin = ClassOrigin.COMMAND_LINE,
                superClassType = null,
                interfaceTypes = emptyList(),
                isFileFacade = true,
            )
        codebase.addTopLevelClassFromSource(classItem)
        return classItem
    }

    /** Creates a [ClassItem] of kind type alias from the [typeAlias]. */
    private fun processTypeAlias(
        typeAlias: KaTypeAliasSymbol,
        containingPackage: PackageItem
    ): ClassItem? {
        val qualifiedName = typeAlias.classId?.asFqNameString() ?: return null
        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                kaTypeItemFactory,
                "for type alias $qualifiedName",
                typeAlias.typeParameters,
            )

        return itemFactory.createTypeAliasItem(
            fileLocation = PsiFileLocation.fromPsiElement(typeAlias.psi),
            modifiers = kaModifierFactory.createForDeclaration(typeAlias),
            aliasedType =
                typeParameterListAndFactory.factory.getGeneralType(typeAlias.expandedType),
            qualifiedName = qualifiedName,
            typeParameterList = typeParameterListAndFactory.typeParameterList,
            containingPackage = containingPackage,
            origin = typeAlias.classOrigin(),
        )
    }

    /** Whether to create a constructor item based on the [constructorSymbol]. */
    private fun KaSession.shouldGenerateConstructor(
        constructorSymbol: KaConstructorSymbol,
    ): Boolean {
        // Deprecation level hidden items can't be resolved from source.
        if (constructorSymbol.isDeprecatedHidden()) return false
        // If this codebase is being created just from the KaModule, all other source constructors
        // should be generated. Only skip constructors when adding to a PsiBasedCodebase.
        if (!addingToPsiCodebase) return true
        // If a constructor has a corresponding UElement it shouldn't be created as kotlin only.
        // TODO(b/491407270): checking parameter types is still required because constructors with
        //  value class type parameters incorrectly exist as UElements.
        if (constructorSymbol.existsAsUElement() && !hasValueClassTypeParameter(constructorSymbol))
            return false
        return true
    }

    /**
     * Constructs a constructor from the [constructorSymbol] and adds it to the [containingClass].
     */
    private fun KaSession.processConstructor(
        constructorSymbol: KaConstructorSymbol,
        containingClass: SkeletonClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory,
    ) {
        if (!shouldGenerateConstructor(constructorSymbol)) return

        val typeParameterListAndFactory =
            typeParameterListAndFactory(
                enclosingTypeItemFactory,
                "for constructor ${containingClass.simpleName()}",
                constructorSymbol.typeParameters
            )

        val modifiers = kaModifierFactory.createForDeclaration(constructorSymbol)
        val constructorItem =
            itemFactory.createConstructorItem(
                fileLocation = PsiFileLocation.fromPsiElement(constructorSymbol.psi),
                targetLanguages = TargetLanguageSet.KOTLIN_ONLY,
                modifiers = modifiers,
                documentationFactory = constructorSymbol.getDocumentation(),
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
                implicitConstructor = false,
                isPrimary = constructorSymbol.isPrimary,
            )
        containingClass.addConstructor(constructorItem)
    }

    /** Processes a [KaCallableSymbol], which could be a property or function. */
    private fun KaSession.processCallable(
        callableSymbol: KaCallableSymbol,
        containingClass: SkeletonClassItem,
        enclosingTypeItemFactory: KaTypeItemFactory,
    ) {
        // Skip callables loaded from the classpath.
        if (callableSymbol.origin == KaSymbolOrigin.LIBRARY) return
        if (callableSymbol.visibility == KaSymbolVisibility.PRIVATE) return

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
        // Skip generated equals and hashCode methods, when they aren't implemented in source.
        if (
            functionSymbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED &&
                functionSymbol.name.identifierOrNullIfSpecial?.let { name ->
                    name == "equals" || name == "hashCode"
                } ?: false
        )
            return false

        // If this codebase is being created just from the KaModule, all other source functions
        // should be generated. Only skip functions when adding to a PsiBasedCodebase.
        if (!addingToPsiCodebase) return true

        // Generate delegate functions.
        if (functionSymbol.origin == KaSymbolOrigin.DELEGATED) return true

        // Composable APIs will have a different signature in bytecode than in source, so the source
        // signature should be generated here as kotlin-only.
        if (functionSymbol.annotations.any { it.classId?.asFqNameString() == ANDROIDX_COMPOSABLE })
            return true

        // Generate functions annotated with JvmName.
        if (functionSymbol.annotations.any { it.classId?.asFqNameString() == JVM_NAME }) return true

        // If a function has a corresponding UElement it generally shouldn't be created as kotlin
        // only, but if a function has a value class return type which is not explicitly declared in
        // source it will still incorrectly exist as a UElement (see
        // https://youtrack.jetbrains.com/issue/KT-74205).
        // TODO(b/491407270): checking parameter types is still required because constructors with
        //  value class type parameters incorrectly exist as UElements, and a data class copy method
        //  has the constructor as its source element so it will also still exist as a UElement when
        //  it has a value class parameter type.
        if (
            functionSymbol.existsAsUElement() &&
                !hasValueClassTypeParameter(functionSymbol) &&
                !isValueClassType(functionSymbol.returnType)
        )
            return false

        return true
    }

    /** Constructs a method from the [functionSymbol] and adds it to the [containingClass]. */
    private fun KaSession.processFunction(
        functionSymbol: KaNamedFunctionSymbol,
        containingClass: SkeletonClassItem,
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

        // Create the jvm signature of the method (which is used when adding to a psi codebase): in
        // addition to the regular parameters, if this is an extension function a parameter is added
        // for the receiver, and if this is a suspend function a parameter is added for the
        // continuation.
        val parameterCount =
            functionSymbol.valueParameters.size +
                (if (functionSymbol.receiverParameter != null) 1 else 0) +
                (if (addingToPsiCodebase && functionSymbol.isSuspend) 1 else 0)
        val fingerprint = MethodFingerprint(name, parameterCount)

        val originalReturnType =
            typeParameterListAndFactory.factory.getMethodReturnType(
                functionSymbol.returnType,
                emptyList(),
                fingerprint,
                containingClass.isAnnotationType()
            )
        // For suspend functions, the jvm signature (which is used when adding to a psi codebase)
        // will have a nullable object return type (the source return type is used for the generated
        // continuation parameter).
        val returnType =
            if (addingToPsiCodebase && functionSymbol.isSuspend) {
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
            itemFactory.createMethodItem(
                fileLocation = PsiFileLocation.fromPsiElement(functionSymbol.psi),
                targetLanguages = targetLanguages,
                modifiers = modifiers,
                documentationFactory = functionSymbol.getDocumentation(),
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

    /**
     * Finds the symbol corresponding to the [classItem], if one exists, and adds any Kotlin
     * properties defined for the class.
     */
    fun addPropertiesToClassFromClasspath(classItem: SkeletonClassItem) {
        analyze(kaModule) {
            // The ClassId format is to have package names separated by slashes instead of dots.
            val classIdString =
                classItem.containingPackage().qualifiedName().replace(".", "/") +
                    "/" +
                    classItem.fullName()
            (findClassLike(ClassId.fromString(classIdString)) as? KaNamedClassSymbol)?.let { symbol
                ->
                val properties = symbol.memberScope.callables.filterIsInstance<KaPropertySymbol>()
                val typeItemFactory =
                    KaTypeItemFactory(
                        codebase,
                        this@KaModuleProcessor,
                        classItem,
                        addingToPsiCodebase,
                    )
                for (property in properties) {
                    // There might be properties included on the class with a signature from a
                    // parent. Skip these, which can be created through the parent class.
                    val callableId = property.callableId ?: continue
                    val containingClassForProperty =
                        callableId.packageName.asString() + "." + callableId.className?.asString()
                    if (containingClassForProperty != classItem.qualifiedName()) continue
                    processProperty(property, classItem, typeItemFactory)
                }
            }
        }
    }

    /** Constructs a property from the [propertySymbol] and adds it to the [containingClass]. */
    private fun KaSession.processProperty(
        propertySymbol: KaPropertySymbol,
        containingClass: SkeletonClassItem,
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
                    propertySymbol,
                    it,
                    typeFactory,
                    getterName,
                    containingClass,
                    type,
                    receiverType,
                    isGetter = true,
                    it.visibility,
                )
            }
        val setter =
            propertySymbol.setter?.let {
                findAccessor(
                    propertySymbol,
                    it,
                    typeFactory,
                    @OptIn(KaExperimentalApi::class) propertySymbol.javaSetterName!!.identifier,
                    containingClass,
                    type,
                    receiverType,
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
                classWithField.findField(propertySymbol.name.identifier)
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
            itemFactory.createPropertyItem(
                fileLocation = PsiFileLocation.fromPsiElement(propertySymbol.psi),
                documentationFactory = propertySymbol.getDocumentation(),
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
                setterVisibility =
                    propertySymbol.setter?.let { kaModifierFactory.getVisibilityLevel(it) }
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

                itemFactory.createParameterItem(
                    fileLocation = PsiFileLocation.fromPsiElement(it.psi),
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

                itemFactory.createParameterItem(
                    fileLocation = PsiFileLocation.fromPsiElement(parameterSymbol.psi),
                    modifiers = kaModifierFactory.createForValueParameter(parameterSymbol),
                    name = parameterSymbol.name.identifier,
                    publicName = parameterSymbol.name.identifierOrNullIfSpecial,
                    containingCallable = containingCallable,
                    parameterIndex = index,
                    type = type,
                    hasDefaultValue = parameterSymbol.hasDefaultValue,
                )
            }

        // If this is a suspend function, there is an extra continuation parameter added to the end
        // for the jvm signature (which is used when adding to a psi codebase).
        val continuationParameter =
            if (addingToPsiCodebase && isSuspend) {
                val index = regularParameters.size + (receiverParameter?.let { 1 } ?: 0)
                itemFactory.createParameterItem(
                    fileLocation = FileLocation.UNKNOWN,
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
        return psiCodebase?.let { psiCodebase -> psi?.createItemDocumentation(psiCodebase) }
            ?:
            // b/476391844: using NONE_FACTORY here causes issues when stubs are generated
            "".toItemDocumentationFactory()
    }

    /**
     * Finds a property accessor with the given [name] in the [containingClass], based on the
     * [propertyType] and [receiverType].
     */
    private fun findAccessor(
        property: KaPropertySymbol,
        accessor: KaPropertyAccessorSymbol,
        typeItemFactory: KaTypeItemFactory,
        name: String,
        containingClass: ClassItem,
        propertyType: TypeItem,
        receiverType: TypeItem?,
        isGetter: Boolean,
        visibility: KaSymbolVisibility,
    ): MethodItem? {
        // Generally, properties using a value class type cannot be accessed from Java. However, if
        // JvmName is used, they can be, but the inlined type needs to be used to find the accessor
        // instead of the value class type.
        val (possiblyInlinedPropertyType, possiblyInlinedReceiverType) =
            if (propertyType.isValueClassType || receiverType?.isValueClassType == true) {
                if (accessor.annotations.any { it.classId?.asFqNameString() == JVM_NAME }) {
                    typeItemFactory.inlineTypeIfNeeded(property.returnType, propertyType) to
                        receiverType?.let {
                            typeItemFactory.inlineTypeIfNeeded(
                                property.receiverType!!,
                                receiverType
                            )
                        }
                } else {
                    return null
                }
            } else {
                propertyType to receiverType
            }

        val parameters =
            listOfNotNull(
                    // Both the getter and setter have the receiver as the first parameter
                    possiblyInlinedReceiverType,
                    // The setter also has the property type as a parameter
                    if (isGetter) {
                        null
                    } else {
                        possiblyInlinedPropertyType
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
                methodItem.isKotlinProperty &&
                methodItem.parameters().map { it.type().toErasedTypeString() } == parameters
        }
    }

    /**
     * Creates a list of type parameters from the [typeParameterSymbols] and a type factory based on
     * the [enclosingTypeItemFactory].
     */
    internal fun typeParameterListAndFactory(
        enclosingTypeItemFactory: KaTypeItemFactory,
        scopeDescription: String,
        typeParameterSymbols: List<KaTypeParameterSymbol>,
    ): TypeParameterListAndFactory<KaTypeItemFactory> {
        return enclosingTypeItemFactory.createTypeParameterItemsAndFactory(
            scopeDescription,
            typeParameterSymbols,
            // Construct type parameter items from the symbols
            { typeParameterSymbol ->
                itemFactory.createTypeParameterItem(
                    kaModifierFactory.createForDeclaration(typeParameterSymbol),
                    typeParameterSymbol.name.identifier,
                    typeParameterSymbol.isReified,
                )
            },
            // Get the bounds of the type parameter from the symbols
            { typeItemFactory, typeParameterSymbol ->
                val upperBounds = typeParameterSymbol.upperBounds
                if (upperBounds.isEmpty()) {
                    WellKnownTypes.defaultTypeParameterBounds(forKotlin = true)
                } else {
                    upperBounds.map { typeItemFactory.getBoundsType(it) }
                }
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
