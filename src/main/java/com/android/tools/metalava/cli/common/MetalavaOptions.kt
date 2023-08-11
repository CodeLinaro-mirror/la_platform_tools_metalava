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

package com.android.tools.metalava.cli.common

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File

// This contains extensions methods for creating custom Clikt options.

/** Convert the option to a [File] that represents an existing file. */
fun RawOption.existingFile(): NullableOption<File, File> {
    return fileConversion(::stringToExistingFile)
}

/** Convert the argument to a [File] that represents an existing file. */
fun RawArgument.existingFile(): ProcessedArgument<File, File> {
    return fileConversion(::stringToExistingFile)
}

/** Convert the argument to a [File] that represents an existing directory. */
fun RawArgument.existingDir(): ProcessedArgument<File, File> {
    return fileConversion(::stringToExistingDir)
}

/** Convert the argument to a [File] that represents a new file. */
fun RawArgument.newFile(): ProcessedArgument<File, File> {
    return fileConversion(::stringToNewFile)
}

/** Convert the option to a [File] using the supplied conversion function.. */
private fun RawOption.fileConversion(conversion: (String) -> File): NullableOption<File, File> {
    return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { str ->
        try {
            conversion(str)
        } catch (e: MetalavaCliException) {
            e.message?.let { fail(it) } ?: throw e
        }
    }
}

/** Convert the argument to a [File] using the supplied conversion function. */
fun RawArgument.fileConversion(conversion: (String) -> File): ProcessedArgument<File, File> {
    return convert(CompletionCandidates.Path) { str ->
        try {
            conversion(str)
        } catch (e: MetalavaCliException) {
            e.message?.let { fail(it) } ?: throw e
        }
    }
}

/**
 * Converts a path to a [File] that represents the absolute path, with the following special
 * behavior:
 * - "~" will be expanded into the home directory path.
 * - If the given path starts with "@", it'll be converted into "@" + [file's absolute path]
 *
 * Note, unlike the other "stringToXxx" methods, this method won't register the given path to
 * [FileReadSandbox].
 */
internal fun fileForPathInner(path: String): File {
    // java.io.File doesn't automatically handle ~/ -> home directory expansion.
    // This isn't necessary when metalava is run via the command line driver
    // (since shells will perform this expansion) but when metalava is run
    // directly, not from a shell.
    if (path.startsWith("~/")) {
        val home = System.getProperty("user.home") ?: return File(path)
        return File(home + path.substring(1))
    } else if (path.startsWith("@")) {
        return File("@" + File(path.substring(1)).absolutePath)
    }

    return File(path).absoluteFile
}

/**
 * Convert a string representing an existing directory to a [File].
 *
 * This will fail if:
 * * The file is not a regular directory.
 */
internal fun stringToExistingDir(value: String): File {
    val file = fileForPathInner(value)
    if (!file.isDirectory) {
        throw MetalavaCliException("$file is not a directory")
    }
    return FileReadSandbox.allowAccess(file)
}

/**
 * Convert a string representing an existing file to a [File].
 *
 * This will fail if:
 * * The file is not a regular file.
 */
internal fun stringToExistingFile(value: String): File {
    val file = fileForPathInner(value)
    if (!file.isFile) {
        throw MetalavaCliException("$file is not a file")
    }
    return FileReadSandbox.allowAccess(file)
}

/**
 * Convert a string representing a new file to a [File].
 *
 * This will fail if:
 * * the file is a directory.
 * * the file exists and cannot be deleted.
 * * the parent directory does not exist, and cannot be created.
 */
internal fun stringToNewFile(value: String): File {
    val output = fileForPathInner(value)

    if (output.exists()) {
        if (output.isDirectory) {
            throw MetalavaCliException("$output is a directory")
        }
        val deleted = output.delete()
        if (!deleted) {
            throw MetalavaCliException("Could not delete previous version of $output")
        }
    } else if (output.parentFile != null && !output.parentFile.exists()) {
        val ok = output.parentFile.mkdirs()
        if (!ok) {
            throw MetalavaCliException("Could not create ${output.parentFile}")
        }
    }

    return FileReadSandbox.allowAccess(output)
}

// Unicode Next Line (NEL) character which forces Clikt to insert a new line instead of just
// collapsing the `\n` into adjacent spaces. Acts like an HTML <br/>.
const val HARD_NEWLINE = "\u0085"

/**
 * Create a property delegate for an enum.
 *
 * This will generate help text that:
 * * uses lower case version of the enum value name (with `_` replaced with `-`) as the value to
 *   supply on the command line.
 * * formats the help for each enum value in its own block separated from surrounding blocks by
 *   blank lines.
 * * will tag the default enum value in the help.
 *
 * @param names the possible names for the option that can be used on the command line.
 * @param help the help for the option, does not need to include information about the default or
 *   the individual options as they will be added automatically.
 * @param enumValueHelpGetter given an enum value return the help for it.
 * @param key given an enum value return the value that must be specified on the command line. This
 *   is used to create a bidirectional mapping so that command line option can be mapped to the enum
 *   value and the default enum value mapped back to the default command line option. Defaults to
 *   using the lowercase version of the name with `_` replaced with `-`.
 * @param default the default value, must be provided to ensure correct type inference.
 */
