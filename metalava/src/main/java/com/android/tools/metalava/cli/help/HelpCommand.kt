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

package com.android.tools.metalava.cli.help

import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.ARG_HIDE_ANNOTATION
import com.android.tools.metalava.ARG_SHOW_ANNOTATION
import com.android.tools.metalava.ARG_SHOW_UNANNOTATED
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.cli.common.ARG_STUB_PACKAGES
import com.android.tools.metalava.cli.common.MetalavaHelpFormatter
import com.android.tools.metalava.cli.common.buildDefinitionListHelp
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.common.terminal
import com.android.tools.metalava.model.text.CustomizableProperty
import com.android.tools.metalava.model.text.FileFormat
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.Localization

class HelpCommand :
    CliktCommand(
        help = "Provides help for general metalava concepts.",
        invokeWithoutSubcommand = true,
    ) {

    init {
        context {
            localization =
                object : Localization {
                    override fun commandsTitle(): String {
                        return "Concepts"
                    }

                    override fun commandMetavar(): String {
                        return "<concept>..."
                    }
                }

            helpFormatter = MetalavaHelpFormatter(this@HelpCommand::terminal, localization)

            // Help options make no sense on a help command.
            helpOptionNames = emptySet()
        }
        subcommands(
            IssuesCommand(),
            packageFilterHelp,
            signatureFileFormatsHelp(),
            historicalApiPatterns(),
            apiSurfacesHelp,
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            stdout.println(getFormattedHelp())
        }
    }
}

private val packageFilterHelp =
    SimpleHelpCommand(
        name = "package-filters",
        help =
            """
Explains the syntax and behavior of package filters used in options like $ARG_STUB_PACKAGES.

A package filter is specified as a sequence of package matchers, separated by `:`. A matcher
consists of an option leading `+` or `-` following by a pattern. If `-` is specified then it will
exclude all packages that match the pattern, otherwise (i.e. with `+` or without either) it will
include all packages that match the pattern. If a package is matched by multiple matchers then the
last one wins.

Patterns can be one of the following:

`*` - match every package.

`<package>` - an exact match, e.g. `foo` will only match `foo` and `foo.bar` will only match
`foo.bar`.

`<package>*` - a prefix match, e.g. `foo*` will match `foo` and `foobar` and `foo.bar`.

`<package>.*` - a recursive match, will match `<package>` and any nested packages, e.g. `foo.*`
will match `foo` and `foo.bar` and `foo.bar.baz` but not `foobar`.
            """
                .trimIndent()
    )

/**
 * Append a line to this [StringBuilder] describing the [change], the [property] and its [value]
 * between a [FileFormat.Version] and its base [FileFormat.Version]. if any.
 */
private fun <T> StringBuilder.appendPropertyLine(
    change: String,
    property: CustomizableProperty<T>,
    value: T & Any,
) {
    append(change)
    append(property.propertyName)
    append(" = ")
    append(property.valueToString(value))
    append("\n")
}

/**
 * Append a description to this [StringBuilder] of the delta between [baseDefaults] and [version].
 */
private fun <T> StringBuilder.appendPropertyDeltaDescription(
    version: FileFormat.Version,
    property: CustomizableProperty<T>,
    baseDefaults: FileFormat?
) {
    val value = version.defaults.propertyMap[property]
    val baseValue = baseDefaults?.propertyMap?.get(property)
    if (baseValue == value) return

    if (baseValue != null) {
        appendPropertyLine("+ ", property, baseValue)
    }
    if (value != null) {
        appendPropertyLine("+ ", property, value)
    }
}

private val sortedCustomizableProperties = CustomizableProperty.entries.sortedBy { it.propertyName }

/**
 * Construct the help for [version].
 *
 * Automatically describes the delta between this version and its predecessor.
 */
fun constructVersionHelp(version: FileFormat.Version): String = buildString {
    append(version.help.trimIndent())
    val baseVersion = version.baseVersion
    val delta = buildString {
        val baseDefaults = baseVersion?.defaults
        for (property in sortedCustomizableProperties) {
            appendPropertyDeltaDescription(version, property, baseDefaults)
        }
    }
    if (delta.isNotEmpty()) {
        append("\n")
        append("This is `${baseVersion!!.versionNumber}` plus the following properties:\n")
        append("```\n")
        append(delta)
        append("```\n")
    }
}

