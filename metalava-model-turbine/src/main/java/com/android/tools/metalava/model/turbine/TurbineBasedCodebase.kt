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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.CodebaseAssemblerFactory
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.reporter.Reporter
import java.io.File

internal open class TurbineBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
    override val reporter: Reporter,
    private val assemblerFactory: CodebaseAssemblerFactory,
) :
    DefaultCodebase(
        location = location,
        description = description,
        preFiltered = false,
        annotationManager = annotationManager,
        trustedApi = false,
        supportsDocumentation = true,
    ) {

    /**
     * Create a [CodebaseAssembler] appropriate for this [Codebase].
     *
     * The leaking of `this` is safe as the implementations do not access anything that has not been
     * initialized.
     */
    internal val assembler = assemblerFactory(@Suppress("LeakingThis") this)

    override fun resolveClass(className: String) =
        findClass(className) ?: assembler.createClassFromUnderlyingModel(className)
}
