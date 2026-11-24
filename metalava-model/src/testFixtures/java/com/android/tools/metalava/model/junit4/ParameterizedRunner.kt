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

package com.android.tools.metalava.model.junit4

import java.text.MessageFormat
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.ParentRunner
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * A simple [ParentRunner] that will use [parametersRunnerFactory] to create a [Runner] for each of
 * the [TestArguments.argumentSets] returned from [computeTestArguments].
 */
abstract class ParameterizedRunner<A : Any>(
    private val testClass: TestClass,
    private val parametersRunnerFactory: ParametersRunnerFactory,
) : ParentRunner<Runner>(testClass) {

    /** The set of test arguments to use. */
    data class TestArguments<A : Any>(
        /**
         * The pattern describing how to construct the test name suffix for a set of arguments.
         *
         * See [Parameters.name] for more details.
         */
        val pattern: String,

        /**
         * The sets of arguments.
         *
         * Each entry can be either an `Array<Any>` (for multiple arguments) or any other value (for
         * a single argument).
         */
        val argumentSets: List<A>,
    )

    /** Compute [TestArguments] for [testClass]. */
    abstract fun computeTestArguments(testClass: TestClass): TestArguments<A>

    /** Create the runners lazily. */
    private val runners: List<Runner> by
        lazy(LazyThreadSafetyMode.NONE) {
            val testArguments = computeTestArguments(testClass)

            // Add brackets around the pattern.
            val pattern = "[${testArguments.pattern}]"

            testArguments.argumentSets.map { argumentSet ->
                val name = MessageFormat.format(pattern, argumentSet)
                val testWithParameters = TestWithParameters(name, testClass, listOf(argumentSet))
                parametersRunnerFactory.createRunnerForTestWithParameters(testWithParameters)
            }
        }

    override fun getChildren(): List<Runner> {
        return runners
    }

    override fun describeChild(child: Runner): Description = child.description

    override fun runChild(child: Runner, notifier: RunNotifier?) {
        child.run(notifier)
    }
}
