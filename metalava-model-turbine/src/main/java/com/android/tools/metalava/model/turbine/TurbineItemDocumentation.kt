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

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.AbstractItemDocumentation
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.model.TurbineJavadoc

/**
 * A Turbine specialization of [AbstractItemDocumentation] that delays finding the position of the
 * comment until needed.
 */
internal class TurbineItemDocumentation(
    item: SelectableItem,
    private val sourceFile: TurbineSourceFile?,
    private val turbineJavadoc: TurbineJavadoc,
) : AbstractItemDocumentation(item) {
    /** Backing field for [fileLocation]. */
    private lateinit var _fileLocation: FileLocation

    override val fileLocation: FileLocation
        get() {
            if (!::_fileLocation.isInitialized) {
                ensureTextBackingFieldHasBeenInitialized()
            }
            return _fileLocation
        }

    override fun initializeTextBackingField() {
        // Reconstruct the original comment.
        val javadoc = turbineJavadoc.value()
        val originalComment = "/**$javadoc*/"

        // Initialize the text backing field.
        _text = originalComment

        // Initialize the file location backing field.
        _fileLocation =
            if (sourceFile == null) {
                FileLocation.UNKNOWN
            } else {
                TurbineFileLocation(sourceFile, turbineJavadoc.startPosition())
            }
    }

    // Behaves the same as Psi.
    override fun snapshot(item: SelectableItem) = this
}
