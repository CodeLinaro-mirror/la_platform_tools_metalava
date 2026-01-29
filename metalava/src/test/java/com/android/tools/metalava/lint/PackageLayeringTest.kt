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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.testing.java
import org.junit.Test

class PackageLayeringTest : DriverTest() {

    @Test
    fun `Check package layering`() {
        check(
            apiLint = "", // enabled
            // Ignore other issues.
            extraArguments = arrayOf(ARG_HIDE, "ArrayReturn"),
            expectedIssues =
                """
                    src/android/content/MyClass1.java:12: warning: Field type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                    src/android/content/MyClass1.java:18: warning: Method parameter type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                    src/android/content/MyClass1.java:18: warning: Method return type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                    src/android/content/MyClass1.java:20: warning: Method parameter type `android.view.View` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                    src/android/content/MyClass1.java:22: warning: Method parameter type `A` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                    src/android/content/MyClass1.java:22: warning: Method parameter type `java.util.List<android.view.View>` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                    src/android/content/MyClass1.java:22: warning: Method return type `android.view.View[]` violates package layering: nothing in `package android.content` should depend on `package android.view` [PackageLayering]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.content;

                            import android.graphics.drawable.Drawable;
                            import android.graphics.Bitmap;
                            import android.view.View;
                            import androidx.annotation.NonNull;
                            import androidx.annotation.Nullable;
                            import java.util.List;

                            public class MyClass1 {
                                @Nullable
                                public final View view = null;
                                @Nullable
                                public final Drawable drawable = null;
                                @Nullable
                                public final Bitmap bitmap = null;
                                @Nullable
                                public View ok(@Nullable View view, @Nullable Drawable drawable) { return null; }
                                @Nullable
                                public Bitmap wrong1(@Nullable View view, @Nullable Bitmap bitmap) { return null; }
                                @NonNull
                                public <A extends View> View[] wrong2(@Nullable A view, @NonNull List<View> views) { return null; }
                            }
                        """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource,
                ),
        )
    }
}
