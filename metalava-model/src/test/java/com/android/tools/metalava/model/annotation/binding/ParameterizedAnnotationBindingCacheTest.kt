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

package com.android.tools.metalava.model.annotation.binding

import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.reflect.KClass
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedAnnotationBindingCacheTest {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams<*, *>

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams<A : Any, B : Any>
    @EntryPoint
    constructor(
        val name: String,
        val kClassA: KClass<A>,
        val defaultsA: AnnotationDefaults?,
        val kClassB: KClass<B>,
        val defaultsB: AnnotationDefaults?,
        val expectedSame: Boolean,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    class Test1

    class Test2

    companion object {
        private val defaults1 = AnnotationDefaults(mapOf("a" to literalValue(1)))
        private val copyDefaults1 = AnnotationDefaults(mapOf("a" to literalValue(1)))
        private val defaults2 = AnnotationDefaults(mapOf("b" to literalValue(2)))

        @EntryPoint
        inline fun <reified A : Any, reified B : Any> testParams(
            name: String,
            defaultsA: AnnotationDefaults?,
            defaultsB: AnnotationDefaults?,
            expectedSame: Boolean = false,
        ) =
            TestParams(
                name = name,
                kClassA = A::class,
                defaultsA = defaultsA,
                kClassB = B::class,
                defaultsB = defaultsB,
                expectedSame = expectedSame,
            )

        private val params =
            listOf(
                testParams<Test1, Test1>(
                    "same class, same defaults",
                    defaultsA = defaults1,
                    defaultsB = defaults1,
                    expectedSame = true,
                ),
                testParams<Test1, Test1>(
                    "same class, equal defaults",
                    defaultsA = defaults1,
                    defaultsB = copyDefaults1,
                    expectedSame = true,
                ),
                testParams<Test1, Test1>(
                    "same class, different defaults",
                    defaultsA = defaults1,
                    defaultsB = defaults2,
                    expectedSame = false,
                ),
                testParams<Test1, Test2>(
                    "different class, same defaults",
                    defaultsA = defaults1,
                    defaultsB = defaults1,
                    expectedSame = false,
                ),
            )

        /** Supply the list of creation tests as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = params
    }

    @Test
    fun `Test cache`() {
        val cache = AnnotationBindingCache()
        val bindingA = cache.bindingFactoryFor(params.kClassA, params.defaultsA)
        val bindingB = cache.bindingFactoryFor(params.kClassB, params.defaultsB)
        if (params.expectedSame) {
            assertSame(bindingA, bindingB)
        } else {
            assertNotSame(bindingA, bindingB)
        }
    }
}
