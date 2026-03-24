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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.FlaggedApiInheritance
import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.text.FileFormat.Builder
import com.android.tools.metalava.model.text.FileFormat.Companion.parseSpecifier
import com.android.tools.metalava.model.text.FileFormat.NamedStyle
import com.android.tools.metalava.model.text.FileFormat.OverloadedMethodOrder
import com.android.tools.metalava.model.text.FileFormat.TypeArgumentSpacing
import java.util.Locale
import kotlin.enums.enumEntries
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Information about the different customizable properties in [FileFormat].
 *
 * This implements [ReadOnlyProperty] and has a [provideDelegate] method so that this can be used as
 * a delegate in [Companion] giving each instance access to the name of the delegating property in
 * [Companion], e.g. the [KOTLIN_STYLE_NULLS] instance is given a [KProperty] whose [KProperty.name]
 * is `"KOTLIN_STYLE_NULLS"`.
 */
class CustomizableProperty<T>
private constructor(
    val defaultable: Boolean,
    val defaultValue: T,
    /** Syntax of command line values. */
    val valueSyntax: String,
    /** Help text to use on the command line. */
    val help: String,
    val valueToString: (T & Any).() -> String,
    private val stringToValue: FromString.() -> T,
) : ReadOnlyProperty<CustomizableProperty.Companion, CustomizableProperty<T>> {

    companion object {
        /** List of all [CustomizableProperty]s, populated by [provideDelegate]. */
        private val propertyList = mutableListOf<CustomizableProperty<*>>()

        /** Factory method for a [Boolean] [CustomizableProperty] */
        private fun booleanProperty(
            defaultable: Boolean = false,
            help: String,
        ) =
            CustomizableProperty(
                defaultable,
                defaultValue = false,
                "yes|no",
                help,
                valueToString = { booleanToYesNo() },
                stringToValue = { yesNoToBoolean() }
            )

        /** Convert a "yes|no" string into a boolean. */
        private fun FromString.yesNoToBoolean() =
            when (string) {
                "yes" -> true
                "no" -> false
                else ->
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$string', expected one of 'yes' or 'no'"
                    )
            }

        /** Convert a boolean into a `yes|no` string. */
        private fun Boolean.booleanToYesNo() = if (this) "yes" else "no"

        /** Factory method for an [E] [CustomizableProperty] */
        private inline fun <reified E : Enum<E>> enumProperty(
            defaultable: Boolean = false,
            defaultValue: E,
            help: String,
            entryFilter: (E) -> Boolean = { true },
        ): CustomizableProperty<E> {
            val entries = enumEntries<E>().filter(entryFilter)
            val valueSyntax = entries.joinToString("|") { it.stringFromEnum() }
            return CustomizableProperty(
                defaultable,
                defaultValue,
                valueSyntax,
                help,
                valueToString = { stringFromEnum() },
                stringToValue = { enumFromString(entries) },
            )
        }

        /** Factory method for an [E] [CustomizableProperty] */
        private inline fun <reified E : Enum<E>> optionalEnumProperty(
            defaultable: Boolean = false,
            defaultValue: E?,
            help: String,
        ): CustomizableProperty<E?> {
            val entries = enumEntries<E>()
            val valueSyntax = entries.joinToString("|") { it.stringFromEnum() }
            return CustomizableProperty(
                defaultable,
                defaultValue,
                valueSyntax,
                help,
                valueToString = { stringFromEnum() },
                stringToValue = { enumFromString(entries) },
            )
        }

        /** Map from a string value to an enum value of the required type. */
        private fun <E : Enum<E>> FromString.enumFromString(entries: List<E>): E {
            return entries.firstOrNull { it.stringFromEnum() == string }
                ?: let {
                    val possibilities = entries.possibilitiesList { "'${it.stringFromEnum()}'" }
                    throw ApiParseException(
                        "unexpected value for $propertyName, found '$string', expected one of $possibilities"
                    )
                }
        }

        /**
         * Given an array of items return a list of possibilities.
         *
         * The last pair of items are separated by " or ", the other pairs are separated by ", ".
         */
        private fun <E> List<E>.possibilitiesList(transform: (E) -> String): String {
            val allButLast = dropLast(1)
            val last = last()
            val options = buildString {
                allButLast.joinTo(this, transform = transform)
                append(" or ")
                append(transform(last))
            }
            return options
        }

        /**
         * Extension function to convert an enum value to an external string.
         *
         * It simply returns the lowercase version of the enum name with `_` replaced with `-`.
         */
        fun <E : Enum<E>> E.stringFromEnum() = name.lowercase(Locale.US).replace("_", "-")

        /** Factory method for an optional [String] [CustomizableProperty] */
        private fun optionalStringProperty(
            valueSyntax: String,
            help: String,
        ) =
            CustomizableProperty(
                defaultable = false,
                defaultValue = null,
                valueSyntax = valueSyntax,
                help = help,
                valueToString = { this },
                stringToValue = { string },
            )

        // The order of values in this is significant as it determines the order of the properties
        // in signature headers. The values in this block are not in alphabetical order because it
        // is important that they are at the start of the signature header.

        val NAME by
            optionalStringProperty(
                valueSyntax = "<identifier>",
                help =
                    """
                        Specifies the name of the API.

                        It must start with a lower case letter, contain any number of lower case
                        letters, numbers and hyphens, and end with either a lowercase letter or
                        number.

                        Its purpose is to provide information to metalava and to a lesser extent the
                        owner of the file about which API the file contains. The exact meaning of
                        the API name is determined by the owner, metalava simply uses this as an
                        identifier for comparison.
                    """,
            )

        val SURFACE by
            optionalStringProperty(
                valueSyntax = "<identifier>",
                help =
                    """
                        Specifies the name of the API surface.

                        It must start with a lower case letter, contain any number of lower case
                        letters, numbers and hyphens, and end with either a lowercase letter or
                        number.

                        Its purpose is to provide information to metalava and to a lesser extent the
                        owner of the file about which API surface the file contains. The exact
                        meaning of the API surface name is determined by the owner, metalava simply
                        uses this as an identifier for comparison.
                    """,
            )

        /** style=[java|kotlin] */
        val STYLE by
            optionalEnumProperty<NamedStyle>(
                defaultValue = null,
                help =
                    """
                        The name of a predefined set of properties to apply as defaults. They
                        override version defaults but are themselves overridden by properties listed
                        in the file.
                    """,
            )

        // The following values must be in alphabetical order.

        /** add-additional-overrides=[yes|no] */
        val ADD_ADDITIONAL_OVERRIDES by
            booleanProperty(
                defaultable = true,
                help =
                    """
                        If `yes` then add additional overrides into the signature file that are
                        needed in order to create compilable stubs from the signature file.
                    """,
            )

        val FLAGGED_API_INHERITANCE by
            enumProperty<FlaggedApiInheritance>(
                defaultable = true,
                defaultValue = FlaggedApiInheritance.NONE,
                help =
                    """
                        Specifies whether `@FlaggedApi` annotations are inherited in signature
                        files.

                        `none` (default) - they are not inherited. This can make it difficult to
                        determine whether a nested class is flagged when reviewin as the containing
                        class may be out of view or even in another file altogether.

                        `nested-classes` - they are inherited onto nested classes that do not have
                        their own `@FlaggedApi` annotation.
                    """,
            )

        /** include-default-parameter-values=[yes|no] */
        val INCLUDE_DEFAULT_PARAMETER_VALUES by
            booleanProperty(
                help =
                    """
                    If `no` then the signature file will not include any information about default
                    parameter values. If `yes` then it will use the pseudo modifier `optional` to
                    indicate a parameter that has a default value.
                """,
            )

        /** include-type-use-annotations=[yes|no] */
        val INCLUDE_TYPE_USE_ANNOTATIONS by
            booleanProperty(
                help =
                    """
                        Whether to include type-use annotations in the signature file. Type-use
                        annotations can only be included when `kotlin-name-type-order=true`, because
                        the Java order makes it ambiguous whether an annotation is type-use.
                    """,
            )

        /** kotlin-name-type-order=[yes|no] */
        val KOTLIN_NAME_TYPE_ORDER by
            booleanProperty(
                help =
                    """
                        Whether to order the names and types of APIs using Kotlin-style syntax
                        (`name: type`) or Java-style syntax (`type name`).

                        When Kotlin ordering is used, all method parameters without public names
                        will be given the placeholder name of `_`, which cannot be used as a Java
                        identifier.

                        For example, the following is an example of a method signature with Kotlin
                        ordering:
                        ```
                        method public foo(_: int, _: char, _: String[]): String;
                        ```

                        And the following is the equivalent Java ordering:
                        ```
                        method public String foo(int, char, String[]);
                        ```
                """,
            )

        /** kotlin-style-nulls=[yes|no] */
        val KOTLIN_STYLE_NULLS by
            booleanProperty(
                help =
                    """
                    If `no` then the signature file will use `@Nullable` and `@NonNull` annotations
                    to indicate that the annotated item accepts `null` and does not accept `null`
                    respectively and neither indicates that it's not defined.

                    If `yes` then the signature file will use a type suffix of `?`, no type suffix
                    and a type suffix of `!` to indicate the that the type accepts `null`, does not
                    accept `null` or it's not defined respectively.
                """,
            )

        val MIGRATING by
            optionalStringProperty(
                valueSyntax = "<reason>",
                help =
                    """
                        Indicates that the file format is being used to migrate a signature file to
                        fix a bug that causes a change in the signature file contents but not a
                        change in version.

                        e.g. This would be used when migrating a 2.0 file format that currently uses
                        source order for overloaded methods (using a command line parameter to
                        override the default order of signature) to a 2.0 file that uses signature
                        order.

                        This should be used to provide an explanation as to what is being migrated
                        and why. It should be relatively concise, e.g. something like:
                        ```
                        "See <short-url> for details"
                        ```

                        This value cannot use `,` (because it is a separator between properties in
                        [specifier]) or `\n` (because it is the terminator of the signature format
                        line).
                    """,
            )

        val NORMALIZE_ABSTRACT_MODIFIER by
            booleanProperty(
                defaultable = true,
                help =
                    """
                    Specifies how the `abstract` modifier is handled on `abstract` methods. If this
                    is `yes` and the method's containing class does not allow `abstract` then the
                    `abstract` modifier is not written out, otherwise it is.
                """,
            )

        val NORMALIZE_FINAL_MODIFIER by
            booleanProperty(
                defaultable = true,
                help =
                    """
                    Specifies how the `final` modifier is handled on `final` methods. If this is
                    `yes` and the method's containing class is `final` then the `final` modifier is
                    not written out, otherwise it is.
                """,
            )

        /** overloaded-method-other=[source|signature] */
        val OVERLOADED_METHOD_ORDER by
            enumProperty<OverloadedMethodOrder>(
                defaultable = true,
                defaultValue = OverloadedMethodOrder.SIGNATURE,
                help =
                    """
                    Specifies the order of overloaded methods in signature files. Applies to the
                    contents of the files specified on `--api` and `--removed-api`.

                    `source` - preserves the order in which overloaded methods appear in the source
                    files. This means that refactorings of the source files which change the order
                    but not the API can cause unnecessary changes in the API signature files.

                    `signature` (default) - sorts overloaded methods by their signature. This means
                    that refactorings of the source files which change the order but not the API
                    will have no effect on the API signature files.
                """,
            )

        val SORT_WHOLE_EXTENDS_LIST by
            booleanProperty(
                defaultable = true,
                help =
                    """
                        Indicates whether the whole extends list for an interface is sorted.

                        Previously, the first type in the extends list was used as the super type
                        and if it was present in the API then it would always be output first to the
                        signature files. The code has been refactored so that is no longer necessary
                        but the previous behavior is maintained to avoid churn in the API signature
                        files.

                        By default, this property preserves the previous behavior but if set to
                        `true` then it will stop treating the first interface specially and just
                        sort all the interface types. The sorting is by the full name (without the
                        package) of the class first then, by fully qualified name.
                    """,
            )

        val STRIP_JAVA_LANG_PREFIX by
            enumProperty<StripJavaLangPrefix>(
                defaultable = true,
                help =
                    """
                        Indicates which of the possible approaches to `java.lang.` prefix stripping
                        is used when outputting types to signature files. The default is `legacy`.

                        `legacy` - roughly only strips off the leading `java.lang.` prefix of a
                        type with a couple of exceptions. This is legacy behavior from when types
                        were treated as strings.

                        `never` - never strip off `java.lang.` prefixes.

                        `always` - always strip off `java.lang.` prefixes.

                        Note: This does not affect annotation names, e.g. `java.lang.SafeVarargs`.
                        They are always fully qualified.
                    """,
                defaultValue = StripJavaLangPrefix.LEGACY,
                // Ignore [StripJavaLangPrefix.VARARGS] as it is internal use only.
                entryFilter = { it != StripJavaLangPrefix.VARARGS }
            )

        val TYPE_ARGUMENT_SPACING by
            enumProperty<TypeArgumentSpacing>(
                defaultable = true,
                defaultValue = TypeArgumentSpacing.LEGACY,
                help =
                    """
                    Specifies the spacing between the type arguments of a generic type. e.g.
                    `Map<String, Integer>`. The default is `legacy`.

                    `legacy` - adds no spaces between type arguments except those used in the bounds
                    of a type parameter. e.g. `Map<String,Integer>` will have no space except in
                    `class Foo<M extends Map<String, Integer>`.

                    `none` - adds no spaces between any type arguments.

                    `space` - adds a single space between every type argument.

                    Note: This does not affect the spacing of type parameters in a type parameter
                    list, e.g. `interface Map<K, V>`. They always have a space separator.
                """,
            )

        /** The [List] of all [CustomizableProperty]s. */
        val entries = propertyList.toList()

        /** Map from [CustomizableProperty.propertyName] to [CustomizableProperty]. */
        private val byPropertyName = entries.associateBy { it.propertyName }

        /**
         * Get the [CustomizableProperty] by name, throwing an [ApiParseException] if it could not
         * be found.
         *
         * @param name the name of the property.
         * @param defaultableOnly if `true` then only [CustomizableProperty.defaultable] properties
         *   are allowed.
         */
        fun getByName(name: String, defaultableOnly: Boolean): CustomizableProperty<*> =
            byPropertyName[name]?.let { if (!defaultableOnly || it.defaultable) it else null }
                ?: let {
                    val possibilities =
                        byPropertyName
                            .filter { (_, property) -> !defaultableOnly || property.defaultable }
                            .keys
                            .sorted()
                            .joinToString("', '")
                    throw ApiParseException(
                        "unknown format property name `$name`, expected one of '$possibilities'"
                    )
                }
    }

    /** The name of the constant in [Companion]. */
    private lateinit var companionPropertyName: String

    /** The property name in the [parseSpecifier] input. */
    lateinit var propertyName: String
        private set

    /** Called to get the value of the delegating property; as this is the value just return it. */
    override fun getValue(thisRef: Companion, property: KProperty<*>) = this

    /**
     * Called once on creation to retrieve the property delegate.
     *
     * Initializes the name and adds a mapping from the name to this and then just returns this as
     * the delegate.
     *
     * @param thisRef the [CustomizableProperty.Companion] object.
     * @param property the property within [CustomizableProperty.Companion] for which this is a
     *   delegate, e.g. [NAME], [KOTLIN_STYLE_NULLS], etc.
     */
    operator fun provideDelegate(
        thisRef: Companion,
        property: KProperty<*>
    ): CustomizableProperty<T> {
        // Initialize property name based on the Companion property names.
        companionPropertyName = property.name
        propertyName = companionPropertyName.lowercase(Locale.US).replace("_", "-")
        propertyList.add(this)
        return this
    }

    /** Context for converting a string to a value. */
    internal class FromString(val propertyName: String, val string: String)

    /**
     * Set the corresponding property in the supplied [Builder] to the value corresponding to the
     * string representation [string].
     */
    internal fun setFromString(mutablePropertyMap: MutablePropertyMap, string: String) {
        val value = FromString(propertyName, string).stringToValue()
        mutablePropertyMap[this] = value
    }

    override fun toString() = companionPropertyName
}

