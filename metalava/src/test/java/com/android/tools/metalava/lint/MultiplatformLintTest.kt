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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.FilterAction
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.defaultJvmPlatforms
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.standardProjectXmlClasspath
import org.junit.Test

@RequiresCapabilities(Capability.KOTLIN, Capability.MULTIPLATFORM)
@FilterByProvider("psi", "k1", action = FilterAction.EXCLUDE)
class MultiplatformLintTest : DriverTest() {
    private fun checkLint(
        commonSource: Array<TestFile>,
        androidSource: Array<TestFile>,
        nativeSource: Array<TestFile>,
        expectedIssues: String?,
        showAnnotations: Array<String> = emptyArray(),
        hideAnnotations: Array<String> = emptyArray(),
        extraArguments: Array<String> = emptyArray(),
        suppressCompatibilityMetaAnnotations: Array<String> = emptyArray(),
    ) {
        check(
            sourceFiles = arrayOf(*commonSource, *androidSource, *nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(commonSource),
                    createAndroidModuleDescription(androidSource),
                    createNativeModuleDescription(nativeSource),
                ),
            enableMultiplatform = true,
            apiLint = "", // enabled
            showAnnotations = showAnnotations,
            hideAnnotations = hideAnnotations,
            suppressCompatibilityMetaAnnotations = suppressCompatibilityMetaAnnotations,
            extraArguments = extraArguments,
            expectedFail = DefaultLintErrorMessage.takeIf { expectedIssues != null },
            expectedIssues = expectedIssues,
        )
    }

