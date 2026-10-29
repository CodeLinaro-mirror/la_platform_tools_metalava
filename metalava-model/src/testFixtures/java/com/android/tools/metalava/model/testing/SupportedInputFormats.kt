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

import com.android.tools.metalava.model.provider.InputFormat

/**
 * Specifies a set of [InputFormat]s that are supported by a test method.
 *
 * If this is not specified then it is assumed that the test method provides all [InputFormat]s.
 *
 * If specified on a class then this applies to all test methods in that class and any subclasses
 * unless overridden by a closer annotation on the subclass or test method.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SupportedInputFormats(
    /** The list of [InputFormat]s that the test method supports. */
    vararg val formats: InputFormat,
)
