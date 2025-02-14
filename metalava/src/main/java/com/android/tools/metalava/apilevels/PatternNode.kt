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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.model.api.surface.ApiSurface
import java.io.File
import java.util.TreeSet

/**
 * A node in a tree of path patterns used to select historical API files.
 *
 * e.g. the nodes of `prebuilts/sdk/{version:level}/public/android.jar` would be:
 * 1. The root element.
 * 2. `prebuilts`
 * 3. `sdk`
 * 4. `{version:level}` - a wild card representing any numbered directory.
 * 5. `public`
 * 6. `android.jar`
 *
 * Where each node is the child of the preceding node.
 *
 * If two or more patterns had matching nodes, then they will share the nodes. e.g.
 * `prebuilts/sdk/{version:level}/system/android.jar` would share the first 4 nodes with above
 * followed by two more nodes `system` and `android.jar`.
 *
 * These will be used to either find matching files in the file system by scanning through matching
 * directories or determine whether a specific file path that is passed in matches the pattern. In
 * either case this will also be used to extract information from the path, e.g. the api version.
 */
sealed class PatternNode {
    /**
     * List of all the children of this node.
     *
     * Nodes are added in the order in which they should be checked.
     */
    private val children = mutableListOf<PatternNode>()

    /** Check to see if this node has any children. */
    internal fun hasChildren() = children.isNotEmpty()

    /**
     * Dump the contents of this node and return as a string.
     *
     * Useful for debugging and testing.
     */
    internal fun dump(): String {
        return buildString { dumpTo(this, "") }
    }

    /** Dump the contents of this node to the [builder] using [indent]. */
    private fun dumpTo(builder: StringBuilder, indent: String) {
        builder.apply {
            append(indent)
            append(this@PatternNode.toString())
            append("\n")
            for (child in children) {
                child.dumpTo(this, "$indent  ")
            }
        }
    }

    /**
     * Get [text] plus a `/` suffix if this has any children as in that case it is assumed to match
     * a directory.
     */
    protected fun withDirectorySuffixIfHasChildren(text: String) =
        text + if (children.isEmpty() || text == "/") "" else "/"

    /**
     * Get an existing child node that matches [child] or if none exist add [child] and return it.
     */
    private fun getExistingOrAdd(child: PatternNode): PatternNode {
        // The child node must be new without any children of its own.
        require(child.children.isEmpty()) { "Cannot add $child as it has children of its own" }
        return children.find { it == child } ?: child.also { children.add(child) }
    }

    /**
     * Provides access to the files that are to be scanned.
     *
     * Callers that want to limit the scanning to only some files can provide a custom
     * implementation of this.
     */
    internal interface FileProvider {
        /**
         * Resolve [name] relative to [base] and if the resulting file exists then return it,
         * otherwise return null.
         */
        fun resolve(base: File, name: String): File?

        /** Return a sequence of the files in [dir], or null if [dir] is not a directory. */
        fun listFiles(dir: File): Sequence<File>?
    }

    /** Provides access to all files in the whole file system. */
    internal open class WholeFileSystemProvider : FileProvider {
        override fun resolve(base: File, name: String): File? {
            val file = base.resolve(name)
            return if (file.exists()) file else null
        }

        override fun listFiles(dir: File): Sequence<File>? {
            return dir.listFiles()?.asSequence()
        }
    }

    /**
     * A [FileProvider] that limits access to a supplied list of [File]s.
     *
     * @param files The list of [File]s to which this will provide access.
     */
    internal class LimitedFileSystemProvider(files: List<File>) : WholeFileSystemProvider() {
        /**
         * Map from [File] to the list of [File]s it contains (or an empty list for [File]s that
         * have no contents).
         */
        private val fileToContents =
            buildMap<File, MutableList<File>> {
                for (file in files) {
                    // Remember the file.
                    computeIfAbsent(file) { mutableListOf() }

                    // Add the file to its parent file's contents. Repeat for its parent file.
                    var f: File = file
                    while (true) {
                        val parent = f.parentFile ?: break
                        val contents = computeIfAbsent(parent) { mutableListOf() }
                        contents.add(f)
                        f = parent
                    }
                }
            }

