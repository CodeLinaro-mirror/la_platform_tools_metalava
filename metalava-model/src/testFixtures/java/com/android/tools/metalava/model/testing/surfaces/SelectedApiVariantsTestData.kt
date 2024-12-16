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

package com.android.tools.metalava.model.testing.surfaces

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.signature

/** Encapsulates information about a [SelectableItem.selectedApiVariants] related test. */
data class SelectedApiVariantsTestData(
    /** The name of the test. */
    val name: String,

    /**
     * A list of signature files, such that a signature file that is a delta on top of another comes
     * after the one it extends.
     */
    val signatureFiles: List<TestFile>,

    /**
     * Java source files that contain a definition of the API provided in the signature files.
     *
     * These must be backwardly compatible with the API defined in [signatureFiles].
     */
    val javaSourceFiles: List<TestFile>,

    /**
     * The expected status of the [SelectableItem.selectedApiVariants] in the [Codebase] loaded from
     * [signatureFiles].
     */
    val expectedSelectedApiVariants: String,
) {
    val needsBase = signatureFiles.any { it.targetRelativePath.contains("base") }

    override fun toString() = name
}

/**
 * A list of [SelectedApiVariantsTestData] used in `metalava-model-testsuite` and `metalava` tests.
 *
 * This is provided because the testsuite and main metalava command have slightly different paths in
 * the handling of signature files. The testsuite tests check the behavior of the setting of
 * [SelectableItem.selectedApiVariants] when loading signature files in a test environment, the main
 * metalava tests will check the behavior when loading signature files for a previously released
 * API. Using the same test data for both simplifies maintenance.
 */
// Suppress issues in javadoc in the tests, e.g. unknown `@removed` tag.
@Suppress("JavadocDeclaration")
val selectedApiVariantsTestData =
    listOf(
        // A test for public and removed signature files.
        SelectedApiVariantsTestData(
            name = "public and removed",
            signatureFiles =
                listOf(
                    signature(
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Test {
                                ctor public Test();
                                field public int field;
                                method public void foo(int);
                              }
                              public static class Test.Nested {
                                ctor public Test.Nested();
                              }
                            }
                        """
                    ),
                    signature(
                        "removed.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Test {
                                field public int removed;
                              }
                              public static class Test.Removed {
                                ctor public Test.Removed();
                              }
                            }
                        """
                    ),
                ),
            javaSourceFiles =
                listOf(
                    java(
                        """
                            package test.pkg;

                            public class Test {
                                public int field;
                                /** @removed */
                                public int removed;
                                public void foo(int p) {}
                                public static class Nested {
                                }
                                /** @removed */
                                public static class Removed {
                                }
                            }
                        """
                    ),
                ),
            expectedSelectedApiVariants =
                """
                    package test.pkg - ApiVariantSet[main(CR)]
                      class test.pkg.Test - ApiVariantSet[main(CR)]
                        constructor test.pkg.Test() - ApiVariantSet[main(C)]
                        method test.pkg.Test.foo(int) - ApiVariantSet[main(C)]
                        field test.pkg.Test.field - ApiVariantSet[main(C)]
                        field test.pkg.Test.removed - ApiVariantSet[main(R)]
                        class test.pkg.Test.Nested - ApiVariantSet[main(C)]
                          constructor test.pkg.Test.Nested() - ApiVariantSet[main(C)]
                        class test.pkg.Test.Removed - ApiVariantSet[main(R)]
                          constructor test.pkg.Test.Removed() - ApiVariantSet[main(R)]
                """,
        ),
        // A test consisting of a base and extending API.
        SelectedApiVariantsTestData(
            name = "base",
            signatureFiles =
                listOf(
                    signature(
                        "base.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                ctor public Base();
                                field public int baseField;
                                method public void baseMethod(int);
                              }
                              public class Test {
                                ctor public Test();
                                field public int baseField;
                                method public void baseMethod(int);
                              }
                            }
                        """
                    ),
                    signature(
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Test {
                                field public int field;
                                method public void method(int);
                              }
                              public static class Test.Nested {
                                ctor public Test.Nested();
                              }
                            }
                        """
                    ),
                ),
            javaSourceFiles =
                listOf(
                    KnownSourceFiles.systemApiSource,
                    java(
                        """
                            package test.pkg;

                            public class Base {
                                public int baseField;
                                public void baseMethod(int p) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.SystemApi;

                            public class Test {
                                /** @hide */
                                @SystemApi
                                public int field;

                                /** @hide */
                                @SystemApi
                                public void method(int p) {}

                                /** @hide */
                                @SystemApi
                                public static class Nested {
                                }
                            }
                        """
                    ),
                ),
            expectedSelectedApiVariants =
                """
                    package test.pkg - ApiVariantSet[base(C),main(C)]
                      class test.pkg.Base - ApiVariantSet[base(C)]
                        constructor test.pkg.Base() - ApiVariantSet[base(C)]
                        method test.pkg.Base.baseMethod(int) - ApiVariantSet[base(C)]
                        field test.pkg.Base.baseField - ApiVariantSet[base(C)]
                      class test.pkg.Test - ApiVariantSet[base(C),main(C)]
                        constructor test.pkg.Test() - ApiVariantSet[base(C)]
                        method test.pkg.Test.baseMethod(int) - ApiVariantSet[base(C)]
                        method test.pkg.Test.method(int) - ApiVariantSet[main(C)]
                        field test.pkg.Test.baseField - ApiVariantSet[base(C)]
                        field test.pkg.Test.field - ApiVariantSet[main(C)]
                        class test.pkg.Test.Nested - ApiVariantSet[main(C)]
                          constructor test.pkg.Test.Nested() - ApiVariantSet[main(C)]
                """,
        )
    )
