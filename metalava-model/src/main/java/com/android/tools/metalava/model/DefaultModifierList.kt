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
import com.android.tools.metalava.model.ModifierFlags.Companion.EXHAUSTIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.EXPECT
import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.FUN
import com.android.tools.metalava.model.ModifierFlags.Companion.INFIX
import com.android.tools.metalava.model.ModifierFlags.Companion.INLINE
import com.android.tools.metalava.model.ModifierFlags.Companion.INTERNAL
import com.android.tools.metalava.model.ModifierFlags.Companion.NATIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.NON_SEALED
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
import com.android.tools.metalava.model.ModifierFlags.Companion.VISIBILITY_LEVEL_ENUMS
import com.android.tools.metalava.model.ModifierFlags.Companion.VISIBILITY_MASK
import com.android.tools.metalava.model.ModifierFlags.Companion.VOLATILE
import com.android.tools.metalava.model.value.Value
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Default [BaseModifierList]. */
internal sealed class DefaultBaseModifierList(
    protected var flags: Int,
    protected var annotations: List<AnnotationItem> = emptyList(),
) : BaseModifierList {

    override val keywordList = ModifierKeyword.entries.filter { it.isSetIn(flags) }

    protected operator fun set(mask: Int, set: Boolean) {
        flags =
            if (set) {
                flags or mask
            } else {
                flags and mask.inv()
            }
    }

    private fun isSet(mask: Int) = flags and mask != 0

    override fun annotations() = annotations

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

    override fun isPublic() = getVisibilityLevel() == VisibilityLevel.PUBLIC

    override fun isProtected() = getVisibilityLevel() == VisibilityLevel.PROTECTED

    override fun isInternal() = getVisibilityLevel() == VisibilityLevel.INTERNAL

    override fun isPublishedApi(): Boolean {
        return isInternal() && hasPublishedApiAnnotation()
    }

    /**
     * Check if the [BaseModifierList] is accessible as part of an API.
     *
     * If this has [VisibilityLevel.INTERNAL] then it is only accessible if it is annotated with the
     * [PublishedApi] annotation.
     */
    override fun hasApiVisibility(): Boolean {
        return when (getVisibilityLevel()) {
            VisibilityLevel.PUBLIC,
            VisibilityLevel.PROTECTED -> true
            VisibilityLevel.INTERNAL -> hasPublishedApiAnnotation()
            else -> false
        }
    }

    private fun hasPublishedApiAnnotation(): Boolean {
        return annotations().any { it.qualifiedName == KOTLIN_PUBLISHED_API }
    }

    override fun isPrivate() = getVisibilityLevel() == VisibilityLevel.PRIVATE

    override fun isPackagePrivate() = getVisibilityLevel() == VisibilityLevel.PACKAGE_PRIVATE

    override fun isStatic() = isSet(STATIC)

    override fun isAbstract() = isSet(ABSTRACT)

    override fun isFinal() = isSet(FINAL)

    override fun isNative() = isSet(NATIVE)

    override fun isSynchronized() = isSet(SYNCHRONIZED)

    override fun isStrictFp() = isSet(STRICT_FP)

    override fun isTransient() = isSet(TRANSIENT)

    override fun isVolatile() = isSet(VOLATILE)

    override fun isDefault() = isSet(DEFAULT)

    override fun isDeprecated() = isSet(DEPRECATED)

    override fun isVarArg() = isSet(VARARG)

    override fun isSealed() = isSet(SEALED)

    override fun isNonSealed() = isSet(NON_SEALED)

    override fun isExhaustive() = isSet(EXHAUSTIVE)

    override fun isFunctional() = isSet(FUN)

    override fun isInfix() = isSet(INFIX)

    override fun isConst() = isSet(CONST)

    override fun isSuspend() = isSet(SUSPEND)

    override fun isCompanion() = isSet(COMPANION)

    override fun isOperator() = isSet(OPERATOR)

    override fun isInline() = isSet(INLINE)

    override fun isValue() = isSet(VALUE)

    override fun isData() = isSet(DATA)

    override fun isExpect() = isSet(EXPECT)

    override fun isActual(): Boolean {
        return isSet(ACTUAL)
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

    override fun mayBeSubtypeOfJavaSealedType() = flags and ModifierFlags.SEALED_SUBTYPE_MASK != 0

    /**
     * Returns the flags for the modifiers from this list which are considered significant, as
     * defined by [EQUIVALENCE_MASK].
     */
    internal fun significantFlags(): Int = flags and EQUIVALENCE_MASK

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

    override fun toString() = "ModifierList(flags = $keywordList, annotations = $annotations)"
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
        const val EXHAUSTIVE = 1 shl 26
        const val NON_SEALED = 1 shl 27

        // Add new flags before this line and make sure to add a corresponding enum to
        // [ModifierKeyword].

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
                COMPANION or
                INLINE

        /**
         * The set of flags that if set indicate that a class could be a subtype of a `sealed`
         * class.
         */
        internal const val SEALED_SUBTYPE_MASK = FINAL or SEALED or NON_SEALED
    }
}

