/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.source.utils.packageHtmlToJavadoc
import org.intellij.lang.annotations.Language

/** Provides support for creating [ItemDocumentation] from source files. */
object SourceItemDocumentation {
    /**
     * Create an [ItemDocumentationFactory] from a `package.html` file by extracting the contents of
     * the body tag.
     */
    @Language("JAVA")
    fun fromHTML(@Language("HTML") packageHtml: String?): ItemDocumentationFactory {
        val text = packageHtmlToJavadoc(packageHtml)
        return text.toItemDocumentationFactory()
    }
}
