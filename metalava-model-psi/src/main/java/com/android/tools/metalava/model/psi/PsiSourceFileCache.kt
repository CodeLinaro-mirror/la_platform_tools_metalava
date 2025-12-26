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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.item.DefaultCodebase
import com.intellij.psi.PsiFile

/**
 * Creates [PsiSourceFile]s on demand for a [PsiFile] and caches the result for reuse.
 *
 * @param codebase the [DefaultCodebase] of which any created [PsiSourceFile]s are part.
 */
internal class PsiSourceFileCache(
    private val codebase: PsiBasedCodebase,
) {
    /** Map from [PsiFile] to the [PsiSourceFile]. */
    private val psiFileToSourceFile = mutableMapOf<PsiFile, PsiSourceFile>()

    /**
     * Get the [PsiSourceFile] for a [PsiFile].
     *
     * If none exists then create a [PsiSourceFile] from that, cache it for future use and return
     * it.
     */
    internal fun psiSourceFile(psiFile: PsiFile): PsiSourceFile =
        psiFileToSourceFile.computeIfAbsent(psiFile) { psiFile -> PsiSourceFile(codebase, psiFile) }
}
