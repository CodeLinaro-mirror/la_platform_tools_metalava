/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class SystemServiceCheckTest : DriverTest() {
    @Test
    fun `SystemService OK, loaded from signature file`() {
        check(
            expectedIssues = "", // OK
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @android.annotation.SystemService("Myservice")
                    public class MyTest2 {
                        @RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public int myMethod1() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <permission
                        android:name="foo.bar.PERMISSION1"
                        android:label="@string/foo"
                        android:description="@string/foo"
                        android:protectionLevel="signature"/>
                    <permission
                        android:name="foo.bar.PERMISSION2"
                        android:protectionLevel="signature"/>

                </manifest>
                """
        )
    }

    @Test
    fun `SystemService OK, loaded from source`() {
        check(
            expectedIssues = "", // OK
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @android.annotation.SystemService("Myservice")
                    public class MyTest2 {
                        @android.annotation.RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public void myMethod1() {
                        }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <permission
                        android:name="foo.bar.PERMISSION1"
                        android:label="@string/foo"
                        android:description="@string/foo"
                        android:protectionLevel="signature"/>
                    <permission
                        android:name="foo.bar.PERMISSION2"
                        android:protectionLevel="signature"/>

                </manifest>
                """
        )
    }

    @Test
    fun `Check SystemService -- no permission annotation`() {
        check(
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                "src/test/pkg/MyTest1.java:4: error: Method 'myMethod2' must be protected with a system permission. [RequiresPermission]",
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @android.annotation.SystemService("Myservice")
                    public class MyTest1 {
                        public int myMethod2() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                    KnownSourceFiles.systemApiSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest/>
                """
        )
    }

    @Test
    fun `Check SystemService -- can miss a permission with anyOf`() {
        check(
            expectedIssues = "",
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @android.annotation.SystemService("myservice")
                    public class MyTest2 {
                        @RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public int myMethod1() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <permission
                        android:name="foo.bar.PERMISSION1"
                        android:label="@string/foo"
                        android:description="@string/foo"
                        android:protectionLevel="signature"/>
                </manifest>
                """
        )
    }

    @Test
    fun `Check SystemService such that at least one permission must be defined with anyOf`() {
        check(
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                src/test/pkg/MyTest2.java:6: error: Method 'myMethod1' must be protected with a system permission. [RequiresPermission]
                src/test/pkg/MyTest2.java:6: error: None of the permissions foo.bar.PERMISSION1, foo.bar.PERMISSION2 are defined by manifest TESTROOT/manifest.xml. [RequiresPermission]
                """,
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @android.annotation.SystemService("Myservice")
                    public class MyTest2 {
                        @RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public int myMethod1() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                    KnownSourceFiles.systemApiSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest/>
                """
        )
    }

    @Test
    fun `Check SystemService -- missing one permission with allOf`() {
        check(
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                "src/test/pkg/MyTest2.java:6: error: Permission 'foo.bar.PERMISSION2' is not defined by manifest TESTROOT/manifest.xml. [RequiresPermission]",
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import android.annotation.RequiresPermission;
                        @android.annotation.SystemService("Myservice")
                        public class MyTest2 {
                            @RequiresPermission(allOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                            public int test() { }
                        }
                        """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                    KnownSourceFiles.systemApiSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <permission
                        android:name="foo.bar.PERMISSION1"
                        android:label="@string/foo"
                        android:description="@string/foo"
                        android:protectionLevel="signature"/>
                </manifest>
                """
        )
    }

    @Test
    fun `Check SystemService -- must be system permission, not normal`() {
        check(
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                "src/test/pkg/MyTest2.java:7: error: Method 'test' must be protected with a system " +
                    "permission; it currently allows non-system callers holding [foo.bar.PERMISSION1, " +
                    "foo.bar.PERMISSION2] [RequiresPermission]",
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @SuppressWarnings("WeakerAccess")
                    @android.annotation.SystemService("Myservice")
                    public class MyTest2 {
                        @RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public int test() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                    KnownSourceFiles.systemApiSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <permission
                            android:name="foo.bar.PERMISSION1"
                            android:label="@string/foo"
                            android:description="@string/foo"
                            android:protectionLevel="normal"/>
                        <permission
                            android:name="foo.bar.PERMISSION2"
                            android:protectionLevel="normal"/>

                    </manifest>
                """
        )
    }

    @Test
    fun `Check SystemService -- missing manifest permissions`() {
        check(
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                src/test/pkg/MyTest2.java:8: error: Method 'test' must be protected with a system permission. [RequiresPermission]
                src/test/pkg/MyTest2.java:8: error: Permission 'android.permission.MY_PERMISSION' is not defined by manifest TESTROOT/manifest.xml. [RequiresPermission]
                src/test/pkg/MyTest2.java:8: error: Permission 'android.permission.MY_PERMISSION2' is not defined by manifest TESTROOT/manifest.xml. [RequiresPermission]
                """,
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @android.annotation.SystemService("Myservice")
                    public class MyTest2 {
                        public static final String MY_PERMISSION = "android.permission.MY_PERMISSION";
                        public static final String MY_PERMISSION2 = "android.permission.MY_PERMISSION2";
                        @RequiresPermission(allOf={MY_PERMISSION, MY_PERMISSION2})
                        public int test() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                    KnownSourceFiles.systemApiSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest/>
                """
        )
    }

    @Test
    fun `Invalid manifest`() {
        check(
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                manifest.xml: error: Failed to parse TESTROOT/manifest.xml: The markup in the document preceding the root element must be well-formed. [ParseError]
                src/test/pkg/MyTest2.java:7: error: Method 'test' must be protected with a system permission. [RequiresPermission]
                src/test/pkg/MyTest2.java:7: error: None of the permissions foo.bar.PERMISSION1, foo.bar.PERMISSION2 are defined by manifest TESTROOT/manifest.xml. [RequiresPermission]
                """,
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.RequiresPermission;
                    @SuppressWarnings("WeakerAccess")
                    @android.annotation.SystemService("Myservice")
                    public class MyTest2 {
                        @RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public int test() { }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                    KnownSourceFiles.systemApiSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                </error>
                """
        )
    }

    @Test
    fun `Warning suppressed via annotation`() {
        check(
            expectedIssues = "", // OK (suppressed)
            // TODO(b/412743564): Use includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS
            //   instead of the following.
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_SERVICE_CHECK),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @android.annotation.SystemService("Myservice")
                    public class MyTest1 {
                        @android.annotation.SuppressLint({"RemovedField","RequiresPermission"})
                        @android.annotation.RequiresPermission(anyOf={"foo.bar.PERMISSION1","foo.bar.PERMISSION2"})
                        public void myMethod1() {
                        }
                    }
                    """
                    ),
                    systemServiceSource,
                    requiresPermissionSource,
                ),
            manifest =
                """<?xml version="1.0" encoding="UTF-8"?>
                <manifest/>
                """
        )
    }
}
