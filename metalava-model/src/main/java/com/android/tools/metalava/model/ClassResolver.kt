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

package com.android.tools.metalava.model

/** Resolves an erased class name to a [ClassItem] or null if it cannot find a matching class. */
interface ClassResolver {
    fun resolveClass(erasedName: String): ClassItem?

    companion object {
        /**
         * A [ClassResolver] that will throw an exception when [resolveClass] is called.
         *
         * Useful for testing and when a [resolveClass] will not be called.
         */
        val THROWING =
            object : ClassResolver {
                override fun resolveClass(erasedName: String): ClassItem? {
                    error("Unsupported: Cannot resolve $erasedName")
                }
            }

        /**
         * A [ClassResolver] that will return `null` when [resolveClass] is called.
         *
         * Useful for testing when [resolveClass] will be called but should return `null`.
         */
        val RETURN_NULL =
            object : ClassResolver {
                override fun resolveClass(erasedName: String) = null
            }
    }
}
