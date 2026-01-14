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

package com.android.tools.metalava.model.scope

import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.ReferencableItem

/** Implementation of [ReferencableNameScope.resolveReferencableItem]. */
internal object ReferencableNameResolver {
    /**
     * Resolve [referencableName] within [scope].
     *
     * First, this will recursively call itself removing the trailing simple name from
     * [referencableName] until it is just a single simple name.
     *
     * Then this will [searchEnclosingScopes] for the simple [referencableName], starting from
     * [scope] to find the [ReferencableItem] for [referencableName], if any, and return.
     *
     * Then as it unrolls each recursive call it will first check to see if the [ReferencableItem]
     * returned from the recursive call is a [ReferencableNameScope]. If it is not then it returns
     * [InvalidReferencableItem]. Otherwise, it will return the result of looking for the trailing
     * simple name of [referencableName] in the [ReferencableNameScope].
     *
     * e.g. if resolving `java.io.IOException` (from any [scope]) it will do the following:
     * * Call [resolveReferencableItem] with `java.io` and [scope].
     *     * Call [resolveReferencableItem] with `java` and [scope].
     *         * Return the result of calling [searchEnclosingScopes] with `java` and [scope] which
     *           will find `PackageItem("java")` in the root package.
     *     * Search for `io` in `PackageItem("java")` finding `PackageItem("java.io")`.
     * * Search for `IOException` in `PackageItem("java.io")` finding
     *   `ClassItem("java.io.IOException")`.
     */
    fun resolveReferencableItem(
        scope: ReferencableNameScope,
        referencableName: String
    ): ReferencableItem {
        val length = referencableName.length

        // The scope being searched.
        var currentScope = scope

        // The start index of the next simple name to find.
        var startIndex = 0

        // Loop over all simple names within the possibly qualified referencableName, resolving each
        // against the scope resulting from the resolving the previous name, starting with the
        // supplied scope. Returns the result of resolving the last simple name.
        while (true) {
            // Find the '.' that terminates the next simple name, if any.
            val dotIndex = referencableName.indexOf('.', startIndex)

            // Compute the end of the next simple name.
            val endIndex = if (dotIndex == -1) length else dotIndex

            // Extract the next simple name. The implementation optimizes the case when the whole
            // string will be returned so this does not have to do that.
            val simpleName = referencableName.substring(startIndex, endIndex)

            // Resolve the simple name against the current scope.
            val resolved =
                if (startIndex == 0) {
                    // If this is the first name to be resolved then search all the enclosing scopes
                    // of the current scope.
                    searchEnclosingScopes(currentScope, simpleName)
                } else {
                    // Otherwise, only search the current scope for the simple name.
                    currentScope.resolveReferencableItemBySimpleName(
                        simpleName,
                        isFirstSimpleName = false,
                    )
                }
                    // If the simple name could not be found then it is an error.
                    ?: return InvalidReferencableItem(
                        unresolvedReferenceName = referencableName,
                        failingScopeName = currentScope.toString(),
                        failingSimpleName = simpleName,
                        reason = InvalidReferencableItem.Reason.NOT_FOUND,
                    )

            // If that was the last simple name to search then return the result.
            if (dotIndex == -1) {
                return resolved
            }

            // Otherwise, if possible treat the resolved item as the next scope to search.
            currentScope =
                resolved as? QualifiedNameScope
                    // It is an error for a containing name to resolve to something that cannot
                    // resolve a qualified name.
                    ?: return InvalidReferencableItem(
                        unresolvedReferenceName = referencableName,
                        failingScopeName = currentScope.toString(),
                        failingSimpleName = simpleName,
                        reason = InvalidReferencableItem.Reason.NOT_QUALIFIED_SCOPE,
                    )

            // Move onto the next simple name to find.
            startIndex = endIndex + 1
        }
    }

    /**
     * Search for [simpleName] in [scope] and all its [ReferencableNameScope.containingScope]s,
     * returning the [ReferencableItem] that is found, or `null` otherwise.
     */
    private fun searchEnclosingScopes(
        scope: ReferencableNameScope,
        simpleName: String
    ): ReferencableItem? {
        // Traverse through the scopes, starting with the one supplied to see if they can map
        // it to a [ReferencableItem].
        var current: ReferencableNameScope? = scope
        while (current != null) {
            current
                .resolveReferencableItemBySimpleName(simpleName, isFirstSimpleName = true)
                ?.let { result ->
                    return result
                }
            current = current.containingScope
        }

        return null
    }
}
