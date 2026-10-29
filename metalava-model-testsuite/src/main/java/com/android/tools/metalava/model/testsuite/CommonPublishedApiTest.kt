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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonPublishedApiTest : BaseModelTest() {
    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @PublishedApi internal class Foo
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuSA087cVsKGJ7cstlq7C1" +
                        "73+J3VnBfVzsYKvSCgWeK+xad0umbnNMXi6ZeenRpvuP5HcIxXgUvnTvNN/a" +
                        "9ODZk3nFh87uNJ8fn/+e4duGcxM2X7rxYWOl+DxVT7Yexf8Ku5R6Wvdx62fz" +
                        "b8oz/iDxx3npVNukD2bbtSyXPGT3UDw8YdP3UwY90/+bWSXM0DUXltaWjSvN" +
                        "2mj0fVPhnxUe8/9cPb9UlDeRNXPP1a8aAuUPLBmYs3c1ffqg/nRV7Cop17be" +
                        "On3xD0z8Ffvuc1dMS49ceF/7Y2zZpJ8V34Ij532/c1WoOLaGa/PlLSKfTTI9" +
                        "2jo3Xfq06PKqk9/Wrji6xGfq5F0d2ovWysh3HfS9lHn96cyilIe7Fq/NOtll" +
                        "1HUnea2w9hovk969Jl+U7v59vojDz1qq73GY2w4PHZkChb1/u950S8t4t/xz" +
                        "eBKhduZvzAEOpu3Ca3jmsK65tNN/3u/GjVp7OB4Lqik9MZRM5LosmGYQzWo8" +
                        "43CNqGbzgQUJE8yu1OX+OKHxgRcUewuEeqaXMjIwMDPhiz1pYOzBU1FuYmae" +
                        "XnZ+SU5mXnxufkppTio8GpMTEhLSgDip4ULCgiMLjjKAU0i4fOUVIaApEuAU" +
                        "wsgkwoCwBTn1gNIqKiCYctGNQ/YOKMkhQAcQ40mA6AYhu1kaxSA3JgaiwiLA" +
                        "m5UNpJ4ZCF8B6VVMIB4ATa97MqoDAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fooClass.modifiers.isPublishedApi()).isTrue()
            assertThat(fooClass.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooClass.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi constructor`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo @PublishedApi internal constructor()
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuSA3w9VZ2FKi9OtnnkYTp" +
                        "q1WFf6NudFcVhLDwdy3gPuRQFh5SdHLlZJNbaZeUX/p9mHH/kf0HlTeOh+S+" +
                        "u3deNWzaMD38a9zed79+fP/1nPUA4z5BL79I8fvb8x991tFu4HhqfXN6odZZ" +
                        "3z+7C5fPWtfQICCXvfgup+KNO6sExVa9PbP01hHxvW6r3q7Nmnvv9WKhTxzK" +
                        "TwJUntxZu63l5avnbO4tN/un20+drPtSJXvRZf4KhYeKHxiYttYe+tzB/nZz" +
                        "7Lmo6pb3hx01hUQFOz8VCHQuWsHD/jtp+jm1ri/fDc+Iulp+uFb7aX/LnZLl" +
                        "byvmyEd6C6aturUifq+fc+n6VOXU1HWzTFOjJse+/RkRs/ON5HJrLcPaXFPv" +
                        "5cdS/KzmSt7T3HWq6u4yq+v3dtlsu/bPOIm/Kd8w7vj8tp/haR87mH++Tfex" +
                        "+Xl04yGJPpfG0GOXdjMKqlmu613dGlhb23L6xoOKE6v2/wqRdWx6yWLMeWqG" +
                        "r0H7T5b4DQHskTpJK/vmHF3ielQpX3AfIygKHxXNelrFyMDAzIQvCqWBUQhP" +
                        "SrmJmXl62fklOZl58bn5KaU5qfC4TE5ISEgD4qSGCwkLjiw4ygBOJuHylVeE" +
                        "gKZIgJMJI5MIA8IW5CQESrCogGDyRTcO2TugdIcAHUCMJxWiG4TsZmkUg7yZ" +
                        "GIgKiwBvVjaQemYgfAWk1zOBeAA60PwJrwMAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooCtor = fooClass.assertConstructor(emptyList())
            assertThat(fooCtor.modifiers.isPublishedApi()).isTrue()
            assertThat(fooCtor.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi function`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @PublishedApi internal fun foo() = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuSA3w9Zc2FKj9pjZ7w5IN" +
                        "DpnMW7bs/ai4kfHrNbFOJUUx1hVRU7z9/K6xTu7tTVJ/Zbi/tf5C8FK5VSuO" +
                        "/1vR9XGS14o1W/Ln3d8j2f/8/P35+xn2HJVumzX5mqr53tNPTszgknCu0XVT" +
                        "Otm6z+ec9fVNr7U4JA5999Lefl4gJ2dbloZyT4BT68In66akzsi2y/nSVxPf" +
                        "mxiR963n9FQ/O17rnZvCjax3BE6J/bF8vpe60UOWzD32XBk67xQ/MLBd+9yy" +
                        "7NH9Kem8STf1nI0kjwuu/9xxfHLhkdWMgnHze75XNNeu+bOP1ztUjl396o4t" +
                        "p6SfJt9XuJ28L9nj8dsvMrx1GUYnDe6HRM3j/S1ruUNg7pwpR5e97NqjynZq" +
                        "/vJHt54vLFulH2wbFDaxZMHWwkOai19+vShuLFPydOKna1NXbv16oiO24Nas" +
                        "/xa9Tt4qMYuDH8332rjnK/PV966/WBMjOmT0ObO/s51RWLSjIfLUp0UVu2+I" +
                        "/P0vzsbPdXNLQe2staL3wlnXPTI9yr/EbiH3s1scQWeWdwTvSjM8KjRvWcSx" +
                        "zWYzjhZfuc2hq9Qt2r3pa820H7KgeF4np+c0n5GBIYQJXzxLA+MZnt5yEzPz" +
                        "9LLzS3Iy8+Jz81NKc1LhEZ6ckJCQBsRJDRcSFhxZcJQBnJbC5SuvCAFNkQCn" +
                        "JUYmEQaELcjpDJSqUQHBNI5uHLJ3QIkTATqAGE9SRTcI2c3SKAYVMDEQFRYB" +
                        "3qxsIPXMQPgKSF9hAvEAZ7tXvNQDAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", emptyList())
            assertThat(fooMethod.modifiers.isPublishedApi()).isTrue()
            assertThat(fooMethod.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooMethod.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi property with accessor methods`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @PublishedApi internal var foo = 0
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuyA3yzb/sIFC7uyzpuG+U" +
                        "+pKl8jXTGDwFl6y1UFjRwLQqRbNmRYhgkJ/fpiW3l5RNvRsiczb+4ZmlQhyd" +
                        "zF1VT9wV0+YqTG3iN86ff3/2xzO/nq9/b89w57hs2x4xr+v9v/b+8pOxTuD5" +
                        "51ghc4098crq/XnyHX1lCYYp21NUviazrY7KfjbR8Vozb/vBa7+DDM+YnROr" +
                        "cmmbd6ElyNnh496DOicWN9yUsXjmsWiZ0G6pK7N4zokJls8PmHzpSAznYo1r" +
                        "C6cVuug+k/4bwZ8SUm+cxb/klvjdt8qec7vqf0x3ar8b0H7k4+HkbrcnahFq" +
                        "DbO+8y7g/MmeecFq4Tu2w/csNKtrJlbfkVeXK9dl0nt65pHFzSev38ws/qWZ" +
                        "vfvO9jLtfXvPXDfmP3bycuGnd18e6+zVrH68JGV35P2obbHHy8NzzZ9XL/um" +
                        "Zpy72eTE3n3LNOcfjz1ocH3CxApTq5zuh3E35ASCs47MLbjA035pzvIl351k" +
                        "eF3Vryw6vWGTddcWGxHHEp2pS77fThd/vLfX/XDfjYnHtptMNfsvmNR8LW/5" +
                        "6gfPxDhutrzU0gx7EWLuvWhO5G+270fW8JtrpgVPeix29I9Lg4zNKdczS87c" +
                        "f6b/wX6+cfR/U/HAWbPkJ82S/nrrVPiP59H+d7iFj17JvcA6q/Gn5RTO6My5" +
                        "TGr81p6X35qELW99dnnTVpN7B6Sesm3k/uIoHr2BSYctgmtDz1SG6QWWB0Rj" +
                        "chs1Yw5/FUsTsWkNbe1Z9F+sVWT61IefuVxbL+WDUhzDok1CmkwMDGHM+FKc" +
                        "NDDFwVN+bmJmnl52fklOZl58bn5KaU4qPOklJyQkpAFxUsOFhAVHFhxlAKfq" +
                        "cPnKK0JAUyTAqZqRSYQBYQtyigflL1RAMLehG4fsHVA2QYAOIMaTadANQnaz" +
                        "NIpBv5gYiAqLAG9WNpB6ZiB8BaTjmEE8AKNB5+heBAAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // No property is created, because it cannot be used from source
            assertThat(fooClass.properties()).isEmpty()

            val fooGetter = fooClass.assertMethod("getFoo", emptyList())
            assertThat(fooGetter.modifiers.isPublishedApi()).isTrue()
            assertThat(fooGetter.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooGetter.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            val fooSetter = fooClass.assertMethod("setFoo", listOf("int"))
            assertThat(fooSetter.modifiers.isPublishedApi()).isTrue()
            assertThat(fooSetter.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooSetter.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi companion object`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @PublishedApi internal companion object
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuyA2IzRZ2FLHdPPt86WW1" +
                        "0Oysvyu2XdvSdFts/i2ljCUbJrgFcHrMUzspoX7zzJS5nh827j9aH1CjoeHx" +
                        "4aK86j3PZN0Xp22/37P8fsf6+fP16+8z2hwWbrvAlbx4cqK91SUr/mMBch84" +
                        "+4XW/br4+pxDSa6cnfO7AK3M+cY/tq4N69ApYjQ6YZzyxj1TQkwqvJVPgqv9" +
                        "bordppqvmYvXuavHeK79xCFZlt27cNuRHrbJgSpTRVc+8vziVfPg8ZFrW43M" +
                        "ZMvX+vVXWom8PHj0UyuDqo6RiP5RiVW9/9dbcgQeDBKUW2e8xrzkVj/bobIg" +
                        "vamdO4JuFXmbCJtuze2b3JK3/s/OlxER9x5PCz4v7VtjdLb4YDkjT84LoU9n" +
                        "fqvEbnOOzJ+eWFwlxRZ3lXvPpy8mH7jqSqt2Tbv5ueyG3ek9Z/7/mHNaZM+m" +
                        "9zeqz3A+ffFoufPpDb/S1qyR6YzwXTJxrsueH9UKlbHXbQ9uuDHBy+T44j6P" +
                        "+4WPDHRMS6ar687zCj8/O3HNFI/XnOZuFwo+hPVfXmcx6V1u6r7lX8+LvZkU" +
                        "PPPm97A5hS3SIU858nf9F05Lv6q8SimdKbHst0zr5pufn8158+m4yGFupi4O" +
                        "Du4dZZYxR8+4tdwPqDp/LvBfk3T65r5+qaMNFapBKy8oRhvvLc0RfhdcP1Mk" +
                        "f+vD4yuORXQWSq82fPcgr6d6x30244jDzWab2BoPOj+eNMfxqTkoPfkkVgax" +
                        "MzEw/GLCl55k0NKTinN+bkFiXmZ+HlrKmhrom3/ZQaB2d9nnRB7f6CihU+aN" +
                        "yzyu+LYeWcixkiV005QgFo3pMhfUIm/5zilNyVfqe7j+A3+V06OGRXI/Azsi" +
                        "1VlVdhTM7P+elpZ+v+b/x6/SDeorDHMkZs5KMLHg5Hu841ZHu/LC0DWfE3iW" +
                        "7pvRoSD0S8Xi3ZfU608eGjyZp7n2TDHb8m13P21smcMuXsl2fc6qKQvv/Pl+" +
                        "orataq1lh++SkgvzeZiX9vps6Wia/qlZwn2Z4OsrKo/XdU5WnsiTsmn/3U8H" +
                        "3r7YmvXd/GfdjI081v/NexP/Lnr4dYfwyi8h3/jcuj5M5f7jIXVR2E3gWLtw" +
                        "u8ujaxbXGhp5cxxOfeg/5bZb5OsrnujNK99/NPpekWn2taJR/Jy95dObJf3v" +
                        "N4kfZbL/P6eYe8+TG59s09R/1mdNOe7b9kh/oUcSUzlbm0ruEduTG+6ERO2T" +
                        "4rc2qlS3ElRcfXaH9qJpHlPOii+Qz9Kd7KZyKeSS4p7tHy1ennJO3Xlq9kaz" +
                        "LI1ordCQmgWlOS0Lr68oKD7YWvVJvNsq9sDW++rn327N/L17CmsY/yfv/n0L" +
                        "CqZd/H7imdGFg7Wb/u1hcK1w6Awsybz4Z4ZNvMnv9JnbtiUm852T3vTCaJ3A" +
                        "80VeM6eb3j2UG/JBofoDn9ykeq+KzqzwWwVOa9oiuAtFXyv27hZb1OMlrXO0" +
                        "NGfbw2Ufgu54bJxyaVl0d+30xvmg1LRc6sj/34wMDMLM+FKTNDA1wUvJ3MTM" +
                        "PL3s/JKczLz43PyU0pxUeGJKTkhISAPipIYLCQuOLDjKAC4Bw+UrrwgBTZEA" +
                        "l4CMTCIMCFuQS0dQWYwKCJbM6MYhZw5QkYoAHUCMp4BFNwg5XGRQDLrBxEBc" +
                        "zkI3EjkYpFGMVGNlICp4A7xZ2UDqWYDQFui4LlYQDwCOMSPn3QYAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooCompanionField = fooClass.assertField("Companion")
            assertThat(fooCompanionField.modifiers.isPublishedApi()).isTrue()
            assertThat(fooCompanionField.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooCompanionField.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            val fooCompanionClass = codebase.assertClass("test.pkg.Foo.Companion")
            assertThat(fooCompanionClass.modifiers.isPublishedApi()).isTrue()
            assertThat(fooCompanionClass.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooCompanionClass.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi property with accessor field`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        companion object {
                            @JvmField
                            @PublishedApi
                            internal val foo = 0

                            @PublishedApi internal const val FOO = 0
                        }
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuyA2K9Rd2FLHdHH1rU95C" +
                        "14NhIQECjFNZHwkLCK/N3LTMYdGyzoeZt/gs3pa1vTlnZvcp+g/7EXYhX56P" +
                        "F+VT92wOyVnEX/1+rvHzurk35+/fV8f+QWY2zz7BTk6/3+qOzq+ZJJx3Hm+3" +
                        "6dhhxcl32/aiIesOnp+2K5dUHnywbt3WGh+F94rtTM7naqZwHg4p3nNBz7lw" +
                        "vXBv0LNj66e9rio4NfXKKob5idlbb15vm+GYb8GxxEgpzIYrrjt5ntuydaFJ" +
                        "j7Ykl+w5/3hfR/n6Wf6VGomzEj/tknDe+vO/2W47rRkzW5pn83j7ys5nsU28" +
                        "ZThdi3NH0K0C5y0qFheeKfQqzbpre21WYyvP5seX90rySQW5xJ7OkJHbtURR" +
                        "Qqte4/ehD3rmc9bNPXjhntW1CRVr8pkvXfupkuuyy/qubtvU08b3i1f1PZ+i" +
                        "f95m+Ysw/fNixbfPqd1O3BneKj//885FwWW61bNvxlXeylmcL/Hs/rJTJxo5" +
                        "rgaobZLtnF4vnqiRuqswNcrGi3VvYGz5SV/zpULsLxfoPvo+p0gibdauw4vF" +
                        "+pfa9jvYh3gd+dqh3rkg6Z713GOyFyd8W2f26vav5drbPB7OuStuqm3hu9tr" +
                        "uc3PyE+mZx5+kwg7uyGqbW3kyoJ2u7R6nU2vW1ieZGb6rIlfUDEvcF7PpNw/" +
                        "U9bUux5QsfnkemeH3zqlatkf9wN+HdAL+NcU/Vzy0SPJyP4K1VVBCwXNNlX1" +
                        "r1Tc4PzDJnRPNYv5kb0C352lchSPKMm1VHQsVd7AcrqipKWTJa5p6hG2nyxT" +
                        "74OSYEgF+0IrJgaGMGZ8SVAGLQmqOOfnFiTmZebnoSXGqdDEaHy7/21Qiqa8" +
                        "2IfoEIfExh8JTYYHhSapsfJMOtbCbpzbblSy+/YbP2n51932R+oPTO7kX/YQ" +
                        "nBx1my6y322vfj+n+t3c3d/PfJeP389Y0RfaFqmb/LY0yjffnEfcO3G3MpMo" +
                        "W6QB23efLy3Puxe8lL1yLiU2PdEwJW6Vw8twnmuXikz7TYz4gk4YLfmyskuy" +
                        "a+r+7XeSNEyWms3gaArg9lX4mK61NuC31od7Jdn7uvxTk3cv1jvJzr5/svk0" +
                        "vZvpe7ctZ9Jd7PDjXp+NYGz89I7Fi/Ni2y/O64gyuhW1zupB2o6VR3+Ffqq1" +
                        "Pmm05m+8edvJ0022K12dMxwl8r/rRKmbHzNi41mTynRL8f8Cny1+gcnNCy1f" +
                        "+FvvOSH7ju8X33ZdrpzHb7v9wwov3H/y+k3puWfi8401Z+++9nDN7FnPX78U" +
                        "b/63u7vfmuX2uQXxHjdnqkzfe0o29mnOdaPruUbGnaVCbSmZk3l+vj6VUjhf" +
                        "8bu3nNC0A1unqSl6Jzob+YSEKHzf/XZWT5/LWfUlAZyvD63v8pj5maOkLEV9" +
                        "UunC1ObEhxukM65Oe7pgeZLrz7evXz097aiweJL+o4hLFyulyxbvuhO67Oyd" +
                        "FZt+ucuaeRyaclM7Mk9O5u81ZavKtItPLmvHJ8yX5vwwI0eNUddGcba80IRm" +
                        "5h2WPYHXvLqOG/65UflomtBuG63z6k8+1qsb73p8LGNN6Kcak2tHKj/JnNHY" +
                        "7Fxg4SW0c4YJU2Krn1/QFwZ+w/blJ9frPJL8GXuI+8hcq7bQYyWSxqmvSjb3" +
                        "KD07NeP4IcOInugdtT0xPOrH5kq8lJgpUT65QufYXonH9oygJMvMq++RAEyy" +
                        "n/EmWWlgkoWX3rmJmXl62fklOZl58bn5KaU5qfAUm5yQkJAGxEkNFxIWHFlw" +
                        "lAFcMofLV14RApoiAS6ZGZlEGBC2IJfaoDoCFRCsMdCNQ86BoKIeATqAGE/B" +
                        "j24QcrjIoBjEzcxAXPZFNxI5GKRRjNzHykBU8AZ4s7KB1LMAoS0jA4MSmAcA" +
                        "E+xxV3UHAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val jvmFieldField = fooClass.assertField("foo")
            assertThat(jvmFieldField.modifiers.isPublishedApi()).isTrue()
            assertThat(jvmFieldField.modifiers.hasApiVisibility()).isTrue()
            assertThat(jvmFieldField.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            val constField = fooClass.assertField("FOO")
            assertThat(constField.modifiers.isPublishedApi()).isTrue()
            assertThat(constField.modifiers.hasApiVisibility()).isTrue()
            assertThat(constField.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)

            val fooCompanion = codebase.assertClass("test.pkg.Foo.Companion")
            // No properties are created, because they cannot be used from source
            assertThat(fooCompanion.properties()).isEmpty()
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi constructor using value class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo @PublishedApi internal constructor(iv: IntValue)
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/42VeTTU+xvHv2TL8sOYEdcy9HMlNYxiahxrZWQZTZaxa6Zp" +
                        "shtiqBjZfijJ3BrRrZgSN/uWZaJUTNEssmbLRbaINNYh+nH/6Kpzft3f8znP" +
                        "H59zPuf9ec5z3s/zwtjsEAIDYmJiAACoA9sDDEgBaAtHc5iVHUoXbW5nhbJw" +
                        "cNRBo5wchQEBII8L2ngNAPNoNsvWBqbTJmUD0+ayWyvt4Z0Hh8bO6Vij91uh" +
                        "28gFj+znrGEh2tZs9j7sHFe3pYX9fmxkTBDA2IiKlcrtLUVu/nRoMzH/s45d" +
                        "wE4gjBgaphvs761rFRSGxQeQiTqEAHxo6LdSKI42JCUn8Mbsor9RlgbhDzRh" +
                        "jxn0Td+FziCtXKrmg500u2C341fUpfZrcptvNekaEQpZavJzEi5lAuXm6RXA" +
                        "zuvJ1loJKg071bmKaTPNl6mo5ZBZ5J/82Qju3Yav6+vSQM1nhtlAlfIh9z9h" +
                        "xNQCdzgLvU9Qnf4pE3bQHVGY+/E9R6VwEiUjg7HG9luqUbkKgvewkXskpWS1" +
                        "Th4/Jhw3Wz0jJjnRimsVl6ytMqjBO9t0hOd6yddmIg7wvaqXFPmeUzUwxAzv" +
                        "Zlcy7rhaVGtJ32y9ZQ5d/VqCuEqj4NG7tr5pvXfiUkYDu6exZBucIQXR0Tsa" +
                        "fd9VKgP+utYq9vO8S1a4MuFVSLjH6qVXGb2fIe0VOfHP/rOAFPepjSv8GJkq" +
                        "ZKCUeydRQLLgwRwZFNIfVp3h535T9zR18iRyxBjc/ly73E2OVvC+GC+uCLmR" +
                        "2U0dTz+GVTtLadNnXZM2uI7grTOzOUYQzO0uTt/BVpvGabHQhUC/2N/rAg2E" +
                        "QN45x/kIfWe+EUaqBt54mFRTisHIjmpPMd26QpQ49939Vx7R7ILS8ON3ukcQ" +
                        "ta9wy/lQd8G+fP+xZO8++MovFdEpSYovWj51D1R4cmYfafeOYZkP2xPd9QeR" +
                        "LuUeoawwdEWL9T4zEoonMyRAI2bsWnAbPXjbmWYRJLs6//5JYYFyekFn2rLj" +
                        "6oOSh/Y725RzkLbzahx7JP8ifuE8CTXeNILZXcSisYXtoiVF0+mqbqodkD5L" +
                        "b7AnWtjSws7oy3T/jdK4wQu2b2++XwXWKhdZ1WTq/ZFVGddFh+7aLJfUiHLI" +
                        "yA1xbbzxOV4q1TaTRq9rMjQcHCDnKwzAFn7naTTnnCV2+BnpHWLJOzOw+izh" +
                        "Svz84KF/FTHOhkQwi9YiycXdAeBiUQTpeiGnwN+5KG6mJ+xig1sxI8/fITZ/" +
                        "5foUp1fuyzId8haeAHc1ued50yAPqf95XycFC81uV1i6Cpr2ipmIUSiO0TKV" +
                        "NV1B84YqRyUmp5NqqQpIM21yTMYCW0AHOtSV/YRtTNXwmj8sFn3epJmcHftC" +
                        "8lpwjinqg2bexOgrj+kyO5Xh8ehTZg9vnymI5DzVYGxkeoEuwqLZhMcqNR5+" +
                        "vBPNn2TEP55QPao6lMroXFmul6pevGhNEDqwKi3ST/33mQfwbJHSWaqj7ZDO" +
                        "IO85Q+cjhHjkhmXmjJ5fkiUFVc97QW5Ja5XOXm7sunyUjb66LrKYEer/9qV9" +
                        "nUvBW6E4+ETdaa+lqt/yo53O1BEXNAafx32ixOsPV05KNC92qsuvTUOY13eB" +
                        "J6Aul54YempyPxRpDDIjKcspuS+jzTRW5OJis3rUQZHDj7B6RnO514Ypn6JB" +
                        "SlmRAtlt9ejCJH0l8OP62JhYaclRqaKh38yJG2+eWbq8iwkuS9yjpBBOR670" +
                        "mDcpTaicUU5QTlAZWNwfMemJzAyBj6nNDF3xVg6Gr0C31hFM2c/ZXAgAlkR/" +
                        "to5A29cRikT6YROln/QkgczBxgefxUlUR/VYp0CdCo9AH9NW6OZoP/QN9MNf" +
                        "DOrncCIK/bHEmt67v5rw3L+IUvxG4tVNVnOuRsiXaZrcCpg1Dp3IihzPWudP" +
                        "90cAJ97gtHwUfSeioqaURc3lr947DHIYPv/ClDy4ZHmOXnlcz6U1vXTBJjHs" +
                        "XWCYov0iqiZGsgLR3nzSNTs/GV88v1yP2OHsIDJ+4NFUkLeyzwk+18cwhFZK" +
                        "nJRovQAzTenpsIyFfoCrTqoZyQ67JAbmVJTfr09Zy7rCHZ2b86hKLSHG5zRj" +
                        "elU89EzRIO3Gja9WejRsglw+E+ODzzK5ldesGXTktdAbTcKO5l83LBQ7NfSN" +
                        "6DPjlRWcwoFxJp+dp/sc6XW31a2+xSv9XFEAhI8g5UVHVbUzwvQImbCzidgS" +
                        "21DKO4jXfiuThhdrBPSGqvDRlrLBk0+j5E6sHviitarq9PouTq2+RCwdqZtS" +
                        "tVxe2nXs8YajxRXrpKdQn3uQYi7ukthIsFpauu87t+rwr75NAq53EJEO4y9D" +
                        "L07vMDTOYe/1XtIJmdnNGMz1KWnZa/SgluETkxp1QC1esCS+TZqFS51lZ/5B" +
                        "S03q9poB5aY0nWp85+r4NbhY+JA50TF50Se/OsvH1G6E0rm3gT4l1d4zqT52" +
                        "uoOJ3uUUwOhBcZiwgdlYi9r4OmMXWZ5s+G7w7oEU6uWyJaUtP43HAHxRQQBQ" +
                        "3PEzPylt+ukbZgPxvkE6/qSwAN+gU4GkM+QA4jdjEXA43NnNPB3zBkdvojOB" +
                        "vxDqDL3QCdpUUfgLoQKCYODvX7bjdQvm38c/ov1Hue3jscXkvyN5M/+J0D+q" +
                        "bW8O6Du1XcLAzwbsR6HtHVD6TshbFPi/OouxERbZei+0eQ4LAABLdOv2X3jS" +
                        "75UZCQAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // No version of the constructor can be used from source
            assertThat(fooClass.constructors()).hasSize(1)
            val fooBytecodeCtor =
                fooClass.assertConstructor(
                    listOf("int", "kotlin.jvm.internal.DefaultConstructorMarker")
                )
            assertThat(fooBytecodeCtor.modifiers.isPublishedApi()).isTrue()
            assertThat(fooBytecodeCtor.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooBytecodeCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi function using value class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo {
                        @PublishedApi internal fun foo(iv: IntValue) = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/42VeTTU+xvHv2SPMCh+CF1HohlDlkYSt4x1pkn2rRkMN9sQ" +
                        "Y82aixIm69xUZItB1ixR3TCWZpCsyU9jl2UwaCzRj/tHV53z697nc54/vud8" +
                        "zvvznOe8v+8XyvgQmzDAxcUFAIAMcLCEAT4AoWemCzZEwpUQukhDuN5VMwgC" +
                        "bm7GDrAABV2g3TcAsIqgUkyMwZAePmOwQhe1u8oU2neGNnUDYoQ4bYjowZOq" +
                        "TZeNwD4KRlSqosVyl1JHB3VianyKFUAZc3KVCZ0qg+29pLHXqP87xzGAG/DD" +
                        "+vopebu7Khl6+VlgPPBYiJMHxtf32yihZsY4cXPhXfq6u9YjWacnCKeTOlJv" +
                        "h4P6vOTzCXJ53KlIbxuDOzJ8p+W62v9oUdJyKqZIH10+bFXOUqGbVglwJ8cZ" +
                        "yUdLvuKW6RJLWmy/TYAzfeiwj5v0gK6Hr77u7PADtSv1OiPPJDRsP4KxCSRb" +
                        "KAWhyCqTvUQEn7FVL85fmOiULJ6FCwigjCw+6EsTukRZH1uEnOTlE5S/YnCJ" +
                        "PYpes8jFO9ON7ubhrXumVouxNO71z3c4WkdUV9l0qPkstmk/VwtWX2Sk98eh" +
                        "DaRvdj8dpjfq52bLJEbzSDazXnxocj3p/YOo+EnPgXkLvDFaM1S99/1kWI41" +
                        "Xwb0TZ1h5Mqq1SN/Cac2H3+7rfC2jPcrIu8qc2/9+fsajOe3uqjihZAENjXx" +
                        "/AcxLLykvGU8yOeDX02Gm226kiNh9gps/Lzwu9cKFTZCqaSJUgyPmEgKcYAw" +
                        "nXbJQtoltEeVksivlqzO2CFndWqJoDL7O4fPdBs3z3P5rnm6Rd5v8FRjA7nm" +
                        "Gmyqq1puaqH4aqHNZ3G1ZSiU4KTCHNmm30e8M8fWfaM6FemVhJl+MDCuXteG" +
                        "ZhZJ2bIOF7lPxbkOQzf+UxkWHyvW1LE0MFJp30mvVng/ZUEufBdjqzoKs6qw" +
                        "86X4ISo7jBR1cHCGAI0lFZtxbM1m8kymZaqel+DW6sSLYpJEGqkviWm2lfe0" +
                        "0JS7RyIXZrIq3WkK2wzGrAXi4NMt46gTJZRUKjsyjJczLfu4zfFekWF9V2F7" +
                        "BLu+HlLry/yHlLKo0SCTwfSJLWC7ap1SgyfkjG8JWK9fHah7ZJUQUCEynsKj" +
                        "gDl/g5FAMCGmZje0aGqOjuCLREfAa/cZsu25LtheNy1lDcpRy3oLVQp7FWZ1" +
                        "VONISb2LTwC5ZDsEXzrgIVzKqY5LLu4kuVuWRC0O+QW/simtL3C/Glm0kTzX" +
                        "+V7oCzNbZBAaDbXWfmyfrlYAU11R7Au1kMp6J/r5LmjeIWImQrQ0Qv6C4IUN" +
                        "BINWNXl4dj62jiAK01HAR2SsUVkgUrT+rBfU8wRZh9WzXGGB2u34rMgm3kTv" +
                        "3AvwT3IFM5NtdvPlSMmx6bBrOoWZzqSQzpey9btEB1AwOIzq9Fyy1s6Ncbl9" +
                        "SYBn4fLxi8dpCfV9G8xGvpr1YCMnNpUtfo4PhF+c86BZHGV0gpkJDTLKeF0P" +
                        "WRDB/pqiT1xUdovVD4U3MprwHUnd/FnM5v7bF6mIuzsc6xm+7oOtpg1WpEG2" +
                        "KOhMg6PD52f3isLMnRuwa7Kjr6OWQm+pjlXNHm5f75M5uj0vQk4+JjwjZRX+" +
                        "QtNerutTiewoOSSUGZ/fGqYjuyEUFfloSAYUMlZtoay1nJ84FroUBhJ/FMKS" +
                        "1dOIKI5VFRd+3hgZEcnPO8lXQruni919+6e+1X8jvMtjToqL+mfDNoZ0W8Rn" +
                        "JJ0loiWiJUfWTwfM2sOIPtAp6UXaHVcJb+iG1H4cgSXcLHXZAOAz58/iCHQw" +
                        "juA43A9JhL2CwPXqCIQ2+HsMchf1mt4vuBlepMf6RPCBjgF7UZN5NhdBCeYJ" +
                        "6vWaGDL3l7xBxY1Jef86HnEi8Yt8S0kmD1v6+vRipkbVuYYL4aHbR2jK9DN9" +
                        "fjl+geqaR/gZI+xOXEiyWXRRe2Jz7u51lbeQ0xwzf1DzwrrQiwutama0jfIx" +
                        "laGtSwoxwcwl+GUxktTqF4yWeLhbN3xy+La/U0wCXX6wiNpUq3chbU5WjJ5l" +
                        "qOILw6IFL040LWv2OMpXQztW+nImbhxKZyEEOHM9TO/Ow3g6Np+PaSY3Tq3K" +
                        "MYPKidsjVXHeH1G5QY31xIDAzBfe4Tf9PZI4B8Zc0LWBDe98WGNztu+kiSlZ" +
                        "KUueo3q49MjzzofL3VWRjK82oBKPfekx6CUqrWKayofTrBkFosyUDs6ey4bW" +
                        "89OH7rU6nugMPuX1SlEF3GaYishPll10tX76e3oQXGPuVXd8hzSM0Pr8WXXr" +
                        "V7EKTqQSRhvpmh8r02hY/NZOiPzmVtnyy9RPttHIUzZ1CKtzGmh2bTLHXthV" +
                        "bhh+ejL2FVxNaVKc6cA+xuBLQKPWW5A08wDjxsgQ4V9MDw2Tem6JYMBNwdGU" +
                        "objCfpdouZpgNA/Yg0lTXL1mXih/lmfQyMGy7inKvHCHY98m9918IKMsAPCS" +
                        "9Wc2Ed+zyTd6emKue0HccX4e172ueeKc8R7Yb35xQqPRLnvtGPEWnd2STQb+" +
                        "IqOlVFAfaE9F9C8ysrAKA3+/cpCa+4z+vv6R2D/KHXT9Pmr/rri9/ifw/qh2" +
                        "cDmg79SOsQM/+29+FDq4AfHvhLQ5gX+1WZQxO8f+fba9c3ZvpBzO/a//AY/6" +
                        "OwzwCAAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // No version of the function can be used from source
            assertThat(fooClass.methods()).hasSize(1)
            val fooBytecodeMethod = fooClass.assertMethod("foo-Vxmw0xk", listOf("int"))
            assertThat(fooBytecodeMethod.modifiers.isPublishedApi()).isTrue()
            assertThat(fooBytecodeMethod.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooBytecodeMethod.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi property using value class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo {
                        @PublishedApi internal var foo = IntValue(0)
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/41WeTjUexf/kT1jGUMjhHJLhKExGq9EsmSMhjR20Rhudpmx" +
                        "XsPIRUUmCUlMSWVsWQrhVSRkhuxrmhhL9iwNil7uH131PG/3nu9z/vs+n3Oe" +
                        "83zO53wwqF1cEICPjw8AAHlgZ0AAEIA2tNJXOW1upIbWNz9tZHjWShVtdM6K" +
                        "G+AAHraCN98AwBKa3mKGUlFtB6FUlFrpbaWWsK6jzLFLqqboI6fR7URameWC" +
                        "qYq/kimdroxdaFVrbqaPjo2McQIYFC9fkdjhIuRWJa2txPzfPvYA/AABH0BQ" +
                        "8/N0VzvtQ8C6eBHxqjgvl4CA762EW6F8pc5BNudWPHUyFXCP0LhDerJvB0K6" +
                        "fBRzKAcf8Ceb+9mZXJUHHTnY2nT7lZoOLq9FTmJht80TjmL9WyUAf1KcqWK0" +
                        "TC2/fKtk4mzTFYoR238O+X5tLqj1bu23jQ1hoPxTpd7QU2kt+/cq+ASaPawF" +
                        "rcwpT51PUzlqj8jLmRllyORNGomIYEyxg8ZylFYo5z1s2CFBkKiihckp7qi5" +
                        "Z7N8ghNtzm0CghVPNctdrFGdgTlOEhVpCI01p2efJdccp8pVELOLKd1xziZy" +
                        "f7QVDsxVG2dT5a9HC8jUcxrcNbuY2J8RdY3l3TONJaKctcMRnf0s0n1bUCrs" +
                        "TcVp8qclm8xAaVyjf6DDekRjav8n8Y6S7Msv/lxGCvxeEZU3E5bApSmVkxHD" +
                        "IUh7sEAE+w8SnqV62KeoXaBMWiBHjkM6XioV24kl00YLXAQkxW+m9VDGb53C" +
                        "yrmFt8NbrgtrJiEWNxqyGDrimDvdjIGjbaj6ab6AZW8PcnqVtyYX2D3bZA0B" +
                        "t17TwYDKYfXHfMuLMBhRltJUg123vxTjvr3nalmyuU+iy3hGzwiiotGZnStr" +
                        "zzmQ6zkW5z4AW91bQroWK1nXPN8zVOLImCtT6h/DNjzuiLGHDyNtih0CWgjo" +
                        "kmZTZT1fo0URJkcyPnXPsh3r6B3rZEMf0fWl0Zo8mvQtWlci22r9QeFjS/52" +
                        "6Wyk2ZIcwxK5FuqyHOxrNP5qBLM/vyWZzm1OEuS9Rd1nt69TfMDYHeKI5jY2" +
                        "NNf5Oj14syhqOMSsN2V0HfhSutLyjEi5P7IuYrtytqci0yYhqFh85KaAksvx" +
                        "S4sJFLO0ZGrVK23t4SFiLnRIZTl9UaEp2w3f6aGjrtUiYV2Jhbdwl7osDWsJ" +
                        "5Ve6+Qc15H8JIxb0eEEKeBG+SXkMmqd1ftRsHyG01q6g8qHnWXLuatIUo1/s" +
                        "K5sq3guLhtnq3nNM0XyIhH9S7grHymZ1QD/Hg6edIicioQWRiidET6yiF5ml" +
                        "rN2T07EVFChST4kYmbpM51CVZXZn1dCPUxSclo7xkYJ1m4hZ5DrB637ZJ4w+" +
                        "Hnw4wWp0mH5iLvNhnHRe7/EdV1oY478KlZtpTuBQFRId91ym3MFj8UzTvIjA" +
                        "zJl9BvuYCZVdq+xq0LOVUFMcl8a6MM8g5YDrA1gWT9EcxcqMqTq8+LJSdUYc" +
                        "f/KmcdqsukescbhR9WIdsTmxTTiLXd99xYCOjt/gWUkN8Ox9bVllQ+vlioJN" +
                        "VF1w+vz0Ri7pnGsVfllh+GXUfPhl+IfSyd1NK13yEl+mxRuS9kAmZG0iarQd" +
                        "D7Z+zFcYbggLZ1/LeU3SU1gViyJn9smDwz6UYdV1FnKufwifJ4GlMsM4stqr" +
                        "0XmxcCnI82pyJFlYkAXKZ97Qx2++fWFs8y7S70nMISloIBW52qf/SmpCxlU6" +
                        "WjpaZmjlSNCkIzLNHzYmN8u86i7tB1uV3ZYjFWkPa30uAPjM+ys5Au+UIyNf" +
                        "35+UCG9pcaYTA9n0GSzIiW8HQXGPFffXEQgisvFgUU0FdAy0qyGat9NbXHV6" +
                        "AM8WCsmIaHA/bwLfK0uvyz7xJngApHFStnWjNagtaO5j0Pz7iNpvQHA0KCZ5" +
                        "ObpskGGwJSlEGM9033F/7/cgodmagWLrVG2rGNZB4Yo/XlML87FnTeQunrzc" +
                        "Sqn0YnKVTK07nmmEEnNjyZS7Po4xd8G+T8N5vBtsiZZQ4zfRV/g9Vg4LSkL9" +
                        "bz55TZUzMFyM2x1DwBMKH52SDk3CXKy1s//W15cw4Rcnset56MC6g663kFCu" +
                        "xuSdMouB+yG7rf9E+YhEd9zKeI1Ly4UbfFx6QeosWHOQDkmYRTzq1NjztfDQ" +
                        "uxhlx5HkniumhYctMNlAeo0khwYaOSaqJOaRf7R6nv0Q/VtR2tXu+dtX7wmB" +
                        "D9yeZyOSNcLuZUWgPWIu5H5pScFi3lxp3BheIs3Xr6XCZ1kmWpnd8Q9yJOAG" +
                        "5fGGDGgp/THSUfLuf85Vjmujj5HIvelJGcGSCJ2YZgHh6t53WMZ0dYCnrnq/" +
                        "qU3QBm3wg4WuAsPaqWq02/GjJnScXUVmqbTxm1cwy2qPe6SvIWYCp4aFEgkB" +
                        "9R1TT0/mdyZ4FKNNCS+j4XXLlxR1qV7XgtelzCARdYq0WsomecbWVX2fpdmB" +
                        "wPRTey3LUhRRIK60OniBJF1Oy8vNuFzf8/6qTL9vKjpJPTchTix1xJYVO3LI" +
                        "quBUd8Oa8oF4WsDlq/e0TwYsn4kf9iY/2cu+/YKJc/9UImEc1ve7ro/9ddwN" +
                        "OsfnuBr9Xe90IqeYKZe5ecv9G1/un9O2qavZBSPjh07omTrbORvKyVMgaRAb" +
                        "cUU+Oh+Ly417kbMnYpvHC2UI7ghOAOjb9SseS23x+Pt593a56KPq6Uvwuuhz" +
                        "3tvXleiF/05onLOzs9tWXoh860x9RW0A/jrd1rIhXeAtFOhfp5uDEwL8XWXn" +
                        "Wd82ET/GP1qKn+F2ruW2F/g74rbyn5zBz2g7hwP+AW0PN/Crxf4ZaOcEpH4A" +
                        "es8L/KvJYlDcPNv/ubbeMQ4AMNgGBv4H+qo4o5EJAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // No property is created, because it cannot be used from source
            assertThat(fooClass.properties()).isEmpty()

            val fooGetter = fooClass.assertMethod("getFoo-RVb1_dM", emptyList())
            assertThat(fooGetter.modifiers.isPublishedApi()).isTrue()
            assertThat(fooGetter.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooGetter.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            val fooSetter = fooClass.assertMethod("setFoo-Vxmw0xk", listOf("int"))
            assertThat(fooSetter.modifiers.isPublishedApi()).isTrue()
            assertThat(fooSetter.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooSetter.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `PublishedApi class with companion object`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @PublishedApi internal class Foo {
                        companion object
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDLwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm6hIawMjAzLzgn9O8XA8Nn3zGkfb129i7zeulrnzpzfHGRwxfjB0yI9" +
                        "L18dT9+Lpau2BH3w0i3U8jpzRjvswzn9kyfPPH766CkTQ4A3O8d6Yc31lkCb" +
                        "zIE4AKc7hBg4GUpSi0v0C7LT9d3y8/WScxKLi+GuyA2M9Rd2FLHdvPtvQuyC" +
                        "BQmC9gEB3FLiHf4OPhvaUiYH9UgJHb68RTbx1luemzNza55s/8Hfwa8yS+LR" +
                        "YvtTFctFVC5O/236fF71+5nx+/f/ZfggdF5SSbunt7+6/WPfNVWDNq/p/BVz" +
                        "ikol3m/dtSjX+4NkV3SQ5++ug+t137trLJBQcuhU3OB+ycDHZJ1wrtGU7bde" +
                        "rkj99utyUOz3/p2ToqYKGK65KmWbyylZzv43Qkvb6OjlH70/jny+cm2rkdnp" +
                        "22Hpu21ed+Z8Tvrh0lHoY9QTO9EwRPq//GbJRK7LyR8KJiduXLj58A7tHO3H" +
                        "QTNnaC/V3c5iXBh8O23esi2/zzz1y/8cpnV24oscee9V/rHXylufyuZZXt3z" +
                        "74C6oW16TfqBA1V6Crp7z19yXS1Sek0i67l64IvQvRdWz5nfEndTf/Ornyed" +
                        "Nrxbu4lXbI/e3z9BPKfijpb/KH22oqvkdPe81Bn107W1fysFMmemaIverg9f" +
                        "Hr/idV/CjBwP1VmV+1U38c8x4LnMe/fZS7vzS067H9UI9Er5OkVaeYVlzfrP" +
                        "a3TLllTPlj5783861zWPwwkVD+ZMnvIn+VTXk/Q5TE+4D7/hTN04ubEm8SnP" +
                        "08sPP18WvHHUdNNFVoZkjpPTCx+v2tm1cELHvuQ/ytbOT7Xz5vJpM0472dYi" +
                        "rT5NMyol9cT2lvQn5ivqZkusZv8gfEZ1x+QEuXLhO4f/svBvKGBI+yDQzxdg" +
                        "wMEr1svd1TZDz38m6z9wImuasPyDDBMDgwIzvkQmg5bIVJzzcwsS8zLz89CS" +
                        "29TA03nChiK2355Y8pjkKi7Zo8kYpWLl23pkEQdnk1bvlCDOALPIEL+krsmS" +
                        "/qZlStMiPyr+cQz30Am4oirfumNey/Em3tm/LZ7Py/7943f/+vsMNgeZ23ZN" +
                        "Pda2/PHkDwq+s9+aWIpGLd3FcyxAunMlR/JC/0OL/3nw5UjKOb+ceemUXfqH" +
                        "bdt2M3Zp/NRYcCZgg/yUxIySf3uL5i/celVyuVmYlYzv5IBMbWPdRx+m1uir" +
                        "bcxdfutGjtGGzDKNTRvPfNu+225H9N57Mn8U+yfPSb3xc38uz+r9LO2XuhZu" +
                        "aDKY52Nr1Lz7wU4GNrMyVssD9lrhb4NMcwxt5pxnmm++43z255nntfcU/TuZ" +
                        "fSa9umb63jVhP34+D1p4KHue9P3kvOAP28VT7ySs+GfWskalID3n5K4yqyk8" +
                        "2rJLn772vDT9pvXFfs7I10uYBctdl8XsZ2n2yJ65NTmxR+Xw3+0vVaRPMD/d" +
                        "0WsunBuqtPuUhlTdwtKs1qWXeaKepB60nWBU/t/t+27n2BdxPjt/vODWSres" +
                        "nZ6kyW6bE8QjxrziEHtWzypu74/Jp2o0+c4VfZ+zYB+DeNOeXR3eRyp3uM+d" +
                        "3533SMzswhn+Pb2/p8wX2ue0eavcHo7VgteU1jwuatFe4b6jy43/zIRnUfys" +
                        "zPcf7HK8uOCLMCjB7NbbLXqPkYHhIhO+BCMNTDDw0jE3MTNPLzu/JCczLz43" +
                        "P6U0JxWeXpITEhLSgDip4ULCgiMLjjKAE2W4fOUVIaApEuCSj5FJhAFhC3Kp" +
                        "CCqDUQHBEhndOOT0DypKEaADiPEUrOgGIYeLDIpBb5kYiMs86EYiB4M0ipFy" +
                        "rAxEBW+ANysbSD0LENoCHdfECuIBALYpzYDVBgAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fooClass.modifiers.isPublishedApi()).isTrue()
            assertThat(fooClass.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooClass.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            val fooCompanion = codebase.assertClass("test.pkg.Foo.Companion")
            assertThat(fooCompanion.modifiers.isPublishedApi()).isTrue()
            assertThat(fooCompanion.modifiers.hasApiVisibility()).isTrue()
            assertThat(fooCompanion.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }
}
