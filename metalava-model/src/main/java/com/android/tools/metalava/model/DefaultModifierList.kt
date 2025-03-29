/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.ModifierFlags.Companion.ABSTRACT
import com.android.tools.metalava.model.ModifierFlags.Companion.ACTUAL
import com.android.tools.metalava.model.ModifierFlags.Companion.COMPANION
import com.android.tools.metalava.model.ModifierFlags.Companion.CONST
import com.android.tools.metalava.model.ModifierFlags.Companion.DATA
import com.android.tools.metalava.model.ModifierFlags.Companion.DEFAULT
import com.android.tools.metalava.model.ModifierFlags.Companion.DEPRECATED
import com.android.tools.metalava.model.ModifierFlags.Companion.EQUIVALENCE_MASK
import com.android.tools.metalava.model.ModifierFlags.Companion.EXPECT
import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.FUN
import com.android.tools.metalava.model.ModifierFlags.Companion.INFIX
import com.android.tools.metalava.model.ModifierFlags.Companion.INLINE
import com.android.tools.metalava.model.ModifierFlags.Companion.NATIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.OPERATOR
import com.android.tools.metalava.model.ModifierFlags.Companion.PACKAGE_PRIVATE
import com.android.tools.metalava.model.ModifierFlags.Companion.PRIVATE
import com.android.tools.metalava.model.ModifierFlags.Companion.PROTECTED
import com.android.tools.metalava.model.ModifierFlags.Companion.SEALED
import com.android.tools.metalava.model.ModifierFlags.Companion.STATIC
import com.android.tools.metalava.model.ModifierFlags.Companion.STRICT_FP
import com.android.tools.metalava.model.ModifierFlags.Companion.SUSPEND
import com.android.tools.metalava.model.ModifierFlags.Companion.SYNCHRONIZED
import com.android.tools.metalava.model.ModifierFlags.Companion.TRANSIENT
import com.android.tools.metalava.model.ModifierFlags.Companion.VALUE
import com.android.tools.metalava.model.ModifierFlags.Companion.VARARG
import com.android.tools.metalava.model.ModifierFlags.Companion.VISIBILITY_LEVEL_ENUMS
import com.android.tools.metalava.model.ModifierFlags.Companion.VISIBILITY_MASK
import com.android.tools.metalava.model.ModifierFlags.Companion.VOLATILE
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Default [BaseModifierList]. */
internal abstract class DefaultBaseModifierList
constructor(
    protected var flags: Int,
    protected var annotations: List<AnnotationItem> = emptyList(),
) : BaseModifierList {

    protected operator fun set(mask: Int, set: Boolean) {
        flags =
            if (set) {
                flags or mask
            } else {
                flags and mask.inv()
            }
    }

    private fun isSet(mask: Int): Boolean {
        return flags and mask != 0
    }

    override fun annotations(): List<AnnotationItem> {
        return annotations
    }

    override fun getVisibilityLevel(): VisibilityLevel {
        val visibilityFlags = flags and VISIBILITY_MASK
        val levels = VISIBILITY_LEVEL_ENUMS
        if (visibilityFlags >= levels.size) {
            throw IllegalStateException(
                "Visibility flags are invalid, expected value in range [0, " +
                    levels.size +
                    ") got " +
                    visibilityFlags
            )
        }
        return levels[visibilityFlags]
    }

    override fun isPublic(): Boolean {
        return getVisibilityLevel() == VisibilityLevel.PUBLIC
    }

    override fun isProtected(): Boolean {
        return getVisibilityLevel() == VisibilityLevel.PROTECTED
    }

    override fun isPrivate(): Boolean {
        return getVisibilityLevel() == VisibilityLevel.PRIVATE
    }

    override fun isStatic(): Boolean {
        return isSet(STATIC)
    }

    override fun isAbstract(): Boolean {
        return isSet(ABSTRACT)
    }

    override fun isFinal(): Boolean {
        return isSet(FINAL)
    }

    override fun isNative(): Boolean {
        return isSet(NATIVE)
    }

    override fun isSynchronized(): Boolean {
        return isSet(SYNCHRONIZED)
    }

    override fun isStrictFp(): Boolean {
        return isSet(STRICT_FP)
    }

    override fun isTransient(): Boolean {
        return isSet(TRANSIENT)
    }

    override fun isVolatile(): Boolean {
        return isSet(VOLATILE)
    }

    override fun isDefault(): Boolean {
        return isSet(DEFAULT)
    }

    override fun isDeprecated(): Boolean {
        return isSet(DEPRECATED)
    }

    override fun isVarArg(): Boolean {
        return isSet(VARARG)
    }

    override fun isSealed(): Boolean {
        return isSet(SEALED)
    }

    override fun isFunctional(): Boolean {
        return isSet(FUN)
    }

    override fun isInfix(): Boolean {
        return isSet(INFIX)
    }

    override fun isConst(): Boolean {
        return isSet(CONST)
    }

    override fun isSuspend(): Boolean {
        return isSet(SUSPEND)
    }

    override fun isCompanion(): Boolean {
        return isSet(COMPANION)
    }

    override fun isOperator(): Boolean {
        return isSet(OPERATOR)
    }

    override fun isInline(): Boolean {
        return isSet(INLINE)
    }

    override fun isValue(): Boolean {
        return isSet(VALUE)
    }

    override fun isData(): Boolean {
        return isSet(DATA)
    }

    override fun isExpect(): Boolean {
        return isSet(EXPECT)
    }

    override fun isActual(): Boolean {
        return isSet(ACTUAL)
    }

    override fun isPackagePrivate(): Boolean {
        return flags and VISIBILITY_MASK == PACKAGE_PRIVATE
    }

    override fun equivalentTo(owner: Item?, other: BaseModifierList): Boolean {
        other as DefaultBaseModifierList

        val flags2 = other.flags
        val mask = EQUIVALENCE_MASK

        val masked1 = flags and mask
        val masked2 = flags2 and mask
        val same = masked1 xor masked2
        if (same == 0) {
            return true
        } else {
            if (
                same == FINAL &&
                    // Only differ in final: not significant if implied by containing class
                    isFinal() &&
                    (owner as? MethodItem)?.containingClass()?.modifiers?.isFinal() == true
            ) {
                return true
            } else if (
                same == DEPRECATED &&
                    // Only differ in deprecated: not significant if implied by containing class
                    isDeprecated() &&
                    (owner as? MethodItem)?.containingClass()?.effectivelyDeprecated == true
            ) {
                return true
            }
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DefaultBaseModifierList) return false

        if (flags != other.flags) return false
        if (annotations != other.annotations) return false

        return true
    }

    override fun hashCode(): Int {
        var result = flags
        result = 31 * result + annotations.hashCode()
        return result
    }

    override fun toString(): String {
        val binaryFlags = Integer.toBinaryString(flags)
        return "ModifierList(flags = 0b$binaryFlags, annotations = $annotations)"
    }
}

