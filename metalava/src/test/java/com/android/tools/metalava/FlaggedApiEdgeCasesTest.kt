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
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Edge case tests of [ANDROID_FLAGGED_API] that cannot be tested in [ParameterizedFlaggedApiTest].
 */
class FlaggedApiEdgeCasesTest : DriverTest() {
    @Test
    fun `Test override flagged method from source path no previously released API`() {
        check(
            extraArguments =
                arrayOf(
                    // Revert all FlaggedApi annotations.
                    ARG_REVERT_ANNOTATION,
                    ANDROID_FLAGGED_API,
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
                                // status of the overridden method should be ignored because the
                                // containing class is not contributing to this API and there is no
                                // previously released API provided so reverting will result in this
                                // method being removed.
                                @Override public void method() {}
                            }
                        """
                    ),
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class Test extends other.pkg.Other {
                            Test() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test override flagged method from source path with previously released API`() {
        check(
            extraArguments =
                arrayOf(
                    // Revert all FlaggedApi annotations.
                    ARG_REVERT_ANNOTATION,
                    ANDROID_FLAGGED_API,
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
}
