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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.type.TypeItemParser

/**
 * Provides a [ValueParser] factory and the list of [testCases] supported by that parser for
 * parameterized tests.
 */
class ValueParserProvider<T>(
    private val name: String,
    private val factory: (AnnotationContext, TypeItemParser) -> ValueParser,
    val testCases: List<T>,
) {
    fun createParser(
        annotationContext: AnnotationContext = AnnotationContext.DEFAULT_RESOLVE_NULL,
        typeItemParser: TypeItemParser = TypeItemParser.forValueParser(annotationContext),
    ): ValueParser =
        factory(
            annotationContext,
            typeItemParser,
        )

    override fun toString() = name
}

/** Expand a list of [ValueParserProvider]s into `(provider, testCase)` parameter pairs. */
fun <T : Any> List<ValueParserProvider<T>>.toTestParameters(): List<Array<Any>> =
    flatMap { provider ->
        provider.testCases.map { testCase -> arrayOf(provider, testCase) }
    }
