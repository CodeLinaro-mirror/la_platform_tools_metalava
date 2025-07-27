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
                    cls.assertConstructor(""),
                    cls.assertMethod("fooMethod", ""),
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

            val ctorImpl = intValue.assertMethod("constructor-impl", "int")
            ctorImpl.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            assertThat(ctorImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(ctorImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(ctorImpl.modifiers.isStatic()).isTrue()

            val boxImpl = intValue.assertMethod("box-impl", "int")
            boxImpl.returnType().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("test.pkg.IntValue")
            }
            assertThat(boxImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(boxImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(boxImpl.modifiers.isStatic()).isTrue()

            val unboxImpl = intValue.assertMethod("unbox-impl", "")
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
            val ctor = intValue.assertConstructor("int")
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

            val mangledMethod = fooClass.assertMethod("usesIntValue-Vxmw0xk", "int")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only and have IntValue param
            // type instead of int
            fooClass.assertMethod("usesIntValue", "int")

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

            val mangledMethod = intValue.assertMethod("foo-impl", "int")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only
            intValue.assertMethod("foo", "")

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

            val mangledMethodA = foo.assertMethod("foo-GHfTWwk", "int")
            assertThat(mangledMethodA.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val mangledMethodB = foo.assertMethod("foo-wveqTnY", "int")
            assertThat(mangledMethodB.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled methods should have distinct signatures
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
            fooClass.assertMethod("foo", "java.lang.Object")
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

            val mangledMethod = barClass.assertMethod("foobar-RVb1_dM", "")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only and have IntValue return
            // type instead of int
            barClass.assertMethod("foobar", "")

            assertThat(barClass.methods()).hasSize(2)
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

            val publicMangledMethod = fooClass.assertMethod("publicValueClassFunction-RVb1_dM", "")
            assertThat(publicMangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicMangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            val protectedMangledMethod =
                fooClass.assertMethod("protectedValueClassFunction-RVb1_dM", "")
            assertThat(protectedMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(protectedMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedMangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            val internalMangledMethod =
                fooClass.assertMethod("internalValueClassFunction-RVb1_dM", "")
            assertThat(internalMangledMethod.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(internalMangledMethod.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            internalMangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }

            // There isn't a private mangled method since those don't need to be tracked.

            // TODO(b/407735992): non-mangled methods should be kotlin only and have IntValue return
            // type instead of int
            fooClass.assertMethod("publicValueClassFunction", "")
            fooClass.assertMethod("protectedValueClassFunction", "")
            fooClass.assertMethod("internalValueClassFunction", "")
            fooClass.assertMethod("privateValueClassFunction", "")

            assertThat(fooClass.methods()).hasSize(7)
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

            val ctorImpl = intValue.assertMethod("constructor-impl", "int")
            assertThat(ctorImpl.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(ctorImpl.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.INTERNAL)

            // Value class constructor can only be used from kotlin
            val ctor = intValue.assertConstructor("int")
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
                fooClass.assertMethod("getPublicValueClassProperty-RVb1_dM", "")
            assertThat(publicMangledGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicMangledGetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicMangledGetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val publicMangledSetter =
                fooClass.assertMethod("setPublicValueClassProperty-Vxmw0xk", "int")
            assertThat(publicMangledSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicMangledSetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            publicMangledSetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val protectedMangledGetter =
                fooClass.assertMethod("getProtectedValueClassProperty-RVb1_dM", "")
            assertThat(protectedMangledGetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(protectedMangledGetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedMangledGetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val protectedMangledSetter =
                fooClass.assertMethod("setProtectedValueClassProperty-Vxmw0xk", "int")
            assertThat(protectedMangledSetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(protectedMangledSetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            protectedMangledSetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val internalMangledGetter =
                fooClass.assertMethod("getInternalValueClassProperty-RVb1_dM", "")
            assertThat(internalMangledGetter.targetLanguages)
                .containsExactly(TargetLanguage.BYTECODE)
            assertThat(internalMangledGetter.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            internalMangledGetter.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val internalMangledSetter =
                fooClass.assertMethod("setInternalValueClassProperty-Vxmw0xk", "int")
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

            val noJvmNameGetter = fooClass.assertMethod("getNoJvmName-RVb1_dM", "")
            assertThat(noJvmNameGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val noJvmNameSetter = fooClass.assertMethod("setNoJvmName-Vxmw0xk", "int")
            assertThat(noJvmNameSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnGetGetter = fooClass.assertMethod("getJvmNameOnGet", "")
            assertThat(jvmNameOnGetGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)
            val jvmNameOnGetSetter = fooClass.assertMethod("setJvmNameOnGet-Vxmw0xk", "int")
            assertThat(jvmNameOnGetSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnSetGetter = fooClass.assertMethod("getJvmNameOnSet-RVb1_dM", "")
            assertThat(jvmNameOnSetGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val jvmNameOnSetSetter = fooClass.assertMethod("setJvmNameOnSet", "int")
            assertThat(jvmNameOnSetSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)

            val jvmNameOnBothGetter = fooClass.assertMethod("getJvmNameOnBoth", "")
            assertThat(jvmNameOnBothGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)
            val jvmNameOnBothSetter = fooClass.assertMethod("setJvmNameOnBoth", "int")
            assertThat(jvmNameOnBothSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)
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

            val noJvmNameGetter = fooClass.assertMethod("getNoJvmName-Vxmw0xk", "int")
            assertThat(noJvmNameGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val noJvmNameSetter = fooClass.assertMethod("setNoJvmName-6VC4vj0", "int,int")
            assertThat(noJvmNameSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnGetGetter = fooClass.assertMethod("getJvmNameOnGet", "int")
            assertThat(jvmNameOnGetGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)
            val jvmNameOnGetSetter = fooClass.assertMethod("setJvmNameOnGet-6VC4vj0", "int,int")
            assertThat(jvmNameOnGetSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            val jvmNameOnSetGetter = fooClass.assertMethod("getJvmNameOnSet-Vxmw0xk", "int")
            assertThat(jvmNameOnSetGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val jvmNameOnSetSetter = fooClass.assertMethod("setJvmNameOnSet", "int,int")
            assertThat(jvmNameOnSetSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)

            val jvmNameOnBothGetter = fooClass.assertMethod("getJvmNameOnBoth", "int")
            assertThat(jvmNameOnBothGetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)
            val jvmNameOnBothSetter = fooClass.assertMethod("setJvmNameOnBoth", "int,int")
            assertThat(jvmNameOnBothSetter.targetLanguages)
                .containsExactlyElementsIn(TargetLanguageSet.ALL)
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

            val mangledMethodA = fooClass.assertMethod("fooA-Vxmw0xk", "int")
            assertThat(mangledMethodA.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodA.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            val mangledMethodB = fooClass.assertMethod("fooB-Vxmw0xk", "int")
            assertThat(mangledMethodB.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethodB.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }

            // TODO(b/407735992): non-mangled method should be kotlin only and have IntValue param
            // type instead of int
            fooClass.assertMethod("fooA", "int")
            fooClass.assertMethod("fooB", "int")

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

            val mangledMethod = fooClass.assertMethod("foo-Vxmw0xk", "int")
            assertThat(mangledMethod.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            mangledMethod.returnType().assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.VOID)
            }
            assertThat(mangledMethod.modifiers.isDeprecated()).isTrue()

            // TODO(b/407735992): non-mangled method should be kotlin only and have IntValue param
            // type instead of int
            fooClass.assertMethod("foo", "int")

            assertThat(fooClass.methods()).hasSize(2)
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

            val getter = fooClass.assertMethod("getFoo-RVb1_dM", "")
            assertThat(getter.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(getter.annotationNames()).contains("kotlin.PublishedApi")

            val setter = fooClass.assertMethod("setFoo-Vxmw0xk", "int")
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
            val ctor = anno.assertConstructor("int")
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
            val ctor = fooClass.assertConstructor("int")
            assertThat(ctor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
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
            val publicValueCtor = publicCtorClass.assertConstructor("test.pkg.IntValue")
            assertThat(publicValueCtor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(publicValueCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            val publicIntCtor =
                publicCtorClass.assertConstructor(
                    "int,kotlin.jvm.internal.DefaultConstructorMarker"
                )
            assertThat(publicIntCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            assertThat(publicIntCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalValueCtor = internalCtorClass.assertConstructor("test.pkg.IntValue")
            assertThat(internalValueCtor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
            assertThat(internalValueCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            val internalIntCtor =
                internalCtorClass.assertConstructor(
                    "int,kotlin.jvm.internal.DefaultConstructorMarker"
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

            val kotlinCtor = fooClass.assertConstructor("test.pkg.IntValue")
            assertThat(kotlinCtor.targetLanguages).containsExactly(TargetLanguage.KOTLIN)

            val bytecodeCtor =
                fooClass.assertConstructor("int,kotlin.jvm.internal.DefaultConstructorMarker")
            assertThat(bytecodeCtor.targetLanguages).containsExactly(TargetLanguage.BYTECODE)

            // When a kotlin constructor has a single default parameter, an overload is generated
            // with no parameters.
            val defaultCtor = fooClass.assertConstructor("")
            assertThat(defaultCtor.targetLanguages).containsExactlyElementsIn(TargetLanguageSet.ALL)

            // Check that the bytecode constructor with extra int and DefaultConstructorMarker
            // params is not generated.
            assertThat(fooClass.constructors()).hasSize(3)
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
                fooClass.assertConstructor("int,kotlin.jvm.internal.DefaultConstructorMarker")
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
            val fooGetter = fooClass.assertMethod("getFoo-RVb1_dM", "")
            assertThat(fooGetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val fooSetter = fooClass.assertMethod("setFoo-Vxmw0xk", "int")
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
            val fooGetter = fooClass.assertMethod("getFoo-RVb1_dM", "")
            assertThat(fooGetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooGetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
            val fooSetter = fooClass.assertMethod("setFoo-Vxmw0xk", "int")
            assertThat(fooSetter.annotationNames()).contains("test.pkg.ExperimentalFoo")
            assertThat(fooSetter.targetLanguages).containsExactly(TargetLanguage.BYTECODE)
        }
    }
}
