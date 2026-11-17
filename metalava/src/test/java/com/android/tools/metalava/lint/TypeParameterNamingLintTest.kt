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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class TypeParameterNamingLintTest : DriverTest() {

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Should not raise lint error there are no generics in Kotlin source`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    public abstract class TestClassKotlin
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Should not raise lint error when generic type is just one letter in Kotlin source`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    public abstract class TestClassKotlin<K, V>
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Should not raise lint error when generic is properly named ending in T in Kotlin source`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    public abstract class TestClassKotlin<MyFirstTypeT, MySecondTypeT>
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Should not raise lint error when method generic is properly named ending in T in Kotlin source`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    public abstract class TestClassKotlin {
                        public abstract fun <MyGenericT> myFun(list: List<MyGenericT>)
                    }
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Enforce Google generic parameter naming guidelines in Kotlin sources`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/TestClassKotlin.kt:3: error: Invalid type parameter name "KotlinTypeParam". Type parameter names must follow the Google naming guidelines specified here: https://developer.android.com/kotlin/style-guide#type_variable_names [TypeParameterName]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    public abstract class TestClassKotlin<KotlinTypeParam>
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Should raise error in improperly named method generic in Kotlin sources`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/TestClassKotlin.kt:4: error: Invalid type parameter name "MyGeneric". Type parameter names must follow the Google naming guidelines specified here: https://developer.android.com/kotlin/style-guide#type_variable_names [TypeParameterName]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    public abstract class TestClassKotlin {
                        public abstract fun <MyGeneric> myFun(list: List<MyGeneric>)
                    }
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @Test
    fun `Should not raise lint error when generic is just one capital letter in Java source`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class TestClassJava<K> {
                    }
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @Test
    fun `Should not raise lint error when generic is properly named ending in T in Java source`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class TestClassJava<MyGenericT> {
                    }
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @Test
    fun `Enforce Google generic parameter naming guidelines for classes in Java sources`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/TestClassJava.java:3: error: Invalid type parameter name "JavaTypeParam". Type parameter names must follow the Google naming guidelines specified here: https://developer.android.com/kotlin/style-guide#type_variable_names [TypeParameterName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class TestClassJava<JavaTypeParam> {
                    }
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }

    @Test
    fun `Enforce Google generic parameter naming guidelines for methods in Java sources`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/TestClassJava.java:4: error: Invalid type parameter name "MyBadMethodParam". Type parameter names must follow the Google naming guidelines specified here: https://developer.android.com/kotlin/style-guide#type_variable_names [TypeParameterName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class TestClassJava {
                        public <MyBadMethodParam> void badMethod(@NonNull List<MyBadMethodParam> list) {
                        }
                    }
                    """
                    )
                ),
            extraArguments =
                errorIssues(
                    Issues.TYPE_PARAMETER_NAME,
                ),
        )
    }
}
