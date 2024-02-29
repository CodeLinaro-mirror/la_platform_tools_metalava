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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.junit4.CustomizableParameterizedRunner
import com.android.tools.metalava.model.testing.BaseModelProviderRunner
import com.android.tools.metalava.model.testsuite.ModelProviderAwareTest.ModelProviderTestInfo
import java.util.ServiceLoader
import kotlin.test.fail
import org.junit.runners.Parameterized

/**
 * A special [CustomizableParameterizedRunner] for use with the model test suite tests.
 *
 * This provides the list of [ModelProviderTestInfo] constructed from the [ModelSuiteRunner]
 * accessible through the [ServiceLoader]. If the test provides its own arguments using
 * [Parameterized.Parameters] then this will compute the cross produce of those arguments with the
 * [ModelProviderTestInfo]. That will ensure that every set of arguments provided by the test clas s
 * will be run with every [ModelSuiteRunner] available.
 *
 * The [ModelProviderTestInfo] is injected into the test through
 * [ModelProviderAwareTest.modelProviderTestInfo] and not through a field annotated with
 * [Parameterized.Parameter]. That means that switching a class that is already [Parameterized] to
 * use this instead does not affect any existing [Parameterized.Parameter] fields.
 */
class ModelTestSuiteRunner(clazz: Class<*>) :
    BaseModelProviderRunner<ModelProviderTestInfo, ModelProviderAwareTest>(
        clazz,
        ModelProviderAwareTest::class.java,
        { m -> modelProviderTestInfo = m },
        { getModelSuiteRunners() },
        ModelTestSuiteBaseline.RESOURCE_PATH,
    ) {

    companion object {
        private fun getModelSuiteRunners(): List<ModelProviderTestInfo> {
            val loader = ServiceLoader.load(ModelSuiteRunner::class.java)
            val modelSuiteRunners = loader.toList()
            if (modelSuiteRunners.isEmpty()) {
                fail("No runners found")
            }
            val modelProviderTestInfoList =
                modelSuiteRunners.flatMap { runner ->
                    runner.testConfigurations.map {
                        ModelProviderTestInfo(
                            runner,
                            it.inputFormat,
                            it.modelOptions,
                        )
                    }
                }
            return modelProviderTestInfoList
        }
    }
}
