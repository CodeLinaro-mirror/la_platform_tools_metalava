/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.model.ANDROID_DEPRECATED_FOR_SDK
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.JAVA_LANG_ANNOTATION_TARGET
import com.android.tools.metalava.model.JAVA_LANG_TYPE_USE_TARGET
import com.android.tools.metalava.model.JVM_STATIC
import com.android.tools.metalava.model.ModifierFlags.Companion.ABSTRACT
import com.android.tools.metalava.model.ModifierFlags.Companion.ACTUAL
import com.android.tools.metalava.model.ModifierFlags.Companion.COMPANION
import com.android.tools.metalava.model.ModifierFlags.Companion.CONST
import com.android.tools.metalava.model.ModifierFlags.Companion.DATA
import com.android.tools.metalava.model.ModifierFlags.Companion.DEFAULT
import com.android.tools.metalava.model.ModifierFlags.Companion.EXPECT
import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.FUN
import com.android.tools.metalava.model.ModifierFlags.Companion.INFIX
import com.android.tools.metalava.model.ModifierFlags.Companion.INLINE
import com.android.tools.metalava.model.ModifierFlags.Companion.INTERNAL
import com.android.tools.metalava.model.ModifierFlags.Companion.NATIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.OPERATOR
import com.android.tools.metalava.model.ModifierFlags.Companion.PACKAGE_PRIVATE
import com.android.tools.metalava.model.ModifierFlags.Companion.PRIVATE
import com.android.tools.metalava.model.ModifierFlags.Companion.PROTECTED
import com.android.tools.metalava.model.ModifierFlags.Companion.PUBLIC
import com.android.tools.metalava.model.ModifierFlags.Companion.SEALED
import com.android.tools.metalava.model.ModifierFlags.Companion.STATIC
import com.android.tools.metalava.model.ModifierFlags.Companion.STRICT_FP
import com.android.tools.metalava.model.ModifierFlags.Companion.SUSPEND
import com.android.tools.metalava.model.ModifierFlags.Companion.SYNCHRONIZED
import com.android.tools.metalava.model.ModifierFlags.Companion.TRANSIENT
import com.android.tools.metalava.model.ModifierFlags.Companion.VALUE
import com.android.tools.metalava.model.ModifierFlags.Companion.VARARG
import com.android.tools.metalava.model.ModifierFlags.Companion.VOLATILE
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isNullnessAnnotation
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.light.LightModifierList
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.kotlin.KotlinUMethodWithFakeLightDelegateBase
import org.jetbrains.uast.toUElement

internal object PsiModifierItem {
    fun create(
        codebase: PsiBasedCodebase,
        element: PsiModifierListOwner,
    ): MutableModifierList {
        val modifiers =
            if (element is UAnnotated) {
                createFromUAnnotated(codebase, element, element)
            } else {
                createFromPsiElement(codebase, element)
            }

        // Sometimes Psi/Kotlin interoperation goes a little awry and adds nullability annotations
        // that it should not, so this removes them.
        if (shouldRemoveNullnessAnnotations(modifiers)) {
            modifiers.mutateAnnotations { removeIf { it.isNullnessAnnotation() } }
        }

        if (
            hasDeprecatedAnnotation(modifiers) ||
                // Check for @Deprecated on sourcePsi
                isDeprecatedFromSourcePsi(element)
        ) {
            modifiers.setDeprecated(true)
        }

        return modifiers
    }

