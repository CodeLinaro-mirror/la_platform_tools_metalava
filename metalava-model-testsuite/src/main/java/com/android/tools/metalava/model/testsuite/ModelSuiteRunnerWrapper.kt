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

package com.android.tools.metalava.model.testsuite

import java.util.ServiceLoader

/** Allows a project that runs the test suite to wrap any [ModelSuiteRunner]s that it runs. */
interface ModelSuiteRunnerWrapper {
    /**
     * Wrap [runner].
     *
     * The returned [ModelSuiteRunner] can augment [runner] with additional information.
     */
    fun wrap(runner: ModelSuiteRunner): ModelSuiteRunner

    companion object {
        /** The default no-op wrapper that just returns the original. */
        private val NOOP =
            object : ModelSuiteRunnerWrapper {
                override fun wrap(runner: ModelSuiteRunner) = runner
            }

        /** Select the wrapper to use by trying to load the service, defaults to [NOOP]. */
        fun selectWrapper(): ModelSuiteRunnerWrapper {
            val wrapperLoader = ServiceLoader.load(ModelSuiteRunnerWrapper::class.java)
            return wrapperLoader.singleOrNull() ?: NOOP
        }
    }
}
