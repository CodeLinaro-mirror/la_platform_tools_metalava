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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.reporter.Issues
import org.junit.Test

class RetentionCompatibilityCheckTest : DriverTest() {

    @Test
    fun `Don't throw compatibility error when annotation retentions are equivalent - source retention`() {
        check(
            expectedIssues = "",
            expectedFail = "",
            extraArguments = arrayOf(ARG_ERROR, Issues.CHANGED_ANNOTATION_RETENTION.name),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface RestrictTo {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) public @interface RestrictTo {
                  }
                }
                """
        )
    }

    @Test
    fun `Don't throw compatibility error when annotation retentions are equivalent - class vs binary retention`() {
        check(
            expectedIssues = "",
            expectedFail = "",
            extraArguments = arrayOf(ARG_ERROR, Issues.CHANGED_ANNOTATION_RETENTION.name),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface RestrictTo {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface RestrictTo {
                  }
                }
                """
        )
    }

    @Test
    fun `Don't throw compatibility error when annotation retentions are equivalent - runtime retention`() {
        check(
            expectedIssues = "",
            expectedFail = "",
            extraArguments = arrayOf(ARG_ERROR, Issues.CHANGED_ANNOTATION_RETENTION.name),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface RestrictTo {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.RUNTIME) public @interface RestrictTo {
                  }
                }
                """
        )
    }

    @Test
    fun `Should throw compatibility error when annotation retentions are different - source vs binary retention`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.RestrictTo incompatibly changed its retention from SOURCE to BINARY [ChangedAnnotationRetention]
            """
                    .trimIndent(),
            expectedFail =
                "Aborting: Found compatibility problems checking the public API (TESTROOT/project/load-api.txt) against the API in TESTROOT/project/released-api.txt",
            extraArguments = arrayOf(ARG_ERROR, Issues.CHANGED_ANNOTATION_RETENTION.name),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public @interface RestrictTo {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface RestrictTo {
                  }
                }
                """
        )
    }

    @Test
    fun `Should throw compatibility error when annotation retentions are different - class vs runtime retention`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.RestrictTo incompatibly changed its retention from CLASS to RUNTIME [ChangedAnnotationRetention]
            """
                    .trimIndent(),
            expectedFail =
                "Aborting: Found compatibility problems checking the public API (TESTROOT/project/load-api.txt) against the API in TESTROOT/project/released-api.txt",
            extraArguments = arrayOf(ARG_ERROR, Issues.CHANGED_ANNOTATION_RETENTION.name),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface RestrictTo {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.RUNTIME) public @interface RestrictTo {
                  }
                }
                """
        )
    }
}
