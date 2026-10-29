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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.STATIC

/**
 * The kind of class.
 *
 * Corresponds to similarly named values in [javax.lang.model.element.ElementKind].
 *
 * @param description the description of this [ClassKind] that can be used in issue messages.
 * @param supportsInitializerBlock `true` if the class kind supports initializer blocks, e.g. `{
 *   field = 0; }` or `static { FIELD = 0; }`.
 * @param signatureKeyword the keyword to use in a signature file to differentiate by class kind.
 * @param implicitSuperClassType the optional super class [ClassTypeItem] that is implicit to this
 *   [ClassKind].
 * @param allowsExplicitSuperClass `true` if the class kind allows super class to be explicitly
 *   provided.
 * @param implicitInterfaceType the optional implicit interface type that classes of the class kind
 *   implement.
 * @param binarySuperClassType the optional super class that classes of this [ClassKind] extend in
 *   the binary class representation. This differs from [implicitSuperClassType] as while these
 *   appear in binary files they cannot be used in sources.
 * @param allowAbstract indicates whether this [ClassKind] allows `abstract` to be used or not. Most
 *   [ClassKind]s do allow `abstract` so this defaults to `false`.
 * @param implicitlyAbstract indicates whether this [ClassKind] is implicitly `abstract` or not.
 *   Most [ClassKind]s are not implicitly `abstract` so this defaults to `false`.
 * @param implicitlyFinal indicates whether this [ClassKind] is implicitly `final` or not. Most
 *   [ClassKind]s are not implicitly `final` so this defaults to `false`.
 * @param implicitlyStatic indicates whether this [ClassKind] is implicitly `static` or not. Most
 *   [ClassKind]s are not implicitly `static` so this defaults to `false`.
 * @param javaModifierMask the modifier flags that are allowed on this [ClassKind]. Defaults to
 *   `0.inv()`, i.e. allowing all flags.
 */
enum class ClassKind(
    val description: String,
    val supportsInitializerBlock: Boolean,
    val signatureKeyword: String,
    val implicitSuperClassType: ClassTypeItem? = null,
    val allowsExplicitSuperClass: Boolean,
    val implicitInterfaceType: ClassTypeItem? = null,
    val binarySuperClassType: ClassTypeItem? = implicitSuperClassType,
    val allowAbstract: Boolean = false,
    val implicitlyAbstract: Boolean = false,
    val implicitlyFinal: Boolean = false,
    val implicitlyStatic: Boolean = false,
    internal val javaModifierMask: Int = 0.inv(),
) {
    /** An interface. */
    INTERFACE(
        description = "interface",
        supportsInitializerBlock = false,
        signatureKeyword = "interface",
        // Interfaces do not have a super class, explicit or otherwise.
        allowsExplicitSuperClass = false,
        // Interfaces are treated as extending java.lang.Object in binary classes.
        binarySuperClassType = WellKnownTypes.JAVA_LANG_OBJECT_NON_NULL_TYPE,
        // Interfaces are implicitly abstract.
        implicitlyAbstract = true,
    ),

    /** An enum class. */
    ENUM(
        description = "enum class",
        supportsInitializerBlock = true,
        signatureKeyword = "enum",
        implicitSuperClassType = WellKnownTypes.JAVA_LANG_ENUM_NON_NULL_TYPE,
        // Enums have an implicit super class and do not allow a super class to be provided
        // explicitly.
        allowsExplicitSuperClass = false,
        // Enum classes are implicitly final even though instances can be subclasses.
        implicitlyFinal = true,
        // Enum classes are implicitly static, even when they are nested within another class.
        implicitlyStatic = true,
        // Enum classes support final and static which are both implicitly true.
        javaModifierMask = flagBits(FINAL, STATIC),
    ),

    /** An annotation class. */
    ANNOTATION_TYPE(
        description = "annotation class",
        supportsInitializerBlock = false,
        signatureKeyword = "@interface",
        // Annotation types are interfaces and so do not have a super class, explicit or otherwise.
        allowsExplicitSuperClass = false,
        implicitInterfaceType = WellKnownTypes.JAVA_LANG_ANNOTATION_NON_NULL_TYPE,
        // Annotation types are interfaces and so are treated as extending java.lang.Object in
        // binary classes.
        binarySuperClassType = WellKnownTypes.JAVA_LANG_OBJECT_NON_NULL_TYPE,
        // Annotation types are interfaces and so are implicitly abstract.
        implicitlyAbstract = true,
    ),

    /** A normal class. */
    CLASS(
        description = "class",
        supportsInitializerBlock = true,
        signatureKeyword = "class",
        // Normal classes allow super classes to be explicitly provided.
        allowsExplicitSuperClass = true,
        // Normal classes allow `abstract` modifiers.
        allowAbstract = true,
    ),

    /** A java record class */
    RECORD(
        description = "record class",
        supportsInitializerBlock = false,
        signatureKeyword = "record",
        implicitSuperClassType = WellKnownTypes.JAVA_LANG_RECORD_NON_NULL_TYPE,
        // Records have an implicit super class and do not allow a super class to be provided
        // explicitly.
        allowsExplicitSuperClass = false,
        // Record classes can never be subclasses so are implicitly final.
        implicitlyFinal = true,
    ),

    /** A typealias */
    TYPEALIAS(
        description = "typealias",
        supportsInitializerBlock = false,
        signatureKeyword = "typealias",
        // Typealiases do not have super classes, explicit or otherwise.
        allowsExplicitSuperClass = false,
    ),
    ;

    /** Set any modifiers on [modifiers] that are implicit for this [ClassKind]. */
    fun setImplicitModifiers(modifiers: MutableModifierList) {
        if (implicitlyAbstract) {
            modifiers.setAbstract(true)
        }
        if (implicitlyFinal) {
            modifiers.setFinal(true)
        }
        if (implicitlyStatic) {
            modifiers.setStatic(true)
        }
    }

    companion object {
        /** Map from [ClassKind.signatureKeyword] to [ClassKind]. */
        private val bySignatureKeyword = ClassKind.entries.associateBy { it.signatureKeyword }

        /**
         * Get the [ClassKind] whose [ClassKind.signatureKeyword] is equal to [keyword] or `null` if
         * none could be found.
         */
        fun bySignatureKeyword(keyword: String) = bySignatureKeyword[keyword]
    }
}
