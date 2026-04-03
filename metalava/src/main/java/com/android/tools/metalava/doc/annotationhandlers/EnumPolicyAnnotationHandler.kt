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
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.asAny
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles @android.processor.devicepolicy.EnumPolicyDefinition annotation. */
class EnumPolicyAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {

    private val policyHandler =
        PolicyDefinitionAnnotationHandler(codebase, reporter, filterReference)

    /** Processes a policy annotation and returns a documentation string. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val resolutionMechanismDoc = buildResolutionMechanismDoc(annotation, item)
        val enumValueToCodeReference = buildEnumValueToCodeReferenceMap(annotation, item)
        val defaultValue =
            annotation.getIntAttribute("defaultValue").elseReportMissing(item, "defaultValue") ?: -1

        val basePolicyDefinition =
            annotation.getPolicyDefinitionAttribute("base").elseReportMissing(item, "base")
        val baseDocs =
            basePolicyDefinition?.let {
                policyHandler.processPolicyAnnotation(basePolicyDefinition, item)
            } ?: ""

        return buildString {
            append("\n<p>Policy Type: Enum</p>\n <ul>\n")
            append(baseDocs)
            append("   <li>Resolution Mechanism: $resolutionMechanismDoc</li>\n")
            if (enumValueToCodeReference.isNotEmpty()) {
                append("   <li>Enum policy values:\n     <ul>\n")
                enumValueToCodeReference.entries
                    .map { entry ->
                        if (entry.key == defaultValue) {
                            "        <li>${entry.value} (default)</li>\n"
                        } else {
                            "        <li>${entry.value}</li>\n"
                        }
                    }
                    .joinTo(this, separator = "")
                append("     </ul>\n   </li>\n")
            }
            append(" </ul>\n")
        }
    }

    private fun buildResolutionMechanismDoc(annotation: AnnotationItem, item: Item): String {
        val resolutionMechanismValue = annotation.findAttribute("resolutionMechanism")?.value
        val resolutionMechanismAnnotation =
            (resolutionMechanismValue as? AnnotationValue)?.annotationItem

        var resolutionMechanismDoc = ""

        if (resolutionMechanismAnnotation != null) {
            val custom = resolutionMechanismAnnotation.getBooleanAttribute("custom") ?: false
            val notCoexistable =
                resolutionMechanismAnnotation.getBooleanAttribute("notCoexistable") ?: false
            val mostRestrictiveValue =
                resolutionMechanismAnnotation.findAttribute("mostRestrictive")?.value
            val mostRestrictive = mostRestrictiveValue?.asFlatList() ?: emptyList()

            if (custom) {
                resolutionMechanismDoc = "custom"
            } else if (notCoexistable) {
                resolutionMechanismDoc = "not coexistable"
            } else if (mostRestrictive.isNotEmpty()) {
                resolutionMechanismDoc = "most restrictive: [${mostRestrictive.joinToString(", ")}]"
            } else {
                reportOnMissingFields("resolutionMechanism", item)
            }
        }

        return resolutionMechanismDoc
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
        annotation: AnnotationItem,
        item: Item
    ): Map<Int, String> {
        // Get the enum value class object. Currently the @EnumPolicyDefinition annotation's intDef
        // field is of type: Class<?>.
        val enumValueClassObject = annotation.findAttribute("intDef")?.value as? ClassObjectValue
        val qualifiedName = (enumValueClassObject?.typeItem as? ClassTypeItem)?.qualifiedName
        val classItem = qualifiedName?.let { codebase.resolveClass(it) }

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
                if (filterReference.test(fieldItem)) {
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
