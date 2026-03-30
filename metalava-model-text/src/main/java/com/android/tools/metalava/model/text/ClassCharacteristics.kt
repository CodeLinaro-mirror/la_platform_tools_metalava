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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.reporter.FileLocation

/**
 * Characteristics of a class apart from its members.
 *
 * This is basically everything that could appear on the line defining the class in the API
 * signature file.
 */
internal data class ClassCharacteristics(
    /** The position of the class definition within the API signature file. */
    val fileLocation: FileLocation,

    /** Name including package and full name. */
    val qualifiedName: String,

    /**
     * Full name, this is in addition to [qualifiedName] as it is possible for two classed to have
     * the same qualified name but different full names. e.g. `a.b.c.D.E` in package `a.b.c` has a
     * full name of `D.E` but in a package `a.b` has a full name of `c.D.E`. While those names would
     * break naming conventions and so would be unlikely they are possible.
     */
    val fullName: String,

    /** The kind of the class. */
    val classKind: ClassKind,

    /** The modifiers. */
    val modifiers: ModifierList,

    /** The super class type . */
    val superClassType: ClassTypeItem?,

    /** The interface types. */
    val interfaceTypes: Set<ClassTypeItem>,

    /** The aliased type. Should be non-null only when [classKind] is [ClassKind.TYPEALIAS]. */
    val optionalAliasedType: TypeItem?,
) {
    /**
     * Checks if the [other] from different signature file can be merged with this
     * [ClassCharacteristics]. For instance, `current.txt` and `system-current.txt` may contain
     * equal class definitions with different class methods. This method is used to determine if the
     * two [ClassItem]s can be safely merged in such scenarios.
     *
     * @param other [ClassCharacteristics] to be checked if it is compatible with `this` and can be
     *   merged
     * @param allowModifierChanges whether to consider classes compatible if they do not have
     *   equivalent modifiers
     * @return a Boolean value representing if [other] is compatible with `this`
     */
    fun isCompatible(other: ClassCharacteristics, allowModifierChanges: Boolean): Boolean {
        // TODO(b/323168612): Check super interface types and super class type of the two
        // TextClassItem
        return fullName == other.fullName &&
            // Allow class kind changing to typealias, but no other changes.
            (classKind == other.classKind || other.classKind == ClassKind.TYPEALIAS) &&
            (allowModifierChanges || modifiers.equivalentTo(null, other.modifiers))
    }

    companion object {
        fun of(classItem: ClassItem): ClassCharacteristics =
            ClassCharacteristics(
                fileLocation = classItem.fileLocation,
                qualifiedName = classItem.qualifiedName(),
                fullName = classItem.fullName(),
                classKind = classItem.classKind,
                modifiers = classItem.modifiers,
                superClassType = classItem.superClassType(),
                interfaceTypes = classItem.interfaceTypes().toSet(),
                optionalAliasedType = classItem.optionalAliasedType,
            )
    }
}
