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
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class HeavyBitSetTest : DriverTest() {
    @Test
    fun `Check no usages of heavy BitSet`() {
        check(
            apiLint = "", // enabled
            // Ignore other issues.
            extraArguments =
                hiddenIssues(
                    Issues.ARRAY_RETURN,
                ),
            expectedIssues =
                """
                    src/android/pkg/MyClass.java:9: error: Type must not use heavy BitSet (field android.pkg.MyClass.bitset) [HeavyBitSet]
                    src/android/pkg/MyClass.java:11: error: Type must not use heavy BitSet (method android.pkg.MyClass.reverse(java.util.BitSet)) [HeavyBitSet]
                    src/android/pkg/MyClass.java:11: error: Type must not use heavy BitSet (parameter bitset in android.pkg.MyClass.reverse(java.util.BitSet bitset)) [HeavyBitSet]
                    src/android/pkg/MyClass.java:13: error: Type must not use heavy BitSet (method android.pkg.MyClass.array()) [HeavyBitSet]
                    src/android/pkg/MyClass.java:15: error: Type must not use heavy BitSet (method android.pkg.MyClass.collectionReturn()) [HeavyBitSet]
                    src/android/pkg/MyClass.java:16: error: Type must not use heavy BitSet (parameter c in android.pkg.MyClass.collectionExtends(java.util.Collection<? extends java.util.BitSet> c)) [HeavyBitSet]
                    src/android/pkg/MyClass.java:17: error: Type must not use heavy BitSet (parameter c in android.pkg.MyClass.collectionSuper(java.util.Collection<? super java.util.BitSet> c)) [HeavyBitSet]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            import androidx.annotation.NonNull;
                            import androidx.annotation.Nullable;
                            import java.util.BitSet;
                            import java.util.Collection;

                            public class MyClass {
                                @Nullable
                                public final BitSet bitset;
                                @Nullable
                                public BitSet reverse(@Nullable BitSet bitset) { return null; }
                                @NonNull
                                public BitSet[] array() { return null; }
                                @NonNull
                                public Collection<BitSet> collectionReturn() { return null; }
                                public void collectionExtends(@Nullable Collection<? extends BitSet> c) { }
                                public void collectionSuper(@Nullable Collection<? super BitSet> c) { }
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                    KnownSourceFiles.androidxNullableJavaSource,
                ),
        )
    }
}
