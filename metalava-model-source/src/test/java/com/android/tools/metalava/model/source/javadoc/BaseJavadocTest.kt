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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.BaseDocCommentTest
import com.android.tools.metalava.model.source.doc.ClassReference
import com.android.tools.metalava.model.source.doc.DocComment

abstract class BaseJavadocTest : BaseDocCommentTest() {
    /** Check that [text] is parsed correctly by [JavadocParser]. */
    internal fun checkParse(
        text: String,
        contentGetter: (DocComment) -> JavadocContent? = { docComment -> docComment.description },
        expectedStructure: String,
        expectedJavadocIssues: String = "",
    ) {
        context.referenceResolver = { ClassReference("resolved.$it") }
        val docComment = createTestDocComment(text)

        // Parse the main description
        var content = contentGetter(docComment)

        // Make sure that no unexpected JavadocParser issues were found.
        reporter.assertJavadocParserIssues(expectedJavadocIssues)

        // Check the model structure.
        content.assertStructure(expectedStructure.trimIndent())
    }
}