/**
 * Base of [PropertyMap] and [MutablePropertyMap].
 *
 * Provides behavior common to both.
 */
abstract class BasePropertyMap : Iterable<CustomizableProperty<*>> {
    /** Iterate over all the properties in this map. */
    abstract override fun iterator(): Iterator<CustomizableProperty<*>>

    /** Check to see if this is empty. */
    abstract fun isEmpty(): Boolean

    /** Check to see if this is not empty. */
    fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Get the value of [property] as [T]
     *
     * This will NOT apply any defaults.
     */
    abstract operator fun <T> get(property: CustomizableProperty<T>): T?

    /** Get the string representation of [property]'s value in this [BasePropertyMap]. */
    private fun <T> propertyAsString(property: CustomizableProperty<T>) =
        this[property]?.let { property.valueToString(it) }

    override fun toString() = buildString {
        append('{')
        var separator = ""
        for (property in CustomizableProperty.entries) {
            val valueAsString = propertyAsString(property) ?: continue
            append(separator)
            append(property.propertyName)
            append('=')
            append(valueAsString)
            separator = ", "
        }
        append('}')
    }
}

/** A type safe map from [CustomizableProperty] to a value of the appropriate type. */
class PropertyMap
internal constructor(
    /**
     * Map from [CustomizableProperty] to `Any?`.
     *
     * This is actually type safe as it can only be populated by [MutablePropertyMap.set] and that
     * ensures that only values compatible with the [CustomizableProperty] key are stored with it.
     */
    private val map: Map<CustomizableProperty<*>, Any?> = emptyMap(),
) : BasePropertyMap() {
    override fun iterator() = map.keys.iterator()

    override fun isEmpty() = map.isEmpty()

    @Suppress("UNCHECKED_CAST")
    override operator fun <T> get(property: CustomizableProperty<T>): T? = map[property] as T?

    /** Convert this to a [MutablePropertyMap]. */
    fun toMutableMap() = MutablePropertyMap(map.toMutableMap())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PropertyMap

        return map == other.map
    }

    override fun hashCode() = map.hashCode()

    companion object {
        internal val EMPTY = PropertyMap(emptyMap())
    }
}

