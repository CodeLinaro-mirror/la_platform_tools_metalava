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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeItem
import java.util.IdentityHashMap

/**
 * Updates the implicit permits list of any sealed [ClassItem] that does not have one provided
 * explicitly.
 */
class SealedClassImplicitPermitTypesUpdater
private constructor(
    private val codebase: Codebase,
) {
    /**
     * Optional [IdentityHashMap] from a sealed [ClassItem] to its subclasses, including private
     * ones that will not appear in the API.
     */
    private val classToPermits: MutableMap<ClassItem, MutableList<ClassTypeItem>> =
        IdentityHashMap()

    /**
     * Visit all [ClassItem]s in [codebase] computing and setting their [ClassItem.permitTypes], if
     * necessary.
     */
    private fun updateImplicitPermitTypes() {
        for (classItem in codebase.getPackages().allClasses()) {
            // Ignore any classes from the class path as sealed classes and their subclasses must
            // all be compiled at the same time.
            if (classItem.origin == ClassOrigin.CLASS_PATH) continue

            updateSealedStatusForClass(classItem)
        }

        // Apply the permit types information collected to each class.
        for ((classItem, permitTypes) in classToPermits.entries) {
            val skeletonClassItem = classItem as SkeletonClassItem
            permitTypes.sortWith(TypeItem.qualifiedComparator)
            skeletonClassItem.permitTypes = permitTypes
        }
    }

    /**
     * Recursively traverses the inner classes of [classItem] to determine if any sealed super
     * classes should be marked as non-exhaustive.
     *
     * A sealed class is considered non-exhaustive if it has at least one inaccessible subclass.
     *
     * @param classItem The current [ClassItem] being checked.
     */
    private fun updateSealedStatusForClass(
        classItem: ClassItem,
    ) {
        // If a ClassItem already exists for this psiClass, use its modifiers. Otherwise, create
        // new ones.
        val modifiers = classItem.modifiers

        // Java requires that all subtypes of a sealed type are either `final`, `sealed`, or
        // `non-sealed`. If they are not then they can never affect the sealed status.
        val isJavaClass = classItem.sourceLanguage == SourceLanguage.JAVA
        if (isJavaClass && !modifiers.mayBeSubtypeOfJavaSealedType()) {
            return
        }

        classItem.superClassType()?.let { processPossibleSealedClassType(it, classItem) }

        for (interfaceType in classItem.interfaceTypes()) {
            processPossibleSealedClassType(interfaceType, classItem)
        }
    }

    /**
     * Process [classTypeItem] which may be a sealed class.
     *
     * This is called for every [ClassTypeItem] that may be a sealed class that may need its permits
     * type computed.
     *
     * @param subClassItem the [ClassItem] that is a subclass of [classTypeItem].
     */
    private fun processPossibleSealedClassType(
        classTypeItem: ClassTypeItem,
        subClassItem: ClassItem,
    ) {
        // Try and find the class. This does not use resolve as sealed class and its subclasses must
        // be defined together. If it cannot be found then it cannot be a sealed class so return
        // immediately.
        val classItem = codebase.findClass(classTypeItem.qualifiedName) ?: return

        // If the class item is not a sealed class then there is nothing to do so return
        // immediately.
        if (!classItem.modifiers.isSealed()) return

        // If the sealed class has an empty list of permit types then keep track of its actual
        // subclasses.
        if (classItem.permitTypes.isEmpty()) {
            val permits = classToPermits.computeIfAbsent(classItem) { mutableListOf() }
            permits.add(subClassItem.type().substitute(arguments = emptyList()))
        }
    }

    companion object {
        /**
         * Compute and update the [ClassItem.permitTypes] of sealed [ClassItem]s in [codebase] for
         * any sealed [ClassItem] that does not have an explicit permits list.
         */
        fun updateImplicitPermitTypes(codebase: Codebase) {
            SealedClassImplicitPermitTypesUpdater(codebase).updateImplicitPermitTypes()
        }
    }
}
