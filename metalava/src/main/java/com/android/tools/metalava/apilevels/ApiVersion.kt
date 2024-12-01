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

package com.android.tools.metalava.apilevels

/** Version of an SDK, e.g. Android or AndroidX. */
@JvmInline
value class SdkVersion internal constructor(val level: Int) : Comparable<SdkVersion> {
    /** Make sure that this is a valid version. */
    val isValid
        get() = level > 0

    override fun toString() = level.toString()

    override operator fun compareTo(other: SdkVersion) = level.compareTo(other.level)

    operator fun plus(increment: Int) = fromLevel(level + increment)

    companion object {
        /** Get the [SdkVersion] for [level], which must be greater than 0. */
        fun fromLevel(level: Int) =
            if (level > 0) SdkVersion(level)
            else error("level must be greater than 0 but was $level")

        /**
         * The lowest [SdkVersion], used as the default value when higher versions override lower
         * ones.
         */
        val LOWEST = SdkVersion(0)

        /**
         * The highest [SdkVersion], used as the default value when lower versions override higher
         * ones.
         */
        val HIGHEST = SdkVersion(Int.MAX_VALUE)
    }
}

/** Version of an SDK extension. */
@JvmInline
value class ExtVersion internal constructor(val level: Int) : Comparable<ExtVersion> {
    /** Make sure that this is a valid version. */
    val isValid
        get() = level > 0

    override fun toString() = level.toString()

    override operator fun compareTo(other: ExtVersion) = level.compareTo(other.level)

    companion object {
        /** Get the [ExtVersion] for [level], which must be greater than 0. */
        fun fromLevel(level: Int) =
            if (level > 0) ExtVersion(level)
            else error("level must be greater than 0 but was $level")

        /**
         * The highest [ExtVersion], used as the default value when lower versions override higher
         * ones.
         */
        val HIGHEST = ExtVersion(Int.MAX_VALUE)
    }
}
