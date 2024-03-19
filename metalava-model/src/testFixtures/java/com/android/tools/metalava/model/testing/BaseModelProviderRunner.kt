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

package com.android.tools.metalava.model.testing

import com.android.tools.metalava.model.junit4.CustomizableParameterizedRunner
import com.android.tools.metalava.model.testing.BaseModelProviderRunner.InstanceRunner
import com.android.tools.metalava.model.testing.BaseModelProviderRunner.InstanceRunnerFactory
import com.android.tools.metalava.model.testing.BaseModelProviderRunner.ModelProviderWrapper
import com.android.tools.metalava.testing.BaselineTestRule
import org.junit.runner.Runner
import org.junit.runners.Parameterized
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * Base class for JUnit [Runner]s that need to run tests across a number of different model
 * providers.
 *
 * The basic approach is:
 * 1. Invoke the `modelProvidersGetter` lambda to get a list of model provider objects of type [M].
 *    The type of model provider objects can vary across different runners, hence why it is
 *    specified as a type parameters.
 * 2. Wrap the model provider objects in a [ModelProviderWrapper] to tunnel information needed
 *    through to [InstanceRunner].
 * 3. Generate the cross product of the model provider objects with any additional test arguments
 *    provided by the test class. If no test arguments are provided then just return the model
 *    provider objects list directly. Either way the returned [TestArguments] object will contain an
 *    appropriate pattern.
 * 4. The [Parameterized.RunnersFactory] will take the list of test arguments returned and then use
 *    them to construct a set of [TestWithParameters] objects, each of which is passed to a
 *    [ParametersRunnerFactory] to create the [Runner] for the test.
 * 5. The [ParametersRunnerFactory] is instantiated by [Parameterized.RunnersFactory] directly from
 *    a class (in this case [InstanceRunnerFactory]) so there is no way for this to pass information
 *    into the [InstanceRunnerFactory]. So, instead it relies on the information to be passed
 *    through the [TestWithParameters] object that is passed to
 *    [ParametersRunnerFactory.createRunnerForTestWithParameters].
 * 6. The [InstanceRunnerFactory] extracts the [ModelProviderWrapper] from the [TestWithParameters]
 *    it is given and passes it in alongside the remaining arguments to [InstanceRunner].
 * 7. The [InstanceRunner] injects the model provider object into the test class along with any
 *    additional parameters and then runs the test as normal.
 *
 * @param M the type of the model provider object.
 * @param I the type of the injectable class through which the model provider will be injected into
 *   the test class.
 * @param clazz the test class to be run, must be assignable to `injectableClass`.
 * @param injectableClass the class through which the model provider object will be injected into
 *   the test class.
 * @param modelProviderInjector the lambda that given an instance of `injectableClass` will inject
 *   the supplied model provider object.
 * @param modelProvidersGetter a lambda for getting the model provider objects.
 * @param baselineResourcePath the resource path to the baseline file that should be consulted for
 *   known errors to ignore / check.
 */
