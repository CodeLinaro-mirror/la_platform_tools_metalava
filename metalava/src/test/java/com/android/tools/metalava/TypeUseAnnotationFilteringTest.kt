/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class TypeUseAnnotationFilteringTest : DriverTest() {

    private fun runTypeUseTest(
        typeUseAnnotationVisibility: String,
        api: String,
        stubFiles: Array<TestFile>,
    ) {
        check(
            format =
                FileFormat.V5.copy(
                    kotlinNameTypeOrder = true,
                    includeTypeUseAnnotations = true,
                ),
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.nonNullSource,
                    KnownSourceFiles.nullableSource,
                    java(
                        """
                            package test.annotation;
                            import java.lang.annotation.ElementType;
                            import java.lang.annotation.Target;

                            @Target(ElementType.TYPE_USE)
                            $typeUseAnnotationVisibility @interface TypeUse {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.NonNull;
                            import android.annotation.Nullable;
                            import test.annotation.TypeUse;
                            public abstract class Class extends @TypeUse Exception implements Interface<@TypeUse @NonNull String, @Nullable Integer> {
                                public Class(@TypeUse float f) throws @TypeUse IllegalStateException {}
                                public @TypeUse long field;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.NonNull;
                            import android.annotation.Nullable;
                            import java.util.List;
                            import java.util.Map;
                            import test.annotation.TypeUse;
                            public interface Interface<T, @TypeUse S> extends Map.Entry<@TypeUse @NonNull T, @Nullable S>, @TypeUse Runnable {
                                @TypeUse @NonNull T method(@TypeUse int p) throws @TypeUse IllegalStateException;
                            }
                        """
                    ),
                ),
            api = api,
            stubFiles = stubFiles,
        )
    }

    @Test
    fun `Keep type use annotations`() {
        // TODO: The type use annotations seem to be in the wrong place.
        runTypeUseTest(
            typeUseAnnotationVisibility = "public",
            api =
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) public @interface TypeUse {
                      }
                    }
                    package test.pkg {
                      public abstract class Class extends java.lang.@test.annotation.TypeUse Exception implements test.pkg.Interface<java.lang.@test.annotation.TypeUse String,java.lang.Integer?> {
                        ctor public Class(@test.annotation.TypeUse _: @test.annotation.TypeUse float) throws java.lang.IllegalStateException;
                        field @test.annotation.TypeUse public field: @test.annotation.TypeUse long;
                      }
                      public interface Interface<T, S> extends java.util.Map.Entry<@test.annotation.TypeUse T,S?> java.lang.@test.annotation.TypeUse Runnable {
                        method @test.annotation.TypeUse public method(@test.annotation.TypeUse _: @test.annotation.TypeUse int): @test.annotation.TypeUse T throws java.lang.IllegalStateException;
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public abstract class Class extends java.lang.Exception implements test.pkg.Interface<java.lang.String,java.lang.Integer> {
                            public Class(@test.annotation.TypeUse float f) throws java.lang.IllegalStateException { throw new RuntimeException("Stub!"); }
                            @test.annotation.TypeUse public long field;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public interface Interface<T, S> extends java.util.Map.Entry<T,S>, java.lang.Runnable {
                            @android.annotation.NonNull
                            @test.annotation.TypeUse
                            public T method(@test.annotation.TypeUse int p) throws java.lang.IllegalStateException;
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Discard type use annotations`() {
        runTypeUseTest(
            typeUseAnnotationVisibility = "",
            api =
                """
                    // Signature format: 5.0
                    // - include-type-use-annotations=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public abstract class Class extends java.lang.Exception implements test.pkg.Interface<java.lang.String,java.lang.Integer?> {
                        ctor public Class(_: float) throws java.lang.IllegalStateException;
                        field public field: long;
                      }
                      public interface Interface<T, S> extends java.util.Map.Entry<T,S?> java.lang.Runnable {
                        method public method(_: int): T throws java.lang.IllegalStateException;
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public abstract class Class extends java.lang.Exception implements test.pkg.Interface<java.lang.String,java.lang.Integer> {
                            public Class(float f) throws java.lang.IllegalStateException { throw new RuntimeException("Stub!"); }
                            public long field;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public interface Interface<T, S> extends java.util.Map.Entry<T,S>, java.lang.Runnable {
                            @android.annotation.NonNull
                            public T method(int p) throws java.lang.IllegalStateException;
                            }
                        """
                    ),
                ),
        )
    }
}