internal inline fun <reified T : Enum<T>> ParameterHolder.enumOption(
    vararg names: String,
    help: String,
    noinline enumValueHelpGetter: (T) -> String,
    noinline key: (T) -> String = { it.name.lowercase().replace("_", "-") },
    default: T,
): OptionWithValues<T, T, T> {
    // Create a choice mapping from option to enum value using the `key` function.
    val enumValues = enumValues<T>()
    return nonInlineEnumOption(names, enumValues, help, enumValueHelpGetter, key, default)
}

/**
 * Extract the majority of the work into a non-inline function to avoid it creating too much bloat
 * in the call sites.
 */
internal fun <T : Enum<T>> ParameterHolder.nonInlineEnumOption(
    names: Array<out String>,
    enumValues: Array<T>,
    help: String,
    enumValueHelpGetter: (T) -> String,
    key: (T) -> String,
    default: T
): OptionWithValues<T, T, T> {
    val optionToValue = enumValues.associateBy { key(it) }

    // Create a reverse mapping from enum value to option. This may break if two enum value uses
    // the same name in different cases, but that is highly unlikely as it breaks all coding
    // standards.
    val valueToOption = optionToValue.entries.associateBy({ it.value }, { it.key })
    val defaultForHelp =
        valueToOption.get(default) ?: throw IllegalStateException("Unknown enum value $default")

    val constructedHelp = buildString {
        append(help)
        append(HARD_NEWLINE)
        for (enumValue in enumValues) {
            val value = key(enumValue)
            // This must match the pattern used in MetalavaHelpFormatter.styleEnumHelpTextIfNeeded
            // which is used to deconstruct this.
            append(constructStyleableChoiceOption(value))
            append(" - ")
            append(enumValueHelpGetter(enumValue))
            append(HARD_NEWLINE)
        }
    }

    return option(names = names, help = constructedHelp)
        .choice(optionToValue)
        .default(default, defaultForHelp = defaultForHelp)
}

/**
 * Construct a styleable choice option.
 *
 * This prefixes and suffixes the choice option with `**` (like Markdown) so that they can be found
 * in the help text using [deconstructStyleableChoiceOption] and replaced with actual styling
 * sequences if needed.
 */
private fun constructStyleableChoiceOption(value: String) = "$HARD_NEWLINE**$value**"

/**
 * A regular expression that will match choice options created using
 * [constructStyleableChoiceOption].
 */
private val deconstructStyleableChoiceOption = """$HARD_NEWLINE\*\*([^*]+)\*\*""".toRegex()

/**
 * Replace the choice option (i.e. the value passed to [constructStyleableChoiceOption]) with the
 * result of calling the [transformer] on it.
 *
 * This must only be called on a [MatchResult] found using the [deconstructStyleableChoiceOption]
 * regular expression.
 */
private fun MatchResult.replaceChoiceOption(
    builder: StringBuilder,
    transformer: (String) -> String
) {
    val group = groups[1] ?: throw IllegalStateException("group 1 not found in $this")
    val choiceOption = group.value
    val replacementText = transformer(choiceOption)
    // Replace the choice option and the surrounding style markers but not the leading NEL.
    builder.replace(range.first + 1, range.last + 1, replacementText)
}

/**
 * Scan [help] using [deconstructStyleableChoiceOption] for enum value help created using
 * [constructStyleableChoiceOption] and if it was found then style it using the [terminal].
 *
 * If an enum value is found that matches the value of the [HelpFormatter.Tags.DEFAULT] tag in
 * [tags] then annotate is as the default and remove the tag, so it is not added by the default help
 * formatter.
 */
internal fun styleEnumHelpTextIfNeeded(
    help: String,
    tags: MutableMap<String, String>,
    terminal: Terminal
): String {
    val defaultForHelp = tags[HelpFormatter.Tags.DEFAULT]

    // Find all styleable choice options in the help text. If there are none then just return
    // and use the default rendering.
    val matchResults = deconstructStyleableChoiceOption.findAll(help).toList()
    if (matchResults.isEmpty()) {
        return help
    }

    val styledHelp = buildString {
        append(help)

        // Iterate over the matches in reverse order replacing any styleable choice options
        // with styled versions.
        for (matchResult in matchResults.reversed()) {
            matchResult.replaceChoiceOption(this) { optionValue ->
                val styledOptionValue = terminal.bold(optionValue)
                if (optionValue == defaultForHelp) {
                    // Remove the default value from the tags so it is not included in the help.
                    tags.remove(HelpFormatter.Tags.DEFAULT)

                    "$styledOptionValue (default)"
                } else {
                    styledOptionValue
                }
            }
        }
    }

    return styledHelp
}
