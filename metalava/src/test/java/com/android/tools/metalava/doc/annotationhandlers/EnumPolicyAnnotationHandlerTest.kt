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

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.ANDROID_MANIFEST_SOURCE
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.ENUM_POLICY_DEFINITION_SOURCE
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.POLICY_DEFINITION_SOURCE
import com.android.tools.metalava.intDefAnnotationSource
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.testing.java
import org.junit.Test

class EnumPolicyAnnotationHandlerTest : DriverTest() {

    @Test
    fun `Test EnumPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    ENUM_POLICY_DEFINITION_SOURCE,
                    intDefAnnotationSource,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.EnumPolicyDefinition;
                        import android.processor.devicepolicy.EnumPolicyValues.EnumPolicyValue;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.EnumResolutionMechanism;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                            private static final int DEFAULT_VALUE = 1;
                            @EnumPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.ENUM_TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                intDef = EnumPolicyValue.class,
                                resolutionMechanism = @EnumResolutionMechanism(custom = true),
                                defaultValue = DEFAULT_VALUE
                            )
                            public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#ENUM_TEST android.permission.ENUM_TEST}, or the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Unaffiliated Full User Profile Owner</li>
                         *          <li>Affiliated Full User Profile Owner</li>
                         *      </ul>
                         *      </p>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Resources affected</td>
                         *    <td>This policy takes effect device-wide, so it affects all users.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>Integer</code>, value is one of the following:
                         *      <ul>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_1} (default)</li>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_2}</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test EnumPolicyDefinition with most restrictive resolution mechanism generates docs with code references`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    ENUM_POLICY_DEFINITION_SOURCE,
                    intDefAnnotationSource,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.EnumPolicyDefinition;
                        import android.processor.devicepolicy.EnumPolicyValues.EnumPolicyValue;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.EnumResolutionMechanism;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                            private static final int DEFAULT_VALUE = 1;
                            @EnumPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.ENUM_TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    )
                                ),
                                intDef = EnumPolicyValue.class,
                                resolutionMechanism = @EnumResolutionMechanism(mostRestrictive = {1, 2}),
                                defaultValue = DEFAULT_VALUE
                            )
                            public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#ENUM_TEST android.permission.ENUM_TEST}, or the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Unaffiliated Full User Profile Owner</li>
                         *          <li>Affiliated Full User Profile Owner</li>
                         *      </ul>
                         *      </p>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Resources affected</td>
                         *    <td>This policy takes effect device-wide, so it affects all users.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Conflict resolution mechanism</td>
                         *    <td>If this policy is set by multiple admins, {@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_1} takes effect if it is set by any admin.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>Integer</code>, value is one of the following:
                         *      <ul>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_1} (default)</li>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_2}</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test EnumPolicyDefinition with invalid links outputs plain text and reports issues`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    ENUM_POLICY_DEFINITION_SOURCE,
                    intDefAnnotationSource,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.EnumPolicyDefinition;
                        import android.processor.devicepolicy.EnumPolicyValues.EnumPolicyValue;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.EnumResolutionMechanism;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_PER_USER = 2;
                            private static final int DEFAULT_VALUE = 1;
                            @EnumPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_PER_USER,
                                    requiredPermission = "android.permission.DOES_NOT_EXIST",
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    )
                                ),
                                intDef = EnumPolicyValue.class,
                                resolutionMechanism = @EnumResolutionMechanism(),
                                defaultValue = DEFAULT_VALUE
                            )
                            public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                ),
            checkCompilation = true,
            docStubs = true,
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/TestPolicy.java:32: error: Cannot find permission field for android.permission.DOES_NOT_EXIST required by field test.pkg.TestPolicy.POLICY_FIELD (may be hidden or removed) [InvalidDevicePolicyAnnotation]
                    src/test/pkg/TestPolicy.java:32: error: Missing required field 'resolutionMechanism' inside field test.pkg.TestPolicy.POLICY_FIELD [InvalidDevicePolicyAnnotation]
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding android.permission.DOES_NOT_EXIST, or the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Managed Profile Owner (Of Personally Owned Device)</li>
                         *          <li>Unaffiliated Full User Profile Owner</li>
                         *          <li>Profile Owner on User 0</li>
                         *          <li>Affiliated Full User Profile Owner</li>
                         *      </ul>
                         *      </p>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Resources affected</td>
                         *    <td>This policy only takes effect on the user on which it is set.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>Integer</code>, value is one of the following:
                         *      <ul>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_1} (default)</li>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_2}</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                )
        )
    }
}
