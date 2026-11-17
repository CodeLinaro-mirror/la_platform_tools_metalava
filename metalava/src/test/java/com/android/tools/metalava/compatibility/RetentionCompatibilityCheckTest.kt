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
import org.junit.Test

class RetentionCompatibilityCheckTest : DriverTest() {

    @Test
    fun `Don't throw compatibility error when annotation retentions are equivalent - source retention`() {
        check(
            expectedIssues = "",
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
    fun `Don't throw compatibility error when annotation retention becomes less restrictive - class to runtime`() {
        check(
            expectedIssues = "",
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

    @Test
    fun `Don't throw compatibility error when annotation retention becomes less restrictive - source to runtime`() {
        check(
            expectedIssues = "",
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
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.RUNTIME) public @interface RestrictTo {
                  }
                }
                """
        )
    }

    @Test
    fun `Should throw compatibility error when annotation retention becomes more restrictive - binary vs source retention`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.RestrictTo incompatibly changed its retention from BINARY to SOURCE [ChangedAnnotationRetention]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.BINARY) public @interface RestrictTo {
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
    fun `Should throw compatibility error when annotation retentions become more restrictive - runtime vs class retention`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.RestrictTo incompatibly changed its retention from RUNTIME to CLASS [ChangedAnnotationRetention]
            """
                    .trimIndent(),
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
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.CLASS) public @interface RestrictTo {
                  }
                }
                """
        )
    }

    @Test
    fun `Should throw compatibility error when annotation retentions become more restrictive - runtime vs source retention`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.RestrictTo incompatibly changed its retention from RUNTIME to SOURCE [ChangedAnnotationRetention]
            """
                    .trimIndent(),
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
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) public @interface RestrictTo {
                  }
                }
                """
        )
    }
}
