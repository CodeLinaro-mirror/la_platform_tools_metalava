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
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.lint.ARG_ALLOWED_ACRONYM
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class AcronymNamesTest : DriverTest() {
    @Test
    fun `Test two letter acronyms`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                "src/android/pkg/Foo.java:7: warning: Acronyms should not be capitalized in method names: was `getID`, should this be `getId`? [AcronymName]",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            public class Foo {
                                @Nullable
                                public String getID() { return null; }
                                public void setZOrderOnTop() { } // OK
                            }
                        """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Test longer acronyms`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/HTMLWriter.java:3: warning: Acronyms should not be capitalized in class names: was `HTMLWriter`, should this be `HtmlWriter`? [AcronymName]
                src/android/pkg/HTMLWriter.java:4: warning: Acronyms should not be capitalized in method names: was `fromHTMLToHTML`, should this be `fromHtmlToHtml`? [AcronymName]
                src/android/pkg/HTMLWriter.java:5: warning: Acronyms should not be capitalized in method names: was `toXML`, should this be `toXml`? [AcronymName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            public class HTMLWriter {
                                public void fromHTMLToHTML() { }
                                public void toXML() { }
                            }
                        """
                    ),
                )
        )
    }

    @Test
    fun `Test all caps class name`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                "src/android/pkg/ALL_CAPS.java:3: warning: Acronyms should not be capitalized in class names: was `ALL_CAPS`, should this be `AllCaps`? [AcronymName]",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            public class ALL_CAPS { // like android.os.Build.VERSION_CODES
                            }
                        """
                    ),
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test acronyms in top level kotlin function`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                "src/Dp.kt:3: warning: Acronyms should not be capitalized in method names: was `badCALL`, should this be `badCall`? [AcronymName]",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            inline class Dp(val value: Float)
                            fun greatCall(width: Dp)
                            fun badCALL(width: Dp)
                        """
                    ),
                )
        )
    }

    @Test
    fun `Test names against previous API`() {
        check(
            apiLint =
                """
                package android.pkg {
                  public class badlyNamedClass {
                    ctor public badlyNamedClass();
                    method public void BadlyNamedMethod1();
                    method public void fromHTMLToHTML();
                    method public String getID();
                    method public void toXML();
                    field public static final int BadlyNamedField = 1; // 0x1
                  }
                }
                """,
            expectedIssues =
                """
                src/android/pkg/badlyNamedClass.java:8: warning: Acronyms should not be capitalized in method names: was `toXML2`, should this be `toXml2`? [AcronymName]
                src/android/pkg2/HTMLWriter.java:3: warning: Acronyms should not be capitalized in class names: was `HTMLWriter`, should this be `HtmlWriter`? [AcronymName]
                src/android/pkg2/HTMLWriter.java:4: warning: Acronyms should not be capitalized in method names: was `fromHTMLToHTML`, should this be `fromHtmlToHtml`? [AcronymName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            public class badlyNamedClass {
                                public static final int BadlyNamedField = 1;

                                public void fromHTMLToHTML() { }
                                public void toXML() { }
                                public void toXML2() { }
                                public String getID() { return null; }
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg2;

                            public class HTMLWriter {
                                public void fromHTMLToHTML() { }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test long acronym following two letter acronym`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                "src/test/pkg/IHaveTwoThenFOUR.java:2: warning: Acronyms should not be capitalized in class names: was `IHaveTwoThenFOUR`, should this be `IHaveTwoThenFour`? [AcronymName]",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class IHaveTwoThenFOUR {} // IH is okay, FOUR is not
                        """
                    )
                )
        )
    }

    @Test
    fun `Test acronyms followed by non-letters`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/test/pkg/Foo.java:3: warning: Acronyms should not be capitalized in method names: was `usingNUMBER123`, should this be `usingNumber123`? [AcronymName]
                src/test/pkg/Foo.java:4: warning: Acronyms should not be capitalized in method names: was `usingUNDERSCORE_`, should this be `usingUnderscore_`? [AcronymName]
                src/test/pkg/Foo.java:5: warning: Acronyms should not be capitalized in method names: was `usingDOLLAR${'$'}`, should this be `usingDollar${'$'}`? [AcronymName]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public void usingNUMBER123() {}
                                public void usingUNDERSCORE_() {}
                                public void usingDOLLAR$() {}
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test allowed acronyms`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                "src/test/pkg/NOTSQL.java:2: warning: Acronyms should not be capitalized in class names: was `NOTSQL`, should this be `Notsql`? [AcronymName]",
            extraArguments = arrayOf(ARG_ALLOWED_ACRONYM, "SQL", ARG_ALLOWED_ACRONYM, "SQ"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class SQLException {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class SupportSQLiteProgram {
                                public void execSQL() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class NOTSQL {}
                        """
                    )
                )
        )
    }
}
