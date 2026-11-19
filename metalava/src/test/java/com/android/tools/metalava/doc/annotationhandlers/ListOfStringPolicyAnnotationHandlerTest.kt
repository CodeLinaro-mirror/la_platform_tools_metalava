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
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.POLICY_DEFINITION_SOURCE
import com.android.tools.metalava.testing.java
import org.junit.Test

class ListOfStringPolicyAnnotationHandlerTest : DriverTest() {

    @Test
    fun `Test ListOfStringPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.ListOfStringPolicyDefinition;
                        import android.processor.devicepolicy.ListResolutionMechanism;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                            private static final int DEFAULT_VALUE = 1;
                          /**
                           * A test policy for list-of-string policy definition.
                           *
                           * Some other human handwritten comments.
                           */
                            @ListOfStringPolicyDefinition(
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
                                emptyListAllowed = true,
                                emptyStringAllowed = true,
                                unprintableCharactersAllowed = true,
                                pureWhitespaceAllowed = true,
                                unstrippedStringAllowed = true,
                                maxListLength = 5,
                                resolutionMechanism = @ListResolutionMechanism(custom = true)
                            )
                            public static final String POLICY_FIELD = "";
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
                         * A test policy for list-of-string policy definition.
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
                         *      <code>List<String></code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 5 items</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final java.lang.String POLICY_FIELD = "";
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test ListOfStringPolicyDefinition reports error when resolution mechanism is invalid`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.ListOfStringPolicyDefinition;
                        import android.processor.devicepolicy.ListResolutionMechanism;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;

                            @ListOfStringPolicyDefinition(
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
                                    )
                                ),
                                maxListLength = Integer.MAX_VALUE,
                                resolutionMechanism = @ListResolutionMechanism()
                            )
                            public static final String POLICY_FIELD = "";
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
                         *      <code>List<String></code> with the following restrictions:
                         *      <ul>
                         *        <li>No empty list allowed</li>
                         *        <li>No empty string allowed</li>
                         *        <li>No unprintable characters allowed</li>
                         *        <li>No pure whitespace allowed</li>
                         *        <li>No unstripped string allowed</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final java.lang.String POLICY_FIELD = "";
                        }
                        """
                    )
                ),
            expectedIssues =
                """
                src/test/pkg/TestPolicy.java:28: error: ListResolutionMechanism must have either 'custom' or 'union' set to true. [InvalidDevicePolicyAnnotation]
                """
        )
    }
}
