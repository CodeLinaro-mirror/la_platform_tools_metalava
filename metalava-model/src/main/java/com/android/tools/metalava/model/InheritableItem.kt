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

/** A [MemberItem] that can be inherited from one class to another. */
interface InheritableItem : MemberItem {

    /** True if this member was inherited from an ancestor class or interface. */
    val inheritedFromAncestor
        get() = inheritedFrom != null

    /**
     * If this member is inherited from a super class (typically via [duplicate]) this field points
     * to the original class it was inherited from
     */
    val inheritedFrom: ClassItem?

    /**
     * Duplicates this member item.
     *
     * This is only used when comparing two [Codebase]s, in which case it is called to inherit a
     * member from a super class/interface when it exists in the other [Codebase]. The resulting
     * [InheritableItem] is expected to behave as if it was part of the [targetContainingClass] but
     * is otherwise identical to `this`, e.g. if [targetContainingClass] is [hidden] then so should
     * the returned [InheritableItem].
     *
     * The [inheritedFrom] property in the returned [InheritableItem] is set to [containingClass] of
     * this [InheritableItem].
     *
     * @param targetContainingClass the [ClassItem] that will be used as
     *   [InheritableItem.containingClass]. Note, this may be from a different [Codebase]
     *   implementation than the [InheritableItem] so implementations must be careful to avoid an
     *   unconditional downcast.
     */
    fun duplicate(targetContainingClass: ClassItem): InheritableItem
}
