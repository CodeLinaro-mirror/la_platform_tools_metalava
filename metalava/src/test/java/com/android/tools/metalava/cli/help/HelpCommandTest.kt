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

import com.android.tools.metalava.cli.common.BaseCommandTest
import org.junit.Test

class HelpCommandTest : BaseCommandTest<HelpCommand>({ HelpCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("help")

            expectedStdout =
                """
Usage: metalava help <concept>...

  Provides help for general metalava concepts.

Concepts
  issues                                     Provides help related to issues and issue reporting
  package-filters                            Explains the syntax and behavior of package filters used in options like
                                             --stub-packages.
  signature-file-formats                     Describes the different signature file formats.
  historical-api-patterns                    Explains the syntax and behavior of historical API patterns used in options
                                             like --android-jar-pattern.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test help package-filters`() {
        commandTest {
            args += listOf("help", "package-filters")

            expectedStdout =
                """
Usage: metalava help package-filters

  Explains the syntax and behavior of package filters used in options like --stub-packages.

  A package filter is specified as a sequence of package matchers, separated by `:`. A matcher consists of an option
  leading `+` or `-` following by a pattern. If `-` is specified then it will exclude all packages that match the
  pattern, otherwise (i.e. with `+` or without either) it will include all packages that match the pattern. If a package
  is matched by multiple matchers then the last one wins.

  Patterns can be one of the following:

  `*` - match every package.

  `<package>` - an exact match, e.g. `foo` will only match `foo` and `foo.bar` will only match `foo.bar`.

  `<package>*` - a prefix match, e.g. `foo*` will match `foo` and `foobar` and `foo.bar`.

  `<package>.*` - a recursive match, will match `<package>` and any nested packages, e.g. `foo.*` will match `foo` and
  `foo.bar` and `foo.bar.baz` but not `foobar`.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test help signature-file-formats`() {
        commandTest {
            args += listOf("help", "signature-file-formats")

            expectedStdout =
                """
Usage: metalava help signature-file-formats

  Describes the different signature file formats.

  See `FORMAT.md` in the top level metalava directory for more information.

  Conceptually, a signature file format is a set of properties that determine the types of information that will be
  output to the API signature file and how it is represented. A format version is simply a set of defaults for those
  properties.

  The supported properties are:

  * `concise-default-values = yes|no` - If `no` then the signature file will use `@Nullable` and `@NonNull` annotations
  to indicate that the annotated item accepts `null` and does not accept `null` respectively and neither indicates that
  it's not defined.

  * `kotlin-style-nulls = yes|no` - If `no` then the signature file will use `@Nullable` and `@NonNull` annotations to
  indicate that the annotated item accepts `null` and does not accept `null` respectively and neither indicates that
  it's not defined.

  If `yes` then the signature file will use a type suffix of `?`, no type suffix and a type suffix of `!` to indicate
  the that the type accepts `null`, does not accept `null` or it's not defined respectively.

  Plus the following properties which can have their default changed using the `--format-defaults` option.

  * `normalize-final-modifier = yes|no` - Specifies how the `final` modifier is handled on `final` methods. If this is
  `yes` and the method's containing class is `final` then the `final` modifier is not written out, otherwise it is.

  * `overloaded-method-order = source|signature` - Specifies the order of overloaded methods in signature files. Applies
  to the contents of the files specified on `--api` and `--removed-api`.

  `source` - preserves the order in which overloaded methods appear in the source files. This means that refactorings of
  the source files which change the order but not the API can cause unnecessary changes in the API signature files.

  `signature` (default) - sorts overloaded methods by their signature. This means that refactorings of the source files
  which change the order but not the API will have no effect on the API signature files.

  * `type-argument-spacing = legacy|none|space` - Specifies the spacing between the type arguments of a generic type.
  e.g. `Map<String, Integer>`. The default is `legacy`.

  `legacy` - adds no spaces between type arguments except those used in the bounds of a type parameter. e.g.
  `Map<String,Integer>` will have no space except in `class Foo<M extends Map<String, Integer>`.

  `none` - adds no spaces between any type arguments.

  `space` - adds a single space between every type argument.

  Note: This does not affect the spacing of type parameters in a type parameter list, e.g. `interface Map<K, V>`. They
  always have a space separator.

  Currently, metalava supports the following versions:

  * `2.0` (--format=v2) - This is the base version (more details in `FORMAT.md`) on which all the others are based. It
  sets the properties as follows:

  + kotlin-style-nulls = no
  + concise-default-values = no

  * `3.0` (--format=v3) - This is `2.0` plus `kotlin-style-nulls = yes` giving the following properties:

  + kotlin-style-nulls = yes
  + concise-default-values = no

  * `4.0` (--format=v4) - This is `3.0` plus `concise-default-values = yes` giving the following properties:

  + kotlin-style-nulls = yes
  + concise-default-values = yes

  * `5.0` - This is the first version that has full support for properties in the signature header. As such it does not
  add any new defaults to `4.0`. The intent is that properties will be explicitly defined in the signature file avoiding
  reliance on version specific defaults.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test help historical-api-patterns`() {
        commandTest {
            args += listOf("help", "historical-api-patterns")

            expectedStdout =
                """
Usage: metalava help historical-api-patterns

  Explains the syntax and behavior of historical API patterns used in options like --android-jar-pattern.

  A historical API pattern is used to find historical versioned API files that are used to construct a history of an API
  surface, e.g. when items were added, removed, deprecated, etc.. It allows for efficiently scanning a directory for
  matching files, or matching a given file. In both cases information is extracted from the file path, e.g. version,
  that is used when constructing the API history.

  Each pattern contains placeholders which match part of a file name, extracts the value, possibly filters it and then
  stores it in a property. Each property can have at most a single associated placeholder in each pattern.

  A `version` placeholder is mandatory but the other options are optional. Files that match a pattern are assumed to
  provide the definition of that version of the API. e.g. given a pattern of
  `prebuilts/sdk/{version:level}/public/android.jar` then it will match a file like `prebuilts/sdk/1/public/android.jar`
  and that file is assumed to define version 1 of the API.

  Patterns can also include any number of wildcards:

  * `*` - matches any characters within a file name, but not into sub-directories. e.g. `foo/b*h/bar` will match
  `foo/blah/bar` but not `foo/blah/blah/bar`.

  The supported properties are:

  * `version` - Mandatory property that stores the version of a matched file.

  Apart from the {version:extension} all placeholders for this will ignore versions that fall outside the range
  --first-version and --current-version, if provided.

  * `module` - Optional property that stores the name of the SDK extension module.

  Patterns that use a placeholder for this are assumed to be matching files for SDK extensions.

  * `surface` - Optional property that stores the API surface.

  The supported placeholders are:

  * `{version:level}` - Placeholder for property `version`. Matches a single non-negative integer and treats it as an
  API version.

  * `{version:major.minor?}` - Placeholder for property `version`. Matches a single non-negative integer or two such
  integers separated by a `.`.

  * `{version:major.minor.patch}` - Placeholder for property `version`. Matches three non-negative integers separated by
  `.`s.

  * `{version:extension}` - Placeholder for property `version`. Matches a single non-negative integer and treats it as
  an extension version.

  A pattern that includes this must also include `{module}` as SDK extension APIs are stored in a file per extension
  module.

  * `{module}` - Placeholder for property `module`. Matches a module name which must consist of lower case letters,
  hyphens and `.`s.

  * `{surface}` - Placeholder for property `surface`. Matches a surface name which must consist of lower case letters
  and hyphens.
                """
                    .trimIndent()
        }
    }
}
