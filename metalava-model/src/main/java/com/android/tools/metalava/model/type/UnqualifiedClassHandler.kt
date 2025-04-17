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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.reporter.Issues

/**
 * Responsible for handling an unqualified [ClassTypeItem] in a string representation of a type
 * being parsed.
 */
interface UnqualifiedClassHandler {
    /**
     * Determine what to do with a type with an [unqualifiedName].
     *
     * It can either throw an exception, or return a value [ClassTypeItem]. In the latter case it
     * can also report an error to [errorReporter], if necessary.
     *
     * All the parameters apart from [unqualifiedName] and [errorReporter] have the same meaning as
     * for [DefaultClassTypeItem].
     */
    fun handleUnqualifiedType(
        classResolver: ClassResolver,
        errorReporter: TypeItemParserErrorReporter,
        modifiers: TypeModifiers,
        unqualifiedName: String,
        arguments: List<TypeArgumentTypeItem>,
        outerClassType: ClassTypeItem?,
    ): ClassTypeItem

    companion object {
        /**
         * [UnqualifiedClassHandler] that will prefix with `java.lang.` if appropriate, otherwise
         * report an error and just use the unqualified name as is.
         */
        val PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR: UnqualifiedClassHandler =
            PrefixWithJavaLang(reportAsError = true)

        /**
         * [UnqualifiedClassHandler] that will prefix with `java.lang.` if appropriate, otherwise
         * just use the unqualified name as is.
         */
        val PREFIX_WITH_JAVA_LANG: UnqualifiedClassHandler =
            PrefixWithJavaLang(reportAsError = false)
    }

    /** An [UnqualifiedClassHandler] that will prefix with `java.lang.` if appropriate. */
    private class PrefixWithJavaLang(private val reportAsError: Boolean) : UnqualifiedClassHandler {
        /**
         * Tracks whether types that were unqualified and so implicitly treated as being part of the
         * 'java.lang` package are actually part of that package. If they are not then an error is
         * reported and it is not prefixed with `java.lang`.
         */
        private val javaLangPackage: JavaLangPackage = JavaLangPackage.DEFAULT

        override fun handleUnqualifiedType(
            classResolver: ClassResolver,
            errorReporter: TypeItemParserErrorReporter,
            modifiers: TypeModifiers,
            unqualifiedName: String,
            arguments: List<TypeArgumentTypeItem>,
            outerClassType: ClassTypeItem?
        ): ClassTypeItem {
            val javaLangName = "java.lang.$unqualifiedName"
            val qualifiedName =
                if (javaLangPackage.containsQualified(javaLangName)) {
                    // Reverse the effect of [TypeItem.stripJavaLangPrefix].
                    javaLangName
                } else {
                    if (reportAsError) {
                        errorReporter.report(
                            Issues.UNQUALIFIED_TYPE_ERROR,
                            "Unqualified type '$unqualifiedName' is not in 'java.lang' and is not a type parameter in scope"
                        )
                    }
                    unqualifiedName
                }

            return DefaultClassTypeItem(
                classResolver,
                modifiers,
                qualifiedName,
                arguments,
                outerClassType
            )
        }
    }
}
