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
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.asInt
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles {@link android.processor.devicepolicy.PolicyDefinition} annotation. */
class PolicyDefinitionAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {

    /** Processes a policy annotation and returns a documentation string. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val basePolicy = parseBasePolicy(annotation, item)
        return generateBaseDocs(basePolicy, item)
    }

    /** Extracts allowed DPC values from the annotation. */
    private fun extractAllowedDpcTypes(annotation: AnnotationItem): List<String> {
        val allowedDpcTypesValue = annotation.findAttribute("allowedDpcTypes")?.value
        val allowedDpcAnnotation = (allowedDpcTypesValue as? AnnotationValue)?.annotationItem
        if (allowedDpcAnnotation == null) {
            return emptyList()
        }

        return AllowedDpcType.entries.mapNotNull {
            if (allowedDpcAnnotation.getIntAttribute(it.attributeName) == DPC_ANNOTATION_ALLOWED) {
                it.description
            } else {
                null
            }
        }
    }

    /** Resolves a permission code link to a format suitable for documentation. */
    private fun resolvePermissionCodeLink(value: String, item: Item): String {
        val permissionClass = codebase.findClass("android.Manifest.permission")
        if (permissionClass == null) {
            reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "Cannot find permission field for $value required by $item (may be hidden or removed)"
            )
            return value
        }
        val fieldName = value.substringAfterLast(".").uppercase()
        val field = permissionClass.findField(fieldName)
        if (field is FieldItem) {
            return "{@link ${field.containingClass().qualifiedName()}#${field.name()} $value}"
        }
        reporter.report(
            Issues.INVALID_DEVICE_POLICY_ANNOTATION,
            item,
            "Cannot find permission field for $value required by $item (may be hidden or removed)"
        )
        return value
    }

    /** Parses the base policy definition from the annotation. */
    fun parseBasePolicy(annotation: AnnotationItem, item: Item): BasePolicyDefinition {
        val allowedScopesItem = annotation.findAttribute("allowedScopes")?.value
        val allowedScopesList =
            allowedScopesItem?.asFlatList()?.mapNotNull { it.asInt() } ?: emptyList()
        if (allowedScopesList.isEmpty()) {
            reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "Missing required field 'allowedScopes' inside $item"
            )
        }

        val affectedResource =
            annotation
                .getIntAttribute("affectedResource")
                .elseReportMissing(item, "affectedResource") ?: -1
        val requiredPermission = annotation.getStringAttribute("requiredPermission")
        val requiredCrossUserPermission =
            annotation.getStringAttribute("requiredCrossUserPermission")

        val allowedDpcTypes = extractAllowedDpcTypes(annotation)

        return BasePolicyDefinition(
            allowedScopes = allowedScopesList,
            affectedResource = affectedResource,
            requiredPermission = requiredPermission,
            requiredCrossUserPermission = requiredCrossUserPermission,
            allowedDpcTypes = allowedDpcTypes,
        )
    }

    /** Generates documentation for the base policy definition. */
    fun generateBaseDocs(basePolicy: BasePolicyDefinition, item: Item) = buildString {
        basePolicy.allowedScopes
            .takeIf { it.isNotEmpty() }
            ?.let { scopes ->
                append("   <li>Allowed Scopes:\n    <ul>\n")
                scopes.joinTo(this, separator = "") { "       <li>${getScopeName(it)}</li>\n" }
                append("     </ul>\n   </li>\n")
            }

        basePolicy.affectedResource.let {
            append("   <li>Affected Resource: ${getResourceName(it)}</li>\n")
        }
        basePolicy.requiredPermission?.let { permission ->
            append(
                "   <li>Required Permission: ${resolvePermissionCodeLink(permission, item)}</li>\n"
            )
        }
        basePolicy.requiredCrossUserPermission?.let { permission ->
            append(
                "   <li>Required Cross User Permission: ${resolvePermissionCodeLink(permission, item)}</li>\n"
            )
        }

        basePolicy.allowedDpcTypes
            .takeIf { it.isNotEmpty() }
            ?.let { dpcTypes ->
                append("   <li>Allowed DPC Types: \n    <ul>\n")
                dpcTypes.joinTo(this, separator = "") { "       <li>$it</li>\n" }
                append("     </ul>\n   </li>\n")
            }
    }

    companion object {
        private const val DPC_ANNOTATION_ALLOWED = 1
    }
}

enum class AllowedDpcType(
    val description: String,
    val attributeName: String,
) {
    DEVICE_OWNER(
        description = "Device Owner",
        attributeName = "deviceOwner",
    ),
    MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE(
        description = "Managed Profile Owner (Of Organization Owned Device)",
        attributeName = "managedProfileOwnerOfOrganizationOwnedDevice",
    ),
    MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE(
        description = "Managed Profile Owner (Of Personally Owned Device)",
        attributeName = "managedProfileOwnerOfPersonalOwnedDevice",
    ),
    UNAFFILIATED_FULL_USER_PROFILE_OWNER(
        description = "Unaffiliated Full User Profile Owner",
        attributeName = "unaffiliatedFullUserProfileOwner",
    ),
    FINANCED_DEVICE_OWNER(
        description = "Financed Device Owner",
        attributeName = "financedDeviceOwner",
    ),
    PROFILE_OWNER_ON_USER_0(
        description = "Profile Owner on User 0",
        attributeName = "profileOwnerOnUser0",
    ),
    AFFILIATED_FULL_USER_PROFILE_OWNER(
        description = "Affiliated Full User Profile Owner",
        attributeName = "affiliatedFullUserProfileOwner",
    ),
}

/** Data class to hold the parsed {@link android.processor.devicepolicy.PolicyDefinition}. */
data class BasePolicyDefinition(
    // Contains parsed values of {@link
    // android.processor.devicepolicy.PolicyDefinition#allowedScopes}
    val allowedScopes: List<Int>,
    // Contains parsed value of {@link
    // android.processor.devicepolicy.PolicyDefinition#affectedResource}
    val affectedResource: Int,
    // Contains parsed value of {@link
    // android.processor.devicepolicy.PolicyDefinition#requiredPermission}
    val requiredPermission: String?,
    // Contains parsed value of {@link
    // android.processor.devicepolicy.PolicyDefinition#requiredCrossUserPermission}
    val requiredCrossUserPermission: String?,
    // Contains parsed values of {@link
    // android.processor.devicepolicy.PolicyDefinition#allowedDpcTypes}
    val allowedDpcTypes: List<String>,
)
