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

package com.android.tools.metalava.model.psi

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.addDefaultRetentionPolicyAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isRetention
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.psi.PsiConstructorItem.Companion.isPrimaryConstructor
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.gatherPackageJavadoc
import com.android.tools.metalava.reporter.Issues
import com.android.utils.associateByNotNull
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.TypeAnnotationProvider
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.toUElement

internal class PsiCodebaseAssembler(
    private val uastEnvironment: UastEnvironment,
    codebaseFactory: (PsiCodebaseAssembler) -> PsiBasedCodebase
) : CodebaseAssembler {

    internal val codebase = codebaseFactory(this)

    internal val globalTypeItemFactory = PsiTypeItemFactory(this, TypeParameterScope.empty)

    internal val project: Project = uastEnvironment.ideaProject

    private val reporter
        get() = codebase.reporter

    /**
     * Map from qualified class name to the heavyweight [PsiClass] implementations corresponding to
     * a source class.
     *
     * Psi can represent classes with a number of different implementations of [PsiClass] that have
     * different capabilities and provide different, and inconsistent, information. This keeps track
     * of the heavyweight [PsiClass] implementations for source classes which do not contribute
     * directly to an API surface (and so do not have a [ClassItem] created in the initialization of
     * the [PsiBasedCodebase]) but which may contribute indirectly, e.g. through inherited methods.
     * If a [ClassItem] needs to be created during processing, e.g. because it is a super type, then
     * the [PsiClass] corresponding to it will be removed from this map (if it exists) and used. If
     * it does not exist then it will be looked up using [JavaPsiFacade].
     */
    private val deferredHeavyweightPsiClasses = mutableMapOf<String, PsiClass>()

    fun dispose() {
        uastEnvironment.dispose()
    }

    private fun getFactory() = JavaPsiFacade.getElementFactory(project)

    internal fun getClassType(cls: PsiClass): PsiClassType =
        getFactory().createType(cls, PsiSubstitutor.EMPTY)

    internal fun getComment(documentation: String, parent: PsiElement? = null): PsiDocComment =
        getFactory().createDocCommentFromText(documentation, parent)

    internal fun createPsiType(s: String, parent: PsiElement? = null): PsiType =
        getFactory().createTypeFromText(s, parent)

    private fun createPsiAnnotation(s: String, parent: PsiElement? = null): PsiAnnotation =
        getFactory().createAnnotationFromText(s, parent)

    internal fun findPsiPackage(pkgName: String): PsiPackage? {
        return JavaPsiFacade.getInstance(project).findPackage(pkgName)
    }

    override fun createPackageItem(
        packageName: String,
        packageDoc: PackageDoc,
        containingPackage: PackageItem?
    ): DefaultPackageItem {
        val psiPackage =
            findPsiPackage(packageName)
                ?: run {
                    // This can happen if a class's package statement does not match its file path.
                    // In that case, this fakes up a PsiPackageImpl that matches the package
                    // statement as that is the source of truth.
                    val manager = PsiManager.getInstance(codebase.project)
                    PsiPackageImpl(manager, packageName)
                }
        return PsiPackageItem.create(
            codebase = codebase,
            psiPackage = psiPackage,
            packageDoc = packageDoc,
            containingPackage = containingPackage,
        )
    }

    override fun createClassFromUnderlyingModel(qualifiedName: String) =
        findOrCreateClass(qualifiedName)

    /** Check if the [BaseModifierList] is accsssible. */
    private val BaseModifierList.isAccessible
        get() =
            when (getVisibilityLevel()) {
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PROTECTED -> true
                VisibilityLevel.INTERNAL -> annotations().any { it.showability.show() }
                else -> false
            }

    /**
     * Create a possible API class, i.e. a class that has a possibility of being part of an API
     * surface.
     *
     * This will ignore any class that is inaccessible as it cannot be part of the API. A
     * [ClassItem] may be created for it later if needed, e.g. if it is a super class of an
     * accessible class.
     */
    private fun createPossibleApiClass(
        psiClass: PsiClass,
        origin: ClassOrigin,
    ): ClassItem? {
        if (psiClass.containingClass != null) error("$psiClass is not a top level class")

        // Ignore inaccessible classes.
        val modifiers = PsiModifierItem.create(codebase, psiClass)
        if (!modifiers.isAccessible) {
            deferredHeavyweightPsiClasses[psiClass.qualifiedName!!] = psiClass
            return null
        }

        return createTopLevelClassAndContents(psiClass, origin, modifiers)
    }

    /** Create a top level class, their inner classes and all the other members. */
    private fun createTopLevelClassAndContents(
        psiClass: PsiClass,
        origin: ClassOrigin,
        modifiers: MutableModifierList = PsiModifierItem.create(codebase, psiClass),
    ): ClassItem {
        if (psiClass.containingClass != null) error("$psiClass is not a top level class")
        return createClass(
            psiClass,
            null,
            globalTypeItemFactory,
            origin,
            modifiers = modifiers,
        )
    }

    private fun createClass(
        psiClass: PsiClass,
        containingClassItem: ClassItem?,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
        origin: ClassOrigin,
        modifiers: MutableModifierList = PsiModifierItem.create(codebase, psiClass),
    ): ClassItem {
        val packageName = getPackageName(psiClass)

        // If the package could not be found then report an error.
        findPsiPackage(packageName)
            ?: run {
                val directory =
                    psiClass.containingFile.containingDirectory.virtualFile.canonicalPath
                reporter.report(
                    Issues.INVALID_PACKAGE,
                    psiClass,
                    "Could not find package $packageName for class ${psiClass.qualifiedName}." +
                        " This is most likely due to a mismatch between the package statement" +
                        " and the directory $directory"
                )
            }

        val packageItem = codebase.packageTracker.findOrCreatePackage(packageName)

        if (psiClass is PsiTypeParameter) {
            error(
                "Must not be called with PsiTypeParameter; use PsiTypeParameterItem.create(...) instead"
            )
        }
        val qualifiedName = psiClass.classQualifiedName
        val classKind = getClassKind(psiClass)
        val isKotlin = psiClass.isKotlin()
        if (
            classKind == ClassKind.ANNOTATION_TYPE &&
                !hasExplicitRetention(modifiers, psiClass, isKotlin)
        ) {
            modifiers.addDefaultRetentionPolicyAnnotation(codebase, isKotlin)
        }
        // Create the TypeParameterList for this before wrapping any of the other types used by
        // it as they may reference a type parameter in the list.
        val (typeParameterList, classTypeItemFactory) =
            PsiTypeParameterList.create(
                codebase,
                enclosingClassTypeItemFactory,
                "class $qualifiedName",
                psiClass
            )
        val (superClassType, interfaceTypes) =
            computeSuperTypes(psiClass, classKind, classTypeItemFactory)
        val classItem =
            PsiClassItem(
                codebase = codebase,
                psiClass = psiClass,
                modifiers = modifiers,
                documentationFactory = PsiItemDocumentation.factory(psiClass, codebase),
                classKind = classKind,
                containingClass = containingClassItem,
                containingPackage = packageItem,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameterList,
                origin = origin,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
            )
        // Construct the children
        val psiMethods = psiClass.methods
        // create methods
        for (psiMethod in psiMethods) {
            if (psiMethod.isConstructor) {
                // Kotlin value class primary constructors must have exactly one parameter. If the
                // parameter is optional, K1 generates an additional no-args constructor for Java.
                // However, this constructor can't actually be called from Java, and the constructor
                // with an optional arg is sufficient for Kotlin API tracking, so filter the no-args
                // constructor out (this is consistent with K2).
                if (
                    classItem.modifiers.isValue() &&
                        (psiMethod as UMethod).isPrimaryConstructor &&
                        psiMethod.parameters.isEmpty()
                )
                    continue

                val constructor =
                    PsiConstructorItem.create(
                        codebase,
                        classItem,
                        psiMethod,
                        classTypeItemFactory,
                    )
                addOverloadedKotlinConstructorsIfNecessary(
                    classItem,
                    classTypeItemFactory,
                    constructor
                )
                classItem.addConstructor(constructor)
            } else {
                // With K1, value class property accessors are present as [PsiMethod]s and with K2
                // they are not. These accessor methods can't actually be used from Java, so this
                // forces the K2 behavior and filters them out for K1.
                if (
                    classItem.modifiers.isValue() && psiMethod.sourceElement is KtPropertyAccessor
                ) {
                    continue
                }

                val method =
                    PsiMethodItem.create(codebase, classItem, psiMethod, classTypeItemFactory)
                if (!method.isEnumSyntheticMethod()) {
                    classItem.addMethod(method)
                }
            }
        }

        // With K2, value class constructors are not present on the PsiClass (b/369846185#comment6)
        // because they can't be used from Java code. They can still be found on the KtClass, and we
        // track them for Kotlin source compatibility.
        // Value classes must have a primary constructor, so if none of the constructors are primary
        // this must be K2, and the primary constructor needs to be added.
        val ktClass = (psiClass as? UClass)?.sourcePsi as? KtClassOrObject
        if (classItem.modifiers.isValue() && classItem.constructors().none { it.isPrimary }) {
            val ktConstructor = ktClass?.primaryConstructor?.toUElement() as? PsiMethod
            if (ktConstructor != null) {
                val primaryConstructor =
                    PsiConstructorItem.create(
                        codebase,
                        classItem,
                        ktConstructor,
                        classTypeItemFactory
                    )
                classItem.addConstructor(primaryConstructor)
            }
        }

        // Note that this is dependent on the constructor filtering above. UAST sometimes
        // reports duplicate primary constructors, e.g.: the implicit no-arg constructor
        // If the primary constructor has optional arguments, `isPrimary` will be true for all
        // overloads, so there won't be one constructor selected as the class primary constructor.
        val constructors = classItem.constructors()
        constructors.singleOrNull { it.isPrimary }?.let { classItem.primaryConstructor = it }
        val hasImplicitDefaultConstructor = hasImplicitDefaultConstructor(classItem)
        if (hasImplicitDefaultConstructor) {
            assert(constructors.isEmpty())
            classItem.addConstructor(classItem.createDefaultConstructor())
        }
        val psiFields = psiClass.fields
        if (psiFields.isNotEmpty()) {
            for (psiField in psiFields) {
                val fieldItem =
                    PsiFieldItem.create(codebase, classItem, psiField, classTypeItemFactory)
                classItem.addField(fieldItem)
            }
        }

        // Find all properties defined on the class
        if (classItem.isKotlin()) {
            // Collect all accessor methods, backing fields, and constructor parameters that could
            // be associated with the class properties.
            val accessors =
                classItem.methods().filterIsInstanceAnd<PsiMethodItem> { it.isKotlinProperty() }
            // Don't include data class component methods
            val getters =
                accessors
                    .filter { it.parameters().isEmpty() && !it.name().startsWith("component") }
                    .associateByNotNull { it.psiMethod.propertyForAccessor() }
            val setters =
                accessors
                    .filter { it.parameters().size == 1 }
                    .associateByNotNull { it.psiMethod.propertyForAccessor() }
            val backingFields =
                classItem
                    .fields()
                    .map { it as PsiFieldItem }
                    .associateByNotNull { it.psi().sourceElement as? KtDeclaration }
            val constructorParameters =
                classItem.primaryConstructor
                    ?.parameters()
                    ?.map { it as PsiParameterItem }
                    ?.filter { (it.sourcePsi as? KtParameter)?.isPropertyParameter() ?: false }
                    ?.associateBy { it.name() }
                    .orEmpty()

            // Properties can either be declared directly as properties or as constructor params.
            // For a file facade class containing top-level property definitions, the KtClass won't
            // exist, so fall back to the properties underlying the getters and fields.
            val allProperties =
                if (ktClass != null) {
                    ktClass.declarations.filterIsInstance<KtProperty>() +
                        ktClass.primaryConstructor
                            ?.valueParameters
                            ?.filter { it.isPropertyParameter() }
                            .orEmpty()
                } else {
                    (getters.keys + backingFields.keys).toSet()
                }

            for (propertyDeclaration in allProperties) {
                val name = propertyDeclaration.name ?: continue
                val property =
                    PsiPropertyItem.create(
                        codebase = codebase,
                        ktDeclaration = propertyDeclaration,
                        containingClass = classItem,
                        typeItemFactory = enclosingClassTypeItemFactory,
                        name = name,
                        getter = getters[propertyDeclaration],
                        setter = setters[propertyDeclaration],
                        constructorParameter = constructorParameters[name],
                        backingField = backingFields[propertyDeclaration],
                    )
                        ?: continue
                classItem.addProperty(property)
            }
        }
        // This actually gets all nested classes not just inner, i.e. non-static nested,
        // classes.
        val psiNestedClasses = psiClass.innerClasses
        for (psiNestedClass in psiNestedClasses) {
            createClass(
                psiClass = psiNestedClass,
                containingClassItem = classItem,
                enclosingClassTypeItemFactory = classTypeItemFactory,
                origin = origin,
            )
        }
        return classItem
    }

    /** Returns the property or parameter declaration associated with the method, if one exists. */
    private fun PsiMethod.propertyForAccessor(): KtDeclaration? {
        return when (val sourceElement = sourceElement) {
            is KtProperty -> sourceElement
            is KtPropertyAccessor -> sourceElement.property
            is KtParameter -> sourceElement
            else -> null
        }
    }

    private fun hasExplicitRetention(
        modifiers: BaseModifierList,
        psiClass: PsiClass,
        isKotlin: Boolean
    ): Boolean {
        if (modifiers.hasAnnotation(AnnotationItem::isRetention)) {
            return true
        }
        if (isKotlin && psiClass is UClass) {
            // In Kotlin some annotations show up on the Java facade only; for example,
            // a @DslMarker annotation will imply a runtime annotation which is present
            // in the java facade, not in the source list of annotations
            val modifierList = psiClass.modifierList
            if (
                modifierList != null &&
                    modifierList.annotations.any { isRetention(it.qualifiedName) }
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Compute the super types for the class.
     *
     * Returns a pair of the optional super class type and the possibly empty list of interface
     * types.
     */
    private fun computeSuperTypes(
        psiClass: PsiClass,
        classKind: ClassKind,
        classTypeItemFactory: PsiTypeItemFactory
    ): Pair<ClassTypeItem?, List<ClassTypeItem>> {

        // A map from the qualified type name to the corresponding [KtTypeReference]. This is
        // empty for non-Kotlin code, otherwise it maps from the qualified type name of a
        // super type to the associated [KtTypeReference]. The qualified name is used to map
        // between them because Kotlin does not differentiate between `implements` and `extends`
        // lists and just has one super type list. The qualified name is safe because a class
        // cannot implement/extend the same generic type multiple times with different type
        // arguments so the qualified name should be unique among the super type list.
        // The [KtTypeReference] is needed to access the type nullability of the generic type
        // arguments.
        val qualifiedNameToKt =
            if (psiClass is UClass) {
                psiClass.uastSuperTypes.associateBy({ it.getQualifiedName() }) {
                    it.sourcePsi as KtTypeReference
                }
            } else emptyMap()

        // Get the [KtTypeReference], if any, associated with ths [PsiType] which must be a
        // [PsiClassType] as that is the only type allowed in an extends/implements list.
        fun PsiType.ktTypeReference(): KtTypeReference? {
            val qualifiedName = (this as PsiClassType).computeQualifiedName()
            return qualifiedNameToKt[qualifiedName]
        }

        // Construct the super class type if needed and available.
        val superClassType =
            if (classKind != ClassKind.INTERFACE) {
                val superClassPsiType = psiClass.superClassType as? PsiType
                superClassPsiType?.let { superClassType ->
                    val ktTypeRef = superClassType.ktTypeReference()
                    classTypeItemFactory.getSuperClassType(PsiTypeInfo(superClassType, ktTypeRef))
                }
            } else null

        // Get the interfaces from the appropriate list.
        val interfaces =
            if (classKind == ClassKind.INTERFACE || classKind == ClassKind.ANNOTATION_TYPE) {
                // An interface uses "extends <interfaces>", either explicitly for normal
                // interfaces or implicitly for annotations.
                psiClass.extendsListTypes
            } else {
                // A class uses "extends <interfaces>".
                psiClass.implementsListTypes
            }

        // Map them to PsiTypeItems.
        val interfaceTypes =
            interfaces.map { interfaceType ->
                val ktTypeRef = interfaceType.ktTypeReference()
                classTypeItemFactory.getInterfaceType(PsiTypeInfo(interfaceType, ktTypeRef))
            }
        return Pair(superClassType, interfaceTypes)
    }

    private fun getClassKind(psiClass: PsiClass): ClassKind {
        return when {
            psiClass.isAnnotationType -> ClassKind.ANNOTATION_TYPE
            psiClass.isInterface -> ClassKind.INTERFACE
            psiClass.isEnum -> ClassKind.ENUM
            psiClass is PsiTypeParameter ->
                error("Must not call this with a PsiTypeParameter - $psiClass")
            else -> ClassKind.CLASS
        }
    }

    private fun hasImplicitDefaultConstructor(classItem: PsiClassItem): Boolean {
        if (classItem.simpleName().startsWith("-")) {
            // Deliberately hidden; see examples like
            //     @file:JvmName("-ViewModelExtensions") // Hide from Java sources in the IDE.
            return false
        }

        val psiClass = classItem.psiClass
        if (psiClass is UClass && psiClass.sourcePsi == null) {
            // Top level kt classes (FooKt for Foo.kt) do not have implicit default constructor
            return false
        }

        val constructors = classItem.constructors()
        return constructors.isEmpty() && classItem.isClass()
    }

    /**
     * Returns true if overloads of this constructor should be checked separately when checking the
     * signature of this constructor.
     *
     * This works around the issue of actual callable not generating overloads for @JvmOverloads
     * annotation when the default is specified on expect side
     * (https://youtrack.jetbrains.com/issue/KT-57537).
     */
    private fun PsiConstructorItem.shouldExpandOverloads(): Boolean {
        val ktFunction = (psiMethod as? UMethod)?.sourcePsi as? KtFunction ?: return false
        return modifiers.isActual() &&
            psiMethod.hasAnnotation(JvmStandardClassIds.JVM_OVERLOADS_FQ_NAME.asString()) &&
            // It is /technically/ invalid to have actual functions with default values, but
            // some places suppress the compiler error, so we should handle it here too.
            ktFunction.valueParameters.none { it.hasDefaultValue() } &&
            parameters().any { it.hasDefaultValue() }
    }

    /**
     * Add overloads of [constructor] if necessary.
     *
     * Workaround for https://youtrack.jetbrains.com/issue/KT-57537.
     *
     * For each parameter with a default value in [constructor] this adds a [ConstructorItem] that
     * excludes that parameter and all following parameters with default values.
     */
    private fun addOverloadedKotlinConstructorsIfNecessary(
        classItem: PsiClassItem,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
        constructor: PsiConstructorItem,
    ) {
        if (!constructor.shouldExpandOverloads()) {
            return
        }

        val parameters = constructor.parameters()

        // Create an overload of the constructor for each parameter that has a default value. The
        // constructor will exclude that parameter and all following parameters that have default
        // values.
        for (currentParameterIndex in parameters.indices) {
            val currentParameter = parameters[currentParameterIndex]
            // There is no need to create an overload if the parameter does not have default value.
            if (!currentParameter.hasDefaultValue()) continue

            // Create an overloaded constructor.
            val overloadConstructor =
                PsiConstructorItem.create(
                    codebase,
                    classItem,
                    constructor.psiMethod,
                    enclosingClassTypeItemFactory,
                    psiParametersGetter = {
                        parameters.mapIndexedNotNull { index, parameterItem ->
                            // Ignore the current parameter as well as any following parameters
                            // with default values.
                            if (index >= currentParameterIndex && parameterItem.hasDefaultValue())
                                null
                            else (parameterItem as PsiParameterItem).psiParameter
                        }
                    },
                )

            classItem.addConstructor(overloadConstructor)
        }
    }

    private fun findOrCreateClass(qualifiedName: String): ClassItem? {
        // Check to see if the class has already been seen and if so return it immediately.
        codebase.findClass(qualifiedName)?.let {
            return it
        }

        // Create the ClassItem from a heavyweight PsiClass, if available.
        deferredHeavyweightPsiClasses.remove(qualifiedName)?.let {
            return findOrCreateClass(it)
        }

        // The following cannot find a class whose name does not correspond to the file name, e.g.
        // in Java a class that is a second top level class.
        val finder = JavaPsiFacade.getInstance(project)
        val psiClass =
            finder.findClass(qualifiedName, GlobalSearchScope.allScope(project)) ?: return null
        return findOrCreateClass(psiClass)
    }

    /**
     * Identifies a point in the [ClassItem] nesting structure where new [ClassItem]s need
     * inserting.
     */
    data class NewClassInsertionPoint(
        /**
         * The [PsiClass] that is the root of the nested classes that need creation, is a top level
         * class if [containingClassItem] is `null`.
         */
        val missingPsiClass: PsiClass,

        /** The containing class item, or `null` if the top level. */
        val containingClassItem: ClassItem?,
    )

    /**
     * Called when no [ClassItem] was found by [PsiBasedCodebase.findClass]`([PsiClass]) when called
     * on [psiClass].
     *
     * The purpose of this is to find where a new [ClassItem] should be inserted in the nested class
     * structure. It finds the outermost [PsiClass] with no associated [ClassItem] but which is
     * either a top level class or whose containing [PsiClass] does have an associated [ClassItem].
     * That is the point where new classes need to be created.
     *
     * e.g. if the nesting structure is `A.B.C` and `A` has already been created then the insertion
     * point would consist of [ClassItem] for `A` (the containing class item) and the [PsiClass] for
     * `B` (the outermost [PsiClass] with no associated item).
     *
     * If none had already been created then it would return an insertion point consisting of no
     * containing class item and the [PsiClass] for `A`.
     */
    private fun findNewClassInsertionPoint(psiClass: PsiClass): NewClassInsertionPoint {
        var current = psiClass
        do {
            // If the current has no containing class then it has reached the top level class so
            // return an insertion point that has no containing class item and the current class.
            val containing = current.containingClass ?: return NewClassInsertionPoint(current, null)

            // If the containing class has a matching class item then return an insertion point that
            // uses that containing class item and the current class.
            codebase.findClass(containing)?.let { containingClassItem ->
                return NewClassInsertionPoint(current, containingClassItem)
            }
            current = containing
        } while (true)
    }

    internal fun findOrCreateClass(psiClass: PsiClass): ClassItem {
        if (psiClass is PsiTypeParameter) {
            error(
                "Must not be called with PsiTypeParameter; call findOrCreateTypeParameter(...) instead"
            )
        }

        // If it has already been created then return it.
        codebase.findClass(psiClass)?.let {
            return it
        }

        // Otherwise, find an insertion point at which new classes should be created.
        val (missingPsiClass, containingClassItem) = findNewClassInsertionPoint(psiClass)

        // Create a top level or nested class as appropriate.
        val createdClassItem =
            if (containingClassItem == null) {
                // Try and determine the origin of the class.
                val containingFile = missingPsiClass.containingFile
                val origin =
                    if (containingFile == null || containingFile.name.endsWith(".class"))
                        ClassOrigin.CLASS_PATH
                    else ClassOrigin.SOURCE_PATH

                createTopLevelClassAndContents(
                    missingPsiClass,
                    origin,
                )
            } else {
                createClass(
                    missingPsiClass,
                    containingClassItem,
                    globalTypeItemFactory.from(containingClassItem),
                    origin = containingClassItem.origin,
                )
            }

        // Select the class item to return.
        return if (missingPsiClass == psiClass) {
            // The created class item was what was requested so just return it.
            createdClassItem
        } else {
            // Otherwise, a nested class was requested so find it. It was created when its
            // containing class was created.
            codebase.findClass(psiClass)!!
        }
    }

    private fun getPackageName(clz: PsiClass): String {
        var top: PsiClass? = clz
        while (top?.containingClass != null) {
            top = top.containingClass
        }
        top ?: return ""

        val simpleName = top.simpleName
        val qualifiedName = top.classQualifiedName

        if (simpleName == qualifiedName) {
            return ""
        }

        return qualifiedName.substring(0, qualifiedName.length - 1 - simpleName.length)
    }

    internal fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        val psiAnnotation = createPsiAnnotation(source, (context as? PsiItem)?.psi())
        return PsiAnnotationItem.create(codebase, psiAnnotation)
    }

    fun getPsiTypeForPsiParameter(psiParameter: PsiParameter): PsiType {
        // UAST workaround: nullity of element type in last `vararg` parameter's array type
        val psiType = psiParameter.type
        return if (
            psiParameter is UParameter &&
                psiParameter.sourcePsi is KtParameter &&
                psiParameter.isVarArgs && // last `vararg`
                psiType is PsiArrayType
        ) {
            val ktParameter = psiParameter.sourcePsi as KtParameter
            val annotationProvider =
                when (uastResolveService?.nullability(ktParameter)) {
                    KaTypeNullability.NON_NULLABLE -> getNonNullAnnotationProvider()
                    KaTypeNullability.NULLABLE -> getNullableAnnotationProvider()
                    else -> null
                }
            val annotatedType =
                if (annotationProvider != null) {
                    psiType.componentType.annotate(annotationProvider)
                } else {
                    psiType.componentType
                }
            PsiEllipsisType(annotatedType, annotatedType.annotationProvider)
        } else {
            psiType
        }
    }

    private val uastResolveService: BaseKotlinUastResolveProviderService? by lazy {
        ApplicationManager.getApplication()
            .getService(BaseKotlinUastResolveProviderService::class.java)
    }

    private var nonNullAnnotationProvider: TypeAnnotationProvider? = null
    private var nullableAnnotationProvider: TypeAnnotationProvider? = null

    /** Type annotation provider which provides androidx.annotation.NonNull */
    private fun getNonNullAnnotationProvider(): TypeAnnotationProvider {
        return nonNullAnnotationProvider
            ?: run {
                val provider =
                    TypeAnnotationProvider.Static.create(
                        arrayOf(createPsiAnnotation("@$ANDROIDX_NONNULL"))
                    )
                nonNullAnnotationProvider = provider
                provider
            }
    }

    /** Type annotation provider which provides androidx.annotation.Nullable */
    private fun getNullableAnnotationProvider(): TypeAnnotationProvider {
        return nullableAnnotationProvider
            ?: run {
                val provider =
                    TypeAnnotationProvider.Static.create(
                        arrayOf(createPsiAnnotation("@$ANDROIDX_NULLABLE"))
                    )
                nullableAnnotationProvider = provider
                provider
            }
    }

    internal fun initializeFromJar(jarFile: File) {
        // Extract the list of class names from the jar file.
        val classNames = buildList {
            try {
                ZipFile(jarFile).use { jar ->
                    for (entry in jar.entries().iterator()) {
                        val fileName = entry.name
                        if (fileName.contains("$")) {
                            // skip inner classes
                            continue
                        }
                        if (!fileName.endsWith(SdkConstants.DOT_CLASS)) {
                            // skip entries that are not .class files.
                            continue
                        }

                        val qualifiedName =
                            fileName.removeSuffix(SdkConstants.DOT_CLASS).replace('/', '.')
                        if (qualifiedName.endsWith(".package-info")) {
                            // skip package-info files.
                            continue
                        }

                        add(qualifiedName)
                    }
                }
            } catch (e: IOException) {
                reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
            }
        }

        // Create the initial set of packages that were found in the jar files. When loading from a
        // jar there is no package documentation so this will only create the root package.
        codebase.packageTracker.createInitialPackages(PackageDocs.EMPTY)

        // Find all classes referenced from the class
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val isFromClassPath = codebase.isFromClassPath()
        val origin = if (isFromClassPath) ClassOrigin.CLASS_PATH else ClassOrigin.COMMAND_LINE
        for (className in classNames) {
            val psiClass = facade.findClass(className, scope) ?: continue

            val classItem = createPossibleApiClass(psiClass, origin) ?: continue
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    internal fun initializeFromSources(
        sourceSet: SourceSet,
        apiPackages: PackageFilter?,
    ) {
        // Get the list of `PsiFile`s from the `SourceSet`.
        val psiFiles = Extractor.createUnitsForFiles(uastEnvironment.ideaProject, sourceSet.sources)

        // Split the `PsiFile`s into `PsiClass`es and `package-info.java` `PsiJavaFile`s.
        val (packageInfoFiles, psiClasses) = splitPsiFilesIntoClassesAndPackageInfoFiles(psiFiles)

        // Gather all package related javadoc.
        val packageDocs =
            gatherPackageJavadoc(
                reporter,
                sourceSet,
                packageNameFilter = { findPsiPackage(it) != null },
                packageInfoFiles,
                packageInfoDocExtractor = { getOptionalPackageDocFromPackageInfoFile(it) },
            )

        // Create the initial set of packages that were found in the source files.
        codebase.packageTracker.createInitialPackages(packageDocs)

        // Process the `PsiClass`es.
        for (psiClass in psiClasses) {
            // If a package filter is supplied then ignore any classes that do not match it.
            if (apiPackages != null) {
                val packageName = getPackageName(psiClass)
                if (!apiPackages.matches(packageName)) continue
            }

            val classItem =
                createPossibleApiClass(
                    psiClass,
                    // Sources always come from the command line.
                    ClassOrigin.COMMAND_LINE,
                )
                    ?: continue
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    /**
     * Split the [psiFiles] into separate `package-info.java` [PsiJavaFile]s and [PsiClass]es.
     *
     * During the processing this checks each [PsiFile] for unresolved imports and each [PsiClass]
     * for syntax errors.
     */
    private fun splitPsiFilesIntoClassesAndPackageInfoFiles(
        psiFiles: List<PsiFile>
    ): Pair<List<PsiJavaFile>, List<PsiClass>> {
        // A set to track `@JvmMultifileClass`es that have already been added to psiClasses.
        val multiFileClassNames = HashSet<FqName>()

        val psiClasses = mutableListOf<PsiClass>()
        val packageInfoFiles = mutableListOf<PsiJavaFile>()

        // Make sure we only process the files once; sometimes there's overlap in the source lists
        for (psiFile in psiFiles.asSequence().distinct()) {
            checkForUnresolvedImports(psiFile)

            val classes = getPsiClassesFromPsiFile(psiFile)
            when {
                classes.isEmpty() && psiFile is PsiJavaFile -> {
                    if (psiFile.name == JAVA_PACKAGE_INFO) {
                        packageInfoFiles.add(psiFile)
                    }
                }
                else -> {
                    for (psiClass in classes) {
                        checkForSyntaxErrors(psiClass)

                        // Multi file classes appear identically from each file they're defined in,
                        // don't add duplicates
                        val multiFileClassName = getOptionalMultiFileClassName(psiClass)
                        if (multiFileClassName != null) {
                            if (multiFileClassName in multiFileClassNames) {
                                continue
                            } else {
                                multiFileClassNames.add(multiFileClassName)
                            }
                        }

                        psiClasses.add(psiClass)
                    }
                }
            }
        }
        return Pair(packageInfoFiles, psiClasses)
    }

    /** Check to see if [psiFile] contains any unresolved imports. */
    private fun checkForUnresolvedImports(psiFile: PsiFile?) {
        // Visiting psiFile directly would eagerly load the entire file even though we only need
        // the importList here.
        (psiFile as? PsiJavaFile)
            ?.importList
            ?.accept(
                object : JavaRecursiveElementVisitor() {
                    override fun visitImportStatement(element: PsiImportStatement) {
                        super.visitImportStatement(element)
                        if (element.resolve() == null) {
                            reporter.report(
                                Issues.UNRESOLVED_IMPORT,
                                element,
                                "Unresolved import: `${element.qualifiedName}`"
                            )
                        }
                    }
                }
            )
    }

    /** Get, the possibly empty, list of [PsiClass]es from the [psiFile]. */
    private fun getPsiClassesFromPsiFile(psiFile: PsiFile): List<PsiClass> {
        // First, check for Java classes, return any that are found.
        (psiFile as? PsiClassOwner)?.classes?.toList()?.let { if (it.isNotEmpty()) return it }

        // Then, check for Kotlin classes, returning any that are found, or an empty list.
        val uFile = UastFacade.convertElementWithParent(psiFile, UFile::class.java) as? UFile?
        return uFile?.classes?.map { it }?.toList() ?: emptyList()
    }

    /**
     * Get the optional [MutablePackageDoc] from [psiFile].
     *
     * @param psiFile must be a `package-info.java` file.
     */
    private fun getOptionalPackageDocFromPackageInfoFile(psiFile: PsiJavaFile): MutablePackageDoc? {
        val packageStatement = psiFile.packageStatement ?: return null
        val packageName = packageStatement.packageName

        // Make sure that this is actually a package.
        findPsiPackage(packageName) ?: return null

        // Look for javadoc on the package statement; this is NOT handed to us on the PsiPackage!
        val comment = PsiTreeUtil.getPrevSiblingOfType(packageStatement, PsiDocComment::class.java)
        if (comment != null) {
            return MutablePackageDoc(
                qualifiedName = packageName,
                fileLocation = PsiFileLocation.fromPsiElement(psiFile),
                commentFactory =
                    PsiItemDocumentation.factory(packageStatement, codebase, comment.text),
            )
        }

        // No comment could be found.
        return null
    }

    /** Check the [psiClass] for any syntax errors. */
    private fun checkForSyntaxErrors(psiClass: PsiClass) {
        psiClass.accept(
            object : JavaRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    super.visitErrorElement(element)
                    reporter.report(
                        Issues.INVALID_SYNTAX,
                        element,
                        "Syntax error: `${element.errorDescription}`"
                    )
                }

                override fun visitCodeBlock(block: PsiCodeBlock) {
                    // Ignore to avoid eagerly parsing all method bodies.
                }

                override fun visitDocComment(comment: PsiDocComment) {
                    // Ignore to avoid eagerly parsing all doc comments.
                    // Doc comments cannot contain error elements.
                }
            }
        )
    }

    /** Get the optional multi file class name. */
    private fun getOptionalMultiFileClassName(psiClass: PsiClass): FqName? {
        val ktLightClass = (psiClass as? UClass)?.javaPsi as? KtLightClassForFacade
        val multiFileClassName =
            if (ktLightClass?.multiFileClass == true) {
                ktLightClass.facadeClassFqName
            } else {
                null
            }
        return multiFileClassName
    }
}

/**
 * Get the simple name of a named class or type parameter.
 *
 * A [PsiClass] is used to represent named classes, type parameters, anonymous and local classes.
 * So, its [PsiClass.getName] can sometimes be `null`. However, Metalava only gets the name for
 * named classes and type parameters which never return `null`. So, this extension property forces
 * it to be non-null.
 */
internal val PsiClass.simpleName
    get() = name!!

/**
 * Get the qualified name of a name class.
 *
 * A [PsiClass] is used to represent named classes, type parameters, anonymous and local classes.
 * So, its [PsiClass.getQualifiedName] can sometimes be `null`. However, Metalava only gets the
 * qualified name for name classes which never return `null`. So, this extension property forces it
 * to be non-null.
 */
internal val PsiClass.classQualifiedName
    get() = qualifiedName!!
