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
  api-surfaces                               Explains what API surfaces are and how to specify them.
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

  * `add-additional-overrides = yes|no` - If `yes` then add additional overrides into the signature file that are needed
  in order to create compilable stubs from the signature file.

  * `flagged-api-inheritance = none|nested-classes` - Specifies whether `@FlaggedApi` annotations are inherited in
  signature files.

  `none` (default) - they are not inherited. This can make it difficult to determine whether a nested class is flagged
  when reviewin as the containing class may be out of view or even in another file altogether.

  `nested-classes` - they are inherited onto nested classes that do not have their own `@FlaggedApi` annotation.

  * `include-default-parameter-values = yes|no` - If `no` then the signature file will not include any information about
  default parameter values. If `yes` then it will use the pseudo modifier `optional` to indicate a parameter that has a
  default value.

  * `include-type-use-annotations = yes|no` - Whether to include type-use annotations in the signature file. Type-use
  annotations can only be included when `kotlin-name-type-order=true`, because the Java order makes it ambiguous whether
  an annotation is type-use.

  * `java-record-classes = yes|no` - Whether to include java record classes in the signature file.

  If `yes` then the signature file will include `record` class type keyword and property items representing the record
  components, along with the constructor and methods. If `no` then record classes will be represented as normal classes
  without any properties but sill with the same constructor and methods.

  * `java-sealed-classes = yes|no` - Whether to include java sealed classes in the signature file.

  If `yes` then the signature file will include `sealed`, and `non-sealed` modifiers, and `permits` list. If `no` then
  they will not be included but it is not clear how that would work.

  * `kotlin-name-type-order = yes|no` - Whether to order the names and types of APIs using Kotlin-style syntax (`name:
  type`) or Java-style syntax (`type name`).

  When Kotlin ordering is used, all method parameters without public names will be given the placeholder name of `_`,
  which cannot be used as a Java identifier.

  For example, the following is an example of a method signature with Kotlin ordering:

  method public foo(_: int, _: char, _: String[]): String;

  And the following is the equivalent Java ordering:

  method public String foo(int, char, String[]);

  * `kotlin-style-nulls = yes|no` - If `no` then the signature file will use `@Nullable` and `@NonNull` annotations to
  indicate that the annotated item accepts `null` and does not accept `null` respectively and neither indicates that
  it's not defined.

  If `yes` then the signature file will use a type suffix of `?`, no type suffix and a type suffix of `!` to indicate
  the that the type accepts `null`, does not accept `null` or it's not defined respectively.

  * `migrating = <reason>` - Indicates that the file format is being used to migrate a signature file to fix a bug that
  causes a change in the signature file contents but not a change in version.

  e.g. This would be used when migrating a 2.0 file format that currently uses source order for overloaded methods
  (using a command line parameter to override the default order of signature) to a 2.0 file that uses signature order.

  This should be used to provide an explanation as to what is being migrated and why. It should be relatively concise,
  e.g. something like:

  "See <short-url> for details"

  This value cannot use `,` (because it is a separator between properties in [specifier]) or `\n` (because it is the
  terminator of the signature format line).

  * `name = <identifier>` - Specifies the name of the API.

  It must start with a lower case letter, contain any number of lower case letters, numbers and hyphens, and end with
  either a lowercase letter or number.

  Its purpose is to provide information to metalava and to a lesser extent the owner of the file about which API the
  file contains. The exact meaning of the API name is determined by the owner, metalava simply uses this as an
  identifier for comparison.

  * `normalize-abstract-modifier = yes|no` - Specifies how the `abstract` modifier is handled on `abstract` methods. If
  this is `yes` and the method's containing class does not allow `abstract` then the `abstract` modifier is not written
  out, otherwise it is.

  * `normalize-final-modifier = yes|no` - Specifies how the `final` modifier is handled on `final` methods. If this is
  `yes` and the method's containing class is `final` then the `final` modifier is not written out, otherwise it is.

  * `overloaded-method-order = source|signature` - Specifies the order of overloaded methods in signature files. Applies
  to the contents of the files specified on `--api` and `--removed-api`.

  `source` - preserves the order in which overloaded methods appear in the source files. This means that refactorings of
  the source files which change the order but not the API can cause unnecessary changes in the API signature files.

  `signature` (default) - sorts overloaded methods by their signature. This means that refactorings of the source files
  which change the order but not the API will have no effect on the API signature files.

  * `sort-whole-extends-list = yes|no` - Indicates whether the whole extends list for an interface is sorted.

  Previously, the first type in the extends list was used as the super type and if it was present in the API then it
  would always be output first to the signature files. The code has been refactored so that is no longer necessary but
  the previous behavior is maintained to avoid churn in the API signature files.

  By default, this property preserves the previous behavior but if set to `true` then it will stop treating the first
  interface specially and just sort all the interface types. The sorting is by the full name (without the package) of
  the class first then, by fully qualified name.

  * `strip-java-lang-prefix = legacy|never|always` - Indicates which of the possible approaches to `java.lang.` prefix
  stripping is used when outputting types to signature files. The default is `legacy`.

  `legacy` - roughly only strips off the leading `java.lang.` prefix of a type with a couple of exceptions. This is
  legacy behavior from when types were treated as strings.

  `never` - never strip off `java.lang.` prefixes.

  `always` - always strip off `java.lang.` prefixes.

  Note: This does not affect annotation names, e.g. `java.lang.SafeVarargs`. They are always fully qualified.

  * `style = java|kotlin` - The name of a predefined set of properties to apply as defaults. They override version
  defaults but are themselves overridden by properties listed in the file.

  * `surface = <identifier>` - Specifies the name of the API surface.

  It must start with a lower case letter, contain any number of lower case letters, numbers and hyphens, and end with
  either a lowercase letter or number.

  Its purpose is to provide information to metalava and to a lesser extent the owner of the file about which API surface
  the file contains. The exact meaning of the API surface name is determined by the owner, metalava simply uses this as
  an identifier for comparison.

  * `type-argument-spacing = legacy|none|space` - Specifies the spacing between the type arguments of a generic type.
  e.g. `Map<String, Integer>`. The default is `legacy`.

  `legacy` - adds no spaces between type arguments except those used in the bounds of a type parameter. e.g.
  `Map<String,Integer>` will have no space except in `class Foo<M extends Map<String, Integer>`.

  `none` - adds no spaces between any type arguments.

  `space` - adds a single space between every type argument.

  Note: This does not affect the spacing of type parameters in a type parameter list, e.g. `interface Map<K, V>`. They
  always have a space separator.

  Currently, metalava supports the following versions:

  * `2.0` - This is the base version for all the others.

  * `4.0` - Introduced support for improved Kotlin tracking. This is `2.0` plus the following properties:

  + include-default-parameter-values = yes
  + kotlin-style-nulls = yes

  * `5.0` - This is the first version that has full support for properties in the signature header. As such it does not
  add any new defaults to `4.0`. The intent is that properties will be explicitly defined in the signature file avoiding
  reliance on version specific defaults.

  * `6.0` - Provides support for sealed and record classes.

  Also, provides defaults for lots of formatting properties to ensure consistent formatting. This is `5.0` plus the
  following properties:

  + add-additional-overrides = yes
  + flagged-api-inheritance = nested-classes
  + java-record-classes = yes
  + normalize-abstract-modifier = yes
  + normalize-final-modifier = yes
  + overloaded-method-order = signature
  + sort-whole-extends-list = yes
  + strip-java-lang-prefix = always
  + type-argument-spacing = space
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
  --api-version-range, if provided.

  * `library` - Optional property that stores the name of a library.

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

  * `{library}` - Placeholder for property `library`. Matches a library name which must consist of lower case letters,
  hyphens and `.`s.

  * `{module}` - Placeholder for property `module`. Matches a module name which must consist of lower case letters,
  hyphens and `.`s.

  * `{surface}` - Placeholder for property `surface`. Matches a surface name which must consist of lower case letters
  and hyphens.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test help api-surfaces`() {
        commandTest {
            args += listOf("help", "api-surfaces")

            expectedStdout =
                """
Usage: metalava help api-surfaces

  Explains what API surfaces are and how to specify them.

  An API surface is a defined subset of the complete API of a library or platform, tailored for a specific set of
  consumers. For example, a platform might expose a public API surface for general app developers, and a broader system
  API surface for system components.

  Using a configuration file allows you to define a hierarchy of API surfaces, specifying what is included in or
  excluded from each surface based on annotations.

  To use API surfaces, you must:

  1. Define the API surfaces in a configuration XML file.

  2. Pass the configuration file to Metalava using the --config-file option.

  3. Select the target API surface to generate using the --api-surface option.

  ### Configuration File Format

  The configuration file contains an `<api-surfaces>` element which encloses one or more `<api-surface>` elements, and
  optional `<doc-only>` and `<removed>` elements.

  An `<api-surface>` element has the following attributes:

  * `name` (required) - The name of the API surface (e.g., `public`, `system`).

  * `extends` (optional) - The name of a parent API surface that this surface extends. When a surface extends another,
  it includes all the items in the parent surface.

  * `contents` (optional) - Either `delta` or `standalone`.

  `delta` (default) - The signature file generated for this surface will only contain the differences (delta) between
  this surface and the surface it extends.

  `standalone` - The signature file will contain the entire API for this surface (including everything in the extended
  parent surfaces).

  Within an `<api-surface>`, the `<selection-criteria>` element determines which code elements are included in the
  surface. It supports:

  * `unannotated` attribute - Set to `show` to include all unannotated public elements in the surface. Set to `hide` (or
  omit) to exclude them.

  * `<annotation-rule>` elements -

  * `pattern` (required) - A pattern matching the annotation (e.g., `android.annotation.SystemApi`).

  * `effect` (optional) - Either `show` (default) or `hide`.

  * `recursive` (optional) - Either `true` (default) or `false`. If `true`, the rule also applies to nested/enclosed
  items.

  The `<doc-only>` and `<removed>` sections allow configuring annotations that mark items as documentation-only or
  removed, respectively.

  * `<doc-only>` - Elements annotated with these are excluded from stub jar files but kept in the signature
  file/documentation.

  * `<removed>` - Elements annotated with these are excluded from signature files and stubs.

  ### Converting Legacy Options to API Surfaces

  Previously, API surfaces were selected using --show-annotation, --show-unannotated, and --hide-annotation. These
  options are now deprecated in favor of using a configuration file. Here is how to convert common legacy options:

  #### 1. Public API

  If you previously used:

  --show-unannotated

  This corresponds to a single "public" API surface that includes unannotated APIs:

  <config xmlns="http://www.google.com/tools/metalava/config">
      <api-surfaces>
          <api-surface name="public">
              <selection-criteria unannotated="show"/>
          </api-surface>
      </api-surfaces>
  </config>

  To run Metalava:

  metalava --config-file config.xml --api-surface public

  #### 2. System API (extends Public API)

  If you previously used:

  --show-annotation android.annotation.SystemApi

  This is typically a "system" API surface that extends the "public" surface. The public surface shows unannotated
  items, and the system surface shows items annotated with `@SystemApi`:

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

  To run Metalava:

  metalava --config-file config.xml --api-surface system

  #### 3. Standalone API with Show and Hide Annotations

  If you previously used:

  --show-annotation my.custom.Api --hide-annotation my.custom.Hide

  And did not want to include unannotated public APIs:

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

  To run Metalava:

  metalava --config-file config.xml --api-surface custom
                """
                    .trimIndent()
        }
    }
}
