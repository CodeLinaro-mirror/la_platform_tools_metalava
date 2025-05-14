/*
 * Copyright (C) 2025 The Android Open Source Project
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

import java.util.EnumSet

/**
 * The [TargetLanguage]s for an [Item] represent which languages a client can use the [Item] from.
 *
 * This is distinct from the [SourceLanguage] of an [Item]. An API defined in one language may be
 * usable from all [TargetLanguage]s or only from a subset.
 *
 * Note that an API from source might be represented as multiple [Item]s with different signatures
 * that have different [TargetLanguage]s (for example, an API defined in Kotlin with [JvmName] will
 * be represented with one item using the original name from source, which can be referenced from
 * Kotlin, and one item using the name from the annotation, which can be referenced from Java and
 * from bytecode).
 */
enum class TargetLanguage {
    /** [Item]s with [KOTLIN] as a [TargetLanguage] can be referenced from Kotlin source code. */
    KOTLIN,
    /** [Item]s with [JAVA] as a [TargetLanguage] can be referenced from Java source code. */
    JAVA,
    /** [Item]s with [BYTECODE] as a [TargetLanguage] can be referenced from compiled bytecode. */
    BYTECODE
}

/** Standard sets of [TargetLanguage]s. */
object TargetLanguageSet {
    /**
     * [TargetLanguage] set for an API that can be referenced from both Java and Kotlin source and
     * from bytecode.
     */
    val ALL: Set<TargetLanguage> =
        EnumSet.of(TargetLanguage.BYTECODE, TargetLanguage.JAVA, TargetLanguage.KOTLIN)

    /**
     * [TargetLanguage] set for an API that can only be referenced from Kotlin source.
     *
     * Examples include:
     * - The version of a Kotlin API annotated with [JvmName] that uses the original source name
     *   rather than the name from the annotation.
     */
    val KOTLIN_ONLY: Set<TargetLanguage> = EnumSet.of(TargetLanguage.KOTLIN)

    /**
     * [TargetLanguage] set for an API that can only be referenced from bytecode, not from source
     * code in Java or Kotlin.
     *
     * Examples include:
     * - Deprecated Kotlin APIs with [DeprecationLevel.HIDDEN]. These APIs cannot be referenced from
     *   source code, but exist in the bytecode to provide compatibility with clients compiled
     *   against a previous version before the API was deprecated.
     * - The mangled form of a Kotlin API function a value class type (see
     *   https://kotlinlang.org/docs/inline-classes.html#mangling). These functions are referenced
     *   from Kotlin code using the non-mangled name and value class type (represented with a
     *   separate [MethodItem] with that signature), but when compiled the referenced use this
     *   mangled form.
     */
    val BYTECODE_ONLY: Set<TargetLanguage> = EnumSet.of(TargetLanguage.BYTECODE)

    /**
     * [TargetLanguage] set for an API that cannot be referenced from Kotlin source, but can be
     * referenced from Java source and from bytecode.
     *
     * Examples include:
     * - The renamed version of a Kotlin API annotated with [JvmName]
     */
    val NOT_KOTLIN: Set<TargetLanguage> = EnumSet.of(TargetLanguage.BYTECODE, TargetLanguage.JAVA)

    /**
     * [TargetLanguage] set for an API that cannot be referenced from Java source, but can be
     * referenced from Kotlin source and from bytecode.
     *
     * Examples include:
     * - An API annotated with [JvmSynthetic]
     */
    val NOT_JAVA: Set<TargetLanguage> = EnumSet.of(TargetLanguage.BYTECODE, TargetLanguage.KOTLIN)

    /**
     * [TargetLanguage] set with all targets except [TargetLanguage.BYTECODE]. This might be used by
     * a visitor which wants to skip APIs that only exist in bytecode.
     */
    val SOURCE: Set<TargetLanguage> = EnumSet.of(TargetLanguage.JAVA, TargetLanguage.KOTLIN)
}
