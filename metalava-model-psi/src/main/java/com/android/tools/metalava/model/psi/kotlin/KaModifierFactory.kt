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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.JVM_STATIC
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.hasAnnotation
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.isTopLevel

/** Creates modifiers for ka symbols. */
internal class KaModifierFactory(private val assembler: KaCodebaseAssembler) {
    /** Creates modifiers for the [propertySymbol]. */
    fun createForProperty(
        propertySymbol: KaPropertySymbol,
        containingClass: ClassItem,
        getter: MethodItem?,
        setter: MethodItem?
    ): MutableModifierList {
        val modifiers = createForDeclaration(propertySymbol, containingClass)

        // Maintaining legacy behavior: if an annotation was supposed to apply to a backing field or
        // constructor parameter but didn't specify a use-site target, metalava would apply it to
        // the property when creating properties through psi.
        val parameterAnnotations =
            if (propertySymbol.isFromPrimaryConstructor) {
                analyze(assembler.kaModule) {
                    val scope =
                        (propertySymbol.containingSymbol as? KaNamedClassSymbol)
                            ?.combinedDeclaredMemberScope
                    scope
                        ?.constructors
                        ?.firstOrNull { it.isPrimary }
                        ?.valueParameters
                        ?.firstOrNull { it.name == propertySymbol.name }
                        ?.annotations ?: emptyList()
                }
            } else {
                emptyList()
            }
        val fieldAnnotations = propertySymbol.backingFieldSymbol?.annotations ?: emptyList()
        for (annotationItem in
            (parameterAnnotations + fieldAnnotations)
                .filter { it.useSiteTarget == null }
                .mapNotNull { assembler.createAnnotation(it) }) {
            modifiers.addAnnotation(annotationItem)
        }

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

        // Special case for RequiresOptIn-annotated annotations: when these are applied
        // to a property, they are implicitly propagated to the getter and setter
        // (if present) for Kotlin clients. Match Kotlin compiler behavior by propagating.
        // Note that the AndroidX experimental lint check will not recognize usages of the
        // accessors by Java clients as experimental. Because of this AndroidX bans defining
        // public experimental properties in projects that target Java clients.
        for (annotationItem in modifiers.annotations()) {
            if (annotationItem.isSuppressCompatibilityAnnotation()) {
                // Manually setting a RequiresOptIn annotation on a getter causes a
                // compiler warning, but this can be suppressed with
                // @Suppress("OPT_IN_MARKER_ON_WRONG_TARGET"). Safely handle
                // such cases by only adding the annotation if it wasn't explicitly added.
                if (getter != null && annotationItem !in getter.modifiers.annotations()) {
                    getter.mutateModifiers { addAnnotation(annotationItem) }
                }
                // Explicit RequiresOptIn annotations on setters are supported by the
                // compiler, so we should only add this annotation implicitly if it is not
                // already explicitly provided.
                if (setter != null && annotationItem !in setter.modifiers.annotations()) {
                    setter.mutateModifiers { addAnnotation(annotationItem) }
                }
            }
        }

        for (annotationItem in getter?.modifiers?.annotations() ?: emptyList()) {
            if (annotationItem !in modifiers.annotations()) {
                modifiers.addAnnotation(annotationItem)
            }
        }

        // Const vals have the static and const modifiers
        if ((propertySymbol as? KaKotlinPropertySymbol)?.isConst == true) {
            modifiers.setStatic(true)
            modifiers.setConst(true)
        }

        if (propertySymbol.isStatic || modifiers.hasAnnotation { it.qualifiedName == JVM_STATIC }) {
            modifiers.setStatic(true)
        }

        // If a property is declared as inline, find that through the getter.
        if (propertySymbol.getter?.isInline == true) {
            modifiers.setInline(true)
        }

        return modifiers
    }

    /** Create modifiers for any declaration. */
    fun createForDeclaration(
        symbol: KaDeclarationSymbol,
        containingClass: ClassItem? = null
    ): MutableModifierList {
        val visibility =
            when (symbol.visibility) {
                KaSymbolVisibility.PUBLIC -> VisibilityLevel.PUBLIC
                KaSymbolVisibility.PACKAGE_PRIVATE -> VisibilityLevel.PACKAGE_PRIVATE
                KaSymbolVisibility.INTERNAL -> VisibilityLevel.INTERNAL
                // KaSymbolVisibility distinguishes between Kotlin protected (visible to containing
                // declaration and subclasses) and Java protected (additionally visible to other
                // classes in the same package). Metalava does not make this distinction.
                KaSymbolVisibility.PROTECTED,
                KaSymbolVisibility.PACKAGE_PROTECTED -> VisibilityLevel.PROTECTED
                // Local and unknown visibility shouldn't occur for API elements, treat them as
                // private if they do.
                KaSymbolVisibility.PRIVATE,
                KaSymbolVisibility.LOCAL,
                KaSymbolVisibility.UNKNOWN -> VisibilityLevel.PRIVATE
            }
        val annotations = symbol.annotations.mapNotNull { assembler.createAnnotation(it) }
        val modifiers = createMutableModifiers(visibility, annotations)

        // Set keyword modifiers if applicable
        if (symbol.isActual) {
            modifiers.setActual(true)
        }
        if (symbol.isExpect) {
            modifiers.setExpect(true)
        }
        // Top level functions correspond to static definitions in file facade classes
        if (symbol.isTopLevel) {
            modifiers.setStatic(true)
        }
        when (symbol.modality) {
            KaSymbolModality.FINAL -> modifiers.setFinal(true)
            KaSymbolModality.SEALED -> modifiers.setSealed(true)
            KaSymbolModality.OPEN -> modifiers.setFinal(false)
            KaSymbolModality.ABSTRACT -> modifiers.setAbstract(true)
        }

        // Since properties and methods in final classes must be final, the modifiers aren't marked
        // as final to avoid printing it redundantly
        if (containingClass?.modifiers?.isFinal() == true) {
            modifiers.setFinal(false)
        } else if (containingClass?.isAnnotationType() == true) {
            // Annotation class properties and functions are non-final and abstract
            modifiers.setFinal(false)
            modifiers.setAbstract(true)
        }

        // If a property or function in an interface isn't abstract, it has a default implementation
        if (containingClass?.isInterface() == true && !modifiers.isAbstract()) {
            modifiers.setDefault(true)
        }

        if (annotations.any { it.qualifiedName == "kotlin.Deprecated" }) {
            modifiers.setDeprecated(true)
        }

        return modifiers
    }
}
