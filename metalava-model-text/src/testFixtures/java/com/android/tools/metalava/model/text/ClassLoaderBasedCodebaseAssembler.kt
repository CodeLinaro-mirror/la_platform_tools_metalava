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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebaseFactory
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.PackageInfo
import com.android.tools.metalava.model.utils.splitIntoOptionalQualifierAndSimpleName
import com.android.tools.metalava.reporter.FileLocation
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * A [CodebaseAssembler] that will use information from a list of jars accessed through a
 * [URLClassLoader] to populate a [Codebase].
 *
 * At the moment it only supports populating [ClassItem]s (without any members or annotations).
 */
internal class ClassLoaderBasedCodebaseAssembler(
    jars: List<File>,
    codebaseFactory: DefaultCodebaseFactory,
) : DefaultCodebaseAssembler() {
    override val codebase = codebaseFactory(this)

    /** Creates [Item] instances for this. */
    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Class files do not contain information about whether an item was originally
            // created from Java or Kotlin.
            defaultSourceLanguage = SourceLanguage.UNKNOWN,
            // Class files do not have any information about API surfaces so they can use
            // the same immutable ApiVariantSelectors.
            defaultVariantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
        )

    private val classLoader by
        lazy(LazyThreadSafetyMode.NONE) {
            val urls = jars.map { it.toURI().toURL() }.toTypedArray()
            URLClassLoader(urls, null)
        }

    /**
     * Add all the possible package names in [packageName] to this [MutableSet].
     *
     * @param packageName is a `.` separated package name which is added to this [MutableSet] if it
     *   is not already in it. If [packageName] is not a top level package then all its containing
     *   packages are also added to this [MutableSet].
     */
    private fun MutableSet<String>.addAllPackageNames(packageName: String) {
        var name = packageName
        while (true) {
            if (contains(name)) return
            add(name)
            val index = name.lastIndexOf('.')
            if (index == -1) return
            name = name.substring(0, index)
        }
    }

    /**
     * The set of packages in [classLoader].
     *
     * Constructed by scanning all the entries in all the jars for any `.class` files and then
     * getting their package name and adding it and all its containing packages to the set.
     */
    private val packages by
        lazy(LazyThreadSafetyMode.NONE) {
            buildSet<String> {
                for (jar in jars) {
                    val jarFile = JarFile(jar)
                    for (jarEntry in jarFile.entries()) {
                        val path = jarEntry.name
                        if (path.endsWith(".class")) {
                            val packageName = path.substringBeforeLast('/').replace('/', '.')
                            addAllPackageNames(packageName)
                        }
                    }
                }
            }
        }

    /** Define the [Package] called [pkgName]. */
    private fun definePackage(pkgName: String): Package? {
        return definePackageMethod.invoke(classLoader, pkgName, classLoader.unnamedModule)
            as? Package
    }

    override fun getPackageInfoFromUnderlyingModel(packageName: String): PackageInfo {
        // Define the [Package] so it can access its annotations.
        val pkg = definePackage(packageName) ?: return PackageInfo.NO_COMMENT

        // Convert the annotation class, ignoring attributes for the moment.
        val annotationItems =
            pkg.annotations.mapNotNull { annotation ->
                annotation.annotationClass.qualifiedName?.let { qualifiedName ->
                    AnnotationItem.createMarkerAnnotation(codebase, qualifiedName)
                }
            }

        return PackageInfo(
            annotations = annotationItems,
            commentFactory = ItemDocumentation.NONE_FACTORY,
        )
    }

    override fun isValidPackage(packageName: String) = packageName in packages

    /**
     * Search for the class called [qualifiedName].
     *
     * This will attempt to find the class using [qualifiedName], returning it if found. Otherwise,
     * it will replace the last `.` with a `$` just in case it was for a nested class and search for
     * that name. It will repeat that until it finds the class or runs out of `.`s to replace.
     */
    private fun findClassInClassLoader(qualifiedName: String): Class<*>? {
        var binaryName = qualifiedName
        do {
            try {
                return classLoader.loadClass(binaryName)
            } catch (e: ClassNotFoundException) {
                // If the class could not be found then maybe it was a nested class so replace the
                // last '.' in the name with a $ and try again. If there is no '.' then return.
                val (before, after) = binaryName.splitIntoOptionalQualifierAndSimpleName()
                if (before == null) {
                    return null
                } else {
                    binaryName = "$before\$$after"
                }
            }
        } while (true)
    }

    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        val cls = findClassInClassLoader(qualifiedName) ?: return null
        val packageName = cls.`package`.name

        val classKind =
            when {
                cls.isAnnotation -> ClassKind.ANNOTATION_TYPE
                cls.isInterface -> ClassKind.INTERFACE
                cls.isEnum -> ClassKind.ENUM
                else -> ClassKind.CLASS
            }

        val packageItem = codebase.findOrCreatePackage(packageName)
        return itemFactory.createClassItem(
            fileLocation = FileLocation.UNKNOWN,
            modifiers = createImmutableModifiers(VisibilityLevel.PACKAGE_PRIVATE),
            classKind = classKind,
            containingClass = null,
            containingPackage = packageItem,
            qualifiedName = cls.canonicalName,
            typeParameterList = TypeParameterList.NONE,
            origin = ClassOrigin.CLASS_PATH,
            superClassType = null,
            interfaceTypes = emptyList(),
        )
    }

    companion object {
        /** Create a [ClassLoaderBasedCodebaseAssembler]. */
        fun createAssembler(
            jars: List<File>,
            codebaseConfig: Codebase.Config
        ): ClassLoaderBasedCodebaseAssembler {
            val location = jars.first()

            val assembler =
                ClassLoaderBasedCodebaseAssembler(
                    jars,
                    codebaseFactory = { assembler ->
                        DefaultCodebase(
                            location = location,
                            description = "Codebase for resolving classes in $location for tests",
                            preFiltered = true,
                            config = codebaseConfig,
                            trustedApi = true,
                            supportsDocumentation = false,
                            assembler = assembler,
                        )
                    },
                )

            return assembler
        }

        /** Access the private [ClassLoader.definePackage]. */
        private val definePackageMethod =
            ClassLoader::class.java.run {
                getDeclaredMethod("definePackage", String::class.java, Module::class.java).apply {
                    isAccessible = true
                }
            }
    }
}
