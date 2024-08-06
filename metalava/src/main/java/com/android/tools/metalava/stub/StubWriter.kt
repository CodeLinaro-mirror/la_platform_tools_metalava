/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.ApiPredicate
import com.android.tools.metalava.FilterPredicate
import com.android.tools.metalava.actualItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.Language
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.psi.trimDocIndent
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer
import java.util.regex.Pattern

internal class StubWriter(
    private val stubsDir: File,
    private val generateAnnotations: Boolean = false,
    private val docStubs: Boolean,
    private val reporter: Reporter,
    private val config: StubWriterConfig,
) : DelegatedVisitor {

    override fun visitPackage(pkg: PackageItem) {
        getPackageDir(pkg, create = true)

        writePackageInfo(pkg)

        if (docStubs) {
            pkg.overviewDocumentation?.let { writeDocOverview(pkg, it) }
        }
    }

    fun writeDocOverview(pkg: PackageItem, content: String) {
        if (content.isBlank()) {
            return
        }

        val sourceFile = File(getPackageDir(pkg), "overview.html")
        val overviewWriter =
            try {
                PrintWriter(BufferedWriter(FileWriter(sourceFile)))
            } catch (e: IOException) {
                reporter.report(Issues.IO_ERROR, sourceFile, "Cannot open file for write.")
                return
            }

        // Should we include this in our stub list?
        //     startFile(sourceFile)

        overviewWriter.println(content)
        overviewWriter.flush()
        overviewWriter.close()
    }

    private fun writePackageInfo(pkg: PackageItem) {
        val annotations = pkg.modifiers.annotations()
        val writeAnnotations = annotations.isNotEmpty() && generateAnnotations
        val writeDocumentation =
            config.includeDocumentationInStubs && pkg.documentation.isNotBlank()
        if (writeAnnotations || writeDocumentation) {
            val sourceFile = File(getPackageDir(pkg), "package-info.java")
            val packageInfoWriter =
                try {
                    PrintWriter(BufferedWriter(FileWriter(sourceFile)))
                } catch (e: IOException) {
                    reporter.report(Issues.IO_ERROR, sourceFile, "Cannot open file for write.")
                    return
                }

            appendDocumentation(pkg, packageInfoWriter, config)

            if (annotations.isNotEmpty()) {
                // Write the modifier list even though the package info does not actually have
                // modifiers as that will write the annotations which it does have and ignore the
                // modifiers.
                ModifierListWriter.forStubs(
                        writer = packageInfoWriter,
                        docStubs = docStubs,
                    )
                    .write(pkg)
            }
            packageInfoWriter.println("package ${pkg.qualifiedName()};")

            packageInfoWriter.flush()
            packageInfoWriter.close()
        }
    }

    private fun getPackageDir(packageItem: PackageItem, create: Boolean = true): File {
        val relative = packageItem.qualifiedName().replace('.', File.separatorChar)
        val dir = File(stubsDir, relative)
        if (create && !dir.isDirectory) {
            val ok = dir.mkdirs()
            if (!ok) {
                throw IOException("Could not create $dir")
            }
        }

        return dir
    }

    private fun getClassFile(classItem: ClassItem): File {
        assert(classItem.containingClass() == null) { "Should only be called on top level classes" }
        val packageDir = getPackageDir(classItem.containingPackage())

        // Kotlin From-text stub generation is not supported.
        // This method will raise an error if
        // config.kotlinStubs == true and classItem is TextClassItem.
        return if (config.kotlinStubs && classItem.isKotlin()) {
            File(packageDir, "${classItem.simpleName()}.kt")
        } else {
            File(packageDir, "${classItem.simpleName()}.java")
        }
    }

    /**
     * Between top level class files the [textWriter] field doesn't point to a real file; it points
     * to this writer, which redirects to the error output. Nothing should be written to the writer
     * at that time.
     */
    private var errorTextWriter =
        PrintWriter(
            object : Writer() {
                override fun close() {
                    throw IllegalStateException(
                        "Attempt to close 'textWriter' outside top level class"
                    )
                }

                override fun flush() {
                    throw IllegalStateException(
                        "Attempt to flush 'textWriter' outside top level class"
                    )
                }

                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    throw IllegalStateException(
                        "Attempt to write to 'textWriter' outside top level class\n'${String(cbuf, off, len)}'"
                    )
                }
            }
        )

    /** The writer to write the stubs file to */
    private var textWriter: PrintWriter = errorTextWriter

    private var stubWriter: DelegatedVisitor? = null

    override fun visitClass(cls: ClassItem) {
        if (cls.isTopLevelClass()) {
            val sourceFile = getClassFile(cls)
            textWriter =
                try {
                    PrintWriter(BufferedWriter(FileWriter(sourceFile)))
                } catch (e: IOException) {
                    reporter.report(Issues.IO_ERROR, sourceFile, "Cannot open file for write.")
                    errorTextWriter
                }

            val kotlin = config.kotlinStubs && cls.isKotlin()
            val language = if (kotlin) Language.KOTLIN else Language.JAVA

            val modifierListWriter =
                ModifierListWriter.forStubs(
                    writer = textWriter,
                    docStubs = docStubs,
                    runtimeAnnotationsOnly = !generateAnnotations,
                    language = language,
                )

            stubWriter =
                if (kotlin) {
                    error("Generating Kotlin stubs is not supported")
                } else {
                    JavaStubWriter(textWriter, modifierListWriter, config)
                }

            // Copyright statements from the original file?
            cls.getSourceFile()?.getHeaderComments()?.let { textWriter.println(it) }
        }
        stubWriter?.visitClass(cls)
    }

    override fun afterVisitClass(cls: ClassItem) {
        stubWriter?.afterVisitClass(cls)

        if (cls.isTopLevelClass()) {
            textWriter.flush()
            textWriter.close()
            textWriter = errorTextWriter
            stubWriter = null
        }
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        stubWriter?.visitConstructor(constructor)
    }

    override fun visitMethod(method: MethodItem) {
        stubWriter?.visitMethod(method)
    }

    override fun visitField(field: FieldItem) {
        stubWriter?.visitField(field)
    }

    /**
     * Create an [ApiVisitor] that will filter the [Item] to which is applied according to the
     * supplied parameters and in a manner appropriate for writing signatures, e.g. not nesting
     * classes. It will delegate any visitor calls that pass through its filter to this [StubWriter]
     * instance.
     */
    fun createFilteringVisitor(
        preFiltered: Boolean,
        apiVisitorConfig: ApiVisitor.Config,
    ): ItemVisitor {
        val filterReference =
            ApiPredicate(
                includeDocOnly = docStubs,
                config = config.apiVisitorConfig.apiPredicateConfig.copy(ignoreShown = true),
            )
        val filterEmit = FilterPredicate(filterReference)
        return FilteringApiVisitor(
            delegate = this,
            preserveClassNesting = true,
            inlineInheritedFields = true,
            // Methods are by default sorted in source order in stubs, to encourage methods
            // that are near each other in the source to show up near each other in the
            // documentation
            methodComparator = MethodItem.sourceOrderComparator,
            filterEmit = filterEmit,
            filterReference = filterReference,
            preFiltered = preFiltered,
            // Make sure that package private constructors that are needed to compile safely are
            // visited, so they will appear in the stubs.
            visitStubsConstructorIfNeeded = true,
            config = apiVisitorConfig,
        )
    }
}

