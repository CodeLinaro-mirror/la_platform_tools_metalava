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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.PackageInfo
import com.android.tools.metalava.model.source.doc.DocCommentContext
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.LocationSpecificReporter
import com.android.tools.metalava.reporter.RecordingReporter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [AbstractItemDocumentation], including [DocCommentContext.checkReference]. */
class AbstractItemDocumentationTest {

    /**
     * Lightweight test context backed by a [DefaultCodebase], [DefaultAnnotationManager], and
     * [SelectedApi.sourceFactory] so [AbstractItemDocumentation] can be exercised directly.
     */
    private class TestContext(
        packageDocs: Map<String, String> = emptyMap(),
    ) {
        private val apiSurfaceRules = TestableApiSurfaces.publicStandaloneRules
        private val codebaseReporter =
            RecordingReporter(excludedIssues = setOf(Issues.DEPRECATED_SURFACE_DOC_TAG))
        private val codebaseConfig =
            Codebase.Config(
                annotationManager =
                    DefaultAnnotationManager(
                        DefaultAnnotationManager.Config(
                            reporter = codebaseReporter,
                            apiSurfaceSelector = ApiSurfaceSelector(apiSurfaceRules),
                        )
                    ),
                apiSurfaces = apiSurfaceRules.apiSurfaces,
                reporter = codebaseReporter,
            )

        private val assembler =
            object : DefaultCodebaseAssembler() {
                override val codebase: DefaultCodebase =
                    DefaultCodebase(
                        location = File("test"),
                        description = "Test codebase",
                        preFiltered = false,
                        config = codebaseConfig,
                        trustedApi = false,
                        supportsDocumentation = true,
                        assembler = this,
                        selectedApiFactory = SelectedApi.sourceFactory(codebaseConfig),
                    )

                override val itemFactory: DefaultItemFactory =
                    DefaultItemFactory(codebase, SourceLanguage.JAVA)

                override fun createClassFromUnderlyingModel(qualifiedName: String) = null

                override fun getPackageInfoFromUnderlyingModel(packageName: String): PackageInfo {
                    val doc = packageDocs[packageName]
                    val commentFactory =
                        doc?.toItemDocumentationFactory() ?: NO_SOURCE_COMMENT_FACTORY
                    return PackageInfo(commentFactory = commentFactory)
                }
            }

        val codebase: DefaultCodebase
            get() = assembler.codebase

        private fun docFactory(doc: String): ItemDocumentationFactory =
            if (doc.isEmpty()) NO_SOURCE_COMMENT_FACTORY else doc.toItemDocumentationFactory()

        private fun createAnnotations(annotationNames: List<String>): List<AnnotationItem> =
            annotationNames.map { AnnotationItem.createMarkerAnnotation(codebase, it)!! }

        fun findOrCreatePackage(name: String): PackageItem = codebase.findOrCreatePackage(name)

        fun createClass(
            qualifiedName: String,
            visibility: VisibilityLevel = VisibilityLevel.PUBLIC,
            classKind: ClassKind = ClassKind.CLASS,
            doc: String = "",
            annotationNames: List<String> = emptyList(),
            containingClass: ClassItem? = null,
        ): SkeletonClassItem {
            val packageName =
                containingClass?.containingPackage()?.qualifiedName()
                    ?: qualifiedName.substringBeforeLast('.')
            val pkg = codebase.findOrCreatePackage(packageName)
            return assembler.itemFactory.createClassItem(
                modifiers =
                    createImmutableModifiers(visibility, createAnnotations(annotationNames)),
                documentationFactory = docFactory(doc),
                classKind = classKind,
                containingClass = containingClass,
                containingPackage = pkg,
                qualifiedName = qualifiedName,
                typeParameterList = TypeParameterList.NONE,
                origin = ClassOrigin.COMMAND_LINE,
                superClassType = null,
                interfaceTypes = emptyList(),
            )
        }

        fun createField(
            containingClass: SkeletonClassItem,
            name: String,
            visibility: VisibilityLevel = VisibilityLevel.PUBLIC,
            doc: String = "",
            annotationNames: List<String> = emptyList(),
        ): FieldItem {
            val field =
                assembler.itemFactory.createFieldItem(
                    modifiers =
                        createImmutableModifiers(visibility, createAnnotations(annotationNames)),
                    documentationFactory = docFactory(doc),
                    name = name,
                    containingClass = containingClass,
                    type = containingClass.type(),
                    isEnumConstant = false,
                    constantValueProvider = null,
                )
            containingClass.addField(field)
            return field
        }

        /**
         * Invoke [DocCommentContext.checkReference] on [from]'s documentation targeting [to],
         * verify its [ReferenceCheckResult.isValid] matches whether an issue was reported, and
         * return any reported issue messages.
         */
        fun checkReference(from: SelectableItem, to: SelectableItem): String {
            val context = from.requiredDocumentation as DocCommentContext
            val reporter = RecordingReporter()
            val locationReporter =
                object : LocationSpecificReporter {
                    override fun report(issue: Issues.Issue, message: String) {
                        reporter.report(issue, null, message, FileLocation.UNKNOWN)
                    }
                }
            val result = context.checkReference(to, locationReporter)
            val issues = reporter.removeIssues()
            assertEquals(
                issues.isEmpty(),
                result.isValid,
                "checkReference return value for $from -> $to"
            )
            return issues
        }
    }

