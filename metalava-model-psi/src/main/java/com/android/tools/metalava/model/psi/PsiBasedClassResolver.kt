/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.SourceSet
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

internal class PsiBasedClassResolver(
    uastEnvironment: UastEnvironment,
    config: Codebase.Config,
    allowReadingComments: Boolean,
) : ClassResolver {
    private val javaPsiFacade: JavaPsiFacade
    private val searchScope: GlobalSearchScope
    private val classpathCodebase: PsiBasedCodebase

    init {
        // Properties used to resolve classes from the classpath
        val project = uastEnvironment.ideaProject
        javaPsiFacade = JavaPsiFacade.getInstance(project)
        searchScope = GlobalSearchScope.everythingScope(project)

        val assembler =
            PsiCodebaseAssembler(uastEnvironment) { assembler ->
                PsiBasedCodebase(
                    location = File("classpath"),
                    description = "Codebase from classpath",
                    config = config,
                    fromClasspath = true,
                    allowReadingComments = allowReadingComments,
                    assembler = assembler,
                )
            }
        assembler.initializeFromSources(SourceSet.empty(), apiPackages = null)
        classpathCodebase = assembler.codebase
    }

    override fun resolveClass(erasedName: String): ClassItem? {
        // If the class cannot be found on the class path then return null, otherwise create a
        // PsiClassItem for it.
        val psiClass = javaPsiFacade.findClass(erasedName, searchScope) ?: return null
        return classpathCodebase.findOrCreateClass(psiClass)
    }
}
