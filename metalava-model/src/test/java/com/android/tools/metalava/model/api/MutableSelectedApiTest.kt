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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.flags.AdditionFlagState
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.flags.FlagState
import com.android.tools.metalava.model.api.flags.RemovalFlagState
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.PackageInfo
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

/**
 * Unit tests for [MutableSelectedApi.flagState] caching and package/removed flag state computation.
 */
class MutableSelectedApiTest {
    @Test
    fun `Test flagState cache invalidation on mutation`() {
        val apiSurfaces = ApiSurfaces.create()
        val main = apiSurfaces.main
        val annotationManager =
            DefaultAnnotationManager(
                DefaultAnnotationManager.Config(
                    apiFlags = ApiFlags(emptyList(), unknownFlagAction = ApiFlagAction.KEEP),
                )
            )
        val assembler =
            object : DefaultCodebaseAssembler() {
                override val codebase: DefaultCodebase =
                    DefaultCodebase(
                        location = File("test"),
                        description = "Test codebase",
                        preFiltered = false,
                        config =
                            Codebase.Config.NOOP.copy(
                                annotationManager = annotationManager,
                                apiSurfaces = apiSurfaces,
                            ),
                        trustedApi = false,
                        supportsDocumentation = false,
                        assembler = this,
                        selectedApiFactory = SelectedApi.MUTABLE_FACTORY,
                    )
                override val itemFactory: DefaultItemFactory =
                    DefaultItemFactory(codebase, SourceLanguage.JAVA)

                override fun createClassFromUnderlyingModel(qualifiedName: String) = null

                override fun getPackageInfoFromUnderlyingModel(packageName: String) =
                    PackageInfo.NO_COMMENT
            }

        val codebase = assembler.codebase
        val factory = assembler.itemFactory
        val pkg = codebase.findOrCreatePackage("test.pkg")

        // A package with only a REMOVED variant must still have FlagState.NONE.
        pkg.selectedApi.addItemApiVariant(main.variantFor(ApiVariantType.REMOVED))
        assertSame(FlagState.NONE, pkg.selectedApi.flagState)

        val flaggedClass =
            factory.createClassItem(
                modifiers =
                    createImmutableModifiers(
                        VisibilityLevel.PUBLIC,
                        listOf(
                            AnnotationItem.createFromSource(
                                codebase,
                                "@$ANDROID_FLAGGED_API(\"test.pkg.f1\")",
                            )!!
                        ),
                    ),
                classKind = ClassKind.CLASS,
                containingClass = null,
                containingPackage = pkg,
                qualifiedName = "test.pkg.FlaggedClass",
                typeParameterList = TypeParameterList.NONE,
                origin = ClassOrigin.COMMAND_LINE,
                superClassType = null,
                interfaceTypes = emptyList(),
            )

        // Read flagState before marking removed annotation, then verify cache invalidates.
        assertEquals(
            FlagState.additionFlag("test.pkg.f1", ApiFlagAction.KEEP),
            flaggedClass.selectedApi.flagState,
        )
        flaggedClass.selectedApi.markHasRemovedAnnotation()
        assertEquals(
            FlagState.removalFlag("test.pkg.f1", ApiFlagAction.KEEP),
            flaggedClass.selectedApi.flagState,
        )

        // Read flagState before adding REMOVED variant to unflagged class, then verify update.
        val unflaggedClass =
            factory.createClassItem(
                modifiers = createImmutableModifiers(VisibilityLevel.PUBLIC),
                classKind = ClassKind.CLASS,
                containingClass = null,
                containingPackage = pkg,
                qualifiedName = "test.pkg.RemovedClass",
                typeParameterList = TypeParameterList.NONE,
                origin = ClassOrigin.COMMAND_LINE,
                superClassType = null,
                interfaceTypes = emptyList(),
            )
        assertSame(FlagState.NONE, unflaggedClass.selectedApi.flagState)
        unflaggedClass.selectedApi.addItemApiVariant(main.variantFor(ApiVariantType.REMOVED))
        assertSame(FlagState.REMOVED, unflaggedClass.selectedApi.flagState)

        // Adding a REMOVED variant to an item with an addition @FlaggedApi (without
        // markHasRemovedAnnotation) preserves both AdditionFlagState.Flagged and
        // RemovalFlagState.Removed.
        val removedFlaggedClass =
            factory.createClassItem(
                modifiers =
                    createImmutableModifiers(
                        VisibilityLevel.PUBLIC,
                        listOf(
                            AnnotationItem.createFromSource(
                                codebase,
                                "@$ANDROID_FLAGGED_API(\"test.pkg.f2\")",
                            )!!
                        ),
                    ),
                classKind = ClassKind.CLASS,
                containingClass = null,
                containingPackage = pkg,
                qualifiedName = "test.pkg.RemovedFlaggedClass",
                typeParameterList = TypeParameterList.NONE,
                origin = ClassOrigin.COMMAND_LINE,
                superClassType = null,
                interfaceTypes = emptyList(),
            )
        removedFlaggedClass.selectedApi.addItemApiVariant(main.variantFor(ApiVariantType.REMOVED))
        assertEquals(
            FlagState(
                addition = AdditionFlagState.Flagged(setOf("test.pkg.f2")),
                removal = RemovalFlagState.Removed,
            ),
            removedFlaggedClass.selectedApi.flagState,
        )
    }
}