        override fun resolve(base: File, name: String): File? {
            val file = super.resolve(base, name)
            return if (file in fileToContents) file else null
        }

        override fun listFiles(dir: File): Sequence<File>? {
            if (!dir.isDirectory) return null
            return fileToContents[dir]?.asSequence()
        }
    }

    /** Configuration provided when scanning. */
    internal data class ScanConfig(
        /** The root directory from which the scanning will be performed. */
        val dir: File,

        /**
         * An optional filter which, if specified, will limit the versions that will be returned.
         * This is provided when scanning, instead of just filtering afterward, to save time when
         * scanning by ignoring version directories that are not accepted by the filter.
         */
        val apiVersionFilter: ((ApiVersion) -> Boolean)? = null,

        /** Provides access to [File]s. */
        val fileProvider: FileProvider = WholeFileSystemProvider(),

        /**
         * Map from [ApiSurface.name] to [ApiSurface]s that is used by [Placeholder.SURFACE] to map
         * from surface name to [ApiSurface].
         *
         * It is an error if a [Placeholder.SURFACE] is used and this is not provided.
         */
        val apiSurfaceByName: Map<String, ApiSurface>? = null,
    )

    /**
     * Scan the [ScanConfig.dir] using this pattern node as the guide.
     *
     * Returns a list of [MatchedPatternFile] objects, in version order (from the lowest to the
     * highest), If multiple matching files have the same version then only the first version will
     * be used.
     */
    internal fun scan(config: ScanConfig): List<MatchedPatternFile> {
        val dir = config.dir
        val start = PatternFileState(file = dir)

        // Create a sorted set into which the matched files will be added.
        val sortedSet = TreeSet(matchedPatternFileComparator)

        // Scan for files and add them to the sorted set if an equivalent one does not exist. That
        // will eliminate duplicates and order them.
        for (matchedPatternFile in scan(config, start)) {
            // Add the file if it does not already exist in the set. That ensures that the set
            // contains the first instance of each duplicate.
            sortedSet.add(matchedPatternFile)
        }

        return sortedSet.toList()
    }

    /**
     * Scan the [PatternFileState.file] using this pattern node as the guide to find the matching
     * files.
     *
     * This returns the result as a [Sequence] of [MatchedPatternFile] which have each been
     * populated with information extracted from matching [File]s.
     *
     * The basic idea is that the [PatternNode] will guide the scanning by using information within
     * the [PatternNode] hierarchy to limit scanning to only those directories that could possibly
     * match the patterns from which the [PatternNode] hierarchy was created.
     *
     * Each implementation of this consumes a [PatternFileState] (whose [PatternFileState.file] is
     * the directory to scan) and then applies its own rules to select [File]s that match. It then
     * creates copies of [state] for each [File] (possibly updating other properties too). Those new
     * [PatternFileState]s are either passed to [children] for further scanning, or if this is a
     * leaf node then they are converted into a sequence of [MatchedPatternFile]s that are returned
     * to the caller.
     */
    internal abstract fun scan(
        config: ScanConfig,
        state: PatternFileState
    ): Sequence<MatchedPatternFile>

    /**
     * Pass the [PatternFileState] on for further scanning or return [MatchedPatternFile]s if no
     * further scanning is necessary.
     *
     * If [children] is empty then this just returns a [Sequence] containing the
     * [MatchedPatternFile] created from [state]. Otherwise, this passes [state] to each of the
     * [children] to scan, and flattens the resulting [Sequence]s of [MatchedPatternFile]s and
     * returns that.
     */
    internal fun scanChildrenOrReturnMatching(
        config: ScanConfig,
        state: PatternFileState,
    ): Sequence<MatchedPatternFile> =
        if (children.isEmpty())
            sequenceOf(
                // Convert the PatternFileState into MatchedPatternFile objects relative to dir.
                state.matchedPatternFile(config.dir),
            )
        else children.asSequence().flatMap { it.scan(config, state) }

    /**
     * Used by [getExistingOrAdd] to allow duplicate nodes to be ignored.
     *
     * This must not include [children] in the check as in [getExistingOrAdd] the existing
     * [PatternNode]s being compared are likely to have a non-empty [children] list but the new
     * [PatternNode] will have an empty [children] list.
     */
    abstract override fun equals(other: Any?): Boolean

