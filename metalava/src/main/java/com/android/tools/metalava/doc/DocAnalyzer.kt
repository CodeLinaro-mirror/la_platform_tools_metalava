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

import com.android.tools.lint.LintCliClient
import com.android.tools.lint.checks.ApiLookup
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.editDistance
import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.SdkExtension
import com.android.tools.metalava.apilevels.ApiToExtensionsMap.Companion.ANDROID_PLATFORM_SDK_ID
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.getAttributeValue
import com.android.tools.metalava.model.getCallableParameterDescriptorUsingDots
import com.android.tools.metalava.model.psi.containsLinkTags
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

private const val DEFAULT_ENFORCEMENT = "android.content.pm.PackageManager#hasSystemFeature"

private const val CARRIER_PRIVILEGES_MARKER = "carrier privileges"

/** Lambda that when given an [ApiVersion] will return a string label for it. */
typealias ApiVersionLabelProvider = (ApiVersion) -> String

/**
 * Lambda that when given an [ApiVersion] will return `true` if it can be referenced from within the
 * documentation and `false` if it cannot.
 */
typealias ApiVersionFilter = (ApiVersion) -> Boolean

/**
 * Walk over the API and apply tweaks to the documentation, such as
 * - Looking for annotations and converting them to auxiliary tags that will be processed by the
 *   documentation tools later.
 * - Reading lint's API database and inserting metadata into the documentation like api versions and
 *   deprecation versions.
 * - Transferring docs from hidden super methods.
 * - Performing tweaks for common documentation mistakes, such as ending the first sentence with ",
 *   e.g. " where javadoc will sadly see the ". " and think "aha, that's the end of the sentence!"
 *   (It works around this by replacing the space with &nbsp;.)
 */