open class BaseModelProviderRunner<M : Any, I : Any>(
    clazz: Class<*>,
    injectableClass: Class<out I>,
    modelProviderInjector: I.(M) -> Unit,
    modelProvidersGetter: (TestClass) -> List<M>,
    baselineResourcePath: String,
) :
    CustomizableParameterizedRunner(
        clazz,
        { testClass, additionalArguments ->
            createTestArguments(
                testClass,
                injectableClass,
                modelProviderInjector,
                modelProvidersGetter,
                baselineResourcePath,
                additionalArguments,
            )
        },
        InstanceRunnerFactory::class,
    ) {

    init {
        if (!injectableClass.isAssignableFrom(clazz)) {
            error("Class ${clazz.name} does not implement ${injectableClass.name}")
        }
    }

    /**
     * A wrapper around a model provider object that tunnels information needed by
     * [InstanceRunnerFactory] through [TestWithParameters].
     */
    private class ModelProviderWrapper<M : Any, T : Any>(
        private val injectionClass: Class<out T>,
        private val modelProviderInjector: T.(M) -> Unit,
        val modelProvider: M,
        val baselineResourcePath: String,
    ) {
        fun injectModelProviderInto(testInstance: Any) {
            val injectableTestInstance = injectionClass.cast(testInstance)
            injectableTestInstance.modelProviderInjector(modelProvider)
        }

        /**
         * Delegate this to [modelProvider] as this string representation ends up in the
         * [TestWithParameters.name].
         */
        override fun toString() = modelProvider.toString()
    }

    /** [ParametersRunnerFactory] for creating [Runner]s for a set of arguments. */
    class InstanceRunnerFactory : ParametersRunnerFactory {
        /**
         * Create a runner for the [TestWithParameters].
         *
         * The [TestWithParameters.parameters] contains at least one argument and the first argument
         * will be the [ModelProviderWrapper] provided by [createTestArguments]. This extracts that
         * from the list and passes them to [InstanceRunner] to inject them into the test class.
         */
        override fun createRunnerForTestWithParameters(test: TestWithParameters): Runner {
            val arguments = test.parameters

            // Extract the [ModelProviderWrapper] from the arguments.
            val modelProviderWrapper = arguments[0] as ModelProviderWrapper<*, *>

            // Create a new set of [TestWithParameters] containing just the remaining arguments.
            // Keep the name as is as that will describe the model provider as well as the other
            // arguments.
            val remainingArguments = arguments.drop(1)

            // If the suffix to add to the end of the test name matches the default suffix then
            // replace it with an empty string. This will cause [InstanceRunner] to avoid adding a
            // suffix to the end of the test so that it can be run directly from the IDE.
            val suffix = test.name.takeIf { it != DEFAULT_SUFFIX } ?: ""

            val newTest = TestWithParameters(suffix, test.testClass, remainingArguments)

            // Create a new [InstanceRunner] that will inject the model provider into the test class
            // when created.
            return InstanceRunner(modelProviderWrapper, newTest)
        }
    }

    /**
     * Runner for a test that must implement [I].
     *
     * This will use the [modelProviderWrapper] to inject the model provider object into the test
     * class after creation.
     */
    private class InstanceRunner(
        private val modelProviderWrapper: ModelProviderWrapper<*, *>,
        test: TestWithParameters
    ) : BlockJUnit4ClassRunnerWithParameters(test) {

        /** The suffix to add at the end of the test name. */
        private val testSuffix = test.name

        /**
         * The runner name.
         *
         * If [testSuffix] is empty then this will be the name of the class, otherwise it will be
         * the test suffix. Using the name of the class should ensure that the test description will
         * match the description generated by IDEs when trying to run individual test methods.
         */
        private val runnerName = testSuffix.takeIf { it != "" } ?: test.testClass.name

        override fun createTest(): Any {
            val testInstance = super.createTest()
            modelProviderWrapper.injectModelProviderInto(testInstance)
            return testInstance
        }

        override fun getName(): String {
            return runnerName
        }

        override fun testName(method: FrameworkMethod): String {
            return method.name + testSuffix
        }

        /**
         * Override [methodInvoker] to allow the [Statement] it returns to be wrapped by a
         * [BaselineTestRule] to take into account known issues listed in a baseline file.
         */
        override fun methodInvoker(method: FrameworkMethod, test: Any): Statement {
            val statement = super.methodInvoker(method, test)
            val baselineTestRule =
                BaselineTestRule(
                    modelProviderWrapper.modelProvider.toString(),
                    modelProviderWrapper.baselineResourcePath,
                )
            return baselineTestRule.apply(statement, describeChild(method))
        }
    }

    companion object {
        /**
         * The default provider; this is the tests that will be run automatically when running a
         * specific method in the IDE.
         */
        private const val DEFAULT_PROVIDER = "psi"

        /** The suffix added to the test method name for the [DEFAULT_PROVIDER]. */
        const val DEFAULT_SUFFIX = "[$DEFAULT_PROVIDER]"

        private fun <M : Any, T : Any> createTestArguments(
            testClass: TestClass,
            injectionClass: Class<out T>,
            modelProviderInjector: T.(M) -> Unit,
            modelProvidersGetter: (TestClass) -> List<M>,
            baselineResourcePath: String,
            additionalArguments: List<Array<Any>>?,
        ): TestArguments {
            // Get the list of model providers over which this must run the tests.
            val modelProviders = modelProvidersGetter(testClass)

            // Wrap each model provider object with information needed by [InstanceRunnerFactory].
            val wrappers =
                modelProviders.map {
                    ModelProviderWrapper(
                        injectionClass,
                        modelProviderInjector,
                        it,
                        baselineResourcePath
                    )
                }

            return if (additionalArguments == null) {
                // No additional arguments were provided so just return the wrappers.
                TestArguments("{0}", wrappers)
            } else {
                val combined =
                    crossProduct(
                        wrappers,
                        additionalArguments,
                    )
                TestArguments("{0},{1}", combined)
            }
        }

        /** Compute the cross product of the supplied [data1] and the [data2]. */
        private fun crossProduct(data1: List<Any>, data2: Iterable<Array<Any>>): List<Array<Any>> =
            data1.flatMap { p1 ->
                data2.map { p2 ->
                    // [data2] is an array that is assumed to have a single parameter within it but
                    // could have none or multiple. Either way just spread the contents into the
                    // combined array.
                    arrayOf(p1, *p2)
                }
            }
    }
}
