/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.model

import java.util.TreeSet

/** Represents a Kotlin/Java source file */
interface SourceFile {
    /** Top level classes contained in this file */
    fun classes(): Sequence<ClassItem>

    fun getHeaderComments(): String? = null

    /** Get all the imports. */
    fun getImports() = getImports { true }

    /** Get only those imports that reference [Item]s for which [predicate] returns `true`. */
    fun getImports(predicate: FilterPredicate): Collection<Import> = emptyList()

    /**
     * Compute set of import statements that are actually referenced from the documentation (we do
     * inexact matching here; we don't need to have an exact set of imports since it's okay to have
     * some extras). This isn't a big problem since our code style forbids/discourages wildcards, so
     * it shows up in fewer places, but we need to handle it when it does -- such as in ojluni.
     */
    fun filterImports(imports: TreeSet<Import>, predicate: FilterPredicate): TreeSet<Import>
}

/** Encapsulates information about the imports used in a [SourceFile]. */
@ConsistentCopyVisibility
data class Import
internal constructor(
    /**
     * The import pattern, i.e. the whole part of the import statement after `import static? ` and
     * before the optional `;`, excluding any whitespace.
     */
    val pattern: String,

    /**
     * The name that is being imported, i.e. the part after the last `.`. Is `*` for wildcard
     * imports.
     */
    val name: String,

    /**
     * True if the item that is being imported is a member of a class. Corresponds to the `static`
     * keyword in Java, has no effect on Kotlin import statements.
     */
    val isMember: Boolean,
) {
    /** Import a whole [PackageItem], i.e. uses a wildcard. */
    constructor(pkgItem: PackageItem) : this("${pkgItem.qualifiedName()}.*", "*", false)

    /** Import a [ClassItem]. */
    constructor(
        classItem: ClassItem
    ) : this(
        classItem.qualifiedName(),
        classItem.simpleName(),
        false,
    )

    /** Import a [MemberItem]. */
    constructor(
        memberItem: MemberItem
    ) : this(
        "${memberItem.containingClass().qualifiedName()}.${memberItem.name()}",
        memberItem.name(),
        true,
    )
}
