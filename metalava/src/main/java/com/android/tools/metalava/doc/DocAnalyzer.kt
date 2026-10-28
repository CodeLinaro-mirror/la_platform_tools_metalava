/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.doc

import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.containsNullWord
import com.android.tools.metalava.doc.annotationhandlers.BaseDevicePolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.DevicePolicyContext
import com.android.tools.metalava.doc.annotationhandlers.EnumPolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.IntegerPolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.ListOfEnumPolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.ListOfPackagePolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.ListOfStringPolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.LongPolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.PackagePolicyAnnotationHandler
import com.android.tools.metalava.doc.annotationhandlers.StringPolicyAnnotationHandler
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROIDX_FLOAT_RANGE
import com.android.tools.metalava.model.ANDROIDX_INT_RANGE
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.KOTLIN_DEPRECATED
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ReferenceKind
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.FloatingPointValue
import com.android.tools.metalava.model.value.IntegralValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.asBoolean
import com.android.tools.metalava.model.value.asInt
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiFiltersVisitor
import com.android.tools.metalava.model.visitors.ApiSurfaceVisitor
import com.android.tools.metalava.permission.getRequiresPermissionProxy
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File

private const val DEFAULT_ENFORCEMENT = "android.content.pm.PackageManager#hasSystemFeature"

private const val CARRIER_PRIVILEGES_MARKER = "carrier privileges"

/** Lambda that when given an [ApiVersion] will return a string label for it. */
typealias ApiVersionLabelProvider = (ApiVersion) -> String

/**
 * Walk over the API and apply tweaks to the documentation, such as
 * - Looking for annotations and converting them to auxiliary tags that will be processed by the
 *   documentation tools later.
 * - Reading lint's API database and inserting metadata into the documentation like api versions and
 *   deprecation versions.
 * - Transferring docs from hidden super methods.
 * - Performing tweaks for common documentation mistakes, such as ending the first sentence with ",
 *   e.g. " where Javadoc will sadly see the ". " and think "aha, that's the end of the sentence!"
 *   (It works around this by replacing the space with &nbsp;.)
 */
