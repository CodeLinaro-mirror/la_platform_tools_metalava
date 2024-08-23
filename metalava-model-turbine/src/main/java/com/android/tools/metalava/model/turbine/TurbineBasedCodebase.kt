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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.reporter.Reporter
import com.google.turbine.tree.Tree.CompUnit
import java.io.File

internal open class TurbineBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
    override val reporter: Reporter,
    val allowReadingComments: Boolean,
) :
    DefaultCodebase(
        location = location,
        description = description,
        preFiltered = false,
        annotationManager = annotationManager,
        trustedApi = false,
        supportsDocumentation = true,
    ) {

    private lateinit var initializer: TurbineCodebaseInitialiser

    override fun resolveClass(className: String) = findOrCreateClass(className)

    fun findOrCreateClass(className: String): ClassItem? {
        return initializer.findOrCreateClass(className)
    }

    fun initialize(
        units: List<CompUnit>,
        classpath: List<File>,
        packageHtmlByPackageName: Map<String, File>,
    ) {
        initializer = TurbineCodebaseInitialiser(units, this, classpath)
        initializer.initialize(packageHtmlByPackageName)
    }
}
