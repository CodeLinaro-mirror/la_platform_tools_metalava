/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.annotation

import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.ANNOTATION_STUBS_ONLY
import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import kotlin.test.assertEquals
import org.junit.Test

class DefaultAnnotationManagerTest {

    private fun createAnnotation(
        manager: DefaultAnnotationManager,
        source: String,
    ): AnnotationItem {
        val context =
            object : AnnotationContext, ClassResolver by ClassResolver.RETURN_NULL {
                override val annotationManager = manager
            }
        return AnnotationItem.createFromSource(context, source)
            ?: error("Could not create annotation from: '$source'")
    }

    @Test
    fun `Test hardcoded annotation targets`() {
        val manager = DefaultAnnotationManager()

        val suppressWarnings = createAnnotation(manager, "@java.lang.SuppressWarnings")
        assertEquals(NO_ANNOTATION_TARGETS, manager.computeTargets(suppressWarnings))

        val exportedProperty = createAnnotation(manager, "@android.view.ViewDebug.ExportedProperty")
        assertEquals(ANNOTATION_STUBS_ONLY, manager.computeTargets(exportedProperty))

        val target = createAnnotation(manager, "@kotlin.annotation.Target")
        assertEquals(ANNOTATION_IN_ALL_STUBS, manager.computeTargets(target))
    }

    @Test
    fun `Test configured annotation targets`() {
        val manager =
            DefaultAnnotationManager(
                DefaultAnnotationManager.Config(
                    annotationClassTargets =
                        mapOf(
                            "test.pkg.CustomExclude" to NO_ANNOTATION_TARGETS,
                            "test.pkg.CustomStubs" to ANNOTATION_STUBS_ONLY,
                        )
                )
            )

        val customExclude = createAnnotation(manager, "@test.pkg.CustomExclude")
        assertEquals(NO_ANNOTATION_TARGETS, manager.computeTargets(customExclude))

        val customStubs = createAnnotation(manager, "@test.pkg.CustomStubs")
        assertEquals(ANNOTATION_STUBS_ONLY, manager.computeTargets(customStubs))
    }
}
