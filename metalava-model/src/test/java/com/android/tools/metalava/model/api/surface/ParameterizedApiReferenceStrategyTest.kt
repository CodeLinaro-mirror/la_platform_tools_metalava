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

package com.android.tools.metalava.model.api.surface

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.flags.FlagState
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.PackageInfo
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Parameterized unit tests for [ApiReferenceStrategy] and [TargetSurfaceReferenceStrategy]. */
@RunWith(Parameterized::class)
class ParameterizedApiReferenceStrategyTest(private val params: Params) {

    /**
     * Parameters for a single reference strategy test case.
     *
     * @property targetSurface The target [ApiSurface] passed to [TargetSurfaceReferenceStrategy].
     * @property includeDocOnlyInCode Whether [ApiVariantType.DOC_ONLY] targets are allowed in
     *   [ReferenceKind.CODE] references from [ApiVariantType.CORE] items.
     * @property from The referencing [SelectableItem], or `null` when checking without a
     *   referencing item.
     * @property to The referenced [ClassItem].
     * @property kind Whether the reference is in [ReferenceKind.CODE] or
     *   [ReferenceKind.DOCUMENTATION].
     * @property conditionFlagState Any external conditional [FlagState] active at the reference
     *   site.
     * @property expectedResult The expected [ReferenceCheckResult] returned by
     *   [ApiReferenceStrategy.checkReference].
     * @property expectedReasonLabel Optional expected [ReferenceCheckResult.Invalid.reasonLabel]
     *   when [expectedResult] is invalid.
     */
    data class Params(
        val targetSurface: ApiSurface = main,
        val includeDocOnlyInCode: Boolean = false,
        val from: SelectableItem? = null,
        val to: ClassItem,
        val kind: ReferenceKind = ReferenceKind.CODE,
        val conditionFlagState: FlagState = FlagState.NONE,
        val expectedResult: ReferenceCheckResult,
        val expectedReasonLabel: String? = null,
    ) {
        override fun toString(): String = buildString {
            if (targetSurface != main) {
                append("target=${targetSurface.name}, ")
            }
            if (includeDocOnlyInCode) {
                append("includeDocOnlyInCode=true, ")
            }
            val fromName =
                when (from) {
                    null -> "null"
                    is ClassItem -> from.simpleName()
                    is MemberItem -> "${from.containingClass().simpleName()}.${from.name()}"
                    else -> from.toString()
                }
            append("from=$fromName, ")
            append("to=${to.simpleName()}, ")
            append("kind=$kind")

            // Omit default `FlagState.NONE` to keep parameterized test names concise.
            if (conditionFlagState != FlagState.NONE) {
                append(", conditionFlagState=$conditionFlagState")
            }
        }
    }

    /** Helper context wrapping a lightweight [DefaultCodebase] and [DefaultItemFactory]. */
    private class TestContext(
        val codebase: DefaultCodebase,
        val factory: DefaultItemFactory,
    ) {
        private val pkg = codebase.findOrCreatePackage("test.pkg")

        /**
         * Create a synthetic [ClassItem] with the given [name], [visibility], [variants], optional
         * [containingClass], optional `@FlaggedApi` [flagName], and optional [hasRemovedAnnotation]
         * marker.
         */
        fun createTestClass(
            name: String,
            visibility: VisibilityLevel = VisibilityLevel.PUBLIC,
            variants: List<ApiVariant> = emptyList(),
            containingClass: ClassItem? = null,
            flagName: String? = null,
            hasRemovedAnnotation: Boolean = false,
        ): ClassItem {
            val annotations =
                if (flagName != null) {
                    listOf(
                        AnnotationItem.createFromSource(
                            codebase,
                            "@$ANDROID_FLAGGED_API(\"$flagName\")",
                        )!!
                    )
                } else {
                    emptyList()
                }
            val qualifiedName =
                if (containingClass != null) {
                    "${containingClass.qualifiedName()}.$name"
                } else {
                    "test.pkg.$name"
                }
            val classItem =
                factory.createClassItem(
                    modifiers = createImmutableModifiers(visibility, annotations),
                    classKind = ClassKind.CLASS,
                    containingClass = containingClass,
                    containingPackage = pkg,
                    qualifiedName = qualifiedName,
                    typeParameterList = TypeParameterList.NONE,
                    origin = ClassOrigin.COMMAND_LINE,
                    superClassType = null,
                    interfaceTypes = emptyList(),
                )
            for (variant in variants) {
                classItem.selectedApi.addItemApiVariant(variant)
            }
            if (hasRemovedAnnotation) {
                classItem.selectedApi.markHasRemovedAnnotation()
            }
            return classItem
        }

        /** Create a synthetic [FieldItem] with the given [name] inside [containingClass]. */
        fun createTestField(
            name: String,
            containingClass: ClassItem,
            visibility: VisibilityLevel = VisibilityLevel.PUBLIC,
            variants: List<ApiVariant> = emptyList(),
        ): FieldItem {
            val fieldItem =
                factory.createFieldItem(
                    modifiers = createImmutableModifiers(visibility),
                    documentationFactory = ItemDocumentation.NONE_FACTORY,
                    name = name,
                    containingClass = containingClass,
                    type = containingClass.type(),
                    isEnumConstant = false,
                    constantValueProvider = null,
                )
            for (variant in variants) {
                fieldItem.selectedApi.addItemApiVariant(variant)
            }
            return fieldItem
        }
    }

