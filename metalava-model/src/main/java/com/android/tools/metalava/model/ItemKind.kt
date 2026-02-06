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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.ModifierFlags.Companion.ABSTRACT
import com.android.tools.metalava.model.ModifierFlags.Companion.DEFAULT
import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.NATIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.STATIC
import com.android.tools.metalava.model.ModifierFlags.Companion.SYNCHRONIZED
import com.android.tools.metalava.model.ModifierFlags.Companion.TRANSIENT
import com.android.tools.metalava.model.ModifierFlags.Companion.VARARG
import com.android.tools.metalava.model.ModifierFlags.Companion.VISIBILITY_MASK
import com.android.tools.metalava.model.ModifierFlags.Companion.VOLATILE

/**
 * The kinds of all [Item]s.
 *
 * Provides a way to associate information with different kinds of [Item] without polluting the
 * [Item] interface.
 */
enum class ItemKind(
    /** The set of java flags that are allowed on each [ItemKind]. */
    javaFlags: Int,
) {
    CLASS(
        javaFlags =
            flagBits(
                ABSTRACT,
                FINAL,
                STATIC,
            ),
    ),
    CONSTRUCTOR(
        javaFlags = flagBits(),
    ),
    FIELD(
        javaFlags =
            flagBits(
                FINAL,
                STATIC,
                TRANSIENT,
                VOLATILE,
            ),
    ),
    METHOD(
        javaFlags =
            flagBits(
                ABSTRACT,
                DEFAULT,
                FINAL,
                NATIVE,
                STATIC,
                SYNCHRONIZED,
            ),
    ),
    PACKAGE(
        javaFlags = flagBits(),
    ),
    PARAMETER(
        javaFlags =
            flagBits(
                VARARG,
            ),
    ),
    PROPERTY(
        javaFlags = flagBits(),
    ),
    TYPE_PARAMETER(
        javaFlags = flagBits(),
    ),
    ;

    /**
     * When given a set of [ModifierFlags] will remove any that do not apply to this [ItemKind].
     *
     * This is needed because the Java Specification uses the same bit value to have different
     * meaning depending on the associated item kind. e.g. `ACC_TRANSIENT` and `ACC_VARARGS` have
     * the same bit value but the former only applies to fields and the latter to methods. Each flag
     * in [ModifierFlags] has a unique value so is not dependent on the item kind.
     *
     * However, mapping from the former to the latter does and this provides a simple way to ignore
     * [ModifierFlags] that do not apply.
     */
    fun normalizeJavaFlags(flags: Int) = flags and javaModifierMask

    /** The set of [ModifierFlags] that are allowed on this [ItemKind]. */
    private val javaModifierMask: Int = VISIBILITY_MASK or javaFlags
}

/**
 * Compute a bit mask consisting of all [bits] ORed together, or `0` if [bits] is empty.
 *
 * Used instead of just using `b1 or b2 or b3 ...` as it provides more consistent formatting.
 */
private fun flagBits(vararg bits: Int) =
    if (bits.isEmpty()) 0 else bits.reduce { b1, b2 -> b1 or b2 }
