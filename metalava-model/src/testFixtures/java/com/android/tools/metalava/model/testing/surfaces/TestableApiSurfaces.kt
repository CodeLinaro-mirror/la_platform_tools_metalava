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

package com.android.tools.metalava.model.testing.surfaces

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.testing.java

/** Provides shared objects for testing API surface related functionality. */
object TestableApiSurfaces {
    /** Create a marker annotation called [name]. */
    private fun createAnnotation(name: String) =
        AnnotationItem.createMarkerAnnotation(AnnotationContext.DEFAULT_RESOLVE_NULL, name)!!

    /** Create a [TestFile] for a marker annotation called [name]. */
    private fun createAnnotationSource(name: String): TestFile {
        val packageName = name.substringBeforeLast('.')
        val simpleName = name.substringAfterLast('.')
        return java(
            """
                package $packageName;
                $HIDE
                public @interface $simpleName {}
            """
        )
    }

    /** An annotation that will be used to hide APIs. */
    val HIDE = createAnnotation("test.api.Hide")

    /** An annotation that will be used to remove an item. */
    val REMOVED_FROM_API = createAnnotation("test.api.RemovedFromApi")

    /** An annotation that will be used to only include an item for documentation purposes. */
    val DOC_ONLY = createAnnotation("test.api.DocOnly")

    /** An annotation that will be used to include an item in the unannotated API. */
    val UNANNOTATED_API = createAnnotation("test.api.UnannotatedApi")

    /**
     * An annotation that will be used to include an item in the unannotated API but not its
     * contents.
     */
    val UNANNOTATED_NON_RECURSIVE_API = createAnnotation("test.api.UnannotatedNonRecursiveApi")

    /** An annotation that will be used to include an item in the public API. */
    val PUBLIC_API = createAnnotation("test.api.PublicApi")

    /** An annotation that will be used to include an item in the system API. */
    val SYSTEM_API = createAnnotation("test.api.SystemApi")

    /** An annotation that will be used to include an item in the module API. */
    val MODULE_API = createAnnotation("test.api.ModuleApi")

    /**
     * An annotation that will be used to include an item in the module API but not including its
     * contents.
     */
    val MODULE_API_NON_RECURSIVE = createAnnotation("test.api.ModuleApiNonRecursive")

    /** An annotation that will be used to include an item in the standalone API. */
    val STANDALONE_API = createAnnotation("test.api.StandaloneApi")

    /** All annotation test source files for the annotations in this object. */
    val annotationSources =
        listOf(
                HIDE,
                REMOVED_FROM_API,
                DOC_ONLY,
                UNANNOTATED_API,
                UNANNOTATED_NON_RECURSIVE_API,
                PUBLIC_API,
                SYSTEM_API,
                MODULE_API,
                MODULE_API_NON_RECURSIVE,
                STANDALONE_API,
            )
            .map { createAnnotationSource(it.qualifiedName) }

    /** A set of API surfaces that includes a single `public` surface. */
    private val publicOnlySurfaces = ApiSurfaces.build { createSurface("public", isMain = true) }

    /** Variant rules (such as doc-only and removed) that are applicable across all surfaces. */
    private val variantRules =
        listOf(
            SurfaceSelectionRule.createAnnotationRule(
                DOC_ONLY.qualifiedName,
                effect = SurfaceSelectionRule.Effect.DOC_ONLY,
            ),
            SurfaceSelectionRule.createAnnotationRule(
                REMOVED_FROM_API.qualifiedName,
                effect = SurfaceSelectionRule.Effect.REMOVED,
            ),
        )

    /**
     * [ApiSurfaceRules] that define a simple public API that does not include any unannotated
     * items.
     */
    val annotatedOnlyRules =
        ApiSurfaceRules(
            publicOnlySurfaces,
            mapOf(
                "public" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(
                            HIDE.qualifiedName,
                            effect = SurfaceSelectionRule.Effect.HIDE,
                        ),
                        SurfaceSelectionRule.createAnnotationRule(UNANNOTATED_API.qualifiedName),
                        SurfaceSelectionRule.createAnnotationRule(
                            UNANNOTATED_NON_RECURSIVE_API.qualifiedName,
                            recursive = false,
                        ),
                    ),
            ),
            variantRules,
        )

    /** A set of API surfaces that includes `public`, `system` and `module` surfaces. */
    private val publicSystemModuleSurfaces =
        ApiSurfaces.build {
            createSurface("public")
            createSurface("system", extends = "public")
            createSurface("module", extends = "system", isMain = true)
        }

    /** [ApiSurfaceRules] that define a public, system and module APIs. */
    val publicSystemModuleRules =
        ApiSurfaceRules(
            publicSystemModuleSurfaces,
            mapOf(
                "public" to
                    listOf(
                        SurfaceSelectionRule.unannotated,
                        SurfaceSelectionRule.createAnnotationRule(
                            HIDE.qualifiedName,
                            effect = SurfaceSelectionRule.Effect.HIDE,
                        ),
                        SurfaceSelectionRule.createAnnotationRule(PUBLIC_API.qualifiedName),
                    ),
                "system" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(SYSTEM_API.qualifiedName),
                    ),
                "module" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(MODULE_API.qualifiedName),
                        SurfaceSelectionRule.createAnnotationRule(
                            MODULE_API_NON_RECURSIVE.qualifiedName,
                            recursive = false,
                        ),
                    ),
            ),
            variantRules,
        )

    /**
     * [ApiSurfaceRules] that define public, system and module APIs that do not include any
     * unannotated items.
     */
    val annotatedOnlyPublicSystemModuleRules =
        ApiSurfaceRules(
            publicSystemModuleSurfaces,
            mapOf(
                "public" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(
                            HIDE.qualifiedName,
                            effect = SurfaceSelectionRule.Effect.HIDE,
                        ),
                        SurfaceSelectionRule.createAnnotationRule(PUBLIC_API.qualifiedName),
                    ),
                "system" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(SYSTEM_API.qualifiedName),
                    ),
                "module" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(MODULE_API.qualifiedName),
                        SurfaceSelectionRule.createAnnotationRule(
                            MODULE_API_NON_RECURSIVE.qualifiedName,
                            recursive = false,
                        ),
                    ),
            ),
            variantRules,
        )

    /** A set of API surfaces that includes only a `standalone` surface. */
    private val standaloneSurfaces =
        ApiSurfaces.build {
            createSurface(
                "standalone",
                isMain = true,
            )
        }

    /** [ApiSurfaceRules] that define a standalone API that combines public and standalone rules. */
    val publicStandaloneRules =
        ApiSurfaceRules(
            standaloneSurfaces,
            mapOf(
                "standalone" to
                    listOf(
                        SurfaceSelectionRule.unannotated,
                        SurfaceSelectionRule.createAnnotationRule(
                            HIDE.qualifiedName,
                            effect = SurfaceSelectionRule.Effect.HIDE,
                        ),
                        SurfaceSelectionRule.createAnnotationRule(PUBLIC_API.qualifiedName),
                        SurfaceSelectionRule.createAnnotationRule(STANDALONE_API.qualifiedName),
                    ),
            ),
            variantRules,
        )
}
