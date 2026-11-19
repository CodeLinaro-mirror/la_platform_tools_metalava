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

package com.android.tools.metalava.apilevels

import androidx.tracing.Tracer
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.ThrowingReporter
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.signature
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Factory for creating an [VersionedApi] instance for a specific [ApiVersion].
 *
 * Separates the selection of the [ApiVersion] from the creation of [VersionedApi]. A single factory
 * can be used to create multiple [VersionedApi]s with different [ApiVersion]s.
 */
typealias VersionedApiFactory = (ApiVersion) -> VersionedApi

/** Verifies the consistency of updating APIs loaded from signature files and sources. */
class ApiUpdateConsistencyTest : DriverTest() {

    /** A rule that will run tests within the lifespan of an [EnvironmentManager]. */
    @get:Rule
    val sourceModelProviderRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val sourceModelProvider = codebaseCreatorConfig.creator
                sourceModelProvider.createEnvironmentManager(forTesting = true).use {
                    environmentManager = it
                    base.evaluate()
                }
            }
        }
    }

    /**
     * The [EnvironmentManager] used by [versionedSourceApi].
     *
     * Initialized and closed by [sourceModelProviderRule].
     */
    private lateinit var environmentManager: EnvironmentManager

    /** Return a [VersionedApiFactory] that will create a [VersionedSignatureApi] for [contents]. */
    private fun versionedSignatureApi(contents: String): VersionedApiFactory {
        return { version ->
            val testFile = signature("$version.txt", contents)
            val file = testFile.toFile()
            VersionedSignatureApi(
                DefaultSignatureFileLoader(Codebase.Config.NOOP),
                listOf(file),
                ApiHistoryUpdater.forApiVersion(version)
            )
        }
    }

    /**
     * Return a [VersionedApiFactory] that will create a [VersionedSourceApi] for a [Codebase]
     * created from [sourceFiles].
     */
    private fun versionedSourceApi(vararg sourceFiles: TestFile): VersionedApiFactory {
        return { version ->
            val sourceSet =
                SourceSet.createFromSourcePath(
                    ThrowingReporter.INSTANCE,
                    sourceFiles.map { it.createFile(temporaryFolder.newFolder()) },
                )
            val classPath =
                listOf(
                    getAndroidJar(),
                )

            val environment =
                environmentManager.createEnvironment(
                    reporter = ThrowingReporter.INSTANCE,
                    tracer = Tracer.getStubTracer(),
                    sourceSet = sourceSet,
                    classPath = classPath,
                )
            val parser = environment.createSourceParser(Codebase.Config.NOOP)

            val codebase = parser.parseSources("version $version")

            assertNotNull(codebase, message = "Codebase was not created")

            val codebaseFragment =
                CodebaseFragment.create(
                    codebase,
                    factory = ::EmittableDelegatingVisitor,
                )
            VersionedSourceApi({ codebaseFragment }, version)
        }
    }

    /**
     * Create an [Api] from a list of [VersionedApi]s, print it using [ApiXmlPrinter] and then
     * verify that the result matches [expectedXmlContents].
     */
    private fun checkVersionedApis(
        versionedApis: List<VersionedApi>,
        expectedXmlContents: String,
        message: String,
    ) {
        val api = createApiFromVersionedApis(useInternalNames = true, versionedApis)
        // Clean the API as is done when generating it properly.
        api.clean()
        val writer = StringWriter()
        val printer = ApiXmlPrinter(null, versionedApis)
        PrintWriter(writer).use { printWriter -> printer.print(api, printWriter) }
        assertEquals(
            expectedXmlContents.trimIndent(),
            writer.toString().trimIndent().replace("\t", "    "),
            message = message
        )
    }

    /**
     * Use a list of [VersionedApiFactory]s to produce lists of [VersionedApi]s with different
     * [ApiVersion] and check that they behave correctly when used to update [Api].
     *
     * Each [VersionedApi] should be identical in terms of the effect on the [Api] and so the order
     * should not matter. However, there are some inconsistencies between the [VersionedApi]s. There
     * is some logic in [addApisFromCodebase] to compensate for those inconsistencies, but it
     * depends on the contents of the [ApiClass.superClasses] list and so is affected by order.
     *
     * So, this test will check the effect of the [VersionedApi]s created by the
     * [versionedApiFactories] on [Api] in three different ways:
     * 1. It will create [VersionedApi]s in order with the version equal to 1 more than the index of
     *    the [VersionedApiFactory] in [versionedApiFactories]. They will then be applied in version
     *    order, e.g. from 1 to `N`. The resulting XML will be compared with [expected].
     * 2. The list of [VersionedApi]s created in step #1 will be reversed. So, version `N` will be
     *    applied before version `N-1`. The resulting XML will be compared with
     *    [expectedReversedVersions].
     * 3. The list of [versionedApiFactories] will be reversed and then used as in step #1. The
     *    resulting XML will be compared with [expectedReversedFactories].
     *
     * Irrespective of which way the [Api] is constructed it should produce XML output that matches
     * [expected].
     */
    private fun checkVersionedApiFactories(
        vararg versionedApiFactories: VersionedApiFactory,
        expected: String,
        expectedReversedVersions: String = expected,
        expectedReversedFactories: String = expected,
    ) {
        val versionedApis =
            versionedApiFactories.mapIndexed { index, factory ->
                factory(ApiVersion.fromLevel(index + 1))
            }

        checkVersionedApis(versionedApis, expected, "forward")
        checkVersionedApis(versionedApis.reversed(), expectedReversedVersions, "reversed versions")

        checkVersionedApis(
            versionedApiFactories.reversed().mapIndexed { index, factory ->
                factory(ApiVersion.fromLevel(index + 1))
            },
            expectedReversedFactories,
            "reversed factories"
        )
    }

    @Test
    fun `Test normal class`() {
        checkVersionedApiFactories(
            versionedSignatureApi(
                """
                        // Signature format: 2.0
                        package java.lang {
                          public class ArithmeticException extends RuntimeException {
                            ctor public ArithmeticException();
                            ctor public ArithmeticException(String);
                          }
                        }
                    """,
            ),
            versionedSourceApi(
                java(
                    """
                        package java.lang;
                        public class ArithmeticException extends RuntimeException {
                            public ArithmeticException() {}
                            public ArithmeticException(String message) {super(message);}
                        }
                    """
                ),
            ),
            expected =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/lang/ArithmeticException" since="1">
                            <extends name="java/lang/RuntimeException"/>
                            <method name="&lt;init>()V"/>
                            <method name="&lt;init>(Ljava/lang/String;)V"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Test simple interface super classes`() {
        checkVersionedApiFactories(
            versionedSignatureApi(
                """
                        // Signature format: 2.0
                        package java.lang {
                          public interface AutoCloseable {
                            method public void close() throws Exception;
                          }
                        }
                    """,
            ),
            versionedSourceApi(
                java(
                    """
                        package java.lang;
                        public interface AutoCloseable {
                            void close() throws Exception;
                        }
                    """
                ),
            ),
            expected =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/lang/AutoCloseable" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="close()V"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Test annotations super classes`() {
        checkVersionedApiFactories(
            versionedSignatureApi(
                """
                        // Signature format: 2.0
                        package java.lang {
                          public @interface FunctionalInterface {
                          }
                        }
                    """
            ),
            versionedSourceApi(
                java(
                    """
                        package java.lang;
                        public @interface FunctionalInterface {
                        }
                    """
                )
            ),
            expected =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/lang/FunctionalInterface" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="java/lang/annotation/Annotation"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Test enums`() {
        checkVersionedApiFactories(
            versionedSignatureApi(
                """
                        // Signature format: 2.0
                        package java.lang.annotation {
                          public enum RetentionPolicy {
                            enum_constant public java.lang.annotation.RetentionPolicy CLASS;
                            enum_constant public java.lang.annotation.RetentionPolicy SOURCE;
                            enum_constant public java.lang.annotation.RetentionPolicy RUNTIME;
                          }
                        }
                    """
            ),
            versionedSourceApi(
                java(
                    """
                        package java.lang.annotation;
                        public enum RetentionPolicy {
                            SOURCE,
                            CLASS,
                            RUNTIME
                        }
                    """
                )
            ),
            expected =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/lang/annotation/RetentionPolicy" since="1">
                            <extends name="java/lang/Enum"/>
                            <method name="valueOf(Ljava/lang/String;)Ljava/lang/annotation/RetentionPolicy;"/>
                            <method name="values()[Ljava/lang/annotation/RetentionPolicy;"/>
                            <field name="CLASS"/>
                            <field name="RUNTIME"/>
                            <field name="SOURCE"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Test deprecated enum`() {
        val deprecatedEnumClass =
            java(
                """
                    package test.pkg;
                    /** @deprecated */
                    @Deprecated public enum DeprecatedEnum {
                        CONSTANT
                    }
                """
            )
        val deprecatedEnumSignature =
            """
                // Signature format: 2.0
                package test.pkg {
                  @Deprecated public enum DeprecatedEnum {
                    enum_constant @Deprecated public test.pkg.DeprecatedEnum CONSTANT;
                  }
                }
            """
        checkVersionedApiFactories(
            versionedSignatureApi(deprecatedEnumSignature),
            versionedSourceApi(deprecatedEnumClass),
            // Emulates an extension signature file being applied after the main API.
            versionedSignatureApi(deprecatedEnumSignature),
            expected =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/DeprecatedEnum" since="1" deprecated="1">
                            <extends name="java/lang/Enum"/>
                            <method name="valueOf(Ljava/lang/String;)Ltest/pkg/DeprecatedEnum;"/>
                            <method name="values()[Ltest/pkg/DeprecatedEnum;"/>
                            <field name="CONSTANT"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Test referenced super class in later package`() {
        checkVersionedApiFactories(
            versionedSignatureApi(
                """
                    // Signature format: 2.0
                    package java.io {
                      public class IOError extends Error {
                        ctor public IOError(Throwable);
                      }
                    }
                    package java.lang {
                      public @interface FunctionalInterface {
                      }
                    }
                """
            ),
            versionedSourceApi(
                java(
                    """
                        package java.io;
                        public class IOError extends Error {
                            public IOError(Throwable cause) {super(cause);}
                        }
                    """
                ),
                java(
                    """
                        package java.lang;
                        public @interface FunctionalInterface {
                        }
                    """
                ),
            ),
            expected =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/io/IOError" since="1">
                            <extends name="java/lang/Error"/>
                            <method name="&lt;init>(Ljava/lang/Throwable;)V"/>
                        </class>
                        <class name="java/lang/FunctionalInterface" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="java/lang/annotation/Annotation"/>
                        </class>
                    </api>
                """,
        )
    }
}
