/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.metalava.testing.java
import org.junit.Test

class ApiAnalyzerTest : DriverTest() {
    @Test
    fun `Hidden abstract method with show @SystemApi`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues =
                """
                src/test/pkg/SystemApiClass.java:7: error: badAbstractHiddenMethod cannot be hidden and abstract when SystemApiClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:5: error: badAbstractHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:6: error: badPackagePrivateMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public abstract class PublicClass {
                        /** @hide */
                        public abstract boolean badAbstractHiddenMethod() { return true; }
                        abstract void badPackagePrivateMethod() { }
                        /**
                         * This method does not fail because it is visible due to showAnnotations,
                         * instead it will fail when running analysis on public API. See test below.
                         * @hide
                         */
                        @SystemApi
                        public abstract boolean goodAbstractSystemHiddenMethod() { return true; }
                    }
                """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public abstract class PublicClassWithHiddenConstructor {
                        private PublicClassWithHiddenConstructor() { }
                        /** @hide */
                        public abstract boolean goodAbstractHiddenMethod() { return true; }
                    }
                """
                    ),
                    java(
                        """
                   package test.pkg;
                   import android.annotation.SystemApi;
                   /** @hide */
                   @SystemApi
                   public abstract class SystemApiClass {
                        /** @hide */
                        public abstract boolean badAbstractHiddenMethod() { return true; }
                        /**
                         * This method is OK, because it matches visibility of the class
                         * @hide
                         */
                        @SystemApi
                        public abstract boolean goodAbstractSystemHiddenMethod() { return true; }
                        public abstract boolean goodAbstractPublicMethod() { return true; }
                   }
               """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    /** This class is OK because it is all hidden @hide */
                    public abstract class HiddenClass {
                        public abstract boolean goodAbstractHiddenMethod() { return true; }
                    }
                """
                    ),
                    systemApiSource
                )
        )
    }

    @Test
    fun `Hidden abstract method for public API`() {
        check(
            expectedIssues =
                """
                src/test/pkg/PublicClass.java:5: error: badAbstractHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:6: error: badPackagePrivateMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:9: error: badAbstractSystemHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public abstract class PublicClass {
                        /** @hide */
                        public abstract boolean badAbstractHiddenMethod() { return true; }
                        abstract void badPackagePrivateMethod() { }
                        /** @hide */
                        @SystemApi
                        public abstract boolean badAbstractSystemHiddenMethod() { return true; }
                    }
                """
                    ),
                    systemApiSource
                )
        )
    }

    @Test
    fun `Deprecation mismatch check look at inherited docs for overriding methods`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:20: error: Method test.pkg.MyClass.inheritedNoCommentInParent(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/MyClass.java:23: error: Method test.pkg.MyClass.notInheritedNoComment(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/MyInterface.java:17: error: Method test.pkg.MyInterface.inheritedNoCommentInParent(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface MyInterface {
                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                void inheritedNoComment();

                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                void inheritedWithComment();

                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                void inheritedWithInheritDoc();

                                @Deprecated
                                void inheritedNoCommentInParent();
                            }
                            """,
                    ),
                    java(
                        """
                            package test.pkg;

                            public class MyClass implements MyInterface {
                                @Deprecated
                                @Override
                                public void inheritedNoComment() {}

                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                @Override
                                public void inheritedWithComment() {}

                                /** {@inheritDoc} */
                                @Deprecated
                                @Override
                                public void inheritedWithInheritDoc() {}

                                @Deprecated
                                @Override
                                public void inheritedNoCommentInParent() {}

                                @Deprecated
                                public void notInheritedNoComment() {}
                            }
                        """
                    )
                )
        )
    }
}