interface ModifierFlags {
    companion object {

        /**
         * 'PACKAGE_PRIVATE' is set to 0 to act as the default visibility when no other visibility
         * flags are explicitly set.
         */
        const val PACKAGE_PRIVATE = 0
        const val PRIVATE = 1
        const val INTERNAL = 2
        const val PROTECTED = 3
        const val PUBLIC = 4
        const val VISIBILITY_MASK = 0b111

        /**
         * An internal copy of VisibilityLevel.values() to avoid paying the cost of duplicating the
         * array on every call.
         */
        internal val VISIBILITY_LEVEL_ENUMS = VisibilityLevel.entries

        // Check that the constants above are consistent with the VisibilityLevel enum, i.e. the
        // mask is large enough
        // to include all allowable values and that each visibility level value is the same as the
        // corresponding enum
        // constant's ordinal.
        init {
            check(PACKAGE_PRIVATE == VisibilityLevel.PACKAGE_PRIVATE.ordinal)
            check(PRIVATE == VisibilityLevel.PRIVATE.ordinal)
            check(INTERNAL == VisibilityLevel.INTERNAL.ordinal)
            check(PROTECTED == VisibilityLevel.PROTECTED.ordinal)
            check(PUBLIC == VisibilityLevel.PUBLIC.ordinal)
            // Calculate the mask required to hold as many different values as there are
            // VisibilityLevel values.
            // Given N visibility levels, the required mask is constructed by determining the MSB in
            // the number N - 1
            // and then setting all bits to the right.
            // e.g. when N is 5 then N - 1 is 4, the MSB is bit 2, and so the mask is what you get
            // when you set bits 2,
            // 1 and 0, i.e. 0b111.
            val expectedMask =
                (1 shl (32 - Integer.numberOfLeadingZeros(VISIBILITY_LEVEL_ENUMS.size - 1))) - 1
            check(VISIBILITY_MASK == expectedMask)
        }

        const val STATIC = 1 shl 3
        const val ABSTRACT = 1 shl 4
        const val FINAL = 1 shl 5
        const val NATIVE = 1 shl 6
        const val SYNCHRONIZED = 1 shl 7
        const val STRICT_FP = 1 shl 8
        const val TRANSIENT = 1 shl 9
        const val VOLATILE = 1 shl 10
        const val DEFAULT = 1 shl 11
        const val DEPRECATED = 1 shl 12
        const val VARARG = 1 shl 13
        const val SEALED = 1 shl 14
        const val FUN = 1 shl 15
        const val INFIX = 1 shl 16
        const val OPERATOR = 1 shl 17
        const val INLINE = 1 shl 18
        const val SUSPEND = 1 shl 19
        const val COMPANION = 1 shl 20
        const val CONST = 1 shl 21
        const val DATA = 1 shl 22
        const val VALUE = 1 shl 23
        const val EXPECT = 1 shl 24
        const val ACTUAL = 1 shl 25

        /**
         * Modifiers considered significant to include signature files (and similarly to consider
         * whether an override of a method is different from its super implementation)
         */
        internal const val EQUIVALENCE_MASK =
            VISIBILITY_MASK or
                STATIC or
                ABSTRACT or
                FINAL or
                TRANSIENT or
                VOLATILE or
                DEPRECATED or
                VARARG or
                SEALED or
                FUN or
                INFIX or
                OPERATOR or
                SUSPEND or
                COMPANION
    }
}

