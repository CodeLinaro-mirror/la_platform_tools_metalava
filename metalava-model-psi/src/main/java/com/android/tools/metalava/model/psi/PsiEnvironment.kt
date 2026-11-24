/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.tracing.Tracer
import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.computeMetadata
import com.android.tools.lint.detector.api.Project
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.AbstractEnvironment
import com.android.tools.metalava.model.source.Environment
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.Reporter
import com.intellij.pom.java.LanguageLevel
import java.io.File
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl

fun kotlinLanguageVersionSettings(value: String?): LanguageVersionSettings {
    val languageLevel =
        LanguageVersion.fromVersionString(value)
            ?: throw IllegalStateException(
                "$value is not a valid or supported Kotlin language level"
            )
    val apiVersion = ApiVersion.createByLanguageVersion(languageLevel)
    return LanguageVersionSettingsImpl(languageLevel, apiVersion)
}

/** PSI implementation of [Environment]. */
internal class PsiEnvironment(
    environmentManager: PsiEnvironmentManager,
    rawSourceSet: SourceSet,
    rawClassPath: List<File>,
    projectDescription: File?,
    javaLanguageLevel: String,
    kotlinLanguageLevel: String,
    jdkHome: File?,
    reporter: Reporter,
    tracer: Tracer,
) :
    AbstractEnvironment<PsiEnvironmentManager>(
        environmentManager = environmentManager,
        rawSourceSet = rawSourceSet,
        rawClassPath = rawClassPath,
        projectDescription = projectDescription,
        javaLanguageLevel = javaLanguageLevel,
        kotlinLanguageLevel = kotlinLanguageLevel,
        jdkHome = jdkHome,
        reporter = reporter,
        tracer = tracer,
    ) {
    private val parsedJavaLanguageLevel: LanguageLevel =
        javaLanguageLevelFromString(javaLanguageLevel)
    private val parsedKotlinLanguageLevel: LanguageVersionSettings =
        kotlinLanguageVersionSettings(kotlinLanguageLevel)

    /**
     * Lazily initialized [UastEnvironment] configured from [projectDescription], [sourceSet], and
     * [classPath].
     */
    val uastEnvironment: UastEnvironment by
        lazy(LazyThreadSafetyMode.NONE) {
            val config =
                tracer.trace("UastEnvironment.Configuration.create") {
                    UastEnvironment.Configuration.create()
                }
            config.javaLanguageLevel = parsedJavaLanguageLevel

            tracer.trace("configureUastEnvironment") {
                when (val projectDescription = projectDescription) {
                    null -> {
                        configureUastEnvironment(config, sourceSet.sourcePath, classPath)
                    }
                    else -> {
                        configureUastEnvironmentFromProjectDescription(config, projectDescription)
                    }
                }
            }

            val environment =
                tracer.trace("environmentManager.createEnvironment") {
                    environmentManager.createEnvironment(config)
                }
            val kotlinFiles = sourceSet.sources.filter { it.path.endsWith(SdkConstants.DOT_KT) }
            if (kotlinFiles.isNotEmpty() || projectDescription == null) {
                tracer.trace("environment.analyzeFiles") { environment.analyzeFiles(kotlinFiles) }
            }
            environment
        }

    override fun createSourceParser(codebaseConfig: Codebase.Config): SourceParser =
        PsiSourceParser(
            psiEnvironment = this,
            codebaseConfig = codebaseConfig,
            tracer = tracer,
        )

    /** Initializes a UAST environment using the [apiJars] as classpath roots. */
    fun loadUastFromJars(apiJars: List<File>): UastEnvironment {
        val config = UastEnvironment.Configuration.create()
        val sourceRoots = emptyList<File>()
        configureUastEnvironment(config, sourceRoots, apiJars)

        val environment = environmentManager.createEnvironment(config)
        environment.analyzeFiles(sourceRoots) // Initializes PSI machinery.
        return environment
    }

    private fun configureUastEnvironment(
        config: UastEnvironment.Configuration,
        sourceRoots: List<File>,
        classpath: List<File>,
    ) {
        val rootDir = sourceRoots.firstOrNull() ?: environmentManager.emptyDir
        val lintClient = MetalavaCliClient()
        // From ...lint.detector.api.Project, `dir` is, e.g., /tmp/foo/dev/src/project1,
        // and `referenceDir` is /tmp/foo/. However, in many use cases, they are just same.
        // `referenceDir` is used to adjust `lib` dir accordingly if needed,
        // but we set `classpath` anyway below.
        val lintProject =
            Project.create(lintClient, /* dir= */ rootDir, /* referenceDir= */ rootDir)
        lintProject.kotlinLanguageLevel = parsedKotlinLanguageLevel
        if (sourceRoots.isEmpty()) {
            lintProject.javaSourceFolders.add(environmentManager.emptyDir)
        } else {
            lintProject.javaSourceFolders.addAll(sourceRoots)
        }
        lintProject.javaLibraries.addAll(classpath)
        config.addModules(
            listOf(
                UastEnvironment.Module(
                    lintProject,
                    // Building KtSdkModule for JDK
                    jdkHome,
                    includeTests = false,
                    includeTestFixtureSources = false,
                    isUnitTest = false
                )
            ),
        )
    }

    /**
     * Configures the environment based on an XML description of Lint's project model.
     *
     * Alas, no proper documentation is available. Please refer to examples at upstream Lint:
     * https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:lint/libs/lint-tests/src/test/java/com/android/tools/lint/ProjectInitializerTest.kt
     *
     * An ideal project structure would look like:
     * ```
     * <project>
     *     <root dir="frameworks/support/compose/ui/ui"/>
     *     <module name="commonMain" android="false">
     *         <src file="src/commonMain/.../file1.kt" /> <!-- and so on -->
     *         <klib file="lib/if/any.klib" />
     *         <classpath jar="/path/to/kotlin/coroutinesCore.jar" />
     *         ...
     *     </module>
     *     <module name="jvmMain" android="false">
     *         <dep module="commonMain" kind="dependsOn" />
     *         <src file="src/jvmMain/.../file1.kt" /> <!-- and so on -->
     *         ...
     *     </module>
     *     <module name="androidMain" android="true">
     *         <dep module="jvmMain" kind="dependsOn" />
     *         <src file="src/androidMain/.../file1.kt" /> <!-- and so on -->
     *         ...
     *     </module>
     *     ...
     * </project>
     * ```
     *
     * That is, there are common modules where `expect` declarations and common business logic
     * reside, along with binary dependencies of several formats, including klib and jar.
     *
     * Then, platform-specific modules "depend" on common modules, and have their own source set and
     * binary dependencies.
     */
    private fun configureUastEnvironmentFromProjectDescription(
        config: UastEnvironment.Configuration,
        projectDescription: File,
    ) {
        val lintClient = MetalavaCliClient()
        // This will parse the description of Lint's project model and populate the module structure
        // inside the given Lint client. We will use it to set up the project structure that
        // [UastEnvironment] requires, which in turn uses that to set up Kotlin compiler frontend.
        // The overall flow looks like:
        //   project.xml -> Lint Project model -> UastEnvironment Module -> Kotlin compiler FE / AA
        // There are a couple of limitations that force use fall into this long steps:
        //  * Lint Project creation is not exposed at all. Only project.xml parsing is available.
        //  * UastEnvironment Module simply reuses existing Lint Project model.
        computeMetadata(lintClient, projectDescription)
        config.addModules(
            lintClient.knownProjects.mapNotNull { lintProject ->
                // TODO(b/383457595): For the given root dir,
                //   Lint creates a bogus, uninitialized [Project]
                if (
                    // The default project name, if not given, is directory name
                    // not something we provided, like `androidMain`.
                    lintProject.name == lintProject.dir.name &&
                        // source folder might be still the root dir
                        // but libraries would be empty / not computed.
                        (lintProject.javaSourceFolders.isEmpty() ||
                            lintProject.javaLibraries.isEmpty())
                ) {
                    return@mapNotNull null
                }
                lintProject.kotlinLanguageLevel = parsedKotlinLanguageLevel
                UastEnvironment.Module(
                    lintProject,
                    // Building KtSdkModule for JDK
                    jdkHome,
                    includeTests = false,
                    includeTestFixtureSources = false,
                    isUnitTest = false
                )
            }
        )
    }
}

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
