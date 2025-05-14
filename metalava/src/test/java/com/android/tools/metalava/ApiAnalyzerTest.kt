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

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ApiAnalyzerTest : DriverTest() {
    @Test
    fun `Hidden abstract method with show @SystemApi`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues =
                """
                    src/test/pkg/PublicClass.java:5: error: badAbstractHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                    src/test/pkg/PublicClass.java:6: error: badPackagePrivateMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                    src/test/pkg/SystemApiClass.java:7: error: badAbstractHiddenMethod cannot be hidden and abstract when SystemApiClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                """,
            expectedFail = DefaultLintErrorMessage,
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
            expectedFail = DefaultLintErrorMessage,
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
            expectedFail = DefaultLintErrorMessage,
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

    @Test
    fun `Test that usage of a hidden class as type parameter of an outer class is flagged`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:3: warning: Field Foo.fieldReferencesHidden1 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:3: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden1 [ReferencesHidden]
                    src/test/pkg/Foo.java:4: warning: Field Foo.fieldReferencesHidden2 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:4: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden2 [ReferencesHidden]
                    src/test/pkg/Foo.java:5: warning: Field Foo.fieldReferencesHidden3 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:5: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden3 [ReferencesHidden]
                    src/test/pkg/Foo.java:6: warning: Field Foo.fieldReferencesHidden4 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:6: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden4 [ReferencesHidden]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @hide */
                            public class Hidden {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Outer<P1> {
                                public class Inner<P2> {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public Hidden fieldReferencesHidden1;
                                public Outer<Hidden> fieldReferencesHidden2;
                                public Outer<Foo>.Inner<Hidden> fieldReferencesHidden3;
                                public Outer<Hidden>.Inner<Foo> fieldReferencesHidden4;
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test inheriting methods from hidden class preserves deprecated status`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            class Hidden {
                                /** @deprecated */
                                public <T> void foo(@Deprecated T t) {}

                                /** @deprecated */
                                public void bar() {}

                                public void baz(@Deprecated int i) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Concrete extends Hidden<String> {
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Concrete {
                        ctor public Concrete();
                        method @Deprecated public void bar();
                        method public void baz(@Deprecated int);
                        method @Deprecated public <T> void foo(@Deprecated T);
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Concrete {
                            public Concrete() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            public void baz(@Deprecated int i) { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public <T> void foo(@Deprecated T t) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test inheriting methods from hidden generic class preserves deprecated status`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            class Hidden<T> {
                                /** @deprecated */
                                public void foo(@Deprecated T t) {}

                                /** @deprecated */
                                public void bar() {}

                                public void baz(@Deprecated int i) {}
                            }

                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Concrete extends Hidden<String> {
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Concrete {
                        ctor public Concrete();
                        method @Deprecated public void bar();
                        method public void baz(@Deprecated int);
                        method @Deprecated public void foo(@Deprecated String);
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Concrete {
                            public Concrete() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            public void baz(@Deprecated int i) { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void foo(@Deprecated java.lang.String t) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            extraArguments = arrayOf(ARG_HIDE, Issues.INHERIT_CHANGES_SIGNATURE.name),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test deprecated class and parameters are output in kotlin`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            @Deprecated
                            class Foo(
                                @Deprecated var i: Int,
                                @Deprecated var b: Boolean,
                            )
                        """
                    ),
                ),
            format = FileFormat.V4,
            api =
                """
                    package test.pkg {
                      @Deprecated public final class Foo {
                        ctor @Deprecated public Foo(@Deprecated int i, @Deprecated boolean b);
                        method @Deprecated public boolean getB();
                        method @Deprecated public int getI();
                        method @Deprecated public void setB(boolean);
                        method @Deprecated public void setI(int);
                        property @Deprecated public boolean b;
                        property @Deprecated public int i;
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Deprecation when ignoring comments`() {
        check(
            extraArguments = arrayOf(ARG_SKIP_READING_COMMENTS, ARG_ERROR, "ReferencesDeprecated"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            @Deprecated
                            class TestClass(
                                val content: String,
                            )

                            @Deprecated
                            val TestClass.propertyDeprecated: String
                                get() = TestClass.content

                            @get:Deprecated
                            val TestClass.getterDeprecated: String
                                get() = TestClass.content

                            /**
                             * @deprecated
                             */
                            val TestClass.commentDeprecated: String
                                get() = TestClass.content

                        """
                    ),
                ),
            format = FileFormat.V4,
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/TestClass.kt:20: error: Parameter references deprecated type test.pkg.TestClass in test.pkg.TestClassKt.getCommentDeprecated(): this method should also be deprecated [ReferencesDeprecated]
                """,
        )
    }

    @Test
    fun `Test inherited method from hidden class into deprecated class inherits deprecated status`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            class Hidden {
                                public void bar() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            /** @deprecated */
                            public class Concrete extends Hidden<String> {
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Concrete {
                        ctor @Deprecated public Concrete();
                        method @Deprecated public void bar();
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @Deprecated
                            public class Concrete {
                            @Deprecated
                            public Concrete() { throw new RuntimeException("Stub!"); }
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test warnings for usage of hidden interface type`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            /** @suppress */
                            interface HiddenInterface
                            class PublicClass {
                                fun returnsHiddenInterface(): HiddenInterface = TODO()
                            }
                        """
                    )
                ),
            api =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class PublicClass {
                        ctor public PublicClass();
                        method public test.pkg.HiddenInterface returnsHiddenInterface();
                      }
                    }
                """,
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/HiddenInterface.kt:5: warning: Method test.pkg.PublicClass.returnsHiddenInterface() references hidden type test.pkg.HiddenInterface. [HiddenTypeParameter]
                    src/test/pkg/HiddenInterface.kt:5: warning: Return type of unavailable type test.pkg.HiddenInterface in test.pkg.PublicClass.returnsHiddenInterface() [UnavailableSymbol]
                    src/test/pkg/HiddenInterface.kt:5: error: Class test.pkg.HiddenInterface is hidden but was referenced (in return type) from public method test.pkg.PublicClass.returnsHiddenInterface() [ReferencesHidden]
                """,
        )
    }

    @Test
    fun `Test PrivateSuperclass for inner class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Container {
                                private class PrivateInnerClass {}
                                public class PublicInnerClass extends PrivateInnerClass {}
                            }
                        """
                    )
                ),
            api =
                """
                    package test.pkg {
                      public class Container {
                        ctor public Container();
                      }
                      public class Container.PublicInnerClass {
                        ctor public Container.PublicInnerClass();
                      }
                    }
                """,
            expectedIssues =
                "src/test/pkg/Container.java:4: warning: Public class test.pkg.Container.PublicInnerClass extends private class test.pkg.Container.PrivateInnerClass [PrivateSuperclass]"
        )
    }

    @Test
    fun `Test references deprecated errors do not apply to inner class of deprecated class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @Deprecated
                            public class DeprecatedOuterClass {
                                public class EffectivelyDeprecatedInnerClass extends DeprecatedOuterClass {
                                    public void usesDeprecatedOuterClass(DeprecatedOuterClass doc) {}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class NotDeprecatedClass extends DeprecatedOuterClass {
                                public void usesDeprecatedOuterClass(DeprecatedOuterClass doc) {}
                            }
                        """
                    )
                ),
            api =
                """
                    package test.pkg {
                      @Deprecated public class DeprecatedOuterClass {
                        ctor @Deprecated public DeprecatedOuterClass();
                      }
                      @Deprecated public class DeprecatedOuterClass.EffectivelyDeprecatedInnerClass extends test.pkg.DeprecatedOuterClass {
                        ctor @Deprecated public DeprecatedOuterClass.EffectivelyDeprecatedInnerClass();
                        method @Deprecated public void usesDeprecatedOuterClass(test.pkg.DeprecatedOuterClass!);
                      }
                      public class NotDeprecatedClass extends test.pkg.DeprecatedOuterClass {
                        ctor public NotDeprecatedClass();
                        method public void usesDeprecatedOuterClass(test.pkg.DeprecatedOuterClass!);
                      }
                    }
                """,
            extraArguments =
                arrayOf(ARG_ERROR, "ReferencesDeprecated", ARG_ERROR, "ExtendsDeprecated"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/NotDeprecatedClass.java:2: error: Extending deprecated super class class test.pkg.DeprecatedOuterClass from test.pkg.NotDeprecatedClass: this class should also be deprecated [ExtendsDeprecated]
                    src/test/pkg/NotDeprecatedClass.java:3: error: Parameter references deprecated type test.pkg.DeprecatedOuterClass in test.pkg.NotDeprecatedClass.usesDeprecatedOuterClass(): this method should also be deprecated [ReferencesDeprecated]
                """,
        )
    }

    @Test
    fun `Test that usage of effectively deprecated class is flagged`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @Deprecated
                            public class DeprecatedOuterClass {
                                public class EffectivelyDeprecatedInnerClass {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class NotDeprecatedClass extends DeprecatedOuterClass.EffectivelyDeprecatedInnerClass {
                                public void usesEffectivelyDeprecatedInnerClass(DeprecatedOuterClass.EffectivelyDeprecatedInnerClass edic) {}
                            }
                        """
                    )
                ),
            api =
                """
                    package test.pkg {
                      @Deprecated public class DeprecatedOuterClass {
                        ctor @Deprecated public DeprecatedOuterClass();
                      }
                      @Deprecated public class DeprecatedOuterClass.EffectivelyDeprecatedInnerClass {
                        ctor @Deprecated public DeprecatedOuterClass.EffectivelyDeprecatedInnerClass();
                      }
                      public class NotDeprecatedClass extends test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass {
                        ctor public NotDeprecatedClass();
                        method public void usesEffectivelyDeprecatedInnerClass(test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass!);
                      }
                    }
                """,
            extraArguments =
                arrayOf(ARG_ERROR, "ReferencesDeprecated", ARG_ERROR, "ExtendsDeprecated"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/NotDeprecatedClass.java:2: error: Extending deprecated super class class test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass from test.pkg.NotDeprecatedClass: this class should also be deprecated [ExtendsDeprecated]
                    src/test/pkg/NotDeprecatedClass.java:3: error: Parameter references deprecated type test.pkg.DeprecatedOuterClass in test.pkg.NotDeprecatedClass.usesEffectivelyDeprecatedInnerClass(): this method should also be deprecated [ReferencesDeprecated]
                    src/test/pkg/NotDeprecatedClass.java:3: error: Parameter references deprecated type test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass in test.pkg.NotDeprecatedClass.usesEffectivelyDeprecatedInnerClass(): this method should also be deprecated [ReferencesDeprecated]
                """,
        )
    }

    @Test
    fun `Test usage of deprecated type `() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @Deprecated
                            public class DeprecatedClass {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            public class NotDeprecatedClass {
                                public List<DeprecatedClass> usesDeprecated(List<DeprecatedClass> list) {
                                    return list;
                                }
                            }
                        """
                    )
                ),
            api =
                """
                    package test.pkg {
                      @Deprecated public class DeprecatedClass {
                        ctor @Deprecated public DeprecatedClass();
                      }
                      public class NotDeprecatedClass {
                        ctor public NotDeprecatedClass();
                        method public java.util.List<test.pkg.DeprecatedClass!>! usesDeprecated(java.util.List<test.pkg.DeprecatedClass!>!);
                      }
                    }
                """,
            extraArguments = arrayOf(ARG_ERROR, "ReferencesDeprecated"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/NotDeprecatedClass.java:4: error: Parameter references deprecated type test.pkg.DeprecatedClass in test.pkg.NotDeprecatedClass.usesDeprecated(): this method should also be deprecated [ReferencesDeprecated]
                    src/test/pkg/NotDeprecatedClass.java:4: error: Return type references deprecated type test.pkg.DeprecatedClass in test.pkg.NotDeprecatedClass.usesDeprecated(): this method should also be deprecated [ReferencesDeprecated]
                """,
        )
    }

    @Test
    fun `Test propagation of @hide through package and class nesting`() {
        check(
            // Include system API annotations as a show annotation overrides hidden on a class that
            // is in a hidden package.
            includeSystemApiAnnotations = SystemApiType.PRIVILEGED_APPS,
            // This is set to true so any class that is incorrectly unhidden will be included in the
            // generated API and fail the test.
            showUnannotated = true,
            sourceFiles =
                arrayOf(
                    // Package "test.a" is hidden but "test.a.B" os marked with a show annotation so
                    // that should cause "test.a" to be unhidden. However, "test.a.C" should still
                    // be hidden as it inherits that from "test.a".
                    java(
                        """
                            /** @hide */
                            package test.a;
                        """
                    ),
                    java(
                        """
                            package test.a;
                            public class A {}
                        """
                    ),
                    java(
                        """
                            package test.a;
                            /** @hide */
                            @android.annotation.SystemApi
                            public class B {}
                        """
                    ),
                    java(
                        """
                            package test.a;
                            public class C {}
                        """
                    ),
                    // Package "test.a.b" is not hidden itself but should inherit the hidden status
                    // of the containing package "test.a" even though test.a has been unhidden
                    // because of "test.a.B" having a show annotation. This should then be unhidden
                    // because "test.a.b.B" has a show annotation but "test.a.b.C" should still be
                    // hidden as it inherits it from "test.a".
                    java(
                        """
                            package test.a.b;
                            public class A {}
                        """
                    ),
                    java(
                        """
                            package test.a.b;
                            /** @hide */
                            @android.annotation.SystemApi
                            public class B {}
                        """
                    ),
                    java(
                        """
                            package test.a.b;
                            public class C {}
                        """
                    ),
                    KnownSourceFiles.systemApiSource,
                ),
            api =
                """
                    package test.a {
                      public class B {
                        ctor public B();
                      }
                    }
                    package test.a.b {
                      public class B {
                        ctor public B();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Fail when erased type changes after pushing down methods from hidden super class`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Hidden.java:3: error: Explicitly override method test.pkg.Hidden.bad1() in class test.pkg.Public, or hide it in class test.pkg.Hidden; it cannot be implicitly inherited as API from the hidden super class because that would change its erased signature from ()Ltest/pkg/Hidden; to ()Ltest/pkg/Public;, and cause failures at runtime. [InheritChangesSignature]
                    src/test/pkg/Hidden.java:4: error: Explicitly override method test.pkg.Hidden.bad1(T) in class test.pkg.Public, or hide it in class test.pkg.Hidden; it cannot be implicitly inherited as API from the hidden super class because that would change its erased signature from (Ltest/pkg/Hidden;)V to (Ltest/pkg/Public;)V, and cause failures at runtime. [InheritChangesSignature]
                    src/test/pkg/Hidden.java:6: error: Explicitly override method test.pkg.Hidden.bad2() in class test.pkg.Public, or hide it in class test.pkg.Hidden; it cannot be implicitly inherited as API from the hidden super class because that would change its erased signature from ()Ljava/lang/Object; to ()Ljava/lang/Integer;, and cause failures at runtime. [InheritChangesSignature]
                    src/test/pkg/Hidden.java:7: error: Explicitly override method test.pkg.Hidden.bad2(U) in class test.pkg.Public, or hide it in class test.pkg.Hidden; it cannot be implicitly inherited as API from the hidden super class because that would change its erased signature from (Ljava/lang/Object;)V to (Ljava/lang/Integer;)V, and cause failures at runtime. [InheritChangesSignature]
                    src/test/pkg/Hidden.java:9: error: Explicitly override method test.pkg.Hidden.bad3() in class test.pkg.Public, or hide it in class test.pkg.Hidden; it cannot be implicitly inherited as API from the hidden super class because that would change its erased signature from ()Ljava/lang/Object; to ()Ljava/lang/Number;, and cause failures at runtime. [InheritChangesSignature]
                    src/test/pkg/Hidden.java:10: error: Explicitly override method test.pkg.Hidden.bad3(V) in class test.pkg.Public, or hide it in class test.pkg.Hidden; it cannot be implicitly inherited as API from the hidden super class because that would change its erased signature from (Ljava/lang/Object;)V to (Ljava/lang/Number;)V, and cause failures at runtime. [InheritChangesSignature]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Public<N extends Number, O> extends Hidden<Public, Integer, N, O> {
                                @Override
                                public Public overriddenOk() { return null; }
                                @Override
                                public void overriddenOk(Public t) { return null; }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            class Hidden<T extends Hidden, U, V, W> {
                                public T bad1() { return null; }
                                public void bad1(T t) {}

                                public U bad2() { return null; }
                                public void bad2(U t) {}

                                public V bad3() { return null; }
                                public void bad3(V t) {}

                                public W ok() { return null; }
                                public void ok(W t) { }

                                public T overriddenOk() { return null; }
                                public void overriddenOk(T t) { }

                                /** @hide */
                                public T hiddenOk() { return null; }
                                /** @hide */
                                public void hiddenOk(T t) { }
                            }
                        """
                    )
                ),
            extraArguments = arrayOf(ARG_ERROR, Issues.INHERIT_CHANGES_SIGNATURE.name)
        )
    }

    @RequiresCapabilities(Capability.KOTLIN, Capability.JAR_WITH_SOURCES)
    @Test
    fun `Checks do not run on bytecode-only items`() {
        check(
            expectedIssues =
                """
                src/test/pkg/IntValue.kt:8: warning: Method test.pkg.Foo.usesHiddenTypeAndValueClass(int) references hidden type test.pkg.HiddenClass. [HiddenTypeParameter]
                src/test/pkg/IntValue.kt:8: warning: Return type of unavailable type test.pkg.HiddenClass in test.pkg.Foo.usesHiddenTypeAndValueClass() [UnavailableSymbol]
                src/test/pkg/IntValue.kt:8: error: Class test.pkg.HiddenClass is hidden but was referenced (in return type) from public method test.pkg.Foo.usesHiddenTypeAndValueClass(int) [ReferencesHidden]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline value class IntValue(val v: Int)

                            /** @hide */
                            class HiddenClass

                            interface Foo {
                                fun usesHiddenTypeAndValueClass(iv: IntValue): HiddenClass
                            }
                        """
                    )
                ),
            // Compiled from the source above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31WeTgU3hoeM0ayZBvCVD8TIcuQLWRJ00wxYUwI2cZOxjbG" +
                        "zqSGMGookSxFKvs6lCU7WSZClDUSgxQSIurSvc+96rl1zvP9d773O8/3nu89" +
                        "LwYNYoYAWFlZAQAADLB7QQDMAH2ksY6srgFKTl/HQBeFPG8M10d97wAAlvXp" +
                        "nefQsvAeTrSsVBe9uwwr/0pxfMoHrqcvo6vfQ8ylYRf1ZL2l9Oh0adPFLrn2" +
                        "dvrk1LspIACD3sNaxHe0SG27wPHtwPyx/P7t8HUk+Mp5XXKW0/XwNcW5Ex3h" +
                        "9u44AiHUePQ81ATyYzSl6IVTeMpLbOn9VcQwmSreYpEHybpfTskwyTbq6OP1" +
                        "azxVGj2o/4haM2szflVOfWOcnUv9fHiJ4r53KaRbkwGfnG7JwybP+Wh3aTL8" +
                        "u9bTCKsLJNIPUL2V+zExzc4Vn5Utny6vmkxzV4tj8iwlzhV0yxbfjsrHw8Gm" +
                        "7RRYMzf8onU8LKFdmCMfSUIgOPQOHPAOWvXDJ+FoXkyEdFxbHj6Ac/ho91B/" +
                        "UMSET2ubQJD1kGeW/0ObihGa9AlT0UieyBnQcMkzpDY5M1IMK8WMEuI9aJg8" +
                        "cOVKZ5brwIfEsib5lMqZqczJ+vaaSGnBpDuuke06POkXUuNibmG9p37w2yTF" +
                        "eMpLHtwnCHdkcSiaEC5Ej0JPPxqUZChz4MyO+Hnyhlc/qq7GDpcAgU2QQ7dh" +
                        "gwjYvvxSB7cYSyJNmS6SZydcJqzwuTletDa/6K5TC+f9VgVURt348iCDw61v" +
                        "sPDZYrlSzfVsWm0nd05gtelVGLEn5o4nopONC9jvtYfAy9jyY0Py48x1jHLV" +
                        "Cl+7JNZYiLKiZ7+uitL3uUgPArnE3iBYPlrqBcYcsxdUAFvXiSapNUrOv66J" +
                        "+fh+1aJ/xC3PTdU3viB5XiPfPL3SPki8PaCcP1MI5xPdCX718ZsYCdcJVRs/" +
                        "wbzZN9tAvE0l3Kb0gW6HWUpKXThMk3Ch9mqdO3Ghq9ZaWFvzzmRghcbNGLbC" +
                        "5ZCZ+ecuZnspbblwSNVzBiRjbsI70Zn6Y6Ni6y73UC43ApviQE/ivBXBLSrG" +
                        "Z2WOzRq4ZWUccbinSc+4uasWZxmpNzs5kjAythYVOniIQxRsN6FbVoVXTk8e" +
                        "5kcnlpsqSCPLcJ3BIgeGFum93++ac32aQxUMSppT3QN1i44f7C60QA6Xz65k" +
                        "TL7S6CvLyTTGG4ATx85/Pq8jYU5jzRRbT/VQ05X5GJ+ThOGarTfKUp5jV6td" +
                        "5AwBi82c7o9T5BGsQwt/lt1is1sWTMLan3m5IlojXnrmaRD5OGlJc+1kb0An" +
                        "Nbxdl/2kBwuPpMIVvpG5J0ZVN1zDgCnQtOwVvhGtecUo3zS6b9884ebCk1m1" +
                        "jCVW93SsX+ngl1nthjqEmK9qKFsY2V/gS/vClPLI9Ae0hf34Ekl+JPKAKCwZ" +
                        "jGvr08zUY/H6ZwE8XgrHSsNBd7UaQuQyKwI1os6mhxAapmjgaUp4pToxNiIn" +
                        "+u397+rFfniZcsrDDf16VkIJerr/MrRwraxxWFa09ysAHb4q++50bYtNuOfc" +
                        "c7NPRRc05PU65ExJz9StWll8TYWlvxbL1ZG1lkB2E/d49q203eAeRDMq9cYf" +
                        "XJ5jPrS+7u2+Mde9mLpqpR567pu9gnIFt0Kx2zgfCJzfw2PIkbvy+KItXKtp" +
                        "QwBl5QJXsWzeJPYgB5DNyFctoTY+1OmOllSK0H6h/a9DJuIoEz9+ao7xLI+g" +
                        "HDMAMLznb5ojvFtzzro6ODh6IHYE59+ygzeqNgDqQDQV6w9LdyBOL5rkRbA3" +
                        "G9hnirXqY/iUlYxBJxrCHQfkQSXvL26CpPnYETJbkpNWWTQ1NWqwBMOplqSl" +
                        "xZRqS81weFgytly8YJUnElclxGbfa8uxHn6HTQ5StTBK3FPq3HdWXtVZTzq4" +
                        "K05V1d05/srk4WglRK/W0SaMx9ba+FFEhnKsezbNX7SAlcz/fqlHSQdpxpEs" +
                        "WF8f+b4u26w/CkyM3FqnEwMjqjUM3bXy6M6c1vZJOUUXrjECNfptlz7lFuEv" +
                        "kvkCNWivsoXAN2GvsRZjBigrtTDIaFyqnfRcdLFsq9QAui80SnwyvQZbkj1m" +
                        "SDMRHTRFGQVlEB0iTOJVsq/2NXQWLm2R+bFjp0A5D/zza3gmsnnVSeIVe8vQ" +
                        "6eHVbf4CTyNGW5wxoR2lB8VC8tfN1uIR6ZJsWjFvZi4LJRTrVoZfwqh4hTHt" +
                        "EJNDyz9hxAQAtDP9jRje3cSgPD3/8w9gGjz6TkJSn+i6v054V4HphTwVgale" +
                        "Ho2CShjn5BpJYnUq39NynvSId1V1FCKCKBsSi/xRoJjG69RTGtewcdxn844v" +
                        "+zm/hb54snCvDhBw4xuZ+Dif0n3m6tdiyRBumHBHTizBlWUp5TsmTf0f5cam" +
                        "XqngrLJzyf77GXMP26sU5Pp14AWhQ2nX30qU8eDWvDeyCJpLj0OOBDbwqbc4" +
                        "cHPd/mCYpaKS2AoxULeS14aPeCcz8ofmY9mSLvHVmRo49erW8k26Xt/Cmgzk" +
                        "AoFLKsf6TJLkO1f3SlACFSH6PDEux7jWjTfZTotcKZu2GxPY+8wNmXnkpsUw" +
                        "ZS72+oMzNto9drWbM/0guc9JSY1c0IbPTVfJue34RhSjO+END/TNN0MRchrp" +
                        "ociXhOKtb8rKHivUex6b77pRNxj3ElpnS/ReGF8c7Q5sIlqTF/FGBgb9GV38" +
                        "j8OOfPx+5EsAbM4MaSqhNF6yLODPvxELDM6rogTH7gmBFrsVXLOIkXdEmz3X" +
                        "f/TmrDe7EZdDSXTBISAFTxkW8Jfxk/lkoSQrLDDGtUMtFG86nbJNqxXwb9RC" +
                        "t+O/NgOPc/WAX/L0dXf1sMF7OhDdHe1tbW2dtoPZzoBFEmP30g7wc55XDtc8" +
                        "49vOFPzpIZiAEMD/0Hf7ix0T8+v6k6X5HWW3Yuz/BYH0Z2fyO8ju1y38C8gy" +
                        "89+k5nec3a3k/QUnluX/Tcbv+bvbBf0l35n1r+3HoMEsO8fA29ty+wJNO3iA" +
                        "fwFEnOJ9NQoAAA=="
                )
        )
    }
}
