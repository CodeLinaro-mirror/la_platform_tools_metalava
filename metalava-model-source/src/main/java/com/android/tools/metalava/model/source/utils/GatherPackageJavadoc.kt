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

package com.android.tools.metalava.model.source.utils

import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/** The kinds of package documentation file. */
private enum class PackageDocumentationKind {
    PACKAGE {
        override fun update(packageDoc: MutablePackageDoc, contents: String, file: File) {
            packageDoc.commentFactory = packageHtmlToJavadoc(contents).toItemDocumentationFactory()
            packageDoc.fileLocation = FileLocation.forFile(file)
        }
    },
    OVERVIEW {
        override fun update(packageDoc: MutablePackageDoc, contents: String, file: File) {
            packageDoc.overview = contents
        }
    };

    /** Update kind appropriate property in [packageDoc] with [contents]. */
    abstract fun update(packageDoc: MutablePackageDoc, contents: String, file: File)
}

/**
 * Gather javadoc related to packages from the [sourceSet] and a list of model specific
 * [packageInfoFiles].
 *
 * This will look for `package.html` and `overview.html` files within the source set and then map
 * that back to a package. It will first check to see if there is a java class in the same directory
 * and if so then extract the package name from that otherwise it will construct one from the
 * directory, which may be wrong.
 *
 * If a `package.html` and `package-info.java` are provided for the same package then it will be
 * reported as an error and the comment from the latter will win.
 *
 * @param P the model specific `package-info.java` file type.
 * @param packageInfoFiles a collection of model specific `package-info.java` files.
 * @param packageInfoDocExtractor get a [MutablePackageDoc] from a model specific
 *   `package-info.java` file.
 */
fun <P> gatherPackageJavadoc(
    reporter: Reporter,
    sourceSet: SourceSet,
    packageInfoFiles: Collection<P>,
    packageInfoDocExtractor: (P) -> MutablePackageDoc?,
): PackageDocs {
    val packages = mutableMapOf<String, MutablePackageDoc>()
    val sortedSourceRoots = sourceSet.sourcePath.sortedBy { -it.name.length }
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

        // Figure out the package: if there is a java file in the same directory, get the package
        // name from the java file. Otherwise, guess from the directory path + source roots.
        // NOTE: This causes metalava to read files other than the ones explicitly passed to it.
        var pkg =
            file.parentFile
                ?.listFiles()
                ?.filter { it.name.endsWith(DOT_JAVA) }
                ?.asSequence()
                ?.mapNotNull { findPackage(it) }
                ?.firstOrNull()
        if (pkg == null) {
            // Strip the longest prefix source root.
            val prefix = sortedSourceRoots.firstOrNull { file.startsWith(it) }?.path ?: ""
            pkg = file.parentFile.path.substring(prefix.length).trim('/').replace("/", ".")
        }

        val packageDoc = packages.computeIfAbsent(pkg, ::MutablePackageDoc)

        val contents = file.readText(Charsets.UTF_8)
        documentationFile.update(packageDoc, contents, file)
    }

    // Merge package-info.java documentation.
    for (packageInfoFile in packageInfoFiles) {
        val (packageName, fileLocation, modifiers, comment, _) =
            packageInfoDocExtractor(packageInfoFile) ?: continue

        val packageDoc = packages.computeIfAbsent(packageName, ::MutablePackageDoc)
        if (packageDoc.commentFactory != null) {
            reporter.report(
                Issues.BOTH_PACKAGE_INFO_AND_HTML,
                null,
                "It is illegal to provide both a package-info.java file and " +
                    "a package.html file for the same package",
                fileLocation,
            )
        }

        // Always set this as package-info.java is preferred over package.html.
        packageDoc.fileLocation = fileLocation
        packageDoc.modifiers = modifiers
        packageDoc.commentFactory = comment
    }

    return PackageDocs(packages)
}
