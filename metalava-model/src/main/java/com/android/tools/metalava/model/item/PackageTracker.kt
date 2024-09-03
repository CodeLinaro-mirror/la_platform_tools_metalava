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

typealias PackageItemFactory = (String, PackageDoc) -> DefaultPackageItem

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

    /** Add the package to this. */
    fun addPackage(packageItem: DefaultPackageItem) {
        packagesByName[packageItem.qualifiedName()] = packageItem
    }

    /** Create and track [PackageItem]s for every entry in [packageDocs]. */
    fun createInitialPackages(packageDocs: PackageDocs) {
        // Create packages for all the documentation packages.
        for ((packageName, packageDoc) in packageDocs) {
            // Consistency check to ensure that there are no collisions.
            findPackage(packageName)?.let {
                error("Duplicate package-info.java files found for $packageName")
            }

            val packageItem = packageItemFactory(packageName, packageDoc)
            addPackage(packageItem)
        }
    }
}
