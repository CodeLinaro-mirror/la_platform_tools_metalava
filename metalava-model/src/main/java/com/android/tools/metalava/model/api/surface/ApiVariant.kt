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

package com.android.tools.metalava.model.api.surface

/** An API variant of [type] for [surface] */
class ApiVariant(
    /** The [ApiSurface] of which this is a variant. */
    val surface: ApiSurface,

    /** The type of this variant. */
    val type: ApiVariantType,

    /**
     * The list of all [ApiVariant]s belonging to the owning [ApiSurfaces].
     *
     * This must add itself to it.
     */
    allVariants: MutableList<ApiVariant>,
) {
    /**
     * Bit mask for this, used within [ApiVariantSet].
     *
     * This must be unique across all [ApiVariant]s within `allVariants` so it computes the bit
     * based on the current size of `allVariants` and then adds itself to the list ensuring that the
     * next [ApiVariant] will use a different bit.
     */
    internal val bitMask: Int = 1 shl allVariants.size.also { allVariants.add(this) }

    override fun toString(): String {
        return "${surface.name}(${type.name})"
    }
}

/**
 * The base set of [ApiVariant]s.
 *
 * Provides common query only functionality for [ApiVariantSet] and [MutableApiVariantSet].
 */
sealed class BaseApiVariantSet(internal val apiSurfaces: ApiSurfaces) {
    internal abstract val bits: Int

    fun isEmpty() = bits == 0

    fun isNotEmpty() = bits != 0

    operator fun contains(variant: ApiVariant) = (bits and variant.bitMask) != 0

    /** True if this set contains any of the variants from [surface]. */
    fun containsAny(surface: ApiSurface) = containsAny(surface.variantSet)

    /** True if this set contains any of the variants from [variantSet]. */
    fun containsAny(variantSet: ApiVariantSet): Boolean {
        require(apiSurfaces === variantSet.apiSurfaces) {
            "Mismatch between ApiSurfaces, this set is for $apiSurfaces, other set is for ${variantSet.apiSurfaces}"
        }
        return (bits and variantSet.bits) != 0
    }

    /**
     * Get a [MutableApiVariantSet] from this.
     *
     * This will return the object on which it is called if that is already mutable, otherwise it
     * will create a separate mutable copy of this.
     */
    abstract fun toMutable(): MutableApiVariantSet

    /**
     * Get an immutable [ApiVariantSet] from this.
     *
     * This will return the object on which it is called if that is already immutable, otherwise it
     * will create a separate immutable copy of this.
     */
    abstract fun toImmutable(): ApiVariantSet

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseApiVariantSet) return false

        if (apiSurfaces != other.apiSurfaces) return false
        if (bits != other.bits) return false

        return true
    }

    override fun hashCode(): Int {
        var result = apiSurfaces.hashCode()
        result = 31 * result + bits
        return result
    }

    override fun toString(): String {
        return buildString {
            append("ApiVariantSet[")
            var separator = ""
            for (apiSurface in apiSurfaces.all) {
                // If this set does not contain any variants from the ApiSurface then ignore it.
                if (!this@BaseApiVariantSet.containsAny(apiSurface)) continue
                append(separator)
                separator = ","
                append(apiSurface.name)
                append("(")
                for (variant in apiSurface.variants) {
                    if (variant in this@BaseApiVariantSet) append(variant.type.shortCode)
                }
                append(")")
            }
            append("]")
        }
    }
}

/** An immutable set of [ApiVariant]s. */
class ApiVariantSet(apiSurfaces: ApiSurfaces, override val bits: Int) :
    BaseApiVariantSet(apiSurfaces) {

    override fun toMutable() = MutableApiVariantSet(apiSurfaces, bits)

    override fun toImmutable() = this

    companion object {
        internal fun emptySet(apiSurfaces: ApiSurfaces) = ApiVariantSet(apiSurfaces, 0)

        /**
         * Build an [ApiVariantSet].
         *
         * Creates a [MutableApiVariantSet], calls [lambda] to modify it and then calls
         * [MutableApiVariantSet.toImmutable] to return an immutable [ApiVariantSet].
         *
         * @param apiSurfaces the [ApiSurfaces] whose [ApiVariant]s it will contain.
         * @param lambda the lambda that will be passed a [MutableApiVariantSet] to modify.
         */
        fun build(apiSurfaces: ApiSurfaces, lambda: MutableApiVariantSet.() -> Unit) =
            MutableApiVariantSet(apiSurfaces).apply(lambda).toImmutable()
    }
}

/** A mutable set of [ApiVariant]s. */
class MutableApiVariantSet
internal constructor(apiSurfaces: ApiSurfaces, override var bits: Int = 0) :
    BaseApiVariantSet(apiSurfaces) {

    override fun toMutable() = this

    override fun toImmutable() =
        if (bits == 0) apiSurfaces.emptyVariantSet else ApiVariantSet(apiSurfaces, bits)

    /**
     * Add [variant] to this set.
     *
     * This has no effect if it is already a member.
     */
    fun add(variant: ApiVariant) {
        bits = bits or variant.bitMask
    }

    /**
     * Remove [variant] from this set.
     *
     * This has no effect if it was not a member.
     */
    fun remove(variant: ApiVariant) {
        bits = bits and variant.bitMask.inv()
    }

    /** Clear the set. */
    fun clear() {
        bits = 0
    }

    companion object {

        /** Create a [MutableApiVariantSet] for [apiSurfaces]. */
        fun setOf(apiSurfaces: ApiSurfaces): MutableApiVariantSet {
            // Make sure all the variant bits can fit into an Int.
            if (apiSurfaces.variants.count() > 30)
                error("Too many API variants to store in the set")
            return MutableApiVariantSet(apiSurfaces, 0)
        }
    }
}