internal fun appendDocumentation(item: Item, writer: PrintWriter, config: StubWriterConfig) {
    if (config.includeDocumentationInStubs) {
        val documentation = item.fullyQualifiedDocumentation()
        if (documentation.isNotBlank()) {
            val trimmed = trimDocIndent(documentation)
            val output = revertDocumentationDeprecationChange(item, trimmed)
            writer.println(output)
            writer.println()
        }
    }
}

/** Regular expression to match the start of a doc comment. */
private const val DOC_COMMENT_START_RE = """\Q/**\E"""
/**
 * Regular expression to match the end of a block comment. If the block comment is at the start of a
 * line, preceded by some white space then it includes all that white space.
 */
private const val BLOCK_COMMENT_END_RE = """(?m:^\s*)?\Q*/\E"""

/**
 * Regular expression to match the start of a line Javadoc tag, i.e. a Javadoc tag at the beginning
 * of a line. Optionally, includes the preceding white space and a `*` forming a left hand border.
 */
private const val START_OF_LINE_TAG_RE = """(?m:^\s*)\Q*\E\s*@"""

/**
 * A [Pattern[] for matching an `@deprecated` tag and its associated text. If the tag is at the
 * start of the line then it includes everything from the start of the line. It includes everything
 * up to the end of the comment (apart from the line for the end of the comment) or the start of the
 * next line tag.
 */
private val deprecatedTagPattern =
    """((?m:^\s*\*\s*)?@deprecated\b(?m:\s*.*?))($START_OF_LINE_TAG_RE|$BLOCK_COMMENT_END_RE)"""
        .toPattern(Pattern.DOTALL)

/** A [Pattern] that matches a blank, i.e. white space only, doc comment. */
private val blankDocCommentPattern = """$DOC_COMMENT_START_RE\s*$BLOCK_COMMENT_END_RE""".toPattern()

/**
 * Revert the documentation change that accompanied a deprecation change.
 *
 * Deprecating an API requires adding an `@Deprecated` annotation and an `@deprecated` Javadoc tag
 * with text that explains why it is being deprecated and what will replace it. When the deprecation
 * change is being reverted then this will remove the `@deprecated` tag and its associated text to
 * avoid warnings when compiling and misleading information being written into the Javadoc.
 */
fun revertDocumentationDeprecationChange(currentItem: Item, docs: String): String {
    val actualItem = currentItem.actualItem
    // The documentation does not need to be reverted if...
    if (
        // the current item is not being reverted
        currentItem === actualItem
        // or if the current item and the actual item have the same deprecation setting
        ||
            currentItem.effectivelyDeprecated == actualItem.effectivelyDeprecated
            // or if the actual item is deprecated
            ||
            actualItem.effectivelyDeprecated
    )
        return docs

    // Find the `@deprecated` tag.
    val deprecatedTagMatcher = deprecatedTagPattern.matcher(docs)
    if (!deprecatedTagMatcher.find()) {
        // Nothing to do as the documentation does not include @deprecated.
        return docs
    }

    // Remove the @deprecated tag and associated text.
    val withoutDeprecated =
        // The part before the `@deprecated` tag.
        docs.substring(0, deprecatedTagMatcher.start(1)) +
            // The part after the `@deprecated` tag.
            docs.substring(deprecatedTagMatcher.end(1))

    // Check to see if the resulting document comment is empty and if it is then discard it all
    // together.
    val emptyDocCommentMatcher = blankDocCommentPattern.matcher(withoutDeprecated)
    return if (emptyDocCommentMatcher.matches()) {
        ""
    } else {
        withoutDeprecated
    }
}
