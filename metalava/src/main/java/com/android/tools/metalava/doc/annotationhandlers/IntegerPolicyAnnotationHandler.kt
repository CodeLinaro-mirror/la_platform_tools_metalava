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

/** Handles @android.processor.devicepolicy.IntegerPolicyDefinition annotation. */
class IntegerPolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {
    /**
     * Processes the [IntegerPolicyDefinitionProxy] and returns the documentation for the policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val hasValidation =
            annotation.findAttribute("validation") != null ||
                annotation.resolve()?.methods()?.any { it.name() == "validation" } == true
        if (hasValidation) {
            val proxy = annotation.bindTo<IntegerPolicyDefinitionProxy>(item)
            return proxy?.generateDocs() ?: ""
        }
        val legacyProxy = annotation.bindTo<LegacyIntegerPolicyDefinitionProxy>(item)
        return legacyProxy?.generateDocs() ?: ""
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.IntegerPolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class IntegerPolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val validation: IntegerValidationProxy,
    val resolutionMechanism: IntegerResolutionMechanismProxy,
) {
    fun generateDocs() = buildString {
        val tableEntries = buildList {
            addAll(base.getTableEntries())
            val resolutionMechanismDoc = resolutionMechanism.generateDocs()
            if (resolutionMechanismDoc.isNotEmpty()) {
                add(Pair("Conflict resolution mechanism", resolutionMechanismDoc))
            }
            val policyValueValidations = validation.getPolicyValueValidations()
            add(Pair("Policy value", renderPolicyValue("Integer", policyValueValidations)))
        }

        append(renderTable(tableEntries))
    }
}

// TODO(b/550199902): Remove when validators are extracted from policy definitions
/**
 * Proxy class bound to an instance of the legacy
 * `android.processor.devicepolicy.IntegerPolicyDefinition` annotation class where validation
 * properties were inline.
 *
 * @see bindTo
 */
data class LegacyIntegerPolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val minValue: Int,
    val maxValue: Int,
    val resolutionMechanism: IntegerResolutionMechanismProxy,
) {
    fun generateDocs() = buildString {
        val tableEntries = buildList {
            addAll(base.getTableEntries())
            val resolutionMechanismDoc = resolutionMechanism.generateDocs()
            if (resolutionMechanismDoc.isNotEmpty()) {
                add(Pair("Conflict resolution mechanism", resolutionMechanismDoc))
            }
            val policyValueValidations =
                IntegerValidationProxy(
                        minValue = minValue,
                        maxValue = maxValue,
                    )
                    .getPolicyValueValidations()
            add(Pair("Policy value", renderPolicyValue("Integer", policyValueValidations)))
        }

        append(renderTable(tableEntries))
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.IntegerValidation`
 * annotation class.
 *
 * @see bindTo
 */
data class IntegerValidationProxy(
    val minValue: Int,
    val maxValue: Int,
) {
    fun getPolicyValueValidations(): List<String> = buildList {
        if (minValue != Integer.MIN_VALUE) add("Minimum value $minValue")
        if (maxValue != Integer.MAX_VALUE) add("Maximum value $maxValue")
    }
}

/**
 * Proxy class bound to an instance of the
 * `android.processor.devicepolicy.IntegerResolutionMechanism` annotation class.
 *
 * @see bindTo
 */
data class IntegerResolutionMechanismProxy(
    val item: Item,
    val custom: Boolean,
    val notCoexistable: Boolean,
) {
    fun generateDocs() =
        if (custom) {
            ""
        } else if (notCoexistable) {
            "This policy can not be set by multiple admins at the same time. When multiple values are set, the resulting behavior is undefined and is monitored to avoid widespread usage."
        } else {
            item.codebase.reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "IntegerResolutionMechanism must have either 'custom' or 'notCoexistable' set to true."
            )
            ""
        }
}
