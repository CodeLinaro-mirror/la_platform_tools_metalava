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

package com.android.tools.metalava.model.testsuite.surface

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.KOTLIN_PUBLISHED_API
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API_NON_RECURSIVE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.PUBLIC_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.kotlin
import org.junit.runners.Parameterized

/**
 * Tests verifying selected API variants for Kotlin-specific language features.
 *
 * Add tests to this class for:
 * - Kotlin `internal` visibility declarations and how they interact with show annotations.
 * - Kotlin `@PublishedApi` annotations on classes and methods (both when `@PublishedApi` is
 *   configured as a show annotation and when it is not).
 * - Kotlin properties with exposed backing fields (e.g. `const val` or `@JvmField`), especially
 *   when the backing field has explicit hide annotations (e.g. `@field:Hide`).
 * - Kotlin properties with private backing fields with explicit hide annotations.
 *
 * For Java-only or language-agnostic visibility tests, see
 * [CommonParameterizedSelectedApiVisibilityTest].
 */
class CommonParameterizedSelectedApiKotlinTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
            val publishedApiSources =
                listOf(
                    kotlin(
                        """
                            package test.pkg

                            @PublishedApi
                            internal class PublishedClass {
                                fun method() {}
                            }

                            class PublicClass {
                                @PublishedApi
                                internal fun publishedMethod() {}

                                internal fun internalMethod() {}
                            }
                        """
                    ),
                )
            val publishedApiCompiledSources =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7ZBk4GUpSi0v0C7LT9QNKk3IyizNSU5xzEouL9ZJBJNxBpQHe+cKG" +
                        "Av9y0s1fml3wlTfJbDzU237FVztPd6qX6hLXh4oOO588SVl77Oyx6KN+Rwz3" +
                        "t9Yf4Ox0Fv16Qz60VOZQdMSnM+b3z5jLnb/6dTs3w/kPExIva76e9Plg5qe8" +
                        "O2e3vl39RmX6tWkHJR4ZW0701Dub+Wlb9bKqdTwHBeSyFt8VbK74siPWtcHA" +
                        "iTO1bdLr7NMXMv4uf86Y2X7qa3dP4mJTjZh1Z9dPelzsy7pV/k/20xS1uSp9" +
                        "/OpNz57e48g/cIBN7kLPn8nfVnhq3E72SLts8ahU7mOFWW+Fq8AD0bD5vt8r" +
                        "J+/ViK/j8xaV6w5PjLn16OyB099mvo/4XXzjbuNi+bkiM5MiMjMX3+tpjzX9" +
                        "pbHb0nD12kXqn7fJZMrrn3jhbCj39Yr32+ZjvzcWVq24dSvrlJx0sMXLksQM" +
                        "59vdMVlq/1M2svu9/tJ+OWRL7KbbIqr/rj9pVo8P/Gnkf19V+pxnY3XK6vvc" +
                        "G1o6anhP2x/1f+69+Pd2gx++J8ScGn9/XqX7ZkK93x/dVbvvNnpsnKTkt/Jr" +
                        "VpfpmuU3Dszlm3FU7ZVsi9hE18NKG+T0hY81Hw0OfcAGivdjybEbljAyMFQw" +
                        "4Yt3KYx4T8Ya6YGn8y47iNga++U8VruSeXnSNptoMRuVOWFvHDxUOR5q2bBo" +
                        "zF6jtvGqz+OexWdUkmbmP+SPUNzoIOhtx1V4nFO0Pynz3J3jO68/v/fz3+PT" +
                        "4Qz8hQcCzFeaXayokNo785vRzfxUp2z+HkUbYWPBVKe5buur0zPvVxRI3Dke" +
                        "5Pm55eDO4u3BjpJ5DUI2bZMkPc5uuLtvxUPltq8bS78q+MYtS5ugzhP9NjHP" +
                        "aOU0l9mWct5JeV6plzqFbk6qqFZZkrTib1JwiMohjqRPF88sdNzBepxJ4sYF" +
                        "qSXz6ydcy5o0VcbIJvrMa8b8Dw3t3ZUHatt5imruRZzpr5hs+3VfbYm3Hf9c" +
                        "YXudt5/SfrJar2zLS9R9NuXJvvRqWcPfs0/Xd6nUPg8WEbaXkLrlqe0fJLVX" +
                        "N/RXltWrpZO/L5oemLJ47nfXa4LCmX9DC96/DZG09Eo2nRwV9CJi5R0O5WLX" +
                        "bt57Ty972tfq6Zamu77943xE0cK246/MJM/3zhW9e068a3v3ae/5D/Y31z60" +
                        "WhjNUntHYY0O546WhndS1zROdz/6fTTr5Z9YiT9hJ8JazcNvel1cUrXuwX5f" +
                        "LXGThztCH4S+uXx3k7R66utYC8NDElslpG/0BB4P68mNedmUabLhcut6wYw3" +
                        "WVNMY3q3Hrp3ecU/FlBa2b9k3YbbwLTyDW9akQamFXhZlZuYmaeXnV+Sk5kX" +
                        "n5ufUpqTCk8syQkJCWlAnNRwIWHBkQVHGcDlULh85RUhoCkS4HKIkUmEAWEL" +
                        "chkFKhFRAcHyEd045KQPKtgQoAOIiSvm0M1EDiIpFDMbmNDNxJqF0A1EDg9p" +
                        "FAPXsDAQFc4B3qxsIPUsQOgGdJoAK4gHAK4O2stsBgAA"
                )

            buildTests(
                name = "PublishedApi when not a show annotation",
                surfaceRules = publicSystemModuleRules,
                sources = publishedApiSources,
                compiledSources = publishedApiCompiledSources,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.PublishedClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublishedClass()
                                       self - ApiVariantSet[]
                                method test.pkg.PublishedClass.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                method test.pkg.PublicClass.publishedMethod()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            val publishedApiRules =
                ApiSurfaceRules(
                    publicSystemModuleRules.apiSurfaces,
                    mapOf(
                        "public" to
                            listOf(
                                SurfaceSelectionRule.unannotated,
                                SurfaceSelectionRule.createAnnotationRule(
                                    HIDE.qualifiedName,
                                    effect = SurfaceSelectionRule.Effect.HIDE,
                                ),
                                SurfaceSelectionRule.createAnnotationRule(PUBLIC_API.qualifiedName),
                                SurfaceSelectionRule.createAnnotationRule(KOTLIN_PUBLISHED_API),
                            ),
                        "system" to
                            listOf(
                                SurfaceSelectionRule.createAnnotationRule(SYSTEM_API.qualifiedName),
                            ),
                        "module" to
                            listOf(
                                SurfaceSelectionRule.createAnnotationRule(MODULE_API.qualifiedName),
                                SurfaceSelectionRule.createAnnotationRule(
                                    MODULE_API_NON_RECURSIVE.qualifiedName,
                                    recursive = false,
                                ),
                            ),
                    ),
                )

            buildTests(
                name = "PublishedApi when a show annotation",
                surfaceRules = publishedApiRules,
                sources = publishedApiSources,
                compiledSources = publishedApiCompiledSources,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.PublishedClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublishedClass()
                                       self - ApiVariantSet[]
                                method test.pkg.PublishedClass.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                method test.pkg.PublicClass.publishedMethod()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            val showOnInternalSources =
                listOf(
                    kotlin(
                        """
                            package test.pkg

                            class PublicClass {
                                $PUBLIC_API
                                internal fun showMethod() {}

                                $PUBLIC_API
                                internal val showProperty: Int = 0

                                internal fun internalMethod() {}
                            }

                            $PUBLIC_API
                            internal class ShowClass {
                                fun method() {}
                            }
                        """
                    ),
                )

            // There is no point in including an internal API in the API surface if it is not
            // annotated with kotlin.PublishedApi because without that it cannot be called
            // outside the API surface anyway.
            buildTests(
                name = "show annotation on internal declaration",
                surfaceRules = publicSystemModuleRules,
                sources = showOnInternalSources,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.showMethod${'$'}src()
                                       self - ApiVariantSet[]
                                method test.pkg.PublicClass.getShowProperty${'$'}src()
                                       self - ApiVariantSet[]
                                method test.pkg.PublicClass.internalMethod${'$'}src()
                                       self - ApiVariantSet[]
                                property test.pkg.PublicClass#showProperty
                                       self - ApiVariantSet[]
                                field test.pkg.PublicClass.showProperty
                                       self - ApiVariantSet[]
                              class test.pkg.ShowClass
                                     self - ApiVariantSet[]
                                constructor test.pkg.ShowClass()
                                       self - ApiVariantSet[]
                                method test.pkg.ShowClass.method()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "property with hidden backing field",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}
                                class Foo {
                                    @field:Hide
                                    @JvmField
                                    val bar: Int = 0
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "property with hidden private backing field",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}
                                class Foo {
                                    @field:Hide
                                    val bar: Int = 0
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Foo.getBar()
                                       self - ApiVariantSet[public(C)]
                                property test.pkg.Foo#bar
                                       self - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "property in hidden class with field suppression",
                surfaceRules = publishedApiRules,
                sources =
                    listOf(
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}

                                @Hide
                                class Foo {
                                    @field:Suppress("ShowingMemberInHiddenClass")
                                    @JvmField
                                    @PublishedApi
                                    internal var bar: Int = -1
                                }
                            """
                        ),
                    ),
                compiledSources =
                    base64gzip(
                        "test.jar",
                        // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                        "" +
                            "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                            "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                            "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                            "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuSA30zb/sIFCbbV2WdOkI" +
                            "r8ilaflcU+QKogodslozVir1BDmwunpY5j4K3fVzg3K1cKFJfWv9BWanRw2L" +
                            "5H4GdkQun6q0UfvsmdmSe2+a/37+fv19hriE2QvXuKz6c3LDe/PHNxiTP7DV" +
                            "HPsr2P/rX/s7hy9TmCcftJswMWS9xJm36W+NuDbERhRwlFceOtBk5FXgbVDQ" +
                            "ezAo3Sh1xZZpJ79LZtUu7KvYdHSxRljMSYtF67ZeMwhTlvwpv/zuRJ/zLhUW" +
                            "E397/zyY82b/7N7ZQT1Op8LF19Qo/d1uJDIlcu8rG8Gu9pjCU5KPZ3pK8eb6" +
                            "5Dr0y7xqUJGKX9onJdK6m+egzeqdbncLePPn7XS7uGTBi3ObFwXPT692a5a1" +
                            "FL+fuzqj4lnm3c1mPW4Pn9pU257fF1OzOsD1+7U1i97OnFXQ/zLSTLPOLoRn" +
                            "9vLLHic3ciya6ZNarsI3/Wh0/qOADF8joffLH6/zsCt+tTB06yM5+a2VSqb7" +
                            "fHYdT5rUIReupPJBc56a7cUpuzbVWS79e5/nzaX5Edx/f3WfsFNImRx/lVsp" +
                            "/sRFZyd2S7Y7c+drvJOwD7A7Xy03ca3D+zOLohdlJ3YHVF7sFV11SHuhl0/a" +
                            "uS1NRxOf76yec3v1wTi/Xyrbv3+c8sfQ1fTT9ZeG5lYGzGtYd0g+EHvFeG2j" +
                            "YILwdusEzXcP8tj9ury6qjW5NN+ILLVZ948ZlIqmtP9i/cvIwHCDCV8qkgam" +
                            "Inhqzk3MzNPLzi/JycyLz81PKc1JhSen5ISEhDQgTmq4kLDgyIKjDOCUGi5f" +
                            "eUUIaIoEOKUyMokwIGxBTsWgPIMKCOYgdOOQvQNK+gjQAcR4MgK6QchulkYx" +
                            "6BwTA1FhEeDNygZSzwyEr4C0ETOIBwByaxH1MgQAAA=="
                    )
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[]
                              class test.pkg.Foo
                                     self - ApiVariantSet[]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[]
                                field test.pkg.Foo.bar
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "file facade with visible members",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        kotlin(
                            """
                                package test.pkg

                                fun foo() {}
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.TestKt
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.TestKt.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "file facade without visible members",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        kotlin(
                            """
                                package test.pkg
                                import ${HIDE.qualifiedName}

                                @Hide
                                fun foo() {}
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[]
                              class test.pkg.TestKt
                                     self - ApiVariantSet[]
                                method test.pkg.TestKt.foo()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "file facade with members in different surfaces",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        kotlin(
                            """
                                package test.pkg
                                import ${SYSTEM_API.qualifiedName}

                                fun foo() {}

                                @SystemApi
                                fun bar() {}
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.TestKt
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.TestKt.foo()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.TestKt.bar()
                                       self - ApiVariantSet[]
                        """,
                )

                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(C)]
                              class test.pkg.TestKt
                                     self - ApiVariantSet[public(C),system(C)]
                                method test.pkg.TestKt.foo()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.TestKt.bar()
                                       self - ApiVariantSet[system(C)]
                        """,
                )
            }
        }
    }
}