    /**
     * Creates modifiers for the property represented by [ktDeclaration] using the [KtModifierList]
     * and from the property. Uses annotations from the [getter] if it exists in addition to
     * property annotations because property modifiers used to be created just from the getter and
     * some places rely on the old behavior for annotations (@RestrictTo in AndroidX is only
     * applicable to accessors, not properties themselves).
     */
    fun createForProperty(
        codebase: PsiBasedCodebase,
        ktDeclaration: KtDeclaration,
        getter: PsiMethodItem?,
        setter: PsiMethodItem?,
    ): MutableModifierList {
        val ktModifierList = ktDeclaration.modifierList
        val visibilityFlags =
            visibilityFlags(
                psiModifierList = null,
                ktModifierList = ktModifierList,
                element = ktDeclaration,
                sourcePsi = ktDeclaration
            )
        val kotlinFlags = kotlinFlags { token ->
            ktModifierList?.hasModifier(token) ?: ktDeclaration.hasModifier(token)
        }
        val javaFlags = javaFlagsForKotlinElement(ktDeclaration)
        val flags = visibilityFlags or kotlinFlags or javaFlags

        // Use the flags computed from the property, and the getter annotations, if they exist.
        val modifiers =
            createMutableModifiers(
                flags,
                // Filter deprecated annotations: the property will pull effectivelyDeprecated
                // status from its getter, but the originallyDeprecated value should reflect
                // the property itself, to avoid propagating deprecation from getter to property to
                // setter. The setter should only inherit deprecation from the property itself.
                getter?.modifiers?.annotations()?.filter { !isDeprecatedAnnotation(it) }
                    ?: emptyList()
            )

        // Correct visibility of accessors (work around K2 bugs with value class type properties)
        // https://youtrack.jetbrains.com/issue/KT-74205
        // The getter must have the same visibility as the property
        val propertyVisibility = modifiers.getVisibilityLevel()
        if (getter != null && getter.modifiers.getVisibilityLevel() != propertyVisibility) {
            getter.mutateModifiers { setVisibilityLevel(modifiers.getVisibilityLevel()) }
        }
        // The setter cannot be more visible than the property
        if (setter != null && setter.modifiers.getVisibilityLevel() > propertyVisibility) {
            setter.mutateModifiers { setVisibilityLevel(modifiers.getVisibilityLevel()) }
        }

        // Annotations whose target is property won't be bound to anywhere in LC/UAST, if the
        // property doesn't need a backing field. Same for unspecified use-site target.
        // Add all annotations applied to the property by examining source PSI directly.
        for (ktAnnotationEntry in ktDeclaration.annotationEntries) {
            val useSiteTarget = ktAnnotationEntry.useSiteTarget?.getAnnotationUseSiteTarget()
            if (useSiteTarget == null || useSiteTarget == AnnotationUseSiteTarget.PROPERTY) {
                val uAnnotation = ktAnnotationEntry.toUElement() as? UAnnotation ?: continue
                val annotationItem = UAnnotationItem.create(codebase, uAnnotation) ?: continue
                if (annotationItem !in modifiers.annotations()) {
                    modifiers.addAnnotation(annotationItem)
                }
                // Make sure static definitions are marked
                if (annotationItem.qualifiedName == JVM_STATIC) {
                    modifiers.setStatic(true)
                }
            }
        }

        if (hasDeprecatedAnnotation(modifiers)) {
            modifiers.setDeprecated(true)
        }

        return modifiers
    }

    /** Determine whether nullness annotations need removing from [modifiers]. */
    private fun shouldRemoveNullnessAnnotations(
        modifiers: BaseModifierList,
    ): Boolean {
        // Kotlin varargs are not nullable but can sometimes and up with an @Nullable annotation
        // added to the [PsiParameter] so remove it from the modifiers. Only Kotlin varargs have a
        // `vararg` modifier.
        if (modifiers.isVarArg()) {
            return true
        }

        return false
    }

    private fun hasDeprecatedAnnotation(modifiers: BaseModifierList) =
        modifiers.hasAnnotation(::isDeprecatedAnnotation)

    private fun isDeprecatedAnnotation(annotationItem: AnnotationItem): Boolean =
        annotationItem.qualifiedName.let { qualifiedName ->
            qualifiedName == "Deprecated" ||
                qualifiedName.endsWith(".Deprecated") ||
                // DeprecatedForSdk that do not apply to this API surface have been filtered
                // out so if any are left then treat it as a standard Deprecated annotation.
                qualifiedName == ANDROID_DEPRECATED_FOR_SDK
        }

