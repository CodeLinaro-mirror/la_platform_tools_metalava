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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.ANDROIDX_COMPOSABLE
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.testing.generateBase64gzipFromKotlin
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests the target languages of items.
 *
 * To generate a jar file from Kotlin source files, see [generateBase64gzipFromKotlin]. The output
 * can be used with [base64gzip] in tests.
 *
 * Most of the tests below do not have signature file cases because they are mainly supposed to test
 * how Kotlin language features translate to target languages. Parsing of target languages is tested
 * in the ApiFileTest in the metalava-model-text module.
 */
class CommonTargetLanguageTest : BaseModelTest() {
    @Test
    fun `Test regular items which can be used from any language`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class FooClass {
                        public void fooMethod() {}
                        public int fooField = 0;
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class FooClass {
                        fun fooMethod() = Unit
                        companion object {
                            @JvmField
                            val fooField: Int = 0
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class FooClass {
                        ctor public FooClass();
                        method public void fooMethod();
                        field public int fooField;
                      }
                    }
                """
            )
        ) {
            val cls = codebase.assertClass("test.pkg.FooClass")
            val items =
                listOf(
                    codebase.assertPackage("test.pkg"),
                    cls,
                    cls.assertConstructor(emptyList()),
                    cls.assertMethod("fooMethod", emptyList()),
                    cls.assertField("fooField"),
                )

            for (item in items) {
                assertThat(item.targetLanguages)
                    .containsExactly(
                        TargetLanguage.JAVA,
                        TargetLanguage.KOTLIN,
                        TargetLanguage.BYTECODE,
                    )
            }
        }
    }

    @Test
    fun `Test properties can only be used from Kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class FooClass {
                        val fooProperty = 0
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class FooClass {
                        property public int fooProperty;
                      }
                    }
                """
            )
        ) {
            val prop = codebase.assertClass("test.pkg.FooClass").assertProperty("fooProperty")
            assertThat(prop.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test type aliases can only be used from Kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = String;
                    }
                """
            ),
        ) {
            val alias = codebase.assertTypeAlias("test.pkg.Foo")
            assertThat(alias.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test value class constructor and boxing methods`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val v: Int)
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9OBCX" +
                        "pBaX6Bdkp+t75pWEJeaUpuol5yQWF9eG3A2WDhX5f3f2+rNpjbMvBG1a8M35" +
                        "dku/2tGo1SJLFmztXhi6IvDUZaGyQ06bOm/6Luvf8zL+QbO+1a8H3PxWwY0b" +
                        "jfkeza6f/LjiXdpkA8XHPkX252yfl5/7Obf42/v6+v/MB2JzDFVsT38t+vq3" +
                        "6FzBnkWRmVGGBmwb07ediTlacmrn8tvVYSe7FY8I6EXHTVScclKKZ41rvbMz" +
                        "j5eMTGHVt7Lc6YlbChiL5yWeWJ1bwXtb8/ytq1WtD4uOnRCriruVv6R8afy2" +
                        "O1u0rcOU2wTbXjDf3rjX1b5lUZtKkBaLm6SQrP/Ma01Np5dkXns9bfNhg9k7" +
                        "Xzxd9PjAyT1t2hLTZ2S2nXQUnBc+Z0LX5KDCp/9F46d35RtoyPJJ6KWypax/" +
                        "KLXO+660y7KbGs9NeRIjVMvyhRp3L9u9O+j2RiamwyJyUxVvOivyrdmUktUV" +
                        "U7rF9IzC6iSpzVJGn45MVN63Zv2stKO8C44ZuS3c/+Dzzec8WZdvrtv7YavJ" +
                        "nt4VW/adFlhZuTusWbH0YteMfOfTXPxMVwvYi4We/y3jchVNjHQMXGW57nrG" +
                        "tD1RyhzeL398Uz7Dl6F9k4lf5YYz29sYr8ouw2QJI9a4/crTLQ9pvLm+p+vt" +
                        "k29RV+9krc6yKJm4duYbmzWR83YmV6mdrNgqukgysajzNOuVt79V6hNPS1s+" +
                        "sGb5c/nlwdKp/cVTuy8zT62L0dAKV9qintF/yc7HOvzcvjgpe9sZjyu32Uzq" +
                        "4lr3uebFm+MZEZzdJ1bpiew6/lxk4auHhdPS+///2vZ3lsCtVQLOQbNTzkzn" +
                        "ndwqoKwiHBsZtOTa5NiQVqWLh71CjpzblxjT5vXy8Z0pd+5976i9KcejzJr0" +
                        "0HPzrlzTeTNvi3pP2xpmpO26OfF0tYLMrQ9nLv2bFcn/7pXb2psakf05lZ7r" +
                        "zWXPr4tyvb315deFj6/YXN68clFIrh/rtHvBn4Id1SO3cCxS+Tknz9JT5+3E" +
                        "ldMD+F8eCFxi+orb+cch8w+iExYIhzqfc0n4o5d5evsX7Y6zyTFevbKO1/s/" +
                        "zDoh9/CV4L0/R7/fYfZ8rGvPpqauy7BT0mXBCQ6/gtX33V5aLfvJmZNXsuia" +
                        "f8HvDecVLpUaXfbdfN5z3/1lz1uOKW+o9Lp5Mu3y8v8s//kmXnj4RfOH4L3I" +
                        "C+r7p18vmL1eP7O38fj/gwXSRT0tKWItiv7fO7QkD9TuEW84vctj7Q6ejF8s" +
                        "n/d23Hr2UUGp8tNZ1umXxWbosz17dcVQbJlaXfOv1wdubd14RW/aK3NG5TNH" +
                        "9WaGM+ftuHeeK397j8djZh3WW9s5IvcdjW/Mf3U84t36cBsDr1P6YfV7rWKP" +
                        "sZWESWn/2KC/v8XuI3PSw/mCfF9P9Anc9H6+0+vB4oZXLHI/fxbm/Hp1/sOc" +
                        "b7FWtT6/k41MtwkYbch6IMzMuuaioD/Pqq/LoxP07A7/EnOLzdAziznyp/Si" +
                        "6zXXI65XjtbGF/U/O3V0TrekuKT49ZqHE7of/geXOaue8lfrszAw3GbHV+ZI" +
                        "AzG8yMtNzMzTy84vycnMi8/NTynNSU1OSEhIA2KWJD82jYCkC0kMYLO/Ku3Z" +
                        "KwzUKQEuzxiZRBgQpiOXdaACFRXgKl7RTUF2vTiKCfW4S0l0Q5CdKY1iyGcW" +
                        "vN4O8GZlAyljBsIrQNqfFcQDAH2x79Q5BgAA"
                ),
        ) {
            val intValue = codebase.assertClass("test.pkg.IntValue")

            val ctorImpl = intValue.assertMethod("constructor-impl", listOf("int"))
            ctorImpl.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            assertThat(ctorImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(ctorImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(ctorImpl.modifiers.isStatic()).isTrue()

            val boxImpl = intValue.assertMethod("box-impl", listOf("int"))
            boxImpl.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.IntValue")
            }
            assertThat(boxImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(boxImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(boxImpl.modifiers.isStatic()).isTrue()

            val unboxImpl = intValue.assertMethod("unbox-impl", emptyList())
            unboxImpl.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            assertThat(unboxImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(unboxImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(unboxImpl.modifiers.isStatic()).isFalse()

            assertThat(intValue.methods().map { it.name() })
                .containsExactly(
                    "constructor-impl",
                    "box-impl",
                    "unbox-impl",
                    "getV",
                    "toString",
                    "hashCode",
                    "equals"
                )

            // Value class constructor can only be used from kotlin
            val ctor = intValue.assertConstructor(listOf("int"))
            assertThat(ctor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test function using value class parameter`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val v: Int)
                        class Foo {
                            fun usesIntValue(iv: IntValue) = Unit
                        }
                    """
                ),
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VeTQbWhpPJZZ6sce+1DaqVILWGKqMl8aTROy11RZ7NMRe" +
                        "2qJqLfVC7YLBqNZObVVLUXtijdKILc+orTytWkuNds6Z0Z5p7z2/P+459/t9" +
                        "9/vO734/YzQQBAGwsbEBAAApwOkFAYAAGIS5rhLSUA+G0TVE6iHMzKEYvc8D" +
                        "AMAWhkI2QCtBRznQSgpDlOFaU+XxS/OLflAU5iISMxpYWme6iVLyVUBRKIoW" +
                        "m0Ow/n7KwuIfi0wAYzQrWxXfhSqNkwTqJzD+YXrBEwS4+gfAfG66w5DeARZY" +
                        "fKAr1BmP9fcPNZ8xE70OOZ4hVQ26RZBGTJ/l78DpUUS5bpsySFF+fULB9acm" +
                        "A1TeoM5fnz2gYYqJLSsO85EwzYP5X7g0zSJqLnH+QQpPWQjecEtRllow8NMZ" +
                        "urp0a2g/13/nz/DwY2C7HV5F9ip522/7yG/Ip6XQGmejosxS495Ase0OGGh6" +
                        "Qr9j0Z8g1cUNvWGfLJXaLwIuR4TD4WCUmJjv7Z0grwxsnc8Z/zxsX5lXMAf9" +
                        "wvDU69vRDL+ePoHb9lOEoluPHRqm6xSvWMjE8MQsA+k1rQidqMIYWVMFkJ4w" +
                        "r7hR1sT9++Qi3MRaeu0rZVLT8mLhQnt/S4yiUEYmLqZflyfPMicpPsXUd/GY" +
                        "3yEjnqAsL84pBHVlcaliiFSiZ0SvFdPkl9TAWKu/BBF4I5qLm5tN6TVMTK8g" +
                        "EmlSNLgUZ/kzF89428A6NYpkmZNIrYjqh65kmbbyqmy3bo78HlW9gpfzW7Ql" +
                        "sCeVVtm6WX+5JfFpXRuZuySk2SJSKnA0PpMAJ7NzMb32YfXnXToKYkfwY611" +
                        "TUo1Kic90ltsZNjQK3s7MhROD0UaE5fsGzjLui0qJF7FWUiV2f6lTIZGp/y7" +
                        "yZb49X/t2Lye9izz/FtAckXWO61y67wm59ty/cH1/IXCWL8HZObx9U+y4Viy" +
                        "qMb8FdAhdaUjMI3on5ZABaaF2corWErXnfcgjmkbXLEcarMX0bmauRDSoPUo" +
                        "nr1y6+7yu14Pq7MJfaVQyIveJUjBKsM33Z14fNBwlM09VcoNNyW5UDI4UqK5" +
                        "ZWT57KxNiyZS7MyjpUdfocy7htqwtjGolYXp1OnZ3bhQmgRYhtmJgax94aWW" +
                        "l0XnR6fXW6gqImqx5DuSYlOblLHP2dZcG6t6FTR5ayI+BFmlLj5caYOg169s" +
                        "FyyMa1FrSwrNvQyZ02fNPpjpnreuYyuU3c/x1kBeXE8uyTDmWmk3KVJb/QW+" +
                        "16m+yZ+Uz3cdPnTN8RCKIzd+VIwbdLZFJYrrThI3s/skGKs8s4fdu9NA5IKS" +
                        "DovceSVAk/C1/D42Q5+yOb0VzeL9s3jvgMIJI59P1cOSY4GqVEztMLJtrngp" +
                        "qkemOgRF63ejPjkGHXMmjzA+XtjjmbUeOf8yY9KHVAXDJUb0Hnf4iPo9jHIR" +
                        "iJIy2o1TEG4PbRG8R36hX/Ec7HEA2mqNm3r7XlI65MMgcwZVIBPG8nZ1XEWg" +
                        "WC4s8mCtfaq+Zhyavqp+RobSDc2yBHo/nx1mJzQ+1F8AXmSeamSzbut2iCCs" +
                        "9lptVFlqKaMGYBbhrZp2PSwBFiKKe9Wwl1Ha74FOjH/wcG73/c5NQy81oeb/" +
                        "eW8VJLG/74s/WB3ezNmx0ww1+OSsqtbArVrtOc8HZC4f5TECl24/ueEI1X51" +
                        "IKBn5wH9q23XYeAoYgLRhRjvDnXwI74d6M5JEBYUFpy8y0hKYBx/nTmli1x3" +
                        "YCAAgM76s5nDe3rm6BEI/xk3dsYYAvXv3KHNn+MWiiqoAvrAVKK9jayPSMfZ" +
                        "KJB+8kMm/XvTcENSl2ejLz7VLlZM5vPZQ6ChJKc8u3oYxGnCCxHNdYkw26Ke" +
                        "R5sLDzsEbXLfAQfmyouHCZ2LndGquqcCeuIk12nu0ssgdeBUx/mtwBt8Wcij" +
                        "1I4pnbg1n/mDahNs9fYDc/BaKzlS4hJeUMHEpNg5WaD/KFh7/00eEdl2TrwL" +
                        "bDskZNU1spzonztw8W6ahwqvTVKakZvAeq/B02zA2KOM4cclU1F6seDfyAjy" +
                        "EmZjy7Fps8kdsMtxOaeXREbENtL83be7Hw+R3Rzrg+kbzyPgtKPtXCXB8Wet" +
                        "qPkK7ffxfwaXDTd7ufXcytzHHX3gAN849m6RxNGnmMOUJwLmEu5eG0vMozya" +
                        "7HZJMphL0EVR3GDn4i8LmOFW/cfCMwSp2LG04jchN1xsDB9ZKSBn3K3Tbxa4" +
                        "5l0Qu0+9SSJSbT7uQW6VsA+SjYM2QiN36/FzhcvpRYl0yJo7kznDzDvfQrXn" +
                        "8Ok4NfdYNHcseLs8xPuyB8caRGMj9YD0HN2EprBo3NcuZ0tkI/E0VGO4LaWz" +
                        "fSu4ike6DM9dZ3YoeDyvYAvryO7kiM7+9Qj2RQy6/Iuxg2cAgBSmn4lB9AT/" +
                        "9T8vLM4bepMQgMd5O3gRXALxrs6Ojo5uJwA5GbLIGzuNOAG+Cm1buqWV7yRS" +
                        "6Ku5nWGCAP7Hftr4vrjrt+tHXvs9y2kpC37DEP5jy/ye5HQLeL8h2QL9vz/w" +
                        "ffzpMkW/rYP1p20zRjOzfLkFOtmQkwfYsn45/RvR+gOthggAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val mangledMethod = fooClass.assertMethod("usesIntValue-Vxmw0xk", listOf("int"))
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val nonMangledMethod =
                fooClass.assertMethod("usesIntValue", listOf("test.pkg.IntValue"))
            assertThat(nonMangledMethod.targetLanguages).containsExactly(TargetLanguage.KOTLIN)

