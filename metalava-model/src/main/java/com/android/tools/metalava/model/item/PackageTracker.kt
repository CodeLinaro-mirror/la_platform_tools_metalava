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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import java.util.HashMap

private const val PACKAGE_ESTIMATE = 500

typealias PackageItemFactory = (String, PackageDoc, PackageItem?) -> DefaultPackageItem

class PackageTracker(private val packageItemFactory: PackageItemFactory) {
    /** Map from package name to [DefaultPackageItem] of all packages in this. */
    private val packagesByName = HashMap<String, DefaultPackageItem>(PACKAGE_ESTIMATE)

    val size
        get() = packagesByName.size

    fun getPackages(): PackageList {
        val list = packagesByName.values.toMutableList()
        list.sortWith(PackageItem.comparator)
        return PackageList(list)
    }

    fun findPackage(pkgName: String): DefaultPackageItem? {
        return packagesByName[pkgName]
    }

    /**
     * Searches for the package with [packageName] in this tracker and if not found creates the
     * corresponding [DefaultPackageItem], supply additional information from [packageDocs] and adds
     * the newly created [DefaultPackageItem] to this tracker.
     *
     * If the [DefaultPackageItem] exists and [PackageDocs] contains [PackageDoc.modifiers] for the
     * package then make sure that the existing [DefaultPackageItem] has the same
     * [DefaultPackageItem.modifiers], if not throw an exception.
     *
     * @param packageName the name of the package to create.
     * @param packageDocs provides additional information needed for creating a package.
     * @param emit if `true` then the package was created from sources that should be emitted as
     *   part of the current API surface and so it should have its [PackageItem.emit] property set
     *   to `true`, whether this call finds it or creates it.
     * @return the [DefaultPackageItem] that was found or created.
     */
    fun findOrCreatePackage(
        packageName: String,
        packageDocs: PackageDocs = PackageDocs.EMPTY,
        emit: Boolean = true,
    ): DefaultPackageItem {
        // Get the `PackageDoc`, if any, to use for creating this package.
        val packageDoc = packageDocs[packageName]

        // Check to see if the package already exists, if it does then return it along with
        // `created = false` to show that this did not create the package.
        findPackage(packageName)?.let { existing ->
            // If the same package showed up multiple times, make sure they have the same modifiers.
            // (Packages can't have public/private/etc., but they can have annotations, which are
            // part of ModifierList.)
            val modifiers = packageDoc.modifiers
            if (modifiers != null && modifiers != existing.modifiers) {
                error(
                    String.format(
                        "Contradicting declaration of package %s." +
                            " Previously seen with modifiers \"%s\", but now with \"%s\"",
                        packageName,
                        existing.modifiers,
                        modifiers
                    ),
                )
            }

            // If this package should be emitted then set its `emit` property to `true`, otherwise
            // leave it unchanged. That ensures that once a package has had its `emit` property to
            // `true` it cannot become `false`.
            if (emit) {
                existing.emit = true
            }

            return existing
        }

        // Unless this is the root package, it has a containing package so get that before creating
        // this package, so it can be passed into the `packageItemFactory`.
        val containingPackageName = getContainingPackageName(packageName)
        val containingPackage =
            if (containingPackageName == null) null
            else
                findOrCreatePackage(
                    containingPackageName,
                    packageDocs,
                    // A package should not be included in the API surface unless it contains
                    // classes that belong to that API surface. This call passes in `emit = false`
                    // to ensure that a package which is created solely as a containing package will
                    // not be included in the API surface.
                    emit = false,
                )

        val packageItem = packageItemFactory(packageName, packageDoc, containingPackage)
        addPackage(packageItem)

        // Newly created package's `emit` property is determined completely by the `emit` parameter
        // supplied.
        packageItem.emit = emit

        return packageItem
    }

    /**
     * Gets the name of [packageName]'s containing package or `null` if [packageName] is `""`, i.e.
     * the root package.
     */
    private fun getContainingPackageName(packageName: String): String? =
        if (packageName == "") null
        else
            packageName.lastIndexOf('.').let { index ->
                if (index == -1) {
                    ""
                } else {
                    packageName.substring(0, index)
                }
            }

    /** Add the package to this. */
    fun addPackage(packageItem: DefaultPackageItem) {
        packagesByName[packageItem.qualifiedName()] = packageItem
    }

    /**
     * Create and track [PackageItem]s for every entry in [packageDocs] and make sure there is a
     * root package.
     */
    fun createInitialPackages(packageDocs: PackageDocs) {
        // Create packages for all the documentation packages.
        for (packageName in packageDocs.packageNames) {
            findOrCreatePackage(packageName, packageDocs)
        }

        // Make sure that there is a root package.
        findOrCreatePackage("", packageDocs)
    }
}
