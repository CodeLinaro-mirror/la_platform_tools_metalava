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

package com.android.tools.metalava.reporter

import java.nio.file.Path

/** Key that can be used to identify an API component for use in the baseline. */
sealed interface BaselineKey {
    /**
     * Get the element id for this key.
     *
     * @param pathTransformer if the key contains a path then it will be passed to this to transform
     *   it into a form suitable for its use.
     */
    fun elementId(pathTransformer: (String) -> String = { it }): String

    companion object {
        val UNKNOWN = forElementId("?")

        /**
         * Get a [BaselineKey] that for the supplied element id.
         *
         * An element id is something that can uniquely identify an API element over a long period
         * of time, e.g. a class name, class name plus method signature.
         */
        fun forElementId(elementId: String): BaselineKey {
            return ElementIdBaselineKey(elementId)
        }

        /** Get a [BaselineKey] for the supplied path. */
        fun forPath(path: Path): BaselineKey {
            return PathBaselineKey(path)
        }
    }

    /**
     * A [BaselineKey] for an element id (which is simply a string that identifies a specific API
     * element).
     */
    private data class ElementIdBaselineKey(val elementId: String) : BaselineKey {
        override fun elementId(pathTransformer: (String) -> String): String {
            return elementId
        }
    }

    /** A [BaselineKey] for a [Path]. */
    private data class PathBaselineKey(val path: Path) : BaselineKey {
        override fun elementId(pathTransformer: (String) -> String): String {
            return pathTransformer(path.toString())
        }
    }
}
