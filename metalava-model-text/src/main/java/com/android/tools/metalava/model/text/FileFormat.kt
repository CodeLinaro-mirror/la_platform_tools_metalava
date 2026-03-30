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

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.FlaggedApiInheritance
import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.ADD_ADDITIONAL_OVERRIDES
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.FLAGGED_API_INHERITANCE
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.INCLUDE_DEFAULT_PARAMETER_VALUES
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.INCLUDE_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_NAME_TYPE_ORDER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.MIGRATING
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.NAME
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.NORMALIZE_ABSTRACT_MODIFIER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.NORMALIZE_FINAL_MODIFIER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.OVERLOADED_METHOD_ORDER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.SORT_WHOLE_EXTENDS_LIST
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.STRIP_JAVA_LANG_PREFIX
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.STYLE
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.SURFACE
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.TYPE_ARGUMENT_SPACING
import com.android.tools.metalava.reporter.FileLocation
import java.io.LineNumberReader
import java.io.Reader
import java.nio.file.Path

/**
 * Encapsulates all the information related to the format of a signature file.
 *
 * Some of these will be initialized from the version specific defaults and some will be overridden
 * on the command line.
 */
data class FileFormat(
    val version: Version,
    /**
     * If specified then it contains property defaults that have been specified on the command line
     * and whose value should be used as the default for any property that has not been specified in
     * this format.
     *
     * Not every property is eligible to have its default overridden on the command line. Only those
     * that have a property getter to provide the default.
     */
    val formatDefaults: PropertyMap? = null,

    /** The map containing [CustomizableProperty] values. */
    val propertyMap: PropertyMap = emptyPropertyMap()
) {
    init {
        val migrating = propertyMap[MIGRATING]
        if (migrating != null && "[,\n]".toRegex().find(migrating) != null) {
            throw IllegalStateException(
                """invalid value for property 'migrating': '$migrating' contains at least one invalid character from the set {',', '\n'}"""
            )
        }

        validateIdentifier(propertyMap[NAME], "name")
        validateIdentifier(propertyMap[SURFACE], "surface")

        if (this[INCLUDE_TYPE_USE_ANNOTATIONS] && !this[KOTLIN_NAME_TYPE_ORDER]) {
            throw IllegalStateException(
                "Type-use annotations can only be included in signatures when `kotlin-name-type-order=yes` is set"
            )
        }
    }

    /** Check that the supplied identifier is valid. */
    private fun validateIdentifier(identifier: String?, propertyName: String) {
        identifier ?: return
        if ("[a-z]([a-z0-9-]*[a-z0-9])?".toRegex().matchEntire(identifier) == null) {
            throw IllegalStateException(
                """invalid value for property '$propertyName': '$identifier' must start with a lower case letter, contain any number of lower case letters, numbers and hyphens, and end with either a lowercase letter or number"""
            )
        }
    }

    /**
     * Compute the effective value of a property.
     *
     * This returns the first non-null value in the following:
     * 1. This [FileFormat]'s property value.
     * 2. The [formatDefaults]'s property value
     * 3. The [CustomizableProperty.defaultValue] which is always set.
     *
     * @param property the property whose value is to be retrieved.
     */
    operator fun <T> get(property: CustomizableProperty<T>): T =
        propertyMap[property] ?: formatDefaults?.get(property) ?: property.defaultValue

    /**
     * The base version of the file format.
     *
     * There is a cycle in the creation of [Version] and [FileFormat] and care must be taken not to
     * initialize this class before [FileFormat] and its companion. That means you must not access
     * [Version.entries] directly. Use [FileFormat.versions] instead.
     */
    enum class Version(
        /** The version number of this as a string, e.g. "3.0". */
        val versionNumber: String,

        /** The optional [Version] that this extends. */
        val baseVersion: Version? = null,

        /** Indicates whether the version supports properties fully or just for migrating. */
        internal val propertySupport: PropertySupport = PropertySupport.FOR_MIGRATING_ONLY,

        /**
         * Factory used to create a [FileFormat] instance encapsulating the defaults of this
         * version.
         */
        factory: (Version) -> FileFormat,
        /** Help text to use on the command line. */
        val help: String,
    ) {
        V2(
            versionNumber = "2.0",
            factory = { version -> FileFormat(version = version) },
            help =
                """
                    This is the base version for all the others.
                """,
        ),
        V4(
            versionNumber = "4.0",
            baseVersion = V2,
            factory = { version ->
                version.baseDefaults.buildCopy {
                    this.version = version
                    // This adds kotlinStyleNulls = true
                    this[KOTLIN_STYLE_NULLS] = true
                    // This adds includeDefaultParameterValues = true
                    this[INCLUDE_DEFAULT_PARAMETER_VALUES] = true
                }
            },
            help =
                """
                    Introduced support for improved Kotlin tracking.
                """,
        ),
        V5(
            versionNumber = "5.0",
            baseVersion = V4,
            // This adds full property support.
            propertySupport = PropertySupport.FULL,
            factory = { version ->
                version.baseDefaults.copy(
                    version = version,
                    // This does not add any property defaults, just full property support.
                )
            },
            help =
                """
                    This is the first version that has full support for properties in the signature
                    header. As such it does not add any new defaults to `4.0`. The intent is that
                    properties will be explicitly defined in the signature file avoiding reliance on
                    version specific defaults.
                """,
        ),
        V6(
            versionNumber = "6.0",
            baseVersion = V5,
            // This adds full property support.
            propertySupport = PropertySupport.FULL,
            factory = { version ->
                version.baseDefaults.buildCopy {
                    this.version = version
                    this[ADD_ADDITIONAL_OVERRIDES] = true
                    this[FLAGGED_API_INHERITANCE] = FlaggedApiInheritance.NESTED_CLASSES
                    this[NORMALIZE_ABSTRACT_MODIFIER] = true
                    this[NORMALIZE_FINAL_MODIFIER] = true
                    this[OVERLOADED_METHOD_ORDER] = OverloadedMethodOrder.SIGNATURE
                    this[SORT_WHOLE_EXTENDS_LIST] = true
                    this[STRIP_JAVA_LANG_PREFIX] = StripJavaLangPrefix.ALWAYS
                    this[TYPE_ARGUMENT_SPACING] = TypeArgumentSpacing.SPACE
                }
            },
            help =
                """
                    Provides support for sealed and record classes.

                    Also, provides defaults for lots of formatting properties to ensure consistent
                    formatting.
                """,
        ),
        ;

        /**
         * The base [Version.defaults] for this version. Must only be called when [baseVersion] is
         * non-null.
         */
        private val baseDefaults
            get() = baseVersion!!.defaults

        /**
         * The defaults associated with this version.
         *
         * It is initialized via a factory to break the cycle where the [Version] constructor
         * depends on the [FileFormat] constructor and vice versa.
         */
        val defaults = factory(this)

        /**
         * Get the version defaults plus any style defaults, if available.
         *
         * @param namedStyle the optional style whose defaults should be applied to the version
         *   defaults.
         */
        internal fun defaultsIncludingStyle(namedStyle: NamedStyle?) =
            namedStyle?.let { style ->
                defaults.buildCopy { mutablePropertyMap.applyDefaultsFromOther(style.propertyMap) }
            } ?: defaults
    }

    internal enum class PropertySupport {
        /**
         * The version only supports properties being temporarily specified in the signature file to
         * aid migration.
         */
        FOR_MIGRATING_ONLY,

        /**
         * The version supports properties fully, both for migration and permanent customization in
         * the signature file.
         */
        FULL
    }

    /** The named styles available to apply. */
    enum class NamedStyle {
        JAVA {
            override fun createPropertyMap() = buildPropertyMap {
                this[INCLUDE_DEFAULT_PARAMETER_VALUES] = false
                this[KOTLIN_STYLE_NULLS] = false
            }
        },
        KOTLIN {
            override fun createPropertyMap() = buildPropertyMap {
                this[INCLUDE_DEFAULT_PARAMETER_VALUES] = true
                this[KOTLIN_STYLE_NULLS] = true
            }
        },
        ;

        /** Create [NamedStyle] specific [PropertyMap]. */
        protected abstract fun createPropertyMap(): PropertyMap

        /**
         * The set of style specific defaults.
         *
         * Created lazily to avoid a dependency cycle during creation of the [NamedStyle] property,
         * it is created before the other properties like [KOTLIN_STYLE_NULLS] that are set.
         */
        val propertyMap: PropertyMap by lazy(LazyThreadSafetyMode.NONE) { createPropertyMap() }
    }

    enum class OverloadedMethodOrder(val comparator: Comparator<CallableItem>) {
        /** Sort overloaded methods according to source order. */
        SOURCE(CallableItem.sourceOrderForOverloadedMethodsComparator),

        /** Sort overloaded methods by their signature. */
        SIGNATURE(CallableItem.comparator)
    }

    /** Different ways of spacing out type arguments in [TypeItem.toTypeString]. */
    enum class TypeArgumentSpacing {
        /**
         * No spacing added between type arguments unless they are in the bounds of a type
         * parameter.
         */
        LEGACY,

        /** No spacing added between type arguments. */
        NONE,

        /** A single space added after the comma that separates type arguments. */
        SPACE,
    }

    /**
     * Get the header for the signature file that corresponds to this format.
     *
     * This always starts with the signature format prefix, and the version number, following by a
     * newline and some option property assignments (e.g. `property=value`), one per line prefixed
     * with [PROPERTY_LINE_PREFIX].
     */
    fun header(): String {
        val migrating = propertyMap[MIGRATING]
        return buildString {
            append(SIGNATURE_FORMAT_PREFIX)
            append(version.versionNumber)
            append("\n")
            // Only output properties if the version supports them fully or it is migrating.
            if (version.propertySupport == PropertySupport.FULL || migrating != null) {
                iterateOverCustomizableProperties { property, value ->
                    append(PROPERTY_LINE_PREFIX)
                    append(property)
                    append("=")
                    append(value)
                    append("\n")
                }
            }
        }
    }

    /**
     * Get the specifier for this format.
     *
     * It starts with the version number followed by an optional `:` followed by at least one comma
     * separate `property=value` pair. This is used on the command line for the `--format` option.
     */
    fun specifier(): String {
        return buildString {
            append(version.versionNumber)

            var separator = VERSION_PROPERTIES_SEPARATOR
            iterateOverCustomizableProperties { property, value ->
                append(separator)
                separator = ","
                append(property)
                append("=")
                append(value)
            }
        }
    }

    /**
     * Iterate over all the properties of this format which have different values to the values in
     * this format's [Version.defaultsIncludingStyle], invoking the [consumer] with each property,
     * value pair.
     */
    private fun iterateOverCustomizableProperties(consumer: (String, String) -> Unit) {
        val defaults = version.defaultsIncludingStyle(propertyMap[STYLE])
        if (this != defaults) {
            CustomizableProperty.entries.forEach { property ->
                // Get the string value of this property, if null then it was not specified so skip
                // the property.
                val thisValue = this@FileFormat.getAsString(property) ?: return@forEach
                val defaultValue = defaults.getAsString(property)
                if (thisValue != defaultValue) {
                    consumer(property.propertyName, thisValue)
                }
            }
        }
    }

    /**
     * Validate the format
     *
     * @param exceptionContext information to add to the start of the exception message that
     *   provides context for the user.
     * @param migratingAllowed true if the [MIGRATING] option is allowed, false otherwise. If it is
     *   allowed then it will also be required if [Version.propertySupport] is
     *   [PropertySupport.FOR_MIGRATING_ONLY].
     */
    private fun validate(exceptionContext: String = "", migratingAllowed: Boolean) {
        // If after applying all the properties the format matches its version defaults then
        // there is nothing else to check.
        if (this == version.defaults) {
            return
        }

        val migrating = propertyMap[MIGRATING]
        if (migratingAllowed) {
            // If the version does not support properties (except when migrating) and the
            // version defaults have been overridden then the `migrating` property is mandatory
            // when migrating is allowed.
            if (version.propertySupport != PropertySupport.FULL && migrating == null) {
                throw ApiParseException(
                    "${exceptionContext}must provide a 'migrating' property when customizing version ${version.versionNumber}"
                )
            }
        } else if (migrating != null) {
            throw ApiParseException("${exceptionContext}must not contain a 'migrating' property")
        }
    }

    /**
     * Get the value of [property] as a [String].
     *
     * This will NOT apply any defaults that it finds.
     */
    fun <T> getAsString(property: CustomizableProperty<T>) =
        propertyMap[property]?.let { property.valueToString(it) }

    /**
     * Get the value of [property] as a [String].
     *
     * This will apply any defaults that it finds.
     */
    fun <T> getWithDefault(property: CustomizableProperty<T>) =
        this[property]?.let { property.valueToString(it) }

    /** Build a copy of this [FileFormat] by applying [body] to [Builder]. */
    inline fun buildCopy(body: Builder.() -> Unit) = Builder(this).apply { body() }.build()

    companion object {
        private val versionByNumber = Version.entries.associateBy { it.versionNumber }

        // The defaults associated with version 2.0.
        val V2 = Version.V2.defaults

        // The defaults associated with version 4.0.
        val V4 = Version.V4.defaults

        // The defaults associated with version 5.0.
        val V5 = Version.V5.defaults

        // The defaults associated with version 6.0.
        val V6 = Version.V6.defaults

        /** The list of all [Version] instances. */
        val versions: List<Version> = Version.entries

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
         * @param path the [Path] of the file from which the content is being read.
         * @param reader the reader to use to read the file contents.
         * @param formatForLegacyFiles the optional format to use if the file uses a legacy, and now
         *   unsupported file format.
         * @return the [FileFormat] or null if the reader was blank.
         */
        fun parseHeader(
            path: Path,
            reader: Reader,
            formatForLegacyFiles: FileFormat? = null
        ): FileFormat? {
            val lineNumberReader =
                reader as? LineNumberReader ?: LineNumberReader(reader, BUFFER_SIZE)

            try {
                return parseHeader(lineNumberReader, formatForLegacyFiles)
            } catch (cause: ApiParseException) {
                // Wrap the exception and add contextual information to help user identify and fix
                // the problem. This is done here instead of when throwing the exception as the
                // original thrower does not have that context.
                throw ApiParseException(
                        "Signature format error - ${cause.message}",
                        FileLocation.createLocation(path, lineNumberReader.lineNumber),
                    )
                    .apply { initCause(cause) }
            }
        }

        /**
         * Parse the start of the contents provided by [reader] to obtain the [FileFormat]
         *
         * This consumes only the content that makes up the header. So, the rest of the file
         * contents can be read from the reader.
         *
         * @return the [FileFormat] or null if the reader was blank.
         */
        private fun parseHeader(
            reader: LineNumberReader,
            formatForLegacyFiles: FileFormat?
        ): FileFormat? {
            // Remember the starting position of the reader just in case it is necessary to reset
            // it back to this point.
            reader.mark(BUFFER_SIZE)

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

                // If formatForLegacyFiles has been provided then check to see if the file adheres
                // to a legacy format and if it does behave as if it was formatForLegacyFiles.
                if (formatForLegacyFiles != null) {
                    // Check for version 1.0, i.e. no header at all.
                    if (prefix.startsWith("package ")) {
                        reader.reset()
                        return formatForLegacyFiles
                    }
                }

                // An error occurred as the prefix did not match. A valid prefix must appear on a
                // single line so just in case what was read contains multiple lines trim it down to
                // a single line for error reporting. The LineNumberReader has translated non-unix
                // newline characters into `\n` so this is safe.
                val firstLine = prefix.substringBefore("\n")
                // As the error is going to be reported for the first line, even though possibly
                // multiple lines have been read set the line number to 1.
                reader.lineNumber = 1
                throw ApiParseException(
                    "invalid prefix, found '$firstLine', expected '$SIGNATURE_FORMAT_PREFIX'"
                )
            }

            // Read the rest of the line after the SIGNATURE_FORMAT_PREFIX which should just be the
            // version.
            val versionNumber = reader.readLine()
            val version = getVersionFromNumber(versionNumber)

            val format = parseProperties(reader, version)
            format.validate(migratingAllowed = true)
            return format
        }

        private const val VERSION_PROPERTIES_SEPARATOR = ":"

        /**
         * Parse a format specifier string and create a corresponding [FileFormat].
         *
         * The [specifier] consists of a version, e.g. `4.0`, followed by an optional list of comma
         * separate properties. If the properties are provided then they are separated from the
         * version with a `:`. A property is expressed as a property assignment, e.g.
         * `property=value`.
         *
         * This extracts the version and then if no properties are provided returns its defaults. If
         * properties are provided then each property is checked to make sure that it is a valid
         * property with a valid value and then it is applied on top of the version defaults. The
         * result of that is returned.
         *
         * @param specifier the specifier string that defines a [FileFormat].
         * @param migratingAllowed indicates whether the `migrating` property is allowed in the
         *   specifier.
         */
        fun parseSpecifier(
            specifier: String,
            migratingAllowed: Boolean = false,
        ): FileFormat {
            val specifierParts = specifier.split(VERSION_PROPERTIES_SEPARATOR, limit = 2)
            val versionNumber = specifierParts[0]
            val version = getVersionFromNumber(versionNumber)
            val versionDefaults = version.defaults
            if (specifierParts.size == 1) {
                return versionDefaults
            }

            val properties = specifierParts[1]

            val format = parseOverrides(versionDefaults, properties)

            format.validate(
                exceptionContext = "invalid format specifier: '$specifier' - ",
                migratingAllowed = migratingAllowed,
            )

            return format
        }

        /**
         * Get the [Version] from the number.
         *
         * @param versionNumber the version number as a string.
         */
        private fun getVersionFromNumber(versionNumber: String): Version =
            versionByNumber[versionNumber]
                ?: let {
                    val allVersions = versionByNumber.keys
                    val possibilities = allVersions.joinToString { "'$it'" }
                    throw ApiParseException(
                        "invalid version, found '$versionNumber', expected one of $possibilities"
                    )
                }

        private const val PROPERTY_LINE_PREFIX = "// - "

        /**
         * Parse property pairs, one per line, each of which must be prefixed with
         * [PROPERTY_LINE_PREFIX], apply them to the supplied [version]s
         * [Version.defaultsIncludingStyle] and returning the result.
         */
        private fun parseProperties(
            reader: LineNumberReader,
            version: Version,
        ): FileFormat {
            // Parse the properties into a PropertyMap.
            val propertyMap = buildPropertyMap {
                do {
                    reader.mark(BUFFER_SIZE)
                    val line = reader.readLine() ?: break
                    if (line.startsWith("package ")) {
                        reader.reset()
                        break
                    }

                    // If the line does not start with "// - " then it is not a property so assume
                    // the header is ended.
                    val remainder = line.removePrefix(PROPERTY_LINE_PREFIX)
                    if (remainder == line) {
                        reader.reset()
                        break
                    }

                    setPropertyFromAssignment(remainder)
                } while (true)
            }

            return if (propertyMap.isEmpty()) {
                version.defaults
            } else {
                // Override the default properties.
                version.defaults.buildCopy { mutablePropertyMap.copyFromOther(propertyMap) }
            }
        }

        /**
         * Parse the supplied set of defaults and construct a [FileFormat].
         *
         * @param defaults comma separated list of property assignments that
         */
        fun parseDefaults(defaults: String) = parseProperties(defaults, defaultableOnly = true)

        /**
         * Parse the supplied set of overrides and construct a [FileFormat] by applying the
         * overrides to [base].
         *
         * @param overrides comma separated list of property assignments that will be applied to a
         *   copy of [base], overriding the existing values of those properties, if any.
         */
        fun parseOverrides(base: FileFormat, overrides: String) =
            base.buildCopy {
                val properties = parseProperties(overrides)
                mutablePropertyMap.copyFromOther(properties)
            }

        /**
         * Parse a comma separated list of property assignments of the form `property=value`,
         * populating a [PropertyMap].
         *
         * @param properties the property assignments.
         * @param defaultableOnly if `true` then only [CustomizableProperty.defaultable] properties
         *   are allowed.
         */
        private fun parseProperties(
            properties: String,
            defaultableOnly: Boolean = false,
        ) = buildPropertyMap {
            properties.trim().split(",").forEach { setPropertyFromAssignment(it, defaultableOnly) }
        }

        /**
         * Get the names of the [CustomizableProperty] that are [CustomizableProperty.defaultable].
         */
        fun defaultableProperties(): List<String> {
            return CustomizableProperty.entries
                .filter { it.defaultable }
                .map { it.propertyName }
                .sorted()
                .toList()
        }
    }

    /** A builder for [FileFormat] that applies some optional values to a base [FileFormat]. */
    class Builder(private val base: FileFormat) {
        var version: Version? = null

        /** Type safe map from [CustomizableProperty] to value. */
        internal val mutablePropertyMap = mutablePropertyMap()

        /** Delegate to [mutablePropertyMap]. */
        operator fun <T> get(property: CustomizableProperty<T>): T? = mutablePropertyMap[property]

        /** Delegate to [mutablePropertyMap]. */
        operator fun <T> set(property: CustomizableProperty<T>, value: T) {
            mutablePropertyMap[property] = value
        }

        /** Build the [FileFormat] from the information in this [Builder]. */
        fun build(): FileFormat {
            // Apply any style defaults first as they take priority over version defaults.
            this[STYLE]?.let { style ->
                mutablePropertyMap.applyDefaultsFromOther(style.propertyMap)
            }

            // Then apply any properties from the base which includes version defaults.
            mutablePropertyMap.applyDefaultsFromOther(base.propertyMap)

            return base.copy(
                version = this.version ?: base.version,
                propertyMap = mutablePropertyMap.toMap(),
            )
        }
    }
}