/** Default [MutableModifierList]. */
internal class DefaultMutableModifierList(
    flags: Int,
    annotations: List<AnnotationItem> = emptyList(),
) : DefaultBaseModifierList(flags, annotations), MutableModifierList {

    override fun toMutable(): MutableModifierList {
        return this
    }

    override fun toImmutable(): ModifierList {
        return DefaultModifierList.create(flags, annotations)
    }

    override fun setVisibilityLevel(level: VisibilityLevel) {
        flags = (flags and VISIBILITY_MASK.inv()) or level.visibilityFlagValue
    }

    override fun setStatic(static: Boolean) {
        set(STATIC, static)
    }

    override fun setAbstract(abstract: Boolean) {
        set(ABSTRACT, abstract)
    }

    override fun setFinal(final: Boolean) {
        set(FINAL, final)
    }

    override fun setNative(native: Boolean) {
        set(NATIVE, native)
    }

    override fun setSynchronized(synchronized: Boolean) {
        set(SYNCHRONIZED, synchronized)
    }

    override fun setStrictFp(strictfp: Boolean) {
        set(STRICT_FP, strictfp)
    }

    override fun setTransient(transient: Boolean) {
        set(TRANSIENT, transient)
    }

    override fun setVolatile(volatile: Boolean) {
        set(VOLATILE, volatile)
    }

    override fun setDefault(default: Boolean) {
        set(DEFAULT, default)
    }

    override fun setSealed(sealed: Boolean) {
        set(SEALED, sealed)
    }

    override fun setFunctional(functional: Boolean) {
        set(FUN, functional)
    }

    override fun setInfix(infix: Boolean) {
        set(INFIX, infix)
    }

    override fun setOperator(operator: Boolean) {
        set(OPERATOR, operator)
    }

    override fun setInline(inline: Boolean) {
        set(INLINE, inline)
    }

    override fun setValue(value: Boolean) {
        set(VALUE, value)
    }

    override fun setData(data: Boolean) {
        set(DATA, data)
    }

    override fun setVarArg(vararg: Boolean) {
        set(VARARG, vararg)
    }

    override fun setDeprecated(deprecated: Boolean) {
        set(DEPRECATED, deprecated)
    }

    override fun setSuspend(suspend: Boolean) {
        set(SUSPEND, suspend)
    }

    override fun setCompanion(companion: Boolean) {
        set(COMPANION, companion)
    }

    override fun setExpect(expect: Boolean) {
        set(EXPECT, expect)
    }

    override fun setActual(actual: Boolean) {
        set(ACTUAL, actual)
    }

    override fun mutateAnnotations(mutator: MutableList<AnnotationItem>.() -> Unit) {
        val mutable = annotations.toMutableList()
        mutable.mutator()
        annotations = mutable.toList()
    }
}

/** Default [ModifierList]. */
internal class DefaultModifierList
private constructor(
    flags: Int,
    annotations: List<AnnotationItem>,
) : DefaultBaseModifierList(flags, annotations), ModifierList {

    override fun toMutable(): MutableModifierList {
        return DefaultMutableModifierList(flags, annotations)
    }

    override fun toImmutable(): ModifierList {
        return this
    }

    override fun snapshot(targetCodebase: Codebase): ModifierList {
        if (annotations.isEmpty()) return this

        val newAnnotations = annotations.map { it.snapshot(targetCodebase) }
        return create(flags, newAnnotations)
    }

    companion object {
        private var cache = mutableMapOf<Int, DefaultModifierList>()

        /** Not thread-safe. */
        fun create(
            flags: Int,
            annotations: List<AnnotationItem> = emptyList(),
        ): ModifierList {
            if (annotations.isEmpty()) {
                return cache.computeIfAbsent(flags) { DefaultModifierList(it, emptyList()) }
            }
            return DefaultModifierList(flags, annotations)
        }
    }
}

