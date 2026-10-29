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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.signature
import org.junit.Test

@RequiresCapabilities(Capability.MULTIPLATFORM)
class MultiplatformCompatibilityTest : DriverTest() {
    @Test
    fun `Test unchanged multiplatform API`() {
        val api =
            listOf(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Common extends kotlin.Any {
                        ctor public Common();
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Android extends kotlin.Any {
                        ctor public Android();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Native extends kotlin.Any {
                        ctor public Native();
                      }
                    }
                    """
                ),
            )
        check(
            enableMultiplatform = true,
            multiplatformSignatureSource = api,
            multiplatformCompatibilityApi = api,
        )
    }

    @Test
    fun `Test breaking change in common source set`() {
        val unchangedApi =
            listOf(
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Android extends kotlin.Any {
                        ctor public Android();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Native extends kotlin.Any {
                        ctor public Native();
                      }
                    }
                    """
                ),
            )
        check(
            enableMultiplatform = true,
            multiplatformSignatureSource =
                unchangedApi +
                    signature(
                        "commonMain.txt",
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Common extends kotlin.Any {
                          }
                        }
                        """
                    ),
            multiplatformCompatibilityApi =
                unchangedApi +
                    signature(
                        "commonMain.txt",
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Common extends kotlin.Any {
                            ctor public Common();
                          }
                        }
                        """
                    ),
            expectedIssues =
                // The issue is reported for each source set because all source sets extend common.
                """
                MULTIPLATFORM-API-DIR/commonMain.txt:4: error: Source breaking change: Removed constructor test.pkg.Common() [RemovedMethod]
                """
                    .trimIndent()
        )
    }

    @Test
    fun `Test breaking change in non-common source set`() {
        val unchangedApi =
            listOf(
                signature(
                    "commonMain.txt",
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Common extends kotlin.Any {
                            ctor public Common();
                          }
                        }
                        """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Android extends kotlin.Any {
                        ctor public Android();
                      }
                    }
                    """
                ),
            )
        check(
            enableMultiplatform = true,
            multiplatformSignatureSource =
                unchangedApi +
                    signature(
                        "nativeMain.txt",
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Native extends kotlin.Any {
                          }
                        }
                        """
                    ),
            multiplatformCompatibilityApi =
                unchangedApi +
                    signature(
                        "nativeMain.txt",
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Native extends kotlin.Any {
                            ctor public Native();
                          }
                        }
                        """
                    ),
            expectedIssues =
                "MULTIPLATFORM-API-DIR/nativeMain.txt:4: error: Source breaking change: Removed constructor test.pkg.Native() [RemovedMethod]"
        )
    }

    @Test
    fun `Test removal of source set`() {
        val commonApi =
            signature(
                "commonMain.txt",
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Common extends kotlin.Any {
                    ctor public Common();
                  }
                }
                """
            )
        val androidApi =
            signature(
                "androidMain.txt",
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Android extends kotlin.Any {
                    ctor public Android();
                  }
                }
                """
            )
        val nativeApi =
            signature(
                "nativeMain.txt",
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Native extends kotlin.Any {
                    ctor public Native();
                  }
                }
                """
            )
        check(
            enableMultiplatform = true,
            multiplatformSignatureSource = listOf(commonApi, androidApi),
            multiplatformCompatibilityApi = listOf(commonApi, androidApi, nativeApi),
            expectedIssues =
                "MULTIPLATFORM-API-DIR/nativeMain.txt: error: Codebase for source set nativeMain has been removed [RemovedSourceSet]"
        )
    }

    @Test
    fun `Test addition of source set`() {
        val commonApi =
            signature(
                "commonMain.txt",
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Common extends kotlin.Any {
                    ctor public Common();
                  }
                }
                """
            )
        val androidApi =
            signature(
                "androidMain.txt",
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Android extends kotlin.Any {
                    ctor public Android();
                  }
                }
                """
            )
        val nativeApi =
            signature(
                "nativeMain.txt",
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Native extends kotlin.Any {
                    ctor public Native();
                  }
                }
                """
            )
        check(
            enableMultiplatform = true,
            multiplatformSignatureSource = listOf(commonApi, androidApi, nativeApi),
            multiplatformCompatibilityApi = listOf(commonApi, androidApi),
            expectedIssues =
                "multiplatform-signature-source/nativeMain.txt: info: Codebase for source set nativeMain has been added [AddedSourceSet]"
        )
    }
}
