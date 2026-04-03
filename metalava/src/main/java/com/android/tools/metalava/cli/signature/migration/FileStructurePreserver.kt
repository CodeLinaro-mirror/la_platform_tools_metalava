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

package com.android.tools.metalava.cli.signature.migration

import com.android.tools.metalava.cli.signature.writeSignatureFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.CustomizableProperty
import com.android.tools.metalava.model.text.EmitFileHeader
import com.android.tools.metalava.model.text.FileFormat
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Encapsulates logic to compute the [com.android.tools.metalava.model.text.FileFormat] that will
 * preserve the file structure of [codebase] formatted with [currentFormat] while setting as many of
 * the properties in [targetFormat] as possible.
 */
internal class FileStructurePreserver(
    private val currentFormat: FileFormat,
    private val targetFormat: FileFormat,
    private val codebase: Codebase,
) {
    /**
     * The current contents of the signature file against which the effect of property settings will
     * be compared. Initialized lazily as it can be expensive to compute and may not be needed.
     */
    private val currentContents by
        lazy(LazyThreadSafetyMode.NONE) { codebase.toSignatureNoHeader(currentFormat) }

    /**
     * Format this [Codebase] as a signature file using [format] but without the header, returning
     * the [String] result.
     */
    private fun Codebase.toSignatureNoHeader(format: FileFormat): String {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { printWriter ->
            writeSignatureFile(this, format, printWriter, emitFileHeader = EmitFileHeader.NEVER)
        }
        return stringWriter.toString()
    }

    /**
     * Check to see whether this should preserve the property from [currentFormat] or see if it can
     * use the property from [targetFormat] instead.
     */
    private fun <T> copyPropertyPreservingStructure(
        builder: FileFormat.Builder,
        property: CustomizableProperty<T>,
    ) {
        // Get the current value of the property, including any defaults. If it is not set
        // then it will have no impact on the resulting format so return.
        val currentValue = currentFormat[property] ?: return

        // Get the target value, including any defaults.
        val targetValue = targetFormat[property]
        if (targetValue == null) {
            // The target value is null so use the value from the current file.
            builder[property] = currentValue
        } else if (targetValue != currentValue) {
            // The target value is different from the current value so see whether it needs
            // to be changed.
            if (property.defaultable) {
                // Defaultable properties should only be specified if needed. Check to see
                // if its value has any impact on the generated signature file.

                // Build a [FileFormat] that is the same as the [currentFormat] but with
                // its [property] set to the [targetFormat]'s value.
                val currentFormatWithTargetValue =
                    currentFormat.buildCopy { this[property] = targetValue }

                // Generate the signature contents with that format.
                val contentsWithProperty =
                    codebase.toSignatureNoHeader(currentFormatWithTargetValue)

                // If the contents are different with the target value then keep the current
                // value.
                if (currentContents != contentsWithProperty) {
                    builder[property] = currentValue
                }
            } else {
                // Always keep the current value for non-defaultable properties as they are
                // significant even if they do not affect the current signature file.
                // e.g. if the current sets `kotlin-style-nulls=no` and the target sets
                // `kotlin-style-nulls=yes` this cannot just use the latter even if it has
                // no impact on the current structure that is a fundamental change in the
                // information that will be recorded.
                builder[property] = currentValue
            }
        }
    }

    /**
     * Compute a [FileFormat] that will preserve the structure of the original [currentFormat] while
     * incorporating changes from [targetFormat].
     *
     * This assumes that every property difference between [currentFormat] and [targetFormat] could
     * result in structural changes.
     *
     * The [codebase] is used to determine whether a specific property setting affects the file
     * structure by generated signature file contents with and without that setting and comparing
     * the result.
     */
    fun computeFormat() =
        // Create a new FileFormat based on the [targetFormat] with properties copied from
        // [currentFormat] where necessary.
        targetFormat.buildCopy {
            // Iterate over all the properties checking to see if the [targetFormat] value needs to
            // be replaced with the [currentFormat] value.
            for (property in CustomizableProperty.entries) {
                copyPropertyPreservingStructure(this, property)
            }
        }
}
