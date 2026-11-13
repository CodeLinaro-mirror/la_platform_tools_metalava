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

/** Common Java import statements used by device policy annotation handler tests. */
val POLICY_IMPORTS =
    """
        import android.processor.devicepolicy.AllowedDpcTypes;
        import android.processor.devicepolicy.AllowedRoles;
        import android.processor.devicepolicy.EnumResolutionMechanism;
        import android.processor.devicepolicy.IntegerPolicyDefinition;
        import android.processor.devicepolicy.IntegerResolutionMechanism;
        import android.processor.devicepolicy.IntegerValidation;
        import android.processor.devicepolicy.ListOfEnumPolicyDefinition;
        import android.processor.devicepolicy.ListOfPackagePolicyDefinition;
        import android.processor.devicepolicy.ListOfStringPolicyDefinition;
        import android.processor.devicepolicy.ListResolutionMechanism;
        import android.processor.devicepolicy.LongPolicyDefinition;
        import android.processor.devicepolicy.LongResolutionMechanism;
        import android.processor.devicepolicy.LongValidation;
        import android.processor.devicepolicy.PackagePolicyDefinition;
        import android.processor.devicepolicy.PolicyDefinition;
        import android.processor.devicepolicy.StringPolicyDefinition;
        import android.processor.devicepolicy.StringValidation;
        import static android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS;
        import static android.Manifest.permission.TEST;
        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;
    """
        .trimIndent()

/** Additional Java import statements for enum-based device policy annotation handler tests. */
private val EXTRA_ENUM_POLICY_IMPORTS =
    """
        import android.processor.devicepolicy.EnumPolicyDefinition;
        import android.processor.devicepolicy.EnumPolicyValues.EnumPolicyValue;
    """
        .trimIndent()

/** Java import statements used by enum and list-of-enum device policy annotation handler tests. */
val ENUM_POLICY_IMPORTS = "$POLICY_IMPORTS\n$EXTRA_ENUM_POLICY_IMPORTS"

/** Common scope, resource, and default value constants used in test policy classes. */
val POLICY_DEFINES =
    """
        private static final int SCOPE_USER = 1;
        private static final int SCOPE_DEVICE = 2;
        private static final int SCOPE_PARENT_USER = 3;
        private static final int SCOPE_LOGIN_SCREEN = 4;
        private static final int RESOURCE_DEVICE_WIDE = 1;
        private static final int RESOURCE_PER_USER = 2;
        private static final int DEFAULT_VALUE = 1;
    """
        .trimIndent()
