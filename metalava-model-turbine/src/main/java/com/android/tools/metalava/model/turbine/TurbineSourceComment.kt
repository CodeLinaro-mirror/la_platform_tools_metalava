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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.source.LazySourceComment
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.model.TurbineJavadoc

/** A Turbine implementation of [LazySourceComment]. */
internal class TurbineSourceComment(
    private val sourceFile: TurbineSourceFile?,
    private val turbineJavadoc: TurbineJavadoc,
) : LazySourceComment() {

    override fun obtainFileLocation() =
        if (sourceFile == null) {
            FileLocation.UNKNOWN
        } else {
            TurbineFileLocation(
                sourceFile,
                turbineJavadoc.startPosition(),
                // Report character position for documentation locations as that is consistent
                // across
                // models (because it is computed by Metalava not the underlying models).
                reportCharacterPosition = true,
            )
        }

    override fun obtainText(): String {
        // Reconstruct the original comment.
        val javadoc = turbineJavadoc.value()
        val originalComment = "/**$javadoc*/"
        return originalComment
    }
}
