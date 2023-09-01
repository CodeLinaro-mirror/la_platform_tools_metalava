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
import java.io.LineNumberReader
import java.io.Reader
import java.util.Locale

/**
 * Encapsulates all the information related to the format of a signature file.
 *
 * Some of these will be initialized from the version specific defaults and some will be overridden
 * on the command line.
 */
data class FileFormat(
    val defaultsVersion: DefaultsVersion,
    val specifiedOverloadedMethodOrder: OverloadedMethodOrder? = null,
    val kotlinStyleNulls: Boolean,
    val conciseDefaultValues: Boolean,
) {
    // This defaults to SIGNATURE but can be overridden on the command line.
    val overloadedMethodOrder
        get() = specifiedOverloadedMethodOrder ?: OverloadedMethodOrder.SIGNATURE

    /** The base version of the file format. */
    enum class DefaultsVersion(
        internal val version: String,
        factory: (DefaultsVersion) -> FileFormat,
    ) {
        V2(
            version = "2.0",
            factory = { defaultsVersion ->
                FileFormat(
                    defaultsVersion = defaultsVersion,
                    kotlinStyleNulls = false,
                    conciseDefaultValues = false,
                )
            }
        ),
        V3(
            version = "3.0",
            factory = { defaultsVersion ->
                FileFormat(
                    defaultsVersion = defaultsVersion,
                    kotlinStyleNulls = true,
                    conciseDefaultValues = false,
                )
            }
        ),
        V4(
            version = "4.0",
            factory = { defaultsVersion ->
                FileFormat(
                    defaultsVersion = defaultsVersion,
                    kotlinStyleNulls = true,
                    conciseDefaultValues = true,
                )
            }
        );

        /**
         * The defaults associated with this version.
         *
         * It is initialized via a factory to break the cycles where the [DefaultsVersion]
         * constructor depends on the [FileFormat] constructor and vice versa.
         */
        val defaults = factory(this)
    }

    enum class OverloadedMethodOrder(val comparator: Comparator<MethodItem>) {
        /** Sort overloaded methods according to source order. */
        SOURCE(MethodItem.sourceOrderForOverloadedMethodsComparator),

        /** Sort overloaded methods by their signature. */
        SIGNATURE(MethodItem.comparator)
    }

    /**
     * Apply some optional overrides, provided from the command line, to this format, returning a
     * new format.
     *
     * @param overloadedMethodOrder If non-null then override the [overloadedMethodOrder] property.
     * @return a format with the overrides applied.
     */
    fun applyOptionalCommandLineSuppliedOverrides(
        overloadedMethodOrder: OverloadedMethodOrder? = null,
    ): FileFormat {
        // Only apply the overloadedMethodOrder command line override to the format if it has not
        // already been specified.
        val effectiveOverloadedMethodOrder =
            this.specifiedOverloadedMethodOrder ?: overloadedMethodOrder

        return copy(
            specifiedOverloadedMethodOrder = effectiveOverloadedMethodOrder,
        )
    }

    fun header(): String {
        val prefix = SIGNATURE_FORMAT_PREFIX
        return prefix + defaultsVersion.version + "\n"
    }

    companion object {
        private val allDefaults = DefaultsVersion.values().map { it.defaults }.toList()

        private val versionToDefaults = allDefaults.associateBy { it.defaultsVersion.version }

        // The defaults associated with version 2.0.
        val V2 = DefaultsVersion.V2.defaults

        // The defaults associated with version 3.0.
        val V3 = DefaultsVersion.V3.defaults

        // The defaults associated with version 4.0.
        val V4 = DefaultsVersion.V4.defaults

        // The defaults associated with the latest version.
        val LATEST = allDefaults.last()

        const val SIGNATURE_FORMAT_PREFIX = "// Signature format: "

        /**
         * The size of the buffer and read ahead limit.
         *
         * Should be big enough to handle any first package line, even one with lots of annotations.
         */
        private const val BUFFER_SIZE = 1024

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * @return the [FileFormat] or null if the reader was blank.
         */
        fun parseHeader(filename: String, reader: Reader): FileFormat? {
            val lineNumberReader =
                if (reader is LineNumberReader) reader else LineNumberReader(reader, BUFFER_SIZE)
            return parseHeader(filename, lineNumberReader)
        }

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * This consumes only the content that makes up the header. So, the rest of the file
         * contents can be read from the reader.
         *
         * @return the [FileFormat] or null if the reader was blank.
         */
        private fun parseHeader(filename: String, reader: LineNumberReader): FileFormat? {
            // This reads the minimal amount to determine whether this is likely to be a
            // signature file.
            val prefixLength = SIGNATURE_FORMAT_PREFIX.length
            val buffer = CharArray(prefixLength)
            val prefix =
                reader.read(buffer, 0, prefixLength).let { count ->
                    if (count == -1) {
                        // An empty file.
                        return null
                    }
                    String(buffer, 0, count)
                }

            if (prefix != SIGNATURE_FORMAT_PREFIX) {
                // If the prefix is blank then either the whole file is blank in which case it is
                // handled specially, or the file is not blank and is not a signature file in which
                // case it is an error.
                if (prefix.isBlank()) {
                    var line = reader.readLine()
                    while (line != null && line.isBlank()) {
                        line = reader.readLine()
                    }
                    // If the line is null then te whole file is blank which is handled specially.
                    if (line == null) {
                        return null
                    }
                }

                // An error occurred as the prefix did not match. A valid prefix must appear on a
                // single line so just in case what was read contains multiple lines trim it down to
                // a single line for error reporting. The LineNumberReader has translated non-unix
                // newline characters into `\n` so this is safe.
                val firstLine = prefix.substringBefore("\n")
                throw ApiParseException(
                    "Unknown file format of $filename: invalid prefix, found '$firstLine', expected '$SIGNATURE_FORMAT_PREFIX'"
                )
            }

            val version = reader.readLine()

            return versionToDefaults[version]
                ?: throw ApiParseException(
                    "Unknown file format of $filename: invalid version, found '$version', expected one of '${
                        versionToDefaults.keys.joinToString(
                            "', '"
                        )
                    }'"
                )
        }

        private const val VERSION_PROPERTIES_SEPARATOR = ":"

        /**
         * Apply some optional additional properties from a string.
         *
         * This parses the string into property/value pairs, makes sure that they are valid
         * properties and values and then returns a new copy of this with its values overridden by
         * the values from the properties string.
         *
         * @param extraVersions extra versions to add to the error message if a version is not
         *   recommended but otherwise ignored. This allows the caller to handle some additional
         *   versions first but still report a helpful message.
         */
        fun parseSpecifier(specifier: String, extraVersions: Set<String> = emptySet()): FileFormat {
            val specifierParts = specifier.split(VERSION_PROPERTIES_SEPARATOR, limit = 2)
            val version = specifierParts[0]
            val versionDefaults =
                versionToDefaults[version]
                    ?: throw ApiParseException(
                        "invalid version, found '$version', expected one of '${
                            (versionToDefaults.keys + extraVersions).joinToString(
                                "', '"
                            )
                        }'"
                    )
            if (specifierParts.size == 1) {
                return versionDefaults
            }

            val properties = specifierParts[1]

            val builder = Builder(versionDefaults)
            properties.trim().split(",").forEach {
                val propertyParts = it.split("=")
                if (propertyParts.size != 2) {
                    throw ApiParseException("expected <property>=<value> but found '$it'")
                }
                val name = propertyParts[0]
                val value = propertyParts[1]
                val overrideable = OverrideableProperty.getByName(name)
                overrideable.setFromString(builder, value)
            }
            return builder.build()
        }
    }

    /** A builder for [FileFormat] that applies some optional values to a base [FileFormat]. */
    private class Builder(private val base: FileFormat) {
        var conciseDefaultValues: Boolean? = null
        var kotlinStyleNulls: Boolean? = null
        var overloadedMethodOrder: OverloadedMethodOrder? = null

        fun build(): FileFormat =
            base.copy(
                conciseDefaultValues = conciseDefaultValues ?: base.conciseDefaultValues,
                kotlinStyleNulls = kotlinStyleNulls ?: base.kotlinStyleNulls,
                specifiedOverloadedMethodOrder = overloadedMethodOrder
                        ?: base.specifiedOverloadedMethodOrder,
            )
    }

    /** Information about the different overrideable properties in [FileFormat]. */
    private enum class OverrideableProperty {
        /** concise-default-values=[yes|no] */
        CONCISE_DEFAULT_VALUES {
            override fun setFromString(builder: Builder, value: String) {
                builder.conciseDefaultValues = yesNo(value)
            }
        },
        /** kotlin-style-nulls=[yes|no] */
        KOTLIN_STYLE_NULLS {
            override fun setFromString(builder: Builder, value: String) {
                builder.kotlinStyleNulls = yesNo(value)
            }
        },
        /** overloaded-method-other=[source|signature] */
        OVERLOADED_METHOD_ORDER {
            override fun setFromString(builder: Builder, value: String) {
                builder.overloadedMethodOrder =
                    when (value) {
                        "source" -> OverloadedMethodOrder.SOURCE
                        "signature" -> OverloadedMethodOrder.SIGNATURE
                        else ->
                            throw ApiParseException(
                                "unexpected value for $propertyName, found '$value', expected one of 'source' or 'signature'"
                            )
                    }
            }
        };

        /** The property name in the [parseSpecifier] input. */
        val propertyName: String = name.lowercase(Locale.US).replace("_", "-")

        /**
         * Set the corresponding property in the supplied [Builder] to the value corresponding to
         * the string representation [value].
         */
        abstract fun setFromString(builder: Builder, value: String)

        /** Convert a "yes|no" string into a boolean. */
        fun yesNo(value: String): Boolean {
            return when (value) {
                "yes" -> true
                "no" -> false
                else ->
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$value', expected one of 'yes' or 'no'"
                    )
            }
        }

        companion object {
            val byPropertyName = values().associateBy { it.propertyName }

            fun getByName(name: String): OverrideableProperty =
                byPropertyName[name]
                    ?: throw ApiParseException(
                        "unknown format property name `$name`, expected one of '${byPropertyName.keys.joinToString("', '")}'"
                    )
        }
    }
}
