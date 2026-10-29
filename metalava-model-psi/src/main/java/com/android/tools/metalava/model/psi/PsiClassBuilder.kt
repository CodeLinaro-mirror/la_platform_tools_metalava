/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.ANDROIDX_COMPOSABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrVariableTypeItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemKind
import com.android.tools.metalava.model.JVM_NAME
import com.android.tools.metalava.model.KOTLIN_PUBLISHED_API
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierContext
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SkeletonTypeParameterItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.WellKnownTypes
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.addDefaultRetentionPolicyAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isNonNullAnnotation
import com.android.tools.metalava.model.isRetention
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeParameterListAndFactory
import com.android.tools.metalava.model.value.OptionalValueProvider
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.reporter.Issues
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterListOwner
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReceiverParameter
import org.jetbrains.uast.kotlin.KotlinUMethodWithFakeLightDelegateBase
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.toUElementOfType

/**
 * Responsible for creating [ClassItem]s from either source or binary [PsiClass]es.
 *
 * @param globalContext provides access to various pieces of data that apply across all classes.
 * @param psiClass the underlying [PsiClass].
 * @param origin the [ClassOrigin] of the class.
 */
internal class PsiClassBuilder(
    private val globalContext: PsiGlobalContext,
    private val psiClass: PsiClass,
    private val origin: ClassOrigin,
) : PsiGlobalContext by globalContext {
    /**
     * Create a [ClassItem] for [psiClass].
     *
     * The parameters are on this method rather than the [PsiClassBuilder] constructor because these
     * only apply to the [ClassItem] that this builds, not to all the members or the nested classes.
     * Adding these as constructor properties would confuse that code and possibly lead to errors if
     * the wrong instance was used.
     *
     * @param containingClassItem the containing [ClassItem] to which the created [ClassItem] will
     *   belong, if any.
     * @param enclosingClassTypeItemFactory the [PsiTypeItemFactory] that is used to create
     *   [TypeItem]s and tracks the in scope type parameters.
     */
    internal fun createClass(
        containingClassItem: ClassItem?,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
    ): SkeletonClassItem {
        val packageName = psiClass.packageName

        // If the package could not be found then report an error.
        globalContext.findPsiPackage(packageName)
            ?: run {
                val directory =
                    psiClass.containingFile.containingDirectory.virtualFile.canonicalPath
                codebase.reporter.report(
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

        val modifiers =
            createModifiers(
                ModifierContext.forClassKind(classKind),
                psiClass,
            )
        // If the containing class is internal, this class cannot be more visible than internal
        // (e.g. it can't be public). Correct the visibility to internal in this case.
        if (
            containingClassItem != null &&
                containingClassItem.modifiers.getVisibilityLevel() == VisibilityLevel.INTERNAL &&
                modifiers.getVisibilityLevel() > VisibilityLevel.INTERNAL
        ) {
            modifiers.setVisibilityLevel(VisibilityLevel.INTERNAL)
            // With the class visibility now internal, @PublishedApi needs to be added if it was on
            // the containing class to give this class API visibility.
            if (containingClassItem.modifiers.isPublishedApi()) {
                modifiers.addAnnotation(
                    AnnotationItem.createMarkerAnnotation(codebase, KOTLIN_PUBLISHED_API)
                )
            }
        }

        if (classKind == ClassKind.ANNOTATION_TYPE && !hasExplicitRetention(modifiers, isKotlin)) {
            modifiers.addDefaultRetentionPolicyAnnotation(codebase, isKotlin)
        }
        // Create the TypeParameterList for this before wrapping any of the other types used by
        // it as they may reference a type parameter in the list.
        val (typeParameterList, classTypeItemFactory) =
            createTypeParameterList(
                enclosingClassTypeItemFactory,
                "class $qualifiedName",
                psiClass,
            )
        val (superClassType, interfaceTypes) =
            computeSuperTypes(psiClass, classKind, classTypeItemFactory)

        // The sorted permits list.
        val permitTypes =
            psiClass.permitsListTypes
                .map { classTypeItemFactory.getHierarchicalClassType(PsiTypeInfo(it, psiClass)) }
                .sortedWith(TypeItem.qualifiedComparator)

        // Get the SourceFile, using the one from the containing class if this is nested.
        val sourceFile =
            if (containingClassItem != null) {
                containingClassItem.sourceFile()
            } else {
                sourceFile(psiClass)
            }

        // Items annotated with PublishedApi can only be used externally from bytecode.
        val targetLanguages =
            if (modifiers.isPublishedApi()) {
                TargetLanguageSet.BYTECODE_ONLY
            } else {
                TargetLanguageSet.ALL
            }

        val classItem =
            itemFactory.createClassItem(
                fileLocation = PsiFileLocation.fromPsiElement(psiClass),
                sourceLanguage = psiClass.sourceLanguage,
                targetLanguages = targetLanguages,
                modifiers = modifiers,
                documentationFactory = psiClass.createItemDocumentation(psiCodebase),
                source = sourceFile,
                classKind = classKind,
                containingClass = containingClassItem,
                containingPackage = packageItem,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameterList,
                origin = origin,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
                permitTypes = permitTypes,
                isFileFacade = psiClass.isFileFacade(),
                optionalAliasedType = null,
                isMultiFileClass = psiClass.isMultiFileClass(),
                recordComponentItemsFactory =
                    if (classKind == ClassKind.RECORD)
                        { classItem ->
                            createRecordComponents(
                                classItem,
                                psiClass.recordComponents,
                                classTypeItemFactory
                            )
                        }
                    else {
                        null
                    },
            )

        if (classKind == ClassKind.RECORD) {
            createRecordComponents(classItem, psiClass.recordComponents, classTypeItemFactory)
        }

        // Add methods, constructors, fields.
        addMembersToClassItem(
            classItem = classItem,
            psiMethods = psiClass.methods.toList(),
            psiFields = psiClass.fields.toList(),
            classTypeItemFactory = classTypeItemFactory,
        )

        // This actually gets all nested classes not just inner, i.e. non-static nested,
        // classes.
        val psiNestedClasses = psiClass.innerClasses
        for (psiNestedClass in psiNestedClasses) {
            val nestedBuilder =
                PsiClassBuilder(
                    globalContext,
                    psiNestedClass,
                    origin,
                )
            nestedBuilder.createClass(
                containingClassItem = classItem,
                enclosingClassTypeItemFactory = classTypeItemFactory,
            )
        }
        return classItem
    }

    /**
     * Create the [PropertyItem]s used to model record components.
     *
     * Must be called before creating any other members of [classItem].
     */
    private fun createRecordComponents(
        classItem: ClassItem,
        components: Array<PsiRecordComponent>,
        classTypeItemFactory: PsiTypeItemFactory
    ) =
        components.mapIndexed { index, component ->
            val modifiers =
                createModifiers(
                    ModifierContext.forItemKind(ItemKind.RECORD_COMPONENT),
                    component,
                )
            modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            modifiers.setFinal(false)

            val type = classTypeItemFactory.getGeneralType(PsiTypeInfo(component.type, component))

            itemFactory.createRecordComponentItem(
                fileLocation = PsiFileLocation.fromPsiElement(component),
                modifiers = modifiers,
                name = component.name,
                containingClass = classItem,
                type = type,
                recordComponentIndex = index,
            )
        }

    /** Create [MutableModifierList] for [psiModifierListOwner] in [psiCodebase]. */
    private fun createModifiers(
        modifierContext: ModifierContext,
        psiModifierListOwner: PsiModifierListOwner,
    ) = PsiModifierItem.create(modifierContext, psiCodebase, psiModifierListOwner)

    /**
     * Get the [PsiSourceFile] for [psiClass].
     *
     * This should only be called on the outermost [PsiClass].
     */
    private fun sourceFile(psiClass: PsiClass): PsiSourceFile? {
        // SourceFile is only used when resolving references from within documentation comments so
        // if they are not read then there is no point in creating the source files.
        if (!codebase.config.allowReadingComments) return null

        require(psiClass.containingClass == null) {
            "internal error: attempted to get source file for nested class $psiClass"
        }
        val containingFile = psiClass.containingFile ?: return null
        if (containingFile is PsiCompiledFile) {
            return null
        }

        // This cache is necessary so that multiple classes within the same file share the same
        // PsiSourceFile.
        return psiCodebase.sourceFileCache.psiSourceFile(containingFile)
    }

    /**
     * Adds the methods and constructors from [psiMethods] and fields from [psiFields] to the
     * [classItem].
     */
    fun addMembersToClassItem(
        classItem: SkeletonClassItem,
        psiMethods: List<PsiMethod>,
        psiFields: List<PsiField>,
        classTypeItemFactory: PsiTypeItemFactory,
    ) {
        // create methods
        for (psiMethod in psiMethods) {
            // Skip fake UAST constructors and methods, which can't be used from java source.
            // If this condition is updated, the one in KaCodebaseAssembler determining which
            // methods to create needs to be updated too.
            if (
                (psiMethod is UastFakeSourceLightMethod ||
                    psiMethod is KotlinUMethodWithFakeLightDelegateBase<*>)
            ) {
                continue
            }

            // Composable APIs will have a different signature in bytecode than in source. The
            // source signature will be generated as kotlin-only by KaCodebaseAssembler and the
            // bytecode signature will be generated as bytecode-only by KotlinBytecodeApis.
            if (psiMethod.hasAnnotation(ANDROIDX_COMPOSABLE)) continue

            // Items annotated with PublishedApi can only be used externally from bytecode. The
            // bytecode version of the method will be added by KotlinBytecodeApis.
            if (psiMethod.hasAnnotation(KOTLIN_PUBLISHED_API)) continue

            if (psiMethod.isConstructor) {
                val constructor = createConstructor(classItem, psiMethod, classTypeItemFactory)

                // There will be a private version of a constructor that takes a value class
                // parameter, by skip generating it here as the non-private source version will be
                // added in KaCodebaseAssembler.
                if (constructor.parameters().any { it.type().isValueClassType }) {
                    continue
                }

                classItem.addConstructor(constructor)
            } else {
                // Property accessors can't be resolved from kotlin, direct access is used instead.
                val targetLanguages =
                    if (
                        psiMethod.isKotlinProperty() &&
                            // Data class component methods are one kind of property accessor that
                            // can be resolved from Kotlin source.
                            !(classItem.modifiers.isData() &&
                                psiMethod.name.startsWith("component"))
                    ) {
                        TargetLanguageSet.NOT_KOTLIN
                    } else {
                        TargetLanguageSet.ALL
                    }
                val method =
                    createMethod(
                        classItem,
                        psiMethod,
                        classTypeItemFactory,
                        targetLanguages = targetLanguages,
                    )

                val hasJvmName = method.modifiers.annotations().any { it.qualifiedName == JVM_NAME }
                // If a method is annotated with JvmName, then mark it as not usable from Kotlin. It
                // is possible that JvmName is used even though the method signature will be
                // identical between Java and Kotlin. If that happens, in the KaCodebaseAssembler
                // step, the method will be updated again to include Kotlin as a target language.
                if (hasJvmName) {
                    method.targetLanguages -= TargetLanguage.KOTLIN
                }

                // If a function has a value class return type which is not explicitly declared in
                // source it will still incorrectly exist as a UElement (see
                // https://youtrack.jetbrains.com/issue/KT-74205).
                if (
                    (method.returnType().isValueClassType ||
                        // If a suspend function returns a value class type, the return is turned
                        // into a final continuation parameter where the argument of the type is
                        // a super bound of the value class type.
                        (method.modifiers.isSuspend() &&
                            ((method.parameters().lastOrNull()?.type() as? ClassTypeItem)
                                    ?.arguments
                                    ?.singleOrNull() as? WildcardTypeItem)
                                ?.superBound
                                ?.isValueClassType == true)) && !hasJvmName
                ) {
                    continue
                }

                if (!method.isEnumSyntheticMethod()) {
                    classItem.addMethod(method)
                }
            }
        }

        val constructors = classItem.constructors()
        val hasImplicitDefaultConstructor = hasImplicitDefaultConstructor(classItem)
        if (hasImplicitDefaultConstructor) {
            assert(constructors.isEmpty())
            classItem.addConstructor(classItem.createImplicitDefaultConstructor())
        }
        if (psiFields.isNotEmpty()) {
            for (psiField in psiFields) {
                createField(classItem, psiField, classTypeItemFactory)?.let { fieldItem ->
                    classItem.addField(fieldItem)
                }
            }
        }
    }

    /**
     * Check to see whether [psiClass] (which must be an annotation class) has an explicit retention
     * annotation applied.
     *
     * @param modifiers the [psiClass]'s modifiers.
     * @param isKotlin `true` if [psiClass] is a Kotlin class.
     */
    private fun hasExplicitRetention(modifiers: BaseModifierList, isKotlin: Boolean): Boolean {
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

    /** Get the [ClassKind] for [psiClass]. */
    private fun getClassKind(psiClass: PsiClass): ClassKind {
        return when {
            psiClass.isAnnotationType -> ClassKind.ANNOTATION_TYPE
            psiClass.isInterface -> ClassKind.INTERFACE
            psiClass.isEnum -> ClassKind.ENUM
            psiClass.isRecord -> ClassKind.RECORD
            psiClass is PsiTypeParameter ->
                error("Must not call this with a PsiTypeParameter - $psiClass")
            else -> ClassKind.CLASS
        }
    }

    /**
     * Whether a no-args constructor should be generated for this class. For Kotlin source classes,
     * the psi will include the implicit no-args constructor if it exists, so this is only needed
     * for Java source classes.
     */
    private fun hasImplicitDefaultConstructor(classItem: ClassItem): Boolean {
        return classItem.isJava() && classItem.constructors().isEmpty() && classItem.isClass()
    }

    internal fun createField(
        containingClass: ClassItem,
        psiField: PsiField,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
    ): FieldItem? {
        val name = psiField.name
        val modifiers =
            createModifiers(
                ModifierContext.forItemKind(ItemKind.FIELD),
                psiField,
            )

        // Ignore private member fields in records.
        if (
            containingClass.classKind == ClassKind.RECORD &&
                modifiers.isPrivate() &&
                !modifiers.isStatic()
        )
            return null

        val isEnumConstant = psiField is PsiEnumConstant

        // Create a type for the field, taking into account the modifiers, whether it is an
        // enum constant and whether the field's initial value is non-null.
        val isInitialValueNonNull = {
            // The initial value is non-null if the field initializer is a method that is annotated
            // as being non-null so would produce a non-null value, or the value is a literal which
            // is not null.
            psiField.isFieldInitializerNonNull()
        }
        val fieldType =
            try {
                enclosingClassTypeItemFactory.getFieldType(
                    underlyingType = PsiTypeInfo(psiField.type, psiField),
                    itemAnnotations = modifiers.annotations(),
                    isEnumConstant = isEnumConstant,
                    isFinal = modifiers.isFinal(),
                    isInitialValueNonNull = isInitialValueNonNull,
                )
            } catch (e: IllegalStateException) {
                // Workaround for b/529762241: the type from the UField is missing the class name
                // and parameters when a property is initialized through an anonymous object,
                // without an explicit type declaration.
                val typeFromJavaPsi =
                    ((psiField as? UField)?.javaPsi as? PsiField)?.type
                        ?: throw IllegalStateException(
                            "Failed to resolve field type for `${psiField.name}` in `${containingClass.qualifiedName()}`",
                            e
                        )
                @Suppress("UElementAsPsi") // Necessary to work around UAST issue.
                enclosingClassTypeItemFactory.getFieldType(
                    underlyingType = PsiTypeInfo(typeFromJavaPsi, psiField),
                    itemAnnotations = modifiers.annotations(),
                    isEnumConstant = isEnumConstant,
                    isFinal = modifiers.isFinal(),
                    isInitialValueNonNull = isInitialValueNonNull,
                )
            }

        // Check to see whether the field could have a constant value.
        val couldHaveConstantValue =
            when (psiField.sourceLanguage) {
                // In Kotlin the `const` modifier is what determines whether the field could
                // have a constant value. However, it also needs to be static as a const
                // instance field cannot be treated as a constant by code outside the class.
                SourceLanguage.KOTLIN -> modifiers.isConst() && modifiers.isStatic()
                // In Java fields have to be static and final in order for them to have a
                // constant value but that is not sufficient.
                else -> modifiers.isStatic() && modifiers.isFinal()
            }

        // Get a ValueProvider for the initializer, if possible.
        val constantValueProvider =
            if (couldHaveConstantValue) constantValueProviderForField(psiField, fieldType) else null

        // Items annotated with PublishedApi can only be used externally from bytecode.
        val targetLanguages =
            if (modifiers.isPublishedApi()) {
                TargetLanguageSet.BYTECODE_ONLY
            } else {
                TargetLanguageSet.ALL
            }

        return itemFactory.createFieldItem(
            fileLocation = PsiFileLocation(psiField),
            sourceLanguage = psiField.sourceLanguage,
            targetLanguages = targetLanguages,
            modifiers = modifiers,
            documentationFactory = psiField.createItemDocumentation(psiCodebase),
            name = name,
            containingClass = containingClass,
            type = fieldType,
            isEnumConstant = isEnumConstant,
            constantValueProvider = constantValueProvider
        )
    }

    /**
     * Get an [OptionalValueProvider] for the [psiField]'s constant value.
     *
     * This will return 'null' if the [psiField] has no initializer at all.
     *
     * The returned [OptionalValueProvider]'s [OptionalValueProvider.optionalValue] property will be
     * `null` if the field is a Java field which does not have an initializer which is a constant
     * expression.
     */
    private fun constantValueProviderForField(psiField: PsiField, fieldType: TypeItem) =
        when (psiField) {
            is UField -> {
                psiField.uastInitializer?.let { uastInitializer ->
                    psiCodebase.valueFactory.providerFor(
                        fieldType,
                        uastInitializer,
                        ValueUseSite.FIELD,
                    )
                }
            }
            else -> {
                psiField.initializer?.let { psiInitializer ->
                    psiCodebase.valueFactory.providerFor(
                        fieldType,
                        psiInitializer,
                        ValueUseSite.FIELD,
                    )
                }
            }
        }

    /** Create a [MethodItem]. */
    internal fun createMethod(
        containingClass: ClassItem,
        psiMethod: PsiMethod,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
        psiParameters: List<PsiParameter> = psiMethod.psiParameters,
        targetLanguages: Set<TargetLanguage> = containingClass.targetLanguages,
    ): MethodItem {
        assert(!psiMethod.isConstructor)
        // TODO(b/457844210): work around a UAST issue where the accessor methods of internal
        //  PublishedApi properties have mangled names even though the compiler does not mangle
        //  their names.
        val name =
            if (
                psiMethod.name.contains("$") &&
                    psiMethod.isKotlinProperty() &&
                    sourcePropertyOrParameter(psiMethod)?.hasPublishedApiAnnotation() == true
            ) {
                psiMethod.name.substringBefore("$")
            } else {
                psiMethod.name
            }
        val modifiers =
            createModifiers(
                ModifierContext.forItemKind(ItemKind.METHOD),
                psiMethod,
            )

        if (containingClass.classKind == ClassKind.INTERFACE) {
            // All interface methods are implicitly public (except in Java 1.9, where they can
            // be private).
            if (!modifiers.isPrivate()) {
                modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            }
        }

        if (modifiers.isFinal() && containingClass.modifiers.isFinal()) {
            // The containing class is final, so it is implied that every method is final as well.
            // No need to apply 'final' to each method. (We do it here rather than just in the
            // signature emit code since we want to make sure that the signature comparison
            // methods with super methods also consider this method non-final.)
            modifiers.setFinal(false)
        }

        // Create the TypeParameterList for this before wrapping any of the other types used by it
        // as they may reference a type parameter in the list.
        val (typeParameterList, methodTypeItemFactory) =
            createTypeParameterList(
                enclosingClassTypeItemFactory,
                "method $name",
                psiMethod,
            )
        val fingerprint = MethodFingerprint(psiMethod.name, psiMethod.parameters.size)
        val isAnnotationElement = containingClass.isAnnotationType() && !modifiers.isStatic()
        val returnType =
            methodTypeItemFactory.getMethodReturnType(
                underlyingReturnType = PsiTypeInfo(psiMethod.returnType!!, psiMethod),
                itemAnnotations = modifiers.annotations(),
                fingerprint = fingerprint,
                isAnnotationElement = isAnnotationElement,
            )

        val defaultValueProvider = psiMethod.defaultValueProvider(psiCodebase, returnType)

        // Use psi util which works for source kt elements to determine if this is an extension
        val isExtensionMethod = (psiMethod as? UMethod)?.sourcePsi?.isExtensionDeclaration() == true

        val method =
            itemFactory.createMethodItem(
                fileLocation = PsiFileLocation(psiMethod),
                sourceLanguage = psiMethod.sourceLanguage,
                targetLanguages = targetLanguages,
                modifiers = modifiers,
                documentationFactory = psiMethod.createItemDocumentation(psiCodebase),
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = returnType,
                parameterItemsFactory = { containingCallable ->
                    parameterList(
                        psiMethod,
                        containingCallable,
                        methodTypeItemFactory,
                        modifiers,
                        psiParameters,
                    )
                },
                throwsTypes = throwsTypes(psiMethod, methodTypeItemFactory),
                defaultValueProvider = defaultValueProvider,
                isExtensionMethod = isExtensionMethod,
                isKotlinProperty = psiMethod.isKotlinProperty(),
            )

        return method
    }

    /**
     * Determine whether to treat constructors of [containingClass] as [VisibilityLevel.PRIVATE].
     *
     * Sealed abstract classes cannot be instantiated directly to treat them as being private.
     */
    private fun treatConstructorAsPrivate(containingClass: ClassItem): Boolean {
        val modifiers = containingClass.modifiers
        return modifiers.isSealed() && modifiers.isAbstract()
    }

    /** Create a [ConstructorItem]. */
    internal fun createConstructor(
        containingClass: ClassItem,
        psiMethod: PsiMethod,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
        psiParameters: List<PsiParameter> = psiMethod.psiParameters,
        targetLanguages: Set<TargetLanguage> = TargetLanguageSet.ALL,
    ): ConstructorItem {
        assert(psiMethod.isConstructor)
        val name = psiMethod.name
        val modifiers =
            createModifiers(
                ModifierContext.forItemKind(ItemKind.CONSTRUCTOR),
                psiMethod,
            )

        // Make the constructor private if necessary.
        if (treatConstructorAsPrivate(containingClass)) {
            modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
        }

        // Create the TypeParameterList for this before wrapping any of the other types used by it
        // as they may reference a type parameter in the list.
        val (typeParameterList, constructorTypeItemFactory) =
            createTypeParameterList(
                enclosingClassTypeItemFactory,
                "constructor $name",
                psiMethod,
            )
        val constructor =
            itemFactory.createConstructorItem(
                fileLocation = PsiFileLocation(psiMethod),
                sourceLanguage = psiMethod.sourceLanguage,
                targetLanguages = targetLanguages,
                modifiers = modifiers,
                documentationFactory = psiMethod.createItemDocumentation(psiCodebase),
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = containingClass.type(),
                parameterItemsFactory = { containingCallable ->
                    parameterList(
                        psiMethod,
                        containingCallable,
                        constructorTypeItemFactory,
                        modifiers,
                        psiParameters,
                    )
                },
                throwsTypes = throwsTypes(psiMethod, constructorTypeItemFactory),
                implicitConstructor = false,
                isPrimary = (psiMethod as? UMethod)?.isPrimaryConstructor == true
            )

        return constructor
    }

    /**
     * For property accessor [psiMethod], returns the [KtProperty] or [KtParameter] which is the
     * source of the method.
     */
    private fun sourcePropertyOrParameter(psiMethod: PsiMethod): KtAnnotated? {
        return when (val sourcePsi = (psiMethod as? UMethod)?.sourcePsi) {
            is KtProperty -> sourcePsi
            is KtParameter -> sourcePsi
            is KtPropertyAccessor -> sourcePsi.property
            else -> null
        }
    }

    /** Returns whether the element is annotated with @PublishedApi. */
    private fun KtAnnotated.hasPublishedApiAnnotation(): Boolean {
        return annotationEntries.any {
            it.toUElementOfType<UAnnotation>()?.qualifiedName == "kotlin.PublishedApi"
        }
    }

    /**
     * Create a list of [ParameterItem]s.
     *
     * The [containingCallableModifiers] parameter is added here, rather than retrieving from
     * [containingCallable]'s [CallableItem.modifiers] properties, because at the time this is
     * called [containingCallable] is in the process of being initialized and its properties have
     * not yet been initialized.
     */
    private fun parameterList(
        psiMethod: PsiMethod,
        containingCallable: CallableItem,
        enclosingTypeItemFactory: PsiTypeItemFactory,
        containingCallableModifiers: BaseModifierList,
        psiParameters: List<PsiParameter> = psiMethod.psiParameters,
    ): List<ParameterItem> {
        val fingerprint = MethodFingerprint(containingCallable.name(), psiParameters.size)
        return psiParameters.mapIndexed { index, parameter ->
            createParameterItem(
                containingCallable,
                fingerprint,
                parameter,
                index,
                enclosingTypeItemFactory,
                psiMethod,
                containingCallableModifiers
            )
        }
    }

    /** Create a [ParameterItem] for [psiParameter]. */
    private fun createParameterItem(
        containingCallable: CallableItem,
        fingerprint: MethodFingerprint,
        psiParameter: PsiParameter,
        parameterIndex: Int,
        enclosingMethodTypeItemFactory: PsiTypeItemFactory,
        psiMethod: PsiMethod,
        containingCallableModifiers: BaseModifierList,
    ): ParameterItem {
        val name = psiParameter.name
        val modifiers = createParameterModifiers(psiParameter)
        val type =
            enclosingMethodTypeItemFactory.getMethodParameterType(
                underlyingParameterType = PsiTypeInfo(psiParameter.type, psiParameter),
                itemAnnotations = modifiers.annotations(),
                fingerprint = fingerprint,
                parameterIndex = parameterIndex,
                isVarArg = psiParameter.type is PsiEllipsisType,
            )
        val kind =
            computeParameterKind(psiParameter, containingCallable, parameterIndex, fingerprint)
        val parameter =
            itemFactory.createParameterItem(
                fileLocation = PsiFileLocation.fromPsiElement(psiParameter),
                sourceLanguage = psiParameter.sourceLanguage,
                modifiers = modifiers,
                name = name,
                publicName =
                    getPublicName(
                        psiParameter,
                        parameterIndex,
                        fingerprint.parameterCount,
                        psiMethod,
                        containingCallableModifiers,
                    ),
                containingItem = containingCallable,
                parameterIndex = parameterIndex,
                type = type,
                hasDefaultValue =
                    PsiParameterDefaultValue.compute(psiParameter, parameterIndex, kind, psiMethod),
                kind = kind,
            )
        return parameter
    }

    /** Create [MutableModifierList] from [psiParameter] for a [ParameterItem]. */
    private fun createParameterModifiers(psiParameter: PsiParameter): MutableModifierList {
        val modifiers =
            createModifiers(
                ModifierContext.forItemKind(ItemKind.PARAMETER),
                psiParameter,
            )
        // Method parameters don't have a visibility level; they are visible to anyone that can
        // call their method. However, Kotlin constructors sometimes appear to specify the
        // visibility of a constructor parameter by putting visibility inside the constructor
        // signature. This is really to indicate that the matching property should have the
        // mentioned visibility.
        // If the method parameter seems to specify a visibility level, we correct it back to
        // the default, here, to ensure we don't attempt to incorrectly emit this information
        // into a signature file.
        modifiers.setVisibilityLevel(VisibilityLevel.PACKAGE_PRIVATE)
        return modifiers
    }

    /**
     * Get the public name of a parameter.
     *
     * @param psiParameter The [PsiParameter] to find the name of.
     * @param parameterIndex The index of this parameter in the containing callable.
     * @param parameterCount The total number of parameters of the containing callable.
     * @param psiMethod The containing [PsiMethod] of the parameter.
     * @param containingCallableModifiers The modifiers of the containing callable.
     */
    private fun getPublicName(
        psiParameter: PsiParameter,
        parameterIndex: Int,
        parameterCount: Int,
        psiMethod: PsiMethod,
        containingCallableModifiers: BaseModifierList,
    ): String? {
        if (psiParameter.isKotlin()) {
            // Omit names of some special parameters in Kotlin. None of these parameters may be set
            // through Kotlin keyword arguments, so there's no need to track their names for
            // compatibility. This also helps avoid signature file churn if PSI or the compiler
            // change what name they're using for these parameters.

            // Receiver parameter of extension function
            // Note receiver parameter used to be named $receiver in previous UAST versions, now it
            // is $this$functionName
            if (parameterIndex == 0 && psiParameter.name.startsWith("\$this$")) {
                return null
            }
            // Property setter parameter
            if (psiMethod.isKotlinProperty()) {
                return null
            }
            // Continuation parameter of suspend function (the final parameter of a suspend function
            // is the continuation).
            if (containingCallableModifiers.isSuspend() && parameterCount - 1 == parameterIndex) {
                return null
            }
            return psiParameter.name
        }

        return null
    }

    /** Determines the [ParameterKind] of the [psiParameter]. */
    private fun computeParameterKind(
        psiParameter: PsiParameter,
        containingCallable: CallableItem,
        parameterIndex: Int,
        fingerprint: MethodFingerprint,
    ): ParameterKind {
        return when {
            // Any Java parameter or parameter loaded from a jar is a value parameter
            !psiParameter.isKotlin() -> ParameterKind.VALUE
            // The final parameter of a suspend function is the continuation parameter
            (containingCallable.modifiers.isSuspend() &&
                parameterIndex == fingerprint.parameterCount - 1) -> ParameterKind.CONTINUATION
            // Receiver parameters have a specific UAST type
            psiParameter is UReceiverParameter -> ParameterKind.RECEIVER
            // The source psi has information about context parameters
            ((psiParameter as? UParameter)?.sourcePsi as? KtParameter)?.isContextParameter ==
                true -> ParameterKind.CONTEXT
            // Not any special kotlin parameter kind, must be a value parameter
            else -> ParameterKind.VALUE
        }
    }

    private fun throwsTypes(
        psiMethod: PsiMethod,
        enclosingTypeItemFactory: PsiTypeItemFactory,
    ): List<ExceptionTypeItem> {
        val throwsClassTypes = psiMethod.throwsList.referencedTypes
        if (throwsClassTypes.isEmpty()) {
            return emptyList()
        }

        return throwsClassTypes
            // Convert the PsiType to an ExceptionTypeItem and wrap it in a ThrowableType.
            .map { psiType -> enclosingTypeItemFactory.getExceptionType(PsiTypeInfo(psiType)) }
            // We're sorting the names here even though outputs typically do their own sorting,
            // since for example the MethodItem.sameSignature check wants to do an
            // element-by-element comparison to see if the signature matches, and that should match
            // overrides even if they specify their elements in different orders.
            .sortedWith(ClassOrVariableTypeItem.fullNameComparator)
    }

    /**
     * Create a [TypeParameterListAndFactory] pair from [psiOwner] for [scopeDescription].
     *
     * If the [TypeParameterListAndFactory.typeParameterList] is empty then
     * [TypeParameterListAndFactory.factory] will be [enclosingTypeItemFactory]. Otherwise, the
     * factory will resolve the [TypeParameterList] and delegate others to
     * [enclosingTypeItemFactory].
     */
    private fun createTypeParameterList(
        enclosingTypeItemFactory: PsiTypeItemFactory,
        scopeDescription: String,
        psiOwner: PsiTypeParameterListOwner
    ): TypeParameterListAndFactory<PsiTypeItemFactory> {
        val psiTypeParameters = psiOwner.typeParameterList?.typeParameters?.asList()
        if (psiTypeParameters.isNullOrEmpty()) {
            return TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        }

        return enclosingTypeItemFactory.createTypeParameterItemsAndFactory(
            scopeDescription,
            psiTypeParameters,
            { createTypeParameterItem(it) },
            // Create bounds and store it in the [PsiTypeParameterItem.bounds] property.
            { typeItemFactory, psiTypeParameter ->
                val refs = psiTypeParameter.extendsList.referencedTypes
                if (refs.isEmpty()) {
                    WellKnownTypes.defaultTypeParameterBounds(psiTypeParameter.isKotlin())
                } else {
                    refs.mapNotNull { typeItemFactory.getBoundsType(PsiTypeInfo(it)) }
                }
            },
        )
    }

    /** Create a [SkeletonTypeParameterItem] for [psiTypeParameter] */
    private fun createTypeParameterItem(
        psiTypeParameter: PsiTypeParameter
    ): SkeletonTypeParameterItem {
        val simpleName = psiTypeParameter.name!!
        val modifiers =
            createModifiers(
                ModifierContext.forItemKind(ItemKind.TYPE_PARAMETER),
                psiTypeParameter,
            )

        return itemFactory.createTypeParameterItem(
            modifiers = modifiers,
            name = simpleName,
            // Functions with reified type parameters can only be used from Kotlin source, which
            // means they are processed by KaCodebaseAssembler instead of through PsiClassBuilder
            // because they don't exist as real UAST.
            isReified = false,
        )
    }
}

/** Whether [this] is a file-facade class. See [ClassItem.isFileFacade]. */
private fun PsiClass.isFileFacade(): Boolean {
    return isKotlin() && this is UClass && this.javaPsi is KtLightClassForFacade
}

/** Whether [this] is a multi-file class. See [ClassItem.isMultiFileClass]. */
private fun PsiClass.isMultiFileClass() =
    ((this as? UClass)?.javaPsi as? KtLightClassForFacade)?.multiFileClass == true

/**
 * Check to see whether the [PsiField] on which this is called has an initializer whose
 * [TypeNullability] is known to be [TypeNullability.NONNULL].
 */
private fun PsiField.isFieldInitializerNonNull(): Boolean {
    // If no initializer was provided then it cannot be non-null.
    val initializer = initializer ?: return false

    // If we're looking at a final field, look on the right hand side of the field to the field
    // initialization. If that right hand side for example represents a method call, and the method
    // we're calling is annotated with @NonNull, then the field (since it is final) will always be
    // @NonNull as well.
    when (initializer) {
        is PsiReference -> {
            initializer.resolve()
        }
        is PsiCallExpression -> {
            initializer.resolveMethod()
        }
        else -> null
    }?.let { resolved ->
        if (
            resolved is PsiModifierListOwner &&
                resolved.annotations.any { isNonNullAnnotation(it.qualifiedName ?: "") }
        ) {
            return true
        }
    }

    // Try and compute a constant value.
    computeConstantValue()?.let {
        // If it was non-null then the field must be non-null.
        return true
    }

    JavaConstantExpressionEvaluator.computeConstantExpression(initializer, false)?.let {
        // If it was non-null then the field must be non-null.
        return true
    }

    return false
}

/** Returns `true` if this [PsiMethod] is part of a Kotlin property. */
private fun PsiMethod.isKotlinProperty(): Boolean {
    return (this is UMethod) &&
        when (val source = sourcePsi) {
            is KtProperty -> true
            is KtPropertyAccessor -> true
            is KtParameter -> source.hasValOrVar()
            else -> false
        }
}

/**
 * Whether the [UMethod] is the primary constructor of a Kotlin class. A primary constructor is
 * declared in the class header, and all other constructors must delegate to it (see
 * https://kotlinlang.org/docs/classes.html#constructors).
 */
private val UMethod.isPrimaryConstructor: Boolean
    get() = sourcePsi is KtPrimaryConstructor || sourcePsi is KtClassOrObject
