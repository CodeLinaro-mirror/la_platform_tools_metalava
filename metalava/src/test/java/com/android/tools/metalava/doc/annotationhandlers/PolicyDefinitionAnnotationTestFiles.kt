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

import com.android.tools.metalava.testing.java

object PolicyDefinitionAnnotationTestFiles {

    val ANDROID_MANIFEST_SOURCE =
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
                    public static final String MANAGE_DEVICE_POLICY_ACROSS_USERS = "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS";
                }
            }
            """
        )

    val ENUM_POLICY_DEFINITION_SOURCE =
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

    val POLICY_DEFINITION_SOURCE =
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
                boolean applyOnFullUsersOnly() default false;
                AllowedDpcTypes allowedDpcTypes();
                AllowedRoles allowedRoles() default @AllowedRoles;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface AllowedDpcTypes {
                public static final int ALLOWED = 1;
                public static final int DISALLOWED = 2;
                public static final int ALLOWED_WHEN_AFFILIATED = 3;

                public int deviceOwner();
                public int managedProfileOwnerOfOrganizationOwnedDevice();
                public int managedProfileOwnerOfPersonalOwnedDevice();
                public int fullUserProfileOwner();
                public int financedDeviceOwner() default DISALLOWED;
                public int profileOwnerOnUser0() default DISALLOWED;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface AllowedRoles {
                public static final int ALLOWED = 1;
                public static final int DISALLOWED = 2;

                public int deviceController() default DISALLOWED;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface EnumResolutionMechanism {
                boolean custom() default false;
                int[] mostRestrictive() default {};
                boolean notCoexistable() default false;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface IntegerResolutionMechanism {
                boolean custom() default false;
                boolean notCoexistable() default false;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface IntegerValidation {
                int minValue() default Integer.MIN_VALUE;
                int maxValue() default Integer.MAX_VALUE;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface IntegerPolicyDefinition {
                PolicyDefinition base();
                IntegerValidation validation() default @IntegerValidation;
                IntegerResolutionMechanism resolutionMechanism();
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface LongResolutionMechanism {
                boolean custom() default false;
                boolean notCoexistable() default false;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface LongValidation {
                long minValue() default Long.MIN_VALUE;
                long maxValue() default Long.MAX_VALUE;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface LongPolicyDefinition {
                PolicyDefinition base();
                LongValidation validation() default @LongValidation;
                LongResolutionMechanism resolutionMechanism();
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface ListResolutionMechanism {
                boolean custom() default false;
                boolean union() default false;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface ListOfStringPolicyDefinition {
                PolicyDefinition base();
                boolean emptyListAllowed() default false;
                boolean emptyStringAllowed() default false;
                boolean unprintableCharactersAllowed() default false;
                boolean pureWhitespaceAllowed() default false;
                boolean unstrippedStringAllowed() default false;
                ListResolutionMechanism resolutionMechanism();
                int maxListLength() default 10000;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface StringValidation {
                boolean emptyStringAllowed() default false;
                boolean unprintableCharactersAllowed() default false;
                boolean pureWhitespaceAllowed() default false;
                boolean unstrippedStringAllowed() default false;
                int maxLength() default Integer.MAX_VALUE;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface StringPolicyDefinition {
                PolicyDefinition base();
                StringValidation validation() default @StringValidation;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface PackagePolicyDefinition {
                PolicyDefinition base();
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface ListOfPackagePolicyDefinition {
                PolicyDefinition base();
                boolean emptyListAllowed() default false;
                ListResolutionMechanism resolutionMechanism();
                int maxListLength() default 10000;
            }
            """
        )

    val LEGACY_POLICY_DEFINITION_SOURCE =
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
                boolean applyOnFullUsersOnly() default false;
                AllowedDpcTypes allowedDpcTypes();
                AllowedRoles allowedRoles() default @AllowedRoles;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface AllowedDpcTypes {
                public static final int ALLOWED = 1;
                public static final int DISALLOWED = 2;
                public static final int ALLOWED_WHEN_AFFILIATED = 3;

                public int deviceOwner();
                public int managedProfileOwnerOfOrganizationOwnedDevice();
                public int managedProfileOwnerOfPersonalOwnedDevice();
                public int fullUserProfileOwner();
                public int financedDeviceOwner() default DISALLOWED;
                public int profileOwnerOnUser0() default DISALLOWED;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface AllowedRoles {
                public static final int ALLOWED = 1;
                public static final int DISALLOWED = 2;

                public int deviceController() default DISALLOWED;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface StringPolicyDefinition {
                PolicyDefinition base();
                boolean emptyStringAllowed() default false;
                boolean unprintableCharactersAllowed() default false;
                boolean pureWhitespaceAllowed() default false;
                boolean unstrippedStringAllowed() default false;
                int maxLength() default Integer.MAX_VALUE;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface IntegerResolutionMechanism {
                boolean custom() default false;
                boolean notCoexistable() default false;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface IntegerPolicyDefinition {
                PolicyDefinition base();
                int minValue() default Integer.MIN_VALUE;
                int maxValue() default Integer.MAX_VALUE;
                IntegerResolutionMechanism resolutionMechanism();
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface LongResolutionMechanism {
                boolean custom() default false;
                boolean notCoexistable() default false;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface LongPolicyDefinition {
                PolicyDefinition base();
                long minValue() default Long.MIN_VALUE;
                long maxValue() default Long.MAX_VALUE;
                LongResolutionMechanism resolutionMechanism();
            }
            """
        )
}
