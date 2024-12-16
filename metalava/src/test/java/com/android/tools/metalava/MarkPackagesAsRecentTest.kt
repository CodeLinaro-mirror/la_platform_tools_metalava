/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Tests for the ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS functionality, which
 * replaces @Nullable/@NonNull with @RecentlyNullable/@RecentlyNonNull
 */
class MarkPackagesAsRecentTest : DriverTest() {

    @Test
    fun `Basic MarkPackagesAsRecentTest test`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.Nullable;
                    public class Foo {
                        @Nullable
                        public void method() { }
                    }
                    """
                    ),
                    androidxNullableSource
                ),
            extraArguments = arrayOf(ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS, "*"),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.RecentlyNullable
                    public void method() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `MarkPackagesAsRecent test with showAnnotation arguments`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.Nullable;
                    public class Foo {
                        @Nullable
                        public void method() { }
                    }
                    """
                    ),
                    androidxNullableSource
                ),
            extraArguments =
                arrayOf(
                    ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS,
                    "*",
                    ARG_SHOW_ANNOTATION,
                    "androidx.annotation.RestrictTo"
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    @androidx.annotation.RecentlyNullable
                    public void method() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }
}
