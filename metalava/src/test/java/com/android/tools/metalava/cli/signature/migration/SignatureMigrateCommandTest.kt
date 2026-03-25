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

package com.android.tools.metalava.cli.signature.migration

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.signature.signatureFormatOptionsHelp
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.stripBlankLines
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.signature
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

private val signatureMigrateHelp =
    """
Usage: metalava signature-migrate [options] <files>...

  Migrates signature files to a new format.

  The purpose of this is, by working in conjunction with the --use-same-format-as option, to simplify the process for
  updating signature files from one version to the next. It assumes a number of things:

  1. That API signature files are checked into some version control system and need to be updated to reflect changes to
  the API. If they are not then this is not needed.

  2. The build uses the --use-same-format-as to pass the checked in API signature file so that its format will be used
  as the output for the file that the build generates to replace it.

  If those assumptions are met then updating the format version of the API file (and its corresponding removed API file
  if needed) simply involves running this command on the API files that need migrating, specifying the target format.
  That will apply a number of migration steps, creating a separate commit for each step. The migration steps will be
  optimized to make the minimum number of changes to each file.

Options:
  --initial-title <text>                     Title for the initial commit (required)
  --title-prefix <prefix>                    Short prefix for commit titles.

                                             If supplied then it will be prepended on every commit title.
  --commit-prolog <text>                     Text to include at the beginning of each commit message.
  --commit-epilog <text>                     Text to include at the end of each commit message.
  --regenerate-command <bash-shell-command>  A bash shell command that will be run during migration to regenerate the
                                             signature files being migrated from sources.

                                             This will be called for every migration step where the new property setting
                                             may result in additional information being included from the sources.
  -h, -?, --help                             Show this message and exit

${signatureFormatOptionsHelp(FileFormat.V6)}

Arguments:
  <files>                                    Signature file groups to migrate.

                                             A file group is a list of files separated by a `:` that are migrated
                                             together so that their formats are always in sync.
    """
        .trimIndent()

