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

package com.android.tools.metalava

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class OverridingMethodTest : DriverTest() {

    @Test
    fun `Test removed class overriding method from public interface marked as @Hide`() {
        check(
            apiSurface = KnownApiSurface.MODULE_LIB,
            extraArguments = arrayOf("--format-defaults", "add-additional-overrides=yes"),
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface PublicInterface {
                                void method();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;
                            import android.annotation.RemovedFromApi;
                            import android.annotation.SystemApi;

                            @RemovedFromApi
                            @SystemApi
                            public class RemovedClass implements PublicInterface {
                                @Hide
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature = "",
            // RemovedClass is part of the system API surface (not module-lib) and its
            // overriding method is marked @Hide, overriding a public interface method.
            // Therefore, neither the class nor the method is emitted into the module-lib
            // removed API signature file, even with add-additional-overrides=yes.
            removedApi = "",
        )
    }

    @Test
    fun `Test system class overriding method from removed public class marked as @Hide`() {
        check(
            apiSurface = KnownApiSurface.MODULE_LIB,
            extraArguments = arrayOf("--format-defaults", "add-additional-overrides=yes"),
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            public class RemovedPublicClass {
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;
                            import android.annotation.SystemApi;

                            @SystemApi
                            public class SystemClass extends RemovedPublicClass {
                                @Hide
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedIssues =
                """
                    src/test/pkg/SystemClass.java:7: warning: Public class test.pkg.SystemClass stripped of unavailable superclass test.pkg.RemovedPublicClass [HiddenSuperclass]
                """,
            expectedApiSignature = "",
            // SystemClass is part of the system API surface (not module-lib) and its
            // overriding method is marked @Hide, overriding a method from a removed public
            // class. Therefore, neither the class nor the method is emitted into the
            // module-lib removed API signature file, even with add-additional-overrides=yes.
            removedApi = "",
        )
    }

    @Test
    fun `Test public class overriding method from superclass marked as @Hide with specialized return type`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Middle.java:8: error: Attempting to hide method test.pkg.Middle.method() which overrides method test.pkg.Base.method() which is already part of the API [HidingApiMethodOverride]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Base<T> {
                                public T method() {
                                    return null;
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;

                            public class Middle extends Base<String> {
                                @Hide
                                @Override
                                public String method() {
                                    return null;
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Sub extends Middle {
                                @Override
                                public String method() {
                                    return null;
                                }
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Base<T> {
                        ctor public Base();
                        method public T method();
                      }
                      public class Middle extends test.pkg.Base<java.lang.String> {
                        ctor public Middle();
                        method public String method();
                      }
                      public class Sub extends test.pkg.Middle {
                        ctor public Sub();
                      }
                    }
                """,
        )
    }
}
