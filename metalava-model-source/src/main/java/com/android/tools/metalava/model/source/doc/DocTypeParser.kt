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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.type.TypeItemParserErrorReporter
import com.android.tools.metalava.model.type.UnqualifiedClassHandler
import com.android.tools.metalava.reporter.Issues

/**
 * Supports parsing a parameter type [String] that is used in a member reference in Javadoc into a
 * [TypeItem]
 *
 * @param reporter used for errors encountered during parsing.
 * @param typeParameterScope resolves [TypeParameterItem]s that are in scope.
 */
class DocTypeParser
private constructor(
    private val reporter: DocumentationIssueReporter,
    private val typeParameterScope: TypeParameterScope,
) : TypeItemParserErrorReporter {

    /** Just return [sourceType] for now. */
    fun parse(sourceType: String): TypeItem {
        val annotationContext =
            object : AnnotationContext, ClassResolver by ClassResolver.THROWING {
                override val annotationManager
                    get() = error("Annotations not supported")
            }

        val unqualifiedClassHandler =
            object : UnqualifiedClassHandler {
                override fun handleUnqualifiedType(
                    errorReporter: TypeItemParserErrorReporter,
                    unqualifiedName: String
                ) = unqualifiedName
            }

        val parser =
            TypeItemParser(
                annotationContext,
                unqualifiedClassHandler,
                kotlinStyleNulls = false,
                errorReporter = this,
            )

        return parser.obtainTypeFromString(sourceType, typeParameterScope)
    }

    override fun report(issue: Issues.Issue, message: String) {
        reporter.report(issue, message)
    }

    companion object {
        /**
         * Create a [DocTypeParser] for [item] that reports issues to [reporter].
         *
         * The [item] determines which [TypeParameterItem]s are in scope.
         */
        internal fun create(
            reporter: DocumentationIssueReporter,
            item: SelectableItem,
        ): DocTypeParser {
            // Get the scope for
            val typeParameterScope =
                when (item) {
                    is CallableItem -> TypeParameterScope.from(item)
                    is MemberItem -> TypeParameterScope.from(item.containingClass())
                    is ClassItem -> TypeParameterScope.from(item)
                    else -> TypeParameterScope.empty
                }

            return create(reporter, typeParameterScope)
        }

        /**
         * Create a [DocTypeParser] for [typeParameterScope] that reports issues to [reporter].
         *
         * The [typeParameterScope] determines which [TypeParameterItem]s are in scope.
         */
        internal fun create(
            reporter: DocumentationIssueReporter,
            typeParameterScope: TypeParameterScope,
        ) = DocTypeParser(reporter, typeParameterScope)
    }
}
