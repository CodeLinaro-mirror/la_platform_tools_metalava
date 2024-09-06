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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.transformer.CodebaseTransformer
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.testing.getAndroidJar
import java.io.File
import java.net.URLClassLoader

// @AutoService(ModelSuiteRunner::class)
class TextModelSuiteRunner : ModelSuiteRunner {

    override val providerName = "text"

    override val supportedInputFormats = setOf(InputFormat.SIGNATURE)

    override val capabilities: Set<Capability> = setOf()

    override fun createCodebaseAndRun(
        inputs: ModelSuiteRunner.TestInputs,
        test: (Codebase) -> Unit
    ) {
        if (inputs.commonSourceDir != null) {
            error("text model does not support common sources")
        }

        val signatureFiles = SignatureFile.fromFiles(inputs.mainSourceDir.createFiles())
        val resolver = ClassLoaderBasedClassResolver(getAndroidJar())
        val codebase = ApiFile.parseApi(signatureFiles, classResolver = resolver)

        // If available, transform the codebase for testing, otherwise use the one provided.
        val transformedCodebase = CodebaseTransformer.transformIfAvailable(codebase)

        test(transformedCodebase)
    }

    override fun toString() = providerName
}

/**
 * A [ClassResolver] that is backed by a [URLClassLoader].
 *
 * When [resolveClass] is called this will first look in [codebase] to see if the [ClassItem] has
 * already been loaded, returning it if found. Otherwise, it will look in the [classLoader] to see
 * if the class exists on the classpath. If it does then it will create a [DefaultClassItem] to
 * represent it and add it to the [codebase]. Otherwise, it will return `null`.
 *
 * The created [DefaultClassItem] is not a complete representation of the class that was found in
 * the [classLoader]. It is just a placeholder to indicate that it was found, although that may
 * change in the future.
 */
internal class ClassLoaderBasedClassResolver(jar: File) : ClassResolver {

    private val codebase by lazy {
        TextCodebase(
            location = jar,
            annotationManager = noOpAnnotationManager,
            classResolver = null,
        )
    }

    private val classLoader by lazy { URLClassLoader(arrayOf(jar.toURI().toURL()), null) }

    private fun findClassInClassLoader(qualifiedName: String): Class<*>? {
        var binaryName = qualifiedName
        do {
            try {
                return classLoader.loadClass(binaryName)
            } catch (e: ClassNotFoundException) {
                // If the class could not be found then maybe it was a nested class so replace the
                // last '.' in the name with a $ and try again. If there is no '.' then return.
                val lastDot = binaryName.lastIndexOf('.')
                if (lastDot == -1) {
                    return null
                } else {
                    val before = binaryName.substring(0, lastDot)
                    val after = binaryName.substring(lastDot + 1)
                    binaryName = "$before\$$after"
                }
            }
        } while (true)
    }

    override fun resolveClass(erasedName: String): ClassItem? {
        return codebase.findClass(erasedName)
            ?: run {
                val cls = findClassInClassLoader(erasedName) ?: return null
                val packageName = cls.`package`.name

                val itemFactory = codebase.assembler.itemFactory

                val packageItem = codebase.findOrCreatePackage(packageName)
                itemFactory.createClassItem(
                    fileLocation = FileLocation.UNKNOWN,
                    modifiers = createImmutableModifiers(VisibilityLevel.PACKAGE_PRIVATE),
                    classKind = ClassKind.CLASS,
                    containingClass = null,
                    containingPackage = packageItem,
                    qualifiedName = cls.canonicalName,
                    typeParameterList = TypeParameterList.NONE,
                    isFromClassPath = true,
                    superClassType = null,
                    interfaceTypes = emptyList(),
                )
            }
    }
}