/** An enumeration of all the modifier keywords. */
enum class ModifierKeyword(
    /** The bits that represent this keyword in [DefaultBaseModifierList.flags]. */
    private val bitSet: Int,
    /**
     * The mask that selects the bits of [DefaultBaseModifierList.flags] to compare [bitSet]
     * against.
     */
    private val mask: Int,
) {
    // Visibility accessors.
    PACKAGE_PRIVATE_KEYWORD(PACKAGE_PRIVATE, VISIBILITY_MASK),
    PRIVATE_KEYWORD(PRIVATE, VISIBILITY_MASK),
    INTERNAL_KEYWORD(INTERNAL, VISIBILITY_MASK),
    PROTECTED_KEYWORD(PROTECTED, VISIBILITY_MASK),
    PUBLIC_KEYWORD(PUBLIC, VISIBILITY_MASK),

    // Other flags.
    STATIC_KEYWORD(STATIC),
    ABSTRACT_KEYWORD(ABSTRACT),
    FINAL_KEYWORD(FINAL),
    NATIVE_KEYWORD(NATIVE),
    SYNCHRONIZED_KEYWORD(SYNCHRONIZED),
    STRICT_FP_KEYWORD(STRICT_FP),
    TRANSIENT_KEYWORD(TRANSIENT),
    VOLATILE_KEYWORD(VOLATILE),
    DEFAULT_KEYWORD(DEFAULT),
    DEPRECATED_KEYWORD(DEPRECATED),
    VARARG_KEYWORD(VARARG),
    SEALED_KEYWORD(SEALED),
    FUN_KEYWORD(FUN),
    INFIX_KEYWORD(INFIX),
    OPERATOR_KEYWORD(OPERATOR),
    INLINE_KEYWORD(INLINE),
    SUSPEND_KEYWORD(SUSPEND),
    COMPANION_KEYWORD(COMPANION),
    CONST_KEYWORD(CONST),
    DATA_KEYWORD(DATA),
    VALUE_KEYWORD(VALUE),
    EXPECT_KEYWORD(EXPECT),
    ACTUAL_KEYWORD(ACTUAL),
    EXHAUSTIVE_KEYWORD(EXHAUSTIVE),
    ;

    /** Special constructor for non-visibility related flags. */
    constructor(bit: Int) : this(bit, bit)

    /** The string representation of this keyword, used in [toString]. */
    private val keyword = name.removeSuffix("_KEYWORD").lowercase()

    /** Is this keyword set in [flags]. */
    fun isSetIn(flags: Int) = (flags and mask == bitSet)

    override fun toString() = keyword
}

/** Default [MutableModifierList]. */
internal class DefaultMutableModifierList(
    flags: Int,
    annotations: List<AnnotationItem> = emptyList(),
) : DefaultBaseModifierList(flags, annotations), MutableModifierList {

    override fun toMutable(): MutableModifierList = this

    override fun toImmutable() = DefaultModifierList.create(flags, annotations)

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

    override fun setNonSealed(nonSealed: Boolean) {
        set(NON_SEALED, nonSealed)
    }

    override fun setExhaustive(exhaustive: Boolean) {
        set(EXHAUSTIVE, exhaustive)
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

    override fun setConst(const: Boolean) {
        set(CONST, const)
    }

    override fun mutateAnnotations(mutator: MutableList<AnnotationItem>.() -> Unit) {
        val mutable = annotations.toMutableList()
        mutable.mutator()
        annotations = mutable.toList()
    }

    override fun makeEquivalentTo(other: ModifierList) {
        other as DefaultBaseModifierList
        // For any flags in the equivalence mask, the new value should be the value from other.
        val significantFlagsFromOther = other.significantFlags()
        // For any flags not in the equivalence mask, the new value should be the same.
        val insignificantFlagsFromThis = flags and EQUIVALENCE_MASK.inv()
        // Combine the significant flags from other and the insignificant flags from this.
        flags = significantFlagsFromOther or insignificantFlagsFromThis
    }
}

/** Default [ModifierList]. */
internal class DefaultModifierList
private constructor(
    flags: Int,
    annotations: List<AnnotationItem>,
) : DefaultBaseModifierList(flags, annotations), ModifierList {

    override fun toMutable(): MutableModifierList = DefaultMutableModifierList(flags, annotations)

    override fun toImmutable(): ModifierList = this

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
    // Create a reference to the default retention policy enum value.
    val policyValue =
        Value.createFieldReferenceValue(
            codebase,
            RetentionPolicy::class.qualifiedName!!,
            defaultRetentionPolicy.name
        )
    // Create a retention annotation.
    val retentionAnnotation =
        AnnotationItem.createSingleElementAnnotation(
            codebase,
            Retention::class.qualifiedName!!,
            policyValue,
        )
    // Add the retention annotation.
    addAnnotation(retentionAnnotation)
}

/**
 * Create an immutable [ModifierList] with the [visibility] level and an optional list of
 * [AnnotationItem]s.
 */
fun createImmutableModifiers(
    visibility: VisibilityLevel,
    annotations: List<AnnotationItem> = emptyList(),
): ModifierList = DefaultModifierList.create(visibility.visibilityFlagValue, annotations)

/**
 * Create a [MutableModifierList] with the [visibility] level and an optional list of
 * [AnnotationItem]s.
 */
fun createMutableModifiers(
    visibility: VisibilityLevel,
    annotations: List<AnnotationItem> = emptyList(),
): MutableModifierList = DefaultMutableModifierList(visibility.visibilityFlagValue, annotations)

/**
 * Create a [MutableModifierList] from a set of [flags] and an optional list of [AnnotationItem]s.
 */
fun createMutableModifiers(
    flags: Int,
    annotations: List<AnnotationItem> = emptyList(),
): MutableModifierList = DefaultMutableModifierList(flags, annotations)
