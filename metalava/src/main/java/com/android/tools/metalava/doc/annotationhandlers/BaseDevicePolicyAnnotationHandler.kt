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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter

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
}

/** Renders a list of table entries into an HTML table format. */
fun renderTable(tableEntries: List<Pair<String, String>>): String {
    return buildString {
        append("\n <table>\n")
        append("  <tr>\n")
        append("    <th colspan=\"2\">Policy details</th>\n")
        append("  </tr>\n")
        for ((name, value) in tableEntries) {
            append("  <tr>\n")
            append("    <td>$name</td>\n")
            if (value.contains('\n')) {
                append("    <td>\n")
                value.trimEnd('\n').split('\n').forEach { line ->
                    if (line.isNotEmpty()) {
                        append("      $line\n")
                    } else {
                        append("\n")
                    }
                }
                append("    </td>\n")
            } else {
                append("    <td>$value</td>\n")
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
