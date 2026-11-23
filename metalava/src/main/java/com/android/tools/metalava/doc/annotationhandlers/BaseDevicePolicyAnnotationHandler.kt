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

package com.android.tools.metalava.doc.annotationhandlers

import com.android.tools.metalava.doc.linkToField
import com.android.tools.metalava.model.ANDROIDX_INT_DEF
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.asAny
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter

/** A single row in a policy details documentation table. */
data class TableRow(
    /** The label shown in the left column of the row. */
    val label: String,
    /** The HTML body shown in the right column of the row. */
    val body: String,
)

/** A device policy definition capable of generating documentation rows. */
interface DevicePolicy {
    /** Generates the documentation table rows describing this policy under [context]. */
    fun generateDocRows(context: DevicePolicyContext): List<TableRow>
}

/**
 * Base class for Device Policy Annotation Handlers.
 *
 * Contains shared utilities for parsing attributes and reporting issues.
 */
abstract class BaseDevicePolicyAnnotationHandler(protected val context: DevicePolicyContext) {
    /** Processes a policy annotation and returns a documentation string. */
    abstract fun processPolicyAnnotation(
        annotation: AnnotationItem,
        item: Item,
    ): String

    /** Generates the HTML documentation table for [currentVersion]. */
    protected fun generateDocs(
        currentVersion: DevicePolicy,
        @Suppress("UNUSED_PARAMETER") item: Item,
    ): String = renderTable(currentVersion.generateDocRows(context))
}

/** Renders a list of table rows into an HTML table format. */
fun renderTable(tableEntries: List<TableRow>): String {
    return buildString {
        append("\n <table>\n")
        append("  <tr>\n")
        append("    <th colspan=\"2\">Policy details</th>\n")
        append("  </tr>\n")
        for ((label, body) in tableEntries) {
            append("  <tr>\n")
            append("    <td>$label</td>\n")
            if (body.contains('\n')) {
                append("    <td>\n")
                body.trimEnd('\n').split('\n').forEach { line ->
                    if (line.isNotEmpty()) {
                        append("      $line\n")
                    } else {
                        append("\n")
                    }
                }
                append("    </td>\n")
            } else {
                append("    <td>$body</td>\n")
            }
            append("  </tr>\n")
        }
        append(" </table>\n")
        append(
            " See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}\n"
        )
    }
}

/** Renders the "Policy value" cell content, merging validations. */
fun renderPolicyValue(type: String, policyValueValidations: List<String>): String {
    if (policyValueValidations.isEmpty()) {
        return "<code>$type</code>"
    }
    return buildString {
        append("<code>$type</code> with the following restrictions:")
        append("\n<ul>\n")
        policyValueValidations.forEach { validation -> append("  <li>$validation</li>\n") }
        append("</ul>")
    }
}

/** Report missing required fields inside the annotation. */
fun Reporter.reportOnMissingFields(fieldName: String, item: Item) {
    report(
        Issues.INVALID_DEVICE_POLICY_ANNOTATION,
        item,
        "Missing required field '$fieldName' inside $item"
    )
}

/**
 * Build a map from the enum integer values to the corresponding integer variable's code reference.
 * For example:
 * ```
 * class SomeClass {
 *   int ENUM_VALUE_1 = 1;
 *   int ENUM_VALUE_2 = 2;
 *   @IntDef({
 *     ENUM_VALUE_1,
 *     ENUM_VALUE_2,
 *   })
 *   public @interface SomeEnumValue {}
 * }
 * ```
 *
 * will be translated to:
 * ```
 * {
 *   1: "{@link SomeClass.SomeEnumValue#ENUM_VALUE_1}",
 *   2: "{@link SomeClass.SomeEnumValue#ENUM_VALUE_2}",
 * }
 * ```
 */
fun buildEnumValueToCodeReferenceMap(
    intDef: ClassItem?,
    item: Item,
    @Suppress("UNUSED_PARAMETER") filterReference: FilterPredicate?,
): Map<Int, String> {
    val reporter = item.codebase.reporter

    // Find the @IntDef annotation of the enum value class
    val intDefAnnotation =
        intDef?.modifiers?.annotations()?.find { it.qualifiedName == ANDROIDX_INT_DEF }

    val enumValueAttrs =
        intDefAnnotation?.findAttribute("value")?.value?.asFlatList() ?: emptyList()

    val enumValueToName = mutableMapOf<Int, String>()

    for (enumValueAttr in enumValueAttrs) {
        if (enumValueAttr is FieldReferenceValue) {
            val fieldName = enumValueAttr.fieldName
            val fieldItem = enumValueAttr.resolve()
            val fieldValue = fieldItem?.constantValue?.asAny() as? Int
            if (fieldValue == null) {
                reporter.report(
                    Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                    item,
                    "Failed to resolve the value of: $fieldName"
                )
                continue
            }
            enumValueToName[fieldValue] =
                linkToField(item, fieldItem) {
                    reporter.report(
                        Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                        item,
                        "Cannot locate $fieldName required by $item (may be hidden or removed)"
                    )
                    fieldName
                }
        }
    }
    return enumValueToName
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.ListResolutionMechanism`
 * annotation class.
 *
 * @see bindTo
 */
data class ListResolutionMechanismProxy(
    val custom: Boolean = false,
    val union: Boolean = false,
) {
    // TODO(b/492421367): Enrich the doc for resolution mechanism.
    fun generateDocs(item: Item): String {
        if (custom) {
            return ""
        } else if (union) {
            return "If this policy is set by multiple admins, the union of all provided values takes effect."
        } else {
            item.codebase.reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "ListResolutionMechanism must have either 'custom' or 'union' set to true."
            )
            return ""
        }
    }
}
