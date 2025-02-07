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

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.computeMetadata
import com.android.tools.lint.detector.api.Project
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.intellij.pom.java.LanguageLevel
import java.io.File
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl

internal val defaultJavaLanguageLevel = LanguageLevel.parse(DEFAULT_JAVA_LANGUAGE_LEVEL)!!

internal val defaultKotlinLanguageLevel = LanguageVersionSettingsImpl.DEFAULT

fun kotlinLanguageVersionSettings(value: String?): LanguageVersionSettings {
    val languageLevel =
        LanguageVersion.fromVersionString(value)
            ?: throw IllegalStateException(
                "$value is not a valid or supported Kotlin language level"
            )
    val apiVersion = ApiVersion.createByLanguageVersion(languageLevel)
    return LanguageVersionSettingsImpl(languageLevel, apiVersion)
}

/**
 * Parses a set of sources into a [PsiBasedCodebase].
 *
 * The codebases will use a project environment initialized according to the properties passed to
 * the constructor and the paths passed to [parseSources].
 */
internal class PsiSourceParser(
    private val psiEnvironmentManager: PsiEnvironmentManager,
    private val codebaseConfig: Codebase.Config,
    private val javaLanguageLevel: LanguageLevel,
    private val kotlinLanguageLevel: LanguageVersionSettings,
    private val useK2Uast: Boolean,
    private val allowReadingComments: Boolean,
    private val jdkHome: File?,
) : SourceParser {

    private val reporter = codebaseConfig.reporter

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        val uastEnvironment = loadUastFromJars(classPath)
        return PsiBasedClassResolver(
            uastEnvironment,
            codebaseConfig,
            allowReadingComments,
        )
    }

    /**
     * Returns a codebase initialized from the given Java or Kotlin source files, with the given
     * description.
     *
     * All supplied [File] objects will be mapped to [File.getAbsoluteFile].
     */
    override fun parseSources(
        sourceSet: SourceSet,
        description: String,
        classPath: List<File>,
        apiPackages: PackageFilter?,
        projectDescription: File?,
    ): Codebase {
        return parseAbsoluteSources(
            sourceSet.absoluteCopy().extractRoots(reporter),
            description,
            classPath.map { it.absoluteFile },
            apiPackages,
            projectDescription,
        )
    }

    /** Returns a codebase initialized from the given set of absolute files. */
    private fun parseAbsoluteSources(
        sourceSet: SourceSet,
        description: String,
        classpath: List<File>,
        apiPackages: PackageFilter?,
        projectDescription: File?,
    ): PsiBasedCodebase {
        val config = UastEnvironment.Configuration.create(useFirUast = useK2Uast)
        config.javaLanguageLevel = javaLanguageLevel

        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile

        when {
            projectDescription != null -> {
                configureUastEnvironmentFromProjectDescription(config, projectDescription)
            }
            else -> {
                configureUastEnvironment(config, sourceSet.sourcePath, classpath, rootDir)
            }
        }
        // K1 UAST: loading of JDK (via compiler config, i.e., only for FE1.0), when using JDK9+
        jdkHome?.let {
            if (isJdkModular(it)) {
                config.kotlinCompilerConfig.put(JVMConfigurationKeys.JDK_HOME, it)
                config.kotlinCompilerConfig.put(JVMConfigurationKeys.NO_JDK, false)
            }
        }

        val environment = psiEnvironmentManager.createEnvironment(config)
        val kotlinFiles = sourceSet.sources.filter { it.path.endsWith(SdkConstants.DOT_KT) }
        environment.analyzeFiles(kotlinFiles)

        val assembler =
            PsiCodebaseAssembler(environment) {
                PsiBasedCodebase(
                    location = rootDir,
                    description = description,
                    config = codebaseConfig,
                    allowReadingComments = allowReadingComments,
                    assembler = it,
                )
            }

        assembler.initializeFromSources(sourceSet, apiPackages)
        return assembler.codebase
    }

    private fun isJdkModular(homePath: File): Boolean {
        return File(homePath, "jmods").isDirectory
    }

    override fun loadFromJar(apiJar: File): Codebase {
        val environment = loadUastFromJars(listOf(apiJar))
        val assembler =
            PsiCodebaseAssembler(environment) { assembler ->
                PsiBasedCodebase(
                    location = apiJar,
                    description = "Codebase loaded from $apiJar",
                    config = codebaseConfig,
                    allowReadingComments = allowReadingComments,
                    assembler = assembler,
                )
            }
        val codebase = assembler.codebase
        assembler.initializeFromJar(apiJar)
        return codebase
    }

    /** Initializes a UAST environment using the [apiJars] as classpath roots. */
    private fun loadUastFromJars(apiJars: List<File>): UastEnvironment {
        val config = UastEnvironment.Configuration.create(useFirUast = useK2Uast)
        // Use the empty dir otherwise this will end up scanning the current working directory.
        configureUastEnvironment(config, listOf(psiEnvironmentManager.emptyDir), apiJars)

        val environment = psiEnvironmentManager.createEnvironment(config)
        environment.analyzeFiles(emptyList()) // Initializes PSI machinery.
        return environment
    }

    private fun configureUastEnvironment(
        config: UastEnvironment.Configuration,
        sourceRoots: List<File>,
        classpath: List<File>,
        rootDir: File = sourceRoots.firstOrNull() ?: File("").canonicalFile
    ) {
        val lintClient = MetalavaCliClient()
        // From ...lint.detector.api.Project, `dir` is, e.g., /tmp/foo/dev/src/project1,
        // and `referenceDir` is /tmp/foo/. However, in many use cases, they are just same.
        // `referenceDir` is used to adjust `lib` dir accordingly if needed,
        // but we set `classpath` anyway below.
        val lintProject =
            Project.create(lintClient, /* dir = */ rootDir, /* referenceDir = */ rootDir)
        lintProject.kotlinLanguageLevel = kotlinLanguageLevel
        lintProject.javaSourceFolders.addAll(sourceRoots)
        lintProject.javaLibraries.addAll(classpath)
        config.addModules(
            listOf(
                UastEnvironment.Module(
                    lintProject,
                    // K2 UAST: building KtSdkModule for JDK
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
                lintProject.kotlinLanguageLevel = kotlinLanguageLevel
                UastEnvironment.Module(
                    lintProject,
                    // K2 UAST: building KtSdkModule for JDK
                    jdkHome,
                    includeTests = false,
                    includeTestFixtureSources = false,
                    isUnitTest = false
                )
            }
        )
    }

    companion object {
        private const val AAR = "aar"
        private const val JAR = "jar"
        private const val KLIB = "klib"
        private val SUPPORTED_CLASSPATH_EXT = listOf(AAR, JAR, KLIB)
    }
}