    /** Not currently used but should be implemented consistent with [equals]. */
    abstract override fun hashCode(): Int

    /**
     * The root [PatternNode].
     *
     * Just acts as a container for other [PatternNode]s.
     */
    private class RootPatternNode : PatternNode() {
        override fun toString() = "<root>"

        override fun scan(
            config: ScanConfig,
            state: PatternFileState
        ): Sequence<MatchedPatternFile> {
            if (!hasChildren()) return emptySequence()
            return scanChildrenOrReturnMatching(config, state)
        }

        /** Root nodes are unique. */
        override fun equals(other: Any?): Boolean {
            return this === other
        }

        /** Root nodes are unique. */
        override fun hashCode(): Int {
            return System.identityHashCode(this)
        }
    }

    /**
     * Matches a fixed file called [name].
     *
     * e.g. if [name] is `foo` then when scanning/matching directory `bar`, this will scan/match
     * `bar/foo`.
     */
    private data class FixedNamePatternNode(
        /** The fixed name of the file that this matches. */
        val name: String,
    ) : PatternNode() {
        override fun toString() = withDirectorySuffixIfHasChildren(name)

        /** Check to see if there i */
        override fun scan(
            config: ScanConfig,
            state: PatternFileState
        ): Sequence<MatchedPatternFile> {
            // Resolve this against the file in [properties] to get a new file. If that file does
            // not exist then ignore it by returning an empty sequence.
            val newFile = config.fileProvider.resolve(state.file, name) ?: return emptySequence()

            // Create a new set of properties by copying the original properties, replacing the file
            // with the new file.
            val newProperties = state.copy(file = newFile)

            // Pass the properties on to the next nodes in the scanning, or return if this is the
            // last node.
            return scanChildrenOrReturnMatching(config, newProperties)
        }
    }

    /**
     * Matches any file name containing one or more placeholders.
     *
     * The [pattern] is used to create a [regex] which is matched against each file name that could
     * match. If it matches then for each placeholder at position `i` in the list of [placeholders]
     * the `i+1`th group is retrieved from the [MatchResult] and passed to the [Placeholder]'s
     * [Property]'s [Property.track] method. That will then process the value and update a property
     * in [PatternFileState].
     *
     * e.g. assume [pattern] is `android-(\d+)` and [placeholders] contains a single instance of
     * [Placeholder.VERSION_LEVEL]. When scanning/matching directory `bar`, this will scan any file
     * in that directory called `android-<version>`, e.g. `bar/android-1`, `bar/android-2`, etc. The
     * 1st group will be retrieved and passed to the [Property.track] method for the
     * [Property.VERSION] which will create an [ApiVersion] and if appropriate store it in the
     * [PatternFileState.version] property.
     *
     * This is a data class as it needs to implement [equals] and [hashCode] so that instances can
     * be dedup-ed by [PatternNode.getExistingOrAdd].
     *
     * @param pattern the regular expression pattern that will match the file name and which has a
     *   capturing group for each [Placeholder] in [placeholders] in the same order.
     * @param placeholders the list of [Placeholder]s that will extract information from a matching
     *   file name and track it in a [PatternFileState].
     */
    private data class PlaceholderPatternNode(
        private val pattern: String,
        val placeholders: List<Placeholder>,
    ) : PatternNode() {
        override fun toString() = withDirectorySuffixIfHasChildren(pattern)

        private val regex = Regex(pattern)

        override fun scan(
            config: ScanConfig,
            state: PatternFileState
        ): Sequence<MatchedPatternFile> {
            val contents = config.fileProvider.listFiles(state.file) ?: return emptySequence()
            return contents.flatMap { file ->
                // Match the regex against the file name, if it does not match then ignore this
                // file and all its contents by returning an empty sequence.
                val name = file.name
                val matcher = regex.matchEntire(name) ?: return@flatMap emptySequence()

                var newState = state.copy(file = file)
                for ((index, placeholder) in placeholders.withIndex()) {
                    // There is a one-to-one correspondence between each capturing group in the
                    // [pattern] and each placeholder in [placeholders] and each placeholder is
                    // associated with the groups index is one more than the index of the
                    // placeholder in the placeholders list. It is one more because group indices
                    // are one based as group 0 corresponds to the text that matches the whole
                    // pattern.
                    val groupIndex = index + 1

                    // Retrieve the value of the group for the placeholder. Throws an error if it
                    // could not be found as that should never happen.
                    val matchGroup =
                        matcher.groups[groupIndex]
                            ?: error("No matching group found for placeholder $placeholder")

                    // Extract the value and store it in the appropriate [PatternFileState]
                    // property.
                    newState =
                        placeholder.property.track(config, newState, matchGroup.value, placeholder)
                            ?: return@flatMap emptySequence()
                }

                scanChildrenOrReturnMatching(config, newState)
            }
        }
    }

