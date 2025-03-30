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

import com.android.tools.metalava.model.testing.BaseModelProviderRunner
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import org.junit.runners.Parameterized

/**
 * Marks a public static method that will be called with each set of arguments provided to a
 * parameterized test and determines whether they are a valid combination.
 *
 * This can only be used in a test that is run by a subclass of [BaseModelProviderRunner]. In that
 * case the method takes one more parameter than the number of [Parameterized.Parameter] annotated
 * fields in the test class. The first parameter of the method is of [CodebaseCreatorConfig] type,
 * the subsequent parameters correspond to the [Parameterized.Parameter] whose value is 1 less than
 * the parameter index.
 *
 * The method must return true if the parameters are a valid combination, false otherwise.
 */
@Target(AnnotationTarget.FUNCTION) annotation class ParameterFilter
