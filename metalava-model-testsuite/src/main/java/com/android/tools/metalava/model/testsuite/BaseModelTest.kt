/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Codebase
import java.util.ServiceLoader
import kotlin.test.fail
import org.junit.runners.Parameterized

/**
 * Base class for tests that verify the behavior of model implementations.
 *
 * This is parameterized by the runners as even though the tests are run in different projects the
 * test results are collated and reported together. Having the runner in the test name makes it
 * easier to differentiate them.
 *
 * Note: In the top-level test report produced by Gradle it appears to just display whichever test
 * ran last. However, the test reports in the model implementation projects do list each run
 * separately. If this is an issue then the [ModelSuiteRunner] implementations could all be moved
 * into the same project and run tests against them all at the same time.
 */
abstract class BaseModelTest(private val runner: ModelSuiteRunner) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun runners(): Iterable<ModelSuiteRunner> {
            val loader = ServiceLoader.load(ModelSuiteRunner::class.java)
            val list = loader.toList()
            if (list.isEmpty()) {
                fail("No runners found")
            }
            return list
        }
    }

    /**
     * Create a [Codebase] from one of the supplied [signature] or [source] files and then run a
     * test on that [Codebase].
     *
     * This must be called with [signature] and [source] contents that are equivalent so that the
     * test can have the same behavior on models that consume the different formats. Subclasses of
     * this must implement this method consuming at least one of them to create a [Codebase] on
     * which the test is run.
     */
    fun createCodebaseAndRun(
        signature: String,
        source: TestFile,
        test: (Codebase) -> Unit,
    ) {
        runner.createCodebaseAndRun(signature, source, test)
    }
}
