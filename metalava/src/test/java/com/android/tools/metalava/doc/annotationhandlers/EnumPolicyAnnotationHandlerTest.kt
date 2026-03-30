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
import com.android.tools.metalava.intDefAnnotationSource
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.testing.java
import org.junit.Test

class EnumPolicyAnnotationHandlerTest : DriverTest() {

    private val policyDefinitionSource =
        java(
            """
            package android.processor.devicepolicy;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.SOURCE)
            public @interface PolicyDefinition {
                int[] allowedScopes() default {};
                int affectedResource() default 0;
                String requiredPermission() default "";
                String requiredCrossUserPermission() default "";
                AllowedDpcTypes allowedDpcTypes() default @AllowedDpcTypes();
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface AllowedDpcTypes {
                int deviceOwner() default 2;
                int managedProfileOwnerOfOrganizationOwnedDevice() default 2;
                int managedProfileOwnerOfPersonalOwnedDevice() default 2;
                int unaffiliatedFullUserProfileOwner() default 2;
                int financedDeviceOwner() default 2;
                int profileOwnerOnUser0() default 2;
                int affiliatedFullUserProfileOwner() default 3;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface EnumResolutionMechanism {
                boolean custom() default false;
                int[] mostRestrictive() default {};
                boolean notCoexistable() default false;
            }
            """
        )

    private val enumPolicyDefinitionSource =
        java(
            """
            package android.processor.devicepolicy;

            import android.annotation.IntDef;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            public final class EnumPolicyValues {
                public static final int ENUM_POLICY_VALUE_1 = 1;
                public static final int ENUM_POLICY_VALUE_2 = 2;

                /**
                 * Possible values.
                 *
                 * @hide
                 */
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(
                        prefix = {"ENUM_POLICY_"},
                        value = {
                            ENUM_POLICY_VALUE_1,
                            ENUM_POLICY_VALUE_2,
                    })
                public @interface EnumPolicyValue {}
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface EnumPolicyDefinition {
                PolicyDefinition base();
                Class<?> intDef();
                EnumResolutionMechanism resolutionMechanism() default @EnumResolutionMechanism();
                int defaultValue() default 0;
            }
            """
        )

    private val androidManifestSource =
        java(
            """
            package android;
            public final class Manifest {
                public static final class permission {
                    public static final String TEST = "android.permission.TEST";
                    public static final String ENUM_TEST = "android.permission.ENUM_TEST";
                    public static final String BOOLEAN_TEST = "android.permission.BOOLEAN_TEST";
                    public static final String INTEGER_TEST = "android.permission.INTEGER_TEST";
                    public static final String LONG_TEST = "android.permission.LONG_TEST";
                    public static final String LIST_TEST = "android.permission.LIST_TEST";
                }
            }
            """
        )

    @Test
    fun `Test EnumPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    androidManifestSource,
                    policyDefinitionSource,
                    enumPolicyDefinitionSource,
                    intDefAnnotationSource,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.EnumPolicyDefinition;
                        import android.processor.devicepolicy.EnumPolicyValues.EnumPolicyValue;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.EnumResolutionMechanism;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                            private static final int DEFAULT_VALUE = 1;
                            @EnumPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = "android.permission.ENUM_TEST"
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
            stubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * <p>Policy Type: Enum</p>
                         * <ul>
                         *   <li>Allowed Scopes:
                         *    <ul>
                         *       <li>User</li>
                         *     </ul>
                         *   </li>
                         *   <li>Affected Resource: Device Wide</li>
                         *   <li>Required Permission: {@link android.Manifest.permission#ENUM_TEST android.permission.ENUM_TEST}</li>
                         *   <li>Resolution Mechanism: custom</li>
                         *   <li>Enum policy values:
                         *     <ul>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_1} (default)</li>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_2}</li>
                         *     </ul>
                         *   </li>
                         * </ul>
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
                    policyDefinitionSource,
                    enumPolicyDefinitionSource,
                    intDefAnnotationSource,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.EnumPolicyDefinition;
                        import android.processor.devicepolicy.EnumPolicyValues.EnumPolicyValue;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.EnumResolutionMechanism;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_PER_USER = 2;
                            private static final int DEFAULT_VALUE = 1;
                            @EnumPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_PER_USER,
                                    requiredPermission = "android.permission.DOES_NOT_EXIST"
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
                    src/test/pkg/TestPolicy.java:22: error: Cannot find permission field for android.permission.DOES_NOT_EXIST required by field TestPolicy.POLICY_FIELD (may be hidden or removed) [InvalidDevicePolicyAnnotation]
                    src/test/pkg/TestPolicy.java:22: error: Missing required field 'resolutionMechanism' inside field TestPolicy.POLICY_FIELD [InvalidDevicePolicyAnnotation]
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * <p>Policy Type: Enum</p>
                         * <ul>
                         *   <li>Allowed Scopes:
                         *    <ul>
                         *       <li>User</li>
                         *     </ul>
                         *   </li>
                         *   <li>Affected Resource: Per User</li>
                         *   <li>Required Permission: android.permission.DOES_NOT_EXIST</li>
                         *   <li>Resolution Mechanism: </li>
                         *   <li>Enum policy values:
                         *     <ul>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_1} (default)</li>
                         *        <li>{@link android.processor.devicepolicy.EnumPolicyValues#ENUM_POLICY_VALUE_2}</li>
                         *     </ul>
                         *   </li>
                         * </ul>
                         */
                        public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                )
        )
    }
}
