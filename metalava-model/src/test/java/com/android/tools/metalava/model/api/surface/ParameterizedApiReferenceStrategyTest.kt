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
import com.android.tools.metalava.model.api.SelectedApi
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
     * @property from The referencing [SelectableItem], or `null` when checking without a
     *   referencing item.
     * @property to The referenced [ClassItem].
     * @property kind Whether the reference is in [ReferenceKind.CODE] or
     *   [ReferenceKind.DOCUMENTATION].
     * @property expectedResult The expected [ReferenceCheckResult] returned by
     *   [ApiReferenceStrategy.checkReference].
     * @property expectedReasonLabel Optional expected [ReferenceCheckResult.Invalid.reasonLabel]
     *   when [expectedResult] is invalid.
     */
    data class Params(
        val targetSurface: ApiSurface = main,
        val from: SelectableItem? = null,
        val to: ClassItem,
        val kind: ReferenceKind = ReferenceKind.CODE,
        val expectedResult: ReferenceCheckResult,
        val expectedReasonLabel: String? = null,
    ) {
        override fun toString(): String = buildString {
            if (targetSurface != main) {
                append("target=${targetSurface.name}, ")
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
        }
    }

    /** Helper context wrapping a lightweight [DefaultCodebase] and [DefaultItemFactory]. */
    private class TestContext(
        val codebase: DefaultCodebase,
        val factory: DefaultItemFactory,
    ) {
        private val pkg = codebase.findOrCreatePackage("test.pkg")

        /**
         * Create a synthetic [ClassItem] with the given [name], [visibility], [variants], and
         * optional [containingClass].
         */
        fun createTestClass(
            name: String,
            visibility: VisibilityLevel = VisibilityLevel.PUBLIC,
            variants: List<ApiVariant> = emptyList(),
            containingClass: ClassItem? = null,
        ): ClassItem {
            val qualifiedName =
                if (containingClass != null) {
                    "${containingClass.qualifiedName()}.$name"
                } else {
                    "test.pkg.$name"
                }
            val classItem =
                factory.createClassItem(
                    modifiers = createImmutableModifiers(visibility),
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

        /** Create a [TestContext] configured with [apiSurfaces] and mutable [SelectedApi]s. */
        private fun createTestContext(apiSurfaces: ApiSurfaces): TestContext {
            val assembler =
                object : DefaultCodebaseAssembler() {
                    override val codebase: DefaultCodebase =
                        DefaultCodebase(
                            location = File("test"),
                            description = "Test codebase",
                            preFiltered = false,
                            config = Codebase.Config.NOOP.copy(apiSurfaces = apiSurfaces),
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
                // References from null and CORE in DOCUMENTATION
                Params(
                    from = null,
                    to = coreItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
                ),
                Params(
                    from = coreItem,
                    to = coreItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Valid,
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
                // Target surface `base` only includes `base`, so `coreItem` (in `main`) is hidden
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
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
                // References from a nested class or member to its containing class
                Params(
                    from = nestedInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
                Params(
                    from = nestedInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
                Params(
                    from = memberInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.CODE,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
                Params(
                    from = memberInHiddenPublicItem,
                    to = hiddenPublicItem,
                    kind = ReferenceKind.DOCUMENTATION,
                    expectedResult = ReferenceCheckResult.Hidden(isPublicOrProtected = true),
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun testParameters() = params
    }

    @Test
    fun `Test reference strategy`() {
        val strategy = TargetSurfaceReferenceStrategy(params.targetSurface)

        // Verify checkReference() returns the expected ReferenceCheckResult.
        val result = strategy.checkReference(params.from, params.to, params.kind)
        assertEquals(params.expectedResult, result)

        // Verify isValidReference() matches result.isValid.
        assertEquals(
            params.expectedResult.isValid,
            strategy.isValidReference(params.from, params.to, params.kind),
        )

        // When an expected reasonLabel is provided for an invalid result, verify it matches.
        if (result is ReferenceCheckResult.Invalid && params.expectedReasonLabel != null) {
            assertEquals(params.expectedReasonLabel, result.reasonLabel)
        }
    }
}
