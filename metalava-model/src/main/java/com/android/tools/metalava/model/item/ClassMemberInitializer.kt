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
import com.android.tools.metalava.model.SkeletonClassItem

/**
 * Bitmask of class member kinds that can be lazily initialized by a [ClassMemberInitializer].
 *
 * @property bits the underlying bitmask of member kinds.
 */
@JvmInline
value class ClassMemberMask(val bits: Int) {
    /** Whether this mask contains no member kinds. */
    val isEmpty: Boolean
        get() = bits == 0

    /** Whether all member kinds in [other] are present in this mask. */
    operator fun contains(other: ClassMemberMask): Boolean =
        (bits and other.bits) == other.bits && other.bits != 0

    /** Union of this mask and [other]. */
    infix fun or(other: ClassMemberMask): ClassMemberMask = ClassMemberMask(bits or other.bits)

    /** Intersection of this mask and [other]. */
    infix fun and(other: ClassMemberMask): ClassMemberMask = ClassMemberMask(bits and other.bits)

    /** Difference of this mask and [other] (removes any member kinds in [other]). */
    operator fun minus(other: ClassMemberMask): ClassMemberMask =
        ClassMemberMask(bits and other.bits.inv())

    companion object {
        /** Mask representing no class member kinds. */
        val NONE = ClassMemberMask(0)

        /** Mask representing [ClassItem.constructors]. */
        val CONSTRUCTORS = ClassMemberMask(1 shl 0)

        /** Mask representing [ClassItem.methods]. */
        val METHODS = ClassMemberMask(1 shl 1)

        /** Mask representing [ClassItem.fields]. */
        val FIELDS = ClassMemberMask(1 shl 2)

        /** Mask representing [ClassItem.properties]. */
        val PROPERTIES = ClassMemberMask(1 shl 3)

        /** Mask representing all class member kinds. */
        val ALL = CONSTRUCTORS or METHODS or FIELDS or PROPERTIES
    }
}

/**
 * Lazily populates members of a [SkeletonClassItem] on demand.
 *
 * Tracks which member kinds remain uninitialized and is discarded by [DefaultClassItem] as soon as
 * all member kinds have been initialized.
 *
 * @param uninitializedMembers the initial mask of member kinds that still need to be initialized.
 */
abstract class ClassMemberInitializer(
    uninitializedMembers: ClassMemberMask,
) {
    /** The mask of member kinds that still need to be initialized. */
    var uninitializedMembers: ClassMemberMask = uninitializedMembers
        private set

    /**
     * Ensure that all member kinds in [requested] have been initialized on [classItem].
     *
     * @return `true` if all member kinds managed by this initializer have now been initialized and
     *   this initializer can be discarded by [classItem].
     */
    fun ensureInitialized(
        classItem: SkeletonClassItem,
        requested: ClassMemberMask,
    ): Boolean {
        val toInitialize = requested and uninitializedMembers
        if (!toInitialize.isEmpty) {
            // Clear `toInitialize` BEFORE calling `initializeMembers` so that:
            // 1. Re-entrant calls to members within `toInitialize` (e.g. `addMethod` or
            //    `constructors()`) see those bits as already cleared and do not recurse.
            // 2. Calls to other member kinds outside `toInitialize` (e.g. `PROPERTIES`
            //    initialization calling `methods()`, `fields()`, or `constructors()`) still see
            //    those bits in `uninitializedMembers` and initialize them on demand.
            uninitializedMembers -= toInitialize

            initializeMembers(classItem, toInitialize)
        }
        return uninitializedMembers.isEmpty
    }

    /** Populate the member kinds present in [mask] on [classItem]. */
    protected abstract fun initializeMembers(
        classItem: SkeletonClassItem,
        mask: ClassMemberMask,
    )
}