private fun signatureFileFormatsHelp(): CliktCommand {

    /** Construct help for the different [FileFormat.Version]s. */
    fun versionHelp(): String {
        /** Generate a label for a [FileFormat.Version]. */
        fun FileFormat.Version.labelGetter() = buildString {
            append('`')
            append(versionNumber)
            append('`')
        }

        return buildDefinitionListHelp(
            FileFormat.versions.map { it.labelGetter() to constructVersionHelp(it) },
            termPrefix = "* ",
        )
    }

    /**
     * Construct help for the different [CustomizableProperty]s.
     *
     * @param filter filter the properties for which help will be provided.
     */
    fun customizablePropertyHelp(): String {
        fun CustomizableProperty<*>.labelGetter() = "`$propertyName = $valueSyntax`"
        return buildDefinitionListHelp(
            sortedCustomizableProperties.map { property ->
                val help = property.help
                if (help == "") error("No help provided for $property")
                property.labelGetter() to help.trimIndent()
            },
            termPrefix = "* ",
        )
    }

    return SimpleHelpCommand(
        name = "signature-file-formats",
        help =
            """
Describes the different signature file formats.

See `FORMAT.md` in the top level metalava directory for more information.

Conceptually, a signature file format is a set of properties that determine the types of information
that will be output to the API signature file and how it is represented. A format version is simply
a set of defaults for those properties.

The supported properties are:
${customizablePropertyHelp()}

Currently, metalava supports the following versions:
${versionHelp()}
            """
                .trimIndent()
    )
}

private fun historicalApiPatterns(): CliktCommand {
    /** Construct help for the different [PatternNode.Placeholder]s. */
    fun placeholderHelp(): String {
        fun PatternNode.Placeholder.labelGetter() = "`$label`"
        return buildDefinitionListHelp(
            PatternNode.Placeholder.entries.mapNotNull {
                val help = it.help()
                if (help == "") return@mapNotNull null
                it.labelGetter() to
                    "Placeholder for property `${it.property}`. ${help.trimIndent()}"
            },
            termPrefix = "* ",
        )
    }

    /** Construct help for the different [PatternNode.Property]s. */
    fun propertyHelp(): String {
        fun PatternNode.Property.labelGetter() = "`$propertyName`"
        return buildDefinitionListHelp(
            PatternNode.Property.entries.mapNotNull {
                val help = it.help()
                if (help == "") return@mapNotNull null
                it.labelGetter() to help.trimIndent()
            },
            termPrefix = "* ",
        )
    }

    return SimpleHelpCommand(
        name = "historical-api-patterns",
        help =
            """
Explains the syntax and behavior of historical API patterns used in options like $ARG_ANDROID_JAR_PATTERN.

A historical API pattern is used to find historical versioned API files that are used to construct a
history of an API surface, e.g. when items were added, removed, deprecated, etc.. It allows for
efficiently scanning a directory for matching files, or matching a given file. In both cases
information is extracted from the file path, e.g. version, that is used when constructing the API
history.

Each pattern contains placeholders which match part of a file name, extracts the value, possibly
filters it and then stores it in a property. Each property can have at most a single associated
placeholder in each pattern.

A `version` placeholder is mandatory but the other options are optional. Files that match a
pattern are assumed to provide the definition of that version of the API. e.g. given a pattern of
`prebuilts/sdk/{version:level}/public/android.jar` then it will match a file like
`prebuilts/sdk/1/public/android.jar` and that file is assumed to define version 1 of the API.

Patterns can also include any number of wildcards:

* `*` - matches any characters within  a file name, but not into sub-directories. e.g. `foo/b*h/bar`
  will match `foo/blah/bar` but not `foo/blah/blah/bar`.

The supported properties are:
${propertyHelp()}

The supported placeholders are:
${placeholderHelp()}
            """
                .trimIndent()
    )
}

