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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.value.Value

/**
 * Merges class re-definitions encountered across multiple signature files into existing
 * [SkeletonClassItem]s after all signature files have been parsed.
 *
 * When parsing multiple signature files into a single [Codebase] (for example, a base API file such
 * as `current.txt` followed by an extending API file such as `system-current.txt`, or common and
 * platform source sets in a multiplatform API), a class already registered in an earlier signature
 * file may be re-defined in a subsequent signature file. The re-definition must be checked for
 * compatibility with the existing [SkeletonClassItem] and then merged into it (to add or replace
 * annotations, update modifiers, update superclass or interface types, transition an `expect class`
 * to an `actual typealias`, or promote its [ClassOrigin] from [ClassOrigin.CLASS_PATH] to
 * [ClassOrigin.COMMAND_LINE]).
 *
 * Performing this check and merge must be deferred until after all signature files have been
 * parsed:
 * 1. Checking modifier compatibility ([ModifierList.equivalentTo]) and merging annotations requires
 *    comparing [AnnotationItem]s and their attribute [Value]s.
 * 2. Comparing annotation attribute [Value]s may require resolving the annotation's class in the
 *    [Codebase] to determine the types of its attributes.
 * 3. If merging were performed while parsing a signature file, an annotation on a re-defined class
 *    could be resolved before the annotation's own `@interface` definition later in the signature
 *    file has been parsed. Resolving an unknown class fabricates a stub [ClassItem] with
 *    [ClassKind.CLASS].
 * 4. When the actual `@interface` definition is subsequently parsed with
 *    [ClassKind.ANNOTATION_TYPE], it would find the fabricated stub [ClassItem] already registered
 *    in the [Codebase], compare the stub's [ClassKind.CLASS] against [ClassKind.ANNOTATION_TYPE],
 *    and fail with an incompatible class definition error.
 *
 * Recording each re-definition's [ClassCharacteristics] via [deferMergingIntoExistingClass] during
 * parsing and executing [performAnyDeferredMerges] only after all signature files have been parsed
 * ensures that all declared classes exist in the [Codebase] before any classes are resolved during
 * merging.
 */
internal class ClassMerger(
    /**
     * Whether class modifiers and annotations are allowed to change (and be overwritten rather than
     * combined) when merging a re-definition into an existing class.
     */
    private val allowClassModifierChanges: Boolean = false,
) {
    /**
     * A map from [SkeletonClassItem] to list of [ClassCharacteristics] for re-definition of the
     * original class that needs to be checked for consistency against the [SkeletonClassItem] and
     * then merge any extensions into it.
     */
    private val deferredMerges =
        mutableMapOf<SkeletonClassItem, MutableList<ClassCharacteristics>>()

    /**
     * Defer merging [newClassCharacteristics] into [existingClass] until after all signature files
     * have been resolved.
     */
    fun deferMergingIntoExistingClass(
        existingClass: SkeletonClassItem,
        newClassCharacteristics: ClassCharacteristics,
    ) {
        val merges = deferredMerges.computeIfAbsent(existingClass) { mutableListOf() }
        merges.add(newClassCharacteristics)
    }

    /** Perform any deferred merges added by [deferMergingIntoExistingClass]. */
    fun performAnyDeferredMerges() {
        for ((existingClass, newClasses) in deferredMerges) {
            for (newClassCharacteristics in newClasses) {
                tryMergingIntoExistingClass(existingClass, newClassCharacteristics)
            }
        }
    }

    /**
     * Try merging [newClassCharacteristics] into [existingClass] that was previously loaded from a
     * separate signature file.
     *
     * Will throw an [ApiParseException] if [existingClass] is not compatible with
     * [newClassCharacteristics].
     */
    private fun tryMergingIntoExistingClass(
        existingClass: SkeletonClassItem,
        newClassCharacteristics: ClassCharacteristics,
    ) {
        // Make sure the new class characteristics are compatible with the old class
        // characteristic.
        val existingCharacteristics = ClassCharacteristics.of(existingClass)
        if (
            !existingCharacteristics.isCompatible(
                newClassCharacteristics,
                allowModifierChanges = allowClassModifierChanges
            )
        ) {
            throw ApiParseException(
                "Incompatible $existingClass definitions",
                newClassCharacteristics.fileLocation
            )
        }

        // If the existing class was loaded from the class path (so it was not marked to be emitted)
        // and the re-definition was loaded from the command line, promote the class and its
        // containing package so that they will be emitted.
        if (
            existingClass.origin == ClassOrigin.CLASS_PATH &&
                newClassCharacteristics.classOrigin == ClassOrigin.COMMAND_LINE
        ) {
            existingClass.origin = ClassOrigin.COMMAND_LINE
            existingClass.emit = true
            existingClass.containingPackage().emit = true
        }

        // Handle the transition to typealias (other class kind changes are not allowed)
        if (
            existingClass.classKind != ClassKind.TYPEALIAS &&
                newClassCharacteristics.classKind == ClassKind.TYPEALIAS
        ) {
            existingClass.classKind = ClassKind.TYPEALIAS
            existingClass.optionalAliasedType = newClassCharacteristics.optionalAliasedType
        }

        // Add new annotations to the existing class
        val newClassAnnotations = newClassCharacteristics.modifiers.annotations().toSet()
        val existingClassAnnotations = existingCharacteristics.modifiers.annotations().toSet()

        // If class modifier changes are allowed, overwrite the old annotations with the new ones.
        // Otherwise, add the new ones.
        if (allowClassModifierChanges) {
            if (existingClassAnnotations != newClassAnnotations) {
                existingClass.mutateModifiers {
                    mutateAnnotations {
                        clear()
                        addAll(newClassAnnotations)
                    }
                }
            }
        } else {
            val extraAnnotations = newClassAnnotations.subtract(existingClassAnnotations)
            if (extraAnnotations.isNotEmpty()) {
                existingClass.mutateModifiers { mutateAnnotations { addAll(extraAnnotations) } }
            }
        }

        // If the class modifiers are allowed to change and have, update them.
        if (
            allowClassModifierChanges &&
                !newClassCharacteristics.modifiers.equivalentTo(
                    existingClass,
                    existingClass.modifiers
                )
        ) {
            existingClass.mutateModifiers { makeEquivalentTo(newClassCharacteristics.modifiers) }
        }

        // Use the latest super class.
        val newSuperClassType = newClassCharacteristics.superClassType
        if (
            newSuperClassType != null && existingCharacteristics.superClassType != newSuperClassType
        ) {
            // Duplicate class with conflicting superclass names are found. Since the class
            // definition found later should be prioritized, overwrite the superclass type.
            existingClass.setSuperClassType(newSuperClassType)
        }

        // If the interface types in the new definition are set, overwrite the original interface
        // types since the later definition should be prioritized.
        val newInterfaceTypes = newClassCharacteristics.interfaceTypes
        if (
            newInterfaceTypes.isNotEmpty() &&
                newInterfaceTypes != existingCharacteristics.interfaceTypes
        ) {
            existingClass.setInterfaceTypes(newInterfaceTypes.toList())
        }
    }
}
