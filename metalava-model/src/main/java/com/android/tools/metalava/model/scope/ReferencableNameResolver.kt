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
     * Resolve [referencableName] as [nameClassification] within [scope]
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
     *
     * The [nameClassification] determines the type of items to which the [referencableName] can
     * refer. It limits the return types to one of the allowable item types (or
     * [InvalidReferencableItem]).
     */
    fun resolveReferencableItem(
        scope: ReferencableNameScope,
        referencableName: String,
        nameClassification: NameClassification,
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

            // If this is searching for the last simple name then use the classification supplied to
            // this method, otherwise just look for items that can qualify other names.
            val simpleNameClassification =
                if (dotIndex == -1) nameClassification else NameClassification.QUALIFIER

            // Resolve the simple name against the current scope.
            val resolved =
                if (startIndex == 0) {
                    // If this is the first name to be resolved then search all the enclosing scopes
                    // of the current scope.
                    searchEnclosingScopes(currentScope, simpleName, simpleNameClassification)
                } else {
                    // Otherwise, only search the current scope for the simple name.
                    currentScope.resolveReferencableItemBySimpleName(
                        simpleName,
                        simpleNameClassification,
                        isFirstSimpleName = false,
                    )
                }
                    // If the simple name could not be found then it is an error.
                    ?: run {
                        // Use a shorter message for unqualified references that avoids duplicating
                        // the reference multiple times in the message.
                        val unqualifiedName = startIndex == 0 && dotIndex == -1
                        val message =
                            if (unqualifiedName) {
                                "Could not resolve ${simpleNameClassification.describeName(simpleName)} in '$currentScope'"
                            } else {
                                "Could not resolve ${nameClassification.describeName(referencableName)} as could not find ${simpleNameClassification.describeName(simpleName)} in '$currentScope'"
                            }

                        return InvalidReferencableItem(message)
                    }

            // If that was the last simple name to search then return the result.
            if (dotIndex == -1) {
                return resolved
            }

            // Otherwise, treat the resolved item as the next scope to search. At this point
            // `resolved` will always be an instance of [QualifiedNameScope] as `resolved` is the
            // result of resolving `simpleName` using [NameClassification.QUALIFIER] and that will
            // always return either `null` (handled above), `PackageItem`, or `ClassItem`.
            currentScope = resolved as QualifiedNameScope

            // Move onto the next simple name to find.
            startIndex = endIndex + 1
        }
    }

    /**
     * Search for [simpleName] in [scope] and all its [ReferencableNameScope.containingScope]s,
     * returning the [ReferencableItem] that is found, or `null` otherwise.
     *
     * The [simpleName] is classified by [nameClassification].
     */
    private fun searchEnclosingScopes(
        scope: ReferencableNameScope,
        simpleName: String,
        nameClassification: NameClassification
    ): ReferencableItem? {
        // Traverse through the scopes, starting with the one supplied to see if they can map
        // it to a [ReferencableItem].
        var current: ReferencableNameScope? = scope
        while (current != null) {
            current
                .resolveReferencableItemBySimpleName(
                    simpleName,
                    nameClassification,
                    isFirstSimpleName = true
                )
                ?.let { result ->
                    return result
                }
            current = current.containingScope
        }

        return null
    }
}
