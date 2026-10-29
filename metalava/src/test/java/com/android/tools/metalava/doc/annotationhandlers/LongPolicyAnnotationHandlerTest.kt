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
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.LEGACY_POLICY_DEFINITION_SOURCE
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.POLICY_DEFINITION_SOURCE
import com.android.tools.metalava.testing.java
import org.junit.Test

class LongPolicyAnnotationHandlerTest : DriverTest() {

    @Test
    fun `Test LongPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    POLICY_DEFINITION_SOURCE,
                    ANDROID_MANIFEST_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.LongPolicyDefinition;
                        import android.processor.devicepolicy.LongResolutionMechanism;
                        import android.processor.devicepolicy.LongValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for long policy definition.
                           *
                           * Some other human handwritten comments.
                           */
                            @LongPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                validation = @LongValidation(
                                    minValue = 10L,
                                    maxValue = 100L
                                ),
                                resolutionMechanism = @LongResolutionMechanism(custom = true)
                            )
                            public static final long POLICY_FIELD = 1L;
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
                         * A test policy for long policy definition.
                         *
                         * Some other human handwritten comments.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST}, or the following DPC types:
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
                         *    <td>This policy takes effect device-wide, so it affects all users.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>Long</code> with the following restrictions:
                         *      <ul>
                         *        <li>Minimum value 10</li>
                         *        <li>Maximum value 100</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final long POLICY_FIELD = 1L;
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test legacy LongPolicyDefinition with inline validation attributes generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    LEGACY_POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.LongPolicyDefinition;
                        import android.processor.devicepolicy.LongResolutionMechanism;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for long policy definition with legacy inline validation attributes.
                           *
                           * Some other human handwritten comments.
                           */
                            @LongPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                minValue = 10L,
                                maxValue = 100L,
                                resolutionMechanism = @LongResolutionMechanism(custom = true)
                            )
                            public static final long POLICY_FIELD = 1L;
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
                         * A test policy for long policy definition with legacy inline validation attributes.
                         *
                         * Some other human handwritten comments.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST}, or the following DPC types:
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
                         *    <td>This policy takes effect device-wide, so it affects all users.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>Long</code> with the following restrictions:
                         *      <ul>
                         *        <li>Minimum value 10</li>
                         *        <li>Maximum value 100</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final long POLICY_FIELD = 1L;
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test LongPolicyDefinition with default LongValidation generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    POLICY_DEFINITION_SOURCE,
                    ANDROID_MANIFEST_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.LongPolicyDefinition;
                        import android.processor.devicepolicy.LongResolutionMechanism;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy with default long validation.
                           */
                            @LongPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    )
                                ),
                                resolutionMechanism = @LongResolutionMechanism(custom = true)
                            )
                            public static final long POLICY_FIELD = 1L;
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
                         * A test policy with default long validation.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST}, or the following DPC types:
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
                         *    <td>This policy takes effect device-wide, so it affects all users.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td><code>Long</code></td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final long POLICY_FIELD = 1L;
                        }
                        """
                    )
                )
        )
    }
}