/**
 * Add a [Retention] annotation with the default [RetentionPolicy] suitable for [codebase].
 *
 * The caller must ensure that the annotation does not already have a [Retention] annotation before
 * calling this.
 */
fun MutableModifierList.addDefaultRetentionPolicyAnnotation(
    codebase: Codebase,
    isKotlin: Boolean,
) {
    // By policy, include explicit retention policy annotation if missing
    val defaultRetentionPolicy = AnnotationRetention.getDefault(isKotlin)
    addAnnotation(
        codebase.createAnnotation(
            buildString {
                append('@')
                append(Retention::class.qualifiedName)
                append('(')
                append(RetentionPolicy::class.qualifiedName)
                append('.')
                append(defaultRetentionPolicy.name)
                append(')')
            },
        )
    )
}

/**
 * Create an immutable [ModifierList] with the [visibility] level and an optional list of
 * [AnnotationItem]s.
 */
fun createImmutableModifiers(
    visibility: VisibilityLevel,
    annotations: List<AnnotationItem> = emptyList(),
): ModifierList {
    return DefaultModifierList.create(visibility.visibilityFlagValue, annotations)
}

/**
 * Create a [MutableModifierList] with the [visibility] level and an optional list of
 * [AnnotationItem]s.
 */
fun createMutableModifiers(
    visibility: VisibilityLevel,
    annotations: List<AnnotationItem> = emptyList(),
): MutableModifierList {
    return DefaultMutableModifierList(visibility.visibilityFlagValue, annotations)
}

/**
 * Modifies the modifier flags based on the `VisibleForTesting` annotation's `otherwise` value.
 *
 * @param otherwiseValue the value of the `otherwise` attribute, or `""` if no attribute is
 *   provided.
 */
private fun useVisibilityFromVisibleForTesting(otherwiseValue: String, flags: Int): Int {
    /** Check to see if this matches [visibility] or numeric [value]. */
    fun String.matchesVisibility(visibility: String, value: Int) =
        endsWith(visibility) || equals(value.toString())

    val visibilityFlags =
        when {
            otherwiseValue.matchesVisibility("PROTECTED", VisibleForTesting.PROTECTED) -> {
                PROTECTED
            }
            otherwiseValue.matchesVisibility(
                "PACKAGE_PRIVATE",
                VisibleForTesting.PACKAGE_PRIVATE
            ) -> {
                PACKAGE_PRIVATE
            }
            otherwiseValue.matchesVisibility("PRIVATE", VisibleForTesting.PRIVATE) ||
                otherwiseValue.matchesVisibility("NONE", VisibleForTesting.NONE) -> {
                PRIVATE
            }
            else -> {
                // Return the flags without changes.
                return flags
            }
        }

    return (flags and VISIBILITY_MASK.inv()) or visibilityFlags
}

/**
 * Create a [MutableModifierList] from a set of [flags] and an optional list of [AnnotationItem]s.
 */
fun createMutableModifiers(
    flags: Int,
    annotations: List<AnnotationItem> = emptyList(),
): MutableModifierList {
    val actualFlags =
        annotations
            .find { it.qualifiedName == ANDROIDX_VISIBLE_FOR_TESTING }
            ?.let { visibleForTesting ->
                visibleForTesting.findAttribute(ATTR_OTHERWISE)?.legacyValue?.let { otherwiseValue
                    ->
                    useVisibilityFromVisibleForTesting(otherwiseValue.toSource(), flags)
                }
            }
            ?: flags

    return DefaultMutableModifierList(actualFlags, annotations)
}

private const val ANDROIDX_VISIBLE_FOR_TESTING = "androidx.annotation.VisibleForTesting"
private const val ATTR_OTHERWISE = "otherwise"

/** Defines the numeric values of the symbols used in tests that use numbers instead of symbols. */
// TODO(b/387992791): Use a real VisibleForTesting annotation.
interface VisibleForTesting {
    companion object {
        const val PRIVATE = 2
        const val PACKAGE_PRIVATE = 3
        const val PROTECTED = 4
        const val NONE = 5
    }
}