    @Test
    fun `Test checkReference on packages, classes, and fields`() {
        val context = TestContext(packageDocs = mapOf("hidden.pkg" to "/** @hide */"))
        val publicPkg = context.findOrCreatePackage("test.pkg")
        val hiddenPkg = context.findOrCreatePackage("hidden.pkg")

        val referencer = context.createClass("test.pkg.Referencer")
        val publicClass = context.createClass("test.pkg.PublicClass")
        val publicField = context.createField(publicClass, "publicField")
        val hiddenField = context.createField(publicClass, "hiddenField", doc = "/** @hide */")
        val packagePrivateField =
            context.createField(
                publicClass,
                "packagePrivateField",
                visibility = VisibilityLevel.PACKAGE_PRIVATE,
            )

        val docOnlyClass =
            context.createClass(
                "test.pkg.DocOnlyClass",
                annotationNames = listOf(TestableApiSurfaces.DOC_ONLY.qualifiedName),
            )
        val packagePrivateClass =
            context.createClass(
                "test.pkg.PackagePrivateClass",
                visibility = VisibilityLevel.PACKAGE_PRIVATE,
            )
        val hiddenClass = context.createClass("test.pkg.HiddenClass", doc = "/** @hide */")
        val fieldInHiddenClass = context.createField(hiddenClass, "field")
        val inHiddenPackage = context.createClass("hidden.pkg.InHiddenPackage")

        assertEquals("", context.checkReference(referencer, publicPkg))
        assertEquals("", context.checkReference(referencer, publicClass))
        assertEquals("", context.checkReference(referencer, publicField))
        assertEquals("", context.checkReference(referencer, docOnlyClass))

        assertEquals(
            "warning: Package hidden.pkg is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, hiddenPkg),
        )
        assertEquals(
            "warning: Class hidden.pkg.InHiddenPackage is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, inHiddenPackage),
        )
        assertEquals(
            "warning: Class test.pkg.PackagePrivateClass is not public (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, packagePrivateClass),
        )
        assertEquals(
            "warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, hiddenClass),
        )
        assertEquals(
            "warning: Field test.pkg.HiddenClass.field is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, fieldInHiddenClass),
        )
        assertEquals(
            "warning: Field test.pkg.PublicClass.hiddenField is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, hiddenField),
        )
        assertEquals(
            "warning: Field test.pkg.PublicClass.packagePrivateField is not public (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, packagePrivateField),
        )
    }

