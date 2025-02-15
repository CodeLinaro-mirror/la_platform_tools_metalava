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

package com.android.tools.metalava.cli.historical

import com.android.SdkConstants
import com.android.tools.metalava.CodebaseComparator
import com.android.tools.metalava.ComparisonVisitor
import com.android.tools.metalava.JarCodebaseLoader
import com.android.tools.metalava.NullnessMigration
import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.SignatureWriter
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.createFilteringVisitorForSignatures
import com.android.tools.metalava.createReportFile
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.BasicReporter
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

/**
 * In an Android source tree, rewrite the signature files in prebuilts/sdk by reading what's
 * actually there in the android.jar files.
 */
class ConvertJarsToSignatureFiles(
    private val stderr: PrintWriter,
    private val stdout: PrintWriter,
    private val progressTracker: ProgressTracker,
    private val fileFormat: FileFormat,
    private val apiVersions: Set<ApiVersion>?,
    private val apiSurfaceNames: Set<String>,
    private val jarCodebaseLoader: JarCodebaseLoader,
    private val root: File
) {
    private val reporter = BasicReporter(stderr)

    private val apiSurfaces =
        ApiSurfaces.build {
            for ((index, apiSurfaceName) in apiSurfaceNames.withIndex()) {
                // The main surface is irrelevant at the moment because this always generates
                // the whole API surface. However, a main surface is required so this just uses
                // the first one as the main surface for now.
                val isMain = index == 0
                createSurface(apiSurfaceName, isMain = isMain)
            }
        }

    fun convertJars() {
        val scanConfig =
            PatternNode.ScanConfig(
                dir = root,
                apiVersionFilter = apiVersions?.let { it::contains },
                apiSurfaceByName = apiSurfaces.all.associateBy { it.name },
            )

        val historicalApis =
            HistoricalApiVersionInfo.scan(
                reporter,
                jarFilePattern = "prebuilts/sdk/{version:major.minor?}/{surface}/android.jar",
                signatureFilePattern =
                    "prebuilts/sdk/{version:major.minor?}/{surface}/api/android.txt",
                scanConfig,
            )

        for (historicalApi in historicalApis) {
            for (surfaceInfo in historicalApi.infoBySurface.values) {
                convertJar(historicalApi.version, surfaceInfo)
            }
        }
    }

    /**
     * Convert a single jar for [version] in [SurfaceInfo.jarFile] into its corresponding signature
     * file, i.e. [SurfaceInfo.signatureFile].
     */
    private fun convertJar(version: ApiVersion, surfaceInfo: SurfaceInfo) {
        val relativeJarFile = surfaceInfo.jarFile
        val jarFile = root.resolve(relativeJarFile)
        val relativeSignatureFile = surfaceInfo.signatureFile
        val signatureFile = root.resolve(relativeSignatureFile)

        progressTracker.progress("Writing signature files $relativeSignatureFile for $jarFile")

        val annotationManager = DefaultAnnotationManager()
        val codebaseConfig =
            Codebase.Config(
                annotationManager = annotationManager,
                apiSurfaces = apiSurfaces,
                reporter = reporter,
            )
        val signatureFileLoader = DefaultSignatureFileLoader(codebaseConfig)

        val jarCodebase = jarCodebaseLoader.loadFromJarFile(jarFile)

        if (version.major >= 28) {
            // As of API 28 we'll put nullness annotations into the jar but some of them may be
            // @RecentlyNullable/@RecentlyNonNull. Translate these back into normal
            // @Nullable/@NonNull
            jarCodebase.accept(
                object :
                    ApiVisitor(
                        apiPredicateConfig = ApiPredicate.Config(),
                    ) {
                    override fun visitItem(item: Item) {
                        unmarkRecent(item)
                        super.visitItem(item)
                    }

                    private fun unmarkRecent(new: Item) {
                        val annotation = NullnessMigration.findNullnessAnnotation(new) ?: return
                        // Nullness information change: Add migration annotation
                        val annotationClass =
                            if (annotation.isNullable()) ANDROIDX_NULLABLE else ANDROIDX_NONNULL

                        val replacementAnnotation =
                            new.codebase.createAnnotation("@$annotationClass", new)
                        new.mutateModifiers {
                            mutateAnnotations {
                                remove(annotation)
                                replacementAnnotation?.let { add(it) }
                            }
                        }
                    }
                }
            )
            assert(!SUPPORT_TYPE_USE_ANNOTATIONS) {
                "We'll need to rewrite type annotations here too"
            }
        }

        // Read deprecated attributes. Seem to be missing from code model; try to read via ASM
        // instead since it must clearly be there.
        markDeprecated(jarCodebase, jarFile, jarFile.path)

        // ASM doesn't seem to pick up everything that's actually there according to javap. So as
        // another fallback, read from the existing signature files:
        try {
            val oldCodebase = signatureFileLoader.load(SignatureFile.fromFiles(signatureFile))
            val visitor =
                object : ComparisonVisitor() {
                    override fun compareItems(old: Item, new: Item) {
                        if (old.originallyDeprecated && old !is PackageItem) {
                            new.deprecateIfRequired("previous signature file for $old")
                        }
                    }
                }
            CodebaseComparator().compare(visitor, oldCodebase, jarCodebase, null)
        } catch (e: Exception) {
            throw IllegalStateException("Could not load $signatureFile: ${e.message}", e)
        }

        createReportFile(progressTracker, jarCodebase, signatureFile, "API") { printWriter ->
            val signatureWriter =
                SignatureWriter(
                    writer = printWriter,
                    fileFormat = fileFormat,
                )

            createFilteringVisitorForSignatures(
                delegate = signatureWriter,
                fileFormat = fileFormat,
                apiType = ApiType.PUBLIC_API,
                preFiltered = jarCodebase.preFiltered,
                showUnannotated = false,
                apiPredicateConfig = ApiPredicate.Config()
            )
        }
    }

    private fun markDeprecated(codebase: Codebase, file: File, path: String) {
        when {
            file.name.endsWith(SdkConstants.DOT_JAR) ->
                try {
                    ZipFile(file).use { jar ->
                        val enumeration = jar.entries()
                        while (enumeration.hasMoreElements()) {
                            val entry = enumeration.nextElement()
                            if (entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                                try {
                                    jar.getInputStream(entry).use { inputStream ->
                                        val bytes = inputStream.readBytes()
                                        markDeprecated(codebase, bytes, path + ":" + entry.name)
                                    }
                                } catch (e: Exception) {
                                    stdout.println(
                                        "Could not read jar file entry ${entry.name} from $file: $e"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    stdout.println("Could not read jar file contents from $file: $e")
                }
            file.isDirectory -> {
                val listFiles = file.listFiles()
                listFiles?.forEach { markDeprecated(codebase, it, it.path) }
            }
            file.path.endsWith(SdkConstants.DOT_CLASS) -> {
                val bytes = file.readBytes()
                markDeprecated(codebase, bytes, file.path)
            }
            else -> stdout.println("Ignoring entry $file")
        }
    }

    private fun markDeprecated(codebase: Codebase, bytes: ByteArray, path: String) {
        val reader: ClassReader
        val classNode: ClassNode
        try {
            // TODO: We don't actually need to build a DOM.
            reader = ClassReader(bytes)
            classNode = ClassNode()
            reader.accept(classNode, 0)
        } catch (t: Throwable) {
            stderr.println("Error processing $path: broken class file?")
            return
        }

        if ((classNode.access and Opcodes.ACC_DEPRECATED) != 0) {
            val item = codebase.findClass(classNode, MATCH_ALL)
            item.deprecateIfRequired("byte code for ${classNode.name}")
        }

        val methodList = classNode.methods
        for (f in methodList) {
            val methodNode = f as MethodNode
            if ((methodNode.access and Opcodes.ACC_DEPRECATED) == 0) {
                continue
            }
            val item = codebase.findMethod(classNode, methodNode, MATCH_ALL)
            item.deprecateIfRequired("byte code for ${methodNode.name}")
        }

        val fieldList = classNode.fields
        for (f in fieldList) {
            val fieldNode = f as FieldNode
            if ((fieldNode.access and Opcodes.ACC_DEPRECATED) == 0) {
                continue
            }
            val item = codebase.findField(classNode, fieldNode, MATCH_ALL)
            item.deprecateIfRequired("byte code for ${fieldNode.name}")
        }
    }

    /** Mark the [Item] as deprecated if required. */
    private fun Item?.deprecateIfRequired(source: String) {
        this ?: return
        if (!originallyDeprecated) {
            // Set the deprecated flag in the modifiers which underpins [originallyDeprecated].
            mutateModifiers { setDeprecated(true) }
            progressTracker.progress("Turned deprecation on for $this from $source")
        }
    }

    companion object {
        val MATCH_ALL: FilterPredicate = FilterPredicate { true }
    }
}

/** Finds the given class by JVM owner */
private fun Codebase.findClassByOwner(owner: String, apiFilter: FilterPredicate): ClassItem? {
    val className = owner.replace('/', '.').replace('$', '.')
    val cls = findClass(className)
    return if (cls != null && apiFilter.test(cls)) {
        cls
    } else {
        null
    }
}

private fun Codebase.findClass(node: ClassNode, apiFilter: FilterPredicate): ClassItem? {
    return findClassByOwner(node.name, apiFilter)
}

private fun Codebase.findMethod(
    classNode: ClassNode,
    node: MethodNode,
    apiFilter: FilterPredicate
): MethodItem? {
    val cls = findClass(classNode, apiFilter) ?: return null
    val types = Type.getArgumentTypes(node.desc)
    val parameters =
        if (types.isNotEmpty()) {
            val sb = StringBuilder()
            for (type in types) {
                if (sb.isNotEmpty()) {
                    sb.append(", ")
                }
                sb.append(type.className.replace('/', '.').replace('$', '.'))
            }
            sb.toString()
        } else {
            ""
        }
    val methodName = if (node.name == "<init>") cls.simpleName() else node.name
    val method = cls.findMethod(methodName, parameters)
    return if (method != null && apiFilter.test(method)) {
        method
    } else {
        null
    }
}

private fun Codebase.findField(
    classNode: ClassNode,
    node: FieldNode,
    apiFilter: FilterPredicate
): FieldItem? {
    val cls = findClass(classNode, apiFilter) ?: return null
    val field = cls.findField(node.name + 2)
    return if (field != null && apiFilter.test(field)) {
        field
    } else {
        null
    }
}
