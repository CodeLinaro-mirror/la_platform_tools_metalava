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
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CommonValueClassTest : BaseModelTest() {
    @Test
    fun `Constructor visibility`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    @JvmInline
                    value class PublicConstructor(val value: Int)

                    @JvmInline
                    value class PrivateConstructor private constructor(val value: Int)
                """
            )
        ) {
            val publicConstructorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicConstructor = publicConstructorClass.constructors().single()
            assertTrue(publicConstructor.isPrimary)
            assertTrue(publicConstructor.modifiers.isPublic())

            val publicConstructorProperty = publicConstructorClass.properties().single()
            assertTrue(publicConstructorProperty.isPublic)
            assertNotNull(publicConstructorProperty.constructorParameter)
            assertNotNull(publicConstructorProperty.backingField)

            val privateConstructorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateConstructor = privateConstructorClass.constructors().single()
            assertTrue(privateConstructor.isPrimary)
            assertTrue(privateConstructor.modifiers.isPrivate())

            val privateConstructorProperty = privateConstructorClass.properties().single()
            // The constructor is private, but the property is public
            assertTrue(privateConstructorProperty.isPublic)
            assertNotNull(privateConstructorProperty.constructorParameter)
            assertNotNull(privateConstructorProperty.backingField)
        }
    }

    @Test
    fun `Secondary constructor`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @JvmInline
                    value class ValueClass(val value: Int) {
                        constructor(v1: Int, v2: Int) : this(v1 + v2)
                    }
                """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            assertEquals(valueClass.constructors().size, 2)

            val primaryConstructor = valueClass.assertConstructor("int")
            assertTrue(primaryConstructor.isPrimary)
            assertTrue(primaryConstructor.modifiers.isPublic())

            val secondaryConstructor = valueClass.assertConstructor("int,int")
            assertFalse(secondaryConstructor.isPrimary)
            assertTrue(secondaryConstructor.modifiers.isPublic())
        }
    }

    fun `Constructor with optional value`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @JvmInline
                value class ValueClass(val value: Int = 0)
            """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            assertEquals(valueClass.constructors().size, 1, "Expected exactly one constructor")
            assertNotNull(valueClass.primaryConstructor, "Expected a primary constructor")

            val primaryConstructor = valueClass.constructors().single()
            assertTrue(primaryConstructor.isPrimary, "Expected a primary constructor")
            val param = primaryConstructor.parameters().single()
            assertTrue(param.hasDefaultValue(), "Expected a default value")
        }
    }

    @Test
    fun `Property accessors`() {
        // Value class property accessors for non-constructor properties can't be used from Java
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @JvmInline
                    value class ValueClass(val value: Int) {
                        var noAccessors: Int
                            get() = 0
                            set(v: Int) {}
                    }
                """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            val ctorProperty = valueClass.assertProperty("value")
            assertNotNull(ctorProperty.getter)
            assertNull(ctorProperty.setter)
            assertNotNull(ctorProperty.constructorParameter)
            assertNotNull(ctorProperty.backingField)

            val noAccessorsProperty = valueClass.assertProperty("noAccessors")
            assertNull(noAccessorsProperty.getter)
            assertNull(noAccessorsProperty.setter)
            assertNull(noAccessorsProperty.constructorParameter)
            assertNull(noAccessorsProperty.backingField)
        }
    }

    @Test
    fun `Modifiers on APIs using value classes in an interface`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline
                    value class IntValue(private val value: Int)
                    interface Interface {
                        val abstractVal: IntValue
                        fun abstractFun(): IntValue
                        val defaultVal: IntValue
                            get() = IntValue(0)
                        fun defaultFun(): IntValue = IntValue(0)
                    }
                """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/42WeTQUah/Hh5D1GvugJtmNZbi2i2YSIcZo7NkyhNAY+zK2" +
                        "yXK5Y18rhOSilOwiMoQ0WSca6y2NbWTPloRX973nfavz1nmf5/z+eM55zvd5" +
                        "zu/5Pp/zRSGOMfACmJmZAQDAacDXgxfAAEDqWZyTNzTRhyLPmRjq65lbKCD1" +
                        "D14CABvI3h5jhLwCmQMhD+nvHag1UxxWnpr1UzBCyhkiyYHldWZrRvK+EKPe" +
                        "XlmrtX4oidQ7PUudpQegEMeZK3lkKjWODlA/KtQPjxc4qgBX/wCoz7WrUENs" +
                        "gJUTJtBV4QrGyd8/wuKtubAl78FKX2WfWxSHlwyLO97mQrQKnZ2pTHY0RSHO" +
                        "qui5WcGNqmNsJEk39Rz4r620Wk74x7vTCMdP6Ku8IK1pkdZddY2UeYxaLEuS" +
                        "co7/KiysdfX2Rn7Yxg6ReEg3pUZhzCiYDd39tGL1xqclw8bDVomZLqIbk7Ob" +
                        "sICZPeW9dMkVGpemXWhttyXUUKvqYb3IF4UQFLNb0m8g9rHar2zJqyy/LH39" +
                        "x2lJ4NNrWg3BF0WySDdUOovKUWlAEM0izaM633zQgOz2FKuRR5+ZU5vTL8nJ" +
                        "3ecJut7fYhhF3ig/czIk6hjUb4p6ePwX3ylXupLimLauXg1W9wun+6rF6J1q" +
                        "qy2BwDjtzlVEB1+LYHynJQfXectxhzJF74kgRgXegd6YzaUJtb+Wu8ZL+Wjg" +
                        "0pQ/oGNI43YKP9vtoveaZCE/kHV6Vyy0ocgpaYD7MQM3xRpZS2WK1RUsHtVa" +
                        "H1tSpqtfwAwX7JkKlSqRaMH3MnMfe6kyMA3cdXfx8mwFz6d7SqMTcG+81c3M" +
                        "uObWF2dzp0OT3cYeqEbXZJlgic+WNMbX9nNca3AZjRJ38j0TLVJd2GMm0rZk" +
                        "1IY6zScrcHwTpNW68t0Sq9c+tDYhKa1TN+QHJlVgmeE146xkXS4MFaNtE0Q6" +
                        "nJzjkUjLO/+Z6r76BBJfAtkqhmVHmllb3I91Rw4KldQXWt0/gVc+G16nLs/m" +
                        "7IRWLO+pk4tNwafEZVeBbYwWTTW7S/kcDOPj9c6d+by4tz3S+EhhITS6mCQV" +
                        "XqjP7LAq5nhI8NYesf408fBiKvGXzItmqt1vWXGf80WkbiK4UTyElOuPg2C5" +
                        "9I78YLcKjJWRW4eaOoTCoxawZQWrbWnGUGI2tNNnQocfYi8ltxU/ouQm12y1" +
                        "zel6liyEBL2GjD1COq5qmfQpw/IkE2VyzUZv+qQ5Apa5Vc6E0s3sRpsob+Yn" +
                        "6L5Xzao/5h6zvwcyXp3OKPirew2uj19vl6ix1QJ6f1SJdrTT/vU5z11L0n7C" +
                        "tfhGcqLpGj/2g8quDMMtYTrJDHjnBDQMkJ4prmFcUCj+NnFBsigPjAabB4df" +
                        "fcWD1zm4foE228k1A8cOlBjgZ67cshFvFReOSTqMOVCbBWqw8Ge1N2uFjEDR" +
                        "TKuMM3LyBl1Ndwi64YMV9zhSj/Ot98cvZpwyARAF2l5P0q3M6Ud21FtHyl8k" +
                        "6e+n6JQ1DvMovC4sFilt4lGqZBp/EVAJqYEFwvR24ex3uwk5xC5H3UclnD7E" +
                        "4Mu+ch/viB/ubvYnNsO0KoMCZeSoxQUH3AvphUA2ovZvw3xaOs4DhRRIVOqx" +
                        "+zwhUztS1XPxZ9uVbdXdd1gqOFk8H4q5oEltZUwJl0rlwYIqiwOU0/bhKdmB" +
                        "4g6TAdXvqQne8/ZJzbfWd4LteWkOjS+aQP7pH/K5BMiHf3PGubvmkxgDAFB0" +
                        "/GecAX3HGVc/N6cr/4DGNX3Am9sU2E2sJdqUYz3OaXtVCBUf/10NbHxGNfe5" +
                        "qlcuWifXR5Fz+1yDHKIjTAVMwMcGwza9FcHs7Xn4LF9+U6MLQfMjV+ee5uCI" +
                        "kfgjsDCCuVltIQ7NtB1Pp/LZe7Karxi5ov19ZTwJF8Lqh7abltwFJIuGMz9V" +
                        "VWxeCAgwFx8Q6ts+iKfFouE4Ie6GzuiTyUMSsn5k4TG+aZf6UuA+xyNxb/BO" +
                        "vCZhaJwwLMpt2yIKikFk7pUYz/xKR7+lcaDU36JpR4a/89SU+ag/tpaM1lzf" +
                        "zZsomA/sGSJFmZYFd7U9EArcWJ4ufPvHx0ITN8eTPT2MamL4FKMH0qdGk/3Q" +
                        "LjRMkGvfuRvtg/dtC7oahX4TjBw7kxdxGeFzLGyb9GKvQX2vxlwKexW6uH29" +
                        "KqKlFVeQbdCTOBuHK5PFnRC66hG5iS8aYQ3JRKgEB41AcXUhiT4+6dICwSzC" +
                        "I2Lhk8Swgh3VDkXkLC1QNCuDfX+nY/2eWCr1PDck2bd8OgrzHIKV+qCIjLel" +
                        "hdyCxJfFZsP45BLRHU4xUwp1jwebjDs6I27AUyNTHwSh2sF7TEHr17dkuiPg" +
                        "p99GC/ppNXJRJbHVBJFOSVb5tGf8DXajKGlIFi9WNosPw2hQdHdKxoLP/nct" +
                        "1qazX7zyRq98cI4OADig/5lXJP6nV8TPu7o5BWICDL18MP7/Nk4yqgfLo8QL" +
                        "61fJl+SoY5GXODOuEzOs8BJFcLC+dy+jxEOb0bWDHieJ9aFWy6ZKUYs/Ajqj" +
                        "OaeKgSOGKDE+Fv5WaGHYStPOfOXqWUDLM/84GBRRaH9Rn0X98hPZ9J43wjgJ" +
                        "yDLOt6IHlOTkL1cqSn6WlPF47NIcXL+zCpYRSXeSOSJc44UJnAMKCvplaOLP" +
                        "MoPb70TqsVlJJQO3UaXGsUoUtkq5rojFvWxKGfvyLNb5kUuSKnc6T28zZIdy" +
                        "UvbV8yq/F6khbMiH6mTtxKI3G4WWpKotdUb2/Hf2KzhX7EzDEGHLNmdGJ1SJ" +
                        "9zKwyspY7AmQrZSLHTQqzlAXvJzISkZQm2ESz3XjKB8YBSmHS+vOVMPNnJNG" +
                        "cckWgmOVCcZPOEWHLBb+VBMIFbnNunt3tPlhkvXvM27lRnolwc8IOq/qRtS7" +
                        "iwmToIX+OmmEocfG0wp7hQHLOzQFaSb/6BL47TtfIL/L9P6SBBFhw70t4Zja" +
                        "+q5/ZGwHPPbaxpdjz8XOH7DiHPPZPNSXRujoqtrFGyfjlfRC9BsMbThS10AO" +
                        "J3qlC+4YRN587/UsAC5Kbeyu7qpO3juhFdJG3uf4YoeVlFFgy5Ed5n9qB+Gj" +
                        "+k9C8nLywCpc8w7AeGAdvbxdAjGuV9BotNtRMTibMEmjnAedAX9jaUu05SnP" +
                        "P+BBIejoeQH/Vf86Gn3JX9+OH6Wx71W+Bp/ANwr4H4eq70W+/hGgb0TmGX5M" +
                        "zO9Vvm6kxDcqPMf/z7/0veTX/RP+RlKU5afvgUIwMn3Zxng0vY7uFMnyZfUv" +
                        "M3zUzAELAAA="
                )
        ) {
            val interfaceClass = codebase.assertClass("test.pkg.Interface")
            // Abstract APIs on interface
            assertTrue(interfaceClass.assertProperty("abstractVal").modifiers.isAbstract())
            assertTrue(
                interfaceClass.assertMethod("getAbstractVal-RVb1_dM", "").modifiers.isAbstract()
            )
            assertTrue(
                interfaceClass.assertMethod("abstractFun-RVb1_dM", "").modifiers.isAbstract()
            )
            // Default APIs on interface
            assertTrue(interfaceClass.assertProperty("defaultVal").modifiers.isDefault())
            // TODO(b/428221305): these are abstract in bytecode even though there are default
            // implementations, find out why.
            assertFalse(
                interfaceClass.assertMethod("getDefaultVal-RVb1_dM", "").modifiers.isDefault()
            )
            assertFalse(interfaceClass.assertMethod("defaultFun-RVb1_dM", "").modifiers.isDefault())
        }
    }

    @Test
    fun `Modifiers on APIs using value classes in an abstract class`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline
                    value class IntValue(private val value: Int)
                    abstract class AbstractClass {
                        abstract val abstractVal: IntValue
                        abstract fun abstractFun(): IntValue
                        val finalVal: IntValue = IntValue(0)
                        fun finalFun(): IntValue = IntValue(0)
                    }
                """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTTUbRv+DZM925gsldJYMswMGcQ0mhJZmsHYIjLWLGOJ" +
                        "QeRNgxcpY4tQtmR9LWWdso0iTa899FqKzy4yJElZXvWd833qnLqfc/33PNd9" +
                        "nvvc93XdxobsYBGAi4sLAAApYHeIAGAAr2N2CqFP0EXhTxH0dXVMzZB43a2X" +
                        "ALCCb//7nCEC2bPXEAHvbO+qJCr1qYxN+SIN8Ir6+B7/4irikgHiMtygvV3B" +
                        "YqkTxWS2T0yNT7EBxoacXOUQ+XKNnQTqOzD+ZXrRHVCc/SgoH49LKH0vioU9" +
                        "2d8Z6Ui29/P7w2zUdL+5yNZiR3mHC3Wvpzy3a6iVXhgadMFEPjlsABlpkfOc" +
                        "mJnykJ2XKeuinqZ1rHG2UkDr8/0JQ7svpEsiYpiJw43r6hq0GbJaBPctlTQ/" +
                        "FvZqI+veSsbVlbWmpm3QmNrAnsTMqeD1L4sWb33qE63crJW5QH+0kdPWY+bI" +
                        "U4e8F847oyLjcVmWF1YlaipV3SznoVRDcdiFBd2apg4em8VVBPr9y/z+aClZ" +
                        "wQYPTE2g0eHbzBR0S06xcbyg2KxZvNujDNPusz0uDV4ad9mS0irTOmUFhDvc" +
                        "xa531utTe1aKTxy8QmVH+Y6Nb3PyXx5zBuXlhjNa2zV4XPWkOh7B2OwrH5kL" +
                        "CkbiWliGz6D14lEt5nuFzpgP2RYoeQ8H7EGKdLWHf1wYVnvzvnUoHzormU+L" +
                        "Rg3izzUP7OO9l/NOs0fCV8wyoTUCVZNjf6tLuBYsPGCJrxzniNAWz/0Hszy4" +
                        "oAKqniP3ZX41kchXZs4GFial13qqgjm67rs6ebo3Ss4kuB8lxQS99VYnEoWm" +
                        "l+en0ieCY10G/1INq7hN8Gp6uqAxtLSZ5lwRlEiXyc5wv2kW58QXPhy/Kq/2" +
                        "qsV0pDQIOsxkVRWv51n0+8wyJOQwh1IQXSNobFJIxRBPj7YQeZyMswpgbo9M" +
                        "Q2Ti757ZGHdlPYFH5cFXc7HJ14iWZkURrvhuibzqLIuiA6EqJ0Oq1BG8DvYk" +
                        "peK/qxQjaKG0yOSHklYG8yaabflQW/2oKJ1TJzbmv356TS9DzgWH5TLlQrJ0" +
                        "uWxZMLvtG96415ZfhkuM4pr4k4yIqm2jPEEbGYfl7hgKG0Nu0K7XBmDT2ez2" +
                        "SbqUki0MXJ6pqcMHIGqUVQtsZX0deSB8BZcwGdxX4nU+lpFbNpAeW7HKmNZ2" +
                        "z5u7EtAPHyzD27EwhA4V7F3Zm/LpxH/u+MTbAe+F0SeCQZPrYQSVjxkx2u9U" +
                        "b1ezu4ZvfhU7x5pIzHzTtqSlG7rcLFNhjRH0/owOs7uAO/Ycct+cuRnjEUXv" +
                        "uWmytM/rA3pdHpy6HySbqNUyjLoKJCRJa5zLzJIevTknm3NXkiRpGhhyqRcS" +
                        "enrrut7sVIvQpJZXV97Z0EnHVCvpRun94be2w7fUpgQ1uPfdbq7DXHmNInGw" +
                        "9kwqIs62Ps6+oR3SXVq4N44TutwZNZ94iAA0iTL6R0CL07rXnlVbXkMYMXU3" +
                        "aacL6H0QZH9W7uH8xxDlco6hF5RyeAXWH6uzrsV3v+1GWlOrnXZZnoBPU+DF" +
                        "y4qfs6W31z923qzDYsoD/OUVx3Mzt4TnErIEeZtwx/ugmNMOXVkDcGocexHk" +
                        "ytia3KPpqJPNKtbqrmvcpQLc7iUwJxKTUcARcz4fISmOnu8akLIJoSX7S9uO" +
                        "UB69G4/xnrG5VZe6vBZoIzJrS3/xWMwv4UOGkGjP9nedcWir+AIDA0AO5+90" +
                        "5sBunTnl4EfxtXekaH+Tmf+KjSfR0PsVTnArYOI4LamG0HuWmG0smbe3Sl/B" +
                        "SyebWPTUnIdI5SrinJTOp19egNcVHdfdgBz3ofIKR8ewxzyNPfRnqn9voAAG" +
                        "ozIaUlmpsrI2Wj4KSGa3K8nbShl+7XjTa41/UWN6Z74RdobtVvOQH/+9RBa4" +
                        "Lm+mGluLhEQrzwo0MJLixBxWePvR4914Vz0lJ5rpILVE81ob/rC+1Tt7XBrG" +
                        "X7xGtGGgsSu7Y5g3QEL+AT5VzF7pbTB1AhJFFVLiUOCm8em8DMwkvbBEovyS" +
                        "tR7Ov4Qdkjk/UjaQMoquuf+BHHlPpUVNs5k7Bdb7fIkJKYeaX5ZVMnWL7F0L" +
                        "601RyIiEHQxOqOV36NPKP0PkNu7Hf2hau62Dn4p1jkQ1LNKZKyOTFuUevuEC" +
                        "fDC39qmrb6AXXYzGN400az9PRzgTVio+URa7pBwpwbZsaJjz3F0NhdLsSkZ0" +
                        "nvdMaSrbxbWTHkLZR7j6ClRhBJ9mA76gtnj9ZGluNcWGbWpBO71n+7ipi1lv" +
                        "CTzoz6K4B/eSLhKz4KIuT4Lhkdb+6Do9/ME9CFWZDi8smc7nCEX5ZPlbq9Zu" +
                        "9hatTzItnj5x/2PY9FO3WyMX5v2Tuv/I5z58w/3gte9fmUtctrxhxpq9uTq9" +
                        "Lm7KVYUzVtVyD1r1CxDSQXElbRGvjJSIG9y5YpiWWQGnpoTwCS2mSaG4m7tj" +
                        "guxjPYtgHI5/Aqx8jnTsSUFfLN8HikDinb7C/qRW1otrWELx2c6WIqflU8eO" +
                        "iCITzU1mmM9lnGh5evtu8NQfIac/VMBED9LdJ9JjxW3koOrk9vFp/lQGYuRt" +
                        "cyGDzjgIsiURxsoYEYwHjFTcRYZwqmiq8HnBoxuc35rZRlAHF84GAHzg3zXz" +
                        "/h38z7M97d28kB7eFLKbl52nt5M/2dmRRCK57ADsQOA4auzQ7QB8H5TVI/UN" +
                        "kJ2XYt8NGcQmAvyffbdZf9sIfoxf7Qc/s+weRdEfGEJ/bfM/k+wuwYEfSGbA" +
                        "v5/hn5l2f3j/D0xVnL8toLHhHo5v18A7RxIEAILf+IB/AYtki2ZECQAA"
                )
        ) {
            val abstractClass = codebase.assertClass("test.pkg.AbstractClass")
            // Abstract APIs on abstract class
            assertTrue(abstractClass.assertProperty("abstractVal").modifiers.isAbstract())
            assertTrue(
                abstractClass.assertMethod("getAbstractVal-RVb1_dM", "").modifiers.isAbstract()
            )
            assertTrue(abstractClass.assertMethod("abstractFun-RVb1_dM", "").modifiers.isAbstract())
            // Final APIs on abstract class
            assertTrue(abstractClass.assertProperty("finalVal").modifiers.isFinal())
            assertTrue(abstractClass.assertMethod("getFinalVal-RVb1_dM", "").modifiers.isFinal())
            assertTrue(abstractClass.assertMethod("finalFun-RVb1_dM", "").modifiers.isFinal())
        }
    }
}
