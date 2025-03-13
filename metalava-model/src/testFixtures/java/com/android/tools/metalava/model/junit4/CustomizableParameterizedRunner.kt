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

package com.android.tools.metalava.model.junit4

import org.junit.runner.Runner
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * Extends [ParameterizedRunner] to combine parameters from the test class (specified using
 * [Parameters] and additional arguments from [argumentsProvider].
 *
 * @param clazz the test class to run.
 * @param argumentsProvider provider of [TestArguments] used by this runner. Is also passed any
 *   additional parameters (provided by the test class using the standard [Parameterized]
 *   mechanism), if any. They can be filtered and/or combined in some way with parameters provides
 *   by this.
 * @param parametersRunnerFactory factory for creating a [Runner] from a [TestWithParameters].
 */
abstract class CustomizableParameterizedRunner(
    clazz: Class<*>,
    private val argumentsProvider: (TestClass, List<Array<Any>>?) -> TestArguments,
    parametersRunnerFactory: ParametersRunnerFactory =
        BlockJUnit4ClassRunnerWithParametersFactory(),
) : ParameterizedRunner(TestClass(clazz), parametersRunnerFactory) {

    override fun computeTestArguments(testClass: TestClass): ParameterizedRunner.TestArguments {
        // Get additional arguments (if any) from the actual test class.
        val additionalArguments = getAdditionalArguments(testClass)

        // Obtain [TestArguments] from the provider and store the list of argument sets in the
        // [RunnersFactory.allParametersField].
        val testArguments = argumentsProvider(testClass, additionalArguments)

        return testArguments
    }

    companion object {
        /**
         * Get additional arguments, if any, provided by the [testClass] through use of a
         * [Parameters] function.
         *
         * The returned values have been normalized so each entry is an `Array<Any>`.
         */
        private fun getAdditionalArguments(testClass: TestClass): List<Array<Any>>? {
            val parametersMethod =
                testClass.getAnnotatedMethods(Parameters::class.java).firstOrNull {
                    it.isPublic && it.isStatic
                }
                    ?: return null
            return when (val parameters = parametersMethod.invokeExplosively(null)) {
                    is List<*> -> parameters
                    is Iterable<*> -> parameters.toList()
                    is Array<*> -> parameters.toList()
                    else ->
                        error(
                            "${testClass.name}.{${parametersMethod.name}() must return an Iterable of arrays."
                        )
                }
                .filterNotNull()
                .map {
                    if (
                        it is Array<*> &&
                            it.javaClass.isArray &&
                            it.javaClass.componentType == Object::class.java
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        it as Array<Any>
                    } else {
                        arrayOf(it)
                    }
                }
        }
    }
}
