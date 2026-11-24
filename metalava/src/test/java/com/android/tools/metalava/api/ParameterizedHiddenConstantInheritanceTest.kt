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

package com.android.tools.metalava.api

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.KnownApiSurface.Companion.TEST_HIDE_ANNOTATION
import com.android.tools.metalava.KnownApiSurface.Companion.TEST_MODULE_API_ANNOTATION
import com.android.tools.metalava.KnownApiSurface.Companion.TEST_SYSTEM_API_ANNOTATION
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.test.Test
import org.junit.Rule
import org.junit.runners.Parameterized

class ParameterizedHiddenConstantInheritanceTest : DriverTest() {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    internal data class TestParams
    @EntryPoint
    constructor(
        val apiSurface: KnownApiSurface,
        val sources: List<TestFile>,
        val expectedConsistentApiSignature: String,
        val expectedConsistentStubFiles: List<TestFile>,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return apiSurface.surface
        }
    }

    companion object {
        /** Sources common to a number of [TestParams]. */
        private val commonSources =
            listOf(
                java(
                    """
                        package test.pkg;

                        @$TEST_HIDE_ANNOTATION
                        public interface HiddenInterface {
                            String HIDDEN_INTERFACE_CONSTANT = "HiddenInterface";
                            String OVERLAPPING_CONSTANT = "HiddenInterface";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_HIDE_ANNOTATION
                        public class HiddenClass implements HiddenInterface {
                            private HiddenClass() {}

                            public static final String HIDDEN_CLASS_CONSTANT = "HiddenClass";
                            public static final String OVERLAPPING_CONSTANT = "HiddenClass";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public interface PublicInterface extends HiddenInterface {
                            String PUBLIC_INTERFACE_CONSTANT = "PublicInterface";
                            String OVERLAPPING_CONSTANT = "PublicInterface";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class PublicClass extends HiddenClass implements PublicInterface {
                            private PublicClass() {}

                            public static final String PUBLIC_CLASS_CONSTANT = "PublicClass";
                            public static final String OVERLAPPING_CONSTANT = "PublicClass";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_HIDE_ANNOTATION
                        public interface PublicSystemBridgeInterface extends SystemInterface {
                            String PUBLIC_SYSTEM_BRIDGE_INTERFACE_CONSTANT = "PublicSystemBridgeInterface";
                            String OVERLAPPING_PUBLIC_SYSTEM_BRIDGE_CONSTANT = "PublicSystemBridgeInterface";
                            String OVERLAPPING_CONSTANT = "PublicSystemBridgeInterface";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_HIDE_ANNOTATION
                        public class PublicSystemBridgeClass extends PublicClass implements PublicSystemBridgeInterface, SystemInterface {
                            private PublicSystemBridgeClass() {}

                            public static final String PUBLIC_SYSTEM_BRIDGE_CLASS_CONSTANT = "PublicSystemBridgeClass";
                            public static final String OVERLAPPING_PUBLIC_SYSTEM_BRIDGE_CONSTANT = "PublicSystemBridgeClass";
                            public static final String OVERLAPPING_CONSTANT = "PublicSystemBridgeClass";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_SYSTEM_API_ANNOTATION
                        public interface SystemInterface extends PublicInterface {
                            String SYSTEM_INTERFACE_CONSTANT = "SystemInterface";
                            String OVERLAPPING_CONSTANT = "SystemInterface";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_SYSTEM_API_ANNOTATION
                        public class SystemClass extends PublicSystemBridgeClass implements SystemInterface {
                            private SystemClass() {}

                            public static final String SYSTEM_CLASS_CONSTANT = "SystemClass";
                            public static final String OVERLAPPING_CONSTANT = "SystemClass";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_HIDE_ANNOTATION
                        public class SystemModuleBridgeClass extends PublicClass implements SystemInterface {
                            private SystemModuleBridgeClass() {}

                            public static final String SYSTEM_MODULE_BRIDGE_CLASS_CONSTANT = "SystemModuleBridgeClass";
                            public static final String OVERLAPPING_CONSTANT = "SystemModuleBridgeClass";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_MODULE_API_ANNOTATION
                        public interface ModuleInterface extends SystemInterface {
                            String MODULE_INTERFACE_CONSTANT = "ModuleInterface";
                            String OVERLAPPING_CONSTANT = "ModuleInterface";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @$TEST_MODULE_API_ANNOTATION
                        public class ModuleClass extends SystemClass implements ModuleInterface {
                            private ModuleClass() {}

                            public static final String MODULE_CLASS_CONSTANT = "ModuleClass";
                            public static final String OVERLAPPING_CONSTANT = "ModuleClass";
                        }
                    """
                ),
            )

        /** The expected PublicInterface stub for common interface behavior. */
        private val commonExpectedPublicInterfaceStub =
            java(
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface PublicInterface {
                    public static final java.lang.String OVERLAPPING_CONSTANT = "PublicInterface";
                    public static final java.lang.String PUBLIC_INTERFACE_CONSTANT = "PublicInterface";
                    }
                """
            )

        /** The expected SystemInterface stub for common interface behavior. */
        private val commonExpectedSystemInterfaceStub =
            java(
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface SystemInterface extends test.pkg.PublicInterface {
                    public static final java.lang.String OVERLAPPING_CONSTANT = "SystemInterface";
                    public static final java.lang.String SYSTEM_INTERFACE_CONSTANT = "SystemInterface";
                    }
                """
            )

        /** The expected ModuleInterface stub for common interface behavior. */
        private val commonExpectedModuleInterfaceStub =
            java(
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface ModuleInterface extends test.pkg.SystemInterface {
                    public static final java.lang.String MODULE_INTERFACE_CONSTANT = "ModuleInterface";
                    public static final java.lang.String OVERLAPPING_CONSTANT = "ModuleInterface";
                    }
                """
            )

        /** The expected PublicClass stub for consistent behavior. */
        private val consistentExpectedPublicClassStub =
            java(
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicClass implements test.pkg.PublicInterface {
                    PublicClass() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String HIDDEN_CLASS_CONSTANT = "HiddenClass";
                    public static final java.lang.String HIDDEN_INTERFACE_CONSTANT = "HiddenInterface";
                    public static final java.lang.String OVERLAPPING_CONSTANT = "PublicClass";
                    public static final java.lang.String PUBLIC_CLASS_CONSTANT = "PublicClass";
                    }
                """
            )

        /** The expected SystemClass stub for consistent behavior. */
        private val consistentExpectedSystemClassStub =
            java(
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SystemClass extends test.pkg.PublicClass implements test.pkg.SystemInterface {
                    SystemClass() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String OVERLAPPING_CONSTANT = "SystemClass";
                    public static final java.lang.String OVERLAPPING_PUBLIC_SYSTEM_BRIDGE_CONSTANT = "PublicSystemBridgeClass";
                    public static final java.lang.String PUBLIC_SYSTEM_BRIDGE_CLASS_CONSTANT = "PublicSystemBridgeClass";
                    public static final java.lang.String PUBLIC_SYSTEM_BRIDGE_INTERFACE_CONSTANT = "PublicSystemBridgeInterface";
                    public static final java.lang.String SYSTEM_CLASS_CONSTANT = "SystemClass";
                    }
                """
            )

        /** The expected ModuleClass stub for consistent behavior. */
        private val consistentExpectedModuleClassStub =
            java(
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ModuleClass extends test.pkg.SystemClass implements test.pkg.ModuleInterface, test.pkg.SystemInterface {
                    ModuleClass() { throw new RuntimeException("Stub!"); }
                    public static final java.lang.String MODULE_CLASS_CONSTANT = "ModuleClass";
                    public static final java.lang.String OVERLAPPING_CONSTANT = "ModuleClass";
                    }
                """
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        internal fun params() =
            listOf(
                TestParams(
                    apiSurface = KnownApiSurface.TEST_PUBLIC_API_SURFACE,
                    sources = commonSources,

                    // Legacy expectations.

                    // Consistent expectations.
                    expectedConsistentApiSignature =
                        """
                            package test.pkg {
                              public class PublicClass implements test.pkg.PublicInterface {
                                field public static final String HIDDEN_CLASS_CONSTANT = "HiddenClass";
                                field public static final String HIDDEN_INTERFACE_CONSTANT = "HiddenInterface";
                                field public static final String OVERLAPPING_CONSTANT = "PublicClass";
                                field public static final String PUBLIC_CLASS_CONSTANT = "PublicClass";
                              }
                              public interface PublicInterface {
                                field public static final String OVERLAPPING_CONSTANT = "PublicInterface";
                                field public static final String PUBLIC_INTERFACE_CONSTANT = "PublicInterface";
                              }
                            }
                        """,
                    expectedConsistentStubFiles =
                        listOf(
                            consistentExpectedPublicClassStub,
                            commonExpectedPublicInterfaceStub,
                        ),
                ),
                TestParams(
                    apiSurface = KnownApiSurface.TEST_SYSTEM_API_SURFACE,
                    sources = commonSources,

                    // Legacy expectations.

                    // Consistent expectations.
                    expectedConsistentApiSignature =
                        """
                            package test.pkg {
                              public class SystemClass extends test.pkg.PublicClass implements test.pkg.SystemInterface {
                                field public static final String OVERLAPPING_CONSTANT = "SystemClass";
                                field public static final String OVERLAPPING_PUBLIC_SYSTEM_BRIDGE_CONSTANT = "PublicSystemBridgeClass";
                                field public static final String PUBLIC_SYSTEM_BRIDGE_CLASS_CONSTANT = "PublicSystemBridgeClass";
                                field public static final String PUBLIC_SYSTEM_BRIDGE_INTERFACE_CONSTANT = "PublicSystemBridgeInterface";
                                field public static final String SYSTEM_CLASS_CONSTANT = "SystemClass";
                              }
                              public interface SystemInterface extends test.pkg.PublicInterface {
                                field public static final String OVERLAPPING_CONSTANT = "SystemInterface";
                                field public static final String SYSTEM_INTERFACE_CONSTANT = "SystemInterface";
                              }
                            }
                        """,
                    expectedConsistentStubFiles =
                        listOf(
                            consistentExpectedPublicClassStub,
                            commonExpectedPublicInterfaceStub,
                            consistentExpectedSystemClassStub,
                            commonExpectedSystemInterfaceStub,
                        ),
                ),
                TestParams(
                    apiSurface = KnownApiSurface.TEST_MODULE_API_SURFACE,
                    sources = commonSources,

                    // Legacy expectations.

                    // Consistent expectations.
                    expectedConsistentApiSignature =
                        """
                            package test.pkg {
                              public class ModuleClass extends test.pkg.SystemClass implements test.pkg.ModuleInterface test.pkg.SystemInterface {
                                field public static final String MODULE_CLASS_CONSTANT = "ModuleClass";
                                field public static final String OVERLAPPING_CONSTANT = "ModuleClass";
                              }
                              public interface ModuleInterface extends test.pkg.SystemInterface {
                                field public static final String MODULE_INTERFACE_CONSTANT = "ModuleInterface";
                                field public static final String OVERLAPPING_CONSTANT = "ModuleInterface";
                              }
                            }
                        """,
                    expectedConsistentStubFiles =
                        listOf(
                            consistentExpectedPublicClassStub,
                            commonExpectedPublicInterfaceStub,
                            consistentExpectedSystemClassStub,
                            commonExpectedSystemInterfaceStub,
                            consistentExpectedModuleClassStub,
                            commonExpectedModuleInterfaceStub,
                        ),
                ),
            )
    }

    private fun checkFieldInheritance(
        apiSurface: KnownApiSurface,
        expectedApiSignature: String,
        expectedStubFiles: List<TestFile>,
    ) {
        check(
            apiSurface = apiSurface,
            format = FileFormat.V6,
            extraArguments =
                hiddenIssues(
                    Issues.HIDDEN_SUPERCLASS,
                ),
            skipEmitPackages = listOf("test.annotation"),
            sourceFiles = params.sources.toTypedArray(),
            expectedApiSignature = expectedApiSignature,
            expectedStubFiles = expectedStubFiles.toTypedArray(),
        )
    }

    @Test
    fun `field inheritance - consistent`() {
        checkFieldInheritance(
            apiSurface = params.apiSurface,
            expectedApiSignature = params.expectedConsistentApiSignature,
            expectedStubFiles = params.expectedConsistentStubFiles,
        )
    }
}
