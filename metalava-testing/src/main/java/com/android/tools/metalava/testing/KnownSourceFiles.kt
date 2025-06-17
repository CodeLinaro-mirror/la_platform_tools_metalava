/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.testing

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles

object KnownSourceFiles {

    val notTypeUseNonNullSource: TestFile =
        TestFiles.java(
            """
                package not.type.use;
                public @interface NonNull {
                }
            """
        )

    val notTypeUseNullableSource: TestFile =
        TestFiles.java(
            """
                package not.type.use;
                public @interface Nullable {
                }
            """
        )

    val typeUseOnlyNonNullSource: TestFile =
        TestFiles.java(
            """
                package type.use.only;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.*;
                @Target(TYPE_USE)
                public @interface NonNull {
                }
            """
        )

    val typeUseOnlyNullableSource: TestFile =
        TestFiles.java(
            """
                package type.use.only;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.*;
                @Target(TYPE_USE)
                public @interface Nullable {
                }
            """
        )

    val nonNullSource: TestFile =
        TestFiles.java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    /**
     * Denotes that a parameter, field or method return value can never be null.
     * @paramDoc This value must never be {@code null}.
     * @returnDoc This value will never be {@code null}.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(CLASS)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface NonNull {
    }
    """
        )

    val nullableSource: TestFile =
        TestFiles.java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    /**
     * Denotes that a parameter, field or method return value can be null.
     * @paramDoc This value may be {@code null}.
     * @returnDoc This value may be {@code null}.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(CLASS)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface Nullable {
    }
    """
        )

    val libcoreNonNullSource: TestFile =
        TestFiles.java(
            """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface NonNull {
    }
    """
        )

    val libcoreNullableSource: TestFile =
        TestFiles.java(
            """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface Nullable {
    }
    """
        )

    /**
     * The version of the Jetbrains nullness annotations used by metalava is not type-use, but the
     * latest version is.
     */
    val jetbrainsNullableTypeUseSource: TestFile =
        TestFiles.java(
            """
    package org.jetbrains.annotations;
    @java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.LOCAL_VARIABLE, java.lang.annotation.ElementType.TYPE_USE })
    public @interface Nullable {}
            """
        )

    /** TYPE_USE version of [com.android.tools.metalava.intRangeAnnotationSource] */
    val intRangeTypeUseSource =
        java(
            """
        package androidx.annotation;
        import java.lang.annotation.*;
        import static java.lang.annotation.ElementType.*;
        import static java.lang.annotation.RetentionPolicy.SOURCE;
        @Retention(SOURCE)
        @Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE,TYPE_USE})
        public @interface IntRange {
            long from() default Long.MIN_VALUE;
            long to() default Long.MAX_VALUE;
        }
        """
        )

    val systemApiSource: TestFile =
        TestFiles.java(
            """
                package android.annotation;
                import static java.lang.annotation.ElementType.*;
                import java.lang.annotation.*;
                @Target({TYPE, FIELD, METHOD, CONSTRUCTOR, ANNOTATION_TYPE, PACKAGE})
                @Retention(RetentionPolicy.SOURCE)
                public @interface SystemApi {
                    enum Client {
                        /**
                         * Specifies that the intended clients of a SystemApi are privileged apps.
                         * This is the default value for {@link #client}.
                         */
                        PRIVILEGED_APPS,

                        /**
                         * Specifies that the intended clients of a SystemApi are used by classes in
                         * <pre>BOOTCLASSPATH</pre> in mainline modules. Mainline modules can also expose
                         * this type of system APIs too when they're used only by the non-updatable
                         * platform code.
                         */
                        MODULE_LIBRARIES,

                        /**
                         * Specifies that the system API is available only in the system server process.
                         * Use this to expose APIs from code loaded by the system server process <em>but</em>
                         * not in <pre>BOOTCLASSPATH</pre>.
                         */
                        SYSTEM_SERVER
                    }

                    /**
                     * The intended client of this SystemAPI.
                     */
                    Client client() default android.annotation.SystemApi.Client.PRIVILEGED_APPS;
                }
            """
        )

    val intRangeAnnotationSource: TestFile =
        TestFiles.java(
            """
                package android.annotation;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.*;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                @Retention(SOURCE)
                @Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})
                public @interface IntRange {
                    /** Smallest value, inclusive */
                    long from() default Long.MIN_VALUE;
                    /** Largest value, inclusive */
                    long to() default Long.MAX_VALUE;
                }
            """
        )

    val floatRangeAnnotationSource: TestFile =
        TestFiles.java(
            """
                package android.annotation;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.*;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                @Retention(SOURCE)
                @Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})
                public @interface FloatRange {
                    /** Smallest value. Whether it is inclusive or not is determined
                     * by {@link #fromInclusive} */
                    double from() default Double.NEGATIVE_INFINITY;
                    /** Largest value. Whether it is inclusive or not is determined
                     * by {@link #toInclusive} */
                    double to() default Double.POSITIVE_INFINITY;
                    /** Whether the from value is included in the range */
                    boolean fromInclusive() default true;
                    /** Whether the to value is included in the range */
                    boolean toInclusive() default true;
                }
            """
        )

    val restrictToSource: TestFile =
        TestFiles.kotlin(
                """
                    package androidx.annotation

                    import androidx.annotation.RestrictTo.Scope
                    import java.lang.annotation.ElementType.*

                    @MustBeDocumented
                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.ANNOTATION_CLASS,
                        AnnotationTarget.CLASS,
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.PROPERTY_GETTER,
                        AnnotationTarget.PROPERTY_SETTER,
                        AnnotationTarget.CONSTRUCTOR,
                        AnnotationTarget.FIELD,
                        AnnotationTarget.FILE
                    )
                    // Needed due to Kotlin's lack of PACKAGE annotation target
                    // https://youtrack.jetbrains.com/issue/KT-45921
                    @Suppress("DEPRECATED_JAVA_ANNOTATION")
                    @java.lang.annotation.Target(ANNOTATION_TYPE, TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE)
                    annotation class RestrictTo(vararg val value: Scope) {
                        enum class Scope {
                            LIBRARY,
                            LIBRARY_GROUP,
                            LIBRARY_GROUP_PREFIX,
                            @Deprecated("Use LIBRARY_GROUP_PREFIX instead.")
                            GROUP_ID,
                            TESTS,
                            SUBCLASSES,
                        }
                    }
                """
            )
            .indented()

    val sdkConstantSource: TestFile =
        TestFiles.java(
                """
                    package android.annotation;
                    import java.lang.annotation.*;
                    /** @hide */
                    @Target({ ElementType.FIELD })
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface SdkConstant {
                        enum SdkConstantType {
                            ACTIVITY_INTENT_ACTION, BROADCAST_INTENT_ACTION, SERVICE_ACTION, INTENT_CATEGORY, FEATURE
                        }
                        SdkConstantType value();
                    }
                """
            )
            .indented()
}