    @Test
    fun `Test deprecation mismatch`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/DeprecatedInCommon.kt",
                        """
                        package test.pkg
                        expect class DeprecatedMembersInCommon() {
                            @Deprecated("")
                            fun deprecatedInCommon(): Unit
                        }
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/DeprecatedInAndroid.kt",
                        """
                        package test.pkg
                        expect class DeprecatedInAndroid()
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/DeprecatedInNative.kt",
                        """
                        package test.pkg
                        expect class DeprecatedMembersInNative() {
                            val deprecatedInNative: Int
                        }
                        """
                    ),
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/DeprecatedInCommon.kt",
                        """
                        package test.pkg
                        actual class DeprecatedMembersInCommon {
                            actual fun deprecatedInCommon() = Unit
                        }
                        """
                    ),
                    kotlin(
                        "androidMain/src/test/pkg/DeprecatedInAndroid.kt",
                        """
                        package test.pkg
                        @Deprecated("")
                        actual class DeprecatedInAndroid
                        """
                    ),
                    kotlin(
                        "androidMain/src/test/pkg/DeprecatedInNative.kt",
                        """
                        package test.pkg
                        actual class DeprecatedMembersInNative {
                            actual val deprecatedInNative: Int = 0
                        }
                        """
                    ),
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/DeprecatedInCommon.kt",
                        """
                        package test.pkg
                        actual class DeprecatedMembersInCommon {
                            actual fun deprecatedInCommon() = Unit
                        }
                        """
                    ),
                    kotlin(
                        "nativeMain/src/test/pkg/DeprecatedInAndroid.kt",
                        """
                        package test.pkg
                        actual class DeprecatedInAndroid
                        """
                    ),
                    kotlin(
                        "nativeMain/src/test/pkg/DeprecatedInNative.kt",
                        """
                        package test.pkg
                        actual class DeprecatedMembersInNative {
                            @Deprecated("")
                            actual val deprecatedInNative: Int = 0
                        }
                        """
                    ),
                ),
            expectedIssues =
                """
                commonMain/src/test/pkg/DeprecatedInAndroid.kt:2: error: multiplatform class test.pkg.DeprecatedInAndroid is deprecated in source sets [androidMain] but not deprecated in source sets [commonMain, nativeMain] [KmpDeprecationMismatch]
                commonMain/src/test/pkg/DeprecatedInCommon.kt:4: error: multiplatform method test.pkg.DeprecatedMembersInCommon#deprecatedInCommon() is deprecated in source sets [commonMain] but not deprecated in source sets [androidMain, nativeMain] [KmpDeprecationMismatch]
                commonMain/src/test/pkg/DeprecatedInNative.kt:3: error: multiplatform property test.pkg.DeprecatedMembersInNative#deprecatedInNative is deprecated in source sets [nativeMain] but not deprecated in source sets [commonMain, androidMain] [KmpDeprecationMismatch]
                """,
        )
    }

    @Test
    fun `Test mismatched visibility`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        expect abstract class Foo {
                            internal fun foo(): Unit
                        }
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual abstract class Foo {
                            actual public fun foo() = Unit
                        }
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_native.kt",
                        """
                        package test.pkg
                        actual abstract class Foo {
                            actual protected fun foo() = Unit
                        }
                        """
                    )
                ),
            expectedIssues =
                """
                commonMain/src/test/pkg/Foo.kt:3: error: multiplatform method test.pkg.Foo#foo() has different visibilities in different source sets: internal in [commonMain], public in [androidMain], protected in [nativeMain] [KmpVisibilityMismatch]
                """,
        )
    }

    @Test
    fun `Test elements of internal class without explicit visibility`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/InternalClass.kt",
                        """
                        package test.pkg
                        internal expect class InternalClass internal constructor() {
                            internal fun internalClassFun(): Unit
                            internal val internalClassVal: Int
                        }
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/InternalClass_android.kt",
                        """
                        package test.pkg
                        internal actual class InternalClass internal actual constructor() {
                            internal actual fun internalClassFun() = Unit
                            internal actual val internalClassVal: Int = 0
                        }
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/InternalClass_native.kt",
                        """
                        package test.pkg
                        internal actual class InternalClass actual constructor() {
                            actual fun internalClassFun() = Unit
                            actual val internalClassVal: Int = 0
                        }
                        """
                    )
                ),
            expectedIssues = null,
        )
    }

    @Test
    fun `Test mismatched show and hide annotations`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Hide.kt",
                        """
                        package test.pkg
                        annotation class Hide
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        expect class Foo {
                            @Hide val hiddenInCommon: Int
                            fun hiddenInNative(): Unit

                            @PublishedApi internal fun shownInCommon(): Unit
                            internal val shownInAndroid: Int
                        }
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual class Foo {
                            actual val hiddenInCommon: Int
                            actual fun hiddenInNative(): Unit

                            actual internal fun shownInCommon(): Unit
                            @PublishedApi actual internal val shownInAndroid: Int
                        }
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual class Foo {
                            actual val hiddenInCommon: Int
                            @Hide actual fun hiddenInNative(): Unit

                            actual internal fun shownInCommon(): Unit
                            actual internal val shownInAndroid: Int
                        }
                        """
                    )
                ),
            showAnnotations = arrayOf("kotlin.PublishedApi"),
            hideAnnotations = arrayOf("test.pkg.Hide"),
            extraArguments = arrayOf(ARG_HIDE, "UnhiddenSystemApi"),
            expectedIssues =
                """
                commonMain/src/test/pkg/Foo.kt:3: error: multiplatform property test.pkg.Foo#hiddenInCommon is hidden with an annotation in source sets [commonMain] but not hidden with an annotation in source sets [androidMain, nativeMain] [KmpHideShowAnnotationMismatch]
                commonMain/src/test/pkg/Foo.kt:4: error: multiplatform method test.pkg.Foo#hiddenInNative() is hidden with an annotation in source sets [nativeMain] but not hidden with an annotation in source sets [commonMain, androidMain] [KmpHideShowAnnotationMismatch]
                commonMain/src/test/pkg/Foo.kt:6: error: multiplatform method test.pkg.Foo#shownInCommon() is shown with an annotation in source sets [commonMain] but not shown with an annotation in source sets [androidMain, nativeMain] [KmpHideShowAnnotationMismatch]
                commonMain/src/test/pkg/Foo.kt:7: error: multiplatform property test.pkg.Foo#shownInAndroid is shown with an annotation in source sets [androidMain] but not shown with an annotation in source sets [commonMain, nativeMain] [KmpHideShowAnnotationMismatch]
                """
        )
    }

    @Test
    fun `Test mismatched experimental annotations`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/ExperimentalApi.kt",
                        """
                        package test.pkg
                        @RequiresOptIn
                        annotation class ExperimentalApi
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/ExperimentalInCommon.kt",
                        """
                        package test.pkg
                        @ExperimentalApi
                        expect class ExperimentalInCommon
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/ExperimentalInNative.kt",
                        """
                        package test.pkg
                        expect class ExperimentalInNative
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/ExperimentalInCommon_android.kt",
                        """
                        package test.pkg
                        actual class ExperimentalInCommon
                        """
                    ),
                    kotlin(
                        "androidMain/src/test/pkg/ExperimentalInNative_android.kt",
                        """
                        package test.pkg
                        actual class ExperimentalInNative
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/ExperimentalInCommon_native.kt",
                        """
                        package test.pkg
                        actual class ExperimentalInCommon
                        """
                    ),
                    kotlin(
                        "nativeMain/src/test/pkg/ExperimentalInNative_native.kt",
                        """
                        package test.pkg
                        @ExperimentalApi
                        actual class ExperimentalInNative
                        """
                    )
                ),
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn"),
            expectedIssues =
                """
                commonMain/src/test/pkg/ExperimentalInCommon.kt:3: error: multiplatform class test.pkg.ExperimentalInCommon is experimental in source sets [commonMain] but not experimental in source sets [androidMain, nativeMain] [KmpExperimentalMismatch]
                commonMain/src/test/pkg/ExperimentalInNative.kt:2: error: multiplatform class test.pkg.ExperimentalInNative is experimental in source sets [nativeMain] but not experimental in source sets [commonMain, androidMain] [KmpExperimentalMismatch]
                """
        )
    }

    @Test
    fun `Test mismatched final`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        expect class Foo
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual open class Foo
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_native.kt",
                        """
                        package test.pkg
                        actual class Foo
                        """
                    )
                ),
            expectedIssues =
                """
                commonMain/src/test/pkg/Foo.kt:2: error: multiplatform class test.pkg.Foo is final in source sets [commonMain, nativeMain] but not final in source sets [androidMain] [KmpModifierMismatch]
                """,
        )
    }

    @Test
    fun `Test final lint check for typealias`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        expect class Foo
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual typealias Foo = String
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_native.kt",
                        """
                        package test.pkg
                        actual class Foo
                        """
                    )
                ),
            expectedIssues = null,
        )
    }

    @Test
    fun `Test mismatched operator`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        expect class Foo {
                            fun plus(other: Foo): Foo
                        }
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual class Foo {
                            actual operator fun plus(other: Foo) = other
                        }
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_native.kt",
                        """
                        package test.pkg
                        actual class Foo {
                            actual fun plus(other: Foo) = other
                        }
                        """
                    )
                ),
            expectedIssues =
                """
                commonMain/src/test/pkg/Foo.kt:3: error: multiplatform method test.pkg.Foo#plus(test.pkg.Foo) is operator in source sets [androidMain] but not operator in source sets [commonMain, nativeMain] [KmpModifierMismatch]
                """
        )
    }

    @Test
    fun `Test mismatched infix`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        expect class Foo {
                            fun foo(i: Int): Unit
                        }
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        actual class Foo {
                            actual infix fun foo(i: Int) = Unit
                        }
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_native.kt",
                        """
                        package test.pkg
                        actual class Foo {
                            actual fun foo(i: Int) = Unit
                        }
                        """
                    )
                ),
            expectedIssues =
                """
                commonMain/src/test/pkg/Foo.kt:3: error: multiplatform method test.pkg.Foo#foo(kotlin.Int) is infix in source sets [androidMain] but not infix in source sets [commonMain, nativeMain] [KmpModifierMismatch]
                """
        )
    }

    @Test
    fun `Test mismatched reified`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Foo.kt",
                        """
                        package test.pkg
                        public expect class Foo() {
                            public inline fun <reified T> foo(): Unit
                        }
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Foo_android.kt",
                        """
                        package test.pkg
                        public actual class Foo {
                            public actual inline fun <T> foo() = Unit
                        }
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Foo_native.kt",
                        """
                        package test.pkg
                        public actual class Foo {
                            public actual inline fun <T> foo() = Unit
                        }
                        """
                    )
                ),
            expectedIssues =
                """
                commonMain/src/test/pkg/Foo.kt:3: error: multiplatform type parameter #0 of multiplatform method test.pkg.Foo#foo() is reified in source sets [commonMain] but not reified in source sets [androidMain, nativeMain] [KmpReifiedMismatch]
                """
        )
    }

    @Test
    fun `Test mismatched class origin`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                class Common
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Mismatch.kt",
                """
                package test.pkg
                class Mismatch
                """
            )
        val jvmSource =
            kotlin(
                "jvmMain/src/test/pkg/Jvm.kt",
                """
                package test.pkg
                class Jvm
                """
            )

        /*
        Generated from the following source:
        package test.pkg
        class Mismatch
         */
        val jvmClasspathJar =
            base64gzip(
                "jvmClasspath.jar",
                // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                "" +
                    "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                    "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                    "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9OBCX" +
                    "pBaX6Bdkp+v7ZhbnJpYkZ+gl5yQWF6cGnvZjchRYI5kwUfTpU+aauVebuY/M" +
                    "Pubi62MWaRJ4uPXyhpyEhJmmZxxsPun+a1QX5m+r+LK4Z6/2vD0hq1ZV71pt" +
                    "HH//vTyD38bzAUaNZucur3i/ZYkc92P+TQk8RxfO4FQXWfX+Tml7Y/4mD8OI" +
                    "50GexQcnWNlkPZ/YmKrcq+N8i2dJ2oqK/8vmXuTKF5kZoLVcVOKisNuE48c3" +
                    "i7gmGD5JYmCWnvhfJzlU/Vq2gYzOs21nV9955prUXVlqmP/Javepmn0Re048" +
                    "XqfWfX/dv7fWkpJdxbJfmdqa1EMLrGXLm08uftH2ZMreE+KJ0T4pa6Yv+yvg" +
                    "q15XoJwcuHu+lvJxx+gsz6veS5WUe3qmuDHPWReb1tuvvmZSkRBvSt/9DZ+8" +
                    "Wha7ZaiqHedL/Led0fJG5/bZD8y+Tcpf8CPkdPmEoo3Pku66Mrou0tipsfgS" +
                    "4+UbB9Sumub06vK+ZgdFifPeLbZ+jAwMhxnxRYk0EMNTRG5iZp5edn5JTmZe" +
                    "fG5+SmlOanJCQkIaELMk+bFpBCRdSGIAR/dXpT17hYE6JcDRzcgkwoAwHTkp" +
                    "gNIbKsCV+tBNQXa9OIoJ9bgTEbohyM6URjFEiAmvtwO8WdlAypiB8AqQzmMC" +
                    "8QDE3l5IWAMAAA=="
            )

        check(
            sourceFiles = arrayOf(commonSource, androidSource, jvmSource),
            classpath = arrayOf(jvmClasspathJar),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createModuleDescription(
                        moduleName = "jvmMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(jvmSource),
                        classpathXml =
                            standardProjectXmlClasspath +
                                "<classpath file=\"${jvmClasspathJar.targetRelativePath}\"/>",
                    )
                ),
            enableMultiplatform = true,
            apiLint = "", // enabled
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                androidMain/src/test/pkg/Mismatch.kt:2: error: multiplatform class test.pkg.Mismatch has different origins in different source sets: COMMAND_LINE in [androidMain], CLASS_PATH in [jvmMain] [KmpOriginMismatch]
                """,
        ) {
            multiplatformCodebase!!.resolveClass("test.pkg.Mismatch")
        }
    }

    @Test
    fun `Test signature clash in unrelated platforms`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/Common.kt",
                        """
                        package test.pkg
                        class Common
                        """
                    )
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/Clash_android.kt",
                        """
                        package test.pkg
                        class Clash
                        """
                    )
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/Clash_native.kt",
                        """
                        package test.pkg
                        class Clash
                        """
                    )
                ),
            expectedIssues =
                """
                androidMain/src/test/pkg/Clash_android.kt:2: error: multiplatform class test.pkg.Clash is not an expect/actual and is defined with the same signature in unrelated source sets ([androidMain, nativeMain]) [KmpSignatureClash]
                """
        )
    }

    @Test
    fun `Check API lint issues are reported for all source sets`() {
        checkLint(
            commonSource =
                arrayOf(
                    kotlin(
                        "commonMain/src/test/pkg/commonBadClassName.kt",
                        """
                        package test.pkg
                        class commonBadClassName
                        """
                    ),
                ),
            androidSource =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/androidBadClassName.kt",
                        """
                        package test.pkg
                        class androidBadClassName
                        """
                    ),
                    kotlin(
                        "androidMain/src/test/pkg/clashingBadClassName.kt",
                        """
                        package test.pkg
                        class clashingBadClassName
                        """
                    ),
                ),
            nativeSource =
                arrayOf(
                    kotlin(
                        "nativeMain/src/test/pkg/nativeBadClassName.kt",
                        """
                        package test.pkg
                        class nativeBadClassName
                        """
                    ),
                    kotlin(
                        "nativeMain/src/test/pkg/clashingBadClassName.kt",
                        """
                        package test.pkg
                        class clashingBadClassName
                        """
                    ),
                ),
            expectedIssues =
                // Each class name is only reported once.
                """
                androidMain/src/test/pkg/androidBadClassName.kt:2: error: Class must start with uppercase char: androidBadClassName [StartWithUpper]
                androidMain/src/test/pkg/clashingBadClassName.kt:2: error: multiplatform class test.pkg.clashingBadClassName is not an expect/actual and is defined with the same signature in unrelated source sets ([androidMain, nativeMain]) [KmpSignatureClash]
                androidMain/src/test/pkg/clashingBadClassName.kt:2: error: Class must start with uppercase char: clashingBadClassName [StartWithUpper]
                commonMain/src/test/pkg/commonBadClassName.kt:2: error: Class must start with uppercase char: commonBadClassName [StartWithUpper]
                nativeMain/src/test/pkg/nativeBadClassName.kt:2: error: Class must start with uppercase char: nativeBadClassName [StartWithUpper]
                """,
        )
    }
}