class DocAnalyzer(
    private val executionEnvironment: ExecutionEnvironment,
    /** The codebase to analyze */
    private val codebase: Codebase,
    private val reporter: Reporter,

    /** Provides a string label for each [ApiVersion]. */
    private val apiVersionLabelProvider: ApiVersionLabelProvider,

    /** The [ApiSurface] whose items will be analyzed and/or enhanced. */
    apiSurface: ApiSurface,
) {
    /** Filters that selects items that will be analyzed and/or enhanced. */
    private val apiFilters = ApiFilters(reference = ApiSurfacePredicate.wholeCoreApi(apiSurface))

    /** Computes the visible part of the API from all the available code in the codebase */
    fun enhance() {
        // Apply options for packages that should be hidden
        documentsFromAnnotations()

        // TODO:
        // insertMissingDocFromHiddenSuperclasses()
    }

    /** Format this [Value] for use in handling `IntRange` and `FloatRange` annotations. */
    private fun Value.forRange(from: Item): String =
        when (this) {
            is IntegralValue<*> -> {
                // Format as an int if it will fit, otherwise a long.
                val asLong = underlyingValue.toLong()
                if (asLong >= Int.MIN_VALUE && asLong <= Int.MAX_VALUE) asLong.toString()
                else "${asLong}L"
            }
            is FloatingPointValue<*> -> {
                // Format as a float if it will fit, otherwise a double.
                val asDouble = underlyingValue.toDouble()
                if (asDouble >= -Float.MAX_VALUE && asDouble <= Float.MAX_VALUE) "${asDouble}f"
                else asDouble.toString()
            }
            is FieldReferenceValue -> {
                val field = resolve()
                if (field != null) {
                    // Format as a link to the field if it can be referenced.
                    linkToField(from, field) {
                        // Otherwise, format its literal value if available.
                        asLiteralValue()?.forRange(from) ?: toValueString()
                    }
                } else {
                    asLiteralValue()?.forRange(from) ?: toValueString()
                }
            }
            else -> {
                // Use default source formatting.
                toValueString()
            }
        }

    private fun documentsFromAnnotations() {
        // Note: Doclava1 inserts its own Javadoc parameters into the documentation,
        // which is then later processed by Javadoc to insert actual descriptions.
        // This indirection makes the actual descriptions of the annotations more
        // configurable from a separate file -- but since this tool isn't hooked
        // into Javadoc anymore (and is going to be used by for example Dokka too)
        // instead metalava will generate the descriptions directly in-line into the
        // docs.
        //
        // This does mean that you have to update the metalava source code to update
        // the docs -- but on the other hand all the other docs in the documentation
        // set also requires updating framework source code, so this doesn't seem
        // like an unreasonable burden.

        codebase.accept(
            object : ApiFiltersVisitor(apiFilters = apiFilters) {
                /** Contextual information for [BaseDevicePolicyAnnotationHandler]s. */
                private val devicePolicyContext = DevicePolicyContext(filterReference)

                override fun visitItem(item: Item) {
                    val annotations = item.modifiers.annotations()
                    if (annotations.isEmpty()) {
                        return
                    }

                    for (annotation in annotations) {
                        handleAnnotation(annotation, item, depth = 0)
                    }

                    // Handled via @memberDoc/@classDoc on the annotations themselves right now.
                    // That doesn't handle combinations of multiple thread annotations, but those
                    // don't occur yet, right?
                    if (findThreadAnnotations(annotations).size > 1) {
                        reporter.report(
                            Issues.MULTIPLE_THREAD_ANNOTATIONS,
                            item,
                            "Found more than one threading annotation on $item; " +
                                "the auto-doc feature does not handle this correctly"
                        )
                    }
                }

                private fun findThreadAnnotations(annotations: List<AnnotationItem>): List<String> {
                    var result: MutableList<String>? = null
                    for (annotation in annotations) {
                        val name = annotation.qualifiedName
                        if (
                            name.endsWith("Thread") && name.startsWith(ANDROIDX_ANNOTATION_PREFIX)
                        ) {
                            if (result == null) {
                                result = mutableListOf()
                            }
                            val threadName =
                                if (name.endsWith("UiThread")) {
                                    "UI"
                                } else {
                                    name.substring(
                                        name.lastIndexOf('.') + 1,
                                        name.length - "Thread".length
                                    )
                                }
                            result.add(threadName)
                        }
                    }
                    return result ?: emptyList()
                }

                /**
                 * Fallback if field can't be resolved or if an inlined string value is used then
                 * try and find the field in the `android.Manifest.permission` class if available.
                 */
                private fun findPermissionField(codebase: Codebase, value: Any): FieldItem? {
                    val perm = value.toString()
                    val permClass = codebase.findClass("android.Manifest.permission")
                    return permClass?.fields()?.firstOrNull { it.constantValue?.asString() == perm }
                }

                /**
                 * Handle the device policy [annotation] by generating some documentation and
                 * appending it to [item].
                 */
                private fun BaseDevicePolicyAnnotationHandler.handlePolicyAnnotation(
                    annotation: AnnotationItem,
                    item: Item,
                ) {
                    val doc = processPolicyAnnotation(annotation, item)
                    appendDocumentation(doc, item, returnValue = false)
                }

                private fun handleAnnotation(
                    annotation: AnnotationItem,
                    item: Item,
                    depth: Int,
                    visitedClasses: MutableSet<String> = mutableSetOf()
                ) {
                    val name = annotation.qualifiedName
                    if (name.startsWith(JAVA_LANG_PREFIX)) {
                        // Ignore java.lang.Retention etc.
                        return
                    }

                    if (item is ClassItem && name == item.qualifiedName()) {
                        // The annotation annotates itself; we shouldn't attempt to recursively
                        // pull in documentation from it; the documentation is already complete.
                        return
                    }

                    // Some annotations include the documentation they want inlined into usage docs.
                    // Copy those here:

                    handleInliningDocs(annotation, item)

                    when (name) {
                        "android.processor.devicepolicy.EnumPolicyDefinition" ->
                            EnumPolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.StringPolicyDefinition" ->
                            StringPolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.IntegerPolicyDefinition" ->
                            IntegerPolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.LongPolicyDefinition" ->
                            LongPolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.ListOfStringPolicyDefinition" ->
                            ListOfStringPolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.ListOfPackagePolicyDefinition" ->
                            ListOfPackagePolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.ListOfEnumPolicyDefinition" ->
                            ListOfEnumPolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "android.processor.devicepolicy.PackagePolicyDefinition" ->
                            PackagePolicyAnnotationHandler(devicePolicyContext)
                                .handlePolicyAnnotation(annotation, item)
                        "androidx.annotation.RequiresPermission" ->
                            handleRequiresPermission(annotation, item)
                        ANDROIDX_INT_RANGE,
                        ANDROIDX_FLOAT_RANGE -> handleRange(annotation, item)
                        "androidx.annotation.IntDef",
                        "androidx.annotation.LongDef",
                        "androidx.annotation.StringDef" -> handleTypeDef(annotation, item)
                        "android.annotation.RequiresFeature" ->
                            handleRequiresFeature(annotation, item)
                        "androidx.annotation.RequiresApi" ->
                            // The RequiresApi annotation can only be applied to SelectableItems,
                            // i.e. not ParameterItems, so ignore it on them.
                            if (item is SelectableItem) handleRequiresApi(annotation, item)
                        "android.provider.Column" -> handleColumn(annotation, item)
                        KOTLIN_DEPRECATED -> handleKotlinDeprecation(annotation, item)
                        "androidx.annotation.RestrictedForEnvironment" ->
                            handleRestrictedForEnvironment(annotation, item)
                    }

                    visitedClasses.add(name)
                    // Thread annotations are ignored here because they're handled as a group
                    // afterward.

                    // TODO: Resource type annotations

                    // Handle nested annotations
                    annotation.resolve()?.modifiers?.annotations()?.forEach { nested ->
                        if (depth == 20) { // Temp debugging
                            throw StackOverflowError(
                                "Unbounded recursion, processing annotation $annotation " +
                                    "in $item at ${annotation.fileLocation} "
                            )
                        } else if (nested.qualifiedName !in visitedClasses) {
                            handleAnnotation(nested, item, depth + 1, visitedClasses)
                        }
                    }
                }

                /**
                 * Called for [item]s that are annotated with the Kotlin [Deprecated] annotation.
                 *
                 * Its purpose is to ensure that it has an `@deprecated` Javadoc tag, creating one
                 * if possible and necessary. It does that just to prevent warning about missing
                 *
                 * @deprecated tags. The actual content is irrelevant as the documentation is not
                 *   used to generate Kotlin stubs.
                 */
                private fun handleKotlinDeprecation(annotation: AnnotationItem, item: Item) {
                    // Ignore Items without documentation.
                    if (item !is SelectableItem) return
                    val documentation = item.documentation ?: return

                    // Drop out if it already has a deprecated Javadoc tag.
                    if (documentation.hasBlockTagOfType("deprecated")) {
                        return
                    }

                    // Get the text from the annotation, returning if it could not be found or was
                    // blank.
                    val text =
                        (annotation.findAttribute("message")
                                ?: annotation.findAttribute(ANNOTATION_ATTR_VALUE))
                            ?.value
                            ?.asString() ?: return
                    if (text.isBlank()) {
                        return
                    }

                    // Create a deprecated block tag using the text from the Deprecated annotation.
                    documentation.addUniqueBlockTagSectionWithSimpleText("deprecated", text)
                }

                private fun documentationContainsNullWord(item: Item): Boolean {
                    return when (item) {
                        is ParameterItem -> {
                            item.description.containsNullWord()
                        }
                        is CallableItem -> {
                            val documentation = item.documentation ?: return false
                            // Don't inspect param docs (and other tags) for this purpose.
                            documentation.mainDescription.containsNullWord() ||
                                documentation.blockTagDescription("return").containsNullWord()
                        }
                        is SelectableItem -> {
                            val documentation = item.documentation ?: return false
                            documentation.mainDescription.containsNullWord()
                        }
                        else -> false
                    }
                }

                private fun handleInliningDocs(annotation: AnnotationItem, item: Item) {
                    if (annotation.isNullable() || annotation.isNonNull()) {
                        // Some docs already specifically talk about null policy; in that case,
                        // don't include the docs (since it may conflict with more specific
                        // conditions outlined in the docs).
                        if (documentationContainsNullWord(item)) return
                    }

                    when (item) {
                        is FieldItem -> {
                            addDoc(annotation, "memberDoc", item.descriptionOwner)
                        }
                        is CallableItem -> {
                            addDoc(annotation, "memberDoc", item.descriptionOwner)
                            val returnDescriptionOwner =
                                item.requiredDocumentation.blockTagDescriptionOwner("return")
                            addDoc(annotation, "returnDoc", returnDescriptionOwner)
                        }
                        is ClassItem -> {
                            addDoc(annotation, "classDoc", item.descriptionOwner)
                        }
                        is ParameterItem -> {
                            addDoc(annotation, "paramDoc", item.descriptionOwner)
                        }
                    }
                }

                private fun handleRequiresPermission(annotation: AnnotationItem, item: Item) {
                    if (item !is MemberItem) {
                        return
                    }

                    val requiresPermissionProxy =
                        annotation.getRequiresPermissionProxy(item) ?: return
                    val (values, any, conditional) = requiresPermissionProxy
                    if (values.isNotEmpty() && !conditional) {
                        // Look at macros_override.cs for the usage of these
                        // tags. In particular, search for def:dump_permission

                        val sb = StringBuilder(100)
                        sb.append("Requires ")
                        var first = true
                        for (value in values) {
                            when {
                                first -> first = false
                                any -> sb.append(" or ")
                                else -> sb.append(" and ")
                            }

                            val resolvedField = (value as? FieldReferenceValue)?.resolve()
                            val field =
                                if (resolvedField == null) {
                                    val v = value.asString() ?: value.toValueString()
                                    if (v == CARRIER_PRIVILEGES_MARKER) {
                                        // TODO: Warn if using allOf with carrier
                                        sb.append(
                                            "{@link android.telephony.TelephonyManager#hasCarrierPrivileges carrier privileges}"
                                        )
                                        continue
                                    } else {
                                        findPermissionField(codebase, v)
                                    }
                                } else resolvedField
                            if (field == null) {
                                reporter.report(
                                    Issues.MISSING_PERMISSION,
                                    item,
                                    "Cannot find permission field for ${value.toValueString()} required by $item (may be hidden or removed)"
                                )
                                sb.append(value.toValueString())
                            } else {
                                sb.append(
                                    linkToField(item, field) {
                                        reporter.report(
                                            Issues.MISSING_PERMISSION,
                                            item,
                                            "Permission $value required by $item is hidden or removed"
                                        )
                                        "${field.containingClass().qualifiedName()}.${field.name()}"
                                    }
                                )
                            }
                        }

                        appendDocumentation(sb.toString(), item, false)
                    }
                }

                private fun handleRange(annotation: AnnotationItem, item: Item) {
                    val from = annotation.findAttribute("from")?.value?.forRange(item)
                    val to = annotation.findAttribute("to")?.value?.forRange(item)
                    // TODO: inclusive/exclusive attributes on FloatRange!
                    if (from != null || to != null) {
                        val doc =
                            if (from != null && to != null) {
                                "Value is between $from and $to inclusive"
                            } else if (from != null) {
                                "Value is $from or greater"
                            } else {
                                "Value is $to or less"
                            }
                        appendDocumentation(doc, item, true)
                    }
                }

                private fun handleTypeDef(annotation: AnnotationItem, item: Item) {
                    val values = annotation.findAttribute("value")?.value?.asFlatList() ?: return
                    val flag = annotation.findAttribute("flag")?.value?.asBoolean() == true

                    // Look at macros_override.cs for the usage of these
                    // tags. In particular, search for def:dump_int_def

                    val sb = StringBuilder(100)
                    sb.append("Value is ")
                    if (flag) {
                        sb.append("either <code>0</code> or ")
                        if (values.size > 1) {
                            sb.append("a combination of the following:")
                        }
                    } else {
                        sb.append("one of the following:")
                    }

                    sb.append("\n <ul>")
                    for (value in values) {
                        val valueAsJavadoc = convertTypeDefValueToJavadoc(item, value) ?: continue
                        sb.append("\n   <li>")
                        sb.append(valueAsJavadoc)
                        sb.append("</li>")
                    }
                    sb.append("\n <ul>")

                    appendDocumentation(sb.toString(), item, true)
                }

                /**
                 * Convert [value] into Javadoc.
                 *
                 * If [value] is a reference to a field that is not part of the API then report an
                 * issue and do not wrap in a `{@link ...}`. If it is a reference to a field that is
                 * part of the API then wrap it in a `{@link ...}. Otherwise, just use its string
                 * representation.
                 */
                private fun convertTypeDefValueToJavadoc(
                    item: Item,
                    value: ArrayElementValue
                ): String? {
                    val field = (value as? FieldReferenceValue)?.resolve()
                    return if (field is FieldItem) {
                        linkToField(item, field) {
                            // Typedef annotation references field which isn't part of the API:
                            // don't try to link to it.
                            reporter.report(
                                Issues.HIDDEN_TYPEDEF_CONSTANT,
                                item,
                                "Typedef references constant which isn't part of the API, skipping in documentation: " +
                                    "${field.containingClass().qualifiedName()}#${field.name()}"
                            )
                            // Do not include the field in the documentation
                            return null
                        }
                    } else {
                        value.toValueString()
                    }
                }

                private fun handleRequiresFeature(annotation: AnnotationItem, item: Item) {
                    val value = annotation.findAttribute("value")?.value ?: return
                    val field = (value as? FieldReferenceValue)?.resolve()
                    val featureField =
                        if (field == null) {
                            reporter.report(
                                Issues.MISSING_PERMISSION,
                                item,
                                "Cannot find feature field for ${value.toValueString()} required by $item (may be hidden or removed)"
                            )
                            return
                        } else {
                            val displayText =
                                "${field.containingClass().simpleName()}#${field.name()}"
                            linkToField(item, field, label = displayText) {
                                reporter.report(
                                    Issues.MISSING_PERMISSION,
                                    item,
                                    "Feature field ${value.toValueString()} required by $item is hidden or removed"
                                )
                                return
                            }
                        }

                    val enforcement =
                        annotation.findAttribute("enforcement")?.value?.asString()
                            ?: DEFAULT_ENFORCEMENT

                    // Compute the link uri and text from the enforcement setting.
                    val regexp = """((?:.*\.)?([^.#]+))#(.*)""".toRegex()
                    val match = regexp.matchEntire(enforcement)
                    val (qualifiedClassName, className, methodName) =
                        if (match == null) {
                            reporter.report(
                                Issues.INVALID_FEATURE_ENFORCEMENT,
                                item,
                                "Invalid 'enforcement' value '$enforcement', must be of the form <qualified-class>#<method-name>, using default"
                            )
                            Triple(
                                "android.content.pm.PackageManager",
                                "PackageManager",
                                "hasSystemFeature"
                            )
                        } else {
                            val (qualifiedClassName, className, methodName) = match.destructured
                            Triple(qualifiedClassName, className, methodName)
                        }

                    val enforcementLink =
                        linkToMethod(
                            item,
                            qualifiedClassName = qualifiedClassName,
                            methodName = methodName,
                            parameters = "java.lang.String",
                            label = "$className.$methodName(String)",
                        ) {
                            return
                        }

                    val doc =
                        "Requires the $featureField feature which can be detected using $enforcementLink."
                    appendDocumentation(doc, item, false)
                }

                /**
                 * Handle `RequiresApi` annotations which can only be applied to classes, methods,
                 * constructors, fields and/or properties, i.e. not parameters.
                 */
                private fun handleRequiresApi(annotation: AnnotationItem, item: SelectableItem) {
                    val possibleFullVersionCode = run {
                        val api = annotation.findAttribute("api")?.value?.asInt()
                        if (api == null || api == 1) {
                            annotation.findAttribute("value")?.value?.asInt() ?: return
                        } else {
                            api
                        }
                    }

                    val apiVersion = decodePossibleFullVersionCode(possibleFullVersionCode)
                    addApiVersionDocumentation(apiVersion, item)
                }

                private fun handleRestrictedForEnvironment(
                    annotationItem: AnnotationItem,
                    item: Item
                ) {
                    val proxy = annotationItem.bindTo<RestrictedForEnvironmentProxy>(item) ?: return
                    val environmentsValue = proxy.environments.firstOrNull()
                    val fromValue = proxy.from

                    if (environmentsValue == null) {
                        reporter.report(
                            Issues.MISSING_ENVIRONMENTS_VALUE,
                            item,
                            "Missing 'environments' value for @RestrictedForEnvironment annotation"
                        )
                        return
                    }

                    appendDocumentation(
                        "Restricted for $environmentsValue environment in API level $fromValue.\n",
                        item,
                        false
                    )
                }

                private fun handleColumn(annotation: AnnotationItem, item: Item) {
                    val value = annotation.findAttribute("value")?.value ?: return
                    val readOnly = annotation.findAttribute("readOnly")?.value?.asBoolean() == true
                    val sb = StringBuilder(100)
                    val field = (value as? FieldReferenceValue)?.resolve()
                    sb.append("This constant represents a column name that can be used with a ")
                    sb.append("{@link android.content.ContentProvider}")
                    sb.append(" through a ")
                    sb.append("{@link android.content.ContentValues}")
                    sb.append(" or ")
                    sb.append("{@link android.database.Cursor}")
                    sb.append(" object. The values stored in this column are ")
                    sb.append("")
                    if (field == null) {
                        reporter.report(
                            Issues.MISSING_COLUMN,
                            item,
                            "Cannot find feature field for ${value.toValueString()} required by $item (may be hidden or removed)"
                        )
                        sb.append("{@link ${value.toValueString()}}")
                    } else {
                        val displayText = "${field.containingClass().simpleName()}#${field.name()}"
                        sb.append(
                            linkToField(item, field, label = displayText) {
                                reporter.report(
                                    Issues.MISSING_COLUMN,
                                    item,
                                    "Feature field ${value.toValueString()} required by $item is hidden or removed"
                                )
                                displayText
                            }
                        )
                        sb.append(" ")
                    }

                    if (readOnly) {
                        sb.append(", and are read-only and cannot be mutated")
                    }
                    sb.append(".")
                    appendDocumentation(sb.toString(), item, false)
                }
            }
        )
    }

    /**
     * Appends the given documentation to the given [item]'s description.
     *
     * If [Item] is a [ParameterItem] then it appends it to the corresponding `@param` tag in the
     * containing [CallableItem]'s documentation.
     *
     * If the [returnValue] flag is `false`, the documentation is added to the description text of
     * the method, otherwise, it is added to the `@return` tag. This lets for example a threading
     * annotation requirement be listed as part of a method description's text, and a range
     * annotation be listed as part of the return value description.
     */
    private fun appendDocumentation(doc: String, item: Item, returnValue: Boolean) {
        val descriptionOwner =
            when (item) {
                is ParameterItem -> item.descriptionOwner
                is MethodItem ->
                    // Document as part of return annotation, not member doc
                    if (returnValue) {
                        val documentation = item.requiredDocumentation
                        documentation.blockTagDescriptionOwner("return")
                    } else item.descriptionOwner
                else -> item.descriptionOwner
            }

        descriptionOwner.append(doc)
    }

    /**
     * Gets documentation from the [annotation]'s block tag [tag] (if any) and appends it to
     * [descriptionOwner].
     */
    private fun addDoc(
        annotation: AnnotationItem,
        tag: String,
        descriptionOwner: DocContentOwner,
    ) {
        // Resolve the annotation class, returning immediately if it could not be found.
        val cls = annotation.resolve() ?: return

        // Documentation of the annotation class that is to be copied into the item where the
        // annotation is used.
        val annotationDocumentation = cls.documentation ?: return

        // Get the text for the supplied tag as that is what needs to be copied into the use site.
        // If there is no such text then return immediately.
        val tagDescription = annotationDocumentation.blockTagDescription(tag) ?: return

        descriptionOwner.append(tagDescription)
    }

    /**
     * Load API history from [apiVersionsFile] and apply `@apiSince`, `@deprecatedSince`, and
     * `@sdkExtSince` documentation tags to items in [codebase].
     */
    fun applyApiVersions(apiVersionsFile: File) {
        val loader =
            executionEnvironment.testEnvironment?.apiHistoryProviderLoader
                ?: MetalavaApiHistoryProvider.loader
        val provider = loader.load(apiVersionsFile)

        val packageToVersion = HashMap<PackageItem, ApiVersion>(300)
        codebase.accept(
            object :
                ApiSurfaceVisitor(
                    // Only SelectableItems have documentation associated with them.
                    visitParameterItems = false,
                    filterEmit = apiFilters.emit,
                ) {

                override fun visitCallable(callable: CallableItem) {
                    // Do not add API information to implicit constructor. It is not clear exactly
                    // why this is needed but without it some existing tests break.
                    // TODO(b/302290849): Investigate this further.
                    if (callable is ConstructorItem && callable.isImplicitConstructor()) {
                        return
                    }
                    applyItemApiHistory(provider.getApiHistory(callable), callable)
                }

                override fun visitClass(cls: ClassItem) {
                    val history = provider.getApiHistory(cls) ?: return
                    applyItemApiHistory(history, cls)

                    history.since?.let { since ->
                        // Compute since version for the package: it's the min of all the classes in
                        // the package
                        val pkg = cls.containingPackage()
                        packageToVersion[pkg] =
                            packageToVersion[pkg]?.let { existing -> minOf(existing, since) }
                                ?: since
                    }
                }

                override fun visitField(field: FieldItem) {
                    applyItemApiHistory(provider.getApiHistory(field), field)
                }
            }
        )

        for ((pkg, version) in packageToVersion.entries) {
            addApiVersionDocumentation(version, pkg)
        }
    }

    /**
     * Apply the API version, SDK extension version, and deprecation version from [history] (if
     * non-null) to [item]'s documentation.
     */
    private fun applyItemApiHistory(history: ItemApiHistory?, item: SelectableItem) {
        if (history == null) return
        addApiVersionDocumentation(history.since, item)
        history.sdkExtSince?.let { addApiExtensionsDocumentation(it, item) }
        addDeprecatedDocumentation(history.deprecatedIn, item)
    }

    /**
     * Add [blockTagType] with [content] to the [item]'s [SelectableItem.documentation].
     *
     * If there is an existing [blockTagType] then an [Issues.FORBIDDEN_TAG] error will be reported,
     * and it will be removed. Irrespective of that a new [blockTagType] will be added with some
     * simple textual content.
     */
    private fun addUniqueVersionBlockTag(
        item: SelectableItem,
        blockTagType: String,
        content: String,
    ) {
        val documentation = item.requiredDocumentation

        // Report an issue if [blockTagType] is present in the sources.
        if (documentation.hasBlockTagOfType(blockTagType)) {
            reporter.report(
                Issues.FORBIDDEN_TAG,
                item,
                "Documentation should not specify @$blockTagType " +
                    "manually; it's computed and injected at build time by $PROGRAM_NAME"
            )
        }

        // Always set @[blockTagType], overriding any existing value.
        documentation.addUniqueBlockTagSectionWithSimpleText(blockTagType, content)
    }

    /**
     * Add API version documentation to the [item].
     *
     * This only applies to classes and class members, i.e. not parameters.
     */
    private fun addApiVersionDocumentation(apiVersion: ApiVersion?, item: SelectableItem) {
        if (apiVersion != null) {
            // Always set @apiSince, overriding any existing value.
            val apiVersionLabel = apiVersionLabelProvider(apiVersion)
            addUniqueVersionBlockTag(item, "apiSince", apiVersionLabel)
        }
    }

    /**
     * Add API extension documentation to the [item].
     *
     * This only applies to classes and class members, i.e. not parameters.
     *
     * @param sdkExtSince the first non Android SDK entry in the `sdks` attribute associated with
     *   [item].
     */
    private fun addApiExtensionsDocumentation(sdkExtSince: SdkAndVersion, item: SelectableItem) {
        // Always set @sdkExtSince, overriding any existing value.
        val sdkExtVersion = "${sdkExtSince.name} ${sdkExtSince.version}"
        addUniqueVersionBlockTag(item, "sdkExtSince", sdkExtVersion)
    }

    /**
     * Add deprecated documentation to the [item].
     *
     * This only applies to classes and class members, i.e. not parameters.
     */
    private fun addDeprecatedDocumentation(version: ApiVersion?, item: SelectableItem) {
        if (version != null) {
            // Always set @deprecatedSince, overriding any existing value.
            val apiVersionLabel = apiVersionLabelProvider(version)
            addUniqueVersionBlockTag(item, "deprecatedSince", apiVersionLabel)
        }
    }

    companion object {
        /**
         * Major value used in android.os.Build.VERSION_CODES_FULL to encode a major/minor version
         * into a single int. That encoding is used in `@RequiresApi(api=....)`.
         */
        const val SDK_INT_MULTIPLIER = 100000

        /**
         * Decode a version number that could either be a major version on its own, or a full
         * version code that encodes a major and minor number.
         */
        private fun decodePossibleFullVersionCode(possibleFullVersionCode: Int) =
            if (possibleFullVersionCode > SDK_INT_MULTIPLIER) {
                val major = possibleFullVersionCode / SDK_INT_MULTIPLIER
                val minor = possibleFullVersionCode % SDK_INT_MULTIPLIER
                ApiVersion.fromMajorMinor(major, minor)
            } else {
                ApiVersion.fromLevel(possibleFullVersionCode)
            }
    }
}

