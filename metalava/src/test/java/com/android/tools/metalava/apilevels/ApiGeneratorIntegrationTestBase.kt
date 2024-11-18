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

package com.android.tools.metalava.apilevels

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.metalava.DriverTest
import java.io.File
import org.junit.Assert
import org.junit.BeforeClass

abstract class ApiGeneratorIntegrationTestBase : DriverTest() {
    companion object {
        // A version higher than SdkVersionInfo.HIGHEST_KNOWN_API.
        // 57 was chosen because previously ApiConstraint used a bit vector requiring that an API
        // version had to be between 1..61.
        internal const val MAGIC_VERSION_INT = 57
        internal const val MAGIC_VERSION_STR = MAGIC_VERSION_INT.toString()
        private val ABOVE_HIGHEST_API = ApiConstraint.above(SdkVersionInfo.HIGHEST_KNOWN_API)

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            assert(ABOVE_HIGHEST_API.includes(MAGIC_VERSION_INT))
        }

        internal val oldSdkJars by
            lazy(LazyThreadSafetyMode.NONE) {
                File("../../../prebuilts/tools/common/api-versions").apply {
                    if (!isDirectory) {
                        Assert.fail("prebuilts for old sdk jars not found: $this")
                    }
                }
            }

        internal val platformJars by
            lazy(LazyThreadSafetyMode.NONE) {
                File("../../../prebuilts/sdk").apply {
                    if (!isDirectory) {
                        Assert.fail("prebuilts for platform jars not found: $this")
                    }
                }
            }

        internal val extensionSdkJars by
            lazy(LazyThreadSafetyMode.NONE) {
                platformJars.resolve("extensions").apply {
                    if (!isDirectory) {
                        Assert.fail("prebuilts for extension jars not found: $this")
                    }
                }
            }
    }
}
