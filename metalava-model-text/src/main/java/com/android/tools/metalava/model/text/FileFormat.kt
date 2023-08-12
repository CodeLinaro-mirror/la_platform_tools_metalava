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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.MethodItem

/**
 * Encapsulates all the information related to the format of a signature file.
 *
 * Some of these will be initialized from the version specific defaults and some will be overridden
 * on the command line.
 */
data class FileFormat(
    val defaultsVersion: DefaultsVersion,
    val description: String = "Metalava signature file",
    val version: String,
    val headerPrefix: String? = "// Signature format: ",
    // This defaults to SIGNATURE but can be overridden on the command line.
    val overloadedMethodOrder: OverloadedMethodOrder = OverloadedMethodOrder.SIGNATURE,
    val kotlinStyleNulls: Boolean,
    val conciseDefaultValues: Boolean,
    /**
     * In old signature files, methods inherited from hidden super classes are not included. An
     * example of this is StringBuilder.setLength. We may see these in the codebase but not in the
     * (old) signature files, so in these cases we want to ignore certain changes such as
     * considering StringBuilder.setLength a newly added method.
     */
    val hasPartialSignatures: Boolean = false,
) {
    /** The base version of the file format. */
    enum class DefaultsVersion {
        V1,
        V2,
        V3,
        V4
    }

    enum class OverloadedMethodOrder(val comparator: Comparator<MethodItem>) {
        /** Sort overloaded methods according to source order. */
        SOURCE(MethodItem.sourceOrderForOverloadedMethodsComparator),

        /** Sort overloaded methods by their signature. */
        SIGNATURE(MethodItem.comparator)
    }

    fun header(): String? {
        val prefix = headerPrefix ?: return null
        return prefix + version + "\n"
    }

    companion object {
        internal val allDefaults = mutableListOf<FileFormat>()

        private fun addDefaults(defaults: FileFormat): FileFormat {
            allDefaults += defaults
            return defaults
        }

        // The defaults associated with version 1.0.
        val V1 =
            addDefaults(
                FileFormat(
                    defaultsVersion = DefaultsVersion.V1,
                    description = "Doclava signature file",
                    version = "1.0",
                    headerPrefix = null,
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                    hasPartialSignatures = true,
                )
            )

        // The defaults associated with version 2.0.
        val V2 =
            addDefaults(
                FileFormat(
                    defaultsVersion = DefaultsVersion.V2,
                    version = "2.0",
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                )
            )

        // The defaults associated with version 3.0.
        val V3 =
            addDefaults(
                FileFormat(
                    defaultsVersion = DefaultsVersion.V3,
                    version = "3.0",
                    kotlinStyleNulls = true,
                    conciseDefaultValues = false,
                )
            )

        // The defaults associated with version 4.0.
        val V4 =
            addDefaults(
                FileFormat(
                    defaultsVersion = DefaultsVersion.V4,
                    version = "4.0",
                    kotlinStyleNulls = true,
                    conciseDefaultValues = true,
                )
            )

        val LATEST = allDefaults.last()
    }
}
