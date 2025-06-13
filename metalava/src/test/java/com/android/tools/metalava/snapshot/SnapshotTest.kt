/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.snapshot

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.CheckerContext
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.snapshot.CodebaseSnapshotTaker
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import kotlin.test.assertNull
import org.junit.Test

/** Test [CodebaseSnapshotTaker] use within the main metalava code. */
class SnapshotTest : DriverTest() {
    private fun CheckerContext.takeSnapshotOfPublicApi(): Codebase {
        val apiPredicateConfig = ApiPredicate.Config()
        val apiFilters =
            ApiFilters(
                ApiType.PUBLIC_API.getEmitFilter(apiPredicateConfig),
                ApiType.PUBLIC_API.getReferenceFilter(apiPredicateConfig)
            )
        val factory: (DelegatedVisitor) -> ItemVisitor = {
            FilteringApiVisitor(
                delegate = it,
                preFiltered = false,
                apiFilters = apiFilters,
            )
        }

        val snapshot =
            CodebaseSnapshotTaker.takeSnapshot(
                codebase,
                definitionVisitorFactory = factory,
                referenceVisitorFactory = factory,
            )
        return snapshot
    }

    @Test
    fun `Test reference to hidden class`() {
        check(
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.sdkConstantSource,
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SdkConstant;
                            import android.annotation.SdkConstant.SdkConstantType;
                            public class Foo {
                                @SdkConstant(SdkConstantType.SERVICE_ACTION)
                                public static final String CONSTANT = "something";
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        field public static final String CONSTANT = "something";
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            @android.annotation.SdkConstant(android.annotation.SdkConstant.SdkConstantType.SERVICE_ACTION) public static final java.lang.String CONSTANT = "something";
                            }
                        """
                    )
                ),
        ) {
            // Take a snapshot of the public API.
            val snapshot = takeSnapshotOfPublicApi()

            // Attempt to resolve a class which was in the original codebase but is not in the
            // snapshot because it is hidden. It should succeed but return null.
            val resolved = snapshot.resolveClass("android.annotation.SdkConstant.SdkConstantType")
            assertNull(resolved)
        }
    }
}