private val apiSurfacesHelp =
    SimpleHelpCommand(
        name = "api-surfaces",
        help =
            """
Explains what API surfaces are and how to specify them.

An API surface is a defined subset of the complete API of a library or platform, tailored for a
specific set of consumers. For example, a platform might expose a public API surface for general app
developers, and a broader system API surface for system components.

Using a configuration file allows you to define a hierarchy of API surfaces, specifying what is
included in or excluded from each surface based on annotations.

To use API surfaces, you must:

1. Define the API surfaces in a configuration XML file.

2. Pass the configuration file to Metalava using the $ARG_CONFIG_FILE option.

3. Select the target API surface to generate using the $ARG_API_SURFACE option.


### Configuration File Format

The configuration file contains an `<api-surfaces>` element which encloses one or more
`<api-surface>` elements, and optional `<doc-only>` and `<removed>` elements.

An `<api-surface>` element has the following attributes:

* `name` (required) - The name of the API surface (e.g., `public`, `system`).

* `extends` (optional) - The name of a parent API surface that this surface extends. When a
  surface extends another, it includes all the items in the parent surface.

* `contents` (optional) - Either `delta` or `standalone`.

  `delta` (default) - The signature file generated for this surface will only contain the
  differences (delta) between this surface and the surface it extends.

  `standalone` - The signature file will contain the entire API for this surface (including
  everything in the extended parent surfaces).

Within an `<api-surface>`, the `<selection-criteria>` element determines which code elements
are included in the surface. It supports:

* `unannotated` attribute - Set to `show` to include all unannotated public elements in the
  surface. Set to `hide` (or omit) to exclude them.

* `<annotation-rule>` elements -

  * `pattern` (required) - A pattern matching the annotation (e.g., `android.annotation.SystemApi`).

  * `effect` (optional) - Either `show` (default) or `hide`.

  * `recursive` (optional) - Either `true` (default) or `false`. If `true`, the rule also
    applies to nested/enclosed items.

The `<doc-only>` and `<removed>` sections allow configuring annotations that mark items as
documentation-only or removed, respectively.

* `<doc-only>` - Elements annotated with these are excluded from stub jar files but kept in
  the signature file/documentation.

* `<removed>` - Elements annotated with these are excluded from signature files and stubs.


### Converting Legacy Options to API Surfaces

Previously, API surfaces were selected using $ARG_SHOW_ANNOTATION, $ARG_SHOW_UNANNOTATED,
and $ARG_HIDE_ANNOTATION. These options are now deprecated in favor of using a configuration
file. Here is how to convert common legacy options:


#### 1. Public API

If you previously used:

```
$ARG_SHOW_UNANNOTATED
```

This corresponds to a single "public" API surface that includes unannotated APIs:

```
<config xmlns="http://www.google.com/tools/metalava/config">
    <api-surfaces>
        <api-surface name="public">
            <selection-criteria unannotated="show"/>
        </api-surface>
    </api-surfaces>
</config>
```

To run Metalava:

```
metalava $ARG_CONFIG_FILE config.xml $ARG_API_SURFACE public
```


#### 2. System API (extends Public API)

If you previously used:

```
$ARG_SHOW_ANNOTATION android.annotation.SystemApi
```

This is typically a "system" API surface that extends the "public" surface. The public surface
shows unannotated items, and the system surface shows items annotated with `@SystemApi`:

```
<config xmlns="http://www.google.com/tools/metalava/config">
    <api-surfaces>
        <api-surface name="public">
            <selection-criteria unannotated="show"/>
        </api-surface>
        <api-surface name="system" extends="public">
            <selection-criteria>
                <annotation-rule pattern="android.annotation.SystemApi"/>
            </selection-criteria>
        </api-surface>
    </api-surfaces>
</config>
```

To run Metalava:

```
metalava $ARG_CONFIG_FILE config.xml $ARG_API_SURFACE system
```


#### 3. Standalone API with Show and Hide Annotations

If you previously used:

```
$ARG_SHOW_ANNOTATION my.custom.Api $ARG_HIDE_ANNOTATION my.custom.Hide
```

And did not want to include unannotated public APIs:

```
<config xmlns="http://www.google.com/tools/metalava/config">
    <api-surfaces>
        <api-surface name="custom">
            <selection-criteria unannotated="hide">
                <annotation-rule pattern="my.custom.Api" effect="show"/>
                <annotation-rule pattern="my.custom.Hide" effect="hide"/>
            </selection-criteria>
        </api-surface>
    </api-surfaces>
</config>
```

To run Metalava:

```
metalava $ARG_CONFIG_FILE config.xml $ARG_API_SURFACE custom
```
            """
                .trimIndent()
    )
