/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationUse
import com.android.tools.metalava.model.AnnotationUse.TYPE_ONLY
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.PackageInfo
import com.android.tools.metalava.model.item.ResourceFile
import com.android.tools.metalava.model.source.utils.DOT_JAVA
import com.android.tools.metalava.model.source.utils.OVERVIEW_HTML
import com.android.tools.metalava.model.source.utils.PACKAGE_HTML
import com.android.tools.metalava.model.source.utils.findPackage
import com.android.tools.metalava.model.source.utils.packageHtmlToJavadoc
import com.android.tools.metalava.model.typeNullability
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import java.io.File

/** Provides support for assembling a [Codebase] from source files. */
abstract class SourceCodebaseAssembler : DefaultCodebaseAssembler() {
    /**
     * Provides additional information needed for creating a package.
     *
     * Initialized to [PackageDocs.EMPTY] but is temporarily overridden by [createInitialPackages].
     */
    private var packageDocs: PackageDocs = PackageDocs.EMPTY

    /** The kinds of package documentation file. */
    private enum class PackageDocumentationKind {
        PACKAGE {
            override fun update(packageDoc: MutablePackageDoc, file: File) {
                val contents = file.readText(Charsets.UTF_8)
                packageDoc.commentFactory =
                    packageHtmlToJavadoc(contents).toItemDocumentationFactory()
                packageDoc.fileLocation = FileLocation.forFile(file)
            }
        },
        OVERVIEW {
            override fun update(packageDoc: MutablePackageDoc, file: File) {
                packageDoc.overview = ResourceFile(file)
            }
        };

        /** Update kind appropriate property in [packageDoc] with [file]. */
        abstract fun update(packageDoc: MutablePackageDoc, file: File)
    }

    /**
     * Gather javadoc related to packages from the [sourceSet].
     *
     * This will look for `package.html` and `overview.html` files within the source set and then
     * map that back to a package. It will first check to see if there is a java class in the same
     * directory and if so then extract the package name from that otherwise it will construct one
     * from the directory, which may be wrong.
     *
     * @param sourceSet the sources to search for `package.html` and `overview.html` files.
     */
    private fun gatherPackageJavadoc(sourceSet: SourceSet): PackageDocs {
        val packages = mutableMapOf<String, MutablePackageDoc>()
        val sortedSourceRoots = sourceSet.sourcePath.sortedBy { -it.path.length }
        for (file in sourceSet.sources) {
            val documentationFile =
                when (file.name) {
                    PACKAGE_HTML -> {
                        PackageDocumentationKind.PACKAGE
                    }
                    OVERVIEW_HTML -> {
                        PackageDocumentationKind.OVERVIEW
                    }
                    else -> continue
                }

            // Figure out the package: if there is a java file in the same directory, get the
            // package
            // name from the java file. Otherwise, guess from the directory path + source roots.
            // NOTE: This causes metalava to read files other than the ones explicitly passed to it.
            var pkg =
                file.parentFile
                    ?.listFiles()
                    ?.filter { it.name.endsWith(DOT_JAVA) }
                    ?.asSequence()
                    ?.map { findPackage(it) }
                    ?.firstOrNull()
            if (pkg == null) {
                // Strip the longest prefix source root.
                val prefix = sortedSourceRoots.firstOrNull { file.startsWith(it) }?.path ?: ""
                pkg = file.parentFile.path.substring(prefix.length).trim('/').replace("/", ".")
            }

            // If the package name is invalid then skip it.
            if (!isValidPackage(pkg)) continue

            val packageDoc = packages.computeIfAbsent(pkg, ::MutablePackageDoc)

            documentationFile.update(packageDoc, file)
        }

        return PackageDocs(packages)
    }

    /** Create and track [PackageItem]s for every entry in [packageDocs]. */
    fun createInitialPackages(sourceSet: SourceSet) {
        // Make the packageDocs available when creating the packages below.
        this.packageDocs = gatherPackageJavadoc(sourceSet)

        // Create packages for all the documentation packages.
        for (packageName in packageDocs.packageNames) {
            codebase.findOrCreatePackage(packageName)
        }

        // Reset the package docs as they are no longer needed.
        this.packageDocs = PackageDocs.EMPTY
    }

    /**
     * Get the default [ItemDocumentationFactory] to use when the [PackageInfo] returned by
     * [getPackageInfoFromSource] has [PackageInfo.commentFactory] set to `null`.
     *
     * This selects the default [ItemDocumentationFactory] on each access as it relies on the
     * [codebase] which is not initialized until the subclass is initialized. A lazy property would
     * work, but it would no more efficient and has a higher overhead.
     */
    private val defaultCommentFactory: ItemDocumentationFactory
        get() =
            if (codebase.config.allowReadingComments) NO_SOURCE_COMMENT_FACTORY
            else ItemDocumentation.NONE_FACTORY

    /**
     * The default [PackageInfo] to return which uses [defaultCommentFactory].
     *
     * Created lazily as the [defaultCommentFactory] cannot be called during initialization as
     * [codebase] has not yet been initialized.
     */
    private val defaultPackageInfo by
        lazy(LazyThreadSafetyMode.NONE) { PackageInfo(commentFactory = defaultCommentFactory) }

