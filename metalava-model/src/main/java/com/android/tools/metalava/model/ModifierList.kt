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

package com.android.tools.metalava.model

interface BaseModifierList {
    fun annotations(): List<AnnotationItem>

    fun getVisibilityLevel(): VisibilityLevel

    fun isPublic(): Boolean

    fun isProtected(): Boolean

    fun isPrivate(): Boolean

    @MetalavaApi fun isStatic(): Boolean

    fun isAbstract(): Boolean

    fun isFinal(): Boolean

    fun isNative(): Boolean

    fun isSynchronized(): Boolean

    fun isStrictFp(): Boolean

    fun isTransient(): Boolean

    fun isVolatile(): Boolean

    fun isDefault(): Boolean

    fun isDeprecated(): Boolean

    // Modifier in Kotlin, separate syntax (...) in Java but modeled as modifier here
    fun isVarArg(): Boolean = false

    // Kotlin
    fun isSealed(): Boolean = false

    fun isFunctional(): Boolean = false

    fun isCompanion(): Boolean = false

    fun isInfix(): Boolean = false

    fun isConst(): Boolean = false

    fun isSuspend(): Boolean = false

    fun isOperator(): Boolean = false

    fun isInline(): Boolean = false

    fun isValue(): Boolean = false

    fun isData(): Boolean = false

    fun isExpect(): Boolean = false

    fun isActual(): Boolean = false

    fun isPackagePrivate() = !(isPublic() || isProtected() || isPrivate())

    fun isPublicOrProtected() = isPublic() || isProtected()

    /**
     * Check whether this [ModifierList]'s modifiers are equivalent to the [other] [ModifierList]'s
     * modifiers.
     *
     * Modifier meaning does depend on the [Item] to which they belong, e.g. just because `final`
     * and `deprecated` are `false` does not mean that the [Item] is not final or deprecated as the
     * containing class may be final or deprecated.
     *
     * It is used for:
     * * Checking method overrides.
     * * Checking consistent of classes whose definition is split across multiple signature files.
     * * Testing the [InheritableItem.duplicate] works correctly.
     *
     * @param owner the optional [Item] that owns this [ModifierList] and which is used to tweak the
     *   check to make it take into account the content within which they are being used. If it is
     *   not provided then this will just compare modifiers like for like.
     *
     * TODO(b/356548977): Currently, [owner] only has an effect if it is a [MethodItem]. That is due
     *   to it historically only being used for method overrides. However, as the previous list
     *   shows that is no longer true so it will need to be updated to correctly handle the other
     *   cases.
     */
    fun equivalentTo(owner: Item?, other: BaseModifierList): Boolean

    /** Returns true if this modifier list contains the `@JvmSynthetic` annotation */
    fun hasJvmSyntheticAnnotation(): Boolean = hasAnnotation(AnnotationItem::isJvmSynthetic)

    /** Returns true if this modifier list contains the given annotation */
    fun isAnnotatedWith(qualifiedName: String): Boolean {
        return findAnnotation(qualifiedName) != null
    }

    /**
     * Returns the annotation of the given qualified name (or equivalent) if found in this modifier
     * list
     */
    fun findAnnotation(qualifiedName: String): AnnotationItem? {
        return findAnnotation { qualifiedName == it.qualifiedName }
    }

    /**
     * Returns true if the visibility modifiers in this modifier list is as least as visible as the
     * ones in the given [other] modifier list
     */
    fun asAccessibleAs(other: BaseModifierList): Boolean {
        val otherLevel = other.getVisibilityLevel()
        val thisLevel = getVisibilityLevel()
        // Generally the access level enum order determines relative visibility. However, there is
        // an exception because
        // package private and internal are not directly comparable.
        val result = thisLevel >= otherLevel
        return when (otherLevel) {
            VisibilityLevel.PACKAGE_PRIVATE -> result && thisLevel != VisibilityLevel.INTERNAL
            VisibilityLevel.INTERNAL -> result && thisLevel != VisibilityLevel.PACKAGE_PRIVATE
            else -> result
        }
    }

    /** User visible description of the visibility in this modifier list */
    fun getVisibilityString(): String {
        return getVisibilityLevel().userVisibleDescription
    }

    /**
     * Like [getVisibilityString], but package private has no modifiers; this typically corresponds
     * to the source code for the visibility modifiers in the modifier list
     */
    fun getVisibilityModifiers(): String {
        return getVisibilityLevel().javaSourceCodeModifier
    }

    /**
     * Copy this, so it can be used on (and possibly modified by) another [Item] from the same
     * codebase.
     */
    fun duplicate(): ModifierList

    /**
     * Take a snapshot of this for use in [targetCodebase].
     *
     * While [duplicate] makes a shallow copy for use within the same [Codebase] this method creates
     * a deep snapshot, including snapshots of each annotation for use in [targetCodebase].
     *
     * @param targetCodebase The [Codebase] of which the snapshot will be part.
     */
    fun snapshot(targetCodebase: Codebase): ModifierList
}

/**
 * Returns the first annotation in the modifier list that matches the supplied predicate, or null
 * otherwise.
 */
inline fun BaseModifierList.findAnnotation(
    predicate: (AnnotationItem) -> Boolean
): AnnotationItem? {
    return annotations().firstOrNull(predicate)
}

/**
 * Returns true iff the modifier list contains any annotation that matches the supplied predicate.
 */
inline fun BaseModifierList.hasAnnotation(predicate: (AnnotationItem) -> Boolean): Boolean {
    return annotations().any(predicate)
}

interface ModifierList : BaseModifierList
