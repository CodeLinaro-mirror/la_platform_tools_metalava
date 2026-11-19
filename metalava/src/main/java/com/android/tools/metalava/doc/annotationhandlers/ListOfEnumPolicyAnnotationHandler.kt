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

/** Handles @android.processor.devicepolicy.ListOfEnumPolicyDefinition annotation. */
class ListOfEnumPolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {

    /**
     * Processes the [ListOfEnumPolicyDefinitionProxy] and returns the documentation for the policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<ListOfEnumPolicyDefinitionProxy>(item)
        return proxy?.generateDocs(context) ?: ""
    }
}

/**
 * Proxy class bound to an instance of the
 * `android.processor.devicepolicy.ListOfEnumPolicyDefinition` annotation class.
 *
 * @see bindTo
 */
data class ListOfEnumPolicyDefinitionProxy(
    val item: Item,
    val base: PolicyDefinitionProxy,
    val intDef: ClassItem?,
    val emptyListAllowed: Boolean,
    val resolutionMechanism: ListResolutionMechanismProxy,
    val maxListLength: Int,
) {
    fun generateDocs(context: DevicePolicyContext) = buildString {
        val tableEntries = buildList {
            addAll(base.getTableEntries())

            val resolutionMechanismDocs = resolutionMechanism.generateDocs(base.item)
            if (resolutionMechanismDocs.isNotEmpty()) {
                add(Pair("Conflict resolution mechanism", resolutionMechanismDocs))
            }

            add(
                Pair(
                    "Policy value",
                    renderPolicyValue("List<Integer>", getValueValidation(context)),
                )
            )
        }

        append(renderTable(tableEntries))
    }

    private fun getValueValidation(context: DevicePolicyContext): List<String> = buildList {
        val enumValueToCodeReference =
            buildEnumValueToCodeReferenceMap(intDef, item, context.filterReference)

        if (maxListLength != Int.MAX_VALUE) add("Length max $maxListLength items")
        if (!emptyListAllowed) add("No empty list allowed")
        if (enumValueToCodeReference.isNotEmpty()) {
            val valuesDoc = buildString {
                append("Each element is one of the following:\n")
                append("    <ul>\n")
                enumValueToCodeReference.values.forEach { codeRef ->
                    append("      <li>$codeRef</li>\n")
                }
                append("    </ul>")
            }
            add(valuesDoc)
        }
    }
}
