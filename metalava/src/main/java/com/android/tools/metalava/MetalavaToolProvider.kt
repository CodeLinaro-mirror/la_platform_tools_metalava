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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ExecutionEnvironment
import java.io.PrintWriter
import java.util.spi.ToolProvider

/**
 * Supports invoking Metalava via the standard [ToolProvider] SPI.
 *
 * Locate with `ToolProvider.findFirst("metalava")` or [java.util.ServiceLoader].
 */
class MetalavaToolProvider : ToolProvider {
    override fun name(): String = PROGRAM_NAME

    override fun run(out: PrintWriter, err: PrintWriter, vararg args: String): Int {
        val executionEnvironment =
            ExecutionEnvironment(
                stdout = out,
                stderr = err,
            )
        return Driver.run(
            executionEnvironment = executionEnvironment,
            args = arrayOf(*args),
        )
    }
}