class DocAnalyzer(
    private val executionEnvironment: ExecutionEnvironment,
    /** The codebase to analyze */
    private val codebase: Codebase,
    private val reporter: Reporter,

    /** Provides a string label for each [ApiVersion]. */
    private val apiVersionLabelProvider: ApiVersionLabelProvider,

    /** Filter that determines whether an [ApiVersion] should be mentioned in the documentation. */
    private val apiVersionFilter: ApiVersionFilter,

    /** Selects [Item]s whose documentation will be analyzed and/or enhanced. */
    private val apiPredicateConfig: ApiPredicate.Config,
) {
    /** Computes the visible part of the API from all the available code in the codebase */
    fun enhance() {
        // Apply options for packages that should be hidden
        documentsFromAnnotations()

        tweakGrammar()

        // TODO:
        // insertMissingDocFromHiddenSuperclasses()
    }

    val mentionsNull: Pattern = Pattern.compile("\\bnull\\b")

    private fun documentsFromAnnotations() {
        // Note: Doclava1 inserts its own javadoc parameters into the documentation,
        // which is then later processed by javadoc to insert actual descriptions.
        // This indirection makes the actual descriptions of the annotations more
        // configurable from a separate file -- but since this tool isn't hooked
        // into javadoc anymore (and is going to be used by for example Dokka too)
        // instead metalava will generate the descriptions directly in-line into the
        // docs.
        //
        // This does mean that you have to update the metalava source code to update
        // the docs -- but on the other hand all the other docs in the documentation
        // set also requires updating framework source code, so this doesn't seem
        // like an unreasonable burden.

        codebase.accept(
            object : ApiVisitor(apiPredicateConfig = apiPredicateConfig) {
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

                /** Fallback if field can't be resolved or if an inlined string value is used */
                private fun findPermissionField(codebase: Codebase, value: Any): FieldItem? {
                    val perm = value.toString()
                    val permClass = codebase.findClass("android.Manifest.permission")
                    permClass
                        ?.fields()
                        ?.filter { it.initialValue(requireConstant = false)?.toString() == perm }
                        ?.forEach {
                            return it
                        }
                    return null
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
                        "androidx.annotation.RequiresPermission" ->
                            handleRequiresPermission(annotation, item)
                        "androidx.annotation.IntRange",
                        "androidx.annotation.FloatRange" -> handleRange(annotation, item)
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
                        "kotlin.Deprecated" -> handleKotlinDeprecation(annotation, item)
                    }

                    visitedClasses.add(name)
                    // Thread annotations are ignored here because they're handled as a group
                    // afterward.

                    // TODO: Resource type annotations

                    // Handle nested annotations
                    annotation.resolve()?.modifiers?.annotations()?.forEach { nested ->
                        if (depth == 20) { // Temp debugging
                            throw StackOverflowError(
                                "Unbounded recursion, processing annotation ${annotation.toSource()} " +
                                    "in $item at ${annotation.fileLocation} "
                            )
                        } else if (nested.qualifiedName !in visitedClasses) {
                            handleAnnotation(nested, item, depth + 1, visitedClasses)
                        }
                    }
                }

                private fun handleKotlinDeprecation(annotation: AnnotationItem, item: Item) {
                    val text =
                        (annotation.findAttribute("message")
                                ?: annotation.findAttribute(ANNOTATION_ATTR_VALUE))
                            ?.value
                            ?.value()
                            ?.toString()
                            ?: return
                    if (text.isBlank() || item.documentation.contains(text)) {
                        return
                    }

                    item.appendDocumentation(text, "@deprecated")
                }

                private fun handleInliningDocs(annotation: AnnotationItem, item: Item) {
                    if (annotation.isNullable() || annotation.isNonNull()) {
                        // Some docs already specifically talk about null policy; in that case,
                        // don't include the docs (since it may conflict with more specific
                        // conditions
                        // outlined in the docs).
                        val documentation = item.documentation
                        val doc =
                            when (item) {
                                is ParameterItem -> {
                                    item
                                        .containingCallable()
                                        .documentation
                                        .findTagDocumentation("param", item.name())
                                        ?: ""
                                }
                                is CallableItem -> {
                                    // Don't inspect param docs (and other tags) for this purpose.
                                    documentation.findMainDocumentation() +
                                        (documentation.findTagDocumentation("return") ?: "")
                                }
                                else -> {
                                    documentation
                                }
                            }
                        if (doc.contains("null") && mentionsNull.matcher(doc).find()) {
                            return
                        }
                    }

                    when (item) {
                        is FieldItem -> {
                            addDoc(annotation, "memberDoc", item)
                        }
                        is CallableItem -> {
                            addDoc(annotation, "memberDoc", item)
                            addDoc(annotation, "returnDoc", item)
                        }
                        is ParameterItem -> {
                            addDoc(annotation, "paramDoc", item)
                        }
                        is ClassItem -> {
                            addDoc(annotation, "classDoc", item)
                        }
                    }
                }

                private fun handleRequiresPermission(annotation: AnnotationItem, item: Item) {
                    if (item !is MemberItem) {
                        return
                    }
                    var values: List<AnnotationAttributeValue>? = null
                    var any = false
                    var conditional = false
                    for (attribute in annotation.attributes) {
                        when (attribute.name) {
                            "value",
                            "allOf" -> {
                                values = attribute.leafValues()
                            }
                            "anyOf" -> {
                                any = true
                                values = attribute.leafValues()
                            }
                            "conditional" -> {
                                conditional = attribute.value.value() == true
                            }
                        }
                    }

                    if (!values.isNullOrEmpty() && !conditional) {
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

                            val resolved = value.resolve()
                            val field =
                                if (resolved is FieldItem) resolved
                                else {
                                    val v: Any = value.value() ?: value.toSource()
                                    if (v == CARRIER_PRIVILEGES_MARKER) {
                                        // TODO: Warn if using allOf with carrier
                                        sb.append(
                                            "{@link android.telephony.TelephonyManager#hasCarrierPrivileges carrier privileges}"
                                        )
                                        continue
                                    }
                                    findPermissionField(codebase, v)
                                }
                            if (field == null) {
                                val v = value.value()?.toString() ?: value.toSource()
                                if (editDistance(CARRIER_PRIVILEGES_MARKER, v, 3) < 3) {
                                    reporter.report(
                                        Issues.MISSING_PERMISSION,
                                        item,
                                        "Unrecognized permission `$v`; did you mean `$CARRIER_PRIVILEGES_MARKER`?"
                                    )
                                } else {
                                    reporter.report(
                                        Issues.MISSING_PERMISSION,
                                        item,
                                        "Cannot find permission field for $value required by $item (may be hidden or removed)"
                                    )
                                }
                                sb.append(value.toSource())
                            } else {
                                if (filterReference.test(field)) {
                                    sb.append(
                                        "{@link ${field.containingClass().qualifiedName()}#${field.name()}}"
                                    )
                                } else {
                                    reporter.report(
                                        Issues.MISSING_PERMISSION,
                                        item,
                                        "Permission $value required by $item is hidden or removed"
                                    )
                                    sb.append(
                                        "${field.containingClass().qualifiedName()}.${field.name()}"
                                    )
                                }
                            }
                        }

                        appendDocumentation(sb.toString(), item, false)
                    }
                }

                private fun handleRange(annotation: AnnotationItem, item: Item) {
                    val from: String? = annotation.findAttribute("from")?.value?.toSource()
                    val to: String? = annotation.findAttribute("to")?.value?.toSource()
                    // TODO: inclusive/exclusive attributes on FloatRange!
                    if (from != null || to != null) {
                        val args = HashMap<String, String>()
                        if (from != null) args["from"] = from
                        if (from != null) args["from"] = from
                        if (to != null) args["to"] = to
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
                    val values = annotation.findAttribute("value")?.leafValues() ?: return
                    val flag = annotation.findAttribute("flag")?.value?.toSource() == "true"

                    // Look at macros_override.cs for the usage of these
                    // tags. In particular, search for def:dump_int_def

                    val sb = StringBuilder(100)
                    sb.append("Value is ")
                    if (flag) {
                        sb.append("either <code>0</code> or ")
                        if (values.size > 1) {
                            sb.append("a combination of ")
                        }
                    }

                    values.forEachIndexed { index, value ->
                        sb.append(
                            when (index) {
                                0 -> {
                                    ""
                                }
                                values.size - 1 -> {
                                    if (flag) {
                                        ", and "
                                    } else {
                                        ", or "
                                    }
                                }
                                else -> {
                                    ", "
                                }
                            }
                        )

                        val field = value.resolve()
                        if (field is FieldItem)
                            if (filterReference.test(field)) {
                                sb.append(
                                    "{@link ${field.containingClass().qualifiedName()}#${field.name()}}"
                                )
                            } else {
                                // Typedef annotation references field which isn't part of the API:
                                // don't
                                // try to link to it.
                                reporter.report(
                                    Issues.HIDDEN_TYPEDEF_CONSTANT,
                                    item,
                                    "Typedef references constant which isn't part of the API, skipping in documentation: " +
                                        "${field.containingClass().qualifiedName()}#${field.name()}"
                                )
                                sb.append(
                                    field.containingClass().qualifiedName() + "." + field.name()
                                )
                            }
                        else {
                            sb.append(value.toSource())
                        }
                    }
                    appendDocumentation(sb.toString(), item, true)
                }

                private fun handleRequiresFeature(annotation: AnnotationItem, item: Item) {
                    val value =
                        annotation.findAttribute("value")?.leafValues()?.firstOrNull() ?: return
                    val resolved = value.resolve()
                    val field = resolved as? FieldItem
                    val featureField =
                        if (field == null) {
                            reporter.report(
                                Issues.MISSING_PERMISSION,
                                item,
                                "Cannot find feature field for $value required by $item (may be hidden or removed)"
                            )
                            "{@link ${value.toSource()}}"
                        } else {
                            if (filterReference.test(field)) {
                                "{@link ${field.containingClass().qualifiedName()}#${field.name()} ${field.containingClass().simpleName()}#${field.name()}}"
                            } else {
                                reporter.report(
                                    Issues.MISSING_PERMISSION,
                                    item,
                                    "Feature field $value required by $item is hidden or removed"
                                )
                                "${field.containingClass().simpleName()}#${field.name()}"
                            }
                        }

                    val enforcement =
                        annotation.getAttributeValue("enforcement") ?: DEFAULT_ENFORCEMENT

                    // Compute the link uri and text from the enforcement setting.
                    val regexp = """(?:.*\.)?([^.#]+)#(.*)""".toRegex()
                    val match = regexp.matchEntire(enforcement)
                    val (className, methodName, methodRef) =
                        if (match == null) {
                            reporter.report(
                                Issues.INVALID_FEATURE_ENFORCEMENT,
                                item,
                                "Invalid 'enforcement' value '$enforcement', must be of the form <qualified-class>#<method-name>, using default"
                            )
                            Triple("PackageManager", "hasSystemFeature", DEFAULT_ENFORCEMENT)
                        } else {
                            val (className, methodName) = match.destructured
                            Triple(className, methodName, enforcement)
                        }

                    val linkUri = "$methodRef(String)"
                    val linkText = "$className.$methodName(String)"

                    val doc =
                        "Requires the $featureField feature which can be detected using {@link $linkUri $linkText}."
                    appendDocumentation(doc, item, false)
                }

                /**
                 * Handle `RequiresApi` annotations which can only be applied to classes, methods,
                 * constructors, fields and/or properties, i.e. not parameters.
                 */
                private fun handleRequiresApi(annotation: AnnotationItem, item: SelectableItem) {
                    val level = run {
                        val api =
                            annotation.findAttribute("api")?.leafValues()?.firstOrNull()?.value()
                        if (api == null || api == 1) {
                            annotation.findAttribute("value")?.leafValues()?.firstOrNull()?.value()
                                ?: return
                        } else {
                            api
                        }
                    }

                    if (level is Int) {
                        addApiVersionDocumentation(ApiVersion.fromLevel(level), item)
                    }
                }

                private fun handleColumn(annotation: AnnotationItem, item: Item) {
                    val value =
                        annotation.findAttribute("value")?.leafValues()?.firstOrNull() ?: return
                    val readOnly =
                        annotation
                            .findAttribute("readOnly")
                            ?.leafValues()
                            ?.firstOrNull()
                            ?.value() == true
                    val sb = StringBuilder(100)
                    val resolved = value.resolve()
                    val field = resolved as? FieldItem
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
                            "Cannot find feature field for $value required by $item (may be hidden or removed)"
                        )
                        sb.append("{@link ${value.toSource()}}")
                    } else {
                        if (filterReference.test(field)) {
                            sb.append(
                                "{@link ${field.containingClass().qualifiedName()}#${field.name()} ${field.containingClass().simpleName()}#${field.name()}} "
                            )
                        } else {
                            reporter.report(
                                Issues.MISSING_COLUMN,
                                item,
                                "Feature field $value required by $item is hidden or removed"
                            )
                            sb.append("${field.containingClass().simpleName()}#${field.name()} ")
                        }
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
     * Appends the given documentation to the given item. If it's documentation on a parameter, it
     * is redirected to the surrounding method's documentation.
     *
     * If the [returnValue] flag is true, the documentation is added to the description text of the
     * method, otherwise, it is added to the return tag. This lets for example a threading
     * annotation requirement be listed as part of a method description's text, and a range
     * annotation be listed as part of the return value description.
     */
    private fun appendDocumentation(doc: String?, item: Item, returnValue: Boolean) {
        doc ?: return

        when (item) {
            is ParameterItem -> item.containingCallable().appendDocumentation(doc, item.name())
            is MethodItem ->
                // Document as part of return annotation, not member doc
                item.appendDocumentation(doc, if (returnValue) "@return" else null)
            else -> item.appendDocumentation(doc)
        }
    }

    private fun addDoc(annotation: AnnotationItem, tag: String, item: Item) {
        // Resolve the annotation class, returning immediately if it could not be found.
        val cls = annotation.resolve() ?: return

        // Documentation of the annotation class that is to be copied into the item where the
        // annotation is used.
        val annotationDocumentation = cls.documentation

        // Get the text for the supplied tag as that is what needs to be copied into the use site.
        // If there is no such text then return immediately.
        val taggedText = annotationDocumentation.findTagDocumentation(tag) ?: return

        assert(taggedText.startsWith("@$tag")) { taggedText }
        val section =
            when {
                taggedText.startsWith("@returnDoc") -> "@return"
                taggedText.startsWith("@paramDoc") -> "@param"
                taggedText.startsWith("@memberDoc") -> null
                else -> null
            }

        val insert = stripLeadingAsterisks(stripMetaTags(taggedText.substring(tag.length + 2)))
        val qualified =
            if (containsLinkTags(insert)) {
                val original = "/** $insert */"
                val qualified = annotationDocumentation.fullyQualifiedDocumentation(original)
                if (original != qualified) {
                    qualified.substring(if (qualified[3] == ' ') 4 else 3, qualified.length - 2)
                } else {
                    insert
                }
            } else {
                insert
            }

        item.appendDocumentation(qualified, section) // 2: @ and space after tag
    }

    private fun stripLeadingAsterisks(s: String): String {
        if (s.contains("*")) {
            val sb = StringBuilder(s.length)
            var strip = true
            for (c in s) {
                if (strip) {
                    if (c.isWhitespace() || c == '*') {
                        continue
                    } else {
                        strip = false
                    }
                } else {
                    if (c == '\n') {
                        strip = true
                    }
                }
                sb.append(c)
            }
            return sb.toString()
        }

        return s
    }

    private fun stripMetaTags(string: String): String {
        // Get rid of @hide and @remove tags etc. that are part of documentation snippets
        // we pull in, such that we don't accidentally start applying this to the
        // item that is pulling in the documentation.
        if (string.contains("@hide") || string.contains("@remove")) {
            return string.replace("@hide", "").replace("@remove", "")
        }
        return string
    }

    private fun tweakGrammar() {
        codebase.accept(
            object :
                ApiVisitor(
                    // Do not visit [ParameterItem]s as they do not have their own summary line that
                    // could become truncated.
                    visitParameterItems = false,
                    apiPredicateConfig = apiPredicateConfig,
                ) {
                /**
                 * Work around an issue with JavaDoc summary truncation.
                 *
                 * This is not called for [ParameterItem]s as they do not have their own summary
                 * line that could become truncated.
                 */
                override fun visitSelectableItem(item: SelectableItem) {
                    item.documentation.workAroundJavaDocSummaryTruncationIssue()
                }
            }
        )
    }

    fun applyApiVersions(apiVersionsFile: File) {
        val apiLookup =
            getApiLookup(
                xmlFile = apiVersionsFile,
                underTest = executionEnvironment.isUnderTest(),
            )
        val elementToSdkExtSinceMap = createSymbolToSdkExtSinceMap(apiVersionsFile)

        val packageToVersion = HashMap<PackageItem, ApiVersion>(300)
        codebase.accept(
            object :
                ApiVisitor(
                    // Only SelectableItems have documentation associated with them.
                    visitParameterItems = false,
                    apiPredicateConfig = apiPredicateConfig,
                ) {

                override fun visitCallable(callable: CallableItem) {
                    // Do not add API information to implicit constructor. It is not clear exactly
                    // why this is needed but without it some existing tests break.
                    // TODO(b/302290849): Investigate this further.
                    if (callable is ConstructorItem && callable.isImplicitConstructor()) {
                        return
                    }
                    addApiVersionDocumentation(apiLookup.getCallableVersion(callable), callable)
                    val methodName = callable.name()
                    val key = "${callable.containingClass().qualifiedName()}#$methodName"
                    elementToSdkExtSinceMap[key]?.let {
                        addApiExtensionsDocumentation(it, callable)
                    }
                    addDeprecatedDocumentation(
                        apiLookup.getCallableDeprecatedIn(callable),
                        callable
                    )
                }

                override fun visitClass(cls: ClassItem) {
                    val qualifiedName = cls.qualifiedName()
                    val since = apiLookup.getClassVersion(cls)
                    if (since != null) {
                        addApiVersionDocumentation(since, cls)

                        // Compute since version for the package: it's the min of all the classes in
                        // the package
                        val pkg = cls.containingPackage()
                        packageToVersion[pkg] =
                            packageToVersion[pkg]?.let { existing -> minOf(existing, since) }
                                ?: since
                    }
                    elementToSdkExtSinceMap[qualifiedName]?.let {
                        addApiExtensionsDocumentation(it, cls)
                    }
                    addDeprecatedDocumentation(apiLookup.getClassDeprecatedIn(cls), cls)
                }

                override fun visitField(field: FieldItem) {
                    addApiVersionDocumentation(apiLookup.getFieldVersion(field), field)
                    elementToSdkExtSinceMap[
                            "${field.containingClass().qualifiedName()}#${field.name()}"]
                        ?.let { addApiExtensionsDocumentation(it, field) }
                    addDeprecatedDocumentation(apiLookup.getFieldDeprecatedIn(field), field)
                }
            }
        )

        for ((pkg, version) in packageToVersion.entries) {
            addApiVersionDocumentation(version, pkg)
        }
    }

    /**
     * Add API version documentation to the [item].
     *
     * This only applies to classes and class members, i.e. not parameters.
     */
    private fun addApiVersionDocumentation(apiVersion: ApiVersion?, item: SelectableItem) {
        if (apiVersion != null) {
            if (item.originallyHidden) {
                // @SystemApi, @TestApi etc -- don't apply API versions here since we don't have
                // accurate historical data
                return
            }

            // Check to see whether an API version should not be included in the documentation.
            if (!apiVersionFilter(apiVersion)) {
                return
            }

            val apiVersionLabel = apiVersionLabelProvider(apiVersion)

            // Also add @since tag, unless already manually entered.
            // TODO: Override it everywhere in case the existing doc is wrong (we know
            // better), and at least for OpenJDK sources we *should* since the since tags
            // are talking about language levels rather than API versions!
            if (!item.documentation.contains("@apiSince")) {
                item.appendDocumentation(apiVersionLabel, "@apiSince")
            } else {
                reporter.report(
                    Issues.FORBIDDEN_TAG,
                    item,
                    "Documentation should not specify @apiSince " +
                        "manually; it's computed and injected at build time by $PROGRAM_NAME"
                )
            }
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
        if (item.documentation.contains("@sdkExtSince")) {
            reporter.report(
                Issues.FORBIDDEN_TAG,
                item,
                "Documentation should not specify @sdkExtSince " +
                    "manually; it's computed and injected at build time by $PROGRAM_NAME"
            )
        }

        item.appendDocumentation("${sdkExtSince.name} ${sdkExtSince.version}", "@sdkExtSince")
    }

    /**
     * Add deprecated documentation to the [item].
     *
     * This only applies to classes and class members, i.e. not parameters.
     */
    private fun addDeprecatedDocumentation(version: ApiVersion?, item: SelectableItem) {
        if (version != null) {
            if (item.originallyHidden) {
                // @SystemApi, @TestApi etc -- don't apply API versions here since we don't have
                // accurate historical data
                return
            }
            val apiVersionLabel = apiVersionLabelProvider(version)

            if (!item.documentation.contains("@deprecatedSince")) {
                item.appendDocumentation(apiVersionLabel, "@deprecatedSince")
            } else {
                reporter.report(
                    Issues.FORBIDDEN_TAG,
                    item,
                    "Documentation should not specify @deprecatedSince " +
                        "manually; it's computed and injected at build time by $PROGRAM_NAME"
                )
            }
        }
    }
}

/** A constraint that will only match for Android Platform SDKs. */
val androidSdkConstraint = ApiConstraint.get(1)

/**
 * Get the min [ApiVersion], i.e. the lowest version of the Android Platform SDK.
 *
 * TODO(b/282932318): Replace with call to ApiConstraint.min() when bug is fixed.
 */
fun ApiConstraint.minApiVersion(): ApiVersion? {
    return getConstraints()
        .filter { it != ApiConstraint.UNKNOWN }
        // Remove any constraints that are not for the Android Platform SDK.
        .filter { it.isAtLeast(androidSdkConstraint) }
        // Get the minimum of all the lowest ApiVersions, or null if there are no ApiVersions in the
        // constraints.
        .minOfOrNull {
            val major = it.fromInclusive()
            val minor = it.fromInclusiveMinor()
            ApiVersion.fromMajorMinor(
                major,
                if (minor == 0) null else minor,
            )
        }
}

fun ApiLookup.getClassVersion(cls: ClassItem): ApiVersion? {
    val owner = cls.qualifiedName()
    return getClassVersions(owner).minApiVersion()
}

fun ApiLookup.getCallableVersion(method: CallableItem): ApiVersion? {
    val containingClass = method.containingClass()
    val owner = containingClass.qualifiedName()
    val desc = method.getCallableParameterDescriptorUsingDots()
    // Metalava uses the class name as the name of the constructor but the ApiLookup uses <init>.
    val name = if (method.isConstructor()) "<init>" else method.name()
    return getMethodVersions(owner, name, desc).minApiVersion()
}

fun ApiLookup.getFieldVersion(field: FieldItem): ApiVersion? {
    val containingClass = field.containingClass()
    val owner = containingClass.qualifiedName()
    return getFieldVersions(owner, field.name()).minApiVersion()
}

fun ApiLookup.getClassDeprecatedIn(cls: ClassItem): ApiVersion? {
    val owner = cls.qualifiedName()
    return getClassDeprecatedInVersions(owner).minApiVersion()
}

fun ApiLookup.getCallableDeprecatedIn(callable: CallableItem): ApiVersion? {
    val containingClass = callable.containingClass()
    val owner = containingClass.qualifiedName()
    val desc = callable.getCallableParameterDescriptorUsingDots() ?: return null
    return getMethodDeprecatedInVersions(owner, callable.name(), desc).minApiVersion()
}

fun ApiLookup.getFieldDeprecatedIn(field: FieldItem): ApiVersion? {
    val containingClass = field.containingClass()
    val owner = containingClass.qualifiedName()
    return getFieldDeprecatedInVersions(owner, field.name()).minApiVersion()
}

fun getApiLookup(
    xmlFile: File,
    cacheDir: File? = null,
    underTest: Boolean = true,
): ApiLookup {
    val client =
        object : LintCliClient(PROGRAM_NAME) {
            override fun getCacheDir(name: String?, create: Boolean): File? {
                if (cacheDir != null) {
                    return cacheDir
                }

                if (create && underTest) {
                    // Pick unique directory during unit tests
                    return Files.createTempDirectory(PROGRAM_NAME).toFile()
                }

                val sb = StringBuilder(PROGRAM_NAME)
                if (name != null) {
                    sb.append(File.separator)
                    sb.append(name)
                }
                val relative = sb.toString()

                val tmp = System.getenv("TMPDIR")
                if (tmp != null) {
                    // Android Build environment: Make sure we're really creating a unique
                    // temp directory each time since builds could be running in
                    // parallel here.
                    val dir = File(tmp, relative)
                    if (!dir.isDirectory) {
                        dir.mkdirs()
                    }

                    return Files.createTempDirectory(dir.toPath(), null).toFile()
                }

                val dir = File(System.getProperty("java.io.tmpdir"), relative)
                if (create && !dir.isDirectory) {
                    dir.mkdirs()
                }
                return dir
            }
        }

    val xmlPathProperty = "LINT_API_DATABASE"
    val prev = System.getProperty(xmlPathProperty)
    try {
        System.setProperty(xmlPathProperty, xmlFile.path)
        return ApiLookup.get(client, null) ?: error("ApiLookup creation failed")
    } finally {
        if (prev != null) {
            System.setProperty(xmlPathProperty, xmlFile.path)
        } else {
            System.clearProperty(xmlPathProperty)
        }
    }
}

/**
 * Generate a map of symbol -> (list of SDKs and corresponding versions the symbol first appeared)
 * in by parsing an api-versions.xml file. This will be used when injecting @sdkExtSince
 * annotations, which convey the same information, in a format documentation tools can consume.
 *
 * A symbol is either of a class, method or field.
 *
 * The symbols are Strings on the format "com.pkg.Foo#MethodOrField", with no method signature.
 */
private fun createSymbolToSdkExtSinceMap(xmlFile: File): Map<String, SdkAndVersion> {
    data class OuterClass(val name: String, val idAndVersion: IdAndVersion?)

    val sdkExtensionsById = mutableMapOf<Int, SdkExtension>()
    var lastSeenClass: OuterClass? = null
    val elementToIdAndVersionMap = mutableMapOf<String, IdAndVersion>()
    val memberTags = listOf("class", "method", "field")
    val parser = SAXParserFactory.newDefaultInstance().newSAXParser()
    parser.parse(
        xmlFile,
        object : DefaultHandler() {
            override fun startElement(
                uri: String,
                localName: String,
                qualifiedName: String,
                attributes: Attributes
            ) {
                if (qualifiedName == "sdk") {
                    val id: Int =
                        attributes.getValue("id")?.toIntOrNull()
                            ?: throw IllegalArgumentException(
                                "<sdk>: missing or non-integer id attribute"
                            )
                    val shortname: String =
                        attributes.getValue("shortname")
                            ?: throw IllegalArgumentException("<sdk>: missing shortname attribute")
                    val name: String =
                        attributes.getValue("name")
                            ?: throw IllegalArgumentException("<sdk>: missing name attribute")
                    val reference: String =
                        attributes.getValue("reference")
                            ?: throw IllegalArgumentException("<sdk>: missing reference attribute")
                    sdkExtensionsById[id] =
                        SdkExtension.fromXmlAttributes(
                            id,
                            shortname,
                            name,
                            reference,
                        )
                } else if (memberTags.contains(qualifiedName)) {
                    val name: String =
                        attributes.getValue("name")
                            ?: throw IllegalArgumentException(
                                "<$qualifiedName>: missing name attribute"
                            )
                    val sdksList = attributes.getValue("sdks")
                    val idAndVersion =
                        sdksList
                            ?.split(",")
                            // Get the first pair of sdk-id:version where sdk-id is not 0. If no
                            // such pair exists then use `null`.
                            ?.firstNotNullOfOrNull {
                                val (sdk, version) = it.split(":")
                                val id = sdk.toInt()
                                // Ignore any references to the Android Platform SDK as they are
                                // handled by ApiLookup.
                                if (id == ANDROID_PLATFORM_SDK_ID) null
                                else IdAndVersion(id, version.toInt())
                            }

                    // Populate elementToIdAndVersionMap. The keys constructed here are derived from
                    // api-versions.xml; when used elsewhere in DocAnalyzer, the keys will be
                    // derived from PsiItems. The two sources use slightly different nomenclature,
                    // so change "api-versions.xml nomenclature" to "PsiItems nomenclature" before
                    // inserting items in the map.
                    //
                    // Nomenclature differences:
                    //   - constructors are named "<init>()V" in api-versions.xml, but
                    //     "ClassName()V" in PsiItems
                    //   - nested classes are named "Outer#Inner" in api-versions.xml, but
                    //     "Outer.Inner" in PsiItems
                    when (qualifiedName) {
                        "class" -> {
                            lastSeenClass =
                                OuterClass(name.replace('/', '.').replace('$', '.'), idAndVersion)
                            if (idAndVersion != null) {
                                elementToIdAndVersionMap[lastSeenClass!!.name] = idAndVersion
                            }
                        }
                        "method",
                        "field" -> {
                            val shortName =
                                if (name.startsWith("<init>")) {
                                    // constructors in api-versions.xml are named '<init>': rename
                                    // to
                                    // name of class instead, and strip signature: '<init>()V' ->
                                    // 'Foo'
                                    lastSeenClass!!.name.substringAfterLast('.')
                                } else {
                                    // strip signature: 'foo()V' -> 'foo'
                                    name.substringBefore('(')
                                }
                            val element = "${lastSeenClass!!.name}#$shortName"
                            if (idAndVersion != null) {
                                elementToIdAndVersionMap[element] = idAndVersion
                            } else if (sdksList == null && lastSeenClass!!.idAndVersion != null) {
                                // The method/field does not have an `sdks` attribute so fall back
                                // to the idAndVersion from the containing class.
                                elementToIdAndVersionMap[element] = lastSeenClass!!.idAndVersion!!
                            }
                        }
                    }
                }
            }

            override fun endElement(uri: String, localName: String, qualifiedName: String) {
                if (qualifiedName == "class") {
                    lastSeenClass = null
                }
            }
        }
    )

    val elementToSdkExtSinceMap = mutableMapOf<String, SdkAndVersion>()
    for (entry in elementToIdAndVersionMap.entries) {
        elementToSdkExtSinceMap[entry.key] =
            entry.value.let {
                val name =
                    sdkExtensionsById[it.first]?.name
                        ?: throw IllegalArgumentException(
                            "SDK reference to unknown <sdk> with id ${it.first}"
                        )
                SdkAndVersion(name, it.second)
            }
    }
    return elementToSdkExtSinceMap
}

private typealias IdAndVersion = Pair<Int, Int>

private data class SdkAndVersion(val name: String, val version: Int)