class SignatureMigrateCommandTest :
    BaseCommandTest<SignatureMigrateCommand>({
        SignatureMigrateCommand(
            committerFactory = { THROWING_COMMITTER },
        )
    }) {

    companion object {
        /** Default [ChangeCommitter] used by tests that throw exceptions when called. */
        private val THROWING_COMMITTER =
            object : ChangeCommitter {
                override fun commit(description: ChangeDescription, files: List<File>) {
                    error("committing of '${description.title}' for $files is not supported")
                }
            }
    }

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-migrate", "--help")

            expectedStdout = signatureMigrateHelp
        }
    }

    @Test
    fun `Migrate an empty file`() {
        commandTest {
            args += "signature-migrate"
            args += "--initial-title" to "Migrate"

            args += signature("api.txt", "")

            expectedStdout = "No files need migrating"
        }
    }

    @Test
    fun `Ensure signature files to migrate are all the same format`() {
        commandTest {
            args += "signature-migrate"
            args += "--initial-title" to "Migrate"

            args +=
                signature(
                    "api1.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Test {
                            }
                        }
                    """
                )

            args +=
                signature(
                    "api2.txt",
                    """
                        // Signature format: 5.0
                        package test.pkg {
                            public class Test {
                            }
                        }
                    """
                )

            expectedStderr =
                """
                    Aborting: This can only be used to migrate files of the same format but
                    there are 2 different formats.

                      The following files use format: 2.0
                        TESTROOT/api1.txt

                      The following files use format: 5.0
                        TESTROOT/api2.txt
                """
                    .trimIndent()
        }
    }

    /** Check migrating [signatureFiles] with [additionalArgs] results in [expectedCommits]. */
    private fun checkFilesMigration(
        signatureFiles: List<TestFile>,
        additionalArgs: List<String> = emptyList(),
        expectedCommits: String,
    ) {
        checkFileGroupMigration(
            signatureFileGroups = signatureFiles.map { listOf(it) },
            additionalArgs = additionalArgs,
            expectedCommits = expectedCommits,
        )
    }

    /** Check migrating [signatureFileGroups] with [additionalArgs] results in [expectedCommits]. */
    private fun checkFileGroupMigration(
        signatureFileGroups: List<List<TestFile>>,
        additionalArgs: List<String> = emptyList(),
        expectedCommits: String,
    ) {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { printWriter ->
            commandTest {
                command =
                    SignatureMigrateCommand(
                        committerFactory = {
                            TestChangeCommitter(this@SignatureMigrateCommandTest, printWriter)
                        },
                        outFactory = { printWriter },
                        regeneratorFactory = { TestSignatureFileRegenerator(printWriter) },
                    )

                args += "signature-migrate"
                args += "--initial-title" to "Migrate"
                args += "--format" to "6.0:style=java"
                args += "--format-defaults" to "overloaded-method-order=source"
                args += additionalArgs

                signatureFileGroups.mapTo(args) { group ->
                    group.joinToString(":") { testFile -> testFile.toFile().path }
                }

                verify {
                    assertEquals(expectedCommits.trimIndent(), stringWriter.toString().trimEnd())
                }
            }
        }
    }

    @Test
    fun `Migrate some files that only need to change the version`() {
        checkFilesMigration(
            signatureFiles =
                listOf(
                    signature(
                        "api1.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Test {
                                }
                            }
                        """
                    ),
                    signature(
                        "api2.txt",
                        """
                            // Signature format: 2.0
                            package other.pkg {
                                public class Other {
                                }
                            }
                        """
                    ),
                ),
            additionalArgs =
                listOf(
                    "--title-prefix",
                    "test: ",
                    "--commit-prolog",
                    "Prolog",
                    "--commit-epilog",
                    "Epilog"
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        test: Migrate

                        Prolog

                        This change migrates these files to format `6.0:style=java`.

                        Epilog
                        ------------------------------------------------------------------------

                        TESTROOT/api1.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public class Test {
                            }
                          }

                        TESTROOT/api2.txt:
                          // Signature format: 6.0
                          // - style=java
                          package other.pkg {
                            public class Other {
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate some files that need to be kept in sync`() {
        // The following signature files should be kept in sync, i.e. have the same format at every
        // step of the migration process. e.g. This would be needed for `current.txt` and
        // `removed.txt` as they are both created in a metalava invocation that sets
        // `--use-same-format-as current.txt`. Failing to keep them in sync would cause problems if
        // they had to be regenerated from sources.
        val signatureFiles =
            listOf(
                signature(
                    "api1.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public enum Enum {
                                enum_constant public static final test.pkg.Enum VALUE;
                                method public abstract int value();
                            }
                            public final class Test {
                                method public final void method();
                            }
                        }
                    """
                ),
                signature(
                    "api2.txt",
                    """
                        // Signature format: 2.0
                        package other.pkg {
                            public @interface Anno {
                                method public abstract String[] value();
                            }
                            public class Other {
                                method public void method(String);
                                method public void method(int);
                            }
                        }
                    """
                ),
            )

        // TODO(b/489071739): api1.txt and api2.txt should have the format kept in sync by starting
        //  with the same format and be updated in the same steps.
        checkFileGroupMigration(
            signatureFileGroups = listOf(signatureFiles),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 4 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api1.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - normalize-abstract-modifier=no
                          // - normalize-final-modifier=no
                          // - overloaded-method-order=source
                          package test.pkg {
                            public enum Enum {
                              method public abstract int value();
                              enum_constant public static final test.pkg.Enum VALUE;
                            }
                            public final class Test {
                              method public final void method();
                            }
                          }

                        TESTROOT/api2.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - normalize-abstract-modifier=no
                          // - normalize-final-modifier=no
                          // - overloaded-method-order=source
                          package other.pkg {
                            public @interface Anno {
                              method public abstract String[] value();
                            }
                            public class Other {
                              method public void method(String);
                              method public void method(int);
                            }
                          }

                    Step 2: Change 'normalize-abstract-modifier' from 'no' to 'yes'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Normalize abstract modifiers in annotations and enums

                        Previously, `abstract` modifiers were not removed from annotation and
                        enum methods even though they were unnecessary.

                        This change cleans them up by setting `normalize-abstract-modifier=yes`.
                        ------------------------------------------------------------------------

                        TESTROOT/api1.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - normalize-final-modifier=no
                          // - overloaded-method-order=source
                          package test.pkg {
                            public enum Enum {
                              method public int value();
                              enum_constant public static final test.pkg.Enum VALUE;
                            }
                            public final class Test {
                              method public final void method();
                            }
                          }

                        TESTROOT/api2.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - normalize-final-modifier=no
                          // - overloaded-method-order=source
                          package other.pkg {
                            public @interface Anno {
                              method public String[] value();
                            }
                            public class Other {
                              method public void method(String);
                              method public void method(int);
                            }
                          }

                    Step 3: Change 'normalize-final-modifier' from 'no' to 'yes'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Normalize final modifiers in final classes

                        Previously, `final` modifiers were not removed from methods in `final`
                        classes.

                        This change cleans them up by setting `normalize-final-modifier=yes`.
                        ------------------------------------------------------------------------

                        TESTROOT/api1.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - overloaded-method-order=source
                          package test.pkg {
                            public enum Enum {
                              method public int value();
                              enum_constant public static final test.pkg.Enum VALUE;
                            }
                            public final class Test {
                              method public void method();
                            }
                          }

                        TESTROOT/api2.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - overloaded-method-order=source
                          package other.pkg {
                            public @interface Anno {
                              method public String[] value();
                            }
                            public class Other {
                              method public void method(String);
                              method public void method(int);
                            }
                          }

                    Step 4: Change 'overloaded-method-order' from 'source' to 'signature'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Sort overloaded methods by signature

                        Previously, overloaded methods were sorted by their order in the source
                        file. That meant that refactoring the sources could cause changes to
                        signature files even though there were no actual API changes.

                        This change fixes that by setting `overloaded-method-order=signature`
                        which will sort overloaded methods by their signature.
                        ------------------------------------------------------------------------

                        TESTROOT/api1.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public enum Enum {
                              method public int value();
                              enum_constant public static final test.pkg.Enum VALUE;
                            }
                            public final class Test {
                              method public void method();
                            }
                          }

                        TESTROOT/api2.txt:
                          // Signature format: 6.0
                          // - style=java
                          package other.pkg {
                            public @interface Anno {
                              method public String[] value();
                            }
                            public class Other {
                              method public void method(int);
                              method public void method(String);
                            }
                          }
                """,
        )
    }

    /** Check migrating [signatureFile] results in [expectedCommits]. */
    private fun checkFileMigration(
        signatureFile: TestFile,
        expectedCommits: String,
    ) {
        checkFilesMigration(
            listOf(signatureFile),
            expectedCommits = expectedCommits,
        )
    }

    @Test
    fun `Migrate file that is affected by changing flagged-api-inheritance from none to nested-classes`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            @FlaggedApi("flag.name") public class Test {
                            }

                            public class Test.Nested {
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - flagged-api-inheritance=none
                          package test.pkg {
                            @FlaggedApi("flag.name") public class Test {
                            }
                            public class Test.Nested {
                            }
                          }

                    Step 2: Change 'flagged-api-inheritance' from 'none' to 'nested-classes'
                      Reformatting files
                      Regenerate signature files from sources
                      Committing changes
                        ------------------------------------------------------------------------
                        Track inherited @FlaggedApi on nested classes

                        An `@FlaggedApi` annotation on a class affects all of its members and
                        nested classes that do not have their own `@FlaggedApi` annotation.
                        Previously, inherited `@FlaggedApi` annotations were not tracked on
                        nested classes which complicated the reviewing of signature file changes
                        as it was difficult to determine which `@FlaggedApi` if any applied to
                        the change.

                        This change sets `flagged-api-inheritance=nested-classes` to fix that.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            @FlaggedApi("flag.name") public class Test {
                            }
                            @FlaggedApi("flag.name") public class Test.Nested {
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate file that is affected by changing normalize-abstract-modifier from no to yes`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public @interface Anno {
                                method public abstract String[] value();
                            }
                            public enum Enum {
                                enum_constant public static final test.pkg.Enum VALUE;
                                method public abstract int value();
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - normalize-abstract-modifier=no
                          package test.pkg {
                            public @interface Anno {
                              method public abstract String[] value();
                            }
                            public enum Enum {
                              method public abstract int value();
                              enum_constant public static final test.pkg.Enum VALUE;
                            }
                          }

                    Step 2: Change 'normalize-abstract-modifier' from 'no' to 'yes'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Normalize abstract modifiers in annotations and enums

                        Previously, `abstract` modifiers were not removed from annotation and
                        enum methods even though they were unnecessary.

                        This change cleans them up by setting `normalize-abstract-modifier=yes`.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public @interface Anno {
                              method public String[] value();
                            }
                            public enum Enum {
                              method public int value();
                              enum_constant public static final test.pkg.Enum VALUE;
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate file that is affected by changing normalize-final-modifier from no to yes`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public final class Test {
                                method public final void method();
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - normalize-final-modifier=no
                          package test.pkg {
                            public final class Test {
                              method public final void method();
                            }
                          }

                    Step 2: Change 'normalize-final-modifier' from 'no' to 'yes'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Normalize final modifiers in final classes

                        Previously, `final` modifiers were not removed from methods in `final`
                        classes.

                        This change cleans them up by setting `normalize-final-modifier=yes`.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public final class Test {
                              method public void method();
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate file that is affected by changing overloaded-method-order from source to signature`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Test {
                                method public void method(String);
                                method public void method(int);
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - overloaded-method-order=source
                          package test.pkg {
                            public class Test {
                              method public void method(String);
                              method public void method(int);
                            }
                          }

                    Step 2: Change 'overloaded-method-order' from 'source' to 'signature'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Sort overloaded methods by signature

                        Previously, overloaded methods were sorted by their order in the source
                        file. That meant that refactoring the sources could cause changes to
                        signature files even though there were no actual API changes.

                        This change fixes that by setting `overloaded-method-order=signature`
                        which will sort overloaded methods by their signature.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public class Test {
                              method public void method(int);
                              method public void method(String);
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate file that is affected by changing sorts-whole-extends-list from no to yes`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public interface Another {
                            }
                            public interface Other {
                            }
                            public interface Test extends test.pkg.Other, test.pkg.Another {
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - sort-whole-extends-list=no
                          package test.pkg {
                            public interface Another {
                            }
                            public interface Other {
                            }
                            public interface Test extends test.pkg.Other test.pkg.Another {
                            }
                          }

                    Step 2: Change 'sort-whole-extends-list' from 'no' to 'yes'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Sort the whole extends list

                        Previously, an interface that had an `extends` list with multiple super
                        interfaces would sort all but the first item in the list. That meant
                        that refactoring the sources could cause changes to signature files even
                        though there were no actual API changes.

                        This change fixes that by setting `sort-whole-extends-list=yes` which
                        will sort the whole list.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public interface Another {
                            }
                            public interface Other {
                            }
                            public interface Test extends test.pkg.Another test.pkg.Other {
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate file that is affected by changing strip-java-lang-prefix from legacy to always`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public interface Test<T extends java.lang.Number> {
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - strip-java-lang-prefix=legacy
                          package test.pkg {
                            public interface Test<T extends java.lang.Number> {
                            }
                          }

                    Step 2: Change 'strip-java-lang-prefix' from 'legacy' to 'always'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Always strip java.lang. prefixes from types

                        Previously, a `java.lang.` prefixes were only stripped from the start of
                        a type. That is legacy behavior from when types were modelled as
                        strings.

                        This change fixes that by setting `strip-java-lang-prefix=always` which
                        will remove the prefix from all types. Note, that does not include
                        annotations, so `java.lang.SafeVarargs` is unaffected.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public interface Test<T extends Number> {
                            }
                          }
                """,
        )
    }

    @Test
    fun `Migrate file that is affected by changing type-argument-spacing from legacy to space`() {
        checkFileMigration(
            signatureFile =
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Test {
                                method void method(java.util.Map<test.pkg.Test,test.pkg.Test>);
                            }
                        }
                    """
                ),
            expectedCommits =
                """
                    Step 1: Initial migration
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Migrate

                        This change is the first in a series of 2 steps to migrate
                        these files to format `6.0:style=java`.

                        This initial change reformats the files to be as close to the target
                        format as possible while not changing the structure of the file. It
                        sets properties in each file that are needed to preserve that
                        structure. The follow-up changes will change the value of one property
                        at a time from the original value to the target value. The intent is to
                        simplify the review process by only making one form of structural
                        change at a time.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          // - type-argument-spacing=legacy
                          package test.pkg {
                            public class Test {
                              method void method(java.util.Map<test.pkg.Test,test.pkg.Test>);
                            }
                          }

                    Step 2: Change 'type-argument-spacing' from 'legacy' to 'space'
                      Reformatting files
                      Committing changes
                        ------------------------------------------------------------------------
                        Always separate type arguments with a space

                        Previously, the separation of type arguments was inconsistent depending
                        on where the type was used.

                        This change fixes that by setting `type-argument-spacing=space` which
                        will separate them with a space separator everywhere.
                        ------------------------------------------------------------------------

                        TESTROOT/api.txt:
                          // Signature format: 6.0
                          // - style=java
                          package test.pkg {
                            public class Test {
                              method void method(java.util.Map<test.pkg.Test, test.pkg.Test>);
                            }
                          }
                """,
        )
    }
}

/**
 * Print details of the commits that are made, including the file contents in each commit to [out].
 */
internal class TestChangeCommitter(
    private val temporaryFolderOwner: TemporaryFolderOwner,
    private val out: PrintWriter,
) : ChangeCommitter {

    override fun commit(description: ChangeDescription, files: List<File>) {
        out.println("    $COMMIT_MESSAGE_SEPARATOR")
        out.println("    ${description.title}\n")
        out.println(description.detail.applyIndentAndTrimLineEnd("    "))
        out.println("    $COMMIT_MESSAGE_SEPARATOR")
        out.println()
        for (file in files) {
            val contents = file.readText().stripBlankLines()
            out.println("    ${temporaryFolderOwner.cleanupString(file.path)}:")
            out.println(contents.applyIndentAndTrimLineEnd("      "))
            out.println()
        }
    }

    /** Apply [indent] to this [String] removing whitespace from the end of the lines. */
    private fun String.applyIndentAndTrimLineEnd(indent: String) =
        lineSequence().joinToString(separator = "\n") { line ->
            when {
                line.isBlank() -> ""
                else -> indent + line.trimEnd()
            }
        }

    companion object {
        /** Separator added before and after the commit message in [commit]. */
        private val COMMIT_MESSAGE_SEPARATOR = "-".repeat(72)
    }
}

/** Prints when the signature files are regenerated to [out]. */
internal class TestSignatureFileRegenerator(
    private val out: PrintWriter,
) : SignatureFileRegenerator {
    override fun regenerateFromSources() {
        out.println("  Regenerate signature files from sources")
    }
}