    /** The properties for which placeholders can be provided. */
    enum class Property(val propertyName: String, val help: () -> String) {
        /**
         * Corresponds to the [PatternFileState.version] and [MatchedPatternFile.version]
         * properties.
         */
        VERSION(
            "version",
            help = {
                """
                    Mandatory property that stores the version of a matched file.

                    Apart from the ${Placeholder.VERSION_EXTENSION} all placeholders for this will
                    ignore versions that fall outside the range $ARG_FIRST_VERSION and
                    $ARG_CURRENT_VERSION, if provided.
                """
            },
        ) {
            override fun track(
                config: ScanConfig,
                state: PatternFileState,
                value: String,
                placeholder: Placeholder,
            ): PatternFileState? {
                // Extract the API version from the value.
                val version = ApiVersion.fromString(value)

                val extension = placeholder == Placeholder.VERSION_EXTENSION

                // Make sure that it is accepted by the filter (if one was specified). If it is not
                // then ignore this file and all its contents by returning an empty sequence. The
                // filter does not apply to extension versions, all extension versions are used.
                if (!extension) {
                    config.apiVersionFilter?.let { apiVersionFilter ->
                        if (!apiVersionFilter(version)) return null
                    }
                }

                return state.copy(version = version, extension = extension)
            }
        },

        /**
         * Corresponds to the [PatternFileState.module] and [MatchedPatternFile.module] properties.
         */
        MODULE(
            "module",
            help = {
                """
                    Optional property that stores the name of the SDK extension module.

                    Patterns that use a placeholder for this are assumed to be matching files for
                    SDK extensions.
                """
            },
        ) {
            override fun track(
                config: ScanConfig,
                state: PatternFileState,
                value: String,
                placeholder: Placeholder,
            ) = state.copy(module = value)
        },

        /**
         * Corresponds to the [PatternFileState.surface] and [MatchedPatternFile.surface]
         * properties.
         */
        SURFACE(
            "surface",
            help = {
                """
                    Optional property that stores the API surface.
                """
            },
        ) {
            override fun track(
                config: ScanConfig,
                state: PatternFileState,
                value: String,
                placeholder: Placeholder,
            ): PatternFileState? {
                val apiSurfaceByName =
                    config.apiSurfaceByName
                        ?: error(
                            "Must provide ScanConfig.apiSurfaceByName when ${Placeholder.SURFACE} is used"
                        )
                // Look the surface up in the available surfaces, if it could not be found then
                // ignore this file.
                val surface = apiSurfaceByName[value] ?: return null
                return state.copy(surface = surface)
            }
        },
        ;

        /**
         * Tracks the placeholder value by extracting it from [value] and creating a copy of [state]
         * with the value stored in the appropriate property.
         *
         * If the placeholder value is invalid for some reason then returns `null` to indicate that
         * the [state] should be ignored.
         *
         * @param config configuration that affects the matching.
         * @param state the input [PatternFileState].
         * @param value the value of the placeholder extracted from the path.
         * @param placeholder the [Placeholder] for which this is being called.
         */
        internal abstract fun track(
            config: ScanConfig,
            state: PatternFileState,
            value: String,
            placeholder: Placeholder,
        ): PatternFileState?

        override fun toString() = propertyName
    }

