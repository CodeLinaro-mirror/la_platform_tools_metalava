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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.reporter.Issues

/** Handles @android.processor.devicepolicy.EnumPolicyDefinition annotation. */
class EnumPolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {

    /** Processes a policy annotation and returns a documentation string. */
    override fun processPolicyAnnotation(
        annotation: AnnotationItem,
        item: Item,
    ): String {
        val proxy = annotation.bindTo<EnumPolicyDefinitionProxy>(item)
        return proxy?.generateDocs(context) ?: ""
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.EnumPolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class EnumPolicyDefinitionProxy(
    val item: Item,
    val base: PolicyDefinitionProxy,
    val resolutionMechanism: EnumResolutionMechanismProxy,
    val intDef: ClassItem?,
    val defaultValue: Int = -1,
) {
    val reporter = item.codebase.reporter

    fun generateDocs(context: DevicePolicyContext): String {
        val enumValueToCodeReference =
            buildEnumValueToCodeReferenceMap(intDef, item, context.filterReference)
        val defaultValue = defaultValue

        val tableEntries = buildList {
            addAll(base.getTableEntries())
            val resolutionMechanismDoc = resolutionMechanism.generateDocs(enumValueToCodeReference)
            if (resolutionMechanismDoc.isNotEmpty()) {
                add(Pair("Conflict resolution mechanism", resolutionMechanismDoc))
            }
            if (enumValueToCodeReference.isNotEmpty()) {
                val valuesDoc = buildString {
                    append("<code>Integer</code>, value is one of the following:\n")
                    append("<ul>\n")
                    enumValueToCodeReference.entries.forEach { entry ->
                        if (entry.key == defaultValue) {
                            append("  <li>${entry.value} (default)</li>\n")
                        } else {
                            append("  <li>${entry.value}</li>\n")
                        }
                    }
                    append("</ul>")
                }
                add(Pair("Policy value", valuesDoc))
            } else {
                reporter.report(
                    Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                    item,
                    "No enum values found for @EnumPolicyDefinition"
                )
                add(Pair("Policy value", "<code>Integer</code>"))
            }
        }

        return buildString { append(renderTable(tableEntries)) }
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.EnumResolutionMechanism`
 * annotation class.
 *
 * @see bindTo
 */
data class EnumResolutionMechanismProxy(
    val item: Item,
    val custom: Boolean,
    val mostRestrictive: List<Int>,
    val notCoexistable: Boolean,
) {
    fun generateDocs(enumValueToCodeReference: Map<Int, String>) =
        if (custom) {
            ""
        } else if (notCoexistable) {
            "This policy can not be set by multiple admins at the same time. When multiple values are set, the resulting behavior is undefined and is monitored to avoid widespread usage."
        } else if (mostRestrictive.isNotEmpty()) {
            val mostRestrictiveDocs =
                mostRestrictive.map { enumValueToCodeReference[it] ?: it.toString() }
            if (mostRestrictiveDocs.size == 2) {
                "If this policy is set by multiple admins, ${mostRestrictiveDocs[0]} takes effect if it is set by any admin."
            } else {
                "If this policy is set by multiple admins, the most restrictive value applies. The most restrictive value is ${mostRestrictiveDocs[0]}, followed by ${mostRestrictiveDocs.drop(1).joinToString(", ")}, in that order."
            }
        } else {
            item.codebase.reporter.reportOnMissingFields("resolutionMechanism", item)
            ""
        }
}
