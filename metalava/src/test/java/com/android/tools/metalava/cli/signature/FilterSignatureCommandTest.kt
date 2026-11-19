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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.CONFIG_FILE_OPTIONS_HELP
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.model.text.assertSignatureContents
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.xml
import java.io.File
import org.junit.Test

/** Name of the `--output-file` option. */
private const val ARG_OUTPUT_FILE = "--output-file"

/** Expected help text for the `filter-signature` sub-command. */
private val filterSignatureHelp =
    """
Usage: metalava filter-signature [options]

  Filter signature files using configured API flags.

  Reads signature files defining an API surface (and optionally its removed API) and writes filtered signature files
  after applying any `<api-flags>` from `--config-file`, reverting flagged changes against `--previously-released-api`
  and `--previously-released-removed-api` if provided.

Options:
  --api-files <files>                        One or more signature files (separated by `${File.pathSeparator}`) defining the current API
                                             from narrowest to widest API surface. The last file is the target signature
                                             file to filter; any preceding files are base surface signature files that
                                             it extends. (required)
  --removed-api-files <files>                One or more signature files (separated by `${File.pathSeparator}`) defining the current removed
                                             API from narrowest to widest API surface, corresponding to the surfaces in
                                             `--api-files`.
  --previously-released-api <files>          One or more signature files (separated by `${File.pathSeparator}`) defining the previously
                                             released API from narrowest to widest API surface, used when reverting
                                             flagged changes to previously released APIs.
  --previously-released-removed-api <files>  One or more signature files (separated by `${File.pathSeparator}`) defining the previously
                                             released removed API from narrowest to widest API surface, used when
                                             reverting flagged changes to previously released removed APIs.
  --output-file <file>                       The output file into which the filtered signature file will be written.
                                             (required)
  --output-removed-file <file>               The output file into which the filtered removed signature file will be
                                             written.
  -h, -?, --help                             Show this message and exit

$CONFIG_FILE_OPTIONS_HELP

$ISSUE_REPORTING_OPTIONS_HELP
    """
        .trimIndent()

