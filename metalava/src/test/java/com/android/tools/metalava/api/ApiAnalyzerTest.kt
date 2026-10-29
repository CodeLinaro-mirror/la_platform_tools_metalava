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

package com.android.tools.metalava.api

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.cli.common.ARG_SKIP_READING_COMMENTS
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.KnownSourceFiles.hideAnnotation
import com.android.tools.metalava.testing.KnownSourceFiles.systemApiSource
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
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
                    src/test/pkg/SystemApiClass.java:6: error: badAbstractHiddenMethod cannot be hidden and abstract when SystemApiClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
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
                           @SystemApi
                           public abstract class SystemApiClass {
                                /** @hide */
                                public abstract boolean badAbstractHiddenMethod() { return true; }
                                /**
                                 * This method is OK, because it matches visibility of the class
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
                            /**
                             * This class is OK because it is all hidden
                             * @hide
                             */
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

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Hidden abstract method in interface for public API`() {
        check(
            extraArguments = errorIssues(Issues.HIDDEN_ABSTRACT_METHOD_IN_INTERFACE),
            hideAnnotations = arrayOf("android.annotation.Hide"),
            expectedIssues =
                """
                src/test/pkg/Interface.kt:6: error: errorAbstract cannot be hidden and abstract when Interface is a non-sealed interface, in case a third-party attempts to subclass it. [HiddenAbstractMethodInInterface]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg;
                        import android.annotation.Hide;

                        interface Interface {
                            @Hide
                            fun errorAbstract(): Int
                            @Hide
                            fun okDefault(): Int = 0
                        }

                        sealed interface SealedInterface {
                            @Hide
                            fun okAbstract(): Int
                        }
                        """
                    ),
                    hideAnnotation
                ),
        )
    }

    @Test
    fun `Hidden abstract method in non-API interface referenced by public API`() {
        check(
            extraArguments = errorIssues(Issues.HIDDEN_ABSTRACT_METHOD_IN_INTERFACE),
            expectedIssues =
                """
                    src/test/pkg/PublicClass.java:4: warning: Parameter p references hidden type test.pkg.PackagePrivateInterface. [HiddenTypeParameter]
                    src/test/pkg/PublicClass.java:4: error: Class test.pkg.PackagePrivateInterface is not public but was referenced (in parameter type) from public parameter p in test.pkg.PublicClass.foo(test.pkg.PackagePrivateInterface p) [ReferencesHidden]
                """,
            sourceFiles =
                @Suppress("ClassEscapesDefinedScope") // For PackagePrivateInterface
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class PublicClass {
                                public void foo(PackagePrivateInterface p) {}
                            }

                            interface PackagePrivateInterface {
                                void hiddenAbstractMethod();
                            }
                        """
                    ),
                ),
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

    @Test
    fun `Test that DeprecationMismatch is not reported when comments are ignored`() {
        check(
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @Deprecated
                        public class MissingDeprecatedDoc {}
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        /** @deprecated reason */
                        public class MissingDeprecatedAnno {}
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        /** @deprecated reason */
                        @Deprecated
                        public class CorrectDeprecation {}
                        """
                    )
                ),
            extraArguments = arrayOf(ARG_SKIP_READING_COMMENTS),
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
            expectedApiSignature =
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
            expectedStubFiles =
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
            expectedApiSignature =
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
            expectedStubFiles =
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
            extraArguments =
                hiddenIssues(
                    Issues.INHERIT_CHANGES_SIGNATURE,
                ),
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
            expectedApiSignature =
                """
                    package test.pkg {
                      @Deprecated public final class Foo {
                        ctor @Deprecated public Foo(@Deprecated int i, @Deprecated boolean b);
                        method @InaccessibleFromKotlin @Deprecated public boolean getB();
                        method @InaccessibleFromKotlin @Deprecated public int getI();
                        method @InaccessibleFromKotlin @Deprecated public void setB(boolean);
                        method @InaccessibleFromKotlin @Deprecated public void setI(int);
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
            extraArguments =
                arrayOf(ARG_SKIP_READING_COMMENTS) +
                    errorIssues(
                        Issues.REFERENCES_DEPRECATED,
                    ),
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
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Concrete {
                        ctor @Deprecated public Concrete();
                        method @Deprecated public void bar();
                      }
                    }
                """,
            expectedStubFiles =
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
            expectedApiSignature =
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
                errorIssues(
                    Issues.REFERENCES_DEPRECATED,
                    Issues.EXTENDS_DEPRECATED,
                ),
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
            expectedApiSignature =
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
                errorIssues(
                    Issues.REFERENCES_DEPRECATED,
                    Issues.EXTENDS_DEPRECATED,
                ),
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
            expectedApiSignature =
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
            extraArguments =
                errorIssues(
                    Issues.REFERENCES_DEPRECATED,
                ),
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
            // is in a hidden package. This also includes unannotated items so any class that is
            // incorrectly unhidden will be included in the generated API and fail the test.
            apiSurface = KnownApiSurface.SYSTEM_WITH_PUBLIC,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.hideAnnotation,
                    // Package "test.a" is hidden but "test.a.B" os marked with a show annotation so
                    // that should cause "test.a" to be unhidden. However, "test.a.C" should still
                    // be hidden as it inherits that from "test.a".
                    java(
                        """
                            @android.annotation.Hide
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
                ),
            expectedApiSignature =
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
            extraArguments =
                errorIssues(
                    Issues.INHERIT_CHANGES_SIGNATURE,
                )
        )
    }

    @RequiresCapabilities(Capability.MULTIPLATFORM)
    @Test
    fun `Multiplatform signature includes top level expect fun`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect fun foo(): Unit
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual fun foo() = Unit
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // skip creating a regular codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public class ${'$'}TopLevelDeclarations {
                            method public static final void foo();
                          }
                        }
                        """,
                    "androidMain.txt" to
                        """
                        // Signature format: 5.0
                        """
                )
        )
    }

    @RequiresCapabilities(Capability.MULTIPLATFORM)
    @Test
    fun `Multiplatform signature includes top level expect val`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect val foo: Int
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual val foo = 0
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // skip creating a regular codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public class ${'$'}TopLevelDeclarations {
                            property public static final kotlin.Int foo;
                          }
                        }
                        """,
                    "androidMain.txt" to
                        """
                        // Signature format: 5.0
                        """
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Signature does not include facade class for top level property hidden through field`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        import android.annotation.Hide
                        @field:Hide
                        const val HIDDEN_CONST = 0
                        """
                    ),
                    KnownSourceFiles.hideAnnotation,
                ),
            hideAnnotations = arrayOf("android.annotation.Hide"),
            expectedApiSignature =
                """
                // Signature format: 5.0
                """
        )
    }
}