    @Test
    fun `Test checkReference on mapped annotations`() {
        val context = TestContext()
        val referencer = context.createClass("test.pkg.Referencer")

        // `@hide` annotation mapped to external `androidx.annotation.Nullable` (not in codebase).
        val androidNullable =
            context.createClass(
                "android.annotation.Nullable",
                classKind = ClassKind.ANNOTATION_TYPE,
                doc = "/** @hide */",
            )

        // `@hide` annotation mapped to external `androidx.annotation.IntDef` (not in codebase)
        // with unhidden, `@hide` doc-tagged, and `@Hide` surface-annotated fields.
        val androidIntDef =
            context.createClass(
                "android.annotation.IntDef",
                classKind = ClassKind.ANNOTATION_TYPE,
                doc = "/** @hide */",
            )
        val intDefDefaultFlag = context.createField(androidIntDef, "DEFAULT_FLAG")
        val intDefDocHiddenFlag =
            context.createField(androidIntDef, "DOC_HIDDEN_FLAG", doc = "/** @hide */")
        val intDefAnnotationHiddenFlag =
            context.createField(
                androidIntDef,
                "ANNOTATION_HIDDEN_FLAG",
                annotationNames = listOf(TestableApiSurfaces.HIDE.qualifiedName),
            )

        // `@hide` annotation mapped to public `androidx.annotation.NonNull` present in codebase.
        val androidNonNull =
            context.createClass(
                "android.annotation.NonNull",
                classKind = ClassKind.ANNOTATION_TYPE,
                doc = "/** @hide */",
            )
        val androidNonNullPublicField = context.createField(androidNonNull, "PUBLIC_FIELD")
        val androidNonNullHiddenField = context.createField(androidNonNull, "HIDDEN_FIELD")
        val androidNonNullUnmappedField = context.createField(androidNonNull, "UNMAPPED_FIELD")

        val androidxNonNull =
            context.createClass(
                "androidx.annotation.NonNull",
                classKind = ClassKind.ANNOTATION_TYPE,
            )
        context.createField(androidxNonNull, "PUBLIC_FIELD")
        context.createField(androidxNonNull, "HIDDEN_FIELD", doc = "/** @hide */")

        // `@hide` annotation mapped to `@hide androidx.annotation.IntRange` present in codebase.
        val androidIntRange =
            context.createClass(
                "android.annotation.IntRange",
                classKind = ClassKind.ANNOTATION_TYPE,
                doc = "/** @hide */",
            )
        context.createClass(
            "androidx.annotation.IntRange",
            classKind = ClassKind.ANNOTATION_TYPE,
            doc = "/** @hide */",
        )

        // Unmapped `@hide` annotation.
        val androidHide =
            context.createClass(
                "android.annotation.Hide",
                classKind = ClassKind.ANNOTATION_TYPE,
                doc = "/** @hide */",
            )

        assertEquals("", context.checkReference(referencer, androidNullable))
        assertEquals("", context.checkReference(referencer, intDefDefaultFlag))
        assertEquals(
            "warning: Field android.annotation.IntDef.DOC_HIDDEN_FLAG is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, intDefDocHiddenFlag),
        )
        assertEquals(
            "warning: Field android.annotation.IntDef.ANNOTATION_HIDDEN_FLAG is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, intDefAnnotationHiddenFlag),
        )
        assertEquals("", context.checkReference(referencer, androidNonNull))
        assertEquals("", context.checkReference(referencer, androidNonNullPublicField))
        assertEquals(
            "warning: Field androidx.annotation.NonNull.HIDDEN_FIELD is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, androidNonNullHiddenField),
        )
        assertEquals(
            "warning: Field android.annotation.NonNull.UNMAPPED_FIELD is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, androidNonNullUnmappedField),
        )
        assertEquals(
            "warning: Class androidx.annotation.IntRange is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, androidIntRange),
        )
        assertEquals(
            "warning: Class android.annotation.Hide is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(referencer, androidHide),
        )
    }

    @Test
    fun `Test checkReference from non-documented items`() {
        val context = TestContext()
        val hiddenTarget = context.createClass("test.pkg.HiddenTarget", doc = "/** @hide */")

        val hiddenClass = context.createClass("test.pkg.HiddenClass", doc = "/** @hide */")
        val fieldInHiddenClass = context.createField(hiddenClass, "field")
        val publicClass = context.createClass("test.pkg.PublicClass")
        val hiddenField = context.createField(publicClass, "hiddenField", doc = "/** @hide */")
        val removedClass = context.createClass("test.pkg.RemovedClass", doc = "/** @removed */")
        val removedAnnotatedClass =
            context.createClass(
                "test.pkg.RemovedAnnotatedClass",
                annotationNames = listOf(TestableApiSurfaces.REMOVED_FROM_API.qualifiedName),
            )
        val printedHiddenClass =
            context.createClass(
                "test.pkg.PrintedHiddenClass",
                doc = "/**\n * Summary.\n * @hide\n */",
            )
        val docOnlyClass =
            context.createClass(
                "test.pkg.DocOnlyClass",
                annotationNames = listOf(TestableApiSurfaces.DOC_ONLY.qualifiedName),
            )

        assertEquals("", context.checkReference(hiddenClass, hiddenTarget))
        assertEquals("", context.checkReference(fieldInHiddenClass, hiddenTarget))
        assertEquals("", context.checkReference(hiddenField, hiddenTarget))
        assertEquals("", context.checkReference(removedClass, hiddenTarget))
        assertEquals("", context.checkReference(removedAnnotatedClass, hiddenTarget))

        // Printing strips `@hide`, so `selectedApi` must be initialized beforehand.
        val printedDoc =
            StringWriter()
                .also { printedHiddenClass.requiredDocumentation.print(PrintWriter(it)) }
                .toString()
        assertEquals("/** Summary. */", printedDoc.trim())
        assertEquals("", context.checkReference(printedHiddenClass, hiddenTarget))

        // `@DocOnly` items are still documented, so references from them to hidden targets report.
        assertEquals(
            "warning: Class test.pkg.HiddenTarget is hidden (ErrorWhenNew) [HiddenDocReference]",
            context.checkReference(docOnlyClass, hiddenTarget),
        )
    }
}
