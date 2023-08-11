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
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.psi.javadoc.CustomJavadocTagProvider
import com.intellij.psi.javadoc.JavadocTagInfo
import java.io.Closeable
import org.jetbrains.kotlin.config.CommonConfigurationKeys

/** Manages the [UastEnvironment] objects created when processing sources. */
class PsiEnvironmentManager(private val disableStderrDumping: Boolean = false) : Closeable {

    /**
     * Determines whether the manager has been closed. Used to prevent creating new environments
     * after the manager has closed.
     */
    private var closed = false

    /** The list of available environments. */
    private val uastEnvironments = mutableListOf<UastEnvironment>()

    /**
     * Create a [UastEnvironment] with the supplied configuration.
     *
     * @throws IllegalStateException if this manager has been closed.
     */
    fun createEnvironment(config: UastEnvironment.Configuration): UastEnvironment {
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

    override fun close() {
        closed = true

        // Codebase.dispose() is not consistently called, so we dispose the environments here too.
        for (env in uastEnvironments) {
            if (!env.ideaProject.isDisposed) {
                env.dispose()
            }
        }
        uastEnvironments.clear()
        UastEnvironment.disposeApplicationEnvironment()
    }
}

private const val METALAVA_SYNTHETIC_SUFFIX = "metalava_module"
