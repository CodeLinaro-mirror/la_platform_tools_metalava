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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.javadoc.ExprBuilderContext
import com.android.tools.metalava.model.source.javadoc.ExprContext

/**
 * Provides contextual information from the surrounding model for use when processing a
 * [DocComment].
 *
 * This purposely does not include [DocumentationIssueReporter] as there are multiple instances of
 * that created at different levels within the [DocComment] whereas this applies to the whole
 * [DocComment].
 */
internal interface DocCommentContext : ExprBuilderContext, ExprContext {
    /**
     * Compute the ordinal value for parameter [name] in the list of all `@param` tags.
     *
     * The `@param` tags can be used for type and callable parameters, sometimes in the same list.
     * They should be in the following order:
     * 1. `@param` tags for type parameters in type parameter list order.
     * 2. `@param` tags for unknown type parameters.
     * 3. `@param` tags for callable parameters in parameter list order.
     * 4. `@param` tags for unknown callable parameters.
     *
     * @param name will be wrapped inside `<...>` if it is a type parameter, otherwise it is a
     *   callable parameter.
     */
    fun ordinalInParamsList(name: String): Int

    /**
     * Check to see whether the comment is on an overriding method and so may require insertion of
     * `{@inheritDoc}` tags when appending content to preserve the developer's intended behavior.
     *
     * @return `true` if the commented [SelectableItem] is a [MethodItem] that has at least one
     *   [MethodItem.superMethods].
     */
    fun isOverridingMethod(): Boolean

    /**
     * The optional [ClassItem] that contains this documentation.
     *
     * The value returned depends on the [SelectableItem] this documents:
     * * For a [PackageItem] this will return `null`.
     * * For a [ClassItem] this will just return the [ClassItem] itself.
     * * For a [MemberItem] this will return [MemberItem.containingClass].
     */
    val containingClassItem: ClassItem?

    /** Provides support for parsing type references within Javadoc content. */
    val docTypeParser: DocTypeParser
}
