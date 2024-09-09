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

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.reporter.FileLocation

/** Set of [PackageDoc] for every documented package defined in the source. */
class PackageDocs(
    private val packages: Map<String, PackageDoc>,
) {
    /** The set of package names. */
    val packageNames: Collection<String> = packages.keys

    operator fun get(packageName: String): PackageDoc {
        return packages[packageName] ?: PackageDoc.EMPTY
    }

    companion object {
        val EMPTY: PackageDocs = PackageDocs(emptyMap())
    }
}

/** Package specific documentation. */
interface PackageDoc {
    val fileLocation: FileLocation
    val modifiers: BaseModifierList?

    /**
     * Factory for creating an [ItemDocumentation] instance containing the package level document.
     *
     * This factory will be invoked when creating the associated [PackageItem].
     *
     * If specified this is used for [PackageItem.documentation].
     */
    val commentFactory: ItemDocumentationFactory?

    /**
     * The `overview.html` file.
     *
     * If specified this is used for [PackageItem.overviewDocumentation].
     */
    val overview: ResourceFile?

    companion object {
        val EMPTY =
            object : PackageDoc {
                override val fileLocation: FileLocation
                    get() = FileLocation.UNKNOWN

                override val modifiers: BaseModifierList?
                    get() = null

                override val commentFactory
                    get() = null

                override val overview
                    get() = null
            }
    }
}

/** Mutable package specific documentation for use in [gatherPackageJavadoc]. */
data class MutablePackageDoc(
    val qualifiedName: String,
    override var fileLocation: FileLocation = FileLocation.UNKNOWN,
    override var modifiers: BaseModifierList? = null,
    override var commentFactory: ItemDocumentationFactory? = null,
    override var overview: ResourceFile? = null,
) : PackageDoc
