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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_STUB_PACKAGES
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Edge case tests of [ANDROID_FLAGGED_API] that cannot be tested in [ParameterizedFlaggedApiTest].
 */
class FlaggedApiEdgeCasesTest : DriverTest() {
    @Test
    fun `Test override flagged method from source path no previously released API`() {
        check(
            // Revert all FlaggedApi annotations.
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            extraArguments =
                arrayOf(
                    // Ignore any classes other than test.pkg.
                    ARG_STUB_PACKAGES,
                    "test.pkg*"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import $ANDROID_FLAGGED_API;

                            @$ANDROID_FLAGGED_API("flag.name")
                            public final class Test {
                                private Test() {}
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            expectedFail =
                "Aborting: Inconsistent options: API flags are provided in a --config-file but no previously released API is provided via --check-compatibility:api:released or --check-compatibility:removed:released",
        )
    }

    @Test
    fun `Test override flagged method from source path with previously released API`() {
        check(
            // Revert all FlaggedApi annotations.
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            extraArguments =
                arrayOf(
                    // Ignore any classes other than test.pkg.
                    ARG_STUB_PACKAGES,
                    "test.pkg*"
                ),
            sourceFiles =
                arrayOf(
                    // A class that will be ignored during the initial codebase creation. However,
                    // as it is referenced from test.pkg.Test class below it will be loaded in later
                    // and that will result in it having an origin of ClassOrigin.SOURCE_PATH
                    // instead of ClassOrigin.COMMAND_LINE like test.pkg.Test.
                    java(
                        """
                            package other.pkg;

                            public abstract class Other {
                                @$ANDROID_FLAGGED_API("flag.name")
                                public abstract void method();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class Test extends other.pkg.Other {
                                private Test() {}
                                // Overrides the flagged method in other.pkg.Other. The flagged
                                // status of the overridden method should not be ignored because
                                // while the containing class is not contributing to this API a
                                // previously released API is provided so reverting will result in
                                // the correct behavior.
                                @Override public void method() {}
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test extends other.pkg.Other {
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class Test extends other.pkg.Other {
                            Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test reverting class with --show-single-annotation`() {
        check(
            // Use an empty api flags which defaults all flags to disabled.
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            extraArguments =
                arrayOf(
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "android.annotation.SystemApi",
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                            * @hide
                            */
                            @$ANDROID_FLAGGED_API("flag.name")
                            @$ANDROID_SYSTEM_API
                            public class Test {
                                // A member of a class that is annotated with a show annotation but
                                // is not marked as @hide. Usually, that would usually report an
                                // error but the show annotation is a --show-single-annotation
                                // so the @hide is not required.
                                @$ANDROID_SYSTEM_API
                                public void method() {}
                            }
                        """
                    ),
                    flaggedApiSource,
                    systemApiSource,
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        method public void method();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test javadoc for flagged class includes @apiSince`() {
        check(
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                            * Javadoc for Test
                            */
                            @$ANDROID_FLAGGED_API("flag.name")
                            public class Test {
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                      <class name="test/pkg/Test" since="31">
                      </class>
                    </api>
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * Javadoc for Test
                             * @apiSince 31
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test unresolvable flag field`() {
        check(
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @$ANDROID_FLAGGED_API(UnresolvableFlag.FLAG_NAME)
                            public class Test {
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            api =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """,
        )
    }
}
