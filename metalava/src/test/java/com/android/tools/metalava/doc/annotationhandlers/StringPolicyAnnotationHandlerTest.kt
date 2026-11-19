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

class StringPolicyAnnotationHandlerTest : DriverTest() {
    @Test
    fun `Test StringPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_DEVICE = 2;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for string policy definition with multiple scopes.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = DISALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    emptyStringAllowed = true,
                                    unprintableCharactersAllowed = true,
                                    pureWhitespaceAllowed = true,
                                    unstrippedStringAllowed = true,
                                    maxLength = 100
                                )
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
                         * A test policy for string policy definition with multiple scopes.
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
                         *          <li>Profile Owner on User 0</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
    fun `Test StringPolicyDefinition with scope not settable by any DPC type generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_PARENT_USER = 3;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for string policy definition with a scope not settable by any DPC.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER, SCOPE_PARENT_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = DISALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    emptyStringAllowed = true,
                                    unprintableCharactersAllowed = true,
                                    maxLength = 100
                                )
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
                         * A test policy for string policy definition with a scope not settable by any DPC.
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
                         *          <li>Profile Owner on User 0</li>
                         *      </ul>
                         *      </p>
                         *      <p>In addition, this policy can be set with scope <code>Parent User</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST} and {@link android.Manifest.permission#MANAGE_DEVICE_POLICY_ACROSS_USERS android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS}.</p>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Resources affected</td>
                         *    <td>This policy takes effect device-wide, so it affects all users.</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
                )
        )
    }

    @Test
    fun `Test StringPolicyDefinition with device and parent scopes generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_DEVICE = 2;
                            private static final int SCOPE_PARENT_USER = 3;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_DEVICE, SCOPE_PARENT_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    maxLength = 100
                                )
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
                         * A test policy.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>Device</code> and <code>Parent User</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Profile Owner on User 0</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
                )
        )
    }

    @Test
    fun `Test StringPolicyDefinition with device scope only without permissions`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_DEVICE = 2;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_DEVICE},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    maxLength = 100
                                )
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
                         * A test policy.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>Device</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
                )
        )
    }

    @Test
    fun `Test StringPolicyDefinition with user and login screen scopes generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_LOGIN_SCREEN = 4;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER, SCOPE_LOGIN_SCREEN},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    maxLength = 100
                                )
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
                         * A test policy.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Managed Profile Owner (Of Personally Owned Device)</li>
                         *      </ul>
                         *      </p>
                         *      <p>In addition, this policy can be set with scope <code>Login screen</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
                )
        )
    }

    @Test
    fun `Test StringPolicyDefinition with user, device and login screen scopes generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_DEVICE = 2;
                            private static final int SCOPE_LOGIN_SCREEN = 4;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER, SCOPE_DEVICE, SCOPE_LOGIN_SCREEN},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    maxLength = 100
                                )
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
                         * A test policy.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Managed Profile Owner (Of Personally Owned Device)</li>
                         *      </ul>
                         *      </p>
                         *      <p>In addition, this policy can be set with scope <code>Device</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Managed Profile Owner (Of Personally Owned Device)</li>
                         *      </ul>
                         *      </p>
                         *      <p>Moreover, this policy can be set with scope <code>Login screen</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
                )
        )
    }

    @Test
    fun `Test StringPolicyDefinition with applyOnFullUsersOnly generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.StringValidation;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;
                        import static android.Manifest.permission.TEST;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for string policy definition.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = TEST,
                                    applyOnFullUsersOnly = true,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                validation = @StringValidation(
                                    emptyStringAllowed = true,
                                    unprintableCharactersAllowed = true,
                                    pureWhitespaceAllowed = true,
                                    unstrippedStringAllowed = true,
                                    maxLength = 100
                                )
                            )
                            public static final String POLICY_FIELD = "policy";
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
                         * A test policy for string policy definition.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Supported users</td>
                         *    <td>Full users only (does not support profiles including work profiles).</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST}, or the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final java.lang.String POLICY_FIELD = "policy";
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test legacy StringPolicyDefinition with inline validation attributes generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    LEGACY_POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_DEVICE = 2;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for string policy definition using legacy inline validation attributes.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = DISALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                emptyStringAllowed = true,
                                unprintableCharactersAllowed = true,
                                pureWhitespaceAllowed = true,
                                unstrippedStringAllowed = true,
                                maxLength = 100
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
                         * A test policy for string policy definition using legacy inline validation attributes.
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
                         *          <li>Profile Owner on User 0</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Length max 100 characters</li>
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
    fun `Test StringPolicyDefinition with default StringValidation generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_DEVICE = 2;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy with default string validation.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_DEVICE},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = DISALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                )
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
                         * A test policy with default string validation.
                         * <br>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>Device</code> by the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
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
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
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
                )
        )
    }
}