    private fun isDeprecatedFromSourcePsi(element: PsiModifierListOwner): Boolean {
        if (element is UMethod) {
            // NB: we can't use sourcePsi.annotationEntries directly due to
            // annotation use-site targets. The given `javaPsi` as a light element,
            // which spans regular functions, property accessors, etc., is already
            // built with targeted annotations. Even KotlinUMethod is using LC annotations.
            return element.javaPsi.isDeprecated
        }
        return ((element as? UElement)?.sourcePsi as? KtAnnotated)?.annotationEntries?.any {
            it.shortName?.toString() == "Deprecated"
        }
            ?: false
    }

    private fun computeFlag(element: PsiModifierListOwner, modifierList: PsiModifierList): Int {
        var flags = 0
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            flags = flags or STATIC
        }
        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
            flags = flags or ABSTRACT
        }
        if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
            flags = flags or FINAL
        }
        if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
            flags = flags or NATIVE
        }
        if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            flags = flags or SYNCHRONIZED
        }
        if (modifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
            flags = flags or STRICT_FP
        }
        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
            flags = flags or TRANSIENT
        }
        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
            flags = flags or VOLATILE
        }
        if (modifierList.hasModifierProperty(PsiModifier.DEFAULT)) {
            flags = flags or DEFAULT
        }

        // Look for special Kotlin keywords
        var ktModifierList: KtModifierList? = null
        val sourcePsi = (element as? UElement)?.sourcePsi
        when (modifierList) {
            is KtLightElement<*, *> -> {
                ktModifierList = modifierList.kotlinOrigin as? KtModifierList
            }
            is LightModifierList -> {
                if (sourcePsi is KtModifierListOwner) {
                    ktModifierList = sourcePsi.modifierList
                }
            }
        }

        // Merge in the visibility flags.
        val visibilityFlags = visibilityFlags(modifierList, ktModifierList, element, sourcePsi)
        flags = flags or visibilityFlags

        // Merge in kotlin flags
        if (ktModifierList != null) {
            flags = flags or kotlinFlags { token -> ktModifierList.hasModifier(token) }
        }
        return flags
    }

    /** Determine the element visibility, which can come from several sources. */
    private fun visibilityFlags(
        psiModifierList: PsiModifierList?,
        ktModifierList: KtModifierList?,
        element: PsiElement?,
        sourcePsi: PsiElement?
    ): Int {
        var visibilityFlags =
            when {
                psiModifierList?.hasModifierProperty(PsiModifier.PUBLIC) == true -> PUBLIC
                psiModifierList?.hasModifierProperty(PsiModifier.PROTECTED) == true -> PROTECTED
                psiModifierList?.hasModifierProperty(PsiModifier.PRIVATE) == true -> PRIVATE
                ktModifierList != null ->
                    when {
                        ktModifierList.hasModifier(KtTokens.PRIVATE_KEYWORD) -> PRIVATE
                        ktModifierList.hasModifier(KtTokens.PROTECTED_KEYWORD) -> PROTECTED
                        ktModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD) -> INTERNAL
                        else -> PUBLIC
                    }
                // UAST workaround: fake light method for inline/hidden function may not have a
                // concrete modifier list, but overrides `hasModifierProperty` to mimic
                // modifiers.
                element is KotlinUMethodWithFakeLightDelegateBase<*> ->
                    when {
                        element.hasModifierProperty(PsiModifier.PUBLIC) -> PUBLIC
                        element.hasModifierProperty(PsiModifier.PROTECTED) -> PROTECTED
                        element.hasModifierProperty(PsiModifier.PRIVATE) -> PRIVATE
                        else -> PUBLIC
                    }
                sourcePsi is KtModifierListOwner ->
                    when {
                        sourcePsi.hasModifier(KtTokens.PRIVATE_KEYWORD) -> PRIVATE
                        sourcePsi.hasModifier(KtTokens.PROTECTED_KEYWORD) -> PROTECTED
                        sourcePsi.hasModifier(KtTokens.INTERNAL_KEYWORD) -> INTERNAL
                        else -> PUBLIC
                    }
                else -> PACKAGE_PRIVATE
            }
        if (ktModifierList != null) {
            if (ktModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                // Reset visibilityFlags to INTERNAL if the internal modifier is explicitly
                // present on the element
                visibilityFlags = INTERNAL
            } else if (
                ktModifierList.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                    ktModifierList.visibilityModifier() == null &&
                    sourcePsi is KtElement
            ) {
                // Reset visibilityFlags to INTERNAL if the element has no explicit visibility
                // modifier, but overrides an internal declaration. Adapted from
                // org.jetbrains.kotlin.asJava.classes.UltraLightMembersCreator.isInternal
                analyze(sourcePsi) {
                    val symbol = (sourcePsi as? KtDeclaration)?.symbol
                    val visibility = symbol?.visibility
                    if (visibility == KaSymbolVisibility.INTERNAL) {
                        visibilityFlags = INTERNAL
                    }
                }
            }
        }

        if (ktModifierList?.hasModifier(KtTokens.INLINE_KEYWORD) == true) {
            // Workaround for b/117565118:
            val func = sourcePsi as? KtNamedFunction
            if (
                func != null &&
                    (func.typeParameterList?.text ?: "").contains(KtTokens.REIFIED_KEYWORD.value) &&
                    !ktModifierList.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
                    !ktModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD)
            ) {
                // Switch back from private to public
                visibilityFlags = PUBLIC
            }
        }

        // Methods that are property accessors inherit visibility from the source element
        if (element is UMethod && (element.sourceElement is KtPropertyAccessor)) {
            val sourceElement = element.sourceElement
            if (sourceElement is KtModifierListOwner) {
                val sourceModifierList = sourceElement.modifierList
                if (sourceModifierList != null) {
                    if (sourceModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                        visibilityFlags = INTERNAL
                    }
                }
            }
        }

        return visibilityFlags
    }

    /** Computes Kotlin-specific flags. */
    private fun kotlinFlags(hasModifier: (KtModifierKeywordToken) -> Boolean): Int {
        var flags = 0
        if (hasModifier(KtTokens.VARARG_KEYWORD)) {
            flags = flags or VARARG
        }
        if (hasModifier(KtTokens.SEALED_KEYWORD)) {
            flags = flags or SEALED
        }
        if (hasModifier(KtTokens.INFIX_KEYWORD)) {
            flags = flags or INFIX
        }
        if (hasModifier(KtTokens.CONST_KEYWORD)) {
            flags = flags or CONST
        }
        if (hasModifier(KtTokens.OPERATOR_KEYWORD)) {
            flags = flags or OPERATOR
        }
        if (hasModifier(KtTokens.INLINE_KEYWORD)) {
            flags = flags or INLINE
        }
        if (hasModifier(KtTokens.VALUE_KEYWORD)) {
            flags = flags or VALUE
        }
        if (hasModifier(KtTokens.SUSPEND_KEYWORD)) {
            flags = flags or SUSPEND
        }
        if (hasModifier(KtTokens.COMPANION_KEYWORD)) {
            flags = flags or COMPANION
        }
        if (hasModifier(KtTokens.FUN_KEYWORD)) {
            flags = flags or FUN
        }
        if (hasModifier(KtTokens.DATA_KEYWORD)) {
            flags = flags or DATA
        }
        if (hasModifier(KtTokens.EXPECT_KEYWORD)) {
            flags = flags or EXPECT
        }
        if (hasModifier(KtTokens.ACTUAL_KEYWORD)) {
            flags = flags or ACTUAL
        }
        return flags
    }

    /** Creates Java-equivalent flags for the Kotlin element. */
    private fun javaFlagsForKotlinElement(ktDeclaration: KtDeclaration): Int {
        // const values are static, and anything in a file-facade class (which top level KtElements
        // are) is also static
        return if (
            ktDeclaration.hasModifier(KtTokens.CONST_KEYWORD) ||
                ktDeclaration.isTopLevelKtOrJavaMember()
        ) {
            FINAL or STATIC
        } else if (ktDeclaration.hasModifier(KtTokens.FINAL_KEYWORD)) {
            FINAL
        } else if (ktDeclaration.isAbstractProperty()) {
            // Declarations with the abstract keyword are abstract, and so are annotation class
            // properties.
            ABSTRACT
        } else if (
            ktDeclaration.isFromInterface() &&
                ktDeclaration is KtProperty &&
                ktDeclaration.getter?.hasBody() == true
        ) {
            // Interface properties can't have backing fields, so they are abstract unless there is
            // a defined getter. If there is a defined getter, there's a default implementation.
            DEFAULT
        } else if (
            !ktDeclaration.hasModifier(KtTokens.OPEN_KEYWORD) &&
                !ktDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                !ktDeclaration.isFromInterface()
        ) {
            // Kotlin elements are final unless declared otherwise.
            FINAL
        } else {
            0
        }
    }

    private fun KtDeclaration.isFromInterface(): Boolean {
        // Can't use containingClass() here -- don't count definitions in interface companions
        return (containingClassOrObject as? KtClass)?.isInterface() == true
    }

    /**
     * Checks if the [KtDeclaration] needs the abstract modifier:
     * - if the definition used the abstract modifier
     * - if the definition is an annotation property
     * - if the definition is an interface property without a defined getter
     */
    private fun KtDeclaration.isAbstractProperty(): Boolean {
        return hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
            (containingClassOrObject as? KtClass)?.isAnnotation() == true ||
            (this is KtProperty && isFromInterface() && getter?.hasBody() != true)
    }

    /**
     * Returns a list of the targets this annotation is defined to apply to, as qualified names
     * (e.g. "java.lang.annotation.ElementType.TYPE_USE").
     *
     * If the annotation can't be resolved or does not use `@Target`, returns an empty list.
     */
    private fun PsiAnnotation.targets(): List<String> {
        return resolveAnnotationType()
            ?.annotations
            ?.firstOrNull { it.hasQualifiedName(JAVA_LANG_ANNOTATION_TARGET) }
            ?.findAttributeValue("value")
            ?.targets()
            ?: emptyList()
    }

    /**
     * Returns the element types listed in the annotation value, if the value is a direct reference
     * or an array of direct references.
     */
    private fun PsiAnnotationMemberValue.targets(): List<String> {
        return when (this) {
            is PsiReferenceExpression -> listOf(qualifiedName)
            is PsiArrayInitializerMemberValue ->
                initializers.mapNotNull { (it as? PsiReferenceExpression)?.qualifiedName }
            else -> emptyList()
        }
    }

    /**
     * Annotations which are only `TYPE_USE` should only apply to types, not the owning item of the
     * type. However, psi incorrectly applies these items to both their types and owning items.
     *
     * If an annotation targets both `TYPE_USE` and other use sites, it should be applied to both
     * types and owning items of the type if the owning item is one of the targeted use sites. See
     * https://docs.oracle.com/javase/specs/jls/se11/html/jls-9.html#jls-9.7.4 for more detail.
     *
     * To work around psi incorrectly applying exclusively `TYPE_USE` annotations to non-type items,
     * this filters all annotations which should apply to types but not the [forOwner] item.
     */
    private fun List<PsiAnnotation>.filterIncorrectTypeUseAnnotations(
        forOwner: PsiModifierListOwner
    ): List<PsiAnnotation> {
        val expectedTarget =
            when (forOwner) {
                is PsiMethod -> "java.lang.annotation.ElementType.METHOD"
                is PsiParameter -> "java.lang.annotation.ElementType.PARAMETER"
                is PsiField -> "java.lang.annotation.ElementType.FIELD"
                else -> return this
            }

        return filter { annotation ->
            val applicableTargets = annotation.targets()
            // If the annotation is not type use, it has been correctly applied to the item.
            !applicableTargets.contains(JAVA_LANG_TYPE_USE_TARGET) ||
                // If the annotation has the item type as a target, it should be applied here.
                applicableTargets.contains(expectedTarget) ||
                // For now, leave in nullness annotations until they are specially handled.
                isNullnessAnnotation(annotation.qualifiedName.orEmpty())
        }
    }

    private fun createFromPsiElement(
        codebase: PsiBasedCodebase,
        element: PsiModifierListOwner
    ): MutableModifierList {
        var flags =
            element.modifierList?.let { modifierList -> computeFlag(element, modifierList) }
                ?: PACKAGE_PRIVATE

        val psiAnnotations = element.annotations
        return if (psiAnnotations.isEmpty()) {
            createMutableModifiers(flags)
        } else {
            val annotations =
                // psi sometimes returns duplicate annotations, using distinct() to counter
                // that.
                psiAnnotations
                    .distinct()
                    // Remove any type-use annotations that psi incorrectly applied to the item.
                    .filterIncorrectTypeUseAnnotations(element)
                    .mapNotNull { PsiAnnotationItem.create(codebase, it) }
                    .filter { !it.isDeprecatedForSdk() }
            createMutableModifiers(flags, annotations)
        }
    }

    private fun createFromUAnnotated(
        codebase: PsiBasedCodebase,
        element: PsiModifierListOwner,
        annotated: UAnnotated
    ): MutableModifierList {
        val modifierList =
            element.modifierList ?: return createMutableModifiers(VisibilityLevel.PACKAGE_PRIVATE)
        val uAnnotations = annotated.uAnnotations
        val psiAnnotations =
            modifierList.annotations.takeIf { it.isNotEmpty() }
                ?: (annotated.javaPsi as? PsiModifierListOwner)?.annotations
                    ?: PsiAnnotation.EMPTY_ARRAY

        var flags = computeFlag(element, modifierList)

        return if (uAnnotations.isEmpty()) {
            if (psiAnnotations.isNotEmpty()) {
                val annotations =
                    psiAnnotations.mapNotNull { PsiAnnotationItem.create(codebase, it) }
                createMutableModifiers(flags, annotations)
            } else {
                createMutableModifiers(flags)
            }
        } else {
            val isPrimitiveVariable = element is UVariable && element.type is PsiPrimitiveType

            var annotations =
                uAnnotations
                    // Uast sometimes puts nullability annotations on primitives!?
                    .filter {
                        !isPrimitiveVariable ||
                            it.qualifiedName == null ||
                            !it.isKotlinNullabilityAnnotation
                    }
                    .mapNotNull { UAnnotationItem.create(codebase, it) }
                    .filter { !it.isDeprecatedForSdk() }

            if (!isPrimitiveVariable) {
                if (psiAnnotations.isNotEmpty() && annotations.none { it.isNullnessAnnotation() }) {
                    val ktNullAnnotation =
                        psiAnnotations.firstOrNull { psiAnnotation ->
                            psiAnnotation.qualifiedName?.let { isNullnessAnnotation(it) } == true
                        }
                    ktNullAnnotation?.let {
                        PsiAnnotationItem.create(codebase, it)?.let { annotationItem ->
                            annotations =
                                annotations.toMutableList().run {
                                    add(annotationItem)
                                    toList()
                                }
                        }
                    }
                }
            }

            createMutableModifiers(flags, annotations)
        }
    }

    /** Returns whether this is a `@DeprecatedForSdk` annotation **that should be skipped**. */
    private fun AnnotationItem.isDeprecatedForSdk(): Boolean {
        if (qualifiedName != ANDROID_DEPRECATED_FOR_SDK) {
            return false
        }

        val allowIn = findAttribute(ATTR_ALLOW_IN) ?: return false

        for (api in allowIn.leafValues()) {
            val annotationName = api.value() as? String ?: continue
            if (codebase.annotationManager.isShowAnnotationName(annotationName)) {
                return true
            }
        }

        return false
    }

    private val NOT_NULL = NotNull::class.qualifiedName
    private val NULLABLE = Nullable::class.qualifiedName

    private val UAnnotation.isKotlinNullabilityAnnotation: Boolean
        get() = qualifiedName == NOT_NULL || qualifiedName == NULLABLE
}

private const val ATTR_ALLOW_IN = "allowIn"
