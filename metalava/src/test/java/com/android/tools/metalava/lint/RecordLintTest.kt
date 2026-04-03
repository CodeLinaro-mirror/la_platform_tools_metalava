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
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class RecordLintTest : DriverTest() {
    @Test
    fun `Test using java-lang-Record class`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/UsingJavaLangRecord.java:5: error: Implemented interface of class android.pkg.UsingJavaLangRecord contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:5: error: Super class of class android.pkg.UsingJavaLangRecord contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:6: error: Type of field android.pkg.UsingJavaLangRecord.field contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:7: error: Return type of method android.pkg.UsingJavaLangRecord.method(Record) contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:7: error: Type of parameter record in android.pkg.UsingJavaLangRecord.method(Record record) contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                """,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package android.pkg;

                            public class SuperClass<T> {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            public interface Interface<T> {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            import type.use.only.NonNull;

                            public class UsingJavaLangRecord<T extends Record> extends SuperClass<Record> implements Interface<Record> {
                                public final @NonNull Record field;
                                public @NonNull Record method(@NonNull Record record) {return record;}
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            public record Test(int a) {
                            }
                        """
                    ),
                )
        )
    }
}
