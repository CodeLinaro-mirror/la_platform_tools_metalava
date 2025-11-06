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

package com.android.tools.metalava.model.imports

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JavaImport
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.utils.extractSimpleName
import com.android.tools.metalava.model.utils.splitIntoOptionalQualifierAndSimpleName

/**
 * Represents a resolved import.
 *
 * This references a class using ([qualifiedClassName]). It can also optionally reference a specific
 * member of that class ([memberName]). The member can be either a nested class, a method or a
 * field.
 *
 * This purposely does not contain a reference to a specific [Item] for a couple of reasons:
 * 1. Finding that would increase the construction time, and storage costs of [ImportResolver] for
 *    each imported name but only a small fraction of imported names will need to be resolved.
 * 2. If [memberName] is not `null` then it can refer to multiple different [Item]s and selecting
 *    the correct one depends on information that is not available to [ImportResolver] which means
 *    that this would have to support returning multiple [Item]s, further increasing the cost.
 *
 * This is represented as a `value` class to save time and space during construction at a slight
 * cost of some extra work when using it.
 */
@JvmInline
value class ResolvedImport private constructor(private val text: String) {
    constructor(
        qualifiedClassName: String,
        memberName: String? = null
    ) : this(if (memberName == null) qualifiedClassName else "$qualifiedClassName#$memberName")

    /** The qualified class name. */
    val qualifiedClassName
        get() =
            text.indexOf('#').let { index -> if (index == -1) text else text.substring(0, index) }

    /** The optional class member name. */
    val memberName
        get() =
            text
                .indexOf('#')
                .takeUnless { index -> index == -1 }
                ?.let { index -> text.substring(index + 1) }

    /**
     * Treat this whole [ResolvedImport] as it referred to a [ClassItem]. If [memberName] is not
     * null then assume it is a nested class inside [qualifiedClassName].
     */
    fun treatAsQualifiedClassName(): String {
        val nestedClassName = memberName ?: return text
        return "$qualifiedClassName.$nestedClassName"
    }
}

/**
 * Resolves a simple name to a qualified name based on the list of `import` statements.
 *
 * @param codebase The [Codebase] within which the name will be resolved. This is needed for on
 *   demand imports, i.e. those that end with `.*`.
 * @param imports The list of [JavaImport]s obtained from [SourceFile.allJavaImports].
 */
