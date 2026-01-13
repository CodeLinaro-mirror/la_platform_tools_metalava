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
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ReferencableItem

/** Defines the scope of package, class, type parameter, method or field names. */
interface ReferencableNameScope {
    /**
     * The containing scope.
     *
     * This is not searched automatically by [resolveReferencableItemBySimpleName], see
     * [ReferencableNameResolver].
     */
    val containingScope: ReferencableNameScope?

    /**
     * Resolves [simpleName], to a [ReferencableItem], relative to this scope, if possible.
     *
     * Implements https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.5.2
     *
     * In Java a type reference can either begin with a type name (e.g. class or type parameter
     * item) which will be resolved with the referencing scope, or it can begin with a package name,
     * in which case it is relative to the root package. It is not possible to have a reference that
     * starts with a package that is relative to the referencing scope.
     *
     * e.g. In the standard Java libraries using the type reference `annotation.ElementType` in
     * `java.lang` package does not resolve to `java.lang.annotation.ElementType` because
     * `annotation` is resolved relative to the root package, not `java.lang`.
     *
     * So, when resolving a type reference with package names the first package name must be
     * resolved relative to the root package but subsequent package names do need to be resolved
     * relative to the [PackageItem] that was the result of resolving the previous package name.
     *
     * e.g. When resolving `java.io.IOException`, `java` needs to be resolved relative to the root
     * package but `io` needs to be resolved relative to `java`.
     *
     * The [PackageItem] implementation of this method is called to resolve [simpleName]s relative
     * to the [PackageItem] and so needs to know whether [simpleName] is the first simple name in
     * the type reference or not so it knows whether to allow relative package names. That is the
     * purpose of the [isFirstSimpleName] parameter.
     *
     * @param simpleName may be for a package, class, type parameter, method or field.
     * @param isFirstSimpleName `false` if this is called for the first simple name in a type
     *   reference, `true` otherwise.
     */
    fun resolveReferencableItemBySimpleName(
        simpleName: String,
        isFirstSimpleName: Boolean
    ): ReferencableItem?

    /**
     * Resolves [referencableName] relative to this.
     *
     * Implements https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.5.2
     *
     * @param referencableName may be for a package, class, type parameter, method or field. Can be
     *   a name (excluding package name) relative to this scope, or a fully qualified name
     *   (including package name) relative to the root package.
     * @return the [ReferencableItem] corresponding to [referencableName], that will be an instance
     *   of [InvalidReferencableItem] if [referencableName] could not be found.
     */
    fun resolveReferencableItem(referencableName: String) =
        ReferencableNameResolver.resolveReferencableItem(this, referencableName)
}
