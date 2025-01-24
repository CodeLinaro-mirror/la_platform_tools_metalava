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

import com.android.tools.metalava.apilevels.ApiVersion.Companion.toString
import java.util.regex.Pattern

/** Version of an SDK, e.g. Android or AndroidX. */
@ConsistentCopyVisibility
data class ApiVersion
internal constructor(
    /** The major version. */
    val major: Int,

    /**
     * The optional minor version.
     *
     * If it is `null` then neither it nor [patch] or [preReleaseQuality] are included in
     * [toString]. If it is not `null` then it must be greater than or equal to 0.
     */
    internal val minor: Int? = null,

    /**
     * The optional patch version.
     *
     * This must only be specified if [minor] is also specified.
     *
     * If it is `null` then neither it nor [preReleaseQuality] are included in [toString]. If it is
     * not `null` then it must be greater than or equal to 0.
     */
    private val patch: Int? = null,

    /**
     * The pre-release quality.
     *
     * This must only be specified if [patch] is also specified.
     *
     * If it is null then the version is assumed to have been released, and this is not included in
     * [toString]. Otherwise, the version has not been released and this is included at the end of
     * [toString], separated from [patch] by `-`.
     *
     * Any string is acceptable, but they must adhere to the rule that when the strings are sorted
     * alphanumerically they appear in order from the lowest quality to the highest quality.
     */
    private val preReleaseQuality: String? = null,
) : Comparable<ApiVersion> {

    // Check constraints.
    init {
        require(major >= 0) { "major must be greater than or equal to 0 but was $major" }

        if (minor != null) {
            require(minor >= 0) { "minor must be greater than or equal to 0 but was $minor" }
        }

        if (patch != null) {
            require(minor != null) { "patch ($patch) was specified without also specifying minor" }

            require(patch >= 0) { "patch must be greater than or equal to 0 but was $patch" }
        }

        if (preReleaseQuality != null) {
            require(patch != null) {
                "preReleaseQuality ($preReleaseQuality) was specified without also specifying patch"
            }
        }
    }

    /**
     * Make sure that this is a valid version.
     *
     * A version of "0" is not valid as historically API levels started from 1. However, it is valid
     * to have a [major] version of "0" as long as a [minor] version has also been provided, e.g.
     * "0.0" is valid.
     */
    val isValid
        get() = major > 0 || minor != null

    private val text = buildString {
        append(major)
        if (minor != null) {
            append('.')
            append(minor)
            if (patch != null) {
                append('.')
                append(patch)
                if (preReleaseQuality != null) {
                    append('-')
                    append(preReleaseQuality)
                }
            }
        }
    }

    override operator fun compareTo(other: ApiVersion) =
        compareValuesBy(
            this,
            other,
            { it.major },
            { it.minor },
            { it.patch },
            { it.preReleaseQuality == null }, // False (released) sorts above true (pre-release)
            {
                it.preReleaseQuality
            } // Pre-release quality names are in alphabetical order from lower quality to higher
            // quality.
        )

    operator fun plus(increment: Int) =
        ApiVersion(major + increment, minor, patch, preReleaseQuality)

    override fun toString() = text

    companion object {
        /** Get the [ApiVersion] for [level], which must be greater than 0. */
        fun fromLevel(level: Int) =
            if (level > 0) ApiVersion(level)
            else error("level must be greater than 0 but was $level")

        /** Pattern for acceptable input to [fromString]. */
        private val VERSION_REGEX = Pattern.compile("""^(\d+)(\.(\d+)(\.(\d+)(-(.+))?)?)?$""")

        /** Index of `major` group in [VERSION_REGEX]. */
        private const val MAJOR_GROUP = 1

        /** Index of `minor` group in [VERSION_REGEX]. */
        private const val MINOR_GROUP = 3

        /** Index of `patch` group in [VERSION_REGEX]. */
        private const val PATCH_GROUP = 5

        /** Index of `pre-release-quality` group in [VERSION_REGEX]. */
        private const val QUALITY_GROUP = 7

        /**
         * Get the [ApiVersion] for [text], which must be match
         * `major(.minor(.patch(-quality)?)?)?`.
         *
         * Where `major`, `minor` and `patch` are all non-negative integers and `quality` is a
         * string chosen such that qualities sort lexicographically from the lowest quality to the
         * highest quality, e.g. `alpha`, `beta`, `rc` and not `good`, `bad`, `worse`.
         */
        fun fromString(text: String): ApiVersion {
            val matcher = VERSION_REGEX.matcher(text)
            if (!matcher.matches()) {
                error("Can not parse version: $text")
            }

            val major = matcher.group(MAJOR_GROUP).toInt()
            val minor = matcher.group(MINOR_GROUP)?.toInt()
            val patch = matcher.group(PATCH_GROUP)?.toInt()
            val quality = matcher.group(QUALITY_GROUP)

            return ApiVersion(major, minor, patch, quality)
        }

        /** Create an [ApiVersion] with the specified [major] and [minor] properties. */
        fun fromMajorMinor(major: Int, minor: Int? = null) = ApiVersion(major, minor)

        /**
         * The lowest [ApiVersion], used as the default value when higher versions override lower
         * ones.
         */
        val LOWEST = ApiVersion(0)

        /**
         * The highest [ApiVersion], used as the default value when lower versions override higher
         * ones.
         */
        val HIGHEST = ApiVersion(Int.MAX_VALUE)
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
    }
}
