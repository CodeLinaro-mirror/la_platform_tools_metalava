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

package com.android.tools.metalava.model.turbine

import androidx.tracing.Tracer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.AbstractEnvironment
import com.android.tools.metalava.model.source.Environment
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.MoreExecutors
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPath
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.JimageClassBinder
import com.google.turbine.binder.Processing.ProcessorInfo
import com.google.turbine.diag.SourceFile
import com.google.turbine.diag.TurbineDiagnostic
import com.google.turbine.diag.TurbineError
import com.google.turbine.diag.TurbineLog
import com.google.turbine.parse.Parser
import com.google.turbine.tree.Tree.CompUnit
import java.io.File
import java.nio.file.Paths
import java.util.Optional
import javax.lang.model.SourceVersion

/** Encapsulates a [SourceSet] along with its parsed [allUnits] and bound [bindingResult]. */
internal class BoundSources(
    val sourceSet: SourceSet,
    val allUnits: ImmutableList<CompUnit>,
    val bindingResult: BindingResult,
)

/** Turbine implementation of [Environment]. */
internal class TurbineEnvironment(
    environmentManager: TurbineEnvironmentManager,
    rawSourceSet: SourceSet,
    rawClassPath: List<File>,
    projectDescription: File?,
    javaLanguageLevel: String,
    kotlinLanguageLevel: String,
    jdkHome: File?,
    reporter: Reporter,
    tracer: Tracer,
) :
    AbstractEnvironment<TurbineEnvironmentManager>(
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
    private val bootclasspath: ClassPath by
        lazy(LazyThreadSafetyMode.NONE) {
            tracer.trace("turbine.bindBootclasspath") {
                jdkHome?.let { home -> JimageClassBinder.bind(home.path) }
                    ?: ClassPathBinder.bindClasspath(listOf())
            }
        }

    private val boundClassPath: ClassPath by
        lazy(LazyThreadSafetyMode.NONE) {
            tracer.trace("turbine.bindClasspath") {
                ClassPathBinder.bindClasspath(classPath.map { it.toPath() })
            }
        }

    /**
     * All parsed [CompUnit]s from [sourceSet] (both [SourceSet.sources] and extra files on
     * [SourceSet.sourcePath]), or `null` if parsing failed with syntax errors.
     */
    private val allUnits: ImmutableList<CompUnit>? by
        lazy(LazyThreadSafetyMode.NONE) {
            // Any non-fatal error (like unresolved symbols) will be captured in this log and will
            // be handled below.
            val log = TurbineLog()

            // Get the units from the source files provided on the command line.
            val commandLineSources = sourceSet.sources
            val sourceFiles =
                tracer.trace("turbine.getSourceFiles") {
                    getSourceFiles(commandLineSources.asSequence())
                }
            val units =
                tracer.trace("turbine.parseSourceFiles") {
                    sourceFiles.mapNotNull { parse(log, it) }
                }

            // Get the sequence of all files that can be found on the source path which are not
            // explicitly listed on the command line.
            val scannedFiles =
                tracer.trace("turbine.scanSourceFiles") {
                    scanSourcePath(sourceSet.sourcePath, commandLineSources.toSet())
                }
            val sourcePathFiles =
                tracer.trace("turbine.getExtraSourceFiles") { getSourceFiles(scannedFiles) }

            // Get the set of qualified class names provided on the command line. If a `.java` file
            // contains multiple java classes then it just used the main class name.
            val commandLineClasses =
                units.mapNotNull { unit -> unit.mainClassQualifiedName }.toSet()

            // Get the units for the extra source files found on the source path.
            val extraUnits =
                tracer.trace("turbine.parseExtraSourceFiles") {
                    sourcePathFiles
                        .mapNotNull { parse(log, it) }

                        // Ignore any files that contain duplicates of a class that was specified on
                        // the command line. This is needed when merging annotations from other java
                        // files as there may be duplicate definitions of the class on the source
                        // path.
                        .filter { unit -> unit.mainClassQualifiedName !in commandLineClasses }
                }

            // If any errors were reported during parsing then report them and abort.
            if (log.anyErrors()) {
                log.reportTo(reporter)
                return@lazy null
            }

            // Combine all the units together.
            ImmutableList.builder<CompUnit>().addAll(units).addAll(extraUnits).build()
        }

    /**
     * The [BoundSources] for [sourceSet] bound against [boundClassPath] and [bootclasspath], or
     * `null` if parsing or binding failed.
     */
    val boundSources: BoundSources? by
        lazy(LazyThreadSafetyMode.NONE) {
            val units = allUnits ?: return@lazy null
            val bindingResult = bind(units, reporter) ?: return@lazy null
            BoundSources(sourceSet, units, bindingResult)
        }

    /**
     * The [BoundSources] for an empty [SourceSet] bound against [boundClassPath] and
     * [bootclasspath] for [SourceParser.getClassPathResolver].
     */
    val classPathBoundSources: BoundSources? by
        lazy(LazyThreadSafetyMode.NONE) {
            val emptyUnits = ImmutableList.of<CompUnit>()
            val bindingResult = bind(emptyUnits, reporter) ?: return@lazy null
            BoundSources(SourceSet.empty(), emptyUnits, bindingResult)
        }

    /**
     * Parses [javaStubFiles] and binds them together with the non-overridden [allUnits] from
     * [sourceSet] against [boundClassPath] and [bootclasspath].
     */
    fun bindJavaStubs(
        javaStubFiles: List<File>,
        reporter: Reporter,
    ): BoundSources? {
        val mainUnits = allUnits ?: return null

        val stubSourcePath = sourceSet.sourcePath.ifEmpty { listOf(File("")) }
        val stubSourceSet = SourceSet(javaStubFiles, stubSourcePath).extractRoots(reporter)
        val log = TurbineLog()
        val sourceFiles =
            tracer.trace("turbine.getSourceFiles") {
                getSourceFiles(stubSourceSet.sources.asSequence())
            }
        val units =
            tracer.trace("turbine.parseSourceFiles") { sourceFiles.mapNotNull { parse(log, it) } }

        if (log.anyErrors()) {
            log.reportTo(reporter)
            return null
        }

        val commandLineClasses = units.mapNotNull { unit -> unit.mainClassQualifiedName }.toSet()
        val extraUnits =
            mainUnits.filter { unit -> unit.mainClassQualifiedName !in commandLineClasses }

        val stubAllUnits =
            ImmutableList.builder<CompUnit>().addAll(units).addAll(extraUnits).build()
        val bindingResult = bind(stubAllUnits, reporter) ?: return null
        return BoundSources(
            stubSourceSet,
            stubAllUnits,
            bindingResult,
        )
    }

    /**
     * Binds [units] against [boundClassPath] and [bootclasspath], reporting any relevant
     * diagnostics to [reporter].
     */
    private fun bind(
        units: ImmutableList<CompUnit>,
        reporter: Reporter,
    ): BindingResult? {
        val log = TurbineLog()
        var bindingResult: BindingResult? = null
        try {
            // No annotation processors are used.
            val annotationProcessorInfo =
                ProcessorInfo.create(
                    ImmutableList.of(),
                    null,
                    ImmutableMap.of(),
                    SourceVersion.latest()
                )

            tracer.trace("turbine.bind") {
                MoreExecutors.newDirectExecutorService().use { executor ->
                    // Bind the units
                    bindingResult =
                        Binder.bind(
                            executor,
                            log,
                            units,
                            boundClassPath,
                            annotationProcessorInfo,
                            bootclasspath,
                            Optional.empty()
                        )
                }
            }
        } catch (e: TurbineError) {
            // Catch the [TurbineError] and extract its diagnostics.
            e.logAllDiagnostics(log)
        }

        // Report all the diagnostics, filtering those that relate to missing references.
        tracer.trace("turbine.reportDiagnostics") {
            log.reportTo(reporter) { diagnostic ->
                // Ignore missing references.
                val errorKind = diagnostic.kind()
                when (errorKind) {
                    TurbineError.ErrorKind.CANNOT_RESOLVE,
                    TurbineError.ErrorKind.CANNOT_RESOLVE_FIELD,
                    TurbineError.ErrorKind.EXPRESSION_ERROR,
                    TurbineError.ErrorKind.NO_JAVA_LANG,
                    TurbineError.ErrorKind.SYMBOL_NOT_FOUND -> {
                        false
                    }
                    else -> true
                }
            }
        }

        return bindingResult
    }

    /**
     * Parse [sourceFile] and return the [CompUnit].
     *
     * If [Parser.parse] throws a [TurbineError] then add any diagnostics from that to [log] and
     * return `null`.
     */
    private fun parse(log: TurbineLog, sourceFile: SourceFile): CompUnit? =
        try {
            Parser.parse(sourceFile)
        } catch (e: TurbineError) {
            e.logAllDiagnostics(log)
            null
        }

    private fun TurbineError.logAllDiagnostics(log: TurbineLog) {
        for (diagnostic in diagnostics()) {
            log.add(diagnostic)
        }
    }

    /** Report all the diagnostics in this [TurbineLog], if any, to [reporter]. */
    private fun TurbineLog.reportTo(
        reporter: Reporter,
        predicate: (TurbineDiagnostic) -> Boolean = { true }
    ) {
        for (diagnostic in diagnostics()) {
            // Ignore any that do not match the predicate.
            if (!predicate(diagnostic)) continue

            val path = diagnostic.path()
            val location =
                FileLocation.createLocation(
                    Paths.get(path),
                    line = diagnostic.line(),
                    characterPosition = diagnostic.column()
                )
            reporter.report(Issues.INVALID_SYNTAX, null, diagnostic.message(), location)
        }
        clear()
    }

    /**
     * Get the qualified class name of the main class in a unit.
     *
     * If a `.java` file contains multiple java classes then the main class is the first one which
     * is assumed to be the public class.
     */
    private val CompUnit.mainClassQualifiedName: String?
        get() {
            val pkgName = getPackageName(this)
            return decls().firstOrNull()?.let { decl -> "$pkgName.${decl.name()}" }
        }

    private fun scanSourcePath(sourcePath: List<File>, existingSources: Set<File>): Sequence<File> {
        val visited = mutableSetOf<String>()
        return sourcePath
            .asSequence()
            .flatMap { sourceRoot ->
                sourceRoot
                    .walkTopDown()
                    // The following prevents repeatedly re-entering the same directory if there is
                    // a cycle in the files, e.g. a symlink from a subdirectory back up to an
                    // ancestor directory.
                    .onEnter { dir ->
                        // Use the canonical path as each file in a cycle can be represented by an
                        // infinite number of paths and using them would make the visited check
                        // useless.
                        val canonical = dir.canonicalPath
                        return@onEnter if (canonical in visited) false
                        else {
                            visited += canonical
                            true
                        }
                    }
            }
            .filter { it !in existingSources }
    }

    override fun createSourceParser(codebaseConfig: Codebase.Config): SourceParser =
        TurbineSourceParser(
            turbineEnvironment = this,
            codebaseConfig = codebaseConfig,
            tracer = tracer,
        )
}

/** Create a [SourceFile] for every `.java` file in [sources]. */
private fun getSourceFiles(sources: Sequence<File>): List<SourceFile> {
    return sources
        .filter { it.isFile && it.extension == "java" } // Ensure only Java files are included
        .map { SourceFile(it.path, it.readText()) }
        .toList()
}
