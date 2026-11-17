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

import androidx.tracing.Tracer
import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.item.SealedClassImplicitPermitTypesUpdater
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.psi.kotlin.KaCodebaseAssembler
import com.android.tools.metalava.model.psi.kotlin.KotlinBytecodeApis
import com.android.tools.metalava.model.source.AbstractSourceParser
import com.android.tools.metalava.model.source.SourceSet
import java.io.File
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.KotlinStaticProjectStructureProvider

/**
 * Parses a set of sources into a [PsiBasedCodebase].
 *
 * The codebases will use the [UastEnvironment] from [psiEnvironment].
 */
internal class PsiSourceParser(
    private val psiEnvironment: PsiEnvironment,
    codebaseConfig: Codebase.Config,
    tracer: Tracer,
) :
    AbstractSourceParser(
        codebaseConfig,
        tracer,
    ) {
    override fun getClassPathResolver(): ClassPathResolver =
        createCodebase(
            sourceSet = SourceSet.empty(),
            description = "Codebase from classpath",
            apiPackages = null,
            compiledSourceJar = null,
            // A ClassPathResolver is used when resolving references from a signature file, possibly
            // for the previously released API, so it must not have a reference to the previously
            // released API itself.
            codebaseConfig = codebaseConfig.copy(previouslyReleasedCodebaseProvider = { null }),
        )

    override fun processSources(
        description: String,
        apiPackages: PackageFilter?,
        compiledSourceJar: File?,
    ): Codebase =
        createCodebase(
            sourceSet = psiEnvironment.sourceSet,
            description = description,
            apiPackages = apiPackages,
            compiledSourceJar = compiledSourceJar,
        )

    override fun processJavaStubs(
        javaStubFiles: List<File>,
        apiPackages: PackageFilter?,
    ): Codebase =
        createCodebase(
            sourceSet =
                SourceSet(javaStubFiles, psiEnvironment.sourceSet.sourcePath)
                    .extractRoots(reporter),
            description = "Codebase loaded from stubs",
            apiPackages = apiPackages,
            compiledSourceJar = null,
        )

    /**
     * Returns a codebase initialized from the given [sourceSet], with the given [description],
     * using [PsiEnvironment.uastEnvironment].
     */
    private fun createCodebase(
        sourceSet: SourceSet,
        description: String,
        apiPackages: PackageFilter?,
        compiledSourceJar: File?,
        codebaseConfig: Codebase.Config = this.codebaseConfig,
    ): PsiBasedCodebase {
        val environment = psiEnvironment.uastEnvironment

        val location = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile
        val assembler =
            tracer.trace("PsiCodebaseAssembler") {
                PsiCodebaseAssembler(environment) {
                    PsiBasedCodebase(
                        location = location,
                        description = description,
                        config = codebaseConfig,
                        assembler = it,
                        inlineTypeAliasUsages = environment.isKMP,
                        mainAnalysisModule = findMainAnalysisModule(environment),
                    )
                }
            }

        tracer.trace("assembler.initializeFromSources") {
            assembler.initializeFromSources(
                sourceSet,
                apiPackages,
                tracer,
            )
        }
        val codebase = assembler.psiCodebase

        compiledSourceJar?.let { tracer.trace("mergeFromJar") { mergeFromJar(codebase, it) } }

        // Update implicit permit types in any sealed class that does not have one provided.
        SealedClassImplicitPermitTypesUpdater.updateImplicitPermitTypes(codebase)

        return codebase
    }

    /** Lists all of the [KaModule]s that exist in this project. */
    @OptIn(KaPlatformInterface::class)
    private fun UastEnvironment.findAllSourceModules(): List<KaSourceModule> {
        return (KotlinProjectStructureProvider.getInstance(ideaProject)
                as? KotlinStaticProjectStructureProvider)
            ?.allModules
            ?.filterIsInstance<KaSourceModule>() ?: emptyList()
    }

    /**
     * Attempts to locate the [KaModule] which should be used to create kotlin-only APIs through the
     * analysis API when creating a regular [Codebase].
     *
     * For non-KMP sources, this will be the only module in the project. For KMP sources, this will
     * be either the androidMain or jvmMain module.
     *
     * All platforms are analyzed when using [createMultiplatformCodebase], but only the main module
     * is used for the [Codebase] created by [parseSources].
     */
    private fun findMainAnalysisModule(environment: UastEnvironment): KaSourceModule? {
        val modules = environment.findAllSourceModules()
        return modules.singleOrNull()
            ?: modules.singleOrNull { it.name == "androidMain" }
            ?: modules.singleOrNull { it.name == "jvmMain" }
    }

    override fun createMultiplatformCodebase(): MultiplatformCodebase {
        val projectDescription =
            psiEnvironment.projectDescription ?: error("No projectDescription configured")
        val environment = tracer.trace("create environment") { psiEnvironment.uastEnvironment }

        return tracer.trace("KaCodebaseAssembler.assembleMultiplatform") {
            KaCodebaseAssembler.assembleMultiplatform(
                environment.findAllSourceModules(),
                projectDescription,
                codebaseConfig,
                tracer
            )
        }
    }

    fun mergeFromJar(existingCodebase: PsiBasedCodebase, jarFile: File) {
        val bytecodeApis =
            tracer.trace("KotlinBytecodeApis") { KotlinBytecodeApis(existingCodebase.psiAssembler) }
        val rewrittenJar = tracer.trace("rewriteJar") { bytecodeApis.rewriteJar(jarFile) }
        val jarEnvironment =
            tracer.trace("loadUastFromJars") {
                psiEnvironment.loadUastFromJars(listOf(rewrittenJar))
            }
        tracer.trace("loadPsiFromProject") {
            bytecodeApis.loadPsiFromProject(jarEnvironment.ideaProject)
        }
        (existingCodebase.assembler as PsiCodebaseAssembler).mergedJarEnvironment = jarEnvironment
    }
}
