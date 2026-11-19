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

/** Handles @android.processor.devicepolicy.ListOfPackagePolicyDefinition annotation. */
class ListOfPackagePolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {
    /**
     * Processes the [ListOfPackagePolicyDefinitionProxy] and returns the documentation for the
     * policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<ListOfPackagePolicyDefinitionProxy>(item)
        return proxy?.generateDocs() ?: ""
    }
}

data class ListOfPackagePolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val resolutionMechanism: ListResolutionMechanismProxy,
    val emptyListAllowed: Boolean,
    val maxListLength: Int,
) {

    fun generateDocs() = buildString {
        val tableEntries = buildList {
            addAll(base.getTableEntries())

            val resMechDocs = resolutionMechanism.generateDocs(base.item)
            if (resMechDocs.isNotEmpty()) {
                add(Pair("Conflict resolution mechanism", resMechDocs))
            }

            val policyValueValidations = buildList {
                if (maxListLength != Int.MAX_VALUE) add("Length max $maxListLength items")
                if (!emptyListAllowed) add("No empty list allowed")
            }
            add(Pair("Policy value", renderPolicyValue("List<Package>", policyValueValidations)))
        }

        append(renderTable(tableEntries))
    }
}
