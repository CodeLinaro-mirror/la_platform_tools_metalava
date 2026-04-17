/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.source.EnvironmentManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.javadoc.CustomJavadocTagProvider
import com.intellij.psi.javadoc.JavadocTagInfo
import java.io.File
import kotlin.io.path.createTempDirectory
import org.jetbrains.kotlin.config.CommonConfigurationKeys

/** Manages the [UastEnvironment] objects created when processing sources. */
internal class PsiEnvironmentManager(
    private val disableStderrDumping: Boolean = false,
    private val forTesting: Boolean = false,
) : EnvironmentManager {
    init {
        openManagerCount++
    }

    /**
     * An empty directory, used when it is necessary to create an environment without any source.
     * Simply providing an empty list of source roots will cause it to use the current working
     * directory.
     */
    internal val emptyDir by lazy {
        val path = createTempDirectory()
        val file = path.toFile()
        file.deleteOnExit()
        file
    }

    /**
     * Determines whether the manager has been closed. Used to prevent creating new environments
     * after the manager has closed.
     */
    private var closed = false

    /** The first environment created by the manager. */
    var initialEnvironment: UastEnvironment? = null
        private set

    /** The list of available environments. */
    private val uastEnvironments = mutableListOf<UastEnvironment>()

    init {
        if (forTesting) {
            System.setProperty("java.awt.headless", "true")
            Disposer.setDebugMode(true)
        }
    }

    /**
     * Create a [UastEnvironment] with the supplied configuration.
     *
     * @throws IllegalStateException if this manager has been closed.
     */
    internal fun createEnvironment(config: UastEnvironment.Configuration): UastEnvironment {
        if (closed) {
            throw IllegalStateException("PsiEnvironmentManager is closed")
        }
        ensurePsiFileCapacity()

        // Note: the Kotlin module name affects the naming of certain synthetic methods.
        config.kotlinCompilerConfig.put(
            CommonConfigurationKeys.MODULE_NAME,
            METALAVA_SYNTHETIC_SUFFIX
        )

        val environment = UastEnvironment.create(config)
        uastEnvironments.add(environment)
        if (initialEnvironment == null) {
            initialEnvironment = environment
        }

        if (disableStderrDumping) {
            DefaultLogger.disableStderrDumping(environment.ideaProject)
        }

        // Missing service needed in metalava but not in lint: javadoc handling
        environment.ideaProject.registerService(
            com.intellij.psi.javadoc.JavadocManager::class.java,
            com.intellij.psi.impl.source.javadoc.JavadocManagerImpl::class.java
        )
        CoreApplicationEnvironment.registerExtensionPoint(
            environment.ideaProject.extensionArea,
            JavadocTagInfo.EP_NAME,
            JavadocTagInfo::class.java
        )
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            CustomJavadocTagProvider.EP_NAME,
            CustomJavadocTagProvider::class.java
        )

        return environment
    }

    private fun ensurePsiFileCapacity() {
        val fileSize = System.getProperty("idea.max.intellisense.filesize")
        if (fileSize == null) {
            // Ensure we can handle large compilation units like android.R
            System.setProperty("idea.max.intellisense.filesize", "100000")
        }
    }

    override fun createSourceParser(
        codebaseConfig: Codebase.Config,
        javaLanguageLevel: String,
        kotlinLanguageLevel: String,
        modelOptions: ModelOptions,
        jdkHome: File?,
    ): PsiSourceParser {
        return PsiSourceParser(
            psiEnvironmentManager = this,
            codebaseConfig = codebaseConfig,
            javaLanguageLevel = javaLanguageLevelFromString(javaLanguageLevel),
            kotlinLanguageLevel = kotlinLanguageVersionSettings(kotlinLanguageLevel),
            jdkHome = jdkHome,
        )
    }

    override fun close() {
        closed = true

        // Codebase.dispose() is not consistently called, so we dispose the environments here too.
        for (env in uastEnvironments) {
            if (!env.ideaProject.isDisposed) {
                env.dispose()
            }
        }
        uastEnvironments.clear()
        initialEnvironment = null

        // Only dispose of the application environment if this is the final environment to close.
        // If it was not then there is no point in checking to make sure that [Disposer] is empty
        // because it will include items that have not yet been disposed of by the other open
        // [PsiEnvironmentManager]s.
        openManagerCount--
        if (openManagerCount == 0) {
            UastEnvironment.disposeApplicationEnvironment()
            if (forTesting) {
                Disposer.assertIsEmpty(true)
            }
        }
    }

    companion object {
        /**
         * Track how many open [PsiEnvironmentManager]s exist. This is so that when the final
         * manager is closed, it can ensure that everything has been disposed.
         */
        private var openManagerCount = 0
    }
}

private const val METALAVA_SYNTHETIC_SUFFIX = "metalava_module"

private fun javaLanguageLevelFromString(value: String): LanguageLevel {
    val level = LanguageLevel.parse(value)
    when {
        level == null ->
            throw IllegalStateException("$value is not a valid or supported Java language level")
        level.isLessThan(LanguageLevel.JDK_1_7) ->
            throw IllegalStateException("$value must be at least 1.7")
        else -> return level
    }
}
