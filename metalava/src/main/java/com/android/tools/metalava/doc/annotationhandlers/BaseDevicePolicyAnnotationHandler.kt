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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.asBoolean
import com.android.tools.metalava.model.value.asInt
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/**
 * Base class for Device Policy Annotation Handlers.
 *
 * Contains shared utilities for parsing attributes and reporting issues.
 */
abstract class BaseDevicePolicyAnnotationHandler(
    protected val codebase: Codebase,
    protected val reporter: Reporter,
    protected val filterReference: Predicate<SelectableItem>
) {

    /** Processes a policy annotation and returns a documentation string. */
    abstract fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String

    /**
     * Converts scope ID ({@link android.processor.devicepolicy.PolicyDefinition#allowedScopes}) to
     * a human-readable name.
     */
    protected fun getScopeName(scope: Int): String {
        return PolicyScope.fromId(scope)?.scopeName ?: scope.toString()
    }

    /**
     * Converts resource type ({@link
     * android.processor.devicepolicy.PolicyDefinition#allowedResource}) to a human-readable name.
     */
    protected fun getResourceName(resource: Int): String {
        return PolicyResource.fromId(resource)?.resourceName ?: resource.toString()
    }

    /** Report missing required fields inside the annotation. */
    protected fun reportOnMissingFields(fieldName: String, item: Item) {
        reporter.report(
            Issues.INVALID_DEVICE_POLICY_ANNOTATION,
            item,
            "Missing required field '$fieldName' inside $item"
        )
    }

    /** Extension to report an issue if the value is null. */
    protected fun <T> T?.elseReportMissing(item: Item, name: String): T? {
        if (this == null) reportOnMissingFields(name, item)
        return this
    }

    enum class PolicyScope(val scopeName: String, val id: Int) {
        USER("User", 1),
        DEVICE("Device", 2),
        PARENT_USER("Parent User", 3);

        companion object {
            fun fromId(id: Int): PolicyScope? = entries.firstOrNull { it.id == id }
        }
    }

    enum class PolicyResource(val resourceName: String, val id: Int) {
        DEVICE_WIDE("Device Wide", 1),
        PER_USER("Per User", 2);

        companion object {
            fun fromId(id: Int): PolicyResource? = entries.firstOrNull { it.id == id }
        }
    }
}

/**
 * Helper to retrieve string type attribute's value of an annotation (e.g. {@link
 * android.processor.devicepolicy.PolicyDefinition#allowedScopes}).
 */
fun AnnotationItem.getStringAttribute(name: String): String? {
    return findAttribute(name)?.value?.asString()
}

/**
 * Helper to retrieve integer type attribute's value of an annotation (e.g. {@link
 * android.processor.devicepolicy.PolicyDefinition#requiredPermission}).
 */
fun AnnotationItem.getIntAttribute(name: String): Int? {
    return findAttribute(name)?.value?.asInt()
}

/**
 * Helper to retrieve boolean type attribute's value of an annotation (e.g. {@link
 * android.processor.devicepolicy.EnumResolutionMechanism#custom}).
 */
fun AnnotationItem.getBooleanAttribute(name: String): Boolean? {
    return findAttribute(name)?.value?.asBoolean()
}

/**
 * Helper to retrieve the annotation item that corresponds to the PolicyDefinition typed field (e.g.
 * {@link android.processor.devicepolicy.EnumPolicyDefinition#base}).
 */
fun AnnotationItem.getPolicyDefinitionAttribute(name: String): AnnotationItem? {
    return (findAttribute(name)?.value as? AnnotationValue)?.annotationItem
}