/** Tests for [FilterSignatureCommand]. */
class FilterSignatureCommandTest :
    BaseCommandTest<FilterSignatureCommand>({ FilterSignatureCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("filter-signature", "--help")

            expectedStdout = filterSignatureHelp
        }
    }

    @Test
    fun `Test standalone signature file filtering with kept, finalized, and reverted flags`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                <api-flag package="test.pkg" name="disabled_flag" mutability="immutable" status="disabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                ctor @FlaggedApi("test.pkg.disabled_flag") public Foo(int);
                                method @FlaggedApi("test.pkg.kept_flag") public void keptMethod();
                                method @FlaggedApi("test.pkg.finalized_flag") public void finalizedMethod();
                                method @FlaggedApi("test.pkg.disabled_flag") public void disabledMethod();
                                method @FlaggedApi("test.pkg.unlisted_flag") public void unlistedMethod();
                                field @FlaggedApi("test.pkg.kept_flag") public static final int KEPT_FIELD = 1; // 0x1
                                field @FlaggedApi("test.pkg.finalized_flag") public static final int FINALIZED_FIELD = 2; // 0x2
                                field @FlaggedApi("test.pkg.disabled_flag") public static final int DISABLED_FIELD = 3; // 0x3
                              }
                              @FlaggedApi("test.pkg.kept_flag") public class KeptClass {
                                ctor public KeptClass();
                                method public void keptClassMethod();
                                method @FlaggedApi("test.pkg.disabled_flag") public void disabledMemberInKeptClass();
                              }
                              @FlaggedApi("test.pkg.finalized_flag") public class FinalizedClass {
                                ctor public FinalizedClass();
                                method public void finalizedClassMethod();
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public class DisabledClass {
                                ctor public DisabledClass();
                                method public void disabledClassMethod();
                              }
                              public class DisabledClass.NestedInDisabledClass {
                                ctor public DisabledClass.NestedInDisabledClass();
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class FinalizedClass {
                            ctor public FinalizedClass();
                            method public void finalizedClassMethod();
                          }
                          public class Foo {
                            ctor public Foo();
                            method public void finalizedMethod();
                            method @FlaggedApi("test.pkg.kept_flag") public void keptMethod();
                            field public static final int FINALIZED_FIELD = 2; // 0x2
                            field @FlaggedApi("test.pkg.kept_flag") public static final int KEPT_FIELD = 1; // 0x1
                          }
                          @FlaggedApi("test.pkg.kept_flag") public class KeptClass {
                            ctor public KeptClass();
                            method public void keptClassMethod();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test superclass and interface promotion when flagged supertypes are dropped`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class BaseClass {
                                ctor public BaseClass();
                              }
                              public interface BaseInterface {
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public class FlaggedSuperClass extends test.pkg.BaseClass {
                                ctor public FlaggedSuperClass();
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public interface FlaggedInterface extends test.pkg.BaseInterface {
                              }
                              public class SubClass extends test.pkg.FlaggedSuperClass implements test.pkg.FlaggedInterface {
                                ctor public SubClass();
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class BaseClass {
                            ctor public BaseClass();
                          }
                          public interface BaseInterface {
                          }
                          public class SubClass extends test.pkg.BaseClass implements test.pkg.BaseInterface {
                            ctor public SubClass();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test dropped annotation class removed from annotated items`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("test.pkg.disabled_flag") @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface DroppedAnnotation {
                              }
                              @FlaggedApi("test.pkg.kept_flag") @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface KeptAnnotation {
                              }
                              @test.pkg.DroppedAnnotation @test.pkg.KeptAnnotation public class Foo {
                                ctor public Foo();
                                method @NonNull @test.pkg.DroppedAnnotation @test.pkg.KeptAnnotation public String bar(@Nullable @test.pkg.DroppedAnnotation String);
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @test.pkg.KeptAnnotation public class Foo {
                            ctor public Foo();
                            method @NonNull @test.pkg.KeptAnnotation public String bar(@Nullable String);
                          }
                          @FlaggedApi("test.pkg.kept_flag") @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface KeptAnnotation {
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test reverting flagged changes against previously released API`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            args +=
                ARG_PREVIOUSLY_RELEASED_API to
                    signature(
                        "prev-api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Bar {
                                ctor public Bar();
                                method public void method();
                                field public static int field;
                              }
                              public final class Foo {
                                method public void existingMethod();
                              }
                            }
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "current-api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @Deprecated @FlaggedApi("test.pkg.disabled_flag") public class Bar {
                                ctor @Deprecated public Bar();
                                method @Deprecated public void method();
                                field @Deprecated public static int field;
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public final class Foo {
                                ctor public Foo();
                                method public void existingMethod();
                                method public void newMethod();
                                field public final int newField;
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                            ctor public Bar();
                            method public void method();
                            field public static int field;
                          }
                          public final class Foo {
                            method public void existingMethod();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test delta signature file filtering`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                <api-flag package="test.pkg" name="disabled_flag" mutability="immutable" status="disabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered-system.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "public.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void publicMethod();
                              }
                            }
                        """,
                    )
            args +=
                ARG_API_FILES to
                    signature(
                        "system.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method @FlaggedApi("test.pkg.kept_flag") public void keptSystemMethod();
                                method @FlaggedApi("test.pkg.finalized_flag") public void finalizedSystemMethod();
                                method @FlaggedApi("test.pkg.disabled_flag") public void disabledSystemMethod();
                              }
                              @FlaggedApi("test.pkg.disabled_flag") public class DisabledSystemClass {
                                ctor public DisabledSystemClass();
                                method public void bar();
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void finalizedSystemMethod();
                            method @FlaggedApi("test.pkg.kept_flag") public void keptSystemMethod();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test delta signature file filtering when class moves from system to public behind reverted flag`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            args +=
                ARG_PREVIOUSLY_RELEASED_API to
                    signature(
                        "prev-public.txt",
                        """
                            // Signature format: 2.0
                        """,
                    )
            args +=
                ARG_PREVIOUSLY_RELEASED_API to
                    signature(
                        "prev-system.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public final class Foo {
                              }
                            }
                        """,
                    )

            val output = outputFile("filtered-system.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "public.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("test.pkg.disabled_flag") public final class Foo {
                                ctor public Foo();
                                method public void method();
                                field public final int field;
                              }
                            }
                        """,
                    )
            args +=
                ARG_API_FILES to
                    signature(
                        "system.txt",
                        """
                            // Signature format: 2.0
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public final class Foo {
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test preserving signature format from target signature file`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "api.txt",
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method @FlaggedApi("test.pkg.finalized_flag") public String? finalizedMethod(String);
                                method @FlaggedApi("test.pkg.disabled_flag") public String? disabledMethod(String);
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo {
                            ctor public Foo();
                            method public String? finalizedMethod(String);
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test method overrides of reverted super methods are dropped or reverted`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            args +=
                ARG_PREVIOUSLY_RELEASED_API to
                    signature(
                        "prev-api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                ctor public Base();
                                method public void existingMethod();
                              }
                              public class Sub extends test.pkg.Base {
                                ctor public Sub();
                              }
                            }
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            args +=
                ARG_API_FILES to
                    signature(
                        "current-api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                ctor public Base();
                                method public void existingMethod();
                                method @FlaggedApi("test.pkg.disabled_flag") public void newSuperMethod();
                              }
                              public class Sub extends test.pkg.Base {
                                ctor public Sub();
                                method @FlaggedApi("test.pkg.disabled_flag") public final void existingMethod();
                                method public final void newSuperMethod();
                              }
                            }
                        """,
                    )

            verify {
                output.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Base {
                            ctor public Base();
                            method public void existingMethod();
                          }
                          public class Sub extends test.pkg.Base {
                            ctor public Sub();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test ForRemoval and FlaggedApi transitions between current and removed signature files`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                <api-flag package="test.pkg" name="reverted_flag" mutability="immutable" status="disabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            args +=
                ARG_API_FILES to
                    signature(
                        "current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @ForRemoval @FlaggedApi("test.pkg.finalized_flag") public class FinalizedRemovedClass {
                                ctor public FinalizedRemovedClass();
                                method public void method();
                              }
                              public class Foo {
                                ctor public Foo();
                                method @ForRemoval @FlaggedApi("test.pkg.finalized_flag") public void finalizedRemovedMethod();
                                method @ForRemoval @FlaggedApi("test.pkg.kept_flag") public void keptRemovedMethod();
                                method @ForRemoval @FlaggedApi("test.pkg.reverted_flag") public void revertedRemovedMethod();
                                field @ForRemoval @FlaggedApi("test.pkg.finalized_flag") public static final int FINALIZED_REMOVED_FIELD = 1; // 0x1
                                field @ForRemoval @FlaggedApi("test.pkg.kept_flag") public static final int KEPT_REMOVED_FIELD = 2; // 0x2
                                field @ForRemoval @FlaggedApi("test.pkg.reverted_flag") public static final int REVERTED_REMOVED_FIELD = 3; // 0x3
                              }
                              @ForRemoval @FlaggedApi("test.pkg.kept_flag") public class KeptRemovedClass {
                                ctor public KeptRemovedClass();
                                method public void method();
                              }
                              @ForRemoval @FlaggedApi("test.pkg.reverted_flag") public class RevertedRemovedClass {
                                ctor public RevertedRemovedClass();
                                method public void method();
                              }
                            }
                        """,
                    )

            args +=
                ARG_REMOVED_API_FILES to
                    signature(
                        "removed.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void alreadyRemovedMethod();
                              }
                            }
                        """,
                    )

            val outputCurrent = outputFile("filtered-current.txt")
            val outputRemoved = outputFile("filtered-removed.txt")
            args += ARG_OUTPUT_FILE to outputCurrent
            args += ARG_OUTPUT_REMOVED_FILE to outputRemoved

            verify {
                outputCurrent.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method @ForRemoval @FlaggedApi("test.pkg.kept_flag") public void keptRemovedMethod();
                            method public void revertedRemovedMethod();
                            field @ForRemoval @FlaggedApi("test.pkg.kept_flag") public static final int KEPT_REMOVED_FIELD = 2; // 0x2
                            field public static final int REVERTED_REMOVED_FIELD = 3; // 0x3
                          }
                          @ForRemoval @FlaggedApi("test.pkg.kept_flag") public class KeptRemovedClass {
                            ctor public KeptRemovedClass();
                            method public void method();
                          }
                          public class RevertedRemovedClass {
                            ctor public RevertedRemovedClass();
                            method public void method();
                          }
                        }
                    """
                )
                outputRemoved.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class FinalizedRemovedClass {
                            ctor public FinalizedRemovedClass();
                            method public void method();
                          }
                          public class Foo {
                            method public void alreadyRemovedMethod();
                            method public void finalizedRemovedMethod();
                            field public static final int FINALIZED_REMOVED_FIELD = 1; // 0x1
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test removed member on class whose flagged change in current API is reverted`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            args +=
                ARG_PREVIOUSLY_RELEASED_API to
                    signature(
                        "prev-current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void existingMethod();
                              }
                            }
                        """,
                    )

            args +=
                ARG_PREVIOUSLY_RELEASED_REMOVED_API to
                    signature(
                        "prev-removed.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void existingRemovedMethod();
                              }
                            }
                        """,
                    )

            args +=
                ARG_API_FILES to
                    signature(
                        "current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @Deprecated @FlaggedApi("test.pkg.disabled_flag") public class Foo {
                                ctor @Deprecated public Foo();
                                method @Deprecated public void existingMethod();
                                method public void newFlaggedMethod();
                              }
                            }
                        """,
                    )

            args +=
                ARG_REMOVED_API_FILES to
                    signature(
                        "removed.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @Deprecated @FlaggedApi("test.pkg.disabled_flag") public class Foo {
                                method @Deprecated @FlaggedApi("test.pkg.disabled_flag") public void existingRemovedMethod();
                                method public void unflaggedRemovedMethod();
                              }
                            }
                        """,
                    )

            val outputCurrent = outputFile("filtered-current.txt")
            val outputRemoved = outputFile("filtered-removed.txt")
            args += ARG_OUTPUT_FILE to outputCurrent
            args += ARG_OUTPUT_REMOVED_FILE to outputRemoved

            verify {
                outputCurrent.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void existingMethod();
                          }
                        }
                    """
                )
                outputRemoved.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void existingRemovedMethod();
                            method public void unflaggedRemovedMethod();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test empty removed signature file preserves empty output when no items are removed`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags/>
                            </config>
                        """,
                    )

            args +=
                ARG_API_FILES to
                    signature(
                        "current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method @ForRemoval @FlaggedApi("test.pkg.reverted_flag") public void revertedRemovedMethod();
                              }
                            }
                        """,
                    )

            args +=
                ARG_REMOVED_API_FILES to
                    signature(
                        "removed.txt",
                        "",
                    )

            val outputCurrent = outputFile("filtered-current.txt")
            val outputRemoved = outputFile("filtered-removed.txt")
            args += ARG_OUTPUT_FILE to outputCurrent
            args += ARG_OUTPUT_REMOVED_FILE to outputRemoved

            verify {
                outputCurrent.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void revertedRemovedMethod();
                          }
                        }
                    """
                )
                outputRemoved.assertSignatureContents("")
            }
        }
    }

    @Test
    fun `Test delta signature file filtering with removed API files`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <api-flags>
                                <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                <api-flag package="test.pkg" name="reverted_flag" mutability="immutable" status="disabled"/>
                              </api-flags>
                            </config>
                        """,
                    )

            args +=
                ARG_API_FILES to
                    signature(
                        "public.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method @ForRemoval @FlaggedApi("test.pkg.finalized_flag") public void finalizedPublicRemovedMethod();
                                method public void publicMethod();
                              }
                            }
                        """,
                    )
            args +=
                ARG_API_FILES to
                    signature(
                        "system.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method @ForRemoval @FlaggedApi("test.pkg.finalized_flag") public void finalizedSystemRemovedMethod();
                                method @ForRemoval @FlaggedApi("test.pkg.reverted_flag") public void revertedSystemRemovedMethod();
                              }
                            }
                        """,
                    )

            args +=
                ARG_REMOVED_API_FILES to
                    signature(
                        "public-removed.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void alreadyRemovedPublicMethod();
                              }
                            }
                        """,
                    )
            args +=
                ARG_REMOVED_API_FILES to
                    signature(
                        "system-removed.txt",
                        "",
                    )

            val outputSystem = outputFile("filtered-system.txt")
            val outputSystemRemoved = outputFile("filtered-system-removed.txt")
            args += ARG_OUTPUT_FILE to outputSystem
            args += ARG_OUTPUT_REMOVED_FILE to outputSystemRemoved

            verify {
                outputSystem.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void revertedSystemRemovedMethod();
                          }
                        }
                    """
                )
                outputSystemRemoved.assertSignatureContents(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void finalizedSystemRemovedMethod();
                          }
                        }
                    """
                )
            }
        }
    }

    @Test
    fun `Test mismatched number of api-files and removed-api-files`() {
        commandTest {
            args += "filter-signature"

            args +=
                ARG_API_FILES to
                    signature(
                        "public.txt",
                        """
                            // Signature format: 2.0
                        """,
                    )
            args +=
                ARG_API_FILES to
                    signature(
                        "system.txt",
                        """
                            // Signature format: 2.0
                        """,
                    )

            args +=
                ARG_REMOVED_API_FILES to
                    signature(
                        "public-removed.txt",
                        """
                            // Signature format: 2.0
                        """,
                    )

            val output = outputFile("filtered.txt")
            args += ARG_OUTPUT_FILE to output

            expectedStderr =
                "Aborting: --removed-api-files must specify the same number of files (1) as --api-files (2)"
        }
    }
}
