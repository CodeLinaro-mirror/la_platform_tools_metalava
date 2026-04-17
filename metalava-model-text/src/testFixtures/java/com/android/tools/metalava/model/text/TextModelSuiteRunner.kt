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
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.transformer.CodebaseTransformer
import com.android.tools.metalava.model.testsuite.JarSupport
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.testing.getAndroidJar
import java.io.File
import java.net.URLClassLoader

// @AutoService(ModelSuiteRunner::class)
class TextModelSuiteRunner : ModelSuiteRunner {

    override val providerName = "text"

    override val supportedInputFormats = setOf(InputFormat.SIGNATURE)

    override val capabilities: Set<Capability> =
        setOf(
            Capability.SIGNATURE,
        )

    override fun createCodebaseAndRun(
        inputs: ModelSuiteRunner.TestInputs,
        test: (Codebase?) -> Unit
    ) {
        if (inputs.projectDescription != null) {
            error("text model does not support project description")
        }

        val testFixture = inputs.testFixture
        val codebaseConfig = testFixture.codebaseConfig

        val signatureFiles = SignatureFile.forTest(inputs.mainSourceDir.createFiles())
        val classPath = listOf(getAndroidJar()) + inputs.testFixture.additionalClassPath
        val resolver = ClassLoaderBasedClassPathResolver(classPath, codebaseConfig)
        val codebase =
            ApiFile.parseApi(
                signatureFiles,
                codebaseConfig = codebaseConfig,
                classPathResolver = resolver,
            )

        // If available, transform the codebase for testing, otherwise use the one provided.
        val transformedCodebase = CodebaseTransformer.transformIfAvailable(codebase)

        test(transformedCodebase)
    }

    override fun createMultiplatformCodebaseAndRun(
        inputs: ModelSuiteRunner.TestInputs,
        test: (MultiplatformCodebase?) -> Unit
    ) {
        TODO("b/407735666")
    }

    override fun createJarSupportAndRun(test: (JarSupport) -> Unit) {
        error("should never be called")
    }

    override fun toString() = providerName
}

/**
 * A [ClassPathResolver] that is backed by a [URLClassLoader].
 *
 * When [resolveClass] is called this will first look in [codebase] to see if the [ClassItem] has
 * already been loaded, returning it if found. Otherwise, it will look in the [jars] to see if the
 * class exists on the classpath. If it does then it will create a [ClassItem] to represent it and
 * add it to the [codebase]. Otherwise, it will return `null`.
 *
 * The created [ClassItem] is not a complete representation of the class that was found in the
 * [jars]. It is just a placeholder to indicate that it was found, although that may change in the
 * future.
 */
class ClassLoaderBasedClassPathResolver(
    jars: List<File>,
    codebaseConfig: Codebase.Config = Codebase.Config.NOOP,
) : ClassPathResolver {

    private val assembler by
        lazy(LazyThreadSafetyMode.NONE) {
            ClassLoaderBasedCodebaseAssembler.createAssembler(jars, codebaseConfig)
        }

    private val codebase by lazy(LazyThreadSafetyMode.NONE) { assembler.codebase }

    override fun resolveClass(erasedName: String) = codebase.resolveClass(erasedName)

    override fun resolvePackage(pkgName: String) = codebase.resolvePackage(pkgName)
}
