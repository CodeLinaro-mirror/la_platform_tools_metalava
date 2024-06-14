/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.reporter

const val ERROR_WHEN_NEW_SUFFIX = " (ErrorWhenNew)"

enum class Severity(
    /** The name to output when reporting an issue of this [Severity]. */
    private val displayName: String,

    /** An optional suffix to append after the issue message, but before the */
    val messageSuffix: String = "",
) {
    INHERIT("inherit"),

    /** The issue is not reported and not included in any baseline files. */
    HIDDEN("hidden"),

    /**
     * Information level are for issues that are informational only; may or may not be a problem.
     */
    INFO("info"),

    /**
     * Warning level means that we encountered some incompatible or inconsistent API change. These
     * must be resolved to preserve API compatibility.
     */
    WARNING("warning"),

    /**
     * An intermediate level between [WARNING] and [ERROR].
     *
     * The purpose of this is to ease transition between [WARNING] and [ERROR]. First, the severity
     * is changed to this which will prevent any new cases being introduced into existing code. Then
     * the existing cases are fixed. Finally, it is changed to [ERROR].
     */
    WARNING_ERROR_WHEN_NEW("warning", messageSuffix = ERROR_WHEN_NEW_SUFFIX),

    /**
     * Error level means that we encountered severe trouble and were unable to output the requested
     * documentation.
     */
    ERROR("error");

    companion object {
        /**
         * The default value for the `maximumSeverity` parameter in [Reporter.report] methods which
         * is the highest [Severity], i.e. last defined.
         */
        val UNLIMITED = values().last()
    }

    override fun toString(): String = displayName
}
