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

/**
 * Encapsulates a set of CLI options for a test and validations to run after the test based on the
 * option values.
 */
abstract class TestOptionGroup {
    /** The CLI options for the test. */
    abstract val args: Array<String>

    /** Validation to run after the test command has executed. */
    abstract fun check()
}
