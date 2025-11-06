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
     * `null`. Otherwise, it will return the result of looking for the trailing simple name of
     * [referencableName] in the [ReferencableNameScope].
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
    ): ReferencableItem? {
        // If the name contains a '.' then it must either be fully qualified or a nested class. The
        // part before the '.' could be either a package or another class. If there is no '.' then
        // it is a simple name.
        val dotIndex = referencableName.lastIndexOf('.')
        if (dotIndex == -1) {
            return searchEnclosingScopes(scope, referencableName)
        } else {
            val containingPackageOrClassName = referencableName.substring(0, dotIndex)
            val referencableNameScope =
                resolveReferencableItem(scope, containingPackageOrClassName)
                    as? ReferencableNameScope ?: return null

            val simpleName = referencableName.substring(dotIndex + 1)
            return referencableNameScope.resolveReferencableItemBySimpleName(
                simpleName,
                isFirstSimpleName = false,
            )
            return null
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
