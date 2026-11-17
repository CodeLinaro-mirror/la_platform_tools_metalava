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
import com.android.tools.metalava.model.ModifierFlags.Companion.NON_SEALED
import com.android.tools.metalava.model.ModifierFlags.Companion.SEALED
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
    /** The set of [ModifierFlags] that are allowed on this [ItemKind]. */
    internal val javaModifierMask: Int,
) {
    CLASS(
        javaModifierMask =
            flagBits(
                ABSTRACT,
                FINAL,
                NON_SEALED,
                SEALED,
                STATIC,
            ),
    ),
    CONSTRUCTOR(
        javaModifierMask = flagBits(),
    ),
    FIELD(
        javaModifierMask =
            flagBits(
                FINAL,
                STATIC,
                TRANSIENT,
                VOLATILE,
            ),
    ),
    METHOD(
        javaModifierMask =
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
        javaModifierMask = flagBits(),
    ),
    PARAMETER(
        javaModifierMask =
            flagBits(
                VARARG,
            ),
    ),
    PROPERTY(
        javaModifierMask = flagBits(),
    ),
    RECORD_COMPONENT(
        javaModifierMask = flagBits(),
    ),
    TYPE_PARAMETER(
        javaModifierMask = flagBits(),
    ),
}

/**
 * Compute a bit mask consisting of all [bits] ORed together, or `0` if [bits] is empty, ORed with
 * [VISIBILITY_MASK].
 *
 * Used instead of just using `b1 or b2 or b3 ...` as it provides more consistent formatting.
 */
internal fun flagBits(vararg bits: Int) =
    if (bits.isEmpty()) VISIBILITY_MASK else bits.reduce { b1, b2 -> b1 or b2 } or VISIBILITY_MASK