/** An empty [PropertyMap]. */
fun emptyPropertyMap() = PropertyMap.EMPTY

/** A type safe mutable map from [CustomizableProperty] to a value of the appropriate type. */
class MutablePropertyMap
@PublishedApi
internal constructor(
    /**
     * Map from [CustomizableProperty] to `Any?`.
     *
     * This is actually type safe as it can only be populated by [MutablePropertyMap.set] and that
     * ensures that only values compatible with the [CustomizableProperty] key are stored with it.
     */
    private val map: MutableMap<CustomizableProperty<*>, Any?> = mutableMapOf(),
) : BasePropertyMap() {
    override fun iterator() = map.keys.iterator()

    override fun isEmpty() = map.isEmpty()

    @Suppress("UNCHECKED_CAST")
    override operator fun <T> get(property: CustomizableProperty<T>): T? = map[property] as T?

    /** Set [property] to [value]. */
    operator fun <T> set(property: CustomizableProperty<T>, value: T) {
        map[property] = value
    }

    /**
     * Parse a property assignment of the form `property=value`, updating the appropriate property
     * in this [Builder], or throwing an exception if there was a problem.
     *
     * @param assignment the string of the form `property=value`.
     * @param defaultableOnly if `true` then only [CustomizableProperty.defaultable] properties are
     *   allowed.
     */
    internal fun setPropertyFromAssignment(
        assignment: String,
        defaultableOnly: Boolean = false,
    ) {
        val propertyParts = assignment.split("=")
        if (propertyParts.size != 2) {
            throw ApiParseException("expected <property>=<value> but found '$assignment'")
        }
        val name = propertyParts[0]
        val value = propertyParts[1]
        val customizable = CustomizableProperty.getByName(name, defaultableOnly)
        customizable.setFromString(this, value)
    }

    /** Apply [property] value from [other] if it is not set in this and is set in [other]. */
    private fun <T> applyDefaultFromOther(
        other: BasePropertyMap,
        property: CustomizableProperty<T>
    ) {
        this[property]?.let {
            return
        }
        copyFromOther(other, property)
    }

    /** Apply any property values set in [other] that are not set in this. */
    internal fun applyDefaultsFromOther(other: BasePropertyMap) {
        for (property in other) {
            applyDefaultFromOther(other, property)
        }
    }

    /** Copy [property] value from [other] if it is set in [other]. */
    private fun <T> copyFromOther(other: BasePropertyMap, property: CustomizableProperty<T>) {
        val value = other[property] ?: return
        this[property] = value
    }

    /** Copy any property values set in [other]. */
    internal fun copyFromOther(other: BasePropertyMap) {
        for (property in other) {
            copyFromOther(other, property)
        }
    }

    /** Convert this to an immutable [PropertyMap]. */
    fun toMap() = PropertyMap(map.toMap())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MutablePropertyMap

        return map == other.map
    }

    override fun hashCode() = map.hashCode()
}

/** Create a [MutablePropertyMap]. */
fun mutablePropertyMap() = MutablePropertyMap()

/**
 * Build a [PropertyMap] by applying [builder] to a [MutablePropertyMap] and returning
 * [MutablePropertyMap.toMap].
 */
inline fun buildPropertyMap(builder: MutablePropertyMap.() -> Unit) =
    MutablePropertyMap().apply(builder).toMap()
