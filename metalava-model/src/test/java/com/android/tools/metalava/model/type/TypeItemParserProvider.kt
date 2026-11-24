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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.reporter.Issues.Issue

/**
 * Provides a [TypeItemParser] factory and the list of [testCases] supported by that parser for
 * parameterized tests.
 */
class TypeItemParserProvider<T>(
    private val name: String,
    private val factory:
        (
            AnnotationContext,
            UnqualifiedClassHandler,
            Boolean,
            TypeItemParserErrorReporter,
        ) -> TypeItemParser,
    val testCases: List<T>,
) {
    fun createParser(
        annotationContext: AnnotationContext = AnnotationContext.DEFAULT_RESOLVE_NULL,
        unqualifiedClassHandler: UnqualifiedClassHandler =
            UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR,
        kotlinStyleNulls: Boolean = false,
        errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
    ): TypeItemParser =
        factory(
            annotationContext,
            unqualifiedClassHandler,
            kotlinStyleNulls,
            errorReporter,
        )

    override fun toString() = name
}

/** Expand a list of [TypeItemParserProvider]s into `(provider, testCase)` parameter pairs. */
fun <T : Any> List<TypeItemParserProvider<T>>.toTestParameters(): List<Array<Any>> =
    flatMap { provider ->
        provider.testCases.map { testCase -> arrayOf(provider, testCase) }
    }

/**
 * [TypeItemParserErrorReporter] that collates reported issues and formats them as a newline
 * separated string for test assertions.
 */
internal class CollatingErrorReporter : TypeItemParserErrorReporter {
    private val list = mutableListOf<Report>()

    private data class Report(
        val issue: Issue,
        val message: String,
        val charOffset: Int,
    )

    override fun report(issue: Issue, message: String, charOffset: Int) {
        list.add(Report(issue, message, charOffset))
    }

    override fun toString(): String {
        list.sortWith(reportComparator)
        return list.joinToString("\n") { report ->
            "${report.charOffset}: ${report.message} [${report.issue.name}]"
        }
    }

    companion object {
        private val reportComparator =
            compareBy<Report>(
                { it.issue.name },
                { it.message },
                { it.charOffset },
            )
    }
}
