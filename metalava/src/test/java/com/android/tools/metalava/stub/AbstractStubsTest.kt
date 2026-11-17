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

package com.android.tools.metalava.stub

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.DEFAULT_SKIP_EMIT_PACKAGES
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.TestEnvironment
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.text.FORMAT_V5_WITH_JAVA_STYLE
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.intellij.lang.annotations.Language

abstract class AbstractStubsTest : DriverTest() {
    protected fun checkStubs(
        /**
         * A wrapper for [expectedStubFiles]. When passing multiple stub Java files to test, use
         * [expectedStubFiles].
         */
        @Language("JAVA") source: String = "",

        /**
         * Array of expected stub files.
         *
         * Each [TestFile] in here will be compared against the generated stub files.
         */
        expectedStubFiles: Array<TestFile> = emptyArray(),

        /** The set of expected issues. */
        warnings: String? = "",

        /**
         * The expected API signature that will be produced.
         *
         * If non-null this causes the API signature file to be generated (through the `--api`
         * command line option) and compared against the contents of this.
         */
        @Language("TEXT") api: String? = null,

        /** Extract arguments to pass to Metalava main command. */
        extraArguments: Array<String> = emptyArray(),

        /**
         * Whether the stubs should be written as documentation stubs instead of plain stubs.
         * Decides whether the stubs include [ApiVariantType.DOC_ONLY] items, uses
         * rewritten/migration annotations, etc
         */
        docStubs: Boolean = false,

        /** Show annotations (--show-annotation arguments) */
        showAnnotations: Array<String> = emptyArray(),

        /** See [TestEnvironment.skipEmitPackages], defaults to [DEFAULT_SKIP_EMIT_PACKAGES]. */
        skipEmitPackages: List<String>? = null,

        /** Signature file format of [api], can also affect the generated stubs. */
        format: FileFormat = FORMAT_V5_WITH_JAVA_STYLE,

        /** The source files to pass to the analyzer */
        sourceFiles: Array<TestFile> = emptyArray(),

        /** Optional API signature files content to load **instead** of Java/Kotlin source files */
        signatureSources: Array<String> = emptyArray(),

        /**
         * If `true` then stubs will be generated and then compiled to make sure that they are valid
         * java.
         */
        checkCompilation: Boolean = true,

        /** A list of [CompilationCheck]s to perform with the generated stubs. */
        compilationChecks: List<CompilationCheck>? = null,

        /**
         * Check whether stubs generated from signature text files matches (ignoring parameter
         * names) the stubs generated from sources.
         *
         * Defaults to `true` unless [docStubs] is `true`, in which case it defaults to `false`.
         */
        checkTextStubEquivalence: Boolean? = null,

        /**
         * Language level of the Java source files. If not specified
         * [com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL] is used.
         */
        javaLanguageLevel: String? = null,
        /** Any jars to add to the class path */
        classpath: Array<TestFile>? = null,
    ) {
        val stubFilesArr = if (source.isNotEmpty()) arrayOf(java(source)) else expectedStubFiles
        if (stubFilesArr.isEmpty()) {
            error("must provide at least one expected stub files")
        }

        check(
            classpath = classpath,
            sourceFiles = sourceFiles,
            signatureSources = signatureSources,
            showAnnotations = showAnnotations,
            expectedStubFiles = stubFilesArr,
            expectedIssues = warnings,
            checkCompilation = checkCompilation,
            compilationChecks = compilationChecks,
            expectedApiSignature = api,
            extraArguments = extraArguments,
            docStubs = docStubs,
            skipEmitPackages = skipEmitPackages,
            format = format,
            javaLanguageLevel = javaLanguageLevel,
        )
        if (checkTextStubEquivalence == true) {
            error("checkTextStubEquivalence defaults to true where possible")
        }
        if (checkTextStubEquivalence ?: !docStubs) {
            check(
                classpath = classpath,
                signatureSources = arrayOf(readFileFilterBlankLines(getApiFile())),
                showAnnotations = showAnnotations,
                expectedStubFiles = stubFilesArr,
                // Signature files do not contain parameter names so ignore them when comparing stub
                // files.
                ignoreParameterNamesInStubFiles = true,
                checkCompilation = checkCompilation,
                compilationChecks = compilationChecks,
                extraArguments = arrayOf(*extraArguments),
                skipEmitPackages = skipEmitPackages,
                format = format,
                javaLanguageLevel = javaLanguageLevel,
            )
        }
    }
}