            assertThat(fooClass.methods()).hasSize(2)
        }
    }

    @Test
    fun `Test function in value class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val v: Int) {
                            fun foo() = Unit
                        }
                    """
                ),
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9OBCX" +
                        "pBaX6Bdkp+t75pWEJeaUpuol5yQWF9eGeAcrh4r8e+er/rLEaqHwPM4Vi43P" +
                        "PVFkkSg5lpggcaFQ6vACAzfJkostZiZPUnc5n5009x+TeN6WOzsSF4ZyTQmJ" +
                        "7LCun5xambf88wqHyKl3qs8//n1u5/M5t+v///4tz/DsA6vzGcNc9XB7jadf" +
                        "VgUbnPI15ji24K10u9u6nBDvnV/n1m5+wfIweYLXsuCED0svWTFbL9m/TonD" +
                        "kqVPYOZc29NpEmfiH85Jn7B/zVW1tV57feo0bX1NJl3U8DV2mXNr2eUNq1TP" +
                        "W/lcqzGvOe3GsvjvrqfLQu+ZSjsekWTiX7vqqc6unaIhPIkRBxXcJA69FQlq" +
                        "5zY6duLyiutnX+kdTnmVNWV1416Fz1qTJMTnCPMIX0+98frHqoIaX4t1f/Y/" +
                        "fn3oI6+W3242HueV2XnrH4ndE9m3PTSjQzPkvUho3vkN38Oydqf5x6403Kg3" +
                        "Ry9/mkvnvUUSufmiwSe0Zrj4ubrNsUhbEfAs3HdxTnWIsqbtfXumzy/Febw2" +
                        "7wquflm1skKasbGu/WB88bczbM5/tLq87i0SW7u3L+Ro7BnOee1cKheN10as" +
                        "vuW3OLIki1dffqfsFV/d2rMtncvcWU8bNd8qFp9lUO913fXdHjvlaZGPTshf" +
                        "mbMp9Oxb+bCi1e2ie72aZlyKuL0hzerYuocSSx5PdZa3FVebNiHCobne4df0" +
                        "aRM0GO6rPvrcvbjn0PEdh7Z2K7fWxUwW1fudyX9Z7+I5W0sv6+n6++OkdlvM" +
                        "eno5p1JB7kLFJP/fevU8HpuW/AyfPMunKCPs2pQlJseW3by8f19u5OFZu6Qu" +
                        "KqX07vrUsGxtVpTPgpPtO3dZGGbcUnjZZrUrMTdF+VqnUnCbpydfxsHT+RLi" +
                        "4Qs2XTha88WnOu7slUbnYtVlbYkK/qZV19a8mdH7/JjfTZG760R7iw5mRDh9" +
                        "mzLR97iG3BrJUv8ws+yzbhdrkjgVN15cYxV2VCdim+u141ezzhrKdP/qlXw1" +
                        "94mYud4+zZIuBTZ7piU/pyyZo2H99t+Z9QF9Lw98lFzLGP7ysNjarNOMiVaM" +
                        "nEodVQ/WePv8j//VEMQ/Yd2LY7yd5s8Y30/cvkH1nOGO0q6LFY9Y1qv4Fzh7" +
                        "z3/wjPPdnzdMu1U4+1s61V9PvPMtaLrrq3SP78kp+o8nVPTLPUv7veuksY/8" +
                        "d8Y/cxWC5/76wjrz7fGS5gvFnw1el1gbHj75Zv6D+wffW5+La5j/4J5T0aNq" +
                        "Ra+1TvsXmHLI/WK/KPaUQ5Pz2VJFv+8f180+wHym+smihYe0VYROzUtcODuQ" +
                        "W+6TkZC6+LrCjx22PxaxhxstjzjT859t6f7yfXJ6Sw8v3XH5cMfnwOUzzPfc" +
                        "+pBSH1qRfW0Ly7QPNz7vcz5zSMyK/b7XvKSl++MV+UWE4w/8q9t6fHKs9aT3" +
                        "D//u+906+bG9gsZflpPXdbfN79p7JXq1wbyKpXyPmb/XydxeV804//K7Cy81" +
                        "31o5hm9/ePnjwr49faY2i5Wvlda09G8/mJOTUCy92CxE+nwuy92vE3vX8O7v" +
                        "behlX/dsX+9us2c5NY+6BKUDpYPNjMzydkqddXtjDyqfjkRd3B/NwsDgyYGv" +
                        "fJIGYnjxmJuYmaeXnV+Sk5kXn5ufUpqTmpyQkJAGxCxJfmwaAUkXkhjAZd9X" +
                        "pT17hYE6JcBlHyOTCAPCdORyEVT4ogJcRTG6KciuF0cxoR53iYpuCLIzpVEM" +
                        "kWfF6+0Ab1Y2kDJmILwCpKtZQTwAfGF5dWUGAAA="
                )
        ) {
            val intValue = codebase.assertClass("test.pkg.IntValue")

            val mangledMethod = intValue.assertMethod("foo-impl", listOf("int"))
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val nonMangledMethod = intValue.assertMethod("foo", emptyList())
            assertThat(nonMangledMethod.targetLanguages).containsExactly(TargetLanguage.KOTLIN)

            assertThat(intValue.methods().map { it.name() })
                .containsExactly(
                    "foo-impl",
                    "foo",
                    "constructor-impl",
                    "getV",
                    "toString",
                    "hashCode",
                    "equals",
                    "box-impl",
                    "unbox-impl",
                )
        }
    }

    @Test
    fun `Test functions using different value classes with the same inlined value`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValueA(val v: Int)
                        @JvmInline
                        value class IntValueB(val v: Int)
                        class Foo {
                            fun foo(ivA: IntValueA) = Unit
                            fun foo(ivB: IntValueB) = Unit
                        }
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/5WXeTQUXhvHh+xG1hAiWxEzlsjyo2KsM/ZtNMhOMmObQSLr" +
                        "GGRJlmgsFUpk38k2lrJPQirKUCO7UXbRq97feX/VeX+/97zPPc8f95x7v/ee" +
                        "5zz38zzXBHaEhgvAwMAAAACEAT8bF4AGYKhtoQHSN9KRMdQw0tfRNrcAG+oc" +
                        "9AEAXwwH+g1gIPAwCwx0ZmiAWG0mO3qWRPYDQw2l9Q2H/YtrzChQkO8Z6MCA" +
                        "lBVlSKa3d+ADeYZMDTCB0TOUc0qWqxweoHToJn97PO+hY1zRGBkfT3cZfS+M" +
                        "lSPS31UD7Ix0RKNvWMDQ/NpsB27riNJXm3r6Ap6ok6x6YhdEurFsPsiv4cmK" +
                        "ftQBbMrnS8mgIJSHeI2UZVIrQYbUTst01DYkvNjhqd4x14HEKS3T9RKrFJiV" +
                        "qfhgfuHe4kr5063VsLBvR9rVkXIFav0bfhv7fkM+zXmXPBAwOVm6Sve6Adtu" +
                        "TF9j4USwVW+8cBcb2ObybeG0Xj5giXYYBAKECgj4Xt8MQGVM+02LzAl0JsMn" +
                        "KHx15rmVTzaYOtMTboPWJ2um9FavtiEbPeABJTpAUeBwTJ1zMHMoUJtFv8CC" +
                        "wdbpRVOZfGkQLVxvrGQc5B7jeAo9PKhNjMhQYYHLndIYAXJGaaqVK4OPnS6Y" +
                        "6fsm1Qo+NuVgoirGZniXat6zIYnfbOvUsMe5viAWCK9V3JRNF6lsfqVsNKAk" +
                        "nCAbrRutt2kSS/NEuggvBj+BYhnpKc7sSMAlqPl+5OV4BTesnqEzyT+e/8L+" +
                        "c+MSmq52Hjl+eRvB917HAjHlHDvabPdEYx89fDPTG9LPwUo95sNTzfFpP4BJ" +
                        "+5jjJU3TYpWy8St3mhGiDLD57Ud1ybW8L6yrGAWhNeJXI9EqZMmeBMcegWv7" +
                        "ZbZukoMdoYNPXht5lVsOltYV4yA88ZpGAYKvpImTcuqpLs1vmYazZRu7yNRX" +
                        "t2+ktFWQEwUoJ6gP5tdJuL7nDX3d8xHQC2CudCn2t5w1uh5Cj3Kk3O1lknIE" +
                        "Bz6r2Qjqa9OOB53HbDorlGC1XSx5mN59qL5pskHh7a9+HnZjItSQAWUZK1xk" +
                        "KD/bj+vVjGVPiSuTljRb7AUXa96ecxQ/4+Q+JQuGPFz3DTVaWQruakOy4W5T" +
                        "D1MKLr2r4yMOLN8UZ7aRupue96aCrM52FHXNzSNssIQ+0D//FTKtRLeuuWA8" +
                        "K8kdXpq3/Ha96aOyInEdWSw5lmUe93Av7avtC30rUKSE/vlCGyN5WLDLZ/FR" +
                        "iKoPl0fibgdbK4UlhFZsToucbM7O2wZ7+xm0z+T0hTfDPF/3xYZo86kq3YYA" +
                        "rFLYmvrWxZfX+m9F9F5ivuhFyy4hH8k5uVJv2pTkEUmdlZXzeINzUmXpbCwm" +
                        "xyBwZAmdslo/r/KAihGZaxZQNbFedIHQBhHDKPszhWIDZzfiVsmKk7OLMIQz" +
                        "aS1MdhInIC58l9axZ+RGHpSOAlilJVWBzaTAR/CqBFuZvLogtVi93BA0gdxA" +
                        "O8sc0agalLDTuzZ1/0C1IgAlXRv/cNewnQH9ADZbG85ftlXdMQESfbkNiI/Y" +
                        "RGne8V6Dk7J25e7sLR5jTUj5nDVmX26UUUmVk/6M6fxSv8yDW9/CrbfdsRD1" +
                        "KlGtzZmgMo5tifuqEbqhoQJZrbtn1QpvZBgJjRxkGiSCsMASvu2ui1SemC7O" +
                        "qKv789yZ8cSK1mgYWIGHH+x00EQSWRSZEVmYiZBpNEaTnV5118QnxC+2+vR0" +
                        "U8K+Y2c/e6hejgYAINH/39jR/As7ExZcBysbsDvpWgETClbU61SlObrLODMm" +
                        "D01Bk2JSTTIXBJ/u5IkUr0NKvxlxDaAwW1dERNBmSTs8Ejuu2omt+KAkB/wE" +
                        "rcrxVRt4vZUbvBIU8umPTwt/YmdJtsB2xBc9uA81qn+XZ+qZ9eQQO/7EOd5G" +
                        "vCPczzhwayCFHxIrbCv3TgQikTp23DrmoCmSt0BZeQazbAWKOcSO0wtaHHt9" +
                        "JblO+qm8+1iWIk7quLrNXOHaas/Kwn2vWtRYn5sUTg43B+mvmxRnhZhqcEge" +
                        "PwOWk2ixuDuUh5VyuaqQ9cjOLbPf1gAth0521SBYGsTe98AVObIR+Y2YOiUd" +
                        "Gza/4cqZO5V4C8CMWuZuejtMZR9wFS36GL5S6GUCg47rzBa3nLVi9gIiH0kH" +
                        "xDEKKoq8uaUplnlMY0zh4QJCKlPb6hlwlssF23XbWS/TvjYRz4nlI85FE+7I" +
                        "uNw724MKrl/rqDajnMvr2BOEFExbDXIPErVlnN6zghBq0o8jn2by+L7fgkn0" +
                        "McRzFz/ELTeUBvSiaqitzZSUEqPnupMJHBCKiZbNA2FluHnuRTeM3Wh7VmC3" +
                        "vLEr9ya/12AVOjjnuleL+ThKNZNG5U3La1m7fGQD3fy1Fs68wI4uDh7mK6Qt" +
                        "/W8dHKeh7f1c24UjUfzy90jycGNWl11fy5RGrgfqGsFpm6ZvGzGbG5+75glq" +
                        "xOqxSgsLMh8xZig0Afewgu/SuEXc0FqQfnotheYHdhanTlm42lnqYhXnMwv5" +
                        "xVktT4jmvB0RHxt1q+KxFBFDWN8srqz25uUxKQrJDj4tFbhpN2MPZOi0jrhy" +
                        "7WXZKr7U/ZnXIFciKi0BMXzl0oyAhG6dbX5Nm6NCVHXz2JV66KwoTOBlFmqi" +
                        "dsFt2E+6ya5yGefZwL2RtjtY1C1yAxoC1uOwiz8JxRPn0zgHXgreDWXcSaZJ" +
                        "Eik1tY+TUaO3N0rWWmYrF/EJzSjptts9NrrsbGuUKKQxkkTB49U7akWbd+Iz" +
                        "GoXEEuvVgAbNtSd7SgERYmzZpM37t0ZhBjISlMY32qU5pKHpcEhBhksdfPZi" +
                        "ytf7xhcY1ZPbP0lann5kmLXD+fUPLc2Ys4Uk3Wvuplm7lwU6z9vbv+Q7cutb" +
                        "lOmFj8qSvaBw4YI9rXTAd+wQtO2xlrYAvApBU+j2qiArG2MvGt54zStiz4wE" +
                        "NhZy9r3+Wamn7ehyjhBspTulRdiBC12k+wM7LFUd+7dNR7YB/REbqBmtyW77" +
                        "CG/P59YnluBqPtCb3q8c2prMn+EMrPg8t33ut2LP7Rxxmr7HfnSjN4ntTf+n" +
                        "RigpP3yBRjBowxcZPEukZG+e0f+OHduNE1hMyeK2li4Vv9hMnFHLAYZ+gOD+" +
                        "epIgTn8zCkQv523nl+zP5svmT2kfmSwMJDu8J5REJUQt5vp1EeZ+YOfjHUP1" +
                        "/40djp+xo+Pt/W/gRJvaeXNocKmf7Q+teFE1ivAYQ1wYwc+Z5RkrnnIws4s6" +
                        "ZxoppwaqKDV3gN+NwG9rfj29yxryctuBoXUnPyGwruuczge1yeAVla3cybs7" +
                        "a18WlQCBPlHC5SJFX8dbjoQp6SNl6VZlBWJCLySwfm3ZdBw1PQekW8yNjW4i" +
                        "OiC9FL/IOjb2tLv1PIZryku/MuZflv24mfrxVafuXiKPrvrCW2AdXkH6mSUd" +
                        "9wA533f9EXubwQ4fpPI9zMK+6yBVL/qdSbRLJ71zVICrqos/YDBVOEwKY3lq" +
                        "9J3bNaUcZENzl92K4WlwoAKpDIdHJygYn344X9DRX+I2dB4xSmn4eNyzObvI" +
                        "Olf/ze5BtOFpMVeBCbRR3KW+lbjML9lvgr2ePjq7V3HZ7Xo000j8cYwkjpxa" +
                        "yOA0BtzLf9z0Bd6kgzkIJC7n5sVcT8aI4jTtuoZHhu+fYOF3fGo8YKY+j913" +
                        "U+Zqyk1d+eKisKT1VJPnCVGnkiPqsWTByVq+53FM6XpOcSifXdANICL1/K0Z" +
                        "1+KQfXzaYNa96RCRdt/ovPQU7jKzASGlk8wZfuyNacOdgSOhTVv3ogJUG/yk" +
                        "h+gVEe0i3SJM3+p9DhynH3ILjL88YLEtbM7A462vEhh8TrX7hVdbC8eiqaDW" +
                        "qAj9kukQ5iBG+EwkVtxXjpaZY5FDpUjceCZFa5rne/akZfSEH1ABAEJH/il7" +
                        "+A/9P606ytHDC+zpjUF6eNmjvF38ka7ODg4ObodO42REJ2Hi9MIJ8KMP3xBp" +
                        "buH8s+SZwKiouQB/qf/co3//CPxqf/ct+F3l55LL+4tC2D9097+r/PyCflXZ" +
                        "pPn7Yv27ys+R5PhFBcX4397e7/t/jhb/L/ufMP9j9E1gtHTfl9EeDpvDC9AD" +
                        "v8/+Be9L4gd4DQAA"
                )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")

            val mangledMethodA = foo.assertMethod("foo-GHfTWwk", listOf("int"))
            assertThat(mangledMethodA.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val mangledMethodB = foo.assertMethod("foo-wveqTnY", listOf("int"))
            assertThat(mangledMethodB.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val nonMangledMethodA = foo.assertMethod("foo", listOf("test.pkg.IntValueA"))
            assertThat(nonMangledMethodA.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            nonMangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val nonMangledMethodB = foo.assertMethod("foo", listOf("test.pkg.IntValueB"))
            assertThat(nonMangledMethodB.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            nonMangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            assertThat(foo.methods()).hasSize(4)
        }
    }

    @Test
    fun `Test only one copy of function with type variable`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo<T> {
                        fun foo(t: T) = Unit
                    }
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBvrmX3YQqN3t9+aIr/TypYq890WWbGGf46ag" +
                        "uELLcWnXAgahrUUnMyfvSJx9pHvT7K7F9dz/G4SF2FMKn7grps2VC+jmz7+3" +
                        "p9L48cb6/z8/Ch8ItklbMSNi9ofPJULzNJ42Gm7hWyy+t7hQ8dBMD7WH7Q8r" +
                        "bYz0+9d58xwMPevceNB2SqeJO49f9vr4Ru7z7oKqbU0/VUwPu72/P9fkmGVw" +
                        "V+Lu0tqjcfOPbV2koX7GIOffe9OsvSuNjGe2vE9SPnaI49KPP4cWSs9s3cR6" +
                        "oUHzZfySs7eOHGPnyd+05d1ntu+brzxvbBVSP5Sbk/KybuGUpYyvP5jVx/75" +
                        "evVp4+cybQ2Tyt03Cpm3n5yfnqlXXRbXHuZyJq/o5Qb14Kr7hzZKHee5Os/n" +
                        "0ffTPw3iXJIsut8qZqmk1W6cYLZf5PTyokkcm2MEvvtfLTLs8l+i+6YmRs3t" +
                        "3ON9VybNWT/JRTJxiZcJ34GXr2+13z66oL6jekrrt4Suo3/fa9SfPv2d827M" +
                        "kwqHiEjJB8m9Sb2fSlfdeJa+s11v560XT3q/hn7cdHGTFtOh9p3VzTHazxLM" +
                        "+8s/djwytMzaklfMsLx7rV5eycZeq8A7bxY1zmWc4Sa8nU0jc03mnWs7Fj/M" +
                        "DCkKuhrxnxuUFpb0Ryw4w8jAsJsJX1qQBmJ4UsxNzMzTy84vycnMi8/NTynN" +
                        "SU1OSEhIA2KWJD82jYCkC0kM4HT2VWnPXmGgTglwOmNkEmFAmI6cBkEJHRXg" +
                        "SvbopiC7XgjFhHqsqRddP7ILpVH0dzPh9XGANysbSBkzEJ4H0s+ZQDwAzcJc" +
                        "6cwDAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            fooClass.assertMethod("foo", listOf("T"))
            assertThat(fooClass.methods()).hasSize(1)
        }
    }

    @Test
    fun `Test function of nested class using value class type`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val intValue: Int)

                        class Foo {
                            class Bar {
                                fun foobar() = IntValue(0)
                            }
                        }
                        """,
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTQUahsf+0y2MNdYqgmZpmFmGjtxs2SSuaMZocUSE4MJ" +
                        "kwzXklvWylguiqJRQrlhZAkzQsiIcK3dMfZwy5X1utaMT91zvk+dr573PH+8" +
                        "57zP73nPs/zOj4ATEgYDgEAgAABQAew0MEAYgLe0M0OesMGi8WY2J7CWp+xQ" +
                        "eCy/FQD4G9/2+iccEtUliUMiOtp+L7M93Ks9OnkZZY3XPIHvCnxSbjtvjfRH" +
                        "WLe1aTjMd6BbWtrGJ99OCgIIODFgsdyhYsPtBPrbTvhmesi20zwCaOhLFz3R" +
                        "J/xoDm4+gR6oCz5uAQHhdkOnlO3BW0OZxe3kiMxO29IHyxYD0UmwpnMF4FzC" +
                        "RYgFMZ8Yh8ZxQeItMLL+T9CQnroXYudpS/WQfTRsRIm2VLf+Fqx5YbAd5mbO" +
                        "ucvY8J9d2+xg1QXM8jfnoFcFq+hoxZSsyVDWuouhp4FLV6FOkYJCFFd3pd2p" +
                        "idbK6ityLpho2k2yuoUsnuHsbnWXFG2aVbiTrSKqLpVoYMEdKHGOHWuIAZZJ" +
                        "CLn4Dl3k2oTwmG80JNK1sRWdCf1GanUaNRVNj5z7K5QwEphjUG7iTPvGXbDu" +
                        "gWRblcNtv1KOt5anZp66dBdArvEzHJDouUuvR3GkSe2UqkjiCQZZYS79fUgz" +
                        "ZsoAtTedP7IQOLUqCuKmRY7ZPkoCKjHH2Es1ZhkZCZddGgGi9rJrsKmqC+wj" +
                        "uGe+Gp2Yn6aIxBRn+ZQGBK4eBrcbcGwElyJgmCIVtdN7DBHF/9j3FFYJ9Flx" +
                        "4EtB9T7LhlE8WgUFfQUtSVVDdP2sqKpLbab8ugoh5l6hn5NnQ3+LPXIn2Xiu" +
                        "kkmwk0mLAedljIcmkFsKdCPLbwX5HG30uT+bwpg3TTHJTRv07Oxw1jgZQpaw" +
                        "cIn6Y+6VW2zwrjDdYtIR1tAmxS+kjmJQnuyc4QMtPJPF8g2F9QcHHj2WdL1F" +
                        "BSVGuGVSWvd0MlFw3leVP7U0ym49zmptmoqwhkLiWm8D20EZOTrS3W23y4oh" +
                        "2JPS5OAspFTew8hpY6jRBkbJoUG1tFA8ZnlB0TI/NPjWZH/q6ZEKGzlzR3mS" +
                        "aWhqwlqFmTkQc0yvq0TdkoLsd7OHy5kUHrP88EGZ4/GEFs42fYgcnjFJQux3" +
                        "jHbLN0UY+zpUFnK0AwpILd1vrMl5Xno+LOl4oemsszxHzHS9i3OxrrL6dAlS" +
                        "f+rZo/F++wKvaj3Gj7R7GZ6heva3ER2/vdIKtebreqmLQ8xuaVryJnOf6hyH" +
                        "/RIVaiWe+Lt3/Dpnd+28QK2QbPlDyqufZa6NqFND0n4BlfjH435LVnu/LuMi" +
                        "16/mZNKQWbeaXH3A+/nkuxcTF8Wt/EQV4VpRcoN/NcEZL5ZiBdsSgt6sg/yg" +
                        "ywBVxv5TjIvLwi0rJLbZ+5vCd+zZDB+98EX0/Nxu0F6brEjoGMMw/GMYS7Jy" +
                        "ifcAI7pvSywK2SqjJatpZcGcTbbTitgIRu9PD0x28b/bOQEiVys+G13Yr8Za" +
                        "HIt8DYG8eCdUZeQfz7pZMpLN51UGIVHPGvPW8S9uBNSq/dlnqsxcedLgiFTr" +
                        "XgWIR6wgSWmeC+mjyusY+bDpH5pTpK1b6xyoNTxnzjrNQSlp3u33Tdl1rvSN" +
                        "mKtYg94fjpiT0Pf+QEQ0R/SCrkyMJI2z5viKet7D2X1yGZKRKKqq++gFIREh" +
                        "jWBrXKHSqvB9v0jWmcZ1WKyzFyqLdWNt/Dm9kH6Dnk/fqJ3cV6MB0UY9cnJ3" +
                        "cvo7QQgluiX0iX0Uz0c/NxQGAJbEvsc+sjvZB0ul/ks8eAIep0wEm6zgtbr8" +
                        "dYiOcnUDbxvYl+x2iWhE7mowtSO0wArSJ5Jr3RcftYXJpK8hN8WYwhBZ9ock" +
                        "RE3OWfeAWcN3QxuzzSNX0cWA2jFtFVor+/3pOmXnR96gY/lLE1Hxst7sLnzB" +
                        "Yx5TOGn0vlfO0KH6gU0ekBgNfXBNNnv0FwruTP7Jq0tRT8/MPUZ1wdi2NkG5" +
                        "qR9u1kc1Jxy63r/H9MGPG0lGp8XajK5Ho8eJCjcygMc8jHe9jM9POH787bXE" +
                        "liNo/toMPe6VdhK8eKXDg2Nx8ucRt4JoJTeUHlMlNZs3dyRrSXUGbs5iJrD3" +
                        "SZ1DVKcXRTPyQnlpJyzIRn/IaccnEPstVYvsn5+/lC5VVsHuXRv4a6bTwIQg" +
                        "6at78L5iqtEUjaIppbKKSu1b1MYwy+KGdQiDYZw2ObkISezW0EvYBYOxMqBB" +
                        "KJckwXWcHESMLzv2Kf3o/7GDv+ddLD4Y8+cFzZfwnAYwHZP2dFD47JUXzbBd" +
                        "8hFNcTFU4qb0p+bZe8duhAsAABsC32ue/FfNO2DudvnfBvoSzaiyZmA+WT5o" +
                        "KnLG9ol5H+UZorc3hpPJgeMzCrP77BpjohRdC0+lDZE9vYxlt25m1QSXLrgC" +
                        "hz8SOZUPsAUBq+Rh/ZGV4ef6I7VX+YKj+2YPB2Mp41Bpqc0hg2GvuJcCastW" +
                        "94BHwV3ZW+VhB8I4EU2uRwnJWtKKhZQiZ1P5CrrEntj4kCKdxsvTPqUbEvTi" +
                        "t1UaKUVRQZULpXX62mISTSPd+7V8ZRJzowLsqiDHeQdysm6mInWNpQ6F6OaL" +
                        "iGjWB5TcUD3rGMWNRiyu5MqEuwnbjzXK0c+RDmnmAkT19vMTrHOMGOuSsZ6p" +
                        "C3GvXw9ODrt/WNxzX8zQJSu+MoShW9azoreympriWbQY+uf0ocx4kELXXac3" +
                        "ZY+pIVlL0356+uGVDp7ew75pDmXvwrVundsqx0prfjRUEpvWuU5+0rHVPbhX" +
                        "4a/Ag7O42Ly0uAm+F/d2i3iXpIcRr+pgQ4WVndLWAXEzcIv7QfA5nFLdhvE+" +
                        "dRNWQXd1az1ciwIfsNaQPZr4kAvM8WJ7XeupzKP3VE9BQFTbicfaOSbVayBW" +
                        "Utm0Y/RyfLl/5tPDEFcB1IzlhGfbefI0+DW04w5j6w7SDuT81oA5LorIb3dS" +
                        "5uZuyiyodzNpriIG0FHza3R4j8xtQEFVr0BPVbzGXgMlJicjtsjffjwD65Iz" +
                        "/3n7l+6BeJvbwzMh+L0BUt72/0ofXzdvP9RFKs3H2++8L9U90MfjgqurK3nb" +
                        "hUk2onACqZME+Kxr/lF9XiO3HanwWdcICIIB/0PfqXk+Casv7Vsy62uUndwF" +
                        "+QLh6rfV0tcgO3dI9guQTeH/R3pfx+8sofwX8WzRb+3d1xg7S6X8BcYG8Lul" +
                        "J+BERD89E9k+Z7Y/cRr06fYfVDQfO8UKAAA="
                )
        ) {
            val barClass = codebase.assertClass("test.pkg.Foo.Bar")

            val mangledMethod = barClass.assertMethod("foobar-RVb1_dM", emptyList())
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            val nonMangledMethod = barClass.assertMethod("foobar", emptyList())
            assertThat(nonMangledMethod.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            nonMangledMethod.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.IntValue")
                assertThat(modifiers.isNonNull).isTrue()
            }

            assertThat(barClass.methods()).hasSize(2)
        }
    }

    @Test
    fun `Test function returning value class type where bytecode method is not mangled`() {
        // In the case of a top-level extension function, the compiler-generated name for the
        // bytecode method is not mangled.
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    fun Int.toIntValue(): IntValue = IntValue(this)
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/32VeTQb2BfHQy3RxlJbainSQa1J6JhahtYSEhH7rsYSsQsl" +
                        "1mFKLWOnpUVrqWKsVXRhakk6WmtQRkQVjXWKiK2iatSP+Z3z+2nPtPed+8c7" +
                        "553vfe/ed74fc/QJNkEAEAgEAAAQwPEQBLABMAhrXWWUqSEMo2uKMkRYWUMx" +
                        "hp/6AIAtDLnfBK0MHeZGKysMkoceWcJHL9AWgqHGGCUUZji05rHlurHyVQVj" +
                        "MlnRdn0Q1ttLnluYXWAFmKM5gQ8F5B9qHBa4eJjmXy0PPkwCLoQAC/LzgqHw" +
                        "BFs3/1AcFOvvFhISYz0Q8saa76D9L5iDrfhrnz/tuV7xeq9Gj+LlKrJly1Hy" +
                        "NkGiyFQIN/jlsLqx/2v7psruSRqMRmLn4LkSHCuCVCUkcS1lEFPcxs/3hnVt" +
                        "wMsi11Y7pt4yPoWVfPq0k3WJZfJlHkieSpiMxhJvUgOaPaydPEHpulMXJgw7" +
                        "fMdv/T3BLHYIwwJVkEJ/NrsBcSGa+uWraBM7vnhBTqNCCJ3hyNOJ3GBlindm" +
                        "O70NunvbuaDRN0Ohc6GuWJF5fzfcI7zCpXm0qUbL80KSW9K7E/1PJ9lhGTev" +
                        "C46oWqfDQeHncO3kRZkrsn5lw/DVs0kF9JdIcDxn3KLglef5pAG4Cahvs22m" +
                        "BV7Il9R/EPjhGTw6voW+TgqS14plYacGOY9PS+lleFbccwWiCs50oKQZooOM" +
                        "4JhuEBJkFTWaac2bGAFXvP4AdZLSaAvPKEyXJ8v1pAoYFUPcfqGo1pgC1O5Q" +
                        "0vYHSvq1vbKdmGH7iV4VtACDxB1n0bhnrX4/iIyA25MjJR0JkXxV17XyhdTX" +
                        "9mRyPRIQYErwVNikZy197K6uo+X5p3lueJJ3ikqEFEli5MenIrkhOCG5obr6" +
                        "NNWIuUx7T67KFpfw2ttFb/2om4qrd/A5UobfU363V3PGdgdxj2w4ahkEJqXI" +
                        "QIXNc2NGiA0JBiwfeFgOCFFBzovYK4vLS3EmknYyBejTEwKPjXzMRgYrHj0E" +
                        "d5vyekbGKXNXlilQIyAEJvZ7pURKT+pvxtLdXjI2Y39g60jFbR/HiJiqvtQa" +
                        "Hi6/ERWzPGlem7OyRROUitH3UTVpChayjvapNY2PAs+Aqy13rS/f159ejcnT" +
                        "lDJMKDa/YR8xURNwd8CrK4wi+OBBXoZvr7ejHk+uvli9xbSLmmicUuCo00DT" +
                        "7ji/nmc7rtV2QMkhE9HMG9DKEK1HOFdkhvg2JRcLE0GaKV0IPhlMSnmb1G1m" +
                        "z3xgNu8SaUPjgYVZ70biXzQRKd3zy0kR4LitVg8IQxVWipeYs4x6D7wTlkAd" +
                        "nTxdLzEz4NLWo50Nmt4Mdp19wi51MR4kfeM5F357DzlFep/EWiJWVPdEYFKH" +
                        "HpJMiDcJ76efsny3s8Reep7/Q51FqPv442oiiagvTVAPPflLQvjik5S1xDPM" +
                        "3YlyNQ6JA06UZHBu7rxwOcR0J1nhBimmDRyb1gGsbUF6L7OFtKdH70Rk31CP" +
                        "ZCiAYdAgF+DkQi+WbJZ5wLNLjWvfapyH3qZzs3xHToQW2J3At4wNndwxMhyg" +
                        "nVDiGGtOFrgKvsbVUX0WW010uar0s6n8BnV5zc6rbzy2t492WodeCyvNOIi1" +
                        "/+CVoK/d9J0Bczaynj9C3LyIZPRTjPiQ9tZQA2IPWlT8qj5npU9YT1NnzGK0" +
                        "MbWS8OJOptn6knB+fKi/v9uLuFlaQXx+M1S9RyNZPVmzZc2HMdwaDF9xndAd" +
                        "e65mpjEvtSt55D6XM73yNNgAgBXOb7mPyL+5D5rwX//BmWPQnZcFC6ceV3f5" +
                        "lm3XbjtFmetaIdZpKDaIvZB11JLP3aRnP2CkW09n7SsX77PM6r4ou2Qc7STE" +
                        "Btuaboskd7zOGsqzA+zZe8mtmPus1VO3/7a/iMhNrHwG6KJNp/48gzjHiAKv" +
                        "voHfq35JezcfngVpngn8CMo5UDopl62b/cK40uFeDsGJuXb/nKGb1pk4fXfM" +
                        "W6TN3kpXuk1Z3jtk17RDFcLI+2oPaW9H2LkflC4KWGWadKKis3+rtaK+xlW1" +
                        "c6cOviHNbE3OMIBPKDfRpBX/NOruXtmAcz+dYbHZRWxdUlOV3KvQ0sZAsnJ4" +
                        "nO18e9R/HPq4Cm8XXk2WtcqQ5rOwKh9ZLykNueXhw1eM1FfIc6jDXfI3lkFp" +
                        "p84ZnFJecmo4lSne0Wmd/SsmNCXf6w+LAbsSFaaHqnvBKwke5To+YSK4KpN3" +
                        "mwKBumcss/gIlN9ybIhu0ZGNMSmlY0Uv6HiqUFmt8o0qEgv1fiqtabjneo3l" +
                        "aHb5dvCgUBYAgJ/1W7MTO8z/gSvAzQcP9Qsk+PvgXQICPUL9cVhXV1fPw2Rz" +
                        "N+WQo/HPzcqhDkGm2GMKnt0kK3KhyfKYbaiF+yt3wD+0Gq7rxyseKsL/oRUL" +
                        "qyDg/1WPk+wIl5/H1+D5pcrxHwn+TOHa1xn4pcjx1oh8JrLP9o2v/KXM8deK" +
                        "fSbzO8c3u2qOZuc4OsZ2uMQO76HMebT7DzjBy8xeCAAA"
                ),
        ) {
            val facadeClass = codebase.assertClass("test.pkg.IntValueKt")

            val bytecodeMethod =
                facadeClass.assertMethod("toIntValue", listOf("int"), TargetLanguage.BYTECODE)
            assertThat(bytecodeMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(bytecodeMethod.returnType().toTypeString()).isEqualTo("int")

            val kotlinMethod =
                facadeClass.assertMethod("toIntValue", listOf("int"), TargetLanguage.KOTLIN)
            assertThat(kotlinMethod.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(kotlinMethod.returnType().toTypeString()).isEqualTo("test.pkg.IntValue")
        }
    }

    @Test
    fun `Test functions returning value class types and visibility`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val intValue: Int)

                        open class Foo {
                            fun publicValueClassFunction() = IntValue(0)
                            protected fun protectedValueClassFunction() = IntValue(0)
                            @PublishedApi internal fun internalValueClassFunction() = IntValue(0)
                            private fun privateValueClassFunction() = IntValue(0)
                        }
                        """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTjUaxv+YRqEaEyNpbJLNDMmWSYUacRMlpElMWosY5kx" +
                        "k4xiThsijl1lK04bp7JMYx/78iGyJjEkhHDsJTKO8el81/V96vrqea/7v/e5" +
                        "3+t9rue578caJwCCAkJCQgAAKABbAwqAAAuMrTHc3NIUaWFsaW6KOW2LsDDl" +
                        "tQDAZ4vWV6dwcESXGA6u3t7aUWCj+UZrePwiAmtx0NyiK+B5oc0CFu6njm1t" +
                        "1bBfaEc2N7eOjn8Y5wescYJCTMkDTPTmA7qbsP7p87BN0D386cgLZE+kOZVu" +
                        "T6QEeCDcKER//6u2g6dl7aAbg/eYbaTge502+Q+WTQZuxqk2nM2GPrEmw0zw" +
                        "T/G/I3F9wiLNqiTdU3JB3dU1gufoS7WwfXTTYJbWjte6G6pNi+/aVInHG1PT" +
                        "1/zmVtfb2dX+c7z1ebnr/KVRSOnEjHEGm+uC9tRz6co5nCslFdqnvdLm3EBv" +
                        "YffkErLHGiRcze7AmbONEi3uYuCGOamUhwpglR2xeiZ9AyxC+EhdmFCBqICL" +
                        "7yC5zzKoP++thmiylmlxZwzniFK1RmVxQxaBUyyDEkWdkOuLnW1bS4VqKyfY" +
                        "KGi2xvucbCm8fe/0hVSAVElFD4h2p0bVIhrFXdt8SkPw5ukkqfnkyaAm1JQe" +
                        "Ym8yb2gxYOorWLgvKWTEJitOSCZvpGyp0jgtLeaiSz0AtoOsqk6VupXp44p8" +
                        "NTpRp6bw+ETC7sQ6dVytqprtgFM9NF9dFZWroOSwB63O/GLXnVPK12PWqLZ0" +
                        "qZayjA7tpxf7IK8gxWhK6l2XpRW1aU0+8V9h+CdXos7uLpN7Fq6fkmAwX5Jn" +
                        "bbszKQyamTbKiCE1Z2uHFN65RDlWT/ljLjF9wSjR8EnSO8/OdoKGVRBJ1MQl" +
                        "tHf+JTE8cPtv2kxXffbgug81qNpHrzCBkEaRyzmTwfZlqHICA46diLvVrIAQ" +
                        "tL5jmF/9YjyWf8FXkTe1NFzWcpLd0jAVjJWD/d5yV6hNOO3xYfHXrXcLmDBT" +
                        "K3FSYAZ8R+ajkGkDuSNrKBn7OsX8HJGw5UVpzFNG4J1xzm2HoWJLyeNOu12N" +
                        "GLdjVouNjwuhTuh0sVQwPnAO0U5N0jDnBGZmRrbR4zn9apnRI/j7WcM4dXmn" +
                        "m8SnRuoGvvYlOY1a/tmuza/fYkmZXjoUtni0wHSGY78TarrWhcDUllWZZsF1" +
                        "p4qyRjl22V7lOulH6ffTPBk6dnfV25+9PMTA8rS9VERgxncOYvrHn7w4fFL1" +
                        "WijDTCS2wzua2yhRtcBXJQApfOTz8vLOG0MqtKCka8Isv2jcswSlSe5OF0mO" +
                        "krNh3b3qrwnlyt4V4xM1Y2QRMypYWu1QqOS7vxrU0muWwvlbYy695QpT5ZYB" +
                        "xXT50+nkZVDzimuZ8WQkKMWuLJ2ic/UTcmFeQnivZUaI3Eg6+urfv7HFSpb6" +
                        "H6DA+zYEQ+EtOw9BDpqZ5M0l2B4KXgtEyicHJLj4pXaOCZPKpYuGF+WV2J9G" +
                        "Ql7BYDUTAqVH/KLZkayhh7z+kktwRFF9JteiJsK/Suljj5Fs3srzOie40uuv" +
                        "gEjwCtw1yXMxeViWi9r92/SupkRxbEu1Pa2yn9DIpdvLxC0QO9Yh3D7xiLDr" +
                        "pnpvdukfd0Xe71UPbgp+I3xlbChulD3Pk9bxfv+wRzJNLARBU3QfdhPYJqAR" +
                        "iMXlyHwF/UENYZ+p56qGE7wQGeyI1dGKqJyoiKinUWtV4/sqNWBaiCxnd2fn" +
                        "zzECCPCGwDf1kT53swINAoAlwV+pD2Sr+pjSaP8RnpjTHVaSeAnee3L5IFWE" +
                        "4l3Lz3swLG5EFFnMQhlDPqQ9Alsg97QWD9Y8HhgkDZFHWYvH8EwXA3ODFDCN" +
                        "h2/k2ETfteHUV6SufryXqktbnmDShvgMbjbE4/Ns7bP9vGbBxpDoWoNLR2LH" +
                        "0byHX/IZM29tISjwLGshjFEn4eSY6x5/gyQfq2zivFe4WdORp9+lFN5gX/5X" +
                        "5NmGxf652Akz9sAyGQvica9llq0WV/VaeWi5b0j4nvtSfzvlgY5PMNKuL++Z" +
                        "vDOsLelkpv2MnX7ogRSwaoLHGWpY8KMdg9zSQ7g6B+qk/vvSB6kXlawzFnId" +
                        "8AOfXubSSkCEx33bMU34OT3q8v3yCfU/59RuBVZwIruiHZVtEgABcvxGekQO" +
                        "xPuLRbhVJfX98DbqhYJe7dL2DlFCr6dI5dxuJOvPyO44Gd3Vz9JaOh24Rc+Z" +
                        "JuHpAHNN/g+MujdC2GWFo6JTV9V6bn2ojze2bj91slMzHlXUvWJgAYfG3LzG" +
                        "HSFFYnKprEn+z8lWiQoOVQvxh2+xcRNNLzQ/rkptT4ZLmHArakxNus7OgAKS" +
                        "ic8PXlnBlDsG1Ota9ih10NFPMZXhivmC7WHrxcwT3UXrsfEhzgNVcmr7safI" +
                        "lIod/cS2wV34yXfyhRcVJDAXzm5M4jqdJ2nybGzSLkP/McLa9nWRA3vIykVa" +
                        "++dut4ouQUWPFJitGJqM9QlnHdsweWsa//klIeb+Hke01T7e9fyjIX+fv9He" +
                        "iY7hZAckyCREuFUiHV4YTxwl9gpp7JEnubU3VsRsc96/S5fy7l+5ApqRGKi4" +
                        "pgLR7KC5F+QVpETlE0Q6Uh9SALmschGyovy6mu9bz9bHBQ7T+QFgFvSrnpXd" +
                        "xH8N25foTUWQaXSKN/WcL809gOLhdv78edImQK6WYDVr105X4B83/qJYUSm5" +
                        "mSn1jxvz8UOB/7Fvdepv68D38bPl4EeWrRMH+47h+s89/keSrSWAfEeyDvp/" +
                        "o/pj/tZvyn6XzxL8ZdmscdvA366BNg+UDwDEvvEB/wY/8WT0NwkAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val publicMangledMethod =
                fooClass.assertMethod("publicValueClassFunction-RVb1_dM", emptyList())
            assertThat(publicMangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicMangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            val protectedMangledMethod =
                fooClass.assertMethod("protectedValueClassFunction-RVb1_dM", emptyList())
            assertThat(protectedMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(protectedMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedMangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            val internalMangledMethod =
                fooClass.assertMethod("internalValueClassFunction-RVb1_dM", emptyList())
            assertThat(internalMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(internalMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            internalMangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            // There isn't a private mangled method since those don't need to be tracked.

            val publicNonMangledMethod =
                fooClass.assertMethod("publicValueClassFunction", emptyList())
            assertThat(publicNonMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.KOTLIN)
            assertThat(publicNonMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicNonMangledMethod.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.IntValue")
                assertThat(modifiers.isNonNull).isTrue()
            }

            val protectedNonMangledMethod =
                fooClass.assertMethod("protectedValueClassFunction", emptyList())
            assertThat(protectedNonMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.KOTLIN)
            assertThat(protectedNonMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedNonMangledMethod.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.IntValue")
                assertThat(modifiers.isNonNull).isTrue()
            }

            val internalNonMangledMethod =
                fooClass.assertMethod("internalValueClassFunction", emptyList())
            assertThat(internalNonMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.KOTLIN)
            assertThat(internalNonMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            internalNonMangledMethod.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.IntValue")
                assertThat(modifiers.isNonNull).isTrue()
            }

            // The private method is not generated since it can't be part of the API surface.
            assertThat(fooClass.methods()).hasSize(6)
        }
    }

    @Test
    fun `Test internal visibility value class constructor`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline
                    value class IntValue internal constructor(val v: Int)
                    """
                ),
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9OBCX" +
                        "pBaX6Bdkp+t75pWEJeaUpuol5yQWF9eG3A2WDhX5f3f2+rNpjbMvBG1a8M35" +
                        "dku/2tGo1SJLFmztXhi6IvDUZaGyQ06bOm/6Luvf8zL+QbO+ld0Lbn6r4MaN" +
                        "xnyPZtdPflzxLm2ygeJjnyL7c7b771t+Lz93d///v3/5Gco3Hp3wRef66avf" +
                        "T1se/tiVtjTz6AGJI/NuGhWLXtZ8Pi/vfaSqfpuA846cSte2ALVsgw1if3l5" +
                        "DTUKC468vHF7a2zbxUOs5yrbFNdve2ias9wmO/y1aNtpSeXoF66Z8yfen1p3" +
                        "u+jyujcRvRJuEov4c45/E/sr0iU1yWOlgESqR4lSahQ3l97k5ZHro8/xHc55" +
                        "ljSlu7FZ4bPUuoSYomWSauypFfcKnaz8vI5P/V9eE2u17+CCEpOEHVMkC6p5" +
                        "rxfE/rqnYHjWzXJ9IsdhrZXVk1numN69O/VWnyiHxLIvK3hOynFnJkxdGhQs" +
                        "surx2kS2SNHLM65OmKjdJ/MytfaqiXaeg+k0Q66/rPPP26UHzD5X9rb1/dKH" +
                        "uS6qv/Sclzx7G8XbdsVVRPP+smmici6rHvI9Uzn/+622SGhLKqvrymc74hZH" +
                        "fsrqVdBdKnz7vIH1grUp3DYTUvgltxSHnwrukzw8IfzBV+uiGcp9h+rOrr7h" +
                        "l7c+9OzabavanKVvzjp57uCspzOtl6VP6g2PVqxS9uErtGzYVTv/jX7OzsUM" +
                        "Ne0d9Va2O9Z9KlxXVFi14FI/t+tTr5ZnLGcDZ7VnPjt187r34+XsxTbnp00+" +
                        "9dJRS2vHuc8LN8svTlTVUVq9KyBDfn5wy6r245HzbP+9vv0t0yljtROPZ/4m" +
                        "w/tmPhyKbifUdm1b4rFWffcaLtXF7VNXdMytPbzjzDSRAzvbi6rvL2Y6wZco" +
                        "oTlBwH/W1dzVH8/cWRexadPWSZGas3ij3zE/vtCWPu3b9TNVG8LNs866naxK" +
                        "Oe1btfdlRdlVrVvrfJfzyy5/NnOer+vSpuJVG3vmTYrnjjq5y1VA9fTzLac9" +
                        "czP7fPa2VHlw9yn1Bt6XOCRud6DcoSksROCnDtOH3U90z19ar2DWX6hpWsYS" +
                        "a38oW6m6ZbV7xWdx+6J+ld7tf2Rm1OQwnjyrwOyaVtAU/VdiqfSMeQENOy+e" +
                        "jLI/+PawncMS6ZRl1Wf3Kfys89Hn2DuB4fOUPPXfvvP/Cf+xdnFqv7z8gXtF" +
                        "uuP8P3F5h3P37ltiyCb3n/1w7ilDIyEdD+d17yaGGDX+rtBXmF46Ma5w5oUv" +
                        "nM+qJbc+/KigvPPTw6bT7OIHxJl3WJX2tK7svL/gn9WGsly9raeX/vI90FG8" +
                        "UfvZVQfpdd9XH7qtq3zpB4N343fdpGnpH8MfzP5lOO33a1F+B8+fF1+tD19+" +
                        "+kbDOa9kVvlvd/UX+v9piPiR3uJsu1FZ5dulyrVCPzQmWDW6//trYVb7Pb3C" +
                        "77PeWrtlPw0de3VbeNZI/TjiwJi95Yhwc9bfl2Izus9v2NcaKD6rm1fcsD6m" +
                        "ZGKpYKFgaeH97XcWlz9NOne0p7unO3tf4YmjH+pBpc6WtUpvDVkYGO6x4yt1" +
                        "pIEYXujlJmbm6WXnl+Rk5sXn5qeU5qQmJyQkpAExS5Ifm0ZA0oUkBnCJ9lVp" +
                        "z15hoE4JcInGyCTCgDAdubQDFamoAFcBi24KsuvFUUyox11OohuC7ExpFEO+" +
                        "suD1doA3KxtIGTMQXgHSgawgHgAluPxSOwYAAA=="
                )
        ) {
            val intValue = codebase.assertClass("test.pkg.IntValue")

            val ctorImpl = intValue.assertMethod("constructor-impl", listOf("int"))
            assertThat(ctorImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(ctorImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.INTERNAL)

            // Value class constructor can only be used from kotlin
            val ctor = intValue.assertConstructor(listOf("int"))
            assertThat(ctor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test properties of value class types and visibility`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val intValue: Int)

                        open class Foo {
                            var publicValueClassProperty = IntValue(0)
                            protected var protectedValueClassProperty = IntValue(0)
                            @PublishedApi internal var internalValueClassProperty = IntValue(0)
                        }
                        """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/32WeTTUex/Hf7YxsjOMpe5YMkfDjBCZJCTbiOwtIjMyZQzu" +
                        "ZAhR1/rIRNlDlC4elQhDZrJFTMYQiowl+40kuxjb1X3OeR51nvp8z/u/7+f9" +
                        "Pd/POZ/3edla8fBCADAYDACAIrC7IAAvYG3qaIy0tDHTsDa2sTQzdXBEWZtt" +
                        "MQFg0ZrVetIKieoUtkIi2llvyu0PvtMenriCwlirW1p3Bjym2s9hkCQEhsVS" +
                        "c55r12hpYY1NjE5wA7ZW/OASyQMl6J0HDu/I9qfPQ3dE9vQna/zufUnD0pfs" +
                        "jCUGeKI8iFh//zDHQQd5J8j2YFZJGz48q8O+7MGKSX/UbXjTuSeQPFtvqIld" +
                        "oV2chlWvgGALHH/4JCz4bV09/wXy0kvob2Sz8FJtka7D23DG/EAbHHu8OSN7" +
                        "nfRlbbOdVuf/ZWtzFnaDu4qiIZuUMxFC47ihL+m5dRYdeiojE9mr87XtfBOZ" +
                        "Set+6vpkvEkMZ5GCLJlpFmNeFAY1fZG5m6sIUhFJ0DPp7S91jRlpiAaXC/G4" +
                        "+Qx699oE9xX3qAmla5tVdsSzjyjXqdVUNhW4sivlNIU0T8B6E2ba1jMgOvsT" +
                        "7RUPsu4QzJnU5CyH3zMAfI0vul/obQblJapZFNdGqIqws8zGy8ymTwYzNKf0" +
                        "UPvSt4bmA6ZWQQK9aREj9gW3wXLFI/SlGuPMzPgrbo0AyEliDT5V5UHXt6rw" +
                        "UevQPDllZ5fkKp3UgLB6CVd17HdphJQh4JpPFZVP70UjSpad3hZVcXVbNKsu" +
                        "Bb4krqAj+8iVBI1QDWE/ZUTnVVklHT8G4c4q1C4vlHJOmg57FKN/N/Ho7PNi" +
                        "W0fxtGhIfuZYSDy+5YlOBDUlkGjYSLz/JSl7zijJIC9t4FJHu6vaqWC8kIlb" +
                        "5PvZ19iYoD3XdEpw+rTBTYJvcB1Bj5romkmEFZ3JofmEwNlBAYYnbv+rRRHF" +
                        "b5tiUFb3bCKBe85HaWtqaZjONKcxm6bCMTBoHDMV3CaQ+ech0S5WankJ1OyU" +
                        "KD4oBymS/zBi+ijsyLqmnHODUlmRYPTKvKxpYUhQygQ7+fRQpY3kcRdpnFFI" +
                        "cvxapfFxsOYJ3c5SFVMCko11UpU0KDph+vmzfLPnY3IY3egh8sOMwW2EgksU" +
                        "ttAIcdTH+XlRs7b/E1xLVw8Gn39Zl0gTvcUznXO2z0Vz+qWba4mOvMp0KfLw" +
                        "VEXBGNvpyeUXutnHyPcyL4XoOqUi2h+91grBbOlcVhGEGqeom/ZN5D07ZA6/" +
                        "HhliIZjwxusWp1msdo6rlkeC+pDw+qr4H0MqfsFp1wVKSbesHiUqT3LE3STZ" +
                        "yucNGrLqVhNf7PeqnvhYP+4taOELklXVipQc+NSkml2/FMPNig/s4Qj4wlYA" +
                        "pWwFh2zvFd6Wrzi68eRN3rtO9GyibtiCxtysmMA+m5wI2Eg2OmzjGk34+VLf" +
                        "A03Qb9v8kUimuJaEuoVJ8ZdER63w9SANhfSARDdSRse4AP6FbMXwvIIybWEk" +
                        "ohUKrf/IU3WEdIt2s3Qod6vveSASVdGYz7Guj/WvVf6r20i++OvjBhekctcq" +
                        "IBj+FYlLuzSfPizP0ZS+Ni3FSBLFMOuc/Wr6XJs5ZGe523PYN5sSnF7R2Ogb" +
                        "ZnrvpPSP4zTuvUeEM8LfCYSOD90eo81uyep6fcjtlswUjkD5KV0c9uDh41EL" +
                        "wlgVya3y3veNoJ1p5MBjXC+jcmixa2PVlCJKLKWQsl478VuNGlQbVXD+4vnz" +
                        "i/E8KNA2z7f0kb0QVY3mBYAl/l+lj8Tu9DHz8/tP8MQ7Dp3qd4ZsFS82JmgO" +
                        "zykLW+amcEdCLNyJM3QC0vPxxQMzCF3P1Du66Jt8Ma8SQv1krrUWeG0zOAPQ" +
                        "PJunKwdN2f1bhQRGoIXs3cK00PYaxtDal6vtNe+3a7en/YD+1SRjSWloU8+8" +
                        "8fyjaaa7ECcxGCctX+Bvu1EQcrE76paMyULuTO+oWkwvk2Bqwm0pRXotNMUV" +
                        "PhIbx77somX/yt5/H6o60LrqA32QqCMzqtb8OBfLm7lH6k8htvOLvmfexl2+" +
                        "jeMb0R8r1U9tsOrBeiIGfAJeMaGt/nwHSj+dLpWDmwxATNoDoybQeOU9aQLY" +
                        "wxaxelR045k13Mw7ij5/WhApPm+5BhOwyOCUP+ACYdfEiBPUNYepJT11WoOC" +
                        "4f4ljvKnD2V1r5abQs94Zojyi6SdUTIb6yszFEPN51ZxDf1lPo/MTBjb49BL" +
                        "bRV6O+Fx+hDx3zLkKNi55ew0a2Hr+4z7ZxuKshry7r47erNzxKPWcg1E7acW" +
                        "kwnr5RLxn7KEBn0vYVLLkhBTgwmIA/Z6dodUkmcCK/MkriVN1mAwdOrzAsYV" +
                        "+wPX6/qDB2aqP29qrbzR75m5g+V9LRZ+RPy0BD2Fombp6harFpfT59JhvpGX" +
                        "XjO0xf8+TI+rAtCTAxjHh2cT6+eBwulHH0q6YjNtQ26wUmWmqmfelzZtfIK3" +
                        "ni4GL2PHQ6tJ6pMPPtMDKNhqon7PYmA5IXgooxtkCmf6tg2B36x83HzAqehe" +
                        "KMq0NI9EMdz6xQPfat2TzOpfCvs42lM4RPcpwRtUbFZnHKNe4YvtJ+uMvtuW" +
                        "CaYHHUraoEitnhyWQkQXG7B9MsNOkR6LlEc/t91TelVmXVIyzHDwK7tcYVO7" +
                        "TBbDV0NS6lDwiJv07drwjyMoiuPvKN83y6mNokA2y0c+Sn1wH13+Q12WNxma" +
                        "JBk5fpa1nAwTO7dAij5qqU9htUfFXq80pJtWaCUdhh3AccrA6OOvIDra9q+W" +
                        "PMUXbMW91E/EvXaTzj0ROGpDjf3sKc48KL7Uej6PpD4sa7r6rLx+UPDz5rFO" +
                        "e6N1U3fDfJVwmCr9ZMQxtjmrPi7ciYdvIF+xR6jIwwN2y8EzPywPfWVv1l7L" +
                        "BCGGNEOJwc2wtjE8xyJN7k3eu0i2QTejF/aWb3N928pCHbqXMw8ANIN+tZXy" +
                        "O/ovkvhgvXxR3n5kopfvBR+/iwFETw93d3f8jnhxNiBVW1wHDviHN5aVqmsk" +
                        "dzpl/uENLm4I8D/33SzyDXi+r5/hz48uuzMF+p3DjZ9TzI8mu0cg8Z3JJu//" +
                        "C6Mf+3d/U/67/mTwL8dma8UH+naNd+dAuABg9psf8De6dQ/VGQoAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val publicMangledGetter =
                fooClass.assertMethod("getPublicValueClassProperty-RVb1_dM", emptyList())
            assertThat(publicMangledGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicMangledGetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicMangledGetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val publicMangledSetter =
                fooClass.assertMethod("setPublicValueClassProperty-Vxmw0xk", listOf("int"))
            assertThat(publicMangledSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicMangledSetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicMangledSetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val protectedMangledGetter =
                fooClass.assertMethod("getProtectedValueClassProperty-RVb1_dM", emptyList())
            assertThat(protectedMangledGetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(protectedMangledGetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedMangledGetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val protectedMangledSetter =
                fooClass.assertMethod("setProtectedValueClassProperty-Vxmw0xk", listOf("int"))
            assertThat(protectedMangledSetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(protectedMangledSetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedMangledSetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val internalMangledGetter =
                fooClass.assertMethod("getInternalValueClassProperty-RVb1_dM", emptyList())
            assertThat(internalMangledGetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(internalMangledGetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            internalMangledGetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val internalMangledSetter =
                fooClass.assertMethod("setInternalValueClassProperty-Vxmw0xk", listOf("int"))
            assertThat(internalMangledSetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(internalMangledSetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            internalMangledSetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // Only the bytecode versions of the accessors are present, since in kotlin source the
            // property reference is expected to be used.
            assertThat(fooClass.methods()).hasSize(6)
        }
    }

    @Test
    fun `Test properties of value class types with JvmName`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline
                    value class IntValue(val intValue: Int)

                    class Foo {
                        var noJvmName = IntValue(0)
                        @get:JvmName("getJvmNameOnGet")
                        var jvmNameOnGet = IntValue(0)
                        @set:JvmName("setJvmNameOnSet")
                        var jvmNameOnSet = IntValue(0)
                        @get:JvmName("getJvmNameOnBoth")
                        @set:JvmName("setJvmNameOnBoth")
                        var jvmNameOnBoth = IntValue(0)
                    }
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WCTQUahseRmNcO8NYqolhLsPMNMpWZF8aYSxDpZkyMrZB" +
                        "MmRNlrrKdkd2oQWJ0WQ3U4hCJksSoUQhSbaSNVzd/5z/V+ev9zvPec93zvc+" +
                        "7/nec97nPHgLIA8EAAaDAQCAHGB7QAA8AEsTewPUYStTjKWB1WFTEzt7tKXp" +
                        "BgcA+GLZ/vSIBQrdLWiBQna2d1XY7n2xb2T8LBpnqXrYsjuguNJ2DofyQ+La" +
                        "21Uc5joxbW3to+PvxrkBeAteMFNcmam91UBzC/hftodugebqT8Oc8XLDHPah" +
                        "OThTA1zRLlRnf/9w+yE7WQJkcyib2UGJzH5mW3590ehVTBKi+XgJJB/vBTWy" +
                        "KbK5grHo5+NvQ1A0j8CCexoe8p6kLTRCd9NMI8v2CT3X3ES0zr/uQDgbtmTm" +
                        "rPnNrKx3shr8ZzbWZ2ER3LVxGOnk3PEQ1ipJ202L1M3YXyolFd2vvtRxopnG" +
                        "YfWWEkvGmkXI5iko5nSLCOe0IKh5RirjhhxIUShRy6j/VRnx0tumi+AKASDJ" +
                        "e8ir3yp48G6fikD6PtPqZwkDB+ANKnXVzYXEgWoZrADWGNafON2xlglRV6Db" +
                        "yu1t/9vTjFN5NdvuTCaAUuej/UqgJzOuEd0iTO7wrI2yOZxDkZpN/xDcip3U" +
                        "Qu9K3xieD5hcBvH1p0W9tS1MAsvcfcteqDPIyko4S3oEABHEVhCTtS7sgxZV" +
                        "3irPsEcmbWySiZLJTUiLRoSS/SunR5ByJAJbKgd33KmNZH4l9DBquXrNW5QW" +
                        "Ahupi9rRg7RqT0wYRtAXjuw+Jy2v7tvq+fcy1CY/LO64JBt259LBDLrObM1d" +
                        "vL1o2kVIQdZoSAKlrUQ9qjIlkKr3iJo3k5wzp5+sm5/22u1ZJ1HFOpgiYESK" +
                        "fjn7xPlS0B+h6kzyQdbQuqdPcIOnViWdmEWFMY7msrxDEANBAXrGSX+1yaF5" +
                        "8Sm65Q33xhO557zlNyYXRtgcMxaneTISB4Ne4aSCO/iybu0Xft6eWsGEmloL" +
                        "U4JyUUIFN6OmdGAH1rAyDk3y5Qz+i4vz0iZFIUEp4wNXHYerrcQNnSTJ+iFX" +
                        "E1aqDQzBWGON7jJFE0/UgDNBSVyXYWzy6ZNsi2sxLZytfxP1Zlo3CbnHKca5" +
                        "SB+p4+1Qw2jZ519Cbnveh6MUuGtQWcLxwKncY4NO2KlGEpGpLqs4VYbSnKwq" +
                        "HB0glLjf18g5RLuW5RaiQUhFdt55ohaC21B3V+SHGqSomgyO59/bb4Y4Hx1i" +
                        "zp/Y5RG/2iJSP8dVDxSrvOn55JzohWFF3+C083xlfvEWd+jwD6uiJPEB+And" +
                        "puyGZfp9BY8H4xMPx7z4zX1A0kpq0eKvPzYr5TxcuMTdnhDYt8rnA1sEyOfs" +
                        "scvxWuRpWyKzDT5c5skgsHOoGuGfMXOzIny7rHKjYG9ztMO/hbIEaxYGr2NB" +
                        "uzd5o1EcUTUxVXOjuzN0e7XItSDMnvQAOskv89kYH+W+dNXI/B446/PbqKdQ" +
                        "6MMJYO0Bv3jW5bLhGxuDNYEodNWjglXLh7H+9fD3vfqyd5eKm5xQ8OfLAP7I" +
                        "JRQ5zW0+fUR2FSsZOiXRmiyM4zQ4+NYNEltWaQ4ySXPOXetiq/3CsRcjTLVe" +
                        "SBw0JGOuvURGtka+4AsbG04aZc1uSGt4vLnRK54lGIX2lT894gLcAVQJwlkw" +
                        "ZJZ58nyiWEcfrSIuEd3RuazYldEHcYy42LiiuLX68d11KtB96MITp0+c+JIA" +
                        "RIM2gd/VR/pkzANtHgBggfd36iO2XX1MfX3/IzypBKK1rBlEt4L0auqrDHNl" +
                        "f61cZDLP6wuC6zjxrMo7kEq6kYLtsdSgIskKef5Fn7FjdrttYKt6gxP1xp69" +
                        "RcISN33XD4YzQxaB7/oLbWA6596/8e+6FupGWX+p17DJNQLXgezlNgNVpZvx" +
                        "VrXW510U2Ss87XK/r7ouerCizui2xGm62BMqO1dWR8QcxdwvmE/vviGvgmcr" +
                        "3Htw3aMm1EpSu7wyzBo0RFKZu1/iM+HqpE+sdlAFBksdoEF4zuZujtVWeJ/b" +
                        "ydVoLkoR+gNnHyC1lhfsZHXqlLzQkUXe3L8iJ7X2wvG1Ajqfo6EgyuiTnTWC" +
                        "pFrbU4zuk5PhkW3AP/vrRe6xe147bQZuetjnNV//JKxhfWC41zrxnVgW94Dl" +
                        "JUdYuaxJss5kD6e9hQ6rJDJNDaoUHg/ZY5D+pvUJEUa6PV2P371RQnJq4FQf" +
                        "PQ1GOlp7DElUCv7Is1i/qz/lBi3RTidEVIh1BuDUNZQqrnmiV/vVSejy2iea" +
                        "rSPnyt0Mt9vsm8rzhippXVaaKeyglEObyrcyOJ1Zmbl2Hz+athy5uDjTB3Ua" +
                        "vra5d3HCyy+QYyBCSTbOs6uKzyTaq3e0T6tmrkiFSbyIvb7DcDbDZf2bYwX0" +
                        "FuSqchxFOYa0Y2fSBXHKzLdQRebtqi+wiamoAlRMgW/sPTnxhBsHO/rGMe+9" +
                        "zHS4v6pE5E0co9bmVgxNfdIeYk7XaeiOCja41mQHvhR8ebmivOcLd83o0Tdl" +
                        "KkR2/YKO3j1gepKxfsTMEhs1USZr6Cbu7g+ODSC5p97Of/659ExhjDW8vOdy" +
                        "TgI1kTB3+ey3+BLJdVUBzZIGg5JCkssnx/g/A5Fl448dSCb7l7Q66YB8Gw9B" +
                        "94yxknDhHard5/JE4YiCWRlvvi+1udSj/L6EDKf1HaN6J8Uo8m6FGO3wzDAg" +
                        "/+HPujdTRRxL8e9L6AM9WPk2Ibc4Rrbj7lm7GDUFsic+RsPwMSIiOCRt+b5y" +
                        "m31TaXdxIYjgMYsPCD3txYkuXqCXHisEux41DsbHbOTJg4qdEPOcJyQCiCDx" +
                        "ZqhEopxg9pQhwFH9BsskNB1KAs+845rmGoLhm3OI+aqM40crlOo7lftQ1JFH" +
                        "RHBbRHXSuRW+VvXL1gpqYnFiebh226LHAeU4VfPzathGl7RaRSzKL9uS/q2f" +
                        "THrX0vB9VVP0cSNeQAAA/ttVld3Cf32Kt7OHD9rLl0b18Dnp7Xs6gOrqcurU" +
                        "KcoWeMhWICU8+RkZ8K8J+Sr/oE58q1LqXxPCxQ0B/I99u0H57oJ+jF95op9Z" +
                        "tgsN9AeGiF9bm59Jto9A7AeSdZ7/p1A/12//puwP9UXg344Nb7ED9P0Zz9aB" +
                        "cG1lvu+3fwAeDUEmLgoAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val noJvmNameGetter = fooClass.assertMethod("getNoJvmName-RVb1_dM", emptyList())
            assertThat(noJvmNameGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val noJvmNameSetter = fooClass.assertMethod("setNoJvmName-Vxmw0xk", listOf("int"))
            assertThat(noJvmNameSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnGetGetter = fooClass.assertMethod("getJvmNameOnGet", emptyList())
            assertThat(jvmNameOnGetGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
            assertThat(jvmNameOnGetGetter.property).isNotNull()
            val jvmNameOnGetSetter = fooClass.assertMethod("setJvmNameOnGet-Vxmw0xk", listOf("int"))
            assertThat(jvmNameOnGetSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnSetGetter = fooClass.assertMethod("getJvmNameOnSet-RVb1_dM", emptyList())
            assertThat(jvmNameOnSetGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val jvmNameOnSetSetter = fooClass.assertMethod("setJvmNameOnSet", listOf("int"))
            assertThat(jvmNameOnSetSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
            assertThat(jvmNameOnSetSetter.property).isNotNull()

            val jvmNameOnBothGetter = fooClass.assertMethod("getJvmNameOnBoth", emptyList())
            assertThat(jvmNameOnBothGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
            assertThat(jvmNameOnBothGetter.property).isNotNull()
            val jvmNameOnBothSetter = fooClass.assertMethod("setJvmNameOnBoth", listOf("int"))
            assertThat(jvmNameOnBothSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
            assertThat(jvmNameOnBothSetter.property).isNotNull()
        }
    }

    @Test
    fun `Test extension properties of value class types with JvmName`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline
                    value class IntValue(val intValue: Int)

                    class Foo {
                        var IntValue.noJvmName
                            get() = 0
                            set(v) = Unit
                        @get:JvmName("getJvmNameOnGet")
                        var IntValue.jvmNameOnGet
                            get() = 0
                            set(v) = Unit
                        @set:JvmName("setJvmNameOnSet")
                        var IntValue.jvmNameOnSet
                            get() = 0
                            set(v) = Unit
                        @get:JvmName("getJvmNameOnBoth")
                        @set:JvmName("setJvmNameOnBoth")
                        var IntValue.jvmNameOnBoth
                            get() = 0
                            set(v) = Unit
                    }
                    """
                )
            ),
            // Compiled from the Kotlin source file above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/32WezjTfR/Hf7GGnBmzFCFL2JbzIaoRYU2b063YipjYWGWE" +
                        "qJDduYUmzSmK5JFjJBo5P85zLDmTUKQcaol1W7d6rut51PXU53u9//t+3t/r" +
                        "+7muz/t64TD8IAggKCgIAIASsLkgAAjAWjigEda2ligs2tba0sLeAYm15LUD" +
                        "wEcsu+MoBoHsFcUg1LvY3Y/s9j3XeTlzHmmD1bTG9gbkldkt2SDOqduw2RpO" +
                        "S12otjb21MyrGT4AhxEQLJbeW2y08YDBhnC/fB66IZqnPw11luyFsvajOblR" +
                        "AjyRpylu/v5hDmP28o6Qr2NpxZ2k8LQeu9K7K+YjV+PhTSfyIdk4MtQcn4v/" +
                        "C4UZFBJug5MMjioGP6utEzhJ49RDFWiW4SU6Yn0GX+Ety6OdcDez5pT0L+cW" +
                        "1ta7WLX+C7z1RcXLfE9iULCEjJkQFpdo5GVI7C3QLZSTixzU+9zp2kRrZ/UX" +
                        "EvKnmyTcrRIRxe+bJdo9RMFNC3LJmUpgVbE4Q/PBkRICfbIhSvCRCD/Rd4w8" +
                        "aBs8XPRCQyRJx7K8J3bIWKVWo7q8KYcwVL5dS0TrsOJg3PvOLykQvd0MO6V9" +
                        "7Bs+R9rLbqbZn00BSNV+RiMiz1Ji6pHN4u6dPk8i8NbpJLnFpNngFq05Q+TO" +
                        "JN7EcsDcKlhokBkxaZcTL7i9aLKSU41OTY09T2wEwI5Sa/C5J6cr92Me+2r0" +
                        "aB2dw+MTCLIJDeqYeriaw4hLI6RUHa5VqKTyxw4j9eJPjs8Knmzpt2pW4wTW" +
                        "U1aMIodp5T6oUJQoVUW99wJMWY/a4nNjFYrPDo05IVup+IC+P5lhslhRhHOQ" +
                        "ZEZB7qdOhcSS2vL1IsoSAykHGyl3FhLSlw4lmGYzR716uggax4JJIubEyIHF" +
                        "Vjd60LaLesXu+1lj6z5+wbU+hmUMQipFscA5g+UbAh8KCjh4OP7PNiWkAC7R" +
                        "tLT24Uwc35KvMm+O87Ky/QirvWku3EYR+lf7LcFOodR7uuJ97FuPiqGWx8RJ" +
                        "QRkIsftZEfMmisZftLY7NSiXFghHrSzDLHJDghJnhm7+MVFuK23mIut+KORm" +
                        "7Fo52kxQ67B+b4mqhQ9iyM1RTdq04LDFu3fyzZ55tLDKQ1mI8fem8eq7XK66" +
                        "5R5SN/F1qiho1vHPd2/re2FDun9Gn8ISv84/n3F82EVrvp5IKNaTV50vQRjM" +
                        "Pc6ZGnLMP1Oln36AdjvVK0Tf8ZZ614NW7RAbnt4ZVWEoOlHTYngm+6HuEfil" +
                        "yBAr4bhu7+vcZomapS01/FJlWT6tFySvTKhSg5mXhErOXcc8YKjMciWJ0kMq" +
                        "rqYNabWrjKrd3k9n3tRNk4Wt/MAwNe1I6dG3TWrpdRw6Hzs28AVXyE9xBVBO" +
                        "32WfTl4BtX12r0TPRoOSHSvTKfphH1BLixJCO20zIhQn043C/r7IEq3gDN/V" +
                        "Ait8FYhEtEtqS2lamRctMBy0w78EoXYlBTCI51J6poVIVbDHL5d3qbA+TEZ0" +
                        "QKF1b/ifGJ+7zooumcjkDVcEIpCPG+9zsXXX/GtUXvcfki/6nNfgglDpWwWE" +
                        "wz8j3Jley0kv5blashfnZVoSxG3aa52o1cOEZi7NaXv8klv3uhR3UPxa1GVL" +
                        "w+cy+83cUbcH1MNbwp8LhU5PxE+xFnkwfe/xzH7pVNEIJFXZ4+Vp/q38GkE2" +
                        "mILtq6A7fhEs50YunE44g8xgXVubehpTEHMtJjfmS82MQrUGVAeZ4+rh6vox" +
                        "lh8J/sr/LX1gJ68+NQIBAEfgd+kjtTl9LKnU/wRPrKPLMWm0nKlONbXiPZPs" +
                        "gpPEC29zkYiObhbefUNPRmpfrNAxYUo7M3BgH2aGsefyte6aVY9XkUomXJW/" +
                        "9zZTy4U5ZuHw9PWVNP+u22uvSy/z1rmipyS7YQ7dUhm8tbt5xZV+ItfR+SlY" +
                        "++I0W8eJwQfHq4giSvRZjZGS6gGRzFc0CwsQfqvbv+UaxmsQcn0zgZlJIuaO" +
                        "HS4XJgzk50mNqYU7DGoS48QFibo5onR2YjbW6A0AthxS6u06nlegqU9WpVv1" +
                        "7svRAHlLMTz46+iPSMGH1RWfEMcCOSceZQoQdC8NLnsKnODvN1a7l2U/fT63" +
                        "N3AN8Za1bxxRphqlnSVt20+GOgdWh9HrkSSN/GiFUyuhcEXN7lEd05s59fKd" +
                        "EoUyN7Fm+neNb+y8ROkwKGV/fg3jZWRAIBlT2wZsj0EUktCujEx2kz3r2c2t" +
                        "RPcOxiMqvmi5M0t275JdED0pSxiP2D0FJVMHDqSHYaXNEFentBAHYHF7dKMe" +
                        "tmk8zPd17m/JCaVUClQNZ6Ytl6TGdxLDfQd2LpVEpip7seqmR+0Jnjuejl6g" +
                        "TzmyuQzX0Mf+1f1djpgy36ppI/LClZAGjQUFVR3PkZIX8dv3v5g84v8vZAV+" +
                        "aExvXuZjD3MRfZ7ysXX5Hd3TEOsymBjyxrDIK6R8vicaVz56r5FA7mf2hAKF" +
                        "HEBL3HaPNU9K96Cx8/O3nJyOWzleHq/3vDqo9lINL++rGg3ORd++YtatmXDJ" +
                        "MiFptk11D6c6BvYxRaVw/Mree5C+53cswCfuLtN5vPF5BZMFzNEyY9Ru511b" +
                        "TsKx1zXBrRK19bjS+Ey3k771R3qMzYniu5NVZ2PF+g+I/XlxR9x4lkfKATF/" +
                        "dNsHj2snqVbzp9UGPHPJlcE2UD98Y+YBsZaiPLfwwy3UcpPRlg+Dn5gtez+x" +
                        "k1LvoURw9zk7h+9dqJDUPttddzX8S3K2hLjI0clJ/eQ6FZNxJsXirpqETCu3" +
                        "vqxOBCaLs3buSY/eUyYAsgRlbKtsZkCTZay2efC+L09nP5a5zgcA/eDfLY/8" +
                        "hv5LDr5u3n5IMpVG8fY76Uv1CKB4nj516hRpQyB3W7Aazr3HHfiOBZ+Un1ZL" +
                        "b3TKfceCLXwQ4H/um5HhG5f8WL+ilJ9dNq8+9AeHy7+GjZ9NNo9A6geTddD/" +
                        "y4yf+zd/U/6HfmPB344Nh9kK/nYNtHEgWwCA+c0P+Af7Di5EwAkAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val noJvmNameGetter = fooClass.assertMethod("getNoJvmName-Vxmw0xk", listOf("int"))
            assertThat(noJvmNameGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val noJvmNameSetter =
                fooClass.assertMethod("setNoJvmName-6VC4vj0", listOf("int", "int"))
            assertThat(noJvmNameSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnGetGetter = fooClass.assertMethod("getJvmNameOnGet", listOf("int"))
            assertThat(jvmNameOnGetGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
            val jvmNameOnGetSetter =
                fooClass.assertMethod("setJvmNameOnGet-6VC4vj0", listOf("int", "int"))
            assertThat(jvmNameOnGetSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnSetGetter = fooClass.assertMethod("getJvmNameOnSet-Vxmw0xk", listOf("int"))
            assertThat(jvmNameOnSetGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val jvmNameOnSetSetter = fooClass.assertMethod("setJvmNameOnSet", listOf("int", "int"))
            assertThat(jvmNameOnSetSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)

            val jvmNameOnBothGetter = fooClass.assertMethod("getJvmNameOnBoth", listOf("int"))
            assertThat(jvmNameOnBothGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
            val jvmNameOnBothSetter =
                fooClass.assertMethod("setJvmNameOnBoth", listOf("int", "int"))
            assertThat(jvmNameOnBothSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)
        }
    }

    @Test
    fun `Test multi file class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    "test/pkg/IntValue.kt",
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val v: Int)
                    """
                ),
                kotlin(
                    "test/pkg/FooA.kt",
                    """
                    @file:JvmName("Foo")
                    @file:JvmMultifileClass
                    package test.pkg
                    fun fooA(iv: IntValue) = Unit
                    """
                ),
                kotlin(
                    "test/pkg/FooB.kt",
                    """
                    @file:JvmName("Foo")
                    @file:JvmMultifileClass
                    package test.pkg
                    fun fooB(iv: IntValue) = Unit
                    """
                ),
            ),
            // Compiled from the source above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/+2WeTTUex/Hxz5Xg8HYlzBTyTBEkp0YZaaxDNkymEjIPkN2" +
                        "KSl0SYXKkkRlD0OJLNli5iG7QZohBsmSKMp16Z7zPHieuuf+/3x/5/M753fO" +
                        "7/v+/n6f1znf78sMzcIKAQCBQAAAIA3YPiAAVgAGaamvYGxipIjRNzE2QlpY" +
                        "IjBGf7QDAEsYCvkkWgHRxYVWkOugdJZjlXpVaBP+CBRG3hjTFZBPwi6gFPzk" +
                        "UBQK3GqhQ7GtjTI+MTbBDDBDcwBL+A+WqG8uoLZZZj9dnm+ziGcJREXf8+cU" +
                        "jXx8EM6eeALhd1OyCbM+uJBecYlsEXaRR6u9QR42qhUV22bU23Tnpa8Uy3WX" +
                        "wVbhQL0IctoijeOreYukUpchj0N+wfkX+aGjug7vAOn0Ow39Mv0dD3X9EMll" +
                        "9cIoi1qaSf3DWIJmelxS5Y2cKo5cjcejymEeCuExvbHDn4Xzdd9eZtJnxE0q" +
                        "iLm7+A/qpUhLC4EouOPEOmhEaHXL6adJaqMyKNBEZglRCsqwc9OZvHffwuH5" +
                        "ie5lil4/QHZUDYZMRLvbxA8+x5xzeFLqJLviAvOepJtR5laVpHK7LaCSQWH7" +
                        "MRv7+ucmk1IeLLjIG08ZJnXbowWa3aBRt6nICled42ieJwSKf/702h7/zgKG" +
                        "wSsKyuWoDGmAZRDGrszYOJy64ezqBj6SZzV1r0jnJiIfUYmbdRpolvRAzAzO" +
                        "Q3kb48y/sm61Prplv6YmEwAwzfSr1ovuar2j4+ZNH038CwHGFOPTowcOL188" +
                        "7sEcoHsRpkcHHmt+NNbCJJCDEmSC6dW+N/O6mTs0VFowu/+98KIpba+vgbgZ" +
                        "xPu7bFNhPPhquMrSKKWjxrGubgOwwPoF+M1KJM0ecq7NGHc/U1m8jiR9DXyf" +
                        "fOUkyjZjn48Rw4PiGf9mtV7i6qr/2iGcIOt8xIrUAeBLKTOjQRFIhUJAcg3W" +
                        "yiQn2dQ2HWFXkyDuqQ74IohYtLX3SM1Nx0jyZxZZ+5EckDKCmKm4EyRknViJ" +
                        "dXhStzl4T0LR/XduL4fCfqupFPqWZipz/fvK5JI+ai4NqkPGGXt3jdLuNxSW" +
                        "Vckz2jNIhz0nIj9EvwmOtLTJjDA3heBDs+DLIToSedRQdbm7VIleZ9tJ/0e4" +
                        "9dTro31LVJsMN+vPox3wgsdyA0blIfanKpKtBpJjLYBBz0nvLideHFcoljwC" +
                        "qisBMT8TuRyz4Hu1lHBQxrC6i0d0dhzRyqD4+F04bAhvTAZlgKz9FBqPjIE0" +
                        "ByUugVae6ho6+b6lxei7+fbUq2n1q06tM20xFciCfbq+yRPE/I+ZHvt7pub/" +
                        "Z/pfTNdq8j3gRcVw/sfgBV/3Oo69TK2n7bU9hSMcRVi9Vdi56HRuZwbW8Frg" +
                        "w72HB1txCa3/mGla5MTy3zMV2s7U2JtohfcMOPsX0HDLtxZipyAbb9NK/uUa" +
                        "lfYGW5a1YjAcnbi/2a4AkpNVEf/g1BPz9h6+wFfHymKpmEeJNdOOtMuKGmu0" +
                        "PTwaFlGlKtxjaZG3xoPmXG8pSY+f9Nft0GZc6FjNIKzMR0ZusNTjPA/BtMnL" +
                        "/svr/h2+Ndm27naHlNhLz1VS7JuJ7VWPh0Ot2uKlm8CI0w5J0rfbREGFyEgD" +
                        "AxBKXNwvZCXQKxVP8mUiZOJfF3gFcQ0f7BzqC7lC9295LRjiMOSTcyHXsXKE" +
                        "BNe0gsbwxkyxDJe+ROpGZ8fAsHKsRiJ8EqZ3+y9dIue4939IKW9USquamsge" +
                        "r2+riYELp95xj2nT5820Tr8RdwvrN7Eh4Jga56MkK8EtjDjL7lJCFy1GvxUz" +
                        "fESVZaiC8Db7An34oqofVVdjh0uZmRshksnSVANp7sIyF484+wCSKkWq4Ixo" +
                        "uajyp6YkaG1hyT3XZq6sFmWjB3W0JSoD5NFDLX65UHG45vcnpFoyOC+42uqy" +
                        "dEBX3B0fAzInD3OfLweBj7EeyIkUwNvqm+erFw+4pdTYQYHo6a8rUAq3G5zK" +
                        "zAMbNGD/aI8KjjvkLKzM5lAHTVV/JTs7UBP38f2KXd+IR4HHUWJS0d1ZrULb" +
                        "zCrnkP1tQRUC2SJ4/1gyW+/Hb7BIPFlMnabJ+r1nuiEgOZGQHN/DkhxhLytn" +
                        "LUM64JbYrXNS07qj1kFUV/vOeHCl1s04zuKlsKnZVjeb3+Jf5yMgL1oZkAcz" +
                        "dL+Uc4kba5Xr98BD+WADbJoLJZXr1hUwFMaPs8Xm9N/CWV6R6WpEWTZ11OLt" +
                        "Y1DT4yO3R0a/XAunSoKgbGfoxuUvvFQz7w4LoFMqrJThyHI8OVRKfGiB0v3H" +
                        "PVueuRmjIqqsbaJnsHGJmkRnsR1yuGJ6+cF4r1ZPeV62pZcJW8qoxScL/QO2" +
                        "JGA2bDXdW91Y/mNSXqoZz3S9eY7qzB712gWuMDbYlGHfDRVe4Tq06CeFdc4z" +
                        "S8KpWOfjb5ahNfvLjj8PiVaLXNT+otcdRE6MajPeo+fNziurfIl/ZOaZ+YsE" +
                        "9wjmNLGMJ8v8IzqzKteIGRRizyzh5vyzafUHi0DPTGxgGfXztG5DnQGMeDSc" +
                        "MyL6guDntvkJ1ZHJD2g7Z9pipNJIjDhU+i4b/nWPdjaK3XfvPButDIGFI1ju" +
                        "6TSEKWZXBmtdO5EZRmiYILFNxkdVaQRcv5IX+y7rD42ngV7yFfG5a5h6IKEU" +
                        "Pdl3Uaz4S/mrYQVo91cAOmpFYcywttkxymem1WauxFpLCdWuaBX5UgPXwk60" +
                        "EoV/fapYF62zyHKGfp+Xe/l1ApiKZlShaA8vzrBKrq76ea7NdC6kr+A0wk9+" +
                        "c1ZWrQQrP/Wg8bOwFXbxmoLylx+fdkLoNK4JGuHcEEfsm74HdCH7kU3I3uZw" +
                        "R//Eyfbm9HgRIRGhgTD6jXj6xg8ts5zmFVZkBQCGOX6154ht1r+t0Avv7o04" +
                        "70P0dPd29PJxCfA86+zk5OS6WaxnTNhlad0TY7LGm5YIf20iNBZI7kxM5OBL" +
                        "GN0HTLh1IRbCD33Ip3KjqCmrGfBj/T7FsZItJTz+QwuZmCGA/3zBdmXc8tKd" +
                        "42eWujtlu/3w7UiI/J+yuXv+9pNWdMf8NaZfGdPunO27+86cDpZfndK7c7YT" +
                        "E9qRk83205Nhd8j2tovtCBHk/CVqMzQb+9Zr7JtXzubfpHBuPf0JTn2S0FAM" +
                        "AAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val mangledMethodA = fooClass.assertMethod("fooA-Vxmw0xk", listOf("int"))
            assertThat(mangledMethodA.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val mangledMethodB = fooClass.assertMethod("fooB-Vxmw0xk", listOf("int"))
            assertThat(mangledMethodB.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val nonMangledMethodA = fooClass.assertMethod("fooA", listOf("test.pkg.IntValue"))
            assertThat(nonMangledMethodA.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            nonMangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val nonMangledMethodB = fooClass.assertMethod("fooB", listOf("test.pkg.IntValue"))
            assertThat(nonMangledMethodB.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            nonMangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            assertThat(fooClass.methods()).hasSize(4)
        }
    }

    @Test
    fun `Test hidden deprecation level with value class type`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val v: Int)
                    class Foo {
                        @Deprecated(level = DeprecationLevel.HIDDEN, message = "")
                        fun foo(iv: IntValue) = Unit
                    }
                    """
                )
            ),
            // Compiled from the source above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VezgTbBsf5pDzYXKuly3mtDm8kjNpK5uJkUwOjQgv5rAp" +
                        "osWLq/hKkrN8bXmlkuMoWc7KGEL0zlTTSNMqEjnmVd91fZ+6vrqf6/fHc13P" +
                        "/buv577u+/dzRQsBQQAxMTEAAKAJ2B4gABCAQXg4GDq5IOEYBxcnJMLdA4ZB" +
                        "fukFABYwzD5ntCFsSAptqDfAHKzHGj015UzHwFAYAyfMEOk2DTuHMozWQzGZ" +
                        "+p5zA3AGg8mdfjUtCHBFi4pVK+hWW2wVMN+C60/LK22BGBRLhEf9cRLuFEn0" +
                        "xIeTgmCB4fjY2CSP5+5qR0Cbz4ur+4OTi59g664vObJTs7S7ve+Ayq43ZFKO" +
                        "VLj1jsjHdeyvu8DClGfRef6cP+GWqxwJGUv35FpT6VfF5Bzu6ffBOUaaXOcY" +
                        "uwGbmVMDK9dilz6QyZtCbb7hxhCbvsWYxY2YgSg6FRfqbWwkUnuykenTText" +
                        "usk+48nI1OyShR3zy9a8ylCVrESQHR0lUerq0QlLcRH5eFqUQGwpvudOxGkp" +
                        "tu7g+GhC2mTMo56dCX7jhLJTf/k3TtD0rTzB6XLpb4TYtQ8RdqnUdAhWD4hU" +
                        "kdc4XDiWktJXFjr2Nq++06i46c00ldvGoKfrK+cXhKYzHORKj5ZczsjBRk9v" +
                        "KvrnZxCMoBrSyrAgkRPVk6pV6OdqB8pZ0BkzSbzXnjiCfHJzeXMzll0rKNgJ" +
                        "2pWryXLUlK6sOxGW4UOimTF/uxOgWq9q8rErG9xSWV0U3C11/ZEJktLKWWDN" +
                        "SIaNsKoezjX8Tr9YQWvpk70V3+z5pyZpKKOA4NgnLiM4GiUaKz+zESeOUMTj" +
                        "HNxuW1Q9C8mje4PF0LzlJTBTOkSfJSgD+dtR5J0PKj7DOFDZRNivFZxv0QHl" +
                        "P6NnvJta8h6dCLsTto+YfbeQb12JK20KTNBmnG5QpKrgYy70CT99twYh4/vU" +
                        "LDhWwPURXjspNys2N3NEKPesD1TvqBZNJyRr2NbZ6uhAi5+qnU0BN77R+kqG" +
                        "eNVC4hv+4xCvHZk9t2GgB49nQJTZyei8k1mbq40bRbLjt2UdscUnmPlSOWmy" +
                        "YIiCLw5bNpbj65GmNdSJ8ugaaMH7pKN43ImrEy8+n09i7ZIECwdMOtU/iDAr" +
                        "LWQrovMaPE30EfX4vjO/qY/PMYe/FOFk3s8i77KguKzweKdqc43BKm8Eu4G3" +
                        "SOE+tR6pv0X1iHARznvh/tHdQQdHE6NCVkoiLZwM3mXfyneV4bW5lZnNSli0" +
                        "zEklCkPeHBi9bCqn3IpW/Wi4IR6woJyPDTz4ZBFM1647eD8h1Zw8b/PZfvh0" +
                        "X1Yyw0nCPlJEDmqSojAxe8/twaXQs4LFatcqFhUmbPmm54nXmMQRfuyVD/d4" +
                        "FpR5sfBSbFwd6xPPrr3VEULclyR+NvXUzk+MD9NmE6/for0DOfNko4l0dbBm" +
                        "oTC+Z8SGihKJ2v1BmFMHw+rDhIps2xPh1MZ46/OHShNj26dpwq8zk5ssSf9K" +
                        "u3Xh5fUvljVxEQYNmX+tYtrEYmvRr0fPqVV9ru9gG4KHlwHo5CXDVwdauv2T" +
                        "CbOPvd5XH7U2QvXCPckPLX0fiRA9VfWXa+CtqbbzQgGT/5aTXuy5JMtCzzSh" +
                        "ODfOzQJ3raxEh6/ODs6VLPlaJjmvBZqYNcqa1IRxFISEK4fkDkveXrx57DjM" +
                        "tnN1J9I3BLbXp2udNIQYQ3QhnnYn+cdkve7tLslUUVJRepY4eTlzcvOb5njw" +
                        "5JThQACALforzZHfrjlIAuE/cuPrhiGM2MsmNV+ym00dC71OE+g1hxzi/J6i" +
                        "ILYD6nZxfzYAt9+miYFtjObrNe+wQa4rLIuW2Ku7giLXoV2VaAHSnsGW+IVg" +
                        "+trSTDXhpcAEIodCTRuebD8O5081STyS5evkQJxzwuGJtc3whPG0bo715cKn" +
                        "MiqVSNOqtnTuDWA+peylU3fN3xvkJ0npH9nWrRq7pV/ekxYtCzvQX0AZ05iy" +
                        "zMXd15VGUvNDhnRuWYFQe86kCEjG6WlrBXlZ6ebGj5ntCYtqpPb332RL+rgo" +
                        "e3W52njaKVnydCw1FXe7GDzQNsW3A4sG3hisuxZVIipa2+eC995dS8xdsy7A" +
                        "JL1XaUsSkpqZH2XR+o/xa202p1yncTY1s/QjYVbJmOb66S/LvcHuennW5nOv" +
                        "d99/y36xzk3ILNxNYxjHdPhsVDu4lXMhGhLcYeMW0ydTwzn0wzADUpB24+NO" +
                        "X+OEa/zyLoNTC35XHjEOFa3L8DZNs/kJFgM09QwvPafRg7i8tx2mCWqbR2x3" +
                        "LR44WC6oxvkUGRwduFc946iuxEULGvteS9uaz9VqN6L4cAcpIxcssJMj5KAY" +
                        "FFBJXfGj6JAP7x1HcHvd5itq7c1Kyk/f6IkcvufM0sUa6hloylbIdw5By311" +
                        "QRrYEkq8vlWNVsnFGlW8FClln9KkNYx/TreS+0p6Jyix6AYH6qNnj5ms24+j" +
                        "fFT7Okz18RsrUoIAwJrgr4ZJbQv/9c8IfGgk7A8CMTw00j+CcIIUHhR4/Pjx" +
                        "4C0AA1xEoK4BTwIA3wZ1UYv+UGErU/mbOQoIggD/Y99unF/d+fv4mVf/yLJ9" +
                        "FZS+YyD/3HJ/JNneAvnvSBaA/2+Hfszf/k217/LtRX/ZNle0sMjXZ8CtAxIA" +
                        "AEpEv97+AaE81ZDGCAAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val mangledMethod = fooClass.assertMethod("foo-Vxmw0xk", listOf("int"))
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }
            assertThat(mangledMethod.modifiers.isDeprecated()).isTrue()

            // No non-mangled method since the function is hidden
            assertThat(fooClass.methods()).hasSize(1)
        }
    }

    @Test
    fun `Test propagating PublishedApi from properties to accessors`() {
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
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTgU6hofW0aNfZkxJabsMTOUY4uyz9gJyXIZY2lixjJj" +
                        "jeyyX51cWyEnlLEdKhzrVCrLIMwgocmaPUuGujjqPs+96rn1fs/vv+/9vc/3" +
                        "Pt/7+72WJmzsQgAgEAgAAGCAwyEEYAeYGdjoKKLNDRFmOuZoQ4NLNnAzw70u" +
                        "AGDDjNptaqII7+c2UZTvpfY9skbSzjJmAuDGZgpos/5A8mPrj8aK/vLGVOoZ" +
                        "u4+9iM5O6tTM5AwrwNKEE1gtKFetflBA9QCWPy0PPgDJg0hC+Hl7IdAEkh3G" +
                        "J9ADjvXBEIkRNj3EtzZ8+y1ziCt2J97gBu25XvNeXQ6nEWRLMqSL0XK2flBU" +
                        "Mowb/KJfzdjnjX3tg1djDASDwnGExykgShSlTErgmk9rS8KMyHQGvVxD3g9d" +
                        "XW4df7eyF1S4t8f85wWWsRfZILlh0lg4tu33YXy9u42jJyhVZ/zsqGHrtZF/" +
                        "/Xt0q+BKEBaohBIerMcAPYgaesXLJqaX+WKFOI3uwpZWHHieo9ZYt048z3B8" +
                        "53cnyzm35lqa/POZioIzW3/sBLsHl7jU02rJmp5nEzAJH9i668Y4EGm/xwgN" +
                        "KNukIkHBpzxaqLNSTtLe9/uRy2IJuUsvUOBYzuhZIaenOZQepCmoa735fQPy" +
                        "Ll9C977vdiMyPLZh6SPFT04zioVj2M95ZEJCN82z5J4rEJ0LaUVLrkB7VwIi" +
                        "XoFQoEthtHQb3vgQ5JmYSvRReo0dMu1uqhxVtiNZ0KgAhrlBVyabA1Ty6Cm7" +
                        "PYXdWl4ZjltBu/FeJQy8fjzTGRrd2OT9m+gAuCUxVNyBFMr3MEYzR1ht9YtU" +
                        "pnucAZgeMB405lm+NHRHx8Fapi4bQ6BcTVIKkaCcHDhfJ5pJ9BCW7auoSlEO" +
                        "mUq39+R60OASXJ6V/857eP3Mch7htoThOfpf9irO2Fd+3ANrDpr6vglJUnAR" +
                        "y8yIgbY/4/RZtnlY9klhfs6zWKfZhfloU/HLUrkm/KOCj41wFgO9JY+qwa/M" +
                        "eT1DoxW5H9yXHw6Bkbaw5xTi6R3JpcaSr7ykbIeeYSsoBc2fh9rMHnYlk3m4" +
                        "vAeULLIleW3FpPNH6SW0zTByiryVtIN9MrnmkS8EXGa9Y3PxD72J5YhsDQnD" +
                        "uALLW/Yho2T8nR6vl0F0ocrK7LRrnVcddHky9Y5XWU24qECjFXxpjj21OyMC" +
                        "up4tHk12PQpX0g3qefFNK9AqA+eSdOK12sQCkTaQRtJLAz4ps6TiZomsrY5p" +
                        "3wzeecqaeqWVReda/BxDVEJHZiEhBBy90eQOW1FGFBFOTlmHbQLzguKGaWP8" +
                        "VSff97g0d2hlgCbWA1wnn3BIqMaCJG895SJ8+oIap2wmsBYez694IjimvURM" +
                        "JMWaBncvHbP+wJznKJIR2K6wCnQbeVzWRmnTkySpBR69ERc8+yRpNR6ytTNa" +
                        "rHLk5D4nWjwgM3NapBhmzkyUv0WJaAZHpbQCyxtQVxfYiS2p4cyQjFtqoSvy" +
                        "YATczwU4NtOJpVqk7/PsDEe3bNRMw7OWuFlOU+PhuZfZCA1DfUeZRoY9DDaF" +
                        "I0P1iYL+4Eiu1jIxbFmbi7/CdXO5teGF1cteXSNRnV0Mfu2lckRR2n6U/bZX" +
                        "nJ5W7Wn9rcnQKoGQE5b5FKN/RJzo09ro+9PgCzy/4HXV7cUuEV0N7SErWk3y" +
                        "A1J7XrrFx3mRnNhAHx9Me/QkIzc2px6u1qGeqJao0bCKW+lvCkAuuo7qDD1V" +
                        "sVCfltgR/6o+F9O9stXZAYBFzl+pj8Bh9TH09f2P8HhYv7MYtIRoMeveNhJS" +
                        "CRSInhWfsDQ2dts1M/EhDKdMu3fFNAQ5bldU4Z09rjnt/7nwwyOEJDSJ1zPX" +
                        "d8/qZfW4xOmt91/8P+cTLdreXoiM3GP1Ez8OQuOOXbmmymvS1CrOhkWmVwqW" +
                        "TztMYOY9lkequiFCCR+yptTXlWJrax3MTsU85BFWStgsiZrvJM8ELUMmi62Z" +
                        "WjDN8cZNTYMH9CBRmjCRbnoquphHTFDy0wRTFIK5jZxkoAYzzgew65GVVxb5" +
                        "WW7WFZMbXCrxzd5L8dqmMS/4qgrqtDMt7jzXI4NqFMOsxqTbdsrbPbo3wnHo" +
                        "m0tKVJu8VpJY9SbZkSjm764aNMqTn9VKYfrgVBRw/C8dZY7i0zhgJ9kzgjVu" +
                        "FWroXC4l6xwL/NKCy59r6E27YTEHFf/N4dzwKn5d+TpUNDwpIHa1WtS4IJvO" +
                        "uTtx+8bqMgO7wFRfZMoioR0Czn0PHdoZobYp0Bop28HUbYagxQd+/MfQIiqK" +
                        "qKRGvM1hXiq/P84Wau5EWzRDN4XOqvokdCZPvUE0VuaNh2LpTrrKwm2Ev2zL" +
                        "p9h3gbTCt8vpzvXF464571bDBnBvSoMngTcnVlOrrSKXgzUDrw9zhRDv9y9E" +
                        "xo7c07R7/mKN0VVmXVYameQW3Yh/Nu+NtNpmqbx39/QnsdyzrGorVqYw+8e6" +
                        "ksLoLm+a1E12U0xeAPRsR3q78dNdy3ShVtjGJ2tJaMwCRD0AXWBA0L+BGjyf" +
                        "A6e61CB7e3lkDJfu5d33dduUmSuz083nAokHiZS6QYaNdDeKfORSy2UvVfGF" +
                        "pYGcL0S5uT1t5xZUrUFJZZmOPVtnyaHgx9Qv6ro6MKou2rhauxpIIJCnM4Rz" +
                        "hDu4IPvfrLRbX1M7jRUAmGP71Wc+foD/OjkegyPAvX1JPjiCC97XPdDHA+vq" +
                        "6up5AHY38yOylm6v3QDfuD+dam4RPMiEfLNpFlYhwP/YD1v41z3h+/jZ1vAj" +
                        "y+FRBH/HEPlz8/+R5HALBL4j2WX/fzP8Y/7hZx7/Lr+L85dtszThOPL1GvvB" +
                        "EWIBAKS/8gH+BkSorclQCQAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val property = fooClass.assertProperty("foo")
            assertThat(property.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(property.annotationNames()).contains("kotlin.PublishedApi")

            val getter = fooClass.assertMethod("getFoo-RVb1_dM", emptyList())
            assertThat(getter.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(getter.annotationNames()).contains("kotlin.PublishedApi")

            val setter = fooClass.assertMethod("setFoo-Vxmw0xk", listOf("int"))
            assertThat(setter.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(setter.annotationNames()).contains("kotlin.PublishedApi")
        }
    }

    @Test
    fun `Test annotation constructor is kotlin only`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                annotation class Anno(val value: Int)
                """
            )
        ) {
            val anno = codebase.assertClass("test.pkg.Anno")
            assertThat(anno.isAnnotationType()).isTrue()
            val ctor = anno.assertConstructor(listOf("int"))
            assertThat(ctor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test deprecation level hidden constructor is bytecode only`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo
                        @Deprecated("deprecated", level = DeprecationLevel.HIDDEN)
                        constructor(i: Int)
                    """
                )
            ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfHBnjnX3YQ+Jcme3slZ+9jJ89Tv+8WHcg82jmd" +
                        "pYXLe6FHQ9HJTOH1h8z6YlPNBY//EfvHqK7Cp/HoRL9Tz1ln0Uvv3tTcP2Nu" +
                        "bWxfV/eX7QHjOyGpMEnp+T7qHxMDfyg4VehMNzqXYtd5v6fv6o1fBRz3kudm" +
                        "/54ScNv3G1vL2n9bNsw893ie1czVG7do1bp8SD8lYRnYovD3uPAdH91Qtxd/" +
                        "9C+53tN31vkap7r33mnlps27zTMOHL3JYJrzqf1bs1nL2X8v/SIdZuU7ZfzV" +
                        "/RUVOdNL21ItzbtdfcmlnClsc6xT/y3WC5y+t2S+wPrwRIVbSgIPq0Q/CCbO" +
                        "nf+tqlRh15c9kd+V9UWsF72u+f+kc0PYKb4VOs8K+b8vn7a7OnCJTd7m9cWl" +
                        "W2b0XvmzVyb1jMvfJJ3NjXNeC/1XbL63cm3JPb2i1elZC3qfvdhso5+zVWie" +
                        "WUW4zrPk0hXGrbUc91xMXU7sbmFnvy5p+9COZf+7vfoZdWnuD9huL1Dy9uQ1" +
                        "v3n739wWgbWrOro5onX2hi68XaPsZpd+lddMQGOOY/PFuatObdV6rJg+J/np" +
                        "T8U5LLc59VQyFh/WKxOKObJU8cQDo3U8D6W28KyTcpn6tY4RFPl/d+5duZeR" +
                        "gSGXCV/kSwMxPO3lJmbm6WXnl+Rk5sXn5qeU5qQmJyQkpAExS5Ifm0ZA0oUk" +
                        "BnDC+qq0Z68wUKcEOGExMokwIExHTnSglI0KcKVzdFOQXS+EYkI91uSKrh/Z" +
                        "hdIo+muY8Po4wJuVDaSMGQjPA+kbTCAeADcz4wS9AwAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val ctor = fooClass.assertConstructor(listOf("int"))
            assertThat(ctor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
        }
    }

    @Test
    fun `Test deprecation level hidden property`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @Deprecated("deprecated", level = DeprecationLevel.HIDDEN)
                        var deprecatedProperty = 0
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBsX6CzuK2Brfzdvu3ZireaI9XkFrQYCCnYRq" +
                        "ndgjNVHOWcdalmdar/TevnuJscmva+b/28/vm5DhU2PA2/9P4/FcXbagm2/u" +
                        "VT+/U33+jvHm/f///GF9wHhNoihKK9Yqy9TbunAOY/IHPtfwKU6Cpt75We2b" +
                        "1hl7GN6IdhR5oc6zcunTLL7MG3o6KRI6fxUDeJKndrv0S6rNDpr+NND9Wuu0" +
                        "3r1+NSrWv4IvvTcWkZq+ZMb8wxrHNj2QUGcqtWzc+kQ/XNIoekp8rZfw2fhd" +
                        "lxd1LIrglj0i2GsemHnUimfRpUrFszMyvZe6BBvteP7ZYInzzamKh88UCBeG" +
                        "+nRFvQ2v27ln7+kJ/8O/RGVdFIgKmLR9t6ZVQprPNYkJO8QeyjNvjlq+YuPB" +
                        "Ddffi8banY1997Var9znwQR//+/Vs2vm7vWYX7dx5/Hv4nuv6MRvl7t1/s33" +
                        "5fOPc+ju+ht3sOiS3PytMrdP60T0PU7lfa9mvPtmCWe2ru+hU+ItkfqP2Tpl" +
                        "17NMvGObdz3l+dwVrS0iWoWxLaEXOc/84plz076v5MK0qw6sLDb7K+a9u/1j" +
                        "R/B0a0eNjY7fU1Wldu60++TtZpR45o96yvTMUKN0B7u9n5jPcbLKaKe8ZXmy" +
                        "w93MXMNBTO/BU0MnDvmsf2zvXwds/lr/OYFp0YnFBW4fO2zMbbWL7z/k5Td4" +
                        "MEMqZE7hjIxHMf3TXSdOCDlk1nXwsJ+QuUDN/H1MU/2Fzk45JOrjfSZhWtjq" +
                        "k2Xlk7nmaz3LuLCa3ePnjmePd/VL7/BUfFar8+Aa44xDSnnyB0Kf7V2coN2j" +
                        "5hiY6KK4fEK4xh9WUIp8aVrkF8TEwHCCGV+KlAZieIbITczM08vOL8nJzIvP" +
                        "zU8pzUlNTkhISANiliQ/No2ApAtJDODU/lVpz15hoE4JcGpnZBJhQJiOnBNA" +
                        "2Q0V4Mp86KYgu14IxYR6rHkIXT+yC6VR9Asy4/VxgDcrG0gZMxCeB9K5zCAe" +
                        "AGbdnHRSBAAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fooClass.properties()).isEmpty()
            val getter = fooClass.assertMethod("getDeprecatedProperty", emptyList())
            assertThat(getter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val setter = fooClass.assertMethod("setDeprecatedProperty", listOf("int"))
            assertThat(setter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
        }
    }

    @Test
    fun `Test constructors using value class type and visibility`() {
        // Constructors using value class types can only be used from Kotlin. In bytecode they get
        // an extra parameter of type `kotlin.jvm.internal.DefaultConstructorMarker`, and the value
        // class type is inlined.
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)

                    class PublicConstructor(val iv: IntValue)
                    class InternalConstructor internal constructor(val iv: IntValue)
                    """
                )
            ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/42WCTTUax/HR3aNpQxjbejakmESZU12gxlDsosxxpBlMGOr" +
                        "hDDXvu+ia5lE4ZKi7JSMLFnGll2SGmSJJFzd95z3rc57e9/nf37nOf9z/v/P" +
                        "75zn+T7f54syomcAAVhYWAAAgCjg+wECMAAQuuaaUDhSTw6hiYTr6V4yl0Xo" +
                        "7XcBABuI7pfGRlDZfnYjqHRvd99DM9jQ2ZkFX1lDhAwc0e9XVmP20RDqI23Y" +
                        "3X3a4mOvHIXSPb8wt3AEgDJiZqnkPlWpfNjg/GGh/rE9+LCIWAJRztsdJwf3" +
                        "IlqgPfywshgPNIEQbN5DeG3OddC4KGdtITTmNmjF+orTdfnGkJcUOUmiGH7q" +
                        "sreAQYwoO/h5v5Khx5hVdcmLiRm5mRZGJg4731B+A3kiiXUpvjkaPSpJ8e9Y" +
                        "gxUFrS43TU6v7Pvf2d/fTtSgm3ieCTw1Qpy4gWlOGfGsdTa3dQHGaU6eHddr" +
                        "ujqa/nV8K9/aH8NyxoBnsBbNgiWoaBcvGxlbcoWDmPVvi9JWbDjaDdaObAm1" +
                        "J9lOe+dm2GdXXY2Xbl94kH96q3AnwDmA7FA7VF2m6nKWhCa9o3/5eIJRLj7l" +
                        "FmhA3jwOBgw4iW3sfituJ+Fe1A9bFiZl054bgMOZw96C7FqzWnpgxsCu9YbZ" +
                        "OthtLtLLA/znp7Ab4XW0jy3ep1RD6RhHvO1Hp0S04l3IfziywLP5muBiKwK9" +
                        "K77BL4AGwEvXhhLMOSMDYadvlcPZqFUWsPjbcae6pTpjuPXzRdE3qfJlSIBi" +
                        "DjV2r+fOS3Vcku2W/14kjjzjqRO5bS8Q9rTe/Rz/ALgxKghiQwziundLNYtH" +
                        "aXVXPM05QhdM9Z30n3C5TxvO1bQxk3ycifZqcY0+EyjScmJA7TF/GgHLI9X3" +
                        "oCJWPnA+wcqFtaTOIeB+Rt60+8j66eUcr1QRPQXqEytFe8wLb/aBNRtVHTwp" +
                        "WlyWF5UWPND8Z4QO3WcOugPiNW/7txi7t++XwowhluLZRsfGuWv03UwGeskP" +
                        "K8EvkJwuQWFQ9pIi6ZFAUeIWRkEmktoZc9dQ7AVO/PJwG+ZBS37Dl+FmxL2u" +
                        "mDIOVveBMyaZYpyXhSXyxqnkoc1rZbHSphI2VjFlVQ/xfOBSsx3zi4XaU8vB" +
                        "mSoiehH5qGSrwPEyz9weXIc/FVRenhl/leJqo8WRpi1YYTrloCgQJoMfsu2p" +
                        "3hk9ruXSiK236JGxTtCt5fSsXxGo0LUnJxCuVkfl8zYDVaI7dLnEEdHFDSIZ" +
                        "W51v8EmcSy1ryuWmJpS1yMUZfhFNyfekQHDYRr2z6Iq8XIHXiXmza5ssOf4R" +
                        "I0MTxypOzPY4NHSqJwGn1n0d5x4xipwPB4olt7J6fdo1mGzZJB25I5j34BH3" +
                        "xAUaIYoYbhzwknbU7N32EmOB5PHPD0z9nEZrSptbmrXFiEp+bDcjAt4+il6N" +
                        "5NvaGS9WZDpxwAyH+KalveEtFkVuR0kntwQ3gENjm1ju1xm4vmcgNMbd2A5M" +
                        "SlYKWpEGy8l6O7BMLFAw3SYJBxw7I2GNG1VvZDNo7HS/dUfKZlvSe9UN97Ft" +
                        "6+v1zNDLMA3XRnH7gENYm0qFMaXNDj4y15Gn1kber1riukZDKV0zxy7Q7ssV" +
                        "xB+EWn3GRWirV/+mszUXVHE8UAiV16J/JVioT32j70/dXdm8/FcVqR+6eLVU" +
                        "LgybDlXFlBCf5SSYfFzizQr38/BAPwubm8kOz6qVVepUjlKKUqlbdVvpr/eF" +
                        "fXAc1xxuVTRRfiOyA/nmPhcTcJnKDADAB+ZfuY/I9+6D8nPycMNo470IRF8/" +
                        "DBHv+y8bSjdF4AcvcgXXw28Pwe/lrvffXShCicTr5FkIJcVIax5LIYNQmk/M" +
                        "742L3fWYH+2sT1DS+8qtRChlkJ4LLfSd1xd1Wc7wDaVPUd99u9ydO5YN0Wi+" +
                        "Se8NRpCebqX7TwO3JnHw80mN5VLmKshrKjGxjsDeMD7W81yzTbs112e2iceU" +
                        "mT40AclTzp3nG9I5tCuqBkr/CC2MVzME2iXwZVmGxa+DlKRBpQ5Gz/8c3Q9o" +
                        "ZSe9b6ps7M/LQwZ4VjbnsV0HfmCvVI8iimhvOyoQsff2lEBhu50h4ikXD0Sr" +
                        "oHNhELzzpNW59Hxslv1Kni6NfZCLRD1Idc5SRoxuPXp3FBo1326aoEeJy5Zn" +
                        "2lyhW8OT12wivrgD5STnc3oW63ryKKN35Z6Tv044N/Ta0tCPXJQP5yfznLwB" +
                        "66ob2dt7R3PjKLBCzy7o8Ab5Fka4eR5bj1K5GUJ+boClgKxxdzdpY8RGVIBO" +
                        "0uch5x1QJYcC1On0+13goCf/F5MU9EiiQ5TXReNy4U86r45VKeyzumkkptQt" +
                        "amR0ZE/DtaC/39SXhtdytsEGtIxyeIZdh3Y2dxQfErWUnVyZn02I4VPVkCWT" +
                        "am3mhNmzZb+/8Zk46e50cayWcqW70Jtj0CUnIk6AHcKxl53hTjLGFeywUhND" +
                        "0kud6bLj2lmoyJRV0xrreKqdUttOew021QeKCQE/RrIJEkU9Y4uSh2062PJs" +
                        "iQHRxEX/5A3YasEKmi6WwWesncdukvFS8dOa2JS2mjY/RttZkhai1TTLVOrg" +
                        "yDdVsu+KMBkcAQCi6X+lypM/3YlYXy+0x3/Rpb07tyZI/Sz8/JKE4uuhxJvW" +
                        "5RYIev/BuXDUKVddKwOewjdkTr+UOL+cngqa8a3E1pDCviZQWgzn8fe5GoYN" +
                        "D+F7gGMRk/WLU8rbE/W4puCDr4wtwub8GfFDHSFU9c3wzk6x47NeQm6pm68l" +
                        "kL6OwKlkPhYNUH/BQbGf2GuJ34HhBBPdLhrM+XTrtVTRS9Z43jORt2Nl1oF2" +
                        "7HxWqIjYNgWlmGcKcukuVqUH7m/OtXcXKLTO+BeNjau9o5EH6NotxO8FYLjL" +
                        "OjUENm8oun2CdIELITnTYls6A+mUrUDIC/IsAUhwX3aIC79iiB79rAEnjT0k" +
                        "qbvp6PNpKk4JOnciXdpr2P/QWDXbe1RtHY/Cpc7uuFr576gFPDkv8HSzWOlO" +
                        "HoS9NjBeXWoheibwRvKIfMmq0ApW8Og87YPkOQEzR3P/UvXarB6ToN4lwSve" +
                        "JesleMurcREWy7ehLiV9e2ifSxHB8Ou8vR/1Rl550jqEKaiy+wYXVrZt5IMk" +
                        "bEQvd32qg7bdNUjrkdTddpswGV9PrqZoFUH4qakIMk/eVGLhKEtRp6xHyuux" +
                        "obDqxiAt/rJXiqgFJqxqms+Xp1RuY8vMC05vs+QTckFQ5meNumtzWZEEKWtt" +
                        "VmvFdciJRgx0+ouHahkEtrFfKf+cFYlV07un5tZk5HFdFem3xLCYOJju+YLq" +
                        "82nRESc/Wnh0yUYonTgsMLxUb4TLNAi4CckGHKXjpZdBZ4vIMlu1pgfDEGX3" +
                        "dzvd0xQq3OoERhS7BPI7HujNMn+TpNqyio72oSSTfilJwcP6d0r0RLt5ybrj" +
                        "iR5uXg6eeGc/DyzG0dHR5bAYnJBMUiinV06AvyPgp5MNjdyHf/L9HQHpjoAA" +
                        "/6F/Hw+/ZdAfxz8l0p8p39s8+AdCyD8Hy58h359KkR8gewz/+374mfb9gp78" +
                        "gZbK/P+c65953y+i4A88CbZfbgrKiJHp22eMh48vHQBwi+3b218myA97CgwA" +
                        "AA=="
                )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicValueCtor = publicCtorClass.assertConstructor(listOf("test.pkg.IntValue"))
            assertThat(publicValueCtor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(publicValueCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            val publicIntCtor =
                publicCtorClass.assertConstructor(
                    listOf("int", "kotlin.jvm.internal.DefaultConstructorMarker")
                )
            assertThat(publicIntCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicIntCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalValueCtor = internalCtorClass.assertConstructor(listOf("test.pkg.IntValue"))
            assertThat(internalValueCtor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(internalValueCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            val internalIntCtor =
                internalCtorClass.assertConstructor(
                    listOf("int", "kotlin.jvm.internal.DefaultConstructorMarker")
                )
            assertThat(internalIntCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(internalIntCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
        }
    }

    @Test
    fun `Test constructor using optional value class type`() {
        // Constructors using value class types can only be used from Kotlin. In bytecode they get
        // an extra parameter of type `kotlin.jvm.internal.DefaultConstructorMarker`, and the value
        // class type is inlined. When a constructor has an optional parameter, another bytecode
        // constructor with a `DefaultConstructorMarker` param and also an extra `int` param is
        // generated as well, but we aren't tracking that.
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)

                    class Foo(val iv: IntValue = IntValue(0))
                    """
                )
            ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTjUaxv+WWcwlhimROiQfUipwafSWMYytjNkSdYxZMxg" +
                        "BsNHEeYgpBJG0VEkYkTZsk0ke/Z9bGXJTjrkdBpH57uu71PXV8973f+9z/1e" +
                        "73M9z30/lqYcnFAADAYDACAD7A8owAmgDTB6qsbmhmpoPXNjQ4NfMXC0IasN" +
                        "ADbRHe1mpqrwHn5TVaU3HV3PrNX7T0zNBsBN0CrG6J7AgufW6yaq/komHR3K" +
                        "tutv1FpbO97Nvp1lByxNQeBiEcVirb0HTu/B8ofPw/ZAxpLIan4+ODVjAtnW" +
                        "FR+IhbvjXUmkcEwnaQwjtFs7r2ZvKzHi3WfH0y3otRLWT1DITT6WY6xo4yeO" +
                        "ipfhhzX1IEzwI3alec3MKbUpBhe3wMWAiEMoDTKVZyGxPs51WL416PWG+sOQ" +
                        "tZW68clVVtB9Fmv7xlk2ZlMaRHGIzAxzr7815FvhgXH0hCTojZ8YNay7PHzn" +
                        "r9GtLPsgd/BxlGhfhSsYS9JG5qyYml0QioKCjO7JLK86CDSiNti3JBqTHSf9" +
                        "7qY60UouJyo1zhZmKW892An2CM51rugvLdDxPEF1pb7naC9ncqkl3roG7dXA" +
                        "JKhDgo9iazvm5C4e83nYo74iSaUtN6FgUaDIOejFl+mMTnUzSNuHmulK9XtC" +
                        "1PZd4qcX6mFRlcvrDD9FnQg2riE/p+EJ6fOJnrm/u4CNaQfrjGVXxd+sBoQ3" +
                        "Q1CQX0P7kzCCMRR15WtFxrwDJbbqifcSFDsUWuJFjLJkXK8MaBSYA5oZA9e/" +
                        "dN5v18UlO24FfYnB5U756sdsO4lHvqj2OXWoF1YbGyLlQA4RenxNJ10UsfZZ" +
                        "LsUj2gA2EDAexPR8sjx4V8/BWr48zZXA8Io7TpFmHOn9V/mhFBJWVKGrkH5d" +
                        "g/Iuyc6TJ6/SOfhJauakz9AH5ZUMwm1pw5MDVXaaTu7Nfvy9Gw46+kRqnBxc" +
                        "zDIlvLf+abQ+2ycBtl1yqJ/TnPvFucWFSDOpC3I00wOjIs+NvC163+Q+K4Y1" +
                        "mwt6hkSq8uc9VBqiyJC33E+qxAy0xD8ykW3GydkMNrgXMrJq/hysRz9uiy8Q" +
                        "4PHpPW6RJitoI3ksc3Qgt/9jaMF1JatjDnbxBSXPiAdh+dY7mHMPkBMr4Wna" +
                        "0obRWZY37SijBb53O3GvgwagRUVpiZdbvRzOC6QgD9OtJpw1xSNViP2OnaU7" +
                        "w8LnPWux1badKvZJBhWCvtWr4nQDp9wk0uXS2Cyxeoh23GsDITl0XE6NdOpW" +
                        "ywwxWXCBsaFVZGXRuhEzP3VIWk9+kUqBRW5We8isaqhlE468sw79CM4Iih7q" +
                        "Zx6gH5nudK5p0U2GTHwIcHlbxiV9Ogoie/MlD+GPz6hxxkcq+/3DmYVlIswz" +
                        "y6RYcpRZcPsyn/X77QWubHnhT4VWgW7Dz/PrGfVIWTIikPdKdPBcWdxazMGt" +
                        "ndEcTe4juyBjqYCUlBmxHBnz7Vilm4zwGljE9Trwk0qU1yInqTYhbJuSfBMR" +
                        "sqoEU4P7OYOZs63uHRZJuwI7Q5G1myUz8NRlfrZfOmLgtAschMrBLt5tI8PO" +
                        "KQ4V7sGKWBF/2FWeunxJ9/x6Z3+Vf5srbgwtrl3AtQ1HtLZNHTiz/EQtO3E3" +
                        "wu4TLhqpW/qL/tbbELowRcIyk2F0KVyiS3ez66nBZ3hmVjf99lKb2HntM4NW" +
                        "/SXxeeRXGUkW6wti6VGBeLzrq8i3U7So9Ao4okUrFhGrXbnmvdpTHaC+5DKq" +
                        "N/hS00JrRnpH6qv6nEvCpWlxAsAS6GfqI7xffQyJxP8Izx3rLgsRKyHWxGq5" +
                        "U3mRqsGOjb5lv3WMLx9IBBznhjrlrcnWxwf6MEqvfD4uV12cKN/3yZnS/wwm" +
                        "zP8qynt9Tgp7r8JR6CHsVmcNLWSzbu0u7cvO0lgwsGSHQ/HoCM35f+5mbtRw" +
                        "HkRuxW5XjUyvZu+U+nucNmBXQob1+UuWNP0uWjirHeVgR88TQjZEcGfq+f8J" +
                        "6TB5eS4AwmuNUZxAsDUmLtrE81PLrmFtxrLeI+gh/WsZScr4yVnJzaovGdva" +
                        "bmMkjuntjIZNHyZxjEWVlajXL7loNZ+ZlclA5Puy8i/5v2jORMnw0BhoDi2m" +
                        "22MovVPe8a7G4xve1I/ZqoWsNkKt1RmvgYZB1RCptG0SRMT0wkkJMhJMHViy" +
                        "so6CCV4un1KFI2ZTCM656OPer3PY+KvQOxVJCSYzZW0QUKPeG8tHoshWARB3" +
                        "eHaxT1+jnoFiCY52j65sj7VVzJ+//9bEdiwz7oEUDo+QblTw7V56VfiouXRj" +
                        "c9ukN3ZhXmreNMFPTA4dY/whbvGsjzStle9qmaoXa+xSu2cLRnLoSs/SKfyu" +
                        "KJGscPtJKHoinzR5c0OBNiFSM0LfeGeFXw7RrfdeQ2ikj6xUNSyMBsr7vd/K" +
                        "Xwq9xvfbo980+J35QLdu70qEiGO9lFPsc72uH40xf0iJL3FjKffKQxXVW9vE" +
                        "7Z2P6hyymebWCKibMT6Zio2+I+lapMdfFUAarRbsHjhhRhDbXIaeOOBJK+RN" +
                        "/YsvTOJZyw3ArOq0tnuTGuoqqzgJgxFaAI/kGd64b1nGDKpJP9u0cvspbsdN" +
                        "pu2S0g2jNiTxSHADS1lmiDcQwPeNs0/DV5FlfkMNunymlRbwI3aU2IYzdhFY" +
                        "6HY2qIWTAmWA1xEMTMQIsAO0Tn5tbPPPB47R2AFAh/NnjX14D/91dV9XbwLc" +
                        "h0jGexOcfYkegXisu4uLi+ceON3MuRUs3brdgH8s+4+jNbUie5kH/7FsNnYo" +
                        "8D/2/Xb+dWf4Nn60QXzPsn8sYd8wXP3xIvA9yf4SCH9D8oXz/83z9/n7v3n4" +
                        "m/xB0E/LZmnKxf31GufegbIBgMZXPuBvgggaVFwJAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val kotlinCtor = fooClass.assertConstructor(listOf("test.pkg.IntValue"))
            assertThat(kotlinCtor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)

            val bytecodeCtor =
                fooClass.assertConstructor(
                    listOf("int", "kotlin.jvm.internal.DefaultConstructorMarker")
                )
            assertThat(bytecodeCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            // When a kotlin constructor has a single default parameter, an overload is generated
            // with no parameters.
            val defaultCtor = fooClass.assertConstructor(emptyList())
            assertThat(defaultCtor.targetLanguages).containsExactlyElementsIn(TargetLanguageSet.ALL)

            val bytecodeDefaultCtor =
                fooClass.assertConstructor(
                    listOf("int", "int", "kotlin.jvm.internal.DefaultConstructorMarker")
                )
            assertThat(bytecodeDefaultCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            assertThat(fooClass.constructors()).hasSize(4)
        }
    }

    @Test
    fun `Test constructor using nullable value class type`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo(intValue: IntValue?)
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VZ1AT2BYOPSCdQCgisEsvSQgLUhaVXgOBjXQWQuglhBCa" +
                        "gnSGLihLL6uAKE0BERRCAorU0KTJgrAI2UV6MYAssuibeQ+dp+fO9+PO3POd" +
                        "e8+c+31IMwZGEAAIBAIAAAnA2QABGAEIA5SOkomFIRShY2FiaPALCoIw/NgP" +
                        "AOwiBgfMzZQgoxxmSvJDg8NN1rBxlYVlPMQUoWiCGA2pfmy9ZaoUJG86OKhg" +
                        "szUE7esbfLu8uEwPQJqxAB/yyT3UOC1w8RTIb5YHn4LgEUyA4vy8oCZYgg3a" +
                        "P8QDgvFHBwdHoSjBf6C4T4h/Qe1tzr/2eWXHOsLlvR45jpWtzJKuMJG7ihM2" +
                        "TpXgAHePqpv6v7ZrrOqZXYAukJmYOZ3wMULGcEIS60oGKQU9LdMX+nIbVh6x" +
                        "ud4xN7/xMbTs48f9m5fpZrvz2OWmCLORGNKtqYAWd5SjJ3u6zpzKjGGH7/Rv" +
                        "/8zQSu1DMUBlY/5XLWigR7CmXsW6mbktdzyIxahYYm3DgfO58TY97fzzLMd5" +
                        "XFGuc0GDb4b88+XaUgXa3cMw97BKl5bxxmotT5UkdNLfDANPZpmgGbfiQGNw" +
                        "VDqMPewHD+IgVcpJ2q98FLYumlSw1m0MjmeJpYKcOvPJFJg5e/9O+5+tsGLu" +
                        "pIGTwINnsMj41rUtMk5OK4aOaQrnPP1GXDfDs/J3V6BJgWCHieSG8NAGPqqH" +
                        "3Zj9l2vjmSiuxHCYQlydCdtEgw0sozhdblC2N5XPqFQCfWMCXm0BUC2cSDum" +
                        "lA1oe2U50kKPE70qFwL0E/edhWOftfmpCY2BickRYg6ECO77cVr5/OqbR1I5" +
                        "7gkG4An8XOisZ83aZJGOg7XMkzw0luydohwuTr4w9vMToZxgD37Z4dr6NHj4" +
                        "20w7T9aqVpewmtySeb+pHYX1QuxtccOfJp7aqTpjenAcY9sOWvqBSSlSEAFk" +
                        "TtQY6VGCPt0BJ90J4RrOmYpxor5biTUXs5UqMOOZ4Xts5GM5NlTZ9BDcY8Hl" +
                        "GRGrxFFVLj8VLkGgYX5STJzoTb1nKtnjJXV1sgtTSy5t/zBJQtzvT63mZPUb" +
                        "U7bMk+S6KipdMjNROb53rTpN3krawS61uqEpUBD8wPoQdeWu3pv1qDxNccOE" +
                        "UmS2XfhMdUARxetl6ASori4vw7fP20GXM0dPpN7qjYuqcKxi4LgjpfFwmlfX" +
                        "k+jRZkNRtM80aOEKaNsQrjdwrswM9m1MLhUgsWumvDTglkKkVLSL59J6lwKz" +
                        "uFbI2xp1VpZ924l/LQiJ68i8SwoHx+62uUtswKF3sBfeWl/bAxaGJkyNz/LU" +
                        "X/iT4tLeq53F/mYH77rYzCR+MZ5dMruTFfv+yHiOvJdEXyZSUtvMN3tpLTiZ" +
                        "EG8eNrB2zvrv/RWmOzK8B7VWIW7Tjx+QyCQ9SYJ6CNuNhDBqc8pmoiDtcKZC" +
                        "lfnCCYuJGD4nZ0mgQsJiP1k+mxzVDo5J6wDWtBp7v2MMJqZH7odnZatHbMiD" +
                        "oRCcC3B2uQ8zaJl5wnk4FUvcbViC5K5x0P04mAgpsGXAtk4Os+0bGVIWGBSZ" +
                        "J1uS+YLA0awdD0QxD0guQYrXLeS2p95t2nr1T8f09S/wXFqrgd7JOImxO/BK" +
                        "0NNu/FGfthhRzxt+HllCNvo16vyw9u7wI4MjSEnpSP3t1X4BXc1Lk1bjDalV" +
                        "hBeFmZZbKwL58SH+/ugXsYsLBfH5LRD1Xo1k9WTN1k2fjdE2PGzVdUZnslPV" +
                        "UmNJ/FDsk/pcyfTK02AEAFZZvqc+vGfVxzAw8D/C8xsSYclnBdIOu+dibqYF" +
                        "qmMiccPozPN1ne6rSrNJ+FhLWr1CgKnNUYt+VSqeVMTbRlJ3NDn1RaKP04fy" +
                        "9H3EuWeumB08afjpfokLiXQC2ALdZJVSwqxHCZVcl0Hns/DM3skHDoM7n4ZN" +
                        "ivR3DD2XXvq1+sqKbcKMxZHwGBvB6CDFfyMUSBvCViUo7nDCZbnhH5pSWZdp" +
                        "JJHChDiH34E1F5E8mYEzIxcSZ2xjLxana0tnWYlHbtrd4E15eLNLfNJqbApN" +
                        "kL+bnV64a4bnVESFU0JPNl/0BGnqTp9cNu3q519A+SpzO+Aowpb+ubfS7eHM" +
                        "jMJBxzVw+/jyTrrOIoRzT7Vj5vLQPc7LfMP33r/eG/EdKTJtXnBYLdBUXSub" +
                        "x+cWKT/Sg7t3KzDRjApVtdlYah0r6+gP1YT23fd09iLrUX+gXQ5EeeRuQ1OH" +
                        "jmApH3b0C9zXnXm9VXNF+RU2OjjjkOfqDsrUXQvg1FBa7mjGPhZHKyZ2X2oL" +
                        "S6lMe3ycXOfDoKb2epLKgEOaszbKooWWeI2WmSgqpcpuS7RSpy6isSp8OiPi" +
                        "vXBREeaVN9PEXn4dYVXPvym6k3iVSpxInOtaF/BVmtUp0attfpTZ5oxMv946" +
                        "b6BmqPwyXrBScEC5P/qzPwmsy/ct0gEAO/TfmxCRU/zXHgPQPliIXyDB3wfr" +
                        "EhDoHuLvgXF1dfU8BaObBbMs0m3EDfCZ+/0P7US+00zBz95HRw8C/I/9rC9+" +
                        "Mt8v41tW/DXL2fkGf8EQ/W1H/ZrkbAt4vyA5Zvx/H+Pr/LPPFPkiX4zlu21D" +
                        "mjExfzrGeLpApxe4zvJp9y95aX3JpQgAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooCtor = fooClass.assertConstructor(listOf("test.pkg.IntValue"))
            assertThat(fooCtor.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_JAVA)
            assertThat(fooClass.constructors()).hasSize(1)
        }
    }

    @Test
    fun `Test deprecated hidden constructor using value class type`() {
        // Constructors using value class types can only be used from Kotlin. In bytecode they get
        // an extra parameter of type `kotlin.jvm.internal.DefaultConstructorMarker`, and the value
        // class type is inlined. If the constructor is hidden, only the bytecode version should
        // exist.
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)

                    class Foo
                        @Deprecated("deprecated", level = DeprecationLevel.HIDDEN)
                        constructor(val iv: IntValue)
                    """
                )
            ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WZ1QTWBYOHTQUAxJAugGkJRGkiyMlkNBhECISQwgQeoCE" +
                        "OjCClAU0GVSkFxWQIiBFeiBxEIHQlC6LigooLUqRMooMzp6zi57V+873793v" +
                        "nnfPfd937Sw5OEUAvLy8AABADnAwRACcAGuEo5E6ysYMZm1kgzJD/OoItTb7" +
                        "wgQA1q37+6ws1aFP+S3VVQb7h+od4KOaM3MhUAtrNZT109CKBw4fLNSDVSz6" +
                        "+1WdPgzCenv738y9nmMH2Fny8N4XVr6vt19AZx92PywP3gfZk0SGBfkRYKhA" +
                        "shPOP9QTivfHkUgxjgOkfzsK7bW/hZ13knzmM4LmeyLovRI9GniiJE2xGKV8" +
                        "LkgCmSrHD+56qmvh/wxdV9o9PQObYXBxC7iGxIojNchJfAsUegpuUqk37PEq" +
                        "vCjy/UrH85esL2GFX75s/fEL23RXJlB5gjwdjadfnwho8nC84AW8avRcc8qs" +
                        "w3fy5uepzYLzYXjek8ijI004Xk+SvknxiqWVs1C8CI95ntwyy0WgE7nKvinZ" +
                        "mXbhZVBuBia71pei0jlXWaC6eWcn3CO8BNs0Wldh4KWZhEt6x9HXOM0Fo1y/" +
                        "LDKs4XgVDgyX92zvn1dwVfQregpfkUrKXu5CguN54uZFXB9mMQbgVkDmGu1V" +
                        "MzxPKKlvj7jdCo+Ob17+wAhSNohl45oIwky+kDWmeJXccuNFZYt1oCAsiUFW" +
                        "SEw3EAn8NWqU6iiYGAFXvVyFOjRW6wSn5F1V7j/RkypsXiCH+31Mo8IGoJUz" +
                        "dmV3oLDPkJB2YTNsN5FQMhNgmriFkYhrbfPTFh8GtydHyriQI4XKLhtkHdV9" +
                        "/0kh3SMBAR4LeR427XVveTzXyMVBqTETF8jwTjkZIcuQHj7dKJ5O8jx6Yqiy" +
                        "+opGxBsq2ouvtBkbfi8j/6XfxJrqSk7gDVmzU2MtaC0MvjuIf3jVxcCUmJSi" +
                        "ABW1S48ZptckmLJtC7DtkaOCMPN41/nFhTgrGWeFbMsjU8IPzH1shwdL6u+D" +
                        "u20EvSLj1PlLi1QmIuTIm/hTaoljPal3LSDdBIVz43/iKxkFtL/G6dZlzNQK" +
                        "AT6/4ZO2mRDBc1KK+VNjJaMbURVXVOwVXdCpFbX1RDFwucOO49k7Ji9WYjL1" +
                        "Zc0SCuyuoSOmKgJyBwiPw8ZEqqoyKb693i7GAukmx6rtX2C1JOLUiKMXBup2" +
                        "JkHGXu2ebU4DauepiCbBgDaWRDUCU0Il+dYlF4jSgfopjxFCCtYpxTTZjM2e" +
                        "WWKa4AJjVa/K3rZ3NfHtjLiskdJiUgQ4br3NQ46lAbsdKP3GIWqDNycsYWJ0" +
                        "+ki19KsBLK3HMA34Yi3E7XUDl6xOPBBy7SFf4MdPyOeMjST2wmP5lQ3C02eW" +
                        "ScnkeKvwvuXDDu+2FrhuK4G2K+1D3ScflNMZdBMIWTf00O8J4fMNKe8TxTZ3" +
                        "poq1uKX3eFAyIenps6LFcjZbySrXGDE0cOyVDt57zUjvRU5S+9XorYi0a7qR" +
                        "LBUwDBqE5Z2e68X321L3BHYm4trXa2ehGcv8bMf7E6HZzhyBzeNDh7bMzQZm" +
                        "ONS4x5uShYPBl/g6yqXw5XRssNpvNsqrE4vvnQnMydhe5syRM8v3YLcpe7Ho" +
                        "bUKCiWHdcdPN15HVoAhJu3yG+cUYySHD9aEaxCdofsGT6htLTFFj/TPj9qO1" +
                        "qaXkRzlU2w8Lolnxof7+uEdxr2ey47OaoLo9esm6yfrN731YT9tC4EtuU0bj" +
                        "D7Vs9WZld2S+qs9ZKiFTjxMAWOL5mfqADqqPGZH4H+G56WBNHDkrFLMEsVks" +
                        "puQa+7i84xFBclSNpHI4HAEFlNWki5bfco0q0+4FTeuGzT03lTz+hS+vxZH9" +
                        "aPBZ+5bVIqF6G/oj/mcv8iMNNImniZf2dlaFGcKVmrPpaUw91oP8fLhV/MlJ" +
                        "c9qQVCFPwNvQN8hT5yT4uNezwRGLeiZ1s9Wj4qCSm1ECysXqkGs4OO0MB4lU" +
                        "kyYVHyeJqNgq8Mgqw5ROeogRD89tOm8tUT52jge6Mj0nT7Q02JRFw4Kof2G6" +
                        "D6cns7MMzq1St6IbpLgdEO13DxVhpENNH5S3ZbUiy1P3K7aA1OWR4YnpNHWx" +
                        "ynGFwbQ1BHYb2HXpBg1etnDcQgvbMrbYZ7hLuIug20Ooc/NHt3A6igT0J7qX" +
                        "uCIMFJb4Wr1aF2fWVCgrl3Xx8QZ7Du5fHwQLNlU8GpBFna9uuYlrJnlOdsYh" +
                        "iP6E65qdD5PscRd2rFrdph5X2qPyYrT5DVqwkpH0jvpXsaJmNYhnC888FD1z" +
                        "CTKUypvMvJic+/CK+mIf7SzFwtZhJr9j/idDowAv2HIDVf58U6coRRu7zl47" +
                        "saRtupGhE218qKGmd1f/j9MrhfFOH3PYu3Yv3qJVl/02XDQCNhKqiPhMtq7U" +
                        "wcF8U6WGwOjSXqU3PiIbMfKqRrwo457ja409mt5NzFIx1aGYzm2+SG5pP7Uz" +
                        "qGG8+83KBXuIYtoEh6KlM8TQbUSn4u27lpEClUtd25l9Dl5AKnPWjK1IQxuj" +
                        "6Kt+ROmXEu+qjPMFBl3LqOnGQbTk7cxH83q1jfNBuXXj7tO3798i2Pbwp+Rm" +
                        "iTYniq+pBCvN6brnyrOElj6zgaCirmqt6D8xGsFDXRosmVjUaUYnhwdkDaTA" +
                        "Z+mOuCOLs7Sy8Nhj/zrRlMBt3evsAMA2x88m+tg+/mvnATifQKgfkezvE4gN" +
                        "IHqE+nvi3dzcvPbB6W7DfcLO/Yk74B+v/ihPaxfezxT7x6vZ2EUA/2M/6ONf" +
                        "l4Vv40erw/csB/8j+BuGSz/eAL4nOdgC0Dcku5z/7yN/n3/wmce+yR/i+Wnb" +
                        "7Cy5uL9e49w/ImwAgOpXPsDfzJWEJVUJAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val bytecodeCtor =
                fooClass.assertConstructor(
                    listOf("int", "kotlin.jvm.internal.DefaultConstructorMarker")
                )
            assertThat(bytecodeCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            // Check that the source version of the constructor is not created since it is hidden.
            assertThat(fooClass.constructors()).hasSize(1)
        }
    }

    @Test
    fun `Test experimental value class property`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    @RequiresOptIn annotation class ExperimentalFoo
                    class Foo {
                        @ExperimentalFoo
                        var foo = IntValue(0)
                    }
                    """
                )
            ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/4VWeTQUahsf+5AtZJAw11IkM8hSrgpZhqwNjUGWsWTXYIzi" +
                        "MjEaa2jKLttUZBt142abkn2sWeabi0a2YiwTl6TLp/udcz91vtv3vuf54z3n" +
                        "fX7PeZ/f8/7Oz8aCg1MMAAQCAQAAGHBwiQE4AZbGcINTMCsTiKWBFczE+DJc" +
                        "zdJktwcAWLek9l6yOKU2JGBxSqWfOvDUDjqiyZhDq5lbqsIsh8IrntmtmZ+6" +
                        "rmJOpZ50WOuHdHdTZ+bezbEDbCx4gDWiyjVn9gvo7IfNP5YH7UeYV2gYJMTf" +
                        "BwILCnNwDwj3UkMFuIeGRsP7Qn+HC+81L0AcHaT/5fsGwTsodG05aiToBCld" +
                        "qQymbB8iZZYEFgC1D+maB/wLUfeoc4IBYVC4uAWd0bGSZhphCbwfUlsT3WnH" +
                        "uzEdLGhp5Opyy+TblV3Mg93drTvn2Sbas/iVx8MmolCtmeOB9Z5wJ2/+FINJ" +
                        "TbpJix/t3hf6ZqEjBgVUNzvypt4d6BV61qhs2eLSFeE4MR7TfDBzBSnYZsZi" +
                        "35RuS3d6G5J33yWH7Jeq0jZXWXhys2Q7wjOC5Fo/Uleh562Z4J7wnqP3+QQX" +
                        "JDXzltiwBjwFyh/xk1czdV7RWcm/dAi6fCwhh9luBorjwc2LOb/MpvRBL/H3" +
                        "fGyaboDmCyf07gV/egGNimtgrlFClPVi2bjGQ1xoU3KGqd6kIjcgLEeiBaaw" +
                        "ItW/go7u5Dfjv3xjJA0uhMdCT96qgvGNkh2gqfkpytQTXUmipoVg919GNSqs" +
                        "AFq5o8l/9j3o1fdJd9rE/In3ITECL+K3XKRwLxr9tSWHQc2ESFlkWKTw41t6" +
                        "2Ud0V3cUiZ7xxqBR9CRmwvsJcyzPAGl3/HmWexDlWqI6Vo4iM/zzc0liqNeR" +
                        "EwOV1cka2Jk0hDfvowbXiCf3C976j388uZwbdFfO5PTobwgtF1RniMAwC6l3" +
                        "MTghUVFN3IYYPdxaG3+R7ZMg217YjRCXeZTz/OIH3CXZK4o5Fofpos9Mfa2H" +
                        "+0lPa0CdVkLekbhTAo9KVcax4LBN1GlV/GhX0kNzhU4fRfuxV6hKSmHT57FW" +
                        "y8c9SRWCvP7D6tZZCkL2x5QK6KOkkY0bFckqtkpIRFIF+WmwBKjcbht+ocRo" +
                        "ajk666ycSXyhTQYCS68IzOvz6cCMilVVZaX6dV9DGgoSjY5W2065aknhVINH" +
                        "nPrqtmkiht7NXo0OfaqOacb1QoGNK1LVxi6ktFC/OkKheCv/2cQOY2FFy8Sy" +
                        "Jrn7m12zwelCHyisM1W21t0s/AJDUs7g+GICFoRbb/QEr2hAioNkZuxubABz" +
                        "MfHjIxOHq2Wm+1ybuvTT+ac+ot3e/colpxPHr5Dxkjfojx2zScpGAvuDowWV" +
                        "v4pOnGOGEsLiLkX0Mg/Zvd/6wFV8XORTpW24B+1ZeSul1UghTDec75f4iPlf" +
                        "E1fxEpvb9DItbpk9HpgsmkicFS8DW20RVDIo0U2g2OQW4JMGs2uLnKHNKVFb" +
                        "2PQM3cgVFRBELcQVODHXjaJap+0Jbo/jmtfJs2r3mQJs8lS8Ws4VjqCGsQG+" +
                        "LVOTPgaHKvdYPUH0OiiGt6X8GKq81fW66k0rZdb44uoVnx5abHcP4/A55hNI" +
                        "cepeLOKTT7yRfp38xc13kdUiWGmbAorp1WjpAf31gVrjHbWCwsHqu0s94oZn" +
                        "z43ZjpCTHoW9zk2zXvsgnh0XHhDg/hr3jpETl12vptt1hqBLONuw6rsy1IiG" +
                        "LrnRDcZealmfmZXblv2qPhfSfLLOcAIASzw/Uh+Zg+pjjA3xQvsGegWFuQeY" +
                        "BAf/R4TwNvrW7AbC0aFAngXQJ9zta89KNgZpvqW8CllFEnCpCi4dz2maURCY" +
                        "PrT9QJWfvGEquSByzzC1j/l75Pp8H/X3nfVDsSimiLO8/IzAjkvNRyhRXiud" +
                        "TiiSStAmpC8nJq6xFzdebZ68ewwlb0DL88GPFR82wdP1dhK0M+OjqmRzL/jW" +
                        "gFc7/RRKPw9++hxf67pjP5WeUCV5HYb+6RZZgbir3MzHMRrpiZtsaG5iQVTi" +
                        "J7KW8affWUm8rNTKVsC6JhkpCcrZPFgXUtm9Jzl5TITvqS+d7NxLrmvfWLo7" +
                        "1//Q1CGn3+dNA4NQ67KzhAh4pQ+KDsxlzfJEG4VUt7Eeyz9RYVjypapb/5xu" +
                        "eaQcI7dQnwRst+4fvysbkFhFpjRfZtHM3PJK8exJXwo1zDWzMkJqCYoK5AnD" +
                        "V51Jd+xlHWclPPo6z2oji4B5XfVuxrQkhsdtvTJEIMdfHLnf88Ig2ACAw+w/" +
                        "4kjkIEd/8+JlNxAsYiC2641xvI8fxj8n2lU9dwgki9GxUDoiRhtcnAs3unQD" +
                        "eqgyvLfOIrsYrXD1i94Gk5TY0REHxq7f0WhxqR1uuK2zGq35dqslWrP1l5g9" +
                        "Noa2OT+v7yHHAJ07STdbZLlQ0DtVok/eI6dsWZHLtOreE2LcgZ0s6cgHBo5O" +
                        "NUrCOO8LxkRwuJrNpucouhEjpm5XvlOYXhDk8rng1hu/Rv5FZGRz/gVpXg7u" +
                        "h2aLqaua0FcP3TsoGSVGH6vkJTLvwVy3gLp1il74OL/ApXDU+Q1bJUQKw6/m" +
                        "j+oR/7RcuGSJ3jNbmmLrb8mv7XvXo3xht5lD863a1jcDG2eeBVgjVx57VQxv" +
                        "kJaw7dFOSAELZDxrLNGwFGVA4GLb4Bi5v5VfVIL72CHexio677+mqtNw5v1R" +
                        "54VSw/G6uF1tCNk0Y/p4at4XtzY4uqlsakjmWk2CbOb5AVqRNW4Ik9BlghyV" +
                        "Vxoof+FtcgXuooNq2/o56OLGdCdJRL59rmhG+1AUFb7byIeW3ssL1lZMXSC/" +
                        "rZV8eDLN5/zScz/MAsk+lBd25cuLOYeqNNER1CDWo14PM5mJiXOfLlqCSdmM" +
                        "HhZcPpefX8p6tYpZ3CycodNKkuFtL9cYV5UvKz/iMVK3HlWbngVd3g4eXO+R" +
                        "zHTNOKcYOyPerkBMLxEBG1yPq3hNiJWkDue+ktZAkT2gMUj5bYhZzDnQkKFN" +
                        "npxpf/d6V1p3xIfTQv1zIAgi29PH5HgHU86pKMZjwzmUeLK4X8xEVk8gy1BC" +
                        "p9MwuThAPWUJjARviEi+iAKSUkga1cTrR3qJHqQUf4Q+p2hD2tHbhENUvgmx" +
                        "Qc6HXGOcaPZQdklcJK4ydpvn6wgrol7ejNsfXyrHj0b46H787bEC3X2D1PyD" +
                        "wwJ8g1wDgz3DA7xQbm5u3vvB6WHFfcLGY9AD8JeB+uOnpmbR/UyJvwwUG7sY" +
                        "4L/oB83VVwf37fonP/c9ykGRBH2DEPPPtux7kIO/WOYbkD85/5+6fo91sJ0i" +
                        "32AVc/8vFfg+/2DLjn6T78n7QwpsLLi4v17j2t9u+4+h8H49/Ruo62aoNgsA" +
                        "AA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooProperty = fooClass.assertProperty("foo")
            assertThat(fooProperty.annotationNames()).contains("test.pkg.ExperimentalFoo")
            val fooGetter = fooClass.assertMethod("getFoo-RVb1_dM", emptyList())
            assertThat(fooGetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val fooSetter = fooClass.assertMethod("setFoo-Vxmw0xk", listOf("int"))
            assertThat(fooSetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
        }
    }

    @Test
    fun `Test experimental value class property in interface`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                package test.pkg
                @JvmInline value class IntValue(val value: Int)
                @RequiresOptIn annotation class ExperimentalFoo
                interface Foo {
                    @ExperimentalFoo
                    var foo: IntValue
                }
                """
                )
            ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/4WWeTiUax/Hxz4cOxlLGMcQkz3KcihkGYydJA7DMJaxxGAo" +
                        "J4OZxmQtuxLZyhYSirFVlhhbtsYuy1v2yJITrzrXdV51vadz39fvj+e67t/3" +
                        "vq/ncz/f52tuTEfPCwACgQAAQAxwfPAC6AFwPWttWZipvjxc2xSmr2dlLQfX" +
                        "P3gNAGzCu7tMjGXl+tmMZaE93b1PLBUGz0zPB8gZwWVg8P6g4mrLdSPZq1Cj" +
                        "7u7Ttus98p2d3e/mZ+dpAebGTMDHPNKP1Y42OHdU5v+4PeioMG6BGHl/b5Q8" +
                        "zBdji0AHucm5ohGBgeHWlMAxa85D8qL8ZduTbz3f2DH3cXisXB/0lSpIlMyH" +
                        "Sdv4CxqSxNhAr/pVjdBv7aqK2sen5aebGRjZHQIiBAyVMATm93FNMYjRU53B" +
                        "bRsKeaFrK40TU6sHwfcPDnYSztOMv0pjlR7BjF93bbo94lOLtL7izhqrPXGG" +
                        "qt/oNZryJ3U7+3KwK1DR8MSbWgTQLVBdN3/F2OQSZxQvk8FdseVVe/YXhhu0" +
                        "2ydfJF6Z8s9Kdcyo9IqDvpgvzT69/WAvBBlS4FQ7WFWs4X6GgCD8h66rZpxB" +
                        "Pu52JO+AknWsAmvIr27k7gUJB0nvvH6FFWFCxvIrQ1AUE26B16ElvZmiYML6" +
                        "+mPDTJ3CXU5C16Hf7nOF61F1y+vN/tIaETQMI/6Oo5NgnTj3ghxnICyDvxEG" +
                        "WRXsWQ0Ib2c1ZLUKG4y35sBjFU5HlsFYhiptFeLuxkp3S3WQeAyyxRB/DCkV" +
                        "mwJUModufaHc79JEJV7ZDv6CRxVM+1zE7zgK4p7Xe58VGACRiaGi9phQzoeR" +
                        "GuknVNf2JZKR0XqgoYCJ4HH3kuXhLG17y1M1aQjfZo8YRSy4WWTgtxqB5EC3" +
                        "E1K9peW3lLDv4u3cmYvqnEJKUu9NeY98PL2S6XsHrK889MxOxdG13Z9tYMNe" +
                        "46IfIUZCjs88OXygqSL6Is0uO80hJszfccHVYeHDe5yJ6CWJDGMuKk+1gafZ" +
                        "QE/Bk8egdlMO91CcLFtRHnQEK4bZdlWWwQ91kAqNIO0oCZvhVtfS5uyGz8NN" +
                        "8IevScXszN4DimZpEA4bYcl71KGCwa2w4ltQC0l7O1Jx5RM/ftAjyz3rCw90" +
                        "J1fC09TB+tHZ5kl2WGqxTxYF1RY8xFtWlhbn1elhr8OerCtUbjHppCKIk/Eb" +
                        "vEKp2hvl1nEnu9XbUmQux+vVcvjUrwqW6zkWxAd6VRGz+ZpY1WPa9Dgl4DH5" +
                        "DeDU7Y45v0SO980bamUWZp0b+MVpAbD2qQ8ELAi3WY8UW1WSz/UVeWcZtgXM" +
                        "DI4eGRznKheZoTg1dGgmsk5+DHCefcoAPhfFCklqYfb9tG840bxFoL0vdK/0" +
                        "Kc+41nIgERNlEtK1/Ivlf3beM+Se4t4ttQhyGa1+1NTcpAvBqAax/BEdsvA0" +
                        "Zg3Pv71HzVdhFDlkgokGJCfP8eWLme4QoUnN4Q2giFuNwJI6Q48P9IHk2Os7" +
                        "2MQk1dBVKEhezt8JOD7f6dptFn/IvjeCI29WzsmlLrPRiHfj5TIu0fnWDfey" +
                        "7BjoU6bpZBiHa4k8V0E3mBsfCbs+anK6KnPNVHpj5MPaJdTr0YjO19NcWssl" +
                        "8rlxhxF2u6hoXc0q8Yvbs6Hl3NiT5veaDX4PP9mrudlbobcvdy+7r/zO0ms+" +
                        "HXWtYYvBSlIR5mVmvNn6e770qCA0GvESNzudEZVeK6faoUZUJarXrXmu9tcH" +
                        "KCw5U7WHW1TM1ObAe6Jf3edCPCpNjR4AWGL6mfuIHHcfPay/W4Cnj5svBoHW" +
                        "9/P7y4Tw5ppmtNqc4YFApkXQLu6mR/WDrb5RzzxmSFoOv7VgMcM55Myorq8Y" +
                        "tX/vvgxr5ZaBwCJ3ik4cZXksdHOB0j22v/lLhOsyt4O4+Du2fcfHHxWSxVUS" +
                        "qcQcQcJZYuJKTMw6bW797+SJO8Ku4tqjWSj8cC6XPp6qsU84ezv6eplo5gXP" +
                        "x2Jr7V6QvM99u5+jK5z2bSYTCWUCV2EBv0ZWQpIPpMksdEOhSNxEHblhQx4a" +
                        "PZ62gleeNeVvKVVJh2CdSLqS7GDz+5sc0IMUgQlhbpYnntRKh67KqldbS3fm" +
                        "ewoNbDN6UG/qpokVjvtLduhWTVC4T+bGHFO4rn/5i42H4iXQaThLnKLZb4nw" +
                        "E4+CwYu1JOArs56RO6LomLLKZrLVxqihc1Yenpb0Z7aS0Zm0JP8KogSkclyn" +
                        "tZ2UYCN6eY7fhdKuftY+B5jVUeusN0qadrmpkW/nQ/eNESLFLdiOBgDgov0Z" +
                        "I+7jjP7mYmMO9+ax4NXcoSDdlYZU4SWSFiUplgN6Gm9mdfzzpPA4PrFWMNLi" +
                        "98w0BQqycIs8KBN1mD+xJU/8hfnqYgL3mnU+l8jOZMjqyLPFkbW184CGVsmZ" +
                        "bAtq9GIeAmvHK0fkvNtpLOCuJEoQvYb6MFhXAXyaq+RQOQYL2z/1xTVyaDJG" +
                        "YPjGDFL9gBtuQF8vvAaJHsqzQFQpcySkJIV7zglF8jvZN6QaWcmyAV/KumSm" +
                        "NhHzDrRmkFol+w/ErnN1o7SaxkYDG7RdtTQ4uYKoOI+YjjDyl+D9zdnC7U0h" +
                        "CgrtcXVlcKkGxZSSuqKG2x/Ybsh5vgu1/ZNUW0clEhfRqd7s6Xv9H+dNEt7a" +
                        "RT0Al064zY5DlLKkTGsPQM7GC6GIfNgthylSi8G5tFO+4eZ0ap0mv2n1Vt/i" +
                        "MybHctoesE0JCZi2hZbLTaIsvV/BDq0Un6na3PQsxPqibmdhSm9bF5FNyPVj" +
                        "y+uKFcbSYmgdBvbmSXw7Ec9WozX1Kbok+0HMoptw9gzDfvoGNGTX0U7xDoKx" +
                        "LkHwJpZTvNkLlG/Rbg9qbHvephtnIJigIigIjyMR8ma8sDe+5QIwP//pmiPq" +
                        "Zj+lLvoDdchFN3dEEBoD8/FHB/51BeAJvaatFzjbmnz6jRgYDl+08DBpekhs" +
                        "LHRGQOBgyfSt2RYZDKSgq8azNnrk44cWpvXc2DeZ/MFPpnpMGt9+oez0LoIi" +
                        "rmEY1AkB7wo2WoUkYH6Ga1XmbU4F0BAwvb3xs9Ts22Ezmr2pyufY0bjVSNYx" +
                        "v8KW0kM+0zDLpZeGPBLqoIrH5XQ1sYeif1Dzk/VOD+hDV8M4PRLevXxoeI1H" +
                        "SSlZP4BiX+ZD4C4YNMZFELsizleftRCYYFauCRWuflbFJlux5s1jaNPng6b3" +
                        "ioJAaEIMPfUDPaXmkorwZ7t5LoYqW23VsU2tPy1E5yCzeYpWjS2RgYJ6pL6b" +
                        "eEFoha2wxig+zqonc/fKbN7zHqjm4pMuHlWJ83J2jj0yJ2f4hSend/MDHAPC" +
                        "EzQ/XRrVcrjPdIEOLsqkutX/meErBKucPKTqEYRSmp9BEDqqv7OhD8LTV87b" +
                        "D4P29HXy8UMGod1cnZ2d3Y+K3sWUUcrcpc8F8A3wp18byDxHnfzfgh8NLS/g" +
                        "f+rHQ+HX5Pn9+Kcc+qPKcXMHfadw45/j5I8ix91H5DuRL/T/9lf4Uev4neb+" +
                        "TiuX8f+514/9x3GIftefBvzX7+BHsePvX+g7sZMsP+VpbszA+HUZ49EsOzoJ" +
                        "luXr038BJPahNzsMAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooProperty = fooClass.assertProperty("foo")
            assertThat(fooProperty.annotationNames()).contains("test.pkg.ExperimentalFoo")
            val fooGetter = fooClass.assertMethod("getFoo-RVb1_dM", emptyList())
            assertThat(fooGetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val fooSetter = fooClass.assertMethod("setFoo-Vxmw0xk", listOf("int"))
            assertThat(fooSetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
        }
    }

    @Test
    fun `Test reified inline function is not accessible from Java`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        class Foo {
                            inline fun <reified T> foo() = Unit
                        }
                        """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfHBvjmX3YQqN3tJ3Ocd62whsrT3U8ufGu4zMXZ" +
                        "2eLVEJKY4pC1OKbolkreLt9Hrh+XvbqoXsfxv0FYiD3l64l+pzN5lgzab8/M" +
                        "nmmen/b9+fr39gw2h4XbZvkZydbN8j2svFQrgefgzh0CD2OWXP7w+IWN5Gsu" +
                        "Gbbt6sEr+SXnzL3ryJu6w85rhcGULzPPSFx7vL911xHr0K6FV1/v7/gv/kZu" +
                        "Elvz9WlvL29burFjodz5KfNOm9TMzzAUjJrAER9jsUXTeFkWT4f14j9LTQLb" +
                        "L6TMXxj9hHe++c7zzUrfd6YzfO/eE/mEfc/huOnr9+2YUhnOP0f3nmbu7riJ" +
                        "7E/Vqqu/3jQWYj/D9f3lzC1/Ls9clr43cHJLovK0zE+JWyVUT++TOKt5ueNq" +
                        "xBn2JE3fP+qRthMvh7y4OmlD/aKw04VFKr4LS/d4bY1RYZ7pmbTPK3hp6zSt" +
                        "sxv4/ewz+JY//LIw5X2wyKzQE9O/7zv4O+s0/4ldZic/Tsh5O6tBq0ZhRXLv" +
                        "qjtm9su51N9b7K2PtpS5XTV7wsxy5tdhR3aKHVQ+ve5ibc5F25zJ9Z5zeKVE" +
                        "eB66rHDPcNnRGc2yUXLitbmKEW1qUc47JP/wRM9kzBa785cXFPVneP0ddjMy" +
                        "MGQw4Yt6aSCGp7zcxMw8vez8kpzMvPjc/JTSnNTkhISENCBmSfJj0whIupDE" +
                        "AE5WX5X27BUG6pQAJytGJhEGhOnISQ6UrlEBrlSObgqy64VQTKjHmljR9SO7" +
                        "UBpFfxUTXh8HeLOygZQxA+F5IH2NCcQDADRtczu7AwAA"
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", emptyList())
            fooMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }
            assertThat(fooMethod.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)

            val typeParameter = fooMethod.typeParameterList.single()
            assertThat(typeParameter.name()).isEqualTo("T")
            assertThat(typeParameter.isReified()).isTrue()

            assertThat(fooClass.methods()).hasSize(1)
        }
    }

    @Test
    fun `Test extension and suspend reified inline functions`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        class Foo {
                            inline fun <reified T> String.extensionFun() = Unit

                            suspend inline fun <reified T> suspendFun() = Unit

                            suspend inline fun <reified T> String.suspendExtensionFun() = Unit
                        }
                        """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhevneodLOwocPz/Zmmz2aoODAcb9I9oMLbwdugL" +
                        "JSk2tXVdFHqisFx1zYaUzd6paZp9U9jtO+Y77M2o3KDRX6dxep6qQuOCaamr" +
                        "ypYt3/38uq1tf83/j0+1GzRtEjq4Z3MX796UqPejTqRHcWPq24hl2ZXSzzZv" +
                        "1d2tsyhAcsucx5ZK5xcsaw9/lNGYptCk4eySYzVn4d4KmSmqG/Zq5/Rb79su" +
                        "UMFj+/KR/rtJKyzvuZVVtCaabLeJtZE0nG1/OPr0yYxK2bc807X3Fciev3Nr" +
                        "5aw+9Tez0nemHZx87o7mZw0hka17Mqdrq8ZPYbpem3d1zSrfPh7nl5cNZ7v2" +
                        "LOa+y2ZV2LfQ+OmSuOVS4Ue5eGI1Z17mdL6eKdV8kHlfbvXrhcdDdkXEO9xc" +
                        "/7OxwGb27OJ97k4JnI9S2v2fn/jwak3VX7lp0tYlFZ63Z5w1b98u97nzkVBz" +
                        "nIau7vlpz55J3pJiKIw56MAzvXDqQmWdMx+3bl12qq709tbUlLZGe90pC3fb" +
                        "Ve9b2MHt63fQXvTs39fO3+JPzbCJm3BM9auszLRl2845fwuOjng9bQPH0pDy" +
                        "kF2rfMwMbzbwxbZ+2d0nk+oRNT+hYvP+8osBk7oWqnz7zuM6nal2l1ftbhn1" +
                        "79/VpvJU3/j0dq7bsqP3Wm6tT1FqnrVv//QMz03q9+/ZcfreSvJXupUgFWhk" +
                        "NfPr0X8ua2Y+TJinaDwzJbHudEngS0GJ9RxVcs/+lM4+5zExK5JrVXZZ7s2o" +
                        "4txK5z+bnq6LMHXve33qxqTNDyOVVkzM0fnpuDZH3+LShe9KC2JX3NLmKz9R" +
                        "fO1zjeA7juMsIlWdT/SdDr5MXPTNrtdnJtdXXeX5C+oVHA+abI1aETJhqVnd" +
                        "gwYJHoWLnWGXNB4+OX6l3VT+7asfPXKlywQXTA5kYltxPfNO9+cU1e/MIRqq" +
                        "F92nxVis733wd0dAu73S3pbsV2fcefblK2hPOacYue511Nw1shNNlxT87aiZ" +
                        "eMLcIER9uoc858mfak8yPOQU+T3l1DZVNdkIPauRcF553OOyubDX9wnL93nY" +
                        "LZ7y/GzoCtYeUYO4BVnpHkdcGCfv2Mp17OX7p9+0oiz3Vu04vLDwS/uh2D7u" +
                        "mFee+w4afhEGZbHKtUJWyswMDL1s+LKYNBDDc3huYmaeXnZ+SU5mXnxufkpp" +
                        "TmpyQkJCGhCzJPmxaQQkXUhiAGffr0p79goDdUqAsy8jkwgDwnTkrA0qP1AB" +
                        "rtIE3RRk1wuhmFCPtVBA14/sQmkU/Y+Y8fo4wJuVDaSMGQjPA2k7sA8Ap95Y" +
                        "KCMFAAA="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val extensionFun = fooClass.assertMethod("extensionFun", listOf("java.lang.String"))
            extensionFun.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }
            assertThat(extensionFun.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)
            val extensionFunTypeParameter = extensionFun.typeParameterList.single()
            assertThat(extensionFunTypeParameter.name()).isEqualTo("T")
            assertThat(extensionFunTypeParameter.isReified()).isTrue()

            val suspendFun =
                fooClass.assertMethod(
                    "suspendFun",
                    listOf("kotlin.coroutines.Continuation<? super java.lang.Void>")
                )
            // Check that Object return for suspend functions
            suspendFun.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.lang.Object")
                assertThat(modifiers.isNullable).isTrue()
            }
            assertThat(suspendFun.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)
            val suspendFunTypeParameter = suspendFun.typeParameterList.single()
            assertThat(suspendFunTypeParameter.name()).isEqualTo("T")
            assertThat(suspendFunTypeParameter.isReified()).isTrue()

            val suspendExtensionFun =
                fooClass.assertMethod(
                    "suspendExtensionFun",
                    listOf(
                        "java.lang.String",
                        "kotlin.coroutines.Continuation<? super java.lang.Void>"
                    )
                )
            // Check that Object return for suspend functions
            suspendExtensionFun.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.lang.Object")
                assertThat(modifiers.isNullable).isTrue()
            }
            assertThat(suspendExtensionFun.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)
            val suspendExtensionFunTypeParameter = suspendExtensionFun.typeParameterList.single()
            assertThat(suspendExtensionFunTypeParameter.name()).isEqualTo("T")
            assertThat(suspendExtensionFunTypeParameter.isReified()).isTrue()

            // Check that no other methods were created
            assertThat(fooClass.methods()).hasSize(3)
        }
    }

    @Test
    fun `Test reified inlined accessors are not present`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        class Foo {
                            inline var <reified T> T.foo: Int
                                get() = 0
                                set(value) {}
                        }
                        """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFheXBsXmCzmK2Bozn950tOzl9C3b6hS0Fgh61Rhs" +
                        "M4ua7KaUdUko6VA3991HIruyZyz8OHH5P4H5tS8nd/K3XTGvn/LxHFPnNq6b" +
                        "1s/r5hZ/f/fnz9e31g3uFQcmXNp3aWn/Xb9Mcx7hBJ53E8sfTa2Ku6C5N1f2" +
                        "Z+7choQN21IUvpo1nxCX0jySaNmheFDgsvySpBlr/3pu8eSVjRdOijh1Xc8o" +
                        "T2wVT3b0V/Xpxrd/Vtl6nX+S8s7AXDVRxPNZT8i7Cz8+T7f2DPJdNf2/YqSu" +
                        "icS1/+slnx5fFDzrR+d16R88L6PMr9hKTon7fcv6wxb3qD8/XC7wzTT0THZw" +
                        "LtP85ym13udFQvPDvxk7XnzgKd9hLWV1x+zAssunetpE49KsP34+L1b+M+5o" +
                        "7/pPRTrZNxcvzE6NmLh/i9Y0bZ8PH/uD//XMK3++fHPIvKjem5zXzx17s+S1" +
                        "xFwu41dKUTyrL8tl/GqP9b3/zLXC4fP5qykP9kvKbP9Tdzf7a83mfRvefpWK" +
                        "i5eqF/J5d9rvwUvH+Rs2y/P9yq0Pn3Xk1D2FC59u5HuorvxUKdBy67IRj4jZ" +
                        "gZdSM+YvEHwRNmGvd9zJNc3nKj5qvzFmkvl+IipwgrK/mamegxDfg2WGzoJy" +
                        "pu9nbF9orDavS7/+q5Th5erJn+b82zxD2Xtq8jSOnR7BqYqvdjvt+Rwx48O+" +
                        "RTskwzrebube8rFjm0em3z+hi10v5i7bkuSUfaFIxUosYslyiYXWO/zTe7PW" +
                        "CUUv4ww86iW6OjTmsJTjmvBCWfNZCV3H9l890tO0eEJoSe5GmcbbXJapjzRb" +
                        "K1tb74PSYebKi3IeTAwMK5jxpUNpIIZng9zEzDy97PySnMy8+Nz8lNKc1OSE" +
                        "hIQ0IGZJ8mPTCEi6kMQATuNflfbsFQbqlACncUYmEQaE6cjpH5TJUAGuLIdu" +
                        "CrLrhVBMqMeac9D1I7tQGkU/OzNeHwd4s7KBlDED4XkgncwM4gEAK5l3LUgE" +
                        "AAA="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooProperty = fooClass.assertProperty("foo")
            assertThat(fooProperty.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)

            // The accessors are not listed because all usages of them are expected to be inlined
            assertThat(fooClass.methods()).hasSize(0)
        }
    }

    @Test
    fun `Test reified inline function using value class type`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo {
                        inline fun <reified T> foo(iv: IntValue) = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VeTTU6xv/EoaMJSZjS0bIOjOUQleFjBk7TZKayxhG1rGM" +
                        "NYqYudaJrkhFt5As2bJd20yLfRBhpMkSudEgFLnF/HR/5/x+6tx63vP54z3n" +
                        "fT7PeZ/zPJ+PneUOPgggKCgIAAAM2B4QgA+wNsMaa2NsUAhrYxsMyuwkFm6N" +
                        "2uwCgBVrZreVpTa8X8RSW6OX2ffQATl4YOJNENzCWgtj3R9SXO3w3kI7UMOC" +
                        "ydR0fN+L6OxkTr15/YYXsLMECZZLqpcbbBU4vAW7H5aHboHsEUxGBPh4IjD+" +
                        "ZEe8b4gHnOCLDw6OxvYEv8SKc5v/QpxxlH/h9dxJ6JnY+fmoQX+1gjTVfIz6" +
                        "qQBZdBJMBNrar2/h+8KpqrCdPYGYYPALiJ4LipFB65KpQrOp9ET8yP7O0LYl" +
                        "ZF7E4nzLq/GFzdDbm5trV47xsFuzwOosMjuKQL/K8qtzx54lglOMXx0YRbV4" +
                        "j1z7MrqaeyaUIKiD3v28Di/oEWxomj9vaXVaPA4CMr8F4yw4iz5BL/Guyj9J" +
                        "OzsecDMTl13pnarx5E1prubq3fUw97ACl7rBquIjxANUPPXtju5aNj8i9epl" +
                        "yIAuNgUJDlPyaGbOqJxT9cnrR87voWZzWtHQOFDsDOTco+uMHqQVuGu5abIe" +
                        "eUuc2s0lfWpARsXVc94zAtSPxPDwswJwI2OKJqnEgj9cBTHZ0i0Y5QXZ3oWg" +
                        "6HYwGnwycpCGFaOEIzUvP8DsHKp0RKbeSlFnqnUkSZrnwvAXh3SLbQC9G0PJ" +
                        "Gz23u408086uhm5QPAsm/E5Q1nCysQ2NPodkBqDNCRF7nckR4vcvH7m+W3/x" +
                        "s0qGe7wZdCjoVSibWMIZvmns7LC/NgvvzzifqBOuyFAY+KVWJiPYY7daX2lZ" +
                        "sm74FM2JKFRY7xJWkpkz7sNa1py/4f+7Iurg0J9OejhCe4DIwJLzkRMkaqIK" +
                        "XMouI3qAXhF/gueTKA+XHBmAmyGcm5mbjbXae1ol23LXqGS1uZftQG/Bw3Jo" +
                        "u40YMSJWW6QwT4MVDiOvEg5qUYY6ku5ZKLd7qpwafkwoZeQ2/T1Mt77flVQs" +
                        "KuQzoGObpSx2ao9qzuhQweCHyOJkDXtVZ6ek4sqHJGlokcM69vhd07H56CxD" +
                        "RVR8rl26U/hosd/NHs+20CHIgwdZqd6d551NRDNM5crsx1z0ZGO1SINne6rW" +
                        "RyRMiM0ejY49WmdoZnVifo0LsmVmuAJasHdVQq4UHWyY2GYmrmKdmN+kmLna" +
                        "MU1KE5tlLBk8sLftXKL8NSGjaLx/jhoOjV1pdIct6CLu+CtMOUR+ELwRGs8a" +
                        "ZO8qU5jscWnqMEoDjy0Hub6u4Vc8HAdWTn8k5P/xM/oV4wOV97ZcTmmNJPso" +
                        "JziBHGcV1s0Rdni7Nst/Z7/Ep1L7ELeR6iI6g26qTNYP2XkxPmymJnGRIr26" +
                        "PpqvJ6DABWH2BmVkTEvlw2zWEjTSGdFN0JjkFsGSevT5Ob7g5pSotfC0dP2I" +
                        "BQ0oAh7gIsh+00lg2tK4ouus2OaVyml4JkeEZx+TAs8+vcO/frhv55o5qmdi" +
                        "h5bAcF2CZCD0klBL0R5CEd0lUOuCjfoSa27xtGfXSExn18Suo5wSxJ1UbozT" +
                        "J894U6OqfSdWX0eUSYTL2+UwzH+Nlu8zWumrMPsMz8l9Vvb7uy4pE8Ojw/aD" +
                        "lUmF5Kc3aLbvZ6Wux4X4+uKfxr6eyI67XgfX7zBI0E8wrF/0WuhvDEK+cx01" +
                        "Hn6kZ2swrbi+96v6HKd5ZhnwAcA70M/UR2K7+qBIpP8KD84eR5IwhhgdEO6u" +
                        "cm2svmhXWlN7/0GiSaJt7aEi1VSf+6qZHGHbHb6BOzmyxOMKiWKbYhugo15R" +
                        "SL7xL/ZtR5JR74irYwfWxoLpM+195ePAL09x1CbRa2VNsyGa7YG3eAjve1ud" +
                        "sH8rzTn+Wtv7pLaX4VYh5q78ERZX81BuOp06r0QBm1rQM5PwRZuku9FU9hn5" +
                        "cTHVuLohzJW7oV9exdwhPwvHco9xNycfz17zWC+Nim2LEoC3gymRiOFomVM2" +
                        "JtqGJsLFDBAhuc0V5Z4K7Ep4un+AXRxveAE86duwsKwU9ufhmw1snRgEWae/" +
                        "9UV1WwkHE7nZr8pa8evTWPL27g4lnkTg3PVePuxMizh0aTGK+5h4MsnSgJaV" +
                        "Q5+SOTzPPOaLzVZ3JNzjT1b9AyULLrkU9trcUHzD/rzUCujYWy9n5vN6SV0t" +
                        "lgekRVrEebIC5hxdVTo4VfsW5KbWs1Gnc0GjMf1ZHgy/dlBB5OVk5UFazek8" +
                        "dLexNFpFfVktyU27OmUfxSggUn0lcI6mQJvWbjjFbEAXLkZTV9QDSenVknkU" +
                        "v6vrZkBVAE95jPyZcJOjunE39N8tmRUXedy/OX3oY2aF1aiy1VXljn1ypgaT" +
                        "uLvc0MuHAn+rts3lvXbO6/Fy5dxU1RXNegtxVhSkcpjvpNpkpGSZrifukWN9" +
                        "eowIv+3kPRMbGFf46xBBixbw/LwAMMP7syGS28L/HNQP7+UP9yGRfb38XfxI" +
                        "7iG+HgRXV1fiFvjcbATU7NyeuQH/2ONHpaZmya1M6X/skYcXAvyffbt1fvXn" +
                        "b+NHbv09y/YVgH7DcOnHpvs9yfYWSHxDssH3b7vzff72b8p9k28C+mnb7Cz5" +
                        "Bb4+49s6EB4AyAV9vf0HJXxebMgIAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val kotlinMethod = fooClass.assertMethod("foo", listOf("test.pkg.IntValue"))
            assertThat(kotlinMethod.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)
            val typeParameter = kotlinMethod.typeParameterList.single()
            assertThat(typeParameter.name()).isEqualTo("T")
            assertThat(typeParameter.isReified()).isTrue()

            // No bytecode entry for this method, as all usages will have been inlined
            assertThat(fooClass.methods()).hasSize(1)
        }
    }

    @Test
    fun `Test JvmSynthetic function is not accessible from Java`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @JvmSynthetic
                        fun foo() = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfHBp72YjYUsTW/7Fe6xupr5PzsrQ9CCpcqrVAI" +
                        "WdXkZR679O2Kl8lKvlp6v3X/NWofbBWZXjelItj1iMO5vc/vzLljY2cXf5/h" +
                        "TcLUCVs3brxY+9rM6qIVs1KL70+Fxdt88hZ3Rxmvu81j07R/vVAqP8e9W1Wq" +
                        "3SZ/GDRTODSrl/MkLvpTvtg2rnyxqWJ4XubtMM8SseibE8KKXEvkbPL8+dxk" +
                        "Gxx0r8lPTXY9JxXdcmiBPJuSPpMg369GicLOE/y/bi/ycH/5XX3+y5XyVd2L" +
                        "ZhRNyy2S/nmndnPqz5jjuwSF1U5rdZ6vnht1Z++0rWbawamx0992vdE6Xn/k" +
                        "gdf3XV9/uFhuKrjpvebVEdFUkXD+yS7RmVP9eLYp/LiVx5F3bP2BHQd0isof" +
                        "zLhXcfDE5w17Trzgd21UPfbmO1OPEsMhmWN1Z1sfCn/4k+/z1/DS9omvTzuf" +
                        "SW59rPf6nfmx1R+8ZmgkbmoRsPQzDD1kkrZKoTnyTOhEMw6eZ7w1zYZ++8Dx" +
                        "FencsaaAkYGBjQlffEkDMTy55CZm5ull55fkZObF5+anlOakJickJKQBMUuS" +
                        "H5tGQNKFJAaw2V+V9uwVBuqUAKcFRiYRBoTpyOkElBhRAa6kiW4KsuuFUEyo" +
                        "x5rC0PUju1AaRb8+E14fB3izsoGUMQPheSDdzQTiAQB5dIhhcAMAAA=="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", emptyList())
            fooMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }
            assertThat(fooMethod.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_JAVA)
            assertThat(fooClass.methods()).hasSize(1)
        }
    }

    @Test
    fun `Test JvmSynthetic accessors are not accessible from Java`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @get:JvmSynthetic
                        @set:JvmSynthetic
                        var foo = 0
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBsVmCzuK2Br/nvnW7O6SrctWxq+NutWUJ1Uj" +
                        "oFvnlKCo5cLZapM7ydRv8huvtKCaM5Ptj6THs7h+cHSS/xv4aK6v3BaO03uf" +
                        "z7N8fm/n4X+//j5Wb6i+kfwhVCn0R50Fh3TkswZDluUfI2X6HpUI7Hs1T832" +
                        "6YGEDf2uWleU2ZL/L3h7uOntwZbDCavKnYxEZtebV3m0OS7ieNrW8Si6N/JJ" +
                        "suhRJotrHkrThYrtrTKMLY673Kn0cfY6m7jMTYk3XkXr3XGtZW01NWkX1s7k" +
                        "Y5V1+Nt2pF5g1yrBV/KGW55t5u0P33G8+eeZx+vY9FJXG82/Wtlru/mc/U2b" +
                        "B2v3Hlj1aVbkdmbn57971Xsnf+53tdKt+hx5teTtgZumseYaL86VLnt+s+Tu" +
                        "bmdX87p5X4Xbti7zmDz9cqH5zD2du5WTTp1dI95/66PLiyrPqvWbXy6ZbtKi" +
                        "ty3qckhn4SWxoAsvL5ucDVnjMs1L5uLuGT+TYuyFFZsN9poelV3ltNco9fUH" +
                        "6zv6LBZlmrcsTxZOXrRfYAEDP4dXxMmZ9WfjBWv2JWz7l623cI+JvYLp9VeH" +
                        "3+Zz6PXF7TAX72q1dn+9pfne07B1+xtvT1FOMjtRuJJZ+VBay2lBrRtKHVkt" +
                        "uTEvGzxjjm7ls1m5//WkOpE1zxNCk1wPTdfwnPIfnER064vDHjMyMBxnwpdE" +
                        "pIEYnkJzEzPz9LLzS3Iy8+Jz81NKc1KTExIS0oCYJcmPTSMg6UISA9jsr0p7" +
                        "9goDdUqAkx8jkwgDwnTkpAlK/6gAV25ANwXZ9UIoJtRjTdTo+pFdKI2ifxET" +
                        "Xh8HeLOygZQxA+F5IP2PCcQDAJi7ljzjAwAA"
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // The accessors are listed as bytecode only since direct property references are
            // expected from kotlin.
            val getter = fooClass.assertMethod("getFoo", emptyList())
            assertThat(getter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.BYTECODE_ONLY)
            val setter = fooClass.assertMethod("setFoo", listOf("int"))
            assertThat(setter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(fooClass.methods()).hasSize(2)
        }
    }

    @Test
    fun `Test JvmSynthetic function using value class type`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo {
                        @JvmSynthetic
                        fun foo(iv: IntValue) = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VezgT6h/fNDY1l1gml1DusQ0pl1RuYzS3VMslZjaEbTHX" +
                        "Q8dyCZnUcVwScjsi6aDixGxd5E7GkEstt45iSE268NP5Pc/vp55T3/f5/PE+" +
                        "z/v9fN/3fT7P5+NkvwUEA0AgEAAAoALYXDAACIC1djXXxTigkVhzBwza+pgr" +
                        "Aote6wAAlrFdnUftdRF9Yva62j1dvXUuqAED3nQIwg6rg8H2hVXecVm00z2r" +
                        "bdfVtffEYg+yvb1rcnpiWgjgZA+G3JbWum28MeDABpx+OB6+ARoxlIakBvoh" +
                        "MWTaCXxQGBFBCMKHhsa6doeOukquM18hT51QeBbQjxN9KuE/HzNA1izLUC/F" +
                        "aB2nytmmqojBW/qM7IKe4WrLW8d4SB5bWETcIyRup60+LUl0lsFKwQ9rtIc/" +
                        "WUKVRC3MN4+/4K+FF66trVw6DBxryYZqDdHGYgisK0PB9b6u7iRomvm4wQi6" +
                        "+czw759HBAWnwgkQPdsd/fV4CDHUxLJ03v7oScl4GNjmmsoc3038ke2SkEDh" +
                        "UYb7C2pelmduzRmG9qPpqoK9guLVCN+IMq/6gdpKU5JBEj7p7y2d98aEkYwr" +
                        "52Ecfdc0FDRiN5HZNaPmoR5Y0oeaV0zKnWuxhceD6TMwjwc57G7UUWjH26aX" +
                        "Dahrkkmd65QP91Ex8Q1zi2yqlmkcUHiI6jn8XNmCQSq77g3B5Mo2Y1T5cj38" +
                        "kNhWqC30WPRAuqtEYiRq7/lbmK3cmhMoxrU0rS7NtlRpmwIV/DmufqUDwPAq" +
                        "9+KX7sJOM78Md0H4l0S/Ml6wVeKKpxz9fmPg/p0cODM5SsmNFiV547xpzg6j" +
                        "hU9qmb4J1nBuyHj4GOnm3GCeuZuLxr1sPJntn6IXqczexTl4b2dmKHGHZm9V" +
                        "9UX9yMl0HEm0vMEr4mZW/ovAobd756+Sf1NG7+P+hTP0JLRSxThLbqZWlKQU" +
                        "NYSMU2Ysh/VnghXwgzhwnRZN9ZwheMy8nqUfVTqplmu/fUT6jk2AI6enrO42" +
                        "vNVBghRF1xUrL9EeilShCQj7dBK5bal/2Km2+qkdH3xIqGIXNH0cZGFvdKRW" +
                        "iosGcvQcs1Uljiuq549wywbeRVde1HZWd8OlVtbUUWThFS6rrkeKLZ/Px2ab" +
                        "KKMTCpwu4yJHKoPzuv2ehHNht25lM860+7tZiGdaylc7P/cylKPrUAbcu2tX" +
                        "h6UsSExi44lunVPp1vUSwY18uWprz7L00DO1yQUyLKhJyhNrSTVsSmmTcpag" +
                        "bYqSITHLXjK+5ezYvpT4irdT2VzjdVIknL7c6KvC10cWkXdNukS/g1wNTxga" +
                        "GNtevetlt1dTm1kG9PnbEO+Ju8LKB+KhqpcfiJLff7IdZ79LEiqUz6+6Kz12" +
                        "aC40mRZ/NKJzbpvL3yuzwkUaUh+qnMN8hu9UsNgsS1WaUdjWcwkRM3dTFhJl" +
                        "BasjpYYiu9bBGKWQzMwpmVIVh5Vk7cvs2CZ43MVmyM0GW//XoFBmWsxKZMZl" +
                        "oyi+NhyJoHpBxqbbCV2O6eviq0N05nLNFCJrTgy4pysRkXtyC7lhsHfrig26" +
                        "m7dFR2SwPln6LPxX0eYKRUIFy+uszi8OWktDrxdO+nUMx7V38LYfmruJLGKs" +
                        "x+E++CVYmtXusRJMRFVLRSo45bNtTscq9Jot9/5p/QmRX/C0+rc3HTIWJocG" +
                        "nQdqUstpj6+mOy7OyuTEhwUF4R/TJ3i58Tn1CKM242SjZJOGhQB+X2MI6o33" +
                        "iPngA0NH4ynlVaWv7nMk3S/bGAQAvAH/zH2kNrsPmkL5r/F4OmEp/UckY+vS" +
                        "FVsx7uXTe8ScmhG6UkYMHshCqNQWDVQ9MpPRr65HzHuMfoZt05soZB/mgSbi" +
                        "iqmTNiokRUmI0vPmKAPKQYPD5859EeHtr5Y+o85VjzY1STGJOi2y/eEx4+w+" +
                        "HEmjr24twARFNAItFHRk/uVIFb90Rc4jEminKW03w+AkzPNZDL5U5e3lz6Ta" +
                        "nKZAXbRHmKySGqhoBpQq+p6veYDwu7Wjy54LHNvw5PaxlzVtFi1CDVlMn6Kg" +
                        "AZ/9B9Wb3HTz6P7JRxJuEpVj7K28K7gxfTjd0WJrifH3NpcMLskX9cNuuYxu" +
                        "8zzHXPDyvzvl2o68sPaRqQmuZq1Rn+4ebcYKysUvOQx/anS+lmuV7lfh+UaC" +
                        "ZajfNFgSkyMVfl/bvrBH/Qvto4ugtWoufJmuoBFSGfTZHXp6qm5WeV5Tfu5w" +
                        "IRGN5VgFtDwDn8qSD3AtCFCwXAye14iUO2Q4CbS5bAZh8Rm9i4d3zHamnVYx" +
                        "aXiIG4VdPw+AvLkhk2r4Qut1FHOdQcYfrMeQ04RHH+4vYXOL16Sql0bxxAuK" +
                        "eAmHtOi0cdlfjB7B/Aj8s81gjr8360Iq+nrfwOI+BMzXfrIYfbxEsO2rEB65" +
                        "vSpvBwIAdKGfCUF+A/9LwWB8ABkRSKEFBZC9gim+YUFEgre3N2kDIB8HEU0n" +
                        "n6c+gH8i7v3uJqb0RqfsPxEHFIIB/s++Of6+Zuy39aPE/Z5ls4zh3zD8+uPg" +
                        "/J5k8xdIfUPyBfRv+v++f/Mz5b/pFwH/9Nuc7IVFvh4DbSzYxgV8wF93/wFn" +
                        "olWRjAgAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val kotlinMethod = fooClass.assertMethod("foo", listOf("test.pkg.IntValue"))
            assertThat(kotlinMethod.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)
            val bytecodeMethod = fooClass.assertMethod("foo-Vxmw0xk", listOf("int"))
            assertThat(bytecodeMethod.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(fooClass.methods()).hasSize(2)
        }
    }

    @Test
    fun `Test target languages for data class property accessors`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                data class Foo(val v1: Int, val v2: String)
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val v1Getter = fooClass.assertMethod("getV1", emptyList())
            assertThat(v1Getter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)

            val v1Component = fooClass.assertMethod("component1", emptyList())
            assertThat(v1Component.targetLanguages).containsExactlyElementsIn(TargetLanguageSet.ALL)

            val v2Getter = fooClass.assertMethod("getV2", emptyList())
            assertThat(v2Getter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)

            val v2Component = fooClass.assertMethod("component2", emptyList())
            assertThat(v2Component.targetLanguages).containsExactlyElementsIn(TargetLanguageSet.ALL)
        }
    }

    @Test
    fun `Test target language for composable APIs`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package androidx.test.pkg
                    import androidx.compose.runtime.Composable
                    public class Foo {
                        @Composable public fun FooFunction(i: Int): Unit = Unit
                        @get:Composable
                            public val fooVal: Int
                                get() = 0
                    }
                    """
                ),
                kotlin(
                    """
                    package androidx.compose.runtime
                    @MustBeDocumented
                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.TYPE,
                        AnnotationTarget.TYPE_PARAMETER,
                        AnnotationTarget.PROPERTY_GETTER,
                    )
                    annotation class Composable
                    """
                )
            ),
            compiledSourceJar =
                // Generated from a gradle project using the AndroidXComposePlugin.
                base64gzip(
                    "test.jar",
                    "" +
                        "H4sIAAAAAAAA/42WeTQU7B7HpzEGhTDWxvrOqBQGr6XkZikmM0y2LC8yzAxh" +
                        "mIkhGonLHSoUrxQilBFlG0rW9ArZxmuZFttYyzIMslXSTed2y73V6fuc5znn" +
                        "Oefz/f3z++P7tULzgACfpcSvBAR8I4FP19LUzljNHGOGsPop5u6PDyB540N+" +
                        "ggl/i1EIgZSfsGL/x5J9vf7L83/my4NJb2rBAMAJIQAA+l3ejERSxxHdAwPj" +
                        "7dFUyHOZ8M5JH/MnjIgIIYzS3oTxF/JkS7RGLu9zmhLjiB8OCM/Feate1kmz" +
                        "9jTbd9tveohbWT5YMrvbldLlmXhddXEe32WaWfQuqq/GC+6EM4pQmGmvDHv9" +
                        "fogbMBRYt872uslz/bEbr/B0cGNQe6scS8/Aza+3YPqfY3BFEMP5LNpBbbCo" +
                        "oLiYJEMwjIw1Wr/hnb4RGnks+2GKseCCeM4x/qMfLHsgB+zO3mtyitSWQGjP" +
                        "Mkj6D4yys5bhbZjakRDatK4f+x502oXKYfo06kRey9x9p9gZLDSOTnA+USF4" +
                        "fpBl2W8b1B8c5pr2pjeXTuD1SEzNR5QBm89Yr5RlABppBm5nPhBotJvooh4i" +
                        "f5xE+WiPiMwcEXyQcxzHzwdK6JAwYUpd2+65oLqH7rNxId729+UKtvloaCTk" +
                        "eOPSrkCyY05Amv/LvzlCzYV8wttvqBjw0G0QufvrfPtYTqkNorLm7tJI3pXQ" +
                        "dExPQTtbs9WSLRIr4e/TmC5TE1Fsfjyhq3yqsKqq/BwrqKBoRHPiHqi1JhZi" +
                        "gz6JZcw0J9/LV3nWm0zRFY/EackubXCW8j3EcMCoOjt9thbxvCntj2GycU95" +
                        "85ha1l61eAiJ1F/j2oI1sR/T2M2b+cbHE8cSsJFUzY9FS2G6U9wdVjwpjPno" +
                        "6KLkDNrpQuuuc7BJrdKetapeHcjRWqnuFZVUoci5dnGFjNZRKJDPRKnF+ey5" +
                        "2pzXAiXiKMKaHVNLcfDvgg+tKUi6XWBYWznL1+DOUSq4mJ41zMHKGsDfKE2c" +
                        "C4qe93lAsnNiUu97LqhFSlGL0oPb8mV8VObdu+aXhWo5h+YGQXUdUW5cS/71" +
                        "PJHuoZfNcumYCa2MMsHLzS/ydaAjB4Iv3mc3saqFYKp1yBmIVP/umzV6N2oT" +
                        "Xh0cZNhbsLEUxLDHdvZj2VvvXRk4ZyOsrvirmICH0JZCe9fBSZGcvOD802ko" +
                        "KbIKklanasVJn4+Z0YrcLxhU5nAHnmxSIf4uNan5UXSj9smGpAiNtz0g8GJG" +
                        "Kzl/TAaFUUxFkBZzNPYNuOiEPVtO8xeokb1owvbouagdqhi3hLpbd/u5ci7S" +
                        "Lxx2bVWvaEccU4JpvTpulrW+DhM8azs8CXvy8E61TsMsufVuJ2iPHUEpKQxC" +
                        "Mvd5W1dvp6dMnVKLK2wm5bWQDEWkp3jieuGIZK/Y0vZe/VYxngqODQdmUEKk" +
                        "AOV6Y+xJjovdL0Se3kfjijLyeNHFSVW3atrKBXXd6VaN9mBvPNxJ48yUyCV4" +
                        "PfRQvlrhvZOOael7uMFlrOzr4CH5CJ4U9D7uc+geUvWEJB8qk1fnCpdlZtAJ" +
                        "vdt+STm0yIK2fUj0ggHC4eQwMO99zGP4jsoH/vnt41XeqRuuJ8NpM7skJM0L" +
                        "sqtVIagQ0cxdh11pVWtwicTKIJTKcorLCTzPs9B42zMWB3+b2HfVFjiz8wHS" +
                        "ptVdORevQMxLxkfLKStXv4THlOJH56NmvSduP7y+NkpaOGXTYlYk226GTP6z" +
                        "dCpEl0kaZbhkry1aBRsldLqMHjgsZklnTCE25DuPoXtM+i5XtDkqwqZGHvg6" +
                        "0uY0+U9nyg3FWXTcsB0JKcH3NdmU1fk/vDurMYHCOFU8ozxPHmOOHwbTXZx7" +
                        "oRkLiRMrtqgyZ7qXzZwJV/PJte6Guw7VogOwlD8EhmR20NuZZK/OPtwSAaGf" +
                        "wF3Q8i9PHWhQkbTW5l/SmBulchontd0uET/WJ+6zVfRqNjRddOPsXYM5VwrK" +
                        "Prn5wrMPKG108ZaSU1osKP0jmmUTGvqbXogCn4QqZlx4Fkvx0H439w94joLS" +
                        "LvfVwwlI8k4EZziX+nQcjLw60HYoRFlaeA47DRO0GOCDdK6U18efx31cS+zq" +
                        "zNq2ulAFLKhUzzadl+8fOzZggZ38yDGDWIHet9OJMT2lUAGtU0+jXvPsbOrf" +
                        "e0heBFGkJd7doJVSanKTQLoaKFrTWbcUrvBIYf6aMEqms3R/xiPa/YQ3uldb" +
                        "75c6BOgFVASTq8eG4m+hMNwqqTwqpTd2PSrJb916b0xStQgubLlFUcF3pu9f" +
                        "ByQBf4o0A7EoAPGKlXLJDGiNhtQQG4n4yxrKjg3Dk+G/VzepNq91JCikGuwa" +
                        "HDKehfQa0laPMy/Uv7KgyoVfcUTlvBRAS6qt4B0Z24SnvCQNx13EzQdeFspf" +
                        "MN0QSJV/xaPAODFPXU9lE9LeCljWUHeIChmySjrOr1s0XF4IFHpUjyAZh6xJ" +
                        "hJ+7xHW9bbDedKT9yMAR/Uk3ob64GHa37U5f+XApkJJlLXd+FZY/hdCG76Bk" +
                        "LrzfzRxZd9iaku/+0gcL/ydNYd/m85f4U9+MP7fNRz2YEBDoTfLXVNdQ11Bz" +
                        "J5JPuWtobt8aovHL0Z1Sn37S/zvu85QAApHgHkhQ9yVRiN7+bn4kfBCRgMNi" +
                        "sZ6fLsgDAwYHhSt2ZD3JagQArNDbgACeHzeEL5o1BmytFb9q27O1ZvzYJrzF" +
                        "hvlO7fixV2yL9+wPasgX//eLyBfdjiz4eS3ZHCQN+PFuvyqL75c2/XXg97f7" +
                        "VVy+X9q1FZoXvInzfTqsbQAAkn/z928UjmckMgoAAA=="
                )
        ) {
            val fooClass = codebase.assertClass("androidx.test.pkg.Foo")

            val sourceFooFunction = fooClass.assertMethod("FooFunction", listOf("int"))
            assertThat(sourceFooFunction.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(sourceFooFunction.annotationNames()).contains(ANDROIDX_COMPOSABLE)

            // Signature generated by the compose compiler.
            val bytecodeFooFunction =
                fooClass.assertMethod(
                    "FooFunction",
                    listOf("int", "androidx.compose.runtime.Composer", "int")
                )
            assertThat(bytecodeFooFunction.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(bytecodeFooFunction.annotationNames()).contains(ANDROIDX_COMPOSABLE)

            val sourceFooVal = fooClass.assertProperty("fooVal")
            assertThat(sourceFooVal.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            // The annotation is applied to the getter, not the property.
            assertThat(sourceFooVal.annotationNames()).doesNotContain(ANDROIDX_COMPOSABLE)

            // Signature generated by the compose compiler.
            val bytecodeFooVal =
                fooClass.assertMethod(
                    "getFooVal",
                    listOf("androidx.compose.runtime.Composer", "int")
                )
            assertThat(bytecodeFooVal.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(bytecodeFooVal.annotationNames()).contains(ANDROIDX_COMPOSABLE)
        }
    }

    @Test
    fun `Test JvmName on regular methods`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    @JvmName("differentJavaName")
                    fun differentKotlinName() = Unit

                    @JvmName("sameJavaAndKotlinName")
                    fun sameJavaAndKotlinName() = Unit
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val differentJavaName = fooClass.assertMethod("differentJavaName", emptyList())
            assertThat(differentJavaName.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)

            val differentKotlinName =
                fooClass.methods().single { it.name() == "differentKotlinName" }
            assertThat(differentKotlinName.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)

            val sameJavaAndKotlinName = fooClass.assertMethod("sameJavaAndKotlinName", emptyList())
            assertThat(sameJavaAndKotlinName.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)

            assertThat(fooClass.methods()).hasSize(3)
        }
    }

    @Test
    fun `Test JvmName on value class type methods`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo {
                        @JvmName("differentJavaName")
                        fun differentKotlinName(iv: IntValue) = Unit

                        @JvmName("sameJavaAndKotlinName")
                        fun sameJavaAndKotlinName(iv: IntValue) = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/31VeTQUahsfyzBkzTK2asi+zIwthqvsjGUYTTVZYjAju8xg" +
                        "dFNk+cYyUnYyrhspZU0Rxky3xTbILlFIiixRWa4yH/c75/vU+ep5z++P95z3" +
                        "+T3nfc7z/H7O9lzc4gAQCAQAAOQBe0McwA1wtMKYaSFR1jBHMxTS2uo4Bupo" +
                        "vd0JAHxyZHU52GtB+wTttdR7WL33XOCDupNvw6F2jppIx76IinqXj3Za59Tt" +
                        "WCyNkx97YB0drDdvp99yApzteUHVYmrViJ0CBjtw/ml58A5IeCIJFhboB0OG" +
                        "kE7igiLwUJ8gHJEYg+kmvsSIsOnvYKdPyr3wH8DyPRc+u3hhMES1LEO5FKl2" +
                        "IkzGNkVeEPy0z9Au6AW2rrxtfBI2yQTyCLmHx0rb6pCS+OaojGTcqEpH5LMV" +
                        "+I3o5cXWiddL25HF29vrV45xjD/NFVAbIY1f8GFcGwlu8MW4EQTSzCZ0x6xb" +
                        "A0azv46t0U5H+oC0bSUGGnAgPNHIonTR3uGUSLw4r811+YUlV6HHtiuca3KP" +
                        "M9xehxXmeOTXBlDVH7+9S9NY+3MzyjeqzLNhsK7CmKCbhEt6z9X1YBwIo167" +
                        "LN6vg0mDC0Qp4OmsWSV35cAbffDFA0n5C09twfG8cbPi7o/ymN1wB4HO1Zap" +
                        "Rvh1kaQuduhGE/xCfOPCR2aYmnEsB3AkzGP0FcScSij7wwuEzJdqRSouyfQs" +
                        "hce0CdgKHD8/mI4RTiTDNS5XIvmHak/CqdfT1Fiq7SliNjR53MUhnQoUQL9g" +
                        "KPVbd3GXiV+G21rkt0S/sslgy8R1D5m4pubAI9L9YDol+pArKVrk1mXjPAnD" +
                        "5S2lLN8EK/BQ+ETkOOHOwnChmauLyoNcXAjzbLI2GcI82P/bA+ksIl5Ctfdu" +
                        "VaoO+U06lsBX3ugZdSen6HXgyKrGYkFIJsRab+ghVt/Dpy1MsH/F1dgyNClZ" +
                        "CSrpnBXTz6hJsOTYEOJgk86Hecz6uM/Oz8U5HDqllG8vOiZWb+Pv1N9Tdq8a" +
                        "3IYSJkTHaQmW31AfIcuT1nz0NBOH2lNu2im2+SmdGP7L5y6T1vL3MMPxVmdK" +
                        "hRBfYL+2U66i8IkDykVjQ2WDn89XpKqjlV2xKRW190KlwLddNjGmf1q8WozJ" +
                        "NYJYJ9Ccr2LJYxXBhd1+zyKHxCsrc6kBHWddzYWyLGSr0K889WXiNEMH3brr" +
                        "Nkf3mxPo+OaT3Zqn060ahIObl2SqrDzK0okBdRSaJEPAKPmZlYiSY3JpCyRn" +
                        "rX0mNEN4jrmCqEQ7dawkvpuUhpipzCeRwXGfmn3ll3RgJSEH37ic/wwqiEwY" +
                        "GRwXrTo41e3Z0m6SIfBqNdxr+j4QYhAvoHj1EV/Ily3bCebnJM5i2aK798XG" +
                        "jy4QKaR4h6iuhX0u79fngCUq+zfuoiO8R+tvM5gMC0WSYQT/xYSo2fvJy4lS" +
                        "a5tjpfo8B9m8yEPhWVkzkqXyqHWK+lVmTAs4NrUVdKfR9uw8N5GedmGdnHHV" +
                        "MHpJHQyDhnmCxt92+LCc0tlCmyNx9E+1M9CcBUGOw6xEaP4prpDG4V7+dRvr" +
                        "7kkuTZ7hBorYOfAlvtbbB3xuMzzPaf6OUlsZmV8+5dc5GtvROSl6dOEOrITK" +
                        "jsVu+CVYmNQdtlybjq7aT5ZzLmLanImR6zX51FtjtQUtoj2vyvzQKWludHQY" +
                        "PVibUk56UpDu9HFOMi8+IigI9yRuejI/Pq8BatiOoBhSjBqX/Zf6msPhH7zG" +
                        "zIYf6TshZiCbh3bVxzTdLxfBDQB84P2V+uzfqz7WoaH/EZ5sdFfIgKm4iS5K" +
                        "ro2POqCnFxx9zE3SkOoFNFe3lRAqsY2t60hCTucgp7VTVCS7Luy7xORPBqZg" +
                        "V9EUYxS/hAdE7t0rXb8eBK3tSrEng4Mc2yyAP6A8xoYfXRF+0wh4Nkm1VP+a" +
                        "7KyyfWdbVe49pybPVi3G5RiuJnK9CT+Da24vYWYMLOP/qhlGyH6mlBhltbVN" +
                        "6WGaHKNtFPiRzbILEdJHjN5gnUpXbfxnGgbaHa4dLtcRzVXj2ic1z0elbpoK" +
                        "evom4e2+DG7rDHqr1kNgV1xSHzxxUCvg7JQSY6ML0JnPw4qnJrxbF1d5oh42" +
                        "5DWPa8c288j01MqkRqcavBj4uu50pLvewCSw0ICo9bLreEMxmNKLAPb6j9Cn" +
                        "H0ovIWaNtQitZ7bo7HuB1d8QN03nqvJYrB778o1t7ADit9R/KVaSSvmvENDH" +
                        "rrdKfOPT/lBEhih7aGcTWhSizvRm5aClbmGUr93iKvPWqk+T8EjdUqY58k6V" +
                        "6PVXZtf/jqLEhDWhPmHrI5s8Kkb8ZwqJpTTGOrDHj1PO4NaYtP+U8tCqS6yC" +
                        "+/yJLMpU/ibuZvnjjZd5vWng3EyNG2YSZEyoKBTNMGXfT/DKvshmkbZV7tnV" +
                        "vTzN1B8vOShCR3VVfPECjoKchVUDMiEyHA9qJrjsa+LtTRqTo2EqNWLuHaZK" +
                        "j064Ei7rANmcu0M0T79YDeQEANy5fjVEsjv4r4MG4/xDoIGhpCD/EM/gUN+I" +
                        "ILyPl5cXYQfc3igeVWfv596Af+zxi0ILXWwnU+ofe+TgFAf8j32vde768/fx" +
                        "M7f+kWXvCoC/Y7j0c9P9kWRvC/Z/R/KN+//tzo/5e78p+12+Oe8v2+ZsD+TZ" +
                        "fca9c8Q5AAAa7+7t35pGlajICAAA"
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val differentJavaName = fooClass.assertMethod("differentJavaName", listOf("int"))
            assertThat(differentJavaName.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)

            val differentKotlinName =
                fooClass.assertMethod("differentKotlinName", listOf("test.pkg.IntValue"))
            assertThat(differentKotlinName.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)

            val sameJavaAndKotlinNameJavaVersion =
                fooClass.assertMethod("sameJavaAndKotlinName", listOf("int"))
            assertThat(sameJavaAndKotlinNameJavaVersion.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.NOT_KOTLIN)

            val sameJavaAndKotlinNameKotlinVersion =
                fooClass.assertMethod("sameJavaAndKotlinName", listOf("test.pkg.IntValue"))
            assertThat(sameJavaAndKotlinNameKotlinVersion.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.KOTLIN_ONLY)

            assertThat(fooClass.methods()).hasSize(4)
        }
    }

    @Test
    fun `Test JvmName with method with fliped kotlin and bytecode names`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @JvmName("bar")
                        @Deprecated("", level = DeprecationLevel.HIDDEN)
                        fun foo() = Unit
                        @JvmName("foo")
                        fun bar() = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfXBkbnCzmK/Eubdncpd27jokvXfm/NenTpmLl+" +
                        "Z9eS2wE+Vz4cusygzPdmaSL7I+X9R+sfTO7kb/vxot9o3s1tEycc8312xvJ+" +
                        "ed7vs+/t7euYKrps2xZNW/nN/5jz/pkqehLOmSpph/hdbb7c/+q2aX1sBM+5" +
                        "YlHtv5MO3qq+oXmo7bEi+3TF5farpyQu3nrnheTTr0daEleb+pWsSNqv6K+2" +
                        "SX+LjMBzNyPfowFGDQf5nvq08dxevVKrM7ptEdNzgwyTe9skVz33XBz5RP7s" +
                        "om0dKh93zsib9VPTU+LUc8vjQd2NF5qEKkw2v5g+0zD72IUEM7U/3UaBc/cc" +
                        "tHMuNlxfseeYbVzUGdt55nzfxT7fSqo78PrN1r+vVzufuFh18tbDr3cWZj/a" +
                        "47txr0j0stuMub5eNpLZUe99jRZ8NYvWmvY288rkyf2LxIWCMlVrBcuOuMz8" +
                        "wfPlX6brqj3zj0W5bloc5PpmtYVfiRpv5r7qY6wf3zBd+z3BR732ZsjCI3YW" +
                        "e6f91FlsIbNpu8anXp6S2/37lP/3yvTdTpnL82SH87aKWQ2mFQ4re5g27lzz" +
                        "o3CZ0tyrTtUrJ8wVe5Rn9lduLlei66l4/42Nvp1Cp3b4VzQWHM5d+mRa4NK0" +
                        "P/+L73A/W1OyKuhg85o3aWZ/WA9Ozzh4OjaCcR3LCtYbx/h23uAtEL2+K0Fz" +
                        "3WM3cT+u/jCf3uATc17Fg9LOlIum+/8wMjBYMuNLO9JADE+6uYmZeXrZ+SU5" +
                        "mXnxufkppTmpyQkJCWlAzJLkx6YRkHQhiQGcLr8q7dkrDNQpAU6XjEwiDAjT" +
                        "kdMsKGOgAlzZBN0UZNcLoZhQjzW1o+tHdqE0iv7dTHh9HODNygZSxgyE54G0" +
                        "ODOIBwA9b1gt/AMAAA=="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val bytecodeBar = fooClass.assertMethod("bar", emptyList(), TargetLanguage.BYTECODE)
            assertThat(bytecodeBar.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(bytecodeBar.modifiers.isDeprecated()).isTrue()

            val kotlinBar = fooClass.assertMethod("bar", emptyList(), TargetLanguage.KOTLIN)
            assertThat(kotlinBar.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(kotlinBar.modifiers.isDeprecated()).isFalse()

            val jvmFoo = fooClass.assertMethod("foo", emptyList(), TargetLanguage.BYTECODE)
            assertThat(jvmFoo.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE, TargetLanguage.JAVA)
            assertThat(jvmFoo.modifiers.isDeprecated()).isFalse()

            // There is no Kotlin foo, because the function is deprecated hidden and not usable from
            // Kotlin source.
            assertThat(fooClass.methods()).hasSize(3)
        }
    }

    @Test
    fun `Test compiler generated default version of function with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        fun foo(i: Int = 0, s: String = "") = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBsf6CzuK2G6+H+ulGbqhpqspSOCmk49Py4wV" +
                        "E1M8u9184itmbfOeWbbbu2x5r3/b/oer/x8QN7WTeLTY/lTF8lyWlvZ71c/O" +
                        "2aa/O/7t+3x5e0abg2bOYWwrv/hZ6+br9+UatNXM/r5T/NOKp3tXbm9u27LQ" +
                        "OGK3ktAny+a4qNWpiYeKEg+kGTzp9JAsmnrayXpKucxxnsuq+4sWlmsIC24t" +
                        "u5bFc3qqRWblW4drwnqLrjwOFPHWc/X5oTCNfa+BiuA0qxOGOmVJkl0nsr/v" +
                        "nteRMsHF5ssfj5VLF028dcLYx9SvyS3dpfa08D1ejYmJW0N+7l84d7bXqUUN" +
                        "0XpqhbPniX75LMl1XVjy2IqNU9MKzlQvTKmI2nXrB+/bX9e5A4QbmnfqHJtQ" +
                        "ujCxMXGT0atNUh73nky6HB21ff3lZTOMJFMdWks1/jmuCJ4ZUNy9sKrO7H2W" +
                        "69mrn56nr3umsWAn17O5C0+Lnj+/batRdea/XzkXn/7LiZt/9dSxbY8zKidP" +
                        "MHUwU3TvvDQ9MdB6tmzWxQpRc+a2zq6Opua47XIq7yttea5UPGLjz7YRfGXD" +
                        "tW7Pa9egEmb7V90hAS/u68XFrJmR+Cfgk9vyU/7uJt8N3qn2JvivOHzGY7FZ" +
                        "yA6XI2K8pwOaG/w2Lzg/4Y2yVWWW0ZOzX/ML9glc2OB4TYrF1EZttqyaAvP0" +
                        "P0ukbO9qfMr+lDXvm1viI+tl6y7p7gr6sjTxVMe6CybveFRynwSoHFt2a5JZ" +
                        "xCWLCecT2DYv/jRP8KZKfIyBXV3Tyvd6M56sPVRTWpv3WFus3b8qhTejr3hl" +
                        "2dxHTw++tTLlfxP4RS9TV9bxe+H547XypyMUO9jt5JK+dvwz1Uh8yzFTJK9r" +
                        "p0hY10bGGXI3ZG7uByXeF7J9abVMDAwsLPgSrzQQw/NObmJmnl52fklOZl58" +
                        "bn5KaU5qckJCQhoQsyT5sWkEJF1IYgBnjK9Ke/YKA3VKgDMGI5MIA8J05EwD" +
                        "ypmoAFc+RTcF2fVCKCbUY81u6PqRXSiNot+GGa+PA7xZ2UDKmIHwPJCewQzi" +
                        "AQCCFldofQQAAA=="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceFunction = fooClass.assertMethod("foo", listOf("int", "java.lang.String"))
            assertThat(sourceFunction.targetLanguages).isEqualTo(TargetLanguageSet.ALL)

            val generatedFunction =
                fooClass.assertMethod(
                    "foo\$default",
                    listOf("test.pkg.Foo", "int", "java.lang.String", "int", "java.lang.Object")
                )
            assertThat(generatedFunction.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @Test
    fun `Test compiler generated default version of top level function with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    @file:JvmName("Foo")
                    package test.pkg
                    fun foo(i: Int = 0, s: String = "") = Unit
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBsf6CzuK2G6+XbZ9qcPKLR6OfxiWbj3FdJH9" +
                        "8o8EH49rU5KOfDh0ucT6Sub2zSnlt36k/JD+x3O/g78tvX7KR79oxsb2vc/P" +
                        "3LH+/vjOm/f29vWMFa2ybXvEOr/5/Y7N1++SMWhz6u/fIV509SvfLVvBDHaz" +
                        "5lohV56nq+7vKZJ/FbEqb3HZkQ33eh6tuy68S7Z4pVC/tmjmulSXPaIvrxnw" +
                        "SPPoFD40WvNVWdbr6Vc3Zn8t3aSjzufD1aKVmo849i0UqRF0rP/1Rbn/p0OA" +
                        "wuG0rZWb9x+L1X068XgOn/GMxZsm2c1fWGE9IbGN7faLDmE7HlPnt+sY/95W" +
                        "PXumIPCUcq8Pn9g0CbHZ15/9MpDasiJda0JQi8qDeYIFD+PP7Luw57DpcoZs" +
                        "g02ZsmdmrDFcUPa7LeLJHy3e22rXFpbu3Weh/UzvXFHYwVkN1o07vZYWFIZt" +
                        "3HZz3etJk2fYpmkmZX6umH7QcPe9V5dnrPwe2F41K99LNeqwY/SR+ZXWIm6L" +
                        "NB48FOxfaumvb27HO3Ua88PVE31UYmPfPb57dZZkGr8p/6/ff0p+6ZRL8D52" +
                        "eONZHDHvmcWslB/vH546++f6nhuKhcFuIodSF5sl9vrxOnVfWCm++fmM/4Z8" +
                        "FX5mzp9SExb77bHhdGTkfizC93aNxz9xt6zsNVuYRTbtaHd9pPZ+dubepVev" +
                        "bH/vcbv3qknisoi8sFfPd9iv/Dwph2va67gwltAtG02/hT0U3Lf0dmjWj4OG" +
                        "OfLZdnK/KlqO281LuHucbZ7c5f8N6Tds3NQcrRw1HRNzYjfdWCZiPCVp5j6v" +
                        "pqn7QekwZs9cFg8mBoatzPjSoTQQw7NBbmJmnl52fklOZl58bn5KaU5qckJC" +
                        "QhoQsyT5sWk8ePT8kYYnMFton/ATTyo7fT486UISAzjRSx/f8lQFaJQmONEz" +
                        "MokwIKxDzhCgXIcKcOVBdFOQvSOEYkI91qyErh/ZhdIo+tmZ8QZBgDcrG0gZ" +
                        "MxCeB1nFDOIBAHhtNvpZBAAA"
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceFunction = fooClass.assertMethod("foo", listOf("int", "java.lang.String"))
            assertThat(sourceFunction.targetLanguages).isEqualTo(TargetLanguageSet.ALL)

            val generatedFunction =
                fooClass.assertMethod(
                    "foo\$default",
                    listOf("int", "java.lang.String", "int", "java.lang.Object")
                )
            assertThat(generatedFunction.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @Test
    fun `Test compiler generated default version of internal functions with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @PublishedApi
                    internal fun foo(i: Int = 0, s: String = "") = Unit

                    class Foo {
                        @PublishedApi
                        internal fun foo(i: Int = 0, s: String = "") = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/31VezQTehxfzLORZtai601DNrTr0TwumZqh2dRR0byG2MhY" +
                        "HleP22M1FcvR2FWZipUeqNCEGok0NHl1leYdNc8c8ti9q3PPvepU39/5/PE7" +
                        "5/f7fM/3ez7fzxePkwdCAMrKygAAwACwMiAAIMAb4+dqifXxQHq7+mA9MEQ/" +
                        "hLeHtAkAmPEWPvfCWSJEajhL8xZh6z2CVftm8RAN4em9CestohfdJ0x6WsaZ" +
                        "ewqFFrsmW5DPngkHhvqH5AB4nJJysaZZsYMsgZ0M+B+mB8uQQI5PQB6IjkB6" +
                        "xMYiQinB8fFUYuAOTV8Np8Se8ZhsN5wWHK+hUKB/F4/1OpHDPf+gMNXdi5RU" +
                        "Sc2m9UgQr6qpqTZLaqRlpZrrUwINvWXfp3yJed6aSw6J40IOOqL80d9L8ypB" +
                        "8jqg9aMebc5UO8klfavHMJpl70B5w3JhNSmaT9+vL+DGqpAa14GYbYRfj0MS" +
                        "VmNhIGyyoSHjfBP5incTWl2OMZVJ5IvRWfmTubm4nYxSTzTr8qTkeEyzpojZ" +
                        "0+2s5WdLUqktfEMjWw7WZlDGF/8YJABPzMnxu2Ydm0LOMzfVUYSiMTqlD2f3" +
                        "kR+BrhkDM1PCgrK0O3a8TkEjnMQGEw+yGkv8iwL0EmbKeeEHlBItB609qArL" +
                        "GxhLze4MA+2k/D7dmTsb11++cEeVdgbU0qwEJwddyRFo2EBRQpuzUmJU14Uq" +
                        "gQlv3x5eNlfNhtzWdFS3s3BeKd8n+oT2e9DiSAQGZxHJZqNd7qGIq5oJVhHq" +
                        "r7f3Ld26dbs0+wYpj16ZuldoUni/LOwVqiwgjdW7TdFgRo14n7wAjeR22Kpx" +
                        "UrVQAQwKZn58XwUxvm6uapjvyeW2VJuvObmlQTCaGEhKS38/77RWRZVK+sAO" +
                        "7luQ/B0YiNVxzqrq7R3iWd+gwxLqKh8atQfbDF/vFTdsYXL83Axc94hGcAdP" +
                        "+pQT0lOjPukIkUCUo8HNNcf8TXKZcdrQT75l2oa+9cVme0eptwbj31ofLgs4" +
                        "JULm6Vx7MqTqzX3BQ7HqjXd2x9WX7Ln01/VO0jtlFi5U9eUUMU/TNy012PTT" +
                        "lCaBeZsDSXZyuffQsXpaa05R0vXMNJ8zXOAASsterMiYylpSx4ycfi9dC1bX" +
                        "tc9JLDl6CjjVKDD03PZxTR2YncFKD8LAPV4YgWYF7m/Dn0kVPgtbkex/iiMH" +
                        "AGwH/kzYkG+EjUv4V9oEnEzaEOm4l95LAvc24ZpfKl7Bfb2+6VMU4rRKXMgN" +
                        "dy+7wDc+yKGDhZvDhsvjyPM6UlDik5PBkgybyz3pGmZnHEbQGb/XzOS8PXJY" +
                        "KjepbVt3DFx6t7W65hCMobcTejbfXhwas+j4rievUr4vsyvMakcdK//uKCq2" +
                        "dqN5puuj0o5w/6iU/GlykF5GZc3ZVvcJcYAtrDM3uL1CyY1uhDjePeyqYX4n" +
                        "bPsDraLOxjmzM/VQGigae06QFPn8NyZcXCMB/mnCjrlYv2EtP6J2NpPX4Ni/" +
                        "1ZUJ0slt7jlkNx5VdjR506DvY0OR58IGxjTFsG6DWsAEk+g8rVXXGXgcfS8r" +
                        "ZV0J3kz/nJeuoq5VllQyvB3eO5BGpIfa244qe525COXuclQDqR6+oC0Bj0ms" +
                        "q/iV8JFixLSHKMc8ughaVNbfgenoJi+yMa5uw8AC5QIXq+XUPIOtvDdTkYz2" +
                        "xZKNMGiujcZtUYnFFWTV+9VOR97RHjUb3ZA84S8kQ+AOEB8rdqlxl6B5FDZW" +
                        "cbWLN4+kSt3jutD9I1jREtY+LSAOXlaa2R7Z2t6gS0UUVLilYIrBpraNUSwq" +
                        "qyUmeyYbLs0xpmQKX0gT2qB9TSgFk6yNJncb+3TrScbJu9t6UiaehkmgeGGf" +
                        "M1s5Or1CZY5mOStBt4VfZZteMkPa3ezJ9ufQaow5x4J3d33QPyhfbCoa6TZY" +
                        "Tq/aGXW4TP6la7Ngq1J00qDY+rJ9Utqs+vL89YHdShzYKOwCrIApPL+0+rMA" +
                        "WUa/VLrIBMiT/5kAdWT4z9ipwftjENGxCZT9MSRqbBidQg4NCgoKlwEY4qMI" +
                        "F4MH++FYmdFbNPqsC6E/b51D+Ia8CAF8MXJdUe1qUxmZxRcjXyUHAfyfcKXJ" +
                        "f94kX8eP9sq3LCsnCvwVw5Hvrodv/69sCOSr//7y35/CbxlW1qjzFcMrhZ+2" +
                        "EY9TUPz8DCg7a1fJSvhy+wfr5KfgbwcAAA=="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceFooFunction = fooClass.assertMethod("foo", listOf("int", "java.lang.String"))
            assertThat(sourceFooFunction.targetLanguages).isEqualTo(TargetLanguageSet.ALL)
            assertThat(sourceFooFunction.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            val generatedFooFunction =
                fooClass.assertMethod(
                    "foo\$default",
                    listOf("test.pkg.Foo", "int", "java.lang.String", "int", "java.lang.Object")
                )
            assertThat(generatedFooFunction.targetLanguages)
                .isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedFooFunction.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            val fooKtClass = codebase.assertClass("test.pkg.FooKt")

            val sourceFooKtFunction =
                fooKtClass.assertMethod("foo", listOf("int", "java.lang.String"))
            assertThat(sourceFooKtFunction.targetLanguages).isEqualTo(TargetLanguageSet.ALL)
            assertThat(sourceFooKtFunction.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            val generatedFooKtFunction =
                fooKtClass.assertMethod(
                    "foo\$default",
                    listOf("int", "java.lang.String", "int", "java.lang.Object")
                )
            assertThat(generatedFooKtFunction.targetLanguages)
                .isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedFooKtFunction.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
        }
    }

    @Test
    fun `Test compiler generated default version of annotated function with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    annotation class Anno

                    class Foo {
                        @Anno
                        fun foo(i: Int = 0, s: String = "") = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9MBCX" +
                        "pBaX6Bdkp+s75uXl6yXnJBYXt07Y68/kKHD0vs6jg5pMdU0iUYFFHm+2XHSZ" +
                        "7O3AW2cSwp1zouj43I/fOspC/gWKtUX8cpdM36igpma2ef/c2nyj33b7791j" +
                        "lFnspiExVeK9oURleqCItYJTps7yS9ypJp/W/U42NeAzamlODd90yWPx44zE" +
                        "X47skYcSf/uHhkuKRloppiw38hFd+K//ixrf1wetvH7f/7069z5G4NRbBbOF" +
                        "fXv6jIo0ep+EG85M+6Gf4n1PTEDjwb/N27arXf7QETD3fNG6FRzhU7J29ocH" +
                        "zjwftcjz+eeZols4jn5br1BhxF+kY8bkXJD/+EvmAr3SE6vDd2uutwjkt7r4" +
                        "N0DX2OOVDa/2m5itsw3Cxbc21dr5z7RIeCLyqWBjm8616hM/NH7c+BDo0imY" +
                        "Kj/7xqtGoZzk5ewTE8wP3jMHBfYjnYliOowMDFsY8QW2EHJgu+VDwzo3ONBf" +
                        "2FHg37db92L7nbxFNQIEWJcqFAX4+rTMWDCxYlmD4GXxedveBm7be2/i7tzH" +
                        "kx/m/3hl/8G1l7/tx4t+o3Nx+kxOs87VWP8+++aM+dv39vZ1TBVNx3ksX7pd" +
                        "snu77e1dBYMDElyydg+2Hf+7rfbCix2+mQpHF+Rzx594zrPylJdtO08Jd6YE" +
                        "T2alklLbxltPnaKPWPMxtZ3sCt69olzD+eH27WGveH5y/+t7v7rlaJTAtrd9" +
                        "2tKK4nUpxVzeeq4OFfYTfteeVxbr9nki+Wx345eW/HI22+gfl3hr393sW//K" +
                        "fZGkpET6jIx5O87mTfo1rX3Pc6GJiTuDftcfWTt71pmHYnnRGklN80S/lEp0" +
                        "Xe/kUTZ5FXTa9lmtv5HN2pipj9gY57zm1fBNWDTnMEeRmMkZ457rkzS/x5yM" +
                        "jimq8n7NG7TFeplPmGRAcmxXvcCrLY4rtp4sKKgL27ZlUlpm62djb2PRCzqs" +
                        "3M8u9E49d37VLbbqiT+/Ri4/bf7UKcvttke4b7B2CsdLQyOOmet5Q7edSlgR" +
                        "u7Y889SX0wuerjLlun/3/XepvB/zzG3CNUVMdoY/XxjwXMXi77r33heLXysJ" +
                        "idzYGaF7OKjo+Y/mh2/3h8Yu7JXzuvfm3cfLS5aUSnw5vGy3to347CXhUZmN" +
                        "Iicd9/tuyi7ZNNXiifHq+wl1igseCkba8i3b6GdmrqLA3f1nR9D8rhVPwo+E" +
                        "TZLKPnqo3CfO9/rLxZfE26aoxC7UnPJoEtunGct4rE9KhN4O9L78aG4vY6tO" +
                        "msHsLb96uFJPTH92Sd5ecfKteacnXO8url69OTu+MP3Hn7Q729NmTX92QTOu" +
                        "N1/+WP/boJLGrLu2TqcO2rJ3800rmK8gZxHh9rTjn0vEwVeCmwTXcDzlqGqZ" +
                        "KOFz1Mz/mz4oLTNeLN7Xw8TAoMqCLy1LAzG83MpNzMzTy84vycnMi8/NTynN" +
                        "SU1OSEhIA2KWJD82jYCkC0kM4ELpq9KevaAiRwJcKDEyiTAgTEcusEClIirA" +
                        "VUaim4KcE4VRTKjHXtShG4DsfSEUA94wYsu+6PqRvSiNon8HC94gC/BmZQMp" +
                        "YwFCAaBVIqwgHgCcT6E6OgYAAA=="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceFunction = fooClass.assertMethod("foo", listOf("int", "java.lang.String"))
            assertThat(sourceFunction.targetLanguages).isEqualTo(TargetLanguageSet.ALL)
            assertThat(sourceFunction.annotationNames()).containsExactly("test.pkg.Anno")

            val generatedFunction =
                fooClass.assertMethod(
                    "foo\$default",
                    listOf("test.pkg.Foo", "int", "java.lang.String", "int", "java.lang.Object")
                )
            assertThat(generatedFunction.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedFunction.annotationNames()).containsExactly("test.pkg.Anno")
        }
    }

    @Test
    fun `Test compiler generated default version of deprecated function with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        @Deprecated("", level = DeprecationLevel.HIDDEN)
                        fun foo(i: Int = 0, s: String = "") = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBscGX3YQsZ29zFOKqyVsQ42mlle3h5frXMFj" +
                        "kqISj6Y86rgsPm9aiebCzEMmmcfV2/c/XP3/gHhpTYGE/N/AvvJgCTv5ou9v" +
                        "7la/A6Jn7/fH72fUeWzpbCYWmCO/ddY3N4lXCTzXFN8X3n7uePnePWuRvDXK" +
                        "RxesrWoU35/kIer9VefSBZ8VFySv/DjQLdG2Slx4bcCdOgmjHtG5ryr3zd3x" +
                        "mPXs1KC5ivG5rRvfs36bZfGKY/rxRY4qe3UXdZizMktc2veCc37in6wV4myh" +
                        "lS4suZcjo6PXG2444TDv4bnMF4nrc6sddawYv+eqTyhVPt9b0mPUs4v9UO7V" +
                        "Qzuuff37fc/h201Mks+6rq5eu/5OyU+dvMUJSTyPQ64tPp94IpY/RUKBM006" +
                        "aX753xkPXT9ucjHneByz32FlXoJFN2OKr8aO4wZFy45N66hsPj6HS/NtXBfb" +
                        "qSUnPZxfbRT7aV9ZFK52JSLphp3lPKOpTUla105uXCkhpLGsZdrF+DYu9eOi" +
                        "S8/cW/K/5uqLD3enTJ0VcCrrqIGItq+H9wS/EzeupN7iOtuwKzInsP3V7Q2O" +
                        "i6XXVTWuKL1dvejzxPc/nVYJ77qz/nOf+vflfp9rPpk85r+r8O3WoneucnP3" +
                        "8TUZ+LH5+mcXqD/Ly+6WnytYfijvuKj0PP0NLotWbEpc8qS70e3asrm6ogtM" +
                        "bf8zHzNn4jqVsvXISgHeE9+YJzRyRigHJXkpnXwtolh6Wbxoe13SI6PYxVWT" +
                        "7C7zv7wsbNK5UmVpn82yzkDPP/4vT0+sn2DjLNiWyNmjkJ5awF5+Up1735KY" +
                        "zCjuy6+e63ssD1t+eUvWzplrf55sfb/j/v6DRpGiXYkxGa62axc9iEwSn1wl" +
                        "XfKtwejcO6WnDxY2u/M9C1DpmNRcaXNv6X9W7opN3T4sk/NEHbsNJjScPPLs" +
                        "5D92UPI+eu8Q+3wmBgYNFnzJWxqI4bkrNzEzTy87vyQnMy8+Nz+lNCc1OSEh" +
                        "IQ2IWZL82DQCki4kMYCzzlelPXuFgTolwFmHkUmEAWE6crYC5V1UgCsno5uC" +
                        "7HohFBPqsWZIdP3ILpRG0R/HjNfHAd6sbCBlzEB4HkjvYgbxAGxz5tyfBAAA"
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceFunction = fooClass.assertMethod("foo", listOf("int", "java.lang.String"))
            assertThat(sourceFunction.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(sourceFunction.modifiers.isDeprecated()).isTrue()

            val generatedFunction =
                fooClass.assertMethod(
                    "foo\$default",
                    listOf("test.pkg.Foo", "int", "java.lang.String", "int", "java.lang.Object")
                )
            assertThat(generatedFunction.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedFunction.modifiers.isDeprecated()).isTrue()
        }
    }

    @Test
    fun `Test compiler generated default version of constructor with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo(i: Int = 0, s: String = "")
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBt/3vxwg8i9vd+lSHg3hAmZHkSmGE/keTRZo" +
                        "XqTia2aSVPHw5borhdfXqb69+2jyw/sfrv5vUO/hb/vxQj7VZvtshylLbJ9b" +
                        "P583/35u/d9//5gPGOcYiSyd7/XcJMO6ujCGIXmD9Z7zcye45z7PXf24ehPD" +
                        "3IM3xCZO0JdIW5X32OqRg5pSVv5aOSMd00dL3pbU3PTa0RHU7h6os1PE8OHf" +
                        "iHffDxV0Ny5V82lrVZi2sGOO3/7XHR6/PI84H+8N2RpSkckodnhL+aPqgwZf" +
                        "Zf0rri93aJua9fi+4PY86ZUdSsd0epLmOK7Xsua/7cQ3mceyUimo7eOxs1+v" +
                        "VjTFTV/4pd6xzXz72ndmedFPt/6f+N1pp/fp00uen7TI/KTyYXKP5owVKxJ5" +
                        "uKWeiWhpm5w3+Lk94vL0PzO8/WK35sUvv53k1aPBKGWo8F994RqrhY952+ae" +
                        "3izprZ09LS1+XuSXjG2WAY+XnJqePjtq7t5VU3yCN2+NXLsuXcNd4//n1Qtv" +
                        "tq1ylm9xdFnDPknjVm9/bVtnW8efv3Hbn6rLV2h/ETXfePyw8zyOVP65p99X" +
                        "TxSJvd/7S0n0ya5dG6Zfv894+Ebjk7gLT5YfNnmt8E513n71KYGS1yxkQnZM" +
                        "7OB9pL68rN3vtXBpir3iadW1ERKx2r01h13eMi0tNjJdM9FEI4TVTnWPBcdh" +
                        "WZ/UVDfDXkmr8BNHYruiwv9kSe5lPRAWe6oldPrzKXPUY+edr/3XtGef7Xz1" +
                        "3Clps7fu3Mp9R3pLicezBTOfhRY/DS19kzhX9NAp66DLMw/qbSiYtzX4syg7" +
                        "++GfJleMIxcfbZS12u2/stv9QqxNwccLR4WmcT6a2H08wnLRfa7QNXILJyRp" +
                        "uaoeLVE76yNVD064sxj8qyqZGBg+MuNLuNJADM83uYmZeXrZ+SU5mXnxufkp" +
                        "pTmpyQkJCWlAzJLkx6YRkHQhiQFs9lelPXuFgTolwJmCkUmEAWE6coYB5UpU" +
                        "gCuPopuC7HohFBPqsWY1dP3ILpRG0W/BjNfHAd6sbCBlzEB4HkhPYQbxAAlk" +
                        "yHF5BAAA"
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceCtor = fooClass.assertConstructor(listOf("int", "java.lang.String"))
            assertThat(sourceCtor.targetLanguages).isEqualTo(TargetLanguageSet.ALL)

            val generatedCtor =
                fooClass.assertConstructor(
                    listOf(
                        "int",
                        "java.lang.String",
                        "int",
                        "kotlin.jvm.internal.DefaultConstructorMarker"
                    )
                )
            assertThat(generatedCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
        }
    }

    @Test
    fun `Test compiler generated default version of internal constructor with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo internal constructor(i: Int = 0, s: String = "")
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfnBt/3vxwg8i9vd+lSHg3hggYFrlMzPNg/enYE" +
                        "Bk5Wk5Yy3POhJPzVjuzrk77lfTz55v6Hq/8b1Hv40z5etD9VkWfeqOFZfa78" +
                        "nK39/tv/fv39y96gfiNRw8t+6rnIC0X3jhQy9hzM+yxX7iS3dc72s9ven2Qq" +
                        "YUv2d3GyS56xesfX1R3OMztWbN1deGRDVP/kaZe/pRo/VPKSlXPd+CToSAt/" +
                        "UuUF3sOxbNNmGUhOa45oUyq6tbdPWemluiC/rHnQ1aCHS1kjeDbfW/medfGV" +
                        "cpuHsvOZJK9pf613v7M3c4lS14wNBn2ebHsXvbHJ4bb0MX7yZKWHpNrr69fD" +
                        "H3NWxbZd+s8qGXd/y67o/ZlTw/+53+N9aqerP3md+uMlBycf8DdalpAUx5sc" +
                        "cWHDioDIhaptL/cfldr3RD2m6NWq0iqru2LBST6cFzoYf9pzpl7lmJtj/Ib/" +
                        "V/G0sGs6Z+W+6FaZ3JZZ5Rn01LHeffXG6d3PFsQb+Z69JrZl1ryFMgv/nF/f" +
                        "lia9SqRWmK1wo6hHUqa93UfJZa+UPvHvmrtsfs2DsxfCvxyR4++vUJpiW7Ru" +
                        "73u3oJIa81edIRNefzoXF1PLxSPcPqnasXl+76U1h3fMrvgnvbDnZ2Dv4sTW" +
                        "7M4bal1FWbUvjjBsdP+mvCnmUlfHx7OXbpj7hOoHWKkHH1bzbHYKfZB8SPuw" +
                        "wZ1DPmFpMptnLC5u9bmzbIWu68ZphjEMOVeX+kSUVnqdrLz0dOmd5/onXld7" +
                        "V15yy1wYGHtnz4G5id7tKXyzMuPMwuNCj8tdjVRfsyc5dK7kG6bTzyOtTeML" +
                        "Kwxm+TvrTg6NkqnwSy/XLH0qsau7Wu/d7oijUo42BR8FXh//kr6wqTdZJOjM" +
                        "khn+hrM9jScfkwcl3UlVnI+qmRgYvjDjS7rSQAzPObmJmXl62fklOZl58bn5" +
                        "KaU5qckJCQlpQMyS5MemEZB0IYkBnC2+Ku3ZKwzUKQHOFoxMIgwI05GzDChf" +
                        "ogJcuRTdFGTXC6GYUI81s6HrR3ahNIp+K2a8Pg7wZmUDKWMGwvNAehoziAcA" +
                        "OjmNJHsEAAA="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceCtor = fooClass.assertConstructor(listOf("int", "java.lang.String"))
            assertThat(sourceCtor.targetLanguages).isEqualTo(TargetLanguageSet.ALL)
            assertThat(sourceCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            val generatedCtor =
                fooClass.assertConstructor(
                    listOf(
                        "int",
                        "java.lang.String",
                        "int",
                        "kotlin.jvm.internal.DefaultConstructorMarker"
                    )
                )
            assertThat(generatedCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
        }
    }

    @Test
    fun `Test compiler generated default version of annotated constructor with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    annotation class Anno
                    class Foo @Anno constructor(i: Int = 0, s: String = "")
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9MBCX" +
                        "pBaX6Bdkp+s75uXl6yXnJBYXt07Y68/kKHD0vs6jg5pMdU0iUYFFHm+2XHSZ" +
                        "7O3AW2cSwp1zouj43I/fOspC/gWKtUX8cpdM36igpma2ef/c2nyj33b7791j" +
                        "lFnspiExVeK9oURleqCItYJTps7yS9ypJp/W/U42NeAzamlODd90yWPx44zE" +
                        "X47skYcSf/uHhkuKRloppiw38hFd+K//ixrf1wetvH7f/7069z5G4NRbBbOF" +
                        "fXv6jIo0ep+EG85M+6Gf4n1PTEDjwb/N27arXf7QETD3fNG6FRzhU7J29ocH" +
                        "zjwftcjz+eeZols4jn5br1BhxF+kY8bkXJD/+EvmAr3SE6vDd2uutwjkt7r4" +
                        "N0DX2OOVDa/2m5itsw3Cxbc21dr5z7RIeCLyqWBjm8616hM/NH7c+BDo0imY" +
                        "Kj/7xqtGoZzk5ewTE8wP3jMHBfYjnYliOowMDFsY8QW2EHJgu+VDwzo3+L6/" +
                        "sKPAv2/r9sb1My310lXoagrSKmqw4zRk8iry7Hbj0bfJ7d+aU71XuTz60eSH" +
                        "9z9c/d+g3sPf9uOFfOqcvdPktTytP7898+bM9vtP6v7++8d8oNgmWUNjosb3" +
                        "FZeLti/WYD8WkPM5+R2bTc7O/Nm5D1fL+LBlx7YF7U3RCN/7K/Touq7UG1HR" +
                        "N5Il1sZvu8VxOKM3cZvcUd3tuiHGz4x8YvwSj07t4XjvkrVcnCXkqJB0ZVv1" +
                        "XA9Wo5Lbx2PD27yOKPLH6F/5K/xS3ZLHsOd3+6H3POv2PTZ8z6WVMmP9tf+q" +
                        "e8uETzK1GT5a0BDItnnV+cgeyxMLjkwIbDdKlFgZL+5yefKX7xJe/81TKoon" +
                        "S8T9XbZ8j5h8pX5A9JcK0e81okd55cpd5mSxS0+e1fP0wIHYXjWTXccNyi6u" +
                        "URSxf9B1OWRxdc6lfzPMKnMdRKxF/qt8zZx54SDX4Wc/PW8aarUli5z3l/kl" +
                        "6br7vduJ0HP+Yb2p2yxTn3ttyxXp9Uy+6RPYFvDtefhpjahdl7Wc/fRLVi72" +
                        "WLr2hcOL2NMBb77s8hWsf3DWIaNSaT57tc20XzYcfnvet3qVHZ3no8Jqtu7b" +
                        "eQPzE5UPC+2rd57ff6qtXPS7NTBLZLx847b3hvhtI4EIxU6jXz8NPBmD+v8o" +
                        "zbXpylZsE/M+Zy6Snb6j1Mud2f9kVMgq7ukc57+cLpB+nDlbcUph+yepp4EL" +
                        "pd89cPN7veqpWdDW3AUr1P0ErgnHByzW+Xmq5Nln8ZSzX1/qVK42ubrCV3de" +
                        "4i77utYctrbceKGdCXZukXfT523oe5attvmG5Lv5yx6bXHeyN3xQNXd649Ur" +
                        "dp6L12tYOefKvTn85cOFw2vaLW2UOo7L8a54d1m8nfXKt4neBvMSjBwtt/68" +
                        "ElUPLjd0xRTudjMxMMix4EvK0kAML7ZyEzPz9LLzS3Iy8+Jz81NKc1KTExIS" +
                        "0oCYJcmPTSMg6UISA9jsr0p79oJKHAlwmcTIJMKAMB25vAIViqgAVxGJbgpy" +
                        "RhRGMaEee0mHbgCy94VQDHjDiC33outH9qI0iv7tLHiDLMCblQ2kjAUIBUCu" +
                        "ZwXxAG/YMW85BgAA"
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceCtor = fooClass.assertConstructor(listOf("int", "java.lang.String"))
            assertThat(sourceCtor.targetLanguages).isEqualTo(TargetLanguageSet.ALL)
            assertThat(sourceCtor.annotationNames()).containsExactly("test.pkg.Anno")

            val generatedCtor =
                fooClass.assertConstructor(
                    listOf(
                        "int",
                        "java.lang.String",
                        "int",
                        "kotlin.jvm.internal.DefaultConstructorMarker"
                    )
                )
            assertThat(generatedCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedCtor.annotationNames()).containsExactly("test.pkg.Anno")
        }
    }

    @Test
    fun `Test compiler generated default version of deprecated constructor with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo @Deprecated("", level = DeprecationLevel.HIDDEN) constructor(i: Int = 0, s: String = "")
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhfXBvv6CztK1KVtv5s3XcxLUCdB0F5TNNPxi8IR" +
                        "DZFHarmmSTsKpLYlR+66q7r78rHJD/MrvsoXHKowOLLY/tSPc9s6HJM2fT73" +
                        "ue7M749n3pyPv7+POeKwcVuPyMQ1MuW3ps5V8Gg0fNJXMT/c5t127Xy+vhbl" +
                        "4AuSPbzXy59yt62cFLu/58bUha+3Xxf/LqH2aq5D1br4lZ2PAyxiuq5ZOa/S" +
                        "XtSxnZVZYtK8F9z9ib+yVkj/KH26iPlY9u6w23lGG064TEvMk6i68j4sJr7r" +
                        "wvO4o99C754oXSRyTEJtrvPDklmNOd9s3uvyV4YHKBx2WllXOuPr61nnE2Qq" +
                        "uS19DF6oLlU3zFRs1GxrsSlRbrv4675L4PwzS1ONn/z3PbGhVtaz7O+uwKxH" +
                        "zO8ri7Q/KEt+k5Fs4ZUrc/HInemx2o+HWypPOyTkBd/hH67L2ibsOaq6fFLU" +
                        "9vWXlxqpXvISuL/G+Vv/ukurWqK0eWY/Vw+eFnZBR7e8Luni8qdnDpp5RT/8" +
                        "kxK6etPZSOOp5r66y89mhNq6cTqdlI/n0U1f4bO6JPDI4SJ3Iedox+535Wve" +
                        "/Vp087Vcxwdb+w8ti9QKdmw/7hz+0nlx6U+bF4p7Xv+89LbL1rzo5O9T35kl" +
                        "97Ml2ezW3nk588Pj7ubdjvolR/pec64R4Oc0Vf4iEyzi6N5qMvtN1q2JS/+8" +
                        "CL+jMf/hM82VUqtUeJOOKOxxqqgTaGNa9thEecZBnZ+ipx6Jua499k19y6cU" +
                        "/4lHeya0qWooCYvrTvIVrfXbOWmLeFen//yJV8y7PG/1/olZbLO7pO2nI3u2" +
                        "ivAFn8/++jtWCKXPlunb4BDx81LqXMk3POX+j1eH+Kaz9SVe/p87Ycvywsan" +
                        "Fo9UXe8sPvL9+8LbFQmPF/q2erFszFjc4/77y+5Ezv7vE7sMfCuVzhwxkvZ+" +
                        "bA5K3GrGanxzmRgYjFnwJW5pIIbnrdzEzDy97PySnMy8+Nz8lNKc1OSEhIQ0" +
                        "IGZJ8mPTCEi6kMQAzjhflfbsFQbqlABnHEYmEQaE6ciZCpRzUQGufIxuCrLr" +
                        "hVBMqMeaHdH1I7tQGkV/DDNeHwd4s7KBlDED4XkgvYMZxAMAGczJo50EAAA="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val sourceCtor = fooClass.assertConstructor(listOf("int", "java.lang.String"))
            assertThat(sourceCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(sourceCtor.modifiers.isDeprecated()).isTrue()

            val generatedCtor =
                fooClass.assertConstructor(
                    listOf(
                        "int",
                        "java.lang.String",
                        "int",
                        "kotlin.jvm.internal.DefaultConstructorMarker"
                    )
                )
            assertThat(generatedCtor.targetLanguages).isEqualTo(TargetLanguageSet.BYTECODE_ONLY)
            assertThat(generatedCtor.modifiers.isDeprecated()).isTrue()
        }
    }

    @Test
    fun `Test compiler generated default version of reified functions with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    class Foo {
                        internal inline fun <reified T> internalReified(i: Int = 0, s: String = "") = Unit
                        inline fun <reified T> publicReified(i: Int = 0, s: String = "") = Unit
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9EBCX" +
                        "pBaX6Bdkp+u75efrJeckFhevDTnvL+wo8i/td3a5ZqrjgW1NCmKaHToP+IUU" +
                        "VbKcdDQXTCl4aLKFm+fuu0nvplQY2VZ+eXf1/4HJx5ozLX65S97XCdVIOHIk" +
                        "z+rzXvP8e7/3mW/+/ff49XwGucRGRc25Drfcr9ZWH/0unMBzTdH+2bbTe2ur" +
                        "k79f0DzNosTzUfUh584zAif989QFne+4iHC2rbARYHReVWF3Ucl5/4clwd93" +
                        "7H7ZYfswpMhUz6btYM3CirAFKq8P3Y6fcydtRtytqqg9EVWFP/0f6lyYmLQj" +
                        "9utSbZvfH3ak/dfKELcRFe8omtBw1CDAruDU1dL4deYx30XXf57l3Mgj/PP0" +
                        "13Va01zk82a8cLV3VlMvqt65eulGpr+szvefqvA4h789GTrH9l6B6mGnfZvr" +
                        "w1foxaStlmKUdebZeOelYYm8aVlsYJ64op7ife5zjXy/e1zyN949a+r+iWv6" +
                        "nIrsrY857oouY3PmVZgY+mm/4KlZqiEzkyvuZ/XNjIoyNl191cQ0lnGt7etl" +
                        "RZGaqcuiz1bmTrPtn1vTE3zk1RTRk1mbL27ccS8raqPJ7OulZ7smawsqzZ3w" +
                        "8dOSlasL7sbIbt+1TvCfX/iaZ5JVeiZzV+RfDF3Gdj7EM/9La3yhxioNm/S9" +
                        "gbdLX0XXXC+9wvkr9V3e510BZbczpxzZdzx1sd+O3SqVO3cd67+VnXYy/8rp" +
                        "c10BbmmbH3oGFm9zVcj4OvP5z0neD6sZny24GL24zvCV8l4Xk3OTUlev03p/" +
                        "Jf3FfI4Mka4CSY+EeceeZDLwzGi42q33KePhs+tvlxwriT8vzuMyzVr4xZcV" +
                        "LMEiSybbdszm8P9sEtpx6Umyopeic7V8m+QuXebIO1nFNkr+Pc/eFHx5y35E" +
                        "yfuw4azHtYezn792e7GZadWSl1UnDiyJfdWXdm7jiTnJE4++Net+ky92M1hM" +
                        "pejJL6VevomL/6t8OlcvmBkQttj5yYv7rpc+WzxJfMx5VnqLmRvPu3cKQQ7r" +
                        "Gj4yvUtsUvjE6pOU1b6vZkf/S1kV5hmMbUxHmG04Dvo0lHAEPbwhmvBEHZRb" +
                        "tuou+/OOiYHhNSu+3CINxPDMmpuYmaeXnV+Sk5kXn5ufUpqTmpyQkJAGxCxJ" +
                        "fmwaAUkXkhjAOfGr0p69wkCdEuCcyMgkwoAwHTmXgooCVICrYEA3Bdn1Qigm" +
                        "1GPN3+j6kV0ojaJ/LTNeHwd4s7KBlDED4XkgzQn2AQCe5Ge97gQAAA=="
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val publicReified =
                fooClass.assertMethod("publicReified", listOf("int", "java.lang.String"))
            assertThat(publicReified.targetLanguages).isEqualTo(TargetLanguageSet.KOTLIN_ONLY)
            assertThat(publicReified.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalReified =
                fooClass.assertMethod("internalReified", listOf("int", "java.lang.String"))
            assertThat(internalReified.targetLanguages).isEqualTo(TargetLanguageSet.KOTLIN_ONLY)
            assertThat(internalReified.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            assertThat(fooClass.methods()).hasSize(2)
        }
    }

    @Test
    fun `Test compiler generated default version of top level reified functions with optional parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    internal inline fun <reified T> internalReified(i: Int = 0, s: String = "") = Unit
                    inline fun <reified T> publicReified(i: Int = 0, s: String = "") = Unit
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9KBCX" +
                        "pBaX6Bdkp+uHABneJXrJOYnFxWtDfP2FA0Vs89Nu7QubsNBpoajgRg4OI4eY" +
                        "zlkSQqVJjs7SZmvill/+9aKkrSSuU/vy1P8H5z88t8PgyFL7UxX3G2pEhcT0" +
                        "nt0rtk87Z/t975n39vZ1TBVdoW2CT4+fsszYPkeqsVyL+VjC+27nhyxKOZbv" +
                        "X//e5x45OSp1xZ88jSbZeLOl9xM0dHZM3Xxt1atTG7Zu3HF6w58939YU/VSW" +
                        "1l+fnW7Inj4jz11rwsLN9TcK/xVa5q34nfTyqOQTiWN7+/ddv9d3pUyfw65j" +
                        "3YsfV04Y7Dc4US56U6DNYM5h3TtpEtqnizbbLOja+cWj7c+D3zzC02+d5t2v" +
                        "WdWYFLHHa661xVQxnj6mwx8c18t5vJ9iMf9MsaZlnaLdYum1c3KyT4cvUpyy" +
                        "4GHPuYWe7Ke3nMxiDrp07cCCR+orBefuvBiR3NH3q/VOoH36DbdzD77c+GOx" +
                        "x3edSfpkw7CbObPkv+YmT1pbnHdDbekalqUcS+//e1X86sydBR+5r295beLX" +
                        "q7j4/R2v2S4RswMPL58dOnHh/ruvr/zN873CHP/e7pPLBu1dQcfVT+aEbnNa" +
                        "WBV76HCo6p5v0ywsVj5dFbj+9c/1S/m6rPcGnOzbr53seTU8UUJzYYrIsYhm" +
                        "Ru98tmmxLlyZXeKaVc52kbeD7H7LKj/rzq/KrvAx841+EhKjZOM8Lzgnecqq" +
                        "K302iiodj/lCp03+blQmliyxPKJ+T6VT1KxNdvWup+7M8Jro/1xd6/lNH5uL" +
                        "yQcf2Dw8q8qj1vV8R/fzHd7L7yxNzhMyqSx2vMbqeFSaQ+uvuKNYYaQ4i+h6" +
                        "YZebU1tqGfl++p66s9R49q3ml4/mdWeKfzd8/IgxLdbGpbL/n8qMw4ttP0is" +
                        "lGiV+Gz4ifNB59w/3KDEO3HSId4qJgaGPyz4Eq80EMPzTm5iZp5edn5JTmZe" +
                        "fG5+SmlOanJCQkIaELMk+bFpPBB69EjDE5iXtE/4iSdxM27a4z1FYosEOKvo" +
                        "N+0TUwWapQPOKoxMIgwI+5CzESivogJcORfdFGT/iKKYUI8rA6IbgexIaRQj" +
                        "bJjxBkOANysbSBkzEF4C0quYQTwAWP/HnJIEAAA="
                ),
        ) {
            val testKtClass = codebase.assertClass("test.pkg.TestKt")

            val publicReified =
                testKtClass.assertMethod("publicReified", listOf("int", "java.lang.String"))
            assertThat(publicReified.targetLanguages).isEqualTo(TargetLanguageSet.KOTLIN_ONLY)
            assertThat(publicReified.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalReified =
                testKtClass.assertMethod("internalReified", listOf("int", "java.lang.String"))
            assertThat(internalReified.targetLanguages).isEqualTo(TargetLanguageSet.KOTLIN_ONLY)
            assertThat(internalReified.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)

            assertThat(testKtClass.methods()).hasSize(2)
        }
    }
}