    /**
     * Enumeration of all possible placeholders.
     *
     * @param property the name of the property in [PatternFileState] that will be updated by the
     *   placeholder.
     * @param format the format of the property. This differentiates between placeholders with the
     *   same [property] but which have different [pattern]s.
     * @param pattern the pattern that determines which part of a file name will be matched by the
     *   placeholder. This must not contain any capturing groups.
     * @param help lambda for providing help used in the `metalava help historical-api-patterns`
     *   command. A lambda is used to allow references to [Property] instances that may not have
     *   been initialized before this is initialized.
     */
    enum class Placeholder(
        val property: Property,
        private val format: String?,
        val pattern: String,
        val help: () -> String,
    ) {
        /** The {version:level} placeholder. */
        VERSION_LEVEL(
            property = Property.VERSION,
            format = "level",
            pattern = """\d+""",
            help = {
                """
                    Matches a single non-negative integer and treats it as an API version.
                """
            },
        ),

        /** The {version:major.minor?} placeholder. */
        VERSION_MAJOR_MINOR(
            property = Property.VERSION,
            format = "major.minor?",
            // Match either a single major version or a major and minor version together.
            pattern = """\d+(?:\.\d+)?""",
            help = {
                """
                    Matches a single non-negative integer or two such integers separated by a `.`.
                """
            },
        ),

        /** The {version:major.minor.patch} placeholder. */
        VERSION_MAJOR_MINOR_PATCH(
            property = Property.VERSION,
            format = "major.minor.patch",
            // Only match a version with major, minor and patch components.
            pattern = """\d+\.\d+\.\d+""",
            help = {
                """
                    Matches three non-negative integers separated by `.`s.
                """
            },
        ),

        /** The {version:extension} placeholder. */
        VERSION_EXTENSION(
            property = Property.VERSION,
            format = "extension",
            // Only match a version with extension version.
            pattern = """\d+""",
            help = {
                """
                    Matches a single non-negative integer and treats it as an extension version.

                    A pattern that includes this must also include `$MODULE` as SDK extension APIs
                    are stored in a file per extension module.
                """
            },
        ),

        /** The {module} placeholder. */
        MODULE(
            property = Property.MODULE,
            format = null,
            pattern = """[a-z-.]+""",
            help = {
                """
                    Matches a module name which must consist of lower case letters, hyphens and
                    `.`s.
                """
            },
        ),

        /** The {surface} placeholder. */
        SURFACE(
            property = Property.SURFACE,
            format = null,
            pattern = """[a-z-]+""",
            help = {
                """
                    Matches a surface name which must consist of lower case letters and hyphens.
                """
            },
        ),
        ;

        /** The label for this that will be used in a path pattern, e.g. `{version:level}`. */
        val label = if (format == null) "{$property}" else "{$property:$format}"

        override fun toString() = label

        companion object {
            fun placeholderForLabel(label: String, pathPattern: String): Placeholder {
                return placeholderByLabel[label]
                    ?: error(
                        "Pattern '$pathPattern' contains an unknown placeholder '$label', expected one of ${placeholderByLabel.keys.joinToString {"'$it'"}}"
                    )
            }

            /** Map from [Placeholder.label] to [Placeholder]. */
            internal val placeholderByLabel = Placeholder.entries.associateBy { it.label }
        }
    }