class ImportResolver(
    private val codebase: Codebase,
    imports: List<JavaImport>,
) {
    /** Map from simple name to [ResolvedImport]. */
    private val namedImports: Map<String, ResolvedImport>

    /** List of [OnDemandImport]s that are in source order. */
    private val onDemandImports: List<OnDemandImport>

    /** Initialize [namedImports] and [onDemandImports] from [imports]. */
    init {
        val namedImportsBuilder = mutableMapOf<String, ResolvedImport>()
        val onDemandImportsBuilder = mutableListOf<OnDemandImport>()
        for (import in imports) {
            val qualifiedName = import.qualifiedName
            if (import.onDemand) {
                if (import.static) {
                    // Import all members from a type.
                    onDemandImportsBuilder.add(OnDemandClassMemberImport(codebase, qualifiedName))
                } else {
                    // Import all types from package or type.
                    onDemandImportsBuilder.add(OnDemandClassImport(codebase, qualifiedName))
                }
            } else {
                if (import.static) {
                    val (className, memberName) =
                        qualifiedName.splitIntoOptionalQualifierAndSimpleName()
                    namedImportsBuilder.put(memberName, ResolvedImport(className!!, memberName))
                } else {
                    val simpleName = qualifiedName.extractSimpleName()
                    namedImportsBuilder.put(simpleName, ResolvedImport(qualifiedName))
                }
            }
        }

        // Always import java.lang.*.
        if (!imports.contains(IMPLICIT_JAVA_LANG_IMPORT)) {
            onDemandImportsBuilder.add(
                OnDemandClassImport(codebase, IMPLICIT_JAVA_LANG_IMPORT.qualifiedName)
            )
        }

        namedImports = namedImportsBuilder.toMap()
        onDemandImports = onDemandImportsBuilder.toList()
    }

    /** Return the [ResolvedImport], if any, for [simpleName]. */
    fun resolveImport(simpleName: String): ResolvedImport? {
        namedImports[simpleName]?.let {
            return it
        }
        for (onDemandImport in onDemandImports) {
            onDemandImport.findImport(simpleName)?.let {
                return it
            }
        }
        return null
    }

    companion object {
        /** The implicit [JavaImport] that is included in every [ImportResolver]. */
        private val IMPLICIT_JAVA_LANG_IMPORT =
            JavaImport(
                qualifiedName = "java.lang",
                onDemand = true,
                static = false,
            )
    }

    /** Implements a [JavaImport] whose [JavaImport.onDemand] was `true`, i.e. ended with `.*`. */
    private interface OnDemandImport {
        /** Check the owning [PackageItem] or [ClassItem] to see if it contains [simpleName]. */
        fun findImport(simpleName: String): ResolvedImport?
    }

    /**
     * Import a reference to a [ClassItem] that is a child of [qualifiedName] which could be a
     * [PackageItem] or a [ClassItem].
     */
    private class OnDemandClassImport(
        private val codebase: Codebase,
        private val qualifiedName: String,
    ) : OnDemandImport {
        /**
         * Check for a [ClassItem] called [qualifiedName].[simpleName].
         *
         * Rather than resolve [qualifiedName] to a [PackageItem] or [ClassItem] and then check to
         * see if it contains a child [ClassItem] called [simpleName] this just constructs the name
         * of that class if it existed and uses [Codebase.resolveClass] to find it. If it can find
         * it then it returns a [ResolvedImport] that wraps the constructed class name.
         */
        override fun findImport(simpleName: String): ResolvedImport? {
            // Assume that [simpleName] does exist in [qualifiedName] and construct its name.
            val possibleClassName = "$qualifiedName.$simpleName"
            // If it does exist then return its name, otherwise return null.
            return if (codebase.resolveClass(possibleClassName) == null) null
            else ResolvedImport(possibleClassName)
        }
    }

    /**
     * Import a reference to one or more nested [ClassItem]s, [MethodItem]s, or [FieldItem]s that
     * are children of [qualifiedName] which must be a [ClassItem].
     */
    private class OnDemandClassMemberImport(
        private val codebase: Codebase,
        private val qualifiedName: String,
    ) : OnDemandImport {
        /**
         * Check for a nested [ClassItem], [MethodItem], or [FieldItem] called
         * [qualifiedName].[simpleName].
         *
         * This resolves [qualifiedName] to a [ClassItem] and then checks to see if it contains a
         * nested [ClassItem], [MethodItem], or [FieldItem] called [simpleName]. If it can find it
         * then it returns a [ResolvedImport] that wraps the class name and member name.
         */
        override fun findImport(simpleName: String): ResolvedImport? {
            // Make sure that [qualifiedName] exists in [codebase].
            val classItem = codebase.resolveClass(qualifiedName) ?: return null

            // See if [simpleName] exists in the class, either as a nested class, a method, or a
            // field.
            val imported =
                classItem.nestedClasses().classHasSimpleName(simpleName) ||
                    classItem.methods().memberHasSimpleName(simpleName) ||
                    classItem.fields().memberHasSimpleName(simpleName)
            return if (imported) ResolvedImport(qualifiedName, simpleName) else null
        }

        companion object {
            /**
             * Check to see if this contains a [ClassItem] whose [ClassItem.simpleName] matches
             * [simpleName].
             */
            private fun Collection<ClassItem>.classHasSimpleName(simpleName: String) = any {
                it.simpleName() == simpleName
            }

            /**
             * Check to see if this contains a [MemberItem] whose [MemberItem.name] matches
             * [simpleName].
             */
            private fun Collection<MemberItem>.memberHasSimpleName(simpleName: String) = any {
                it.name() == simpleName
            }
        }
    }
}
