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

package com.android.tools.metalava.model.testsuite.annotationitem.binding

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import org.junit.Test

class CommonAnnotationBindingTest : BaseModelTest() {
    data class CheckBindContext(
        val annotation: AnnotationItem,
        val item: Item,
    )

    @Suppress("SameParameterValue")
    private fun checkBind(annotation: String, checker: CheckBindContext.() -> Unit) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    $annotation
                    public class Test {}
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val annotation = testClass.modifiers.annotations().single()
            CheckBindContext(
                    annotation = annotation,
                    item = testClass,
                )
                .checker()
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test empty bind`() {
        class Empty
        checkBind("@Anno") { annotation.bindTo<Empty>(item) }
    }
}
