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

import com.android.tools.metalava.model.ANDROIDX_INT_DEF
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.testOrTrue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.asAny
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
        val enumValueToCodeReference = buildEnumValueToCodeReferenceMap(context.filterReference)
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

    /**
     * Build a map from the enum integer values to the corresponding integer variable's code
     * reference. For example:
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
    private fun buildEnumValueToCodeReferenceMap(
        filterReference: FilterPredicate?
    ): Map<Int, String> {
        // Get the enum value class object. Currently, the @EnumPolicyDefinition annotation's intDef
        // field is of type: Class<?>.
        val classItem = intDef

        // Find the @IntDef annotation of the enum value class
        val intDefAnnotation =
            classItem?.modifiers?.annotations()?.find { it.qualifiedName == ANDROIDX_INT_DEF }

        val enumValueAttrs =
            intDefAnnotation?.findAttribute("value")?.value?.asFlatList() ?: emptyList()

        val enumValueToName = mutableMapOf<Int, String>()

        for (enumValueAttr in enumValueAttrs) {
            if (enumValueAttr is FieldReferenceValue) {
                val qualifiedClassName = enumValueAttr.qualifiedClassName
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
                if (filterReference.testOrTrue(fieldItem)) {
                    val link = "{@link $qualifiedClassName#$fieldName}"
                    enumValueToName[fieldValue] = link
                } else {
                    reporter.report(
                        Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                        item,
                        "Cannot locate $fieldName required by $item (may be hidden or removed)"
                    )
                    enumValueToName[fieldValue] = fieldName
                }
            }
        }
        return enumValueToName
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