    final override fun getPackageInfoFromUnderlyingModel(packageName: String): PackageInfo {
        val sourcePackageInfo = getPackageInfoFromSource(packageName)

        // Get the `PackageDoc`, if any, to use for creating this package.
        val packageDoc = packageDocs[packageName]

        if (packageDoc == null) {
            // Make sure the returned [PackageInfo] has a non-null [PackageInfo.commentFactory].
            return sourcePackageInfo?.toPackageInfo(defaultCommentFactory) ?: defaultPackageInfo
        } else if (sourcePackageInfo == null) {
            return packageDoc.toPackageInfo(defaultCommentFactory)
        }

        if (packageDoc.commentFactory != null && sourcePackageInfo.commentFactory != null) {
            codebase.reporter.report(
                Issues.BOTH_PACKAGE_INFO_AND_HTML,
                null,
                "It is illegal to provide both a package-info.java file and " +
                    "a package.html file for the same package",
                sourcePackageInfo.fileLocation,
            )
        }

        // Create a PackageInfo that combines information from PackageDoc, with the information from
        // the model taking precedence.
        val packageInfo =
            PackageInfo(
                fileLocation =
                    sourcePackageInfo.fileLocation.takeUnless { it == FileLocation.UNKNOWN }
                        ?: packageDoc.fileLocation,
                annotations = sourcePackageInfo.annotations,
                commentFactory =
                    // The comment returned from [getPackageInfoFromSource] takes precedence.
                    sourcePackageInfo.commentFactory
                        // Then the comment from any `package-info.java` files is next.
                        ?: packageDoc.commentFactory
                        // Finally, use the default to make sure it is not `null`.
                        ?: defaultCommentFactory,
                overview = packageDoc.overview,
            )

        return packageInfo
    }

    /**
     * Gets the [PackageInfo] from the underlying source model.
     *
     * See [CodebaseAssembler.getPackageInfoFromUnderlyingModel].
     *
     * Returns `null` when it cannot find any information about the package in the underlying model.
     * That does not mean the package does not exist it could mean that it exists but has no
     * `package-info.java` or `package-info.class` file.
     */
    protected abstract fun getPackageInfoFromSource(packageName: String): SourcePackageInfo?

    /**
     * Copy [AnnotationUse.TYPE_ONLY] only nullness annotations from types to [Item]s.
     *
     * The Psi model has historically included nullness annotations in the annotations for an item
     * even when those annotations are [AnnotationUse.TYPE_ONLY]. This replicates that behavior.
     *
     * This is not strictly the same as Psi, as Psi only does that for annotations that are used in
     * a context that means it could apply to either the declaration or the type. This simply always
     * copies them. That means that in theory the behavior could differ but in practice this does
     * not as type use only nullness annotations are not heavily used in Android or AndroidX.
     */
    fun copyTypeUseOnlyNullnessAnnotationsToItems() {
        codebase.accept(
            object : BaseItemVisitor() {
                override fun visitItem(item: Item) {
                    if (item is ClassItem || item is PackageItem) return
                    val type = item.type() ?: return

                    val itemAnnotations = item.modifiers.annotations()
                    if (itemAnnotations.typeNullability == null) {
                        val annotationToAdd =
                            type.modifiers.annotations.find { it.isNullnessAnnotation() }
                        if (
                            annotationToAdd != null &&
                                annotationToAdd.annotationUse == AnnotationUse.TYPE_ONLY
                        ) {
                            item.mutateModifiers { mutateAnnotations { add(annotationToAdd) } }
                        }
                    }
                }
            }
        )
    }
}

/**
 * A slightly modified version of [PackageInfo] used by [SourceCodebaseAssembler] to better handle
 * merging with [PackageDoc].
 *
 * This does not have an equivalent to [PackageInfo.overview] as that cannot be obtained from the
 * underlying model. That can only come from [PackageDoc.overview].
 */
data class SourcePackageInfo(
    /** See [PackageInfo.sourceFile] for details. */
    val sourceFile: SourceFile? = null,

    /** See [PackageInfo.annotations] for details. */
    val annotations: List<AnnotationItem> = emptyList(),

    /** See [PackageInfo.commentFactory] for details. */
    val commentFactory: ItemDocumentationFactory? = null,
) {
    /** See [PackageInfo.fileLocation] for details. */
    val fileLocation: FileLocation = sourceFile?.fileLocation ?: FileLocation.UNKNOWN

    /**
     * Construct a [PackageInfo] from this.
     *
     * @param defaultCommentFactory the default [ItemDocumentationFactory] to use if
     *   [commentFactory] is `null`.
     */
    fun toPackageInfo(defaultCommentFactory: ItemDocumentationFactory) =
        PackageInfo(
            fileLocation,
            sourceFile,
            annotations,
            // Make sure the returned [PackageInfo] has a non-null [PackageInfo.commentFactory].
            commentFactory ?: defaultCommentFactory,
        )
}