/**
 * Return a `{@link ...}` to [field] if it is a valid documentation reference from [from], or the
 * result of calling [alternate] otherwise.
 *
 * @param from the [Item] from whose documentation [field] is being referenced.
 * @param field the [FieldItem] to link to.
 * @param label optional label to include in the `{@link ...}` tag.
 * @param alternate lambda invoked to report an issue and/or return alternate content if [field]
 *   cannot be referenced.
 */
internal inline fun linkToField(
    from: Item,
    field: FieldItem,
    label: String? = null,
    alternate: () -> String,
): String {
    val referenceStrategy = from.codebase.apiSurfaces.referenceStrategy
    return if (referenceStrategy.isValidReference(from, field, ReferenceKind.DOCUMENTATION)) {
        val labelSuffix = if (label == null) "" else " $label"
        "{@link ${field.containingClass().qualifiedName()}#${field.name()}$labelSuffix}"
    } else {
        alternate()
    }
}

/**
 * Return a `{@link ...}` to the method [methodName] with [parameters] on [qualifiedClassName] if it
 * can be resolved and is a valid documentation reference from [from], or the result of calling
 * [alternate] otherwise.
 *
 * @param from the [Item] from whose documentation the method is being referenced.
 * @param qualifiedClassName the qualified name of the [ClassItem] containing the method.
 * @param methodName the name of the [MethodItem] to link to.
 * @param parameters comma-separated erased parameter types for [ClassItem.findBytecodeMethod].
 * @param label optional label to include in the `{@link ...}` tag.
 * @param alternate lambda invoked to report an issue and/or return alternate content if the method
 *   cannot be referenced.
 */
internal inline fun linkToMethod(
    from: Item,
    qualifiedClassName: String,
    methodName: String,
    parameters: String = "",
    label: String? = null,
    alternate: () -> String,
): String {
    val codebase = from.codebase
    val cls = codebase.resolveClass(qualifiedClassName)
    val method = cls?.findBytecodeMethod(methodName, parameters)
    val referenceStrategy = codebase.apiSurfaces.referenceStrategy
    return if (
        method != null &&
            referenceStrategy.isValidReference(from, method, ReferenceKind.DOCUMENTATION)
    ) {
        val paramSuffix = if (parameters.isEmpty()) "" else "($parameters)"
        val labelSuffix = if (label == null) "" else " $label"
        "{@link ${cls.qualifiedName()}#${method.name()}$paramSuffix$labelSuffix}"
    } else {
        alternate()
    }
}
