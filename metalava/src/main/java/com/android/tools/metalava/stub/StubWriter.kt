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

import com.android.tools.metalava.model.CallableItem
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
import com.android.tools.metalava.model.item.ResourceFile
import com.android.tools.metalava.model.psi.trimDocIndent
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.tools.metalava.model.visitors.MatchOverridingMethodPredicate
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer

internal class StubWriter(
    private val stubsDir: File,
    private val generateAnnotations: Boolean = false,
    private val docStubs: Boolean,
    private val reporter: Reporter,
    private val config: StubWriterConfig,
    private val stubConstructorManager: StubConstructorManager,
) : DelegatedVisitor {

    /**
     * Stubs need to preserve class nesting when visiting otherwise nested classes will not be
     * nested inside their containing class properly.
     */
    override val requiresClassNesting: Boolean
        get() = true

    override fun visitPackage(pkg: PackageItem) {
        getPackageDir(pkg, create = true)

        writePackageInfo(pkg)

        if (docStubs) {
            pkg.overviewDocumentation?.let { writeDocOverview(pkg, it) }
        }
    }

    fun writeDocOverview(pkg: PackageItem, resourceFile: ResourceFile) {
        val content = resourceFile.content
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
                    JavaStubWriter(textWriter, modifierListWriter, config, stubConstructorManager)
                }

            // Copyright statements from the original file?
            cls.sourceFile()?.getHeaderComments()?.let { textWriter.println(it) }
        }
        stubWriter?.visitClass(cls)

        dispatchStubsConstructorIfAvailable(cls)
    }

    /**
     * Stubs that have no accessible constructor may still need to generate one and that constructor
     * is available from [StubConstructorManager.optionalSyntheticConstructor].
     */
    private fun dispatchStubsConstructorIfAvailable(cls: ClassItem) {
        // If a special constructor had to be synthesized for the class then it will not be in the
        // ClassItem's list of constructors that would be visited automatically. So, this will visit
        // it explicitly to make sure it appears in the stubs.
        val syntheticConstructor = stubConstructorManager.optionalSyntheticConstructor(cls)
        if (syntheticConstructor != null) {
            visitConstructor(syntheticConstructor)
        }
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
}

/**
 * Create an [ApiVisitor] that will filter the [Item] to which is applied according to the supplied
 * parameters and in a manner appropriate for writing stubs, e.g. nesting classes. It will delegate
 * any visitor calls that pass through its filter to this [StubWriter] instance.
 */
fun createFilteringVisitorForStubs(
    delegate: DelegatedVisitor,
    docStubs: Boolean,
    preFiltered: Boolean,
    apiPredicateConfig: ApiPredicate.Config,
    ignoreEmit: Boolean = false,
): ItemVisitor {
    val filterReference =
        ApiPredicate(
            includeDocOnly = docStubs,
            config = apiPredicateConfig.copy(ignoreShown = true),
        )
    val filterEmit = MatchOverridingMethodPredicate(filterReference)
    val apiFilters =
        ApiFilters(
            emit = filterEmit,
            reference = filterReference,
        )
    return FilteringApiVisitor(
        delegate = delegate,
        inlineInheritedFields = true,
        // Sort methods in stubs based on their signature. The order of methods in stubs is
        // irrelevant, e.g. it does not affect compilation or document generation. However, having a
        // consistent order will prevent churn in the generated stubs caused by changes to Metalava
        // itself or changes to the order of methods in the sources.
        callableComparator = CallableItem.comparator,
        apiFilters = apiFilters,
        preFiltered = preFiltered,
        ignoreEmit = ignoreEmit,
    )
}

internal fun appendDocumentation(item: Item, writer: PrintWriter, config: StubWriterConfig) {
    if (config.includeDocumentationInStubs) {
        val documentation = item.documentation
        val text = documentation.fullyQualifiedDocumentation()
        if (text.isNotBlank()) {
            val trimmed = trimDocIndent(text)
            writer.println(trimmed)
            writer.println()
        }
    }
}
