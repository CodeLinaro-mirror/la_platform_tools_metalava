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

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.reporter.Issues

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.PolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
class PolicyDefinitionProxy(
    /** The item on which this was annotated. */
    val item: Item,
    private val allowedScopes: List<Int>,
    private val affectedResource: Int,
    private val requiredPermission: String?,
    private val requiredCrossUserPermission: String?,
    private val applyOnFullUsersOnly: Boolean = false,
    private val allowedDpcTypes: AllowedDpcTypesProxy,
    private val allowedRoles: AllowedRolesProxy,
) {
    private val codebase = item.codebase
    private val reporter = codebase.reporter

    private val allowedDpcTypesForLoginScreen =
        listOf(
            AllowedDpcType.DEVICE_OWNER,
            AllowedDpcType.MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
            AllowedDpcType.FINANCED_DEVICE_OWNER,
        )

    init {
        // Validate the properties.
        if (allowedScopes.isEmpty()) {
            reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "'allowedScopes' is empty on $item: Must provide at least one scope"
            )
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

    /**
     * Which DPCs have which cross-user permission granted. Scraped from
     * frameworks/base/services/devicepolicy/java/com/android/server/devicepolicy/PermissionChecker.java
     * Keep it in sync with:
     * cts/tools/cts-policy-tests-generator/src/com/android/cts/policytestsgenerator/AppliedByGenerator.kt
     */
    private fun getDpcTypesWithCrossUserPermission(crossUserPermission: String) =
        when (crossUserPermission) {
            "" -> AllowedDpcType.entries.toList()
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS" ->
                listOf(
                    AllowedDpcType.DEVICE_OWNER,
                    AllowedDpcType.FINANCED_DEVICE_OWNER,
                    AllowedDpcType.MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                )
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL" ->
                listOf(
                    AllowedDpcType.DEVICE_OWNER,
                    AllowedDpcType.FINANCED_DEVICE_OWNER,
                )
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL" ->
                listOf(
                    AllowedDpcType.DEVICE_OWNER,
                    AllowedDpcType.FINANCED_DEVICE_OWNER,
                    AllowedDpcType.MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                    AllowedDpcType.PROFILE_OWNER_ON_USER_0,
                    AllowedDpcType.MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
                    AllowedDpcType.UNAFFILIATED_FULL_USER_PROFILE_OWNER,
                    AllowedDpcType.AFFILIATED_FULL_USER_PROFILE_OWNER,
                )
            else -> {
                reporter.report(
                    Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                    item,
                    "Unknown cross-user permission: $crossUserPermission"
                )
                emptyList()
            }
        }

    private fun getAllowedDPCTypesForScope(
        crossUserPermission: String,
        scope: Int
    ): List<AllowedDpcType> {
        val allowedDpcs = AllowedDpcType.entries.filter { it.isAllowed(allowedDpcTypes) }
        return if (scope == PolicyScope.USER.id) {
            allowedDpcs
        } else if (scope == PolicyScope.DEVICE.id || scope == PolicyScope.PARENT_USER.id) {
            val crossUserDpcs = getDpcTypesWithCrossUserPermission(crossUserPermission)
            allowedDpcs.filter { it in crossUserDpcs }
        } else if (scope == PolicyScope.LOGIN_SCREEN.id) {
            val crossUserDpcs = getDpcTypesWithCrossUserPermission(crossUserPermission)
            allowedDpcs.filter { it in crossUserDpcs && it in allowedDpcTypesForLoginScreen }
        } else {
            reporter.report(Issues.INVALID_DEVICE_POLICY_ANNOTATION, item, "Invalid scope: $scope")
            emptyList()
        }
    }

    private fun getAllowedDpcForScopeDoc(scopeId: Int): String {
        val dpcs = getAllowedDPCTypesForScope(requiredCrossUserPermission ?: "", scopeId)
        return if (dpcs.isNotEmpty()) {
            "<ul>\n" + dpcs.joinToString("") { "    <li>${it.description}</li>\n" } + "</ul>\n"
        } else ""
    }

    /** Generates table entries for the base policy definition. */
    fun getTableEntries(): List<Pair<String, String>> = buildList {
        if (applyOnFullUsersOnly) {
            add(
                Pair(
                    "Supported users",
                    "Full users only (does not support profiles including work profiles)."
                )
            )
        }

        if (allowedScopes.isNotEmpty()) {
            val docLines = mutableListOf<String>()

            val hasUser = allowedScopes.contains(PolicyScope.USER.id)
            val hasDevice = allowedScopes.contains(PolicyScope.DEVICE.id)
            val hasParent = allowedScopes.contains(PolicyScope.PARENT_USER.id)
            val hasLoginScreen = allowedScopes.contains(PolicyScope.LOGIN_SCREEN.id)

            if (hasUser) {
                val allowedDpcDoc = getAllowedDpcForScopeDoc(PolicyScope.USER.id).ifEmpty { null }
                val permission = requiredPermission?.let { resolvePermissionCodeLink(it, item) }
                docLines.add(
                    formatSettableByEntry(
                        scope = "<code>User</code>",
                        permission = permission,
                        dpcTypes = allowedDpcDoc
                    )
                )
            }

            if (hasDevice || hasParent) {
                val scopeId = if (hasDevice) PolicyScope.DEVICE.id else PolicyScope.PARENT_USER.id
                val scope =
                    if (hasDevice && hasParent) "<code>Device</code> and <code>Parent User</code>"
                    else if (hasDevice) "<code>Device</code>" else "<code>Parent User</code>"

                val allowedDpcDoc = getAllowedDpcForScopeDoc(scopeId).ifEmpty { null }

                val permission = formatPermissions(requiredPermission, requiredCrossUserPermission)
                docLines.add(
                    if (hasUser) {
                        formatAlsoSettableByEntry(
                            scope = scope,
                            permission = permission,
                            dpcTypes = allowedDpcDoc,
                        )
                    } else {
                        formatSettableByEntry(
                            scope = scope,
                            permission = permission,
                            dpcTypes = allowedDpcDoc,
                        )
                    }
                )
            }

            if (hasLoginScreen) {
                val allowedDpcDoc =
                    getAllowedDpcForScopeDoc(PolicyScope.LOGIN_SCREEN.id).ifEmpty { null }
                val permission = formatPermissions(requiredPermission, requiredCrossUserPermission)
                docLines.add(
                    if (hasUser && (hasDevice || hasParent)) {
                        formatAlsoSettableByEntry(
                            scope = "<code>Login screen</code>",
                            permission = permission,
                            dpcTypes = allowedDpcDoc,
                            leadingWords = "Moreover"
                        )
                    } else if (hasUser || hasDevice || hasParent) {
                        formatAlsoSettableByEntry(
                            scope = "<code>Login screen</code>",
                            permission = permission,
                            dpcTypes = allowedDpcDoc
                        )
                    } else {
                        formatSettableByEntry(
                            scope = "<code>Login screen</code>",
                            permission = permission,
                            dpcTypes = allowedDpcDoc
                        )
                    }
                )
            }

            add(Pair("Settable by", docLines.joinToString("")))
        }

        add(Pair("Resources affected", getResourceName(affectedResource)))
    }

    private fun formatPermissions(first: String?, second: String?): String? {
        val firstCodeLink = first?.let { resolvePermissionCodeLink(it, item) }
        val secondCodeLink = second?.let { resolvePermissionCodeLink(it, item) }
        return if (firstCodeLink != null && secondCodeLink != null) {
            "$firstCodeLink and $secondCodeLink"
        } else {
            firstCodeLink ?: secondCodeLink
        }
    }

    private fun formatSettableByEntry(
        scope: String,
        permission: String?,
        dpcTypes: String?
    ): String {
        return if (permission == null && dpcTypes == null) {
            "<p>This policy can be set with scope ${scope}.</p>\n"
        } else if (permission == null) {
            "<p>This policy can be set with scope ${scope} by the following DPC types: \n$dpcTypes</p>\n"
        } else if (dpcTypes == null) {
            "<p>This policy can be set with scope ${scope} by anyone holding $permission.</p>\n"
        } else {
            // Nothing is null
            "<p>This policy can be set with scope ${scope} by anyone holding $permission, or the following DPC types: \n$dpcTypes</p>\n"
        }
    }

    private fun formatAlsoSettableByEntry(
        scope: String,
        permission: String?,
        dpcTypes: String?,
        leadingWords: String = "In addition",
    ): String {
        return if (permission == null && dpcTypes == null) {
            "<p>${leadingWords}, this policy can be set with scope ${scope}.</p>\n"
        } else if (permission == null) {
            "<p>${leadingWords}, this policy can be set with scope ${scope} by the following DPC types: \n$dpcTypes</p>\n"
        } else if (dpcTypes == null) {
            "<p>${leadingWords}, this policy can be set with scope ${scope} by anyone holding $permission.</p>\n"
        } else {
            // Nothing is null
            "<p>${leadingWords}, this policy can be set with scope ${scope} by anyone holding $permission, or the following DPC types: \n$dpcTypes</p>\n"
        }
    }

    companion object {
        /** Converts scope ID from [allowedScopes] to a human-readable name. */
        private fun getScopeName(scope: Int) =
            PolicyScope.fromId(scope)?.scopeName ?: scope.toString()

        /** Converts resource type from [affectedResource] to a human-readable name. */
        private fun getResourceName(resource: Int) =
            PolicyResource.fromId(resource)?.resourceName ?: resource.toString()
    }
}

enum class PolicyScope(val scopeName: String, val id: Int) {
    USER("User", 1),
    DEVICE("Device", 2),
    PARENT_USER("Parent User", 3),
    LOGIN_SCREEN("Login screen", 4);

    companion object {
        fun fromId(id: Int): PolicyScope? = entries.firstOrNull { it.id == id }
    }
}

enum class PolicyResource(val resourceName: String, val id: Int) {
    DEVICE_WIDE("This policy takes effect device-wide, so it affects all users.", 1),
    PER_USER("This policy only takes effect on the user on which it is set.", 2);

    companion object {
        fun fromId(id: Int): PolicyResource? = entries.firstOrNull { it.id == id }
    }
}