    companion object {
        private val apiSurfaces = ApiSurfaces.create(needsBase = true)
        private val base = apiSurfaces.base!!
        private val main = apiSurfaces.main

        private val context = createTestContext(apiSurfaces)

        /** Public class belonging to the `base` surface's CORE variant. */
        private val baseCoreItem =
            context.createTestClass(
                "BaseCoreClass",
                variants = listOf(base.variantFor(ApiVariantType.CORE)),
            )

        /** Public class belonging to the `main` surface's CORE variant. */
        private val coreItem =
            context.createTestClass(
                "CoreClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
            )

        /** Public class belonging to the `main` surface's DOC_ONLY variant (`@DocOnly`). */
        private val docOnlyItem =
            context.createTestClass(
                "DocOnlyClass",
                variants = listOf(main.variantFor(ApiVariantType.DOC_ONLY)),
            )

        /** Public class belonging to the `main` surface's REMOVED variant (`@removed`). */
        private val removedItem =
            context.createTestClass(
                "RemovedClass",
                variants = listOf(main.variantFor(ApiVariantType.REMOVED)),
            )

        /** Public class not belonging to any API surface (e.g. `@hide`). */
        private val hiddenPublicItem = context.createTestClass("HiddenPublicClass")

        /**
         * Nested public class inside [hiddenPublicItem] (e.g. a nested class inside a class hidden
         * by `@hide` or a disabled `@FlaggedApi`).
         */
        private val nestedInHiddenPublicItem =
            context.createTestClass(
                "NestedInHiddenPublicClass",
                containingClass = hiddenPublicItem,
            )

        /**
         * Public field inside [hiddenPublicItem] (e.g. a member inside a class hidden by `@hide` or
         * a disabled `@FlaggedApi`).
         */
        private val memberInHiddenPublicItem =
            context.createTestField(
                "memberInHiddenPublicClass",
                containingClass = hiddenPublicItem,
            )

        /** Package-private class not belonging to any API surface. */
        private val packagePrivateItem =
            context.createTestClass(
                "PackagePrivateClass",
                visibility = VisibilityLevel.PACKAGE_PRIVATE,
            )

        /** Public class in `main` CORE guarded by `@FlaggedApi("test.pkg.flag1")`. */
        private val flaggedItem =
            context.createTestClass(
                "FlaggedClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.flag1",
            )

        /** Public class in `main` CORE guarded by a different kept flag (`"test.pkg.flag2"`). */
        private val otherFlaggedItem =
            context.createTestClass(
                "OtherFlaggedClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.flag2",
            )

        /**
         * Public class in `main` CORE annotated with `@FlaggedApi("test.pkg.finalized_flag")` whose
         * flag has [ApiFlagAction.FINALIZE].
         */
        private val finalizedFlaggedItem =
            context.createTestClass(
                "FinalizedFlaggedClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.finalized_flag",
            )

        /**
         * Nested public class inside [flaggedItem] without its own `@FlaggedApi` annotation;
         * inherits `"test.pkg.flag1"` from its enclosing class.
         */
        private val nestedInFlaggedItem =
            context.createTestClass(
                "NestedInFlaggedClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                containingClass = flaggedItem,
            )

        /** Public class in `base` CORE guarded by `@FlaggedApi("test.pkg.flag1")`. */
        private val baseFlaggedItem =
            context.createTestClass(
                "BaseFlaggedClass",
                variants = listOf(base.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.flag1",
            )

        /**
         * Nested public class in `main` CORE inside [baseFlaggedItem] (which is in `base` CORE)
         * without its own `@FlaggedApi` annotation; inherits `"test.pkg.flag1"` from its enclosing
         * class in `base`.
         */
        private val nestedInBaseFlaggedItem =
            context.createTestClass(
                "NestedInBaseFlaggedClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                containingClass = baseFlaggedItem,
            )

        /**
         * Public class in `main` CORE scheduled for removal by `@RemovedFromApi` and
         * `@FlaggedApi("test.pkg.remove_flag")`.
         */
        private val flaggedForRemovalItem =
            context.createTestClass(
                "FlaggedForRemovalClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.remove_flag",
                hasRemovedAnnotation = true,
            )

        /**
         * Public class in `main` CORE scheduled for removal by `@RemovedFromApi` and a different
         * kept flag (`"test.pkg.other_remove_flag"`).
         */
        private val otherFlaggedForRemovalItem =
            context.createTestClass(
                "OtherFlaggedForRemovalClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.other_remove_flag",
                hasRemovedAnnotation = true,
            )

        /**
         * Public class in `main` CORE annotated with `@RemovedFromApi` and
         * `@FlaggedApi("test.pkg.reverted_remove_flag")` whose removal flag has
         * [ApiFlagAction.REVERT].
         */
        private val revertedFlaggedForRemovalItem =
            context.createTestClass(
                "RevertedFlaggedForRemovalClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.reverted_remove_flag",
                hasRemovedAnnotation = true,
            )

        /**
         * Nested public class inside [flaggedForRemovalItem] without its own `@RemovedFromApi` or
         * `@FlaggedApi` annotation; inherits `"test.pkg.remove_flag"` from its enclosing class.
         */
        private val nestedInFlaggedForRemovalItem =
            context.createTestClass(
                "NestedInFlaggedForRemovalClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                containingClass = flaggedForRemovalItem,
            )

        /**
         * Public class in `base` CORE scheduled for removal by `@RemovedFromApi` and
         * `@FlaggedApi("test.pkg.remove_flag")`.
         */
        private val baseFlaggedForRemovalItem =
            context.createTestClass(
                "BaseFlaggedForRemovalClass",
                variants = listOf(base.variantFor(ApiVariantType.CORE)),
                flagName = "test.pkg.remove_flag",
                hasRemovedAnnotation = true,
            )

        /**
         * Nested public class in `main` CORE inside [baseFlaggedForRemovalItem] (which is in `base`
         * CORE) without its own `@RemovedFromApi` or `@FlaggedApi` annotation; inherits
         * `"test.pkg.remove_flag"` from its enclosing class in `base`.
         */
        private val nestedInBaseFlaggedForRemovalItem =
            context.createTestClass(
                "NestedInBaseFlaggedForRemovalClass",
                variants = listOf(main.variantFor(ApiVariantType.CORE)),
                containingClass = baseFlaggedForRemovalItem,
            )

        /** Create a [TestContext] configured with [apiSurfaces] and mutable [SelectedApi]s. */
        private fun createTestContext(apiSurfaces: ApiSurfaces): TestContext {
            // Configure `finalized_flag` as FINALIZE; any other flag defaults to KEEP.
            val apiFlags =
                ApiFlags(
                    listOf(
                        ApiFlag("test.pkg.finalized_flag", ApiFlagAction.FINALIZE),

                        // Reverting a removal flag cancels scheduled removal (retaining the item).
                        ApiFlag("test.pkg.reverted_remove_flag", ApiFlagAction.REVERT),
                    ),
                    unknownFlagAction = ApiFlagAction.KEEP,
                )
            val annotationManager =
                DefaultAnnotationManager(
                    DefaultAnnotationManager.Config(
                        apiFlags = apiFlags,
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
            return TestContext(assembler.codebase, assembler.itemFactory)
        }

        private val params =
            listOf(
                // References from null and CORE in CODE
                Params(
                    from = null,
                    to = coreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = null,
                    to = baseCoreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = null,
                    to = docOnlyItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.DocOnly,
                    expectedReasonLabel = "doconly",
                ),

                // `@removed` items are invalid targets outside `REMOVED` code and report reason
                // `"removed"`.
                Params(
                    from = null,
                    to = removedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Removed,
                    expectedReasonLabel = "removed",
                ),
                Params(
                    from = null,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                    expectedReasonLabel = "hidden",
                ),
                Params(
                    from = null,
                    to = packagePrivateItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = false),
                    expectedReasonLabel = "not public",
                ),
                Params(
                    from = coreItem,
                    to = coreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.DocOnly,
                ),
                Params(
                    from = coreItem,
                    to = removedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Removed,
                ),
                Params(
                    from = coreItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
                Params(
                    from = coreItem,
                    to = packagePrivateItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = false),
                ),
                // References from null, CORE, and DOC_ONLY in DOCUMENTATION
                Params(
                    from = null,
                    to = coreItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = null,
                    to = docOnlyItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = null,
                    to = removedItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Removed,
                ),
                Params(
                    from = coreItem,
                    to = coreItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = removedItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Removed,
                ),
                Params(
                    from = coreItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
                Params(
                    from = coreItem,
                    to = packagePrivateItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = false),
                ),
                Params(
                    from = docOnlyItem,
                    to = coreItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = docOnlyItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // References from DOC_ONLY in CODE (valid to CORE and DOC_ONLY, invalid to Removed
                // and Hidden)
                Params(
                    from = docOnlyItem,
                    to = coreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = docOnlyItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = docOnlyItem,
                    to = removedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Removed,
                ),
                Params(
                    from = docOnlyItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),

                // References from REMOVED in CODE (valid to CORE and REMOVED, invalid to DOC_ONLY
                // and Hidden)
                Params(
                    from = removedItem,
                    to = coreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = removedItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.DocOnly,
                ),
                Params(
                    from = removedItem,
                    to = removedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = removedItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),

                // includeDocOnlyInCode = true (e.g. doc stubs)
                Params(
                    includeDocOnlyInCode = true,
                    from = coreItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Target surface `base` only includes `base`, so items in `main` report
                // WiderSurface(fromSurface = base, toSurface = main) when valid in `main`
                Params(
                    targetSurface = base,
                    from = null,
                    to = baseCoreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    targetSurface = base,
                    from = null,
                    to = coreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.WiderSurface(
                            fromSurface = base,
                            toSurface = main,
                        ),
                    expectedReasonLabel = "in a wider API surface (main) than base",
                ),
                Params(
                    targetSurface = base,
                    from = baseCoreItem,
                    to = coreItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.WiderSurface(
                            fromSurface = base,
                            toSurface = main,
                        ),
                ),
                Params(
                    targetSurface = base,
                    from = baseCoreItem,
                    to = docOnlyItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult =
                        ReferenceCheckResult.WiderSurface(
                            fromSurface = base,
                            toSurface = main,
                        ),
                ),

                // `@removed` items in wider surfaces are still classified as `Hidden` rather than
                // `WiderSurface`.
                Params(
                    targetSurface = base,
                    from = baseCoreItem,
                    to = removedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),

                // References from a nested class or member to its containing class
                Params(
                    from = nestedInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = nestedInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = memberInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = memberInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // References to @FlaggedApi items (`UnguardedFlaggedApi`).
                // Global (`from = null`) surface membership checks ignore per-item `@FlaggedApi`.
                Params(
                    from = null,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Finalized/unflagged CORE items referencing an unfinalized `@FlaggedApi` item
                // report `UnguardedFlaggedApi` in both CODE and DOCUMENTATION.
                Params(
                    from = coreItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                    expectedReasonLabel = "guarded by @FlaggedApi(test.pkg.flag1)",
                ),
                Params(
                    from = coreItem,
                    to = flaggedItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                ),

                // Self, nested-to-enclosing, and enclosing-to-nested references under the same
                // unfinalized flag are valid.
                Params(
                    from = flaggedItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = nestedInFlaggedItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = flaggedItem,
                    to = nestedInFlaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Nested classes inside a `@FlaggedApi` class inherit its flag (both within `main`
                // and across `base` -> `main`).
                Params(
                    from = coreItem,
                    to = nestedInFlaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                ),
                Params(
                    from = nestedInBaseFlaggedItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = nestedInBaseFlaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                ),

                // Under permanence ordering, an unfinalized `@FlaggedApi` item may reference
                // another unfinalized `@FlaggedApi` item (`Flagged` is not `>` `Flagged`).
                Params(
                    from = otherFlaggedItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Finalized `@FlaggedApi` items have `AdditionFlagState.Finalized`, so referencing
                // an unfinalized target from them is unguarded, while referencing them is valid.
                Params(
                    from = finalizedFlaggedItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                ),
                Params(
                    from = coreItem,
                    to = finalizedFlaggedItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // An unfinalized `conditionFlagState` guards DOCUMENTATION references, whereas a
                // finalized `conditionFlagState` or a CODE reference remains unguarded.
                Params(
                    from = coreItem,
                    to = flaggedItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    conditionFlagState =
                        FlagState.additionFlag("test.pkg.flag1", ApiFlagAction.KEEP),
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = flaggedItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    conditionFlagState =
                        FlagState.additionFlag("test.pkg.finalized_flag", ApiFlagAction.FINALIZE),
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                ),
                Params(
                    from = coreItem,
                    to = flaggedItem,
                    kind = ReferenceKind.CODE,
                    conditionFlagState =
                        FlagState.additionFlag("test.pkg.flag1", ApiFlagAction.KEEP),
                    expectedResult =
                        ReferenceCheckResult.UnguardedFlaggedApi(setOf("test.pkg.flag1")),
                ),

                // References to @RemovedFromApi @FlaggedApi items (`FlaggedForRemoval`).
                // Global (`from = null`) surface membership checks ignore scheduled removal flags.
                Params(
                    from = null,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Retained items (including items guarded only by an addition `@FlaggedApi`, since
                // addition and removal permanence are orthogonal) report `FlaggedForRemoval`.
                Params(
                    from = coreItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                    expectedReasonLabel =
                        "scheduled for removal by @FlaggedApi(test.pkg.remove_flag)",
                ),
                Params(
                    from = flaggedItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                ),

                // Items also scheduled for removal (`FlaggedForRemoval` is not `>`
                // `FlaggedForRemoval`) may reference scheduled-removal targets.
                Params(
                    from = flaggedForRemovalItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = otherFlaggedForRemovalItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Items whose removal flag was reverted have `RemovalFlagState.Retained`, so
                // referencing a scheduled-removal target from them reports `FlaggedForRemoval`,
                // whereas referencing them from `coreItem` is valid.
                Params(
                    from = revertedFlaggedForRemovalItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                ),
                Params(
                    from = coreItem,
                    to = revertedFlaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // Nested classes inside a scheduled-removal class inherit its removal flag state
                // (both within `main` and across `base` -> `main`).
                Params(
                    from = nestedInFlaggedForRemovalItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = nestedInFlaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                ),
                Params(
                    from = nestedInBaseFlaggedForRemovalItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = nestedInBaseFlaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                ),

                // Already-removed items (`RemovalFlagState.Removed < FlaggedForRemoval`) may
                // reference items scheduled for removal.
                Params(
                    from = removedItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Valid,
                ),

                // An active removal `conditionFlagState` guards DOCUMENTATION references, whereas a
                // reverted removal `conditionFlagState` or a CODE reference reports
                // `FlaggedForRemoval`.
                Params(
                    from = coreItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    conditionFlagState =
                        FlagState.removalFlag("test.pkg.remove_flag", ApiFlagAction.KEEP),
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    conditionFlagState =
                        FlagState.removalFlag(
                            "test.pkg.reverted_remove_flag",
                            ApiFlagAction.REVERT,
                        ),
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                ),
                Params(
                    from = coreItem,
                    to = flaggedForRemovalItem,
                    kind = ReferenceKind.CODE,
                    conditionFlagState =
                        FlagState.removalFlag("test.pkg.remove_flag", ApiFlagAction.KEEP),
                    expectedResult =
                        ReferenceCheckResult.FlaggedForRemoval(setOf("test.pkg.remove_flag")),
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun testParameters() = params
    }

    @Test
    fun `Test reference strategy`() {
        val strategy =
            TargetSurfaceReferenceStrategy(
                targetSurface = params.targetSurface,
                includeDocOnlyInCode = params.includeDocOnlyInCode,
            )

        // Verify checkReference() returns the expected ReferenceCheckResult.
        val result =
            strategy.checkReference(
                params.from,
                params.to,
                params.kind,
                params.conditionFlagState,
            )
        assertEquals(params.expectedResult, result)

        // Verify isValidReference() matches result.isValid.
        assertEquals(
            params.expectedResult.isValid,
            strategy.isValidReference(
                params.from,
                params.to,
                params.kind,
                params.conditionFlagState,
            ),
        )

        // When an expected reasonLabel is provided for an invalid result, verify it matches.
        if (result is ReferenceCheckResult.Invalid && params.expectedReasonLabel != null) {
            assertEquals(params.expectedReasonLabel, result.reasonLabel)
        }
    }
}
