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

package com.android.tools.metalava.cli.util

import com.android.tools.metalava.cli.common.ARG_TRACE_FILE
import com.android.tools.metalava.testing.TemporaryFolderOwner
import java.nio.file.Files
import org.junit.Assert.assertTrue

/**
 * If [enableTracing] is true, returns [TestOptionGroup] with CLI arguments to write a trace file,
 * and checks that the file is written to.
 *
 * If [enableTracing] is false, returns a no-op [TestOptionGroup].
 */
fun TemporaryFolderOwner.tracingOptions(enableTracing: Boolean): TestOptionGroup {
    val traceFile =
        if (enableTracing) {
            newFile("trace.perfetto-trace")
        } else {
            null
        }

    return object : TestOptionGroup() {
        override val args: Array<String> =
            if (traceFile != null) {
                arrayOf(ARG_TRACE_FILE, traceFile.path)
            } else {
                emptyArray()
            }

        override fun check() {
            if (traceFile != null) {
                assertTrue("Trace file exists", traceFile.exists())
                assertTrue("Trace file is not empty", Files.size(traceFile.toPath()) > 0)
            }
        }
    }
}
