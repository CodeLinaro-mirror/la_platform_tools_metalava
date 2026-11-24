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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Common tests for implementations of [ClassItem] that are `data` classes.
 *
 * Contains a couple of tests to give an overview of the members and then some more specific tests
 * for some synthetic methods. Although, they overlap with the overview tests they do make it easier
 * to track issues with the handling of the different forms of synthetic methods created as part of
 * a data class.
 */
@SupportedInputFormats(InputFormat.KOTLIN)
class CommonDataClassTest : BaseModelTest() {
    private val simpleDataClass =
        kotlin(
            """
                    package test.pkg
                    data class Foo(val i: Int, val s: String, var opt: String?)
                """
        )

    @Test
    fun `Test data class fields`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val fields =
                fooClass.fields().joinToString(separator = "\n") {
                    "${it.name()}: ${it.type().testTypeString(kotlinStyleNulls = true)}"
                }
            assertEquals(
                """
                    i: int
                    s: java.lang.String
                    opt: java.lang.String?
                """
                    .trimIndent(),
                fields
            )
        }
    }

    @Test
    fun `Test data class methods and constructors`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val constructorsAndMethods =
                fooClass.constructors().asSequence() + fooClass.methods().asSequence()
            val methods =
                constructorsAndMethods
                    .map { it.kotlinLikeDescription() }
                    .sorted()
                    .joinToString(separator = "\n")
            assertEquals(
                """
                    constructor Foo(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo
                    fun component1(): int
                    fun component2(): java.lang.String
                    fun component3(): java.lang.String?
                    fun copy(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo
                    fun equals(other: java.lang.Object?): boolean
                    fun getI(): int
                    fun getOpt(): java.lang.String?
                    fun getS(): java.lang.String
                    fun hashCode(): int
                    fun setOpt(<set-?>: java.lang.String?): void
                    fun toString(): java.lang.String
                """
                    .trimIndent(),
                methods
            )
        }
    }

    @Test
    fun `Test data class constructor`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val constructor = fooClass.constructors().single()
            assertThat(constructor.kotlinLikeDescription())
                .isEqualTo(
                    "constructor Foo(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo"
                )
        }
    }

    @Test
    fun `Test data class copy method`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val method = fooClass.methods().single { it.name() == "copy" }
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun copy(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo"
                )
        }
    }

    @Test
    fun `Test data class getter method`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val method = fooClass.methods().single { it.name() == "getOpt" }
            assertThat(method.kotlinLikeDescription()).isEqualTo("fun getOpt(): java.lang.String?")
        }
    }

    @Test
    fun `Test data class setter method`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val method = fooClass.methods().single { it.name() == "setOpt" }
            assertThat(method.kotlinLikeDescription())
                .isEqualTo("fun setOpt(<set-?>: java.lang.String?): void")
        }
    }

    @Test
    fun `Test generic data class all members`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    data class Foo<T>(val t: T?)
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val allMembers =
                (fooClass.fields().asSequence().map {
                        "${it.name()}: ${it.type().testTypeString(kotlinStyleNulls = true)}"
                    } +
                        (fooClass.constructors().asSequence() + fooClass.methods().asSequence())
                            .map { it.kotlinLikeDescription() })
                    .sorted()
                    .joinToString("\n")
            assertEquals(
                """
                    constructor Foo(t: T?): test.pkg.Foo<T>
                    fun component1(): T?
                    fun copy(t: T?): test.pkg.Foo<T>
                    fun equals(other: java.lang.Object?): boolean
                    fun getT(): T?
                    fun hashCode(): int
                    fun toString(): java.lang.String
                    t: T?
                """
                    .trimIndent(),
                allMembers
            )
        }
    }

    @Test
    fun `Test data class copy method visibility without CopyVisibility annotations`() {
        /*
        Currently, the default visibility for a data class copy method is public, regardless of the
        constructor visibility, unless ConsistentCopyVisibility is used. In the future, the default
        will flip so that the copy method visibility matches the constructor unless
        ExposedCopyVisibility is used.
        See https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-exposed-copy-visibility/
         */
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    data class PublicConstructor(val value: Int)
                    data class InternalConstructor internal constructor(val value: Int)
                    data class InternalPublishedConstructor @PublishedApi internal constructor(val value: Int)
                    data class PrivateConstructor private constructor(val value: Int)
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/5WYdVSUa9fGh64BpDsH6U4FRHKIYSgJSekWGBgYUhAQJaWk" +
                        "O5UUQRqkYeg+gHQOKQgSBrzo+b7z6vnec77z7mft/5517Xut+1m/59qXFgQD" +
                        "kxyAi4sLAABYAD8XOYAQAFXSleNT1QALQOU0VMFK93T5oWA9XSwAGqBomPSy" +
                        "HwA4hg4OqEP4+McIIXzcw4Mj1TqCkyLLG+78alBeVeiYZ0mNzqEaH4xbbXCQ" +
                        "R/9wWACJHFzbWN1AB2hBcHArybgqb19Pkrhurb88BzMADwC38YALuDnZCWh5" +
                        "Wjo7WCm4unjA3T2t4K7u/FbOFh4ef5wpQFdbk16P/PJgcO9g73Ub72hSh5Gu" +
                        "vklSLcaMI1+N0Wj/sryqEBvFA/6mNWHBwTIRRHgrdZRqoDxZgNBROwejY97k" +
                        "TBdzwLEEzVqDFOghAkZ2muFBvOW/1bSVIRX47fgLE6DCItpC8Vbca9omT6fy" +
                        "ho2Gqe6254ozZRHMg0FCKVL+2caMZ7vTLeUMxdzCWjTjxxUfIt4DjZBZ8+Za" +
                        "kAS62wWxQjlydir7HpXbgoJSOBh3DDn7tF/PeIHC5DeunIaFUJ7Kzl3bmE4i" +
                        "1FPmlSTnMQK2uPm0oq2W1PmNCxY9PHL367zoqyjcf3vOe/Dl8XJHIbOT2I1S" +
                        "6Eh6QuIEdpzUSGxCW3fa8W1X32CLt5OEi7U8+wGpu/0cT+WASDUQXI6kLHL0" +
                        "0y5QoRBJFWQpAHs3emBs32g2lBtuIizITic9r5036XjBx1A55u2dv3IuFsZT" +
                        "qHE+HIEnGUevrE3pHRLGRjfPdwiluR//OHIBY0DYjfspulncNIVjm/jzWqoQ" +
                        "WuitK+mJ0yf3khEZMb9F6Pd2lD2maVaScG+1d3+etGWyHTUc2K+1J+6SJrb0" +
                        "kX7HmfvGe40+wdFweWvupseHJktGMMxKWDuyeG+/9ESfnEuXS9a4hYfQl74/" +
                        "uaLm2Wud/AVpaZl6a87OiNyUXhzsb1xFLpy+YXBihxfw7EcftZhkZFL0imbl" +
                        "r2TndMGJDp/We2uKnGwdbzvtfGw+2cttlLa3RRZpmRqsCrpXb7BMMDfZ6Ut4" +
                        "gOYE020hI4yZ/qV8dLau9MeodTqfnfx7gf6N2Xf58snKSaCa6TRInyTkLtBz" +
                        "b3zK1BfNSDr3MmJXqnSohY/jq6K49in/S3fXkKcV0hzDY1+gD+r9YvY/NMVB" +
                        "KqRGcNpGm/i9PRFPJOJRln4ntMQXWaCMw0JRqzv2YXpbFTwe/Oy36RjOa1jx" +
                        "AxDrptjrW2G8mc6+79mSUfD0y8pY8X7NWL7nlxGL2RvqFgmJQuNm7D4yMfJM" +
                        "nzcKkPH2NdSC21RWhu5oKsX973LODtVTTOhj29bDJwORa/vIvUHMz/o3EtVT" +
                        "Hs+tJgbgb7SjyEy8/dPIE4ay4ofYre9qzGRgDPKGwYRe3YkT5Xow2npJ1Ggt" +
                        "+IDxToc90DCOgIidBFXIvuEanzSJryl0uoxnxuD9DZdM+Bg3YXOdhJ9pZWet" +
                        "1omcFCxwyOyQeWrOui0X2e5aP72IfQdpVx9TR8aYHZckUH4/6zMe177bjibn" +
                        "EqiQUYNBwTBONnn7LOTE/kidu/zoFc0313URgeg677kqVpdl05JqeS9wvG/x" +
                        "i8a5eKYVxm3ox8UC5OBD+bdCrmtJ0PcnWdFnsN4edEou5g6cczcy29BLXASI" +
                        "mi81IjamQITJgrnDPHSX7yoaVz8UffYK/TtfGsffIPAwAYCnOH/HF9af+aLq" +
                        "Ardxd7Fw/mvCPPlfwuz9QRg1Y0OY4RyReenbUUc5nhCCm+Yq3K0mXr025imG" +
                        "dgekAULUCZeh7J8tV0M02aYVqSbYsbfT3yGpRxZo0Kjk/0SYr8SAutddsVRe" +
                        "nIOf9YdqpC1wGBF2AQLkcvmsX5kJe8ydPzK4NM6Xpj6fltiPUAG1x6Z5SSvP" +
                        "22lFTqO4FfJuNaYpkTowcIuGrRMh6MQxqt+MjsU9DubFiiTksKbmzjbwjJAr" +
                        "nbUUQ2c2j5SrPH/vuVR9R0iOk+LdJUWnWMlX7pyVN0yTg1DjMpz5I/kEvyyB" +
                        "ejfOPpOTbstOz2K2/UQNTAFh65vwXMfAYboK8/v8WQ/a3vTvL6T6YhXz+o5x" +
                        "lBKOij15x4reWyp0QqGsKatkthr4akFiwm2+BJpAMw5TEvddjmLt1j/+4FAP" +
                        "Dln91LivR+NCrkl1y1SkhyOtk7OP1HU0pg+cTXVeBOTtb5s9xoAIu/FGSpsl" +
                        "TBM6tmMTS9h+44ZKIjK4Pugm1NE/p4xK6usqiMG7Rsybd/ZuydMo+aOog2vE" +
                        "3HJFicJaD+Ym3xcIiO8F5W6J4sdxuV50eEp7tFM292NxZL9pSchB5KvExaLX" +
                        "LBmP59jl+W+rROLy6M2NZRN7PYUXPhijWDM3KL7bPQpXgiQcMGcyLxQyyIx9" +
                        "JvbieAETT5gg8vLTCRPOieIgClR/lebItiUSxj7iIZDNtukXDZwlL3dQIwe7" +
                        "iC/ZoawR6o5gL9l9uzXng42JTtcpnwVUlGUW4yOQpil8J0ZjoCFfmFaITutt" +
                        "YlxSDUdqs/aSjtWCJzfH9PYm5R0sMVodxOzRXnfPaSODP+sXzvdbBvVN/RjK" +
                        "WVZby+6dZMO1D32kNFCxu+1HQGpJA8xKZ75DX3pT++dmq2hLtQ6VV8lFlefR" +
                        "OZZ34dbDG7fsPpHLPAyXZ2LxSWa82GeBgNIZbyNvSy4UX/EUfBqXES5sXxq9" +
                        "iKTn4Y0XDiuK9ImnyueE5xoTpfOGg5vQQh6RJHEhguBvTngkz4ZDkWA0IfUY" +
                        "lW/J0vNZg8PqOcTY7f2heMGJGcICOdKM872FdsqqJLODJLPq6qdrKUNYbX05" +
                        "TMlrcyBYgrvPp90POarYE4Xt7NigPvdImQTck/trHxGqEMcLqvqP3kwGfmau" +
                        "loq24kKOUeKwlvD0kbmKuOc0mcty49I77SrzIZQhS7DSFkKjjBE3OtdeaRkQ" +
                        "H5NxrugpWfzO4ky2VquycJWEF8BGDt20eIl4PB8swlXXk6ziFAhKLjqG9eIr" +
                        "M/AsEy0WShJYF998hTB6Fcl3QcIYZwb6YJo2OElLSlnEN19tn75YfSjDeD+l" +
                        "nRcr9BzAVNGOO8awaoHbcjOBRScXvil/EooihDVtvCPrAMZxfHnwnTVhpU0a" +
                        "ZNesSfpb1vD8J9b88DQe9jbWfw2daN2F79C5WpidXZi1eDyLZYRfoz87buPQ" +
                        "RDDt+CQvSB+bKqI7Vte/YS9moE2wenj+5mdLQfZvZGmoyHBiGZJ7ZFhWZCEn" +
                        "Q79Dh8oEl0XEY6SBY2sN0bpZGfjoCqPN1L/LTeH+Y87pOkelCYqkD+52DVUH" +
                        "M+1hepnH87Ln0RGHX9moQ6qQCO+xFZfyc/uAiZJnbJbAcK73b7tFQ9IklA1o" +
                        "PvcbZ7k51818FLlXx0FjeTE2Jbo6Cn+pItSFqJ94TZ2SCsYZ5+JEsXFYdhJR" +
                        "uyQJYEbOO4pBysKyOIOROQbN+41bRhXUHAiKysubCo9zTGfW3GaPnbqhkLIv" +
                        "814hrAhTTTeLBB0YkgxC2JuaGM4GD/ZPZeGzWi+ZtmtewQ1zvAyOU6+eNxtF" +
                        "Ruap51MFCF8y68l683TULdAquD46CR3RUqMxdUkeEncudtQ2eDAGMxW3Om+2" +
                        "v7AtZrT3jm4c2vUcKBeeH86OgUt6kz873HRT6KG1oI4fuqUpJEChXLg6fxFh" +
                        "pXOkT2sAg7ojXlLSSc+kHJeIEqZaQVJNoEVSjBpcUBO8yt2UNtP+vs/N7Qxp" +
                        "Da1fab6dh7IgFqVHBQ89e/NYe+9UWVwAZyfMcAcDIVX2eGYbwQnqs4vhY5Tc" +
                        "H58hMWqAxvG5pvdaX980YK1QexkoOd9SxPMtooL6aZNkS0TudkQS25ycKLgG" +
                        "IuVb8onpQIPzpnj62H1xC04MNS6vEwX5KV1Qa6ywqE924IVo7ahVwTMOG+g9" +
                        "eTHS6idmChytVvIi4xiOd3mqLpJHYlq1qeD5IpM+Xidvmz3HyqWa7wzXdNhj" +
                        "OstOkYJ18rLMhXMXN2X1NNQnMvmMSMX2tBGzXuWF1wzaCOQd4Czd0quv7g8w" +
                        "XgStLK+F8ew9HIAj9trYxwZhBP/DoI++BZlOv5ltyizV2rde2RVVehO+s7xT" +
                        "Uzj0os/2U2Jg1ETSq6FN643AiFvR3kaB8enwdPaSG1MEOCeElxEV2TYGVbJ2" +
                        "waJy00zjPlyT8hLAWafVAokw94JktrRcDkjziXHimqyo2TmWqZ9do/pT/LgB" +
                        "oK9RT+d+z546xudwRcMo0HRXsi/aBnIZ7XN2XZT88ivKQ126Q0hQXAcFULFK" +
                        "OS/YJmyysvUJO4k6BfaZEHmjSeIa0bQc7/5D8suHeaRzowQmge5BBtWdX+q5" +
                        "+M60BcFnMD+m7q2HDkClZxrLOdWTJe0quiHswUuHYD+amr6sFcbFKDd/Nd0D" +
                        "0wrBU9Dq7xBSkK6V8MK10ZZsVFo608kLu2PoXCucNHkJZlc9W4vYMKIYWJGx" +
                        "6Xut6K8T7Fvq8Pmlz7BVKopWleOVyD2nZ6ne5pZzXVLev3UBbaGDK3d0cK9h" +
                        "dM2g9S9yQTGbNMSxuLF4sYSjZDkSbS1FXx8F6QSVdl0R/IDQGV0J6BpCH/4W" +
                        "Qiy/LFTuDl4WcJu/8ztLv/odypl9YptuGwepoPJp+VKsciB/IrmCztHGGyl+" +
                        "jM5BSY9kVGR7yommfZ4Au8y5ojqUPAoKHJe4YreSRthhmvSl7S36HSARrccL" +
                        "HsOZd68u9xHXhmfwh+Hp0V8wIcpg4N+7vUSgNFqGf1clRIg63f9pOv/Zzmb+" +
                        "J2gT97iKYIEt/QjJGc1kvFhtfyxndLltnKLRU/xolobOhaKhtjJeONwmhxli" +
                        "XrUSCkzmYYjXELH3NC4DH3U5CaH0AptW/Go52MMLdiRazevB4VdP7QpWcTw9" +
                        "Jih30RDNo+tZWwT89OipkNb7jo0HNeSZ46qLSmFl1vzTe5o+ExTAgcKYr0l6" +
                        "DgJDcDslwxMr2U3aIin8QCXMGAM7as7oTMuI5oJLnSbX2u16sfRGd6VePcP1" +
                        "3OrbauWNSxOH+J+eSdDWGljVa0sOBIrbqGtyK2sl0phZcfaxItBb+R/fxMvY" +
                        "BfJClr0psUySpglL2q7tzuZXWs/bCFIHn46KAYiGYrXSsZWlWi/bYg7dbLZY" +
                        "A4QMngvX2L9ry+m1c+ttemWT+Avx7UhCKXOU1IC8JbdxqxvDiEkN1o+VyvkD" +
                        "G6qb0121D0t07trvUOX5H6hEYvKIpY0wESMqUf05eTw9KxUav/udiUUlv+BT" +
                        "xn3UK3HveqlC9dezGUUnhUCb+6kq9AHZOmJTKvzWQGjW1gMZKO8JHc0bvDIH" +
                        "NUyQhDG1IPxNU5EoSKqM6A2e+NXH2bHfdoJd4MopdsoPkPeIM33zJ9bmYbTR" +
                        "oYSlRv0qkKl76mb25g0NB5JT9ybgHpFZj2k/vaxb9T7V8fBbIJKJt0mcOi6d" +
                        "N914DBqpErmq38aze1/bzMAB7/u0XIAFXOAJNqij9AGBqd6PDUgvv699HXIV" +
                        "us3zSOTlHDEDjQh5b1nWSjGLNuoY9GGc452ktcN6Lo5Gls+13UmQbzKTkS3K" +
                        "uMJauIus6MjTs5SdwTISAHOe5BicpvEiwfloIZckyVzKsuDEQPDZYoPo7EyM" +
                        "vTvbztJy3Km5lwsggP+ZPrY669h4og/ahuolIDmLCUfJUqTXUqRqhthKjAoA" +
                        "CpEDdjcor7Fu4O8kL8km4Nv2XwSsg/oj6GGCSFlXMY3AZC+u8kN0g2z6ZaOl" +
                        "0i/UL+K+UIv0nMICmCSy60zzb/BIFoboH9dj58MIjfAfdZefCtvDpDsfNtPs" +
                        "nod6XUxHbBwxxeqe9VwFfeSLD285UuCMjEdJfnuXwCi2o1o41apo9IWgQTRT" +
                        "k1tfuuI+CeJQfMOQTYpPqWUK39SxnGmZZN9pvQWSmWqm0NER9cSPEjohRbnY" +
                        "jUFQnQKSfXZ2eEkbdaOFhTFs1NhJWJE7cdjkWeuNbywudV8R8iks+NZXGN9J" +
                        "s48+tkt6TZrnf0saumvS/BEhPbRwcOF3coU7O7g8eOhq7els8wdjrMzNzW2v" +
                        "2zJo1DynK6cb8CMeMmDymSS9VqH+EQ+hoZMD/j3l5+joe1D1a/2/sdWf5X7e" +
                        "FL/nTf+uZ9f9j9OnP8v+bApZf5FFYQL+m6Xz/wj/BHqeX4R18f6D8D9wmH+e" +
                        "8PMFs/wyYZYQ8F/8Pv6s+/Ol0v2iq0AK+EcfixYEC/v7+9jXDwE6AFD8XQ7w" +
                        "Lwn/RdvIFAAA"
                )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicCtor = publicCtorClass.assertConstructor(listOf("int"))
            assertThat(publicCtor.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            val publicCtorCopy = publicCtorClass.assertMethod("copy", listOf("int"))
            assertThat(publicCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalCtor = internalCtorClass.assertConstructor(listOf("int"))
            assertThat(internalCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            val internalCtorCopy = internalCtorClass.assertMethod("copy", listOf("int"))
            assertThat(internalCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalPublishedCtorClass =
                codebase.assertClass("test.pkg.InternalPublishedConstructor")
            val internalPublishedCtor = internalPublishedCtorClass.assertConstructor(listOf("int"))
            assertThat(internalPublishedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(internalPublishedCtor.annotationNames()).contains("kotlin.PublishedApi")
            val internalPublishedCtorCopy =
                internalPublishedCtorClass.assertMethod("copy", listOf("int"))
            assertThat(internalPublishedCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(internalPublishedCtorCopy.annotationNames())
                .doesNotContain("kotlin.PublishedApi")

            val privateCtorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateCtor = privateCtorClass.assertConstructor(listOf("int"))
            assertThat(privateCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
            val privateCtorCopy = privateCtorClass.assertMethod("copy", listOf("int"))
            assertThat(privateCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
        }
    }

    @Test
    fun `Test data class copy method visibility with ConsistentCopyVisibility`() {
        // @ConsistentCopyVisibility makes the copy method visibility match the constructor
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @ConsistentCopyVisibility
                    data class PublicConstructor(val value: Int)
                    @ConsistentCopyVisibility
                    data class InternalConstructor internal constructor(val value: Int)
                    @ConsistentCopyVisibility
                    data class InternalPublishedConstructor @PublishedApi internal constructor(val value: Int)
                    @ConsistentCopyVisibility
                    data class PrivateConstructor private constructor(val value: Int)
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/5XYdzTb+/8H8HRRe8dsS6i9Z63SImbsVaO2CpIQMUrUCLGK" +
                        "2EWN1t611axSe0cpQo0qqlRLqerlp+0599ve8733d7/vnPd/Oc/X55xPziOv" +
                        "10tf+9x5esDFixcBAAAH4NdDD6AAQNSMbwhp6oJFIDd0NcFqRsbCELCJ8QXA" +
                        "GUDhCO3xAACwCxka1NEWEh6n0BbiHxkarTUUnZRYXEUKa0EENSHj3qV1hjta" +
                        "Qp78WkNDAqY7IyL9/UMrq8urZwH62sQXq+j4qmRPK8mcXv2/fQ52AAkA5eSF" +
                        "EvFwuyOi723vDnVQQcC9UEhvBxQCKezgbufl9eczBRobIOaM6Y8rt7jiMht0" +
                        "52DWrKbmKpoTMPggkcMWkZvd5NXhUgYTltUXEJ13U/WYQKNOMv6bs3oPzoto" +
                        "KOxMRnDEAsVIUrZjX4eNjcRC5BJp+tvXCCte7bvzXiOfbU6+HRMHb5o7MK34" +
                        "9MX2wOfN3eBPdb37D25eliwjUBnakn+Nq0cwV66+jD2ylPdJKWKX4GJkG13y" +
                        "ZUoOvUWwxZG9iCWn1YDq54pz7Qs1QiVEJc5RBEI1+nASr31uYq0OT7xGJIq9" +
                        "MxGqS+fdxF1MgqqWAhp675DkvZNpd36Q29pmxz14T1/Gx72dwTyOkSV17ZCw" +
                        "TCnLzJBjllEpL8LLIwXyzebgDN6VOiIr20BitR4xtkJT5TfFXyfpEXUx0aT3" +
                        "rAcTbNZCVl4yg0lfpAWDBDw7xqCW7nLw4cI4qwlRDlKF2ZTHY/yHMAaEfkA5" +
                        "uGvfldyMbH5nntkBBZKNatXY4ZbSpG9KW7F8Xs3OluYvnxq2giKSbeWuKzMO" +
                        "hgkQbn9ym1x6fyt/w7NFeOQaXSxDbFyILEWtX6auY9BMnRVLtle4mfGxBBZd" +
                        "5WNUVSE0XTjAjW6HEZOrzPOqFrZuek1/vG+SaSkVY+mYtL/v1CyQaO5YHVzo" +
                        "b8aLks2vYntEY1cCrr3rh/TQTAJfhUzoUUr6c1m/5JJSq4nP10EqKwbmx2Wz" +
                        "DWlKuHt68JZoO0zcR381MjRgUKxOnDKptrl3rHGbeQqfn1RWqpXAmj4zEFmZ" +
                        "sCBT9C5dzCl5+MnqWrxeh5GpZJvSp7vorxWvKMbgorOKfQ34YlU3HYMkfy2P" +
                        "bIfla5zWtShYGjyY1tvlyXs/n4KnjU7EOdGg6NlDk8H5J2fV3zusfMEU9mYp" +
                        "zu3tZ41x4OdriUT3qrzKmltcpew/oIsOMhjMA6QbMSdh0M17EsWzB2w0XmJx" +
                        "M+vPi2gS1js4R8S57snkl+Tj5wu9031i9qxBJSlxCcKxJ9HHNqp6dqSWmzGR" +
                        "adU3Zw/76M4x0c9zQ9gnPJSLZJ5LdFPjdJKzH588oUhxZ+Z8oPOGussx6lVH" +
                        "Z1JW5/uUM3sd7OY0otq4UKOvzPkRK71NO/mX6BMgjf2FDjU53e4jZ/xZ8pqY" +
                        "1rKjyVKG+V6jqUxEzXljr9gphAo2rkuEKDdK6QbF5OemBmCjInr9Atc/0RSq" +
                        "vqGJ9v940SzOY0uzVwwplpfmN+Za5ybKMJ37YlHG6oMghqVXgvBMXix+VDN6" +
                        "LlUEcnI/DHntD578KzgcVZEeewVprsDeMa+g1mQSsiwQqfUH6wz91ih/KsJM" +
                        "MD7TL/5TeWI2bf4CLNzUYrO3SQ+ZVN5YLLGbiYMIPmiCLVMuyHw8s50haan9" +
                        "hnpZBV1HothtGxnAUTeVcTGBPFyM1Z632yOSVehomBocybYh8sOZi3xs8TTn" +
                        "AYAY4n9yBvSrM5pwlBMSbuf+99KEG7/WmzP9KQ3Jov6sYd+6nDjwpuU5t8bK" +
                        "MXh4RZegPhGdYa9kP9dt0Ws+zq9zt5wGJEuCbhZsOU1c3u9W4akQrZjoXoQF" +
                        "4SW3CK/IBNlLuw+yA9Ajvu27K1+rgu6dnHsmhB7CfZfmq89LUUU4cfbqyOHt" +
                        "Pgw4/egG6f0br2t8BLvqW0s/ujgMCu/SO2Lf3F+R/XYdYyjaO8X05vkrCyKS" +
                        "fElefWZa+dS5J8wP0kOwlZJcjn0PEHIcXR4brcV4cteXb91oAGcyyS1mql4G" +
                        "ju5/4VJpfj/dMTIt2OvQBF6ywUobfsKUVjvtxgajb3Z9UsixJHBwOvSvaVNM" +
                        "ZwEJSSzVrd042MqawD6jadcquUD4bt4D55JaXbwIR2rnhb0YHCZZ9MDs7qKf" +
                        "Cfb4+SuSYg1tTavtyWFptkZ7o3islBXD1SsKnBqPXmID+CsNBsv95/f5yWVI" +
                        "i849jGNeoRumeaMWOQ21UK0flUCrMAHDlgiHWDW7a9k3KKsubhCl2OJJZda+" +
                        "kX5WCHjPhyyNFuR5bJDnKS5aYEeLMATvp1EMFoQ0m1/L+5oj/mZ0U6F+ftYD" +
                        "Vgp7vpbp82zMlyW8L7khcFsuu9rFq8zeMwZlCFMvZOWKUgOSuUw0hiszgr8O" +
                        "0NDSpPJLVoxSZXRlSLJDE++NTa6LbJRyl+ytW9cN1h0UR+hNhmDeJkt2Ote+" +
                        "u1ErUqKNmQhDf/kpDm7KxDNbyR8kQr4OTey3MCnocz8u7u8zNqwPv9cWI5nO" +
                        "Z/LhsjHRq/OZSv7+X7JqcjcUE1bxXYgXJWm6/iiL/gZMSTdC/zs7uBx7kIga" +
                        "gYhlcLUHu5msNzQ1DGtm1JqsXkhcmdGOncVeQoOi+V030oR8h0nZPONSVAQo" +
                        "4uWl5vZ8Ho5xvMSsYEVRVciy1jaXlw6b6KLPNQzmUpnYb0Hw2dLXFwf2VdS/" +
                        "WEUFTuku4fs09mC0fJ+j9Gg1+ftRW1CpDUntD1dn65evT7x4tLZ+rLbHRHZz" +
                        "2j9vTCQkXqOZ/tZj4rbHR1wkqizA9T1VPywT6SzdC5vxtjmsFngAqMOdyvlA" +
                        "kOZo9G4+Pj1rQ9mGOpSBMgln+XBChF3x8mzv6h1YUaR7M0T6UpLQC3fhs/4s" +
                        "55poey/H/unPAz7KLTveUCt6FSpu6vX9nqUPE8LFq7cX360Ets4ezF4Gh69d" +
                        "7tmrVrR63TV85xL0jT2Y5ydALD8AIrw7BehIgtBjeQqQC2OjiA3bCTOJk+83" +
                        "PQ0ldo4PcVeULeiVpzcODVySi3liKvb7+T4jHI1Q1yvN262rFbYXL0XXqiK1" +
                        "cP4tq1+L60cc0vdHNQPS5CTaKGe670cFcG+vWNN9WArRrJVqpAQuB6KfxLlH" +
                        "LlBTqbhUSNGAmG9ShbeLdEdwCIc1zft784TRgNQ/dXw36AamMFbg1KC6fzRI" +
                        "4L8Z9KPn8XJxcvx7jGKN579jdGI9p8lG90zzUYFyCzxfMqyBpHxz6kbLGcuI" +
                        "WmVmHY1LeK4kv87FV7fQmIa0KKjhSZjW3GC+4vuoCNN3thYFUZ1zx4XQ2acN" +
                        "jObF1Fro0ae9ayu+7d+mlTpOzixSpjJXeg2uhfeYVtg2EVybC2snLXzdg7pK" +
                        "R+5WjmZWkB5kkIgSBeIJ0mRxG1Ok8VMyFaR6ys6RenOVw1dEeWUd3FMedbSu" +
                        "evb6kMGUJCgCom+MxFYUUmNKb9xI5GiAyQnFhDDzoThSwsLgyOcy7jlL4EOr" +
                        "J30VFrVfXNejQEzkFi/KZBSmkYfnVWqWpdr7pyBzTn107V4tl/gChi3s8Mub" +
                        "EPF9ssJNf8IbaT4GoGL7K+RDvgSIFSchjWliS3mm9HPBDPKaipTviAfWYFtw" +
                        "a/Luo8KxiHtn7CNdO9sYFurmWVRs4O/DRvW1oNbvkocb3ItcDYJs8B6QOqc3" +
                        "5S5Pc+8cmLTeCJRhpRisECcMZMWh5PzoI3ecPa27WZyYgJdktcV46HvDlgkz" +
                        "0Ss3PzqTmnmYeDSWAFkLO8TWTKDCE72poKcZGr6gh2YZbaCFSdEo+eSEzyaD" +
                        "KJevX72q1nvvkM/UYkO5n1v5CvIjh1ZYTZM4vGbJQg4RikBBuCkEnqhTihis" +
                        "i0+85ugZPGWvLVrnPTHdsBjdbULyOQaTVfYTqCd54zYTJhDDjfGWyAHVgMku" +
                        "nnfPOkfyjR1roSlOy82SvAyrXTkr0aXr1YaGZi8Cj9ejrFjGkrqTakq1Hs1+" +
                        "G6fkNNF8qRXoz6hpmFS6oFhM/gT0g6c2w4m9bP3djSXf5SltGcVWoaK5TuNQ" +
                        "meJiT0WOJ3riCamg/bAeiQeVA1HjzW1qrHlhFJtCc5X1r0qrLIf11CBqfG3R" +
                        "G+UZEEsC59IYcnDVZ16cAJtr1JT0IlBhtxngpg3AIXB+1tbkbfFM5/nHdic5" +
                        "6Q3L9RkMm4+E3IXVpcuKvjmEmuqSyKXQ5IgsGy9vYJQ/0c47R2mp1n+7rqjS" +
                        "8XjtqYvYdWDNIuThezpeOl+xSVUR1Dh6OUwdG64ckwDEFdpg/ZZa2N9iXARP" +
                        "ziq2wBo/KjM164STJgxKCtfFqsvFzghQrJfROomfkX2WhibZ+Dh2nSi7Pvbm" +
                        "Yr/0jiPxTkxwdrcTObhaPe9xQUQKIiiYn1pnLWJflK3Zin6XakpNUAhJf2z9" +
                        "+OzsjrZVQGv8tARnQBOH4AFuunffw40q/FQncvVI3cXc2leVnS7GGEAnwq+s" +
                        "ltzVcdSDEaF+7ToPP1As88rJBbGfOoG4f+hEHQef2E3C8drpATGmAySfEfl0" +
                        "KKVK8wbrasFtAyx5vOra+WLfs/iCRckdknERDTm1rPTbGPfF9hBiooWuIyJn" +
                        "UTdbyivlIYdfqKmmcfuPjr70qZt59KC7AzmGvjQtOiyo58S3dJYFXI++Xkl9" +
                        "IvIdp+sVYc3ypzhRXPwnnDh+G8SQUB87lNM/9ke/TmKzMFjSH8HYxMI5QipF" +
                        "zyzF1PPilroyU2NoMqOMWMncJOU+5IJIcXCdkh1tK/vHZ0bh2PNC9rT8W6xB" +
                        "dDcfumWi8o1wtK+94kZ9R3zvWZ0cHiA6zjWFMGAVGs/BJRs3e6I/vLcgZB8Q" +
                        "go8q+A++0TNh2p22LMBdyM2erlihlgEjHHNibdzmTgu5Vj+bjQNOe8WNmQs3" +
                        "kavspPHeIjZ1+NkQueRePgeHyggaT8IkbNrMlWWnvyltE7NDP+04Vrqgi/eC" +
                        "pekZ4Eqnvz1cIr26zaI+puma/IVNBWwbLXVEV/ZykVygoFIDX44CKu4GPSUS" +
                        "5UVS0AVsVyBfe26SYwo+MoLUTEsRdcuH3g6Db1nbV5tC7J5W9gpt6ys7zKwM" +
                        "VoAxrcIo0JqFbp4z3lgY2iSYoYBO1eR7f3SJoWrsfrkz3seNfFrrzq2HV4ER" +
                        "RRZgA3rm2w68kdy+Xmjra6dTGNq3t8bjYR97fCiu0iBHusLWa6FsPLm2SAO/" +
                        "6mNNL8u8BdyKD4FSGPlh+GuUYLcoZnO9CtJ01jmx9o0jhpYVsVhIBcQPjd9n" +
                        "Rt7Jo04faOhljXMTnCGU3iKAMutqBfCxCd0qQKoxVwRZCDxyL52TCyT40kUp" +
                        "SGG2JV9TJZF7Qz2A5Y+fs9gdqizxrMB3H+m8110QFhmOXrdMBq56Q1UzkuM7" +
                        "9PhLH/MA8c38e+FJPJ+Wg7O50x+amEQTgLrt9jGYZjBytK70DjiZQ7ofqJt9" +
                        "OahC0NUf8WZ374v2o88JU1eg8TVbkvw0rAaT+dm05UXpvC4eBfXjHqUi7vjP" +
                        "sGZBLcm84+iV2cGKLIq5E/06A9RomUWs1KBv2GXWALOnJvO3azKqs4MmkU9r" +
                        "HTK3K+muOTb7ytFmmK3NMQw2+rjDj0488aZtqTb1H+RByx73b8nTxaeqPh6N" +
                        "L77K0y7HVeL4aBRwC6iRyQl5bCRwBWQ9chK6kNNtfDqTfQFfRbPEcGXFK4xH" +
                        "3BNDJKqqWlCt78UvM9tTrOskB4E/3JM3FpcE24knqLBxa3cfX4j3CZ3RBsh+" +
                        "tIs6Y4RT0d1Yw11eo/I8vsX6cybjrGmOJmOKXnLw9Kq5sBqDyI0ur3UilaAN" +
                        "v8tuyqqTrOGbjL7wVjorVHdJVljpencKoYyYia7v3P3NFeIm/+DNUbliRntg" +
                        "ShMdY9cBuGW7umkb11nVNH6rmWWFGb48/KC3FxUtbyqo9y04rEbpI3E+IgF3" +
                        "wBPHLhSlbLZxkvWEd5ztchhnktE7m75CDaUtw+r5AhB8CTbwsyESCBOOGKXa" +
                        "wY141ywUQofKVaTF3Jbjolr8ertbmu5MXB9OUgk+2DmW2wP8cWabeOz5HZCy" +
                        "OHWPe7BVcAhARuWoAgAOVug/+TGRkaiXaDGfghPxj90Q6yk4f26gYHZQuLAb" +
                        "AuUOhdvAEI7e7k5/UuNga2vrfHrtg8dsc7tzXwB+1DC7cneS9jSF6cd26cxZ" +
                        "esB/qvy6efq+5/r9/L9br7/G/Tpgfl9X/edEnt5/vbz6a+yvPSPot9id84D/" +
                        "ZVb9a/Cv3gv8Fowi+S/B/6IB/WuFX18wx28VaCgB/8O/yF9zf32prL/lhtIC" +
                        "/tWPRV/7AtH37xOdfsjOAgBvv8cB/g8sAUygBxUAAA=="
                )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicCtor = publicCtorClass.assertConstructor(listOf("int"))
            assertThat(publicCtor.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            val publicCtorCopy = publicCtorClass.assertMethod("copy", listOf("int"))
            assertThat(publicCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalCtor = internalCtorClass.assertConstructor(listOf("int"))
            assertThat(internalCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            // The copy method gets a mangled name (copy$<module name>).
            val internalCtorCopy =
                internalCtorClass.methods().single { it.name().startsWith("copy") }
            assertThat(internalCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            val internalPublishedCtorClass =
                codebase.assertClass("test.pkg.InternalPublishedConstructor")
            val internalPublishedCtor = internalPublishedCtorClass.assertConstructor(listOf("int"))
            assertThat(internalPublishedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(internalPublishedCtor.annotationNames()).contains("kotlin.PublishedApi")
            // The copy method gets a mangled name (copy$<module name>).
            val internalPublishedCtorCopy =
                internalPublishedCtorClass.methods().single { it.name().startsWith("copy") }
            assertThat(internalPublishedCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            // Note: @ConsistentCopyVisibility on an internal @PublishedApi constructor does not
            // make the copy method @PublishedApi, just internal.
            assertThat(internalPublishedCtorCopy.annotationNames())
                .doesNotContain("kotlin.PublishedApi")

            val privateCtorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateCtor = privateCtorClass.assertConstructor(listOf("int"))
            assertThat(privateCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
            val privateCtorCopy = privateCtorClass.assertMethod("copy", listOf("int"))
            assertThat(privateCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
        }
    }

    @Test
    fun `Test data class copy method visibility with ExposedCopyVisibility`() {
        // With @ExposedCopyVisibility, the copy method is always public.
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @ExposedCopyVisibility
                        data class PublicConstructor(val value: Int)
                        @ExposedCopyVisibility
                        data class InternalConstructor internal constructor(val value: Int)
                        @ExposedCopyVisibility
                        data class InternalPublishedConstructor @PublishedApi internal constructor(val value: Int)
                        @ExposedCopyVisibility
                        data class PrivateConstructor private constructor(val value: Int)
                        """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/5WYZ1RT29aGgwKKBBAE6UgTEOmhFz20EEro1dAJQugSmgKC" +
                        "gqD03jsxIL1JVXpXQhUQgnQJSK/SP/THuXrGPec7d+0x/+3xzjXW2uPZ73w1" +
                        "VS/jUwKuXr0KAABYAL8uSgAJAKqoK8urrA7mh8qqK4MVdXT5oGA9XQIAHgCN" +
                        "oTjrBQB2oB8/qKny8g2SqPJyYz72V2oLjIBmFl35VKA8ytBB94Iq7U0V3kfc" +
                        "Kh8/3tXfxPD39HycX5xbvATQVL1ytfTGnVKJi05iF6X5t/tgBhAB3KyRbvwu" +
                        "9jb8mu6WDggreWcnpJuru5WbsyuflYMFEvnnnnx1tZwndSnPStbYI1Kr1Scd" +
                        "Tej1DeWVhxydPhBarRHaW4zc7iug0qNb7ICqrYy+DfDVaSHmlpvQSMLnh0hv" +
                        "jgSzhN8UJIpfD58OHMCEQyVjyHsal7DzyMadKSRmz+z85OyK/zdDK5p5j+7w" +
                        "TqcpQ3unOnX3ngM5JuFCLJm2OfAo4q0zbcnip/BjmJRHfB4ziJ2aoX/Wkybu" +
                        "uRHWPIq4IxxIAUFoZgmx7/PWIEACoMskvghIdxRo2kMuyPjwHIkB5bunOivM" +
                        "4tsL2er5lc56V3fZEOWuiDU+TMp6996C48NTTTEPh0YqwwhquoSlQ+wcqQQt" +
                        "VaZBSokUPxenCKtnBgub/47IMXHhsmuQSjb1O0SC1Deh6VgNwjYa8uROnD/W" +
                        "bOnZ/Cda8LWORH/Wu4+aBhAwB0mnPnSE8ZAAyzXpificAe5DRypnTe8icNu+" +
                        "HdCAeGpzitbKjVXi1TvIJoeIMmVt4jystZyZIfGJVELgvBuhxDuOqkJdf8e7" +
                        "WNNt+5HZVSPU8qMGPoz4jXCq8IhnEiSVXqnqcL/PVcZ0GcgXBrpnoCCfUg+d" +
                        "0mLeMXQvh0+j4xWg/BSXAvrdN+TYVpheKkwkFAaP3d+3rr8bYwgv90c/MeBy" +
                        "k0CVMmSTW7wBVz72cnVRjgXfhg5pkAo/YTf5xC6iWBGJUnP9Q8YXFZHB8FEZ" +
                        "5PDIheuNqtVQmM+RjrYWlUx5zKheudnTM4gp7egwKrawQCWaPvlz78uS6C9i" +
                        "eSvJgtZxfWWLS5EaTTr6wu/vbz/2OSoeJxlwEpiQ6a4ezlewV9OKfaLikmE1" +
                        "J85mUunmmOjkT+FuW7bq5fG6rsb6SmYIa8jEod6HqbJLSqtW898D0F3pMpO7" +
                        "++kDLMNTlYQCu6XIwvoGOxHLDZ+8gxQqQ2/RmoDzQMS3p6D8iQMGcqRgxGdc" +
                        "ax55NK6JDSPE/lQM9QY1PIV2T/YI3TVhfRMfEc0Xfh5yZqagYXEN9i30ZWK5" +
                        "3MRh943LNJRTHFDmIZc/8sRaQe3Xo9TiMnLOy0jiHWjZktQWrrfBX403tcSm" +
                        "t6zG4+02MRuSC6hGPdc5okUFz3fVbqIYKaOhNT1oq4rMdgcM3hO63FqapYwQ" +
                        "4vi+O9M+ZHoChlzhtyykn/PU4EDP/qgRUfcLRWUleAe9Cu7y8sVtk6MVFshD" +
                        "nmxdNYhwWVPuEnQVzE30GrCrshegGsvqmBEz3uAJoOsCYZulBCP7lUMmE/ih" +
                        "52GBruKnnKhbUVFkeRrMxdey7u6ecfGojMS6Fvq6qpzSf6Zc6+dOcDbgiUz1" +
                        "itwuismgQH1xfKH/4FtXrYZrbFFNPmgnNQrKk1TrOEf6RWwLbz1FGKa6cH1O" +
                        "3qeKSKbd/KU3S9VoytVo4AtBekuudpeX9LzHfdfBLxmW+X9y5uodhkhyfAAg" +
                        "9Mo/cYb1V84oO7lZuzpZOPw9aV7oamkw6FGera+urq+WNfMMJLSqwIweGU2Q" +
                        "mhe+HbCTvRtAfNscwt1o7NFlbZ5kZLNO4StIE3sWyHFkORegwT6q0FL1Svbo" +
                        "q7NQJ9Kzk4BarlLqIM37a9qGz0EacqnUL+Mcb+ZmAs12K+kLo+mC5ATO1eb5" +
                        "9aKNF6y2uoG3UC00YdDGNmiit8jO4BHahBzBTqM9HLH08hhoN0hnYg2hVDKo" +
                        "hLAWy7/ojMLOOrU9NNe/KSkppBl126Z8NhAov7hToS5oKwIrAgPwHQRxVH4N" +
                        "697VnBxXX8+NNVnWgpPNCISN9wNS6hChe/5Sprau/TsE1KQSyaGNhg6163Go" +
                        "9KFXM4pBRYF8umv9j9uogB86dm4Y6te9l9QpnYlT5D+CV+NJojcNFGa8RIIa" +
                        "53BE+d0qJ/xvJx/SRcDevgHJS5Z0y5GlRaPGec5kSMbytd6Z1rznbgufW8bP" +
                        "Q3Z+V0DSECTPkaSQG+F2GKbjIhSXxb0brZQlI6yju+y1G2Rvj8/MdhaZvVZf" +
                        "imBbTLbbjpsf9B+8npw0IvisBqewT02yPkRjwpWhdapBe21JJF0SM2aSape6" +
                        "2xHEKLCbBmYRWuQ1q42w4Rt/blA3A9ep9qRwc6asY1+4lLLB+2ZARKvRDQUO" +
                        "jOfuw/UHN8jXD5XiQh7VFg6bCQqbDA6xS0E4IB4IJY2qs7YpZeu0voWqtqmM" +
                        "uGwg1/yr3Fs6BWi6nsOw7J5T7AfOV4embPKSj+1TKOqik/sOaCWXGWNTohk1" +
                        "jz+Lp3of7uJg7qZikmyZeUrT3SbUa/W5U64l8WC2h5cLEoSGYvbZYzlXYHZF" +
                        "DeqL7eJT2JzCLDB0hU8q4/34uGlNgAac0frw5JqnweOZbl8HBmMnr9bP1bzO" +
                        "XUSMQjLi63jySyEz8cUEppHvide+qErPTIr27Z237lI1keuuBkeE1TF0FWW0" +
                        "5t3RwrGx9w+xNcmgEKjh23nuER6gXVOWN4mkUXdIzoIwZnCYhTJsJ1ylif2r" +
                        "cgPN4xziqYpZlLJCChnNroIXOu1yTM/F2WDtZV+zWt3goP9Grhbx8pRaGsv0" +
                        "EQPMiiRs6fUnYk9JE+LPkmaSbn5tU5z10gGtKHoj9lT8IRJPQAjXtSh+ADHO" +
                        "NzY9PZKyjWlxQ7jcCiCHG2EkkhSyiOnzmlS1tT80Ncd3Xamd2Dm6B4Yv3AOH" +
                        "D/oaT3f12Th2dXeFqW9mfXL4ZM5eIhvbslH7yZSQN8KmNrzGiikzOo66RJPu" +
                        "mEjZ3XlVg2uaLXmcXiqwgvlZyfBOQFW+lkZv3/7toYnp7tcIP+NyuUJLAmxL" +
                        "Q5zNs90q9j11kFt53dNAJfax7ieJkqC3QYEvEMQHPNgu75tfui6HPrRPYJDz" +
                        "8ScbMw/EkW4JBH4xjmXRzhb+KrcbiCN51IBoutEKjOY8NfuBHOsYHk26C+Sk" +
                        "/CNy7v435Py0OEhba/jfsydcd0pjUp/y3GRSmeFGM9GANqTBCSUcWE1U9G1U" +
                        "tgEPFmyUVaQGYfQWfEUN7MSs0le9V1OLPeLUnTDlWLPQsF1BkaD1Umh8J6eF" +
                        "oVJj+5eIqeTKNyQOFnxOMInnR0sH9wHYTiXgWC3VpiuyUEvU2OmwZJmnZpRR" +
                        "arxsBZlB79KkN5+ReclqQKyPsXI1ySn3xMV3uGCLEh60ULU2cXIvQFu4U5+W" +
                        "sADG5OlQg90HDVfl0SzMjdsREuXTUaJoKaTQwxU0SdGy1EIICI6D06eNlKbE" +
                        "oJ8gFFtAEloKzuB63pL16d0aeukBXyazp0jp4W35V1npk/OQTzv0i9DQ0mP0" +
                        "GqH1sLuMk2tqXK8qk5mdBCYOwv6GfCEUSEueq1lzDwQjtcmn7qftEShIJ0iX" +
                        "5O8FHgkyz5HpENTWtPpOOgrsUnepfw9DxQ12qH92+OCxctN9j4rEQBEExoTo" +
                        "A6XcNTrsRxwrP1dhJdv65pYheQFdz646440pFGkFvbPtNApRzBbnoFzC6DNQ" +
                        "h4gJ1Ybdfd6g+HZcICBcolR9OEqmmrJHRKH+xmDcULveZn2QMhYdVtQPQ8uD" +
                        "xDR9lO/fs+BKEyETBn6tt9KKvsE0XeYThEXw439tem1YVWskVqcshPBWQvPG" +
                        "tbOEXq4Cwp5nm8Q2lhEYsJSo5/tJYt/nKAfEcCwHN9DJxJTYxaSwTijqckzk" +
                        "MsloXSHz5Hx9LbWvgCdx5DXLG9Zbp1G6uooaPCh3PeySmXQ09dGu8BaqqEAb" +
                        "BbasHii1b/eEFjx5CKGFU4mezy0PivU+R3sqSWpENt6sYarfA9coffagifcn" +
                        "uWOnrUE0EqvGPnKLVHetXXhqIue2Vg7Tnr7j2AM7x1LYWVdOWM4T0z43fej0" +
                        "zQND8ZP8RVfvSSEso1SZsjASeyloncpptPpm38MiE9yIqagvcjLn8CwzuXru" +
                        "7UnKXvNN0UTL1E86Z4KyMHRgX8+rDcpH3FtVZ+xiOFFVXdaCeFIc4xmVzP3Q" +
                        "EkvZ7la/GHhma3C7yUvhQWKT3VTx2ehsOjvBoUsVux/I+X0SOyDXoXFPZftN" +
                        "Jx2FKMF8oGi5VaHQxuotSJ6g9xs8X8icGgdz8PehfhYZpnvNM5XVWvjSU4QZ" +
                        "64CneAPDtYHZ4vu2xlUOpocrsPxLFzDyunXYvZhD87iMzxLhOb/pfINqYLv/" +
                        "0d4Wnmlf+Op8fRlWSfnWZFefb1ymOyo6kjy9WdbmtUnAYC0hy/PpTbAN7ch4" +
                        "xizpl7BHPiq6q6bZwmdsFL1fvmdGNcrLfaG/H1jFxTCVdeDNopWTwRNUHKez" +
                        "Mt2Nxj29gFHDBYxeA2kTFJbw82vkhoNnJSKtc+zZ03MwaqWyoi79zb6XN2ZM" +
                        "LoOSRASCDyAszcdeZYQy1/uv4NW3gsiZaZnpmamz+P/w85fKPnHyV/H/0H5O" +
                        "/INFI6zQYu4LFu38I4tYfhuzXBEeFm7W/+R+pn93PzfH18isO6wRUv7Fo3KF" +
                        "BMVAvjhKee2txQopvsttHyWRibjQlqRdDdscfo573xXUoHF0qTQo5zNop9c6" +
                        "pnZmqIjIu58Mc4jx7JfwPDs5rpnGwzZzy5uOZwvQS7kjRVz2a8uoffgpZSu5" +
                        "T5hJOs0ddmSc6rGFqWSiNWsFELaWqBSktBLWWlN1tF1SPke4PEORAsHILRy0" +
                        "wOSZKnq5smJgMPbZcx6CUF5OeK3hsrZbgmzhpKVML6dxqGzpUQPJdKWMIJRZ" +
                        "r2mLoI2K++S+uBXV/fzUyepxYO38C44DaTOXWc2ournQePQ3jMrQnXRcuk23" +
                        "yoJl31mcmElL2YOVEGejeMzoRw+buA7mIHixVn3u5tiF+XEPyh5qv4PqVl3l" +
                        "dzv6mhoBq0RwJ7oZJ0CZJGC5IyvHjCSlA2FeuRbeIkH2ryu/26h2bra7AhWN" +
                        "ZkWCnju41jSPWZsrE/K5kfF8aO7huVbL9dZ+5BkvkxPvd5EU13X24e1Ak8TE" +
                        "tBCP0P2u9rAIxDtZsYr79q6Jojjj5dvvz5Oyxx3nhJ0alydGJlD81av+FjM8" +
                        "16LfrH5vdZdGfr75rpOAM7OiITGLOrebjYVswG4D8sL05XYdmwhr0YjtfT/p" +
                        "iSbU3baQEhqlY8nTO0Houw9S4GQ9+D331r6Xi/pU3c9VKz9MQwNRQGvdZBUG" +
                        "v0xtkU8QPmsgdCKUn4kzgTAcuExZhFChBDtVC8NxqIOPtmAPXcaHOg5bHbHz" +
                        "pZ9uT+HCLTMY/G4X36s4YvtmUWICZrPM/el99oCx1HQPHJ3q1fdGRE2ms22y" +
                        "wKCVUvGM90PYqaqASDi99cEJt+fY4xmIlMfXW6Iys6sTCTVLgaR16+lh/tme" +
                        "0M0VjJBz+2AHBqfHtGkCG2/xG9oVaaLVXTuO+Ej7CjnaPzMcw7V7GO1JG2XG" +
                        "OCS8PXCZs5/VaAd+h31vGwTvPTQbOI5N4+ZZtj9Vyj0BJkCkbinmkJ26frnw" +
                        "Pg8uvE/oplodIXvsYuag90CQyts0cjXRay8tXBVWpxfKMfD9Ifwj+PUitSRw" +
                        "TsedHYbe3BOQsdeEekj3wwk4SPAzmZXoEoAtwCKs/W3XPHjx2kri9B+xwK+9" +
                        "h/ILbN0hxkiBV39siPD5paJQ+l4RHyIzZnubC4NpEpODkwQ4SB/tXR/L9DDO" +
                        "vQ6S7AhQr5wK2Kq9wkvYNAe2oa8Sz5zl+BL22EdOu7HG8vH2FfZPAT33s3xC" +
                        "4XPm21d7K/DrVZo2UErhD7UR4fzRo2dyfNB95a6QtQiqR8TOOunWQ8MxI7Di" +
                        "kfjRQzymKCzzhmPKx1VCiptfebDlLtQbhl7UBzwTxQTXrm4xfZn2J8+53356" +
                        "/ck3tSDIi6FkYsHrlgLtfQnn+ldBCs92/X7OW8Y3tBNpL4AT+4/Aob8Azp/5" +
                        "kqMFwonP3tnNAeFk5ugMd3ew/hM1Vubm5g8vytJ/wDyrPasD8LOHwa3HIxQX" +
                        "KjQ/syO8S5SA/3T5NVf6kWL9vv7fTOuvcr+Ojz/CqP+slxf1r6Opv8r+ahFZ" +
                        "f5PdxAf8L5PoX4V/5f3d34StiP6L8L/wm3/t8OsFs/zWYYME8D/8Rf6q++ul" +
                        "0v+ma04B+Fcfi6YqAeGP9wkvHuJLAED7DznA/wHbARqv5RQAAA=="
                )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicCtor = publicCtorClass.assertConstructor(listOf("int"))
            assertThat(publicCtor.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            val publicCtorCopy = publicCtorClass.assertMethod("copy", listOf("int"))
            assertThat(publicCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalCtor = internalCtorClass.assertConstructor(listOf("int"))
            assertThat(internalCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            val internalCtorCopy = internalCtorClass.assertMethod("copy", listOf("int"))
            assertThat(internalCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalPublishedCtorClass =
                codebase.assertClass("test.pkg.InternalPublishedConstructor")
            val internalPublishedCtor = internalPublishedCtorClass.assertConstructor(listOf("int"))
            assertThat(internalPublishedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(internalPublishedCtor.annotationNames()).contains("kotlin.PublishedApi")
            val internalPublishedCtorCopy =
                internalPublishedCtorClass.assertMethod("copy", listOf("int"))
            assertThat(internalPublishedCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(internalPublishedCtorCopy.annotationNames())
                .doesNotContain("kotlin.PublishedApi")

            val privateCtorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateCtor = privateCtorClass.assertConstructor(listOf("int"))
            assertThat(privateCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
            val privateCtorCopy = privateCtorClass.assertMethod("copy", listOf("int"))
            assertThat(privateCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
        }
    }

    @Test
    fun `Test optional parameters for data class constructor and copy method`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                data class Foo(val optionalParam: Int = 0, val requiredParam: Int) {
                    // Manual copy function, where the parameter is not optional
                    fun copy(requiredParam: Int)
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val ctor = fooClass.assertConstructor(listOf("int", "int"))
            // The first constructor parameter is optional, the second is not.
            assertThat(ctor.parameters()[0].hasDefaultValue()).isTrue()
            assertThat(ctor.parameters()[1].hasDefaultValue()).isFalse()

            // This is the copy function generated by the constructor. All parameters are optional.
            val generatedCopy = fooClass.assertMethod("copy", listOf("int", "int"))
            assertThat(generatedCopy.parameters()[0].hasDefaultValue()).isTrue()
            assertThat(generatedCopy.parameters()[1].hasDefaultValue()).isTrue()

            // This is a copy function defined in source, where the only parameter is not optional.
            val manualCopy = fooClass.assertMethod("copy", listOf("int"))
            assertThat(manualCopy.parameters().single().hasDefaultValue()).isFalse()
        }
    }

    @Test
    fun `Test data modifier for data class and data object`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                data class DataClass(val i: Int)
                data object DataObject {
                    val i: Int = 0
                }
                """
            )
        ) {
            val dataClass = codebase.assertClass("test.pkg.DataClass")
            assertThat(dataClass.modifiers.isData()).isTrue()

            val dataObject = codebase.assertClass("test.pkg.DataObject")
            // Data object does not have the "data" modifier included because all "data" does for an
            // object is provide toString/equals/hashCode implementations, so it isn't important for
            // the API surface.
            assertThat(dataObject.modifiers.isData()).isFalse()
        }
    }
}