    companion object {
        /**
         * Parse a list of [patterns] into a tree of [PatternNode]s.
         *
         * Each pattern in [patterns] must contain a single `{version:level}` that is a placeholder
         * for the version number.
         */
        fun parsePatterns(patterns: List<String>): PatternNode {
            val root = RootPatternNode()
            for (pattern in patterns) {
                addPattern(root, pattern)
            }
            return root
        }

        /**
         * Add a new pattern [pathPattern] to [root], where [pathPattern] consists of name patterns
         * separated by `/`.
         *
         * Creates a [PatternNode] for each name pattern in the supplied [pathPattern] inserting it
         * into the [root], reusing existing [PatternNode]s where possible.
         */
        private fun addPattern(root: PatternNode, pathPattern: String) {
            // The list of nodes used for the pattern.
            val nodes = mutableListOf<PatternNode>()

            var parent = root
            // Split the pattern using `/` and iterate over each of the parts adding them into the
            // tree structure.
            for (namePattern in pathPattern.split("/")) {
                // Create a node for the pattern.
                val node =
                    when {
                        // Handle when the path is absolute and starts with a /
                        namePattern == "" -> {
                            FixedNamePatternNode("/")
                        }
                        '{' in namePattern || '*' in namePattern -> {
                            parseParameterizedPattern(pathPattern, namePattern)
                        }
                        else -> FixedNamePatternNode(namePattern)
                    }

                nodes.add(node)

                // Find a matching node in the parent adding the new node if no existing node
                // exists. Use the result as the parent for the next node.
                parent = parent.getExistingOrAdd(node)
            }

            // Check to make sure that exactly one of the nodes will match an API version.
            val usedPlaceholders =
                nodes.mapNotNull { it as? PlaceholderPatternNode }.flatMap { it.placeholders }
            val placeholdersByProperty = usedPlaceholders.groupBy { it.property }

            // Do some basic validation of the placeholders in the pattern.
            if (Property.VERSION !in placeholdersByProperty) {
                // At least one placeholder that will set the version property must be provided.
                error("Pattern '$pathPattern' does not contain placeholder for ${Property.VERSION}")
            }

            for ((property, placeholders) in placeholdersByProperty.entries) {
                val count = placeholders.size
                // An entry in a map created by groupBy will always have a list containing at least
                // one item.
                if (count != 1) {
                    // No property can have multiple placeholders for it as that could lead to a
                    // conflict over which value will be used and/or complicate the logic to make
                    // sure that all the values are the same.
                    error(
                        "Pattern '$pathPattern' contains multiple placeholders for $property; found ${placeholders.joinToString()}"
                    )
                }
            }

            if (
                Placeholder.VERSION_EXTENSION in usedPlaceholders &&
                    Placeholder.MODULE !in usedPlaceholders
            ) {
                error(
                    "Pattern '$pathPattern' contains `${Placeholder.VERSION_EXTENSION}` but does not contain `${Placeholder.MODULE}`"
                )
            }
        }

        /** [Regex] to find placeholders or wildcards in a pattern. */
        private val PLACEHOLDER_OR_WILDCARD_REGEX = Regex("""(\{[^}]+})|(\*)""")

        /**
         * Parse a parameterized pattern, i.e. one with a placeholder like '{version:level}'.
         *
         * The basic approach is to convert the [pattern] into a standard regular expression and a
         * list of [Placeholder]s such that each placeholder in [pattern] has a corresponding
         * capture group in the regular expression and a [Placeholder] in the list. The list is in
         * the same order as the groups. Together they are used to create a [PlaceholderPatternNode]
         * that will use that information to update a [PatternFileState] with information extracted
         * from a matching file.
         *
         * @param pathPattern the pattern for the whole file path, used for error reporting.
         * @param pattern the pattern for one file name in the path. This is the pattern that this
         *   method will parse.
         */
        private fun parseParameterizedPattern(pathPattern: String, pattern: String): PatternNode {
            val regexBuilder = StringBuilder()
            var literalStart = 0

            val placeholders = mutableListOf<Placeholder>()

            /**
             * Quote any literal text found between the start of the pattern or last placeholder and
             * the [firstNonLiteral].
             */
            fun quoteLiteralText(firstNonLiteral: Int) {
                if (firstNonLiteral > literalStart) {
                    regexBuilder.append(
                        Regex.escape(pattern.substring(literalStart, firstNonLiteral))
                    )
                }
            }

            // Convert the pattern into a regular expression, quoting any literal text and replacing
            // placeholders/wildcards with an appropriate regular expression.
            for (matchResult in PLACEHOLDER_OR_WILDCARD_REGEX.findAll(pattern)) {
                // Quote any literal text found between the start of the pattern or last
                // placeholder/wildcard and this match.
                quoteLiteralText(matchResult.range.first)

                // The next block of literal text (if any) will start after the match.
                literalStart = matchResult.range.last + 1

                // Extract the text representation of the placeholder/wildcard from the pattern and
                // process it accordingly.
                when (val placeholderOrWildcardText = matchResult.value) {
                    "*" -> {
                        regexBuilder.append("""[^/]*""")
                    }
                    else -> {
                        // Find the corresponding [Placeholder], failing if it could not be found.
                        val placeholder =
                            Placeholder.placeholderForLabel(placeholderOrWildcardText, pathPattern)

                        // Add a capturing group to the pattern for the placeholder. This requires
                        // that the placeholder pattern does not contain any capturing groups of its
                        // own.
                        regexBuilder.append("""(${placeholder.pattern})""")

                        // Add a placeholder. As placeholder patterns do not contain capturing
                        // groups the combined pattern has a single group for each placeholder and
                        // in the same order as the placeholders.
                        placeholders.add(placeholder)
                    }
                }
            }

            // Quote any literal text found at the end of the pattern after the last placeholder.
            quoteLiteralText(pattern.length)

            return PlaceholderPatternNode(regexBuilder.toString(), placeholders.toList())
        }
    }
}

