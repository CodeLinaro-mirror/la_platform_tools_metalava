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

package com.android.tools.metalava.model.testsuite.fielditem

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Common tests for implementations of [FieldItem]. */
@RunWith(Parameterized::class)
class CommonFieldItemTest : BaseModelTest() {

    @Test
    fun `Test access type parameter of outer class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public class Outer.Middle.Inner {
                        field public O field;
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}

                        public class Middle {
                            private Middle() {}
                            public class Inner {
                                private Inner() {}
                                public O field;
                            }
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val fieldType =
                codebase.assertClass("test.pkg.Outer.Middle.Inner").assertField("field").type()

            fieldType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test handling of Float MIN_NORMAL`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        field public static final float MIN_NORMAL1 = 1.17549435E-38f;
                        field public static final float MIN_NORMAL2 = 1.1754944E-38f;
                        field public static final float MIN_NORMAL3 = 0x1.0p-126f;
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}

                        public static final float MIN_NORMAL1 = 1.17549435E-38f;
                        public static final float MIN_NORMAL2 = 1.1754944E-38f;
                        public static final float IN_NORMAL3 = 0x1.0p-126f;
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val minNormalBits = java.lang.Float.MIN_NORMAL.toBits()
            for (field in testClass.fields()) {
                val value = field.initialValue(true) as Float
                val valueBits = value.toBits()
                assertEquals(
                    minNormalBits,
                    valueBits,
                    message =
                        "field ${field.name()} - expected ${Integer.toHexString(minNormalBits)}, found ${Integer.toHexString(valueBits)}"
                )

                val written =
                    StringWriter()
                        .apply {
                            PrintWriter(this).use { out -> field.writeValueWithSemicolon(out) }
                        }
                        .toString()

                assertEquals(" = 1.17549435E-38f;", written, message = "field ${field.name()}")
            }
        }
    }
}