/**
 * Encapsulates the information accrued about a specific [file] that matches a pattern during
 * scanning.
 */
internal data class PatternFileState(
    /**
     * The [File] that has been matched so far.
     *
     * This could be a directory, e.g. `prebuilts/sdk` after matching
     */
    val file: File,

    /** The optional [ApiVersion] that was extracted from the path. */
    val version: ApiVersion? = null,

    /** Indicates whether the file is for an SDK extension module. */
    val extension: Boolean = false,

    /** The optional module that was extracted from the path. */
    val module: String? = null,

    /** The optional surface that was extracted from the path. */
    val surface: ApiSurface? = null,
) {
    /**
     * Construct a [MatchedPatternFile] from this.
     *
     * This must only be called when this has been matched by a leaf [PatternNode] and so is
     * guaranteed to have had [version] set to a non-null value.
     */
    fun matchedPatternFile(dir: File) =
        if (version == null) error("matching pattern could not extract version from $file")
        else
            MatchedPatternFile(
                file = file.relativeDescendantOfOrSelf(dir),
                version = version,
                extension = extension,
                module = module,
                surface = surface,
            )

    /**
     * If this [File] is a descendant of [base] then return a relative path from [base] to this,
     * otherwise just return this.
     *
     * This ensures that an absolute path does not end up being turned into an even more complicated
     * relative path that starts with lots of `../../`. This is only needed for tests that use
     * patterns in a temporary directory which is not relative to the current directory in which the
     * scanning is performed. It should not be an issue in practice as callers typically run with
     * patterns relative to the current directory.
     */
    private fun File.relativeDescendantOfOrSelf(base: File) =
        relativeTo(base).let { relative -> if (relative.startsWith("../")) file else relative }
}

/** Represents a [File] that matches a pattern encapsulate in a hierarchy of [PatternNode]s. */
data class MatchedPatternFile(
    /**
     * The matched [File].
     *
     * This is relative to the directory supplied in [PatternNode.ScanConfig.dir].
     */
    val file: File,

    /** The [ApiVersion] extracted from the [File] path. */
    val version: ApiVersion,

    /** True if this represents a file from an extension module. */
    val extension: Boolean = false,

    /** The optional module that was extracted from the [File] path. */
    val module: String? = null,

    /** The optional surface that was extracted from the [File] path. */
    val surface: ApiSurface? = null,
) {
    /**
     * Create a string representation of the properties, used for testing and debugging.
     *
     * Any optional properties will only be included in the string representation if they are
     * provided, e.g. not `null`. That means adding a new optional property will not affect any
     * tests that rely on the output of this.
     */
    override fun toString(): String {
        return buildString {
            append("MatchedPatternFile(")
            append("file=")
            append(file.path)
            append(", version=")
            append(version)
            if (extension) {
                append(", extension=true")
            }
            if (module != null) {
                append(", module='")
                append(module)
                append("'")
            }
            if (surface != null) {
                append(", surface='")
                append(surface.name)
                append("'")
            }
            append(")")
        }
    }
}

/**
 * Comparator that is used to identify duplicate [MatchedPatternFile]s and defined an order for the
 * unique instances.
 */
private val matchedPatternFileComparator: Comparator<MatchedPatternFile> =
    // If any of the selectors return `null` that will compare before any other value.
    compareBy(
        // Group into those without modules and then by those with module, in order.
        { it.module },
        // Then sort them from the lowest version to the highest version.
        { it.version },
        // Then group into those without surface and then by those with a surface, in order.
        { it.surface },
    )
