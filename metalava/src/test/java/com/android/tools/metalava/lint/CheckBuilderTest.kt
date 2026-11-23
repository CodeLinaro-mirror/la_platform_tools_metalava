/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.suppressLintSource
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [ApiLint.checkBuilder] method. */
class CheckBuilderTest : DriverTest() {

    @Test
    fun `Check builders`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/Bad.java:13: warning: Builder must be final: android.pkg.Bad.BadBuilder [StaticFinalBuilder]
                src/android/pkg/Bad.java:13: warning: Builder must be static: android.pkg.Bad.BadBuilder [StaticFinalBuilder]
                src/android/pkg/Bad.java:14: warning: Builder constructor arguments must be mandatory (i.e. not @Nullable): parameter badParameter in android.pkg.Bad.BadBuilder(String badParameter) [OptionalBuilderConstructorArgument]
                src/android/pkg/Bad.java:20: warning: android.pkg.Bad does not declare a `getWithoutMatchingGetters()` method matching method android.pkg.Bad.BadBuilder.addWithoutMatchingGetter(String) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:23: warning: android.pkg.Bad does not declare a `isWithoutMatchingGetter()` method matching method android.pkg.Bad.BadBuilder.setWithoutMatchingGetter(boolean) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:26: warning: android.pkg.Bad does not declare a `getPluralWithoutMatchingGetters()` method matching method android.pkg.Bad.BadBuilder.addPluralWithoutMatchingGetter(java.util.Collection<java.lang.String>) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:32: warning: android.pkg.Bad does not declare a getter method matching method android.pkg.Bad.BadBuilder.addPluralWithoutMatchingGetters(java.util.Collection<java.lang.String>) (expected one of: [getPluralWithoutMatchingGetters(), getPluralWithoutMatchingGetterses()]) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:38: warning: Builder methods names should use setFoo() / addFoo() / clearFoo() style: method android.pkg.Bad.BadBuilder.withBadSetterStyle(boolean) [BuilderSetStyle]
                src/android/pkg/Bad.java:41: warning: Builder setter must be @NonNull: method android.pkg.Bad.BadBuilder.setReturnsNullable(boolean) [SetterReturnsThis]
                src/android/pkg/Bad.java:43: warning: Getter should be on the built object, not the builder: method android.pkg.Bad.BadBuilder.getOnBuilder() [GetterOnBuilder]
                src/android/pkg/Bad.java:45: warning: android.pkg.Bad does not declare a `isNotReturningBuilder()` method matching method android.pkg.Bad.BadBuilder.setNotReturningBuilder(boolean) [MissingGetterMatchingBuilder]
                src/android/pkg/Bad.java:45: warning: Methods must return the builder object (return type android.pkg.Bad.BadBuilder instead of void): method android.pkg.Bad.BadBuilder.setNotReturningBuilder(boolean) [SetterReturnsThis]
                src/android/pkg/Bad.java:51: warning: android.pkg.Bad.NoBuildMethodBuilder does not declare a `build()` method, but builder classes are expected to [MissingBuildMethod]
                src/android/pkg/Bad.java:57: warning: Methods must return the builder object (return type android.pkg.Bad.BadGenericBuilder<T> instead of T): method android.pkg.Bad.BadGenericBuilder.setBoolean(boolean) [SetterReturnsThis]
                src/android/pkg/TopLevelBuilder.java:3: warning: android.pkg.TopLevelBuilder does not declare a `build()` method, but builder classes are expected to [MissingBuildMethod]
                src/android/pkg/TopLevelBuilder.java:3: warning: Builder should be defined as nested class: android.pkg.TopLevelBuilder [TopLevelBuilder]
                src/test/pkg/BadClass.java:6: warning: Public API for builder class test.pkg.BadClass.Builder only contains `build` method. A public constructor on the built class should be provided instead. [EmptyBuilder]
                src/test/pkg/BadClass.java:6: warning: Builder must be final: test.pkg.BadClass.Builder [StaticFinalBuilder]
                src/test/pkg/BadInterface.java:6: warning: Public API for builder class test.pkg.BadInterface.Builder only contains `build` method. A public constructor on the built class should be provided instead. [EmptyBuilder]
                src/test/pkg/BadInterface.java:6: warning: Builder must be final: test.pkg.BadInterface.Builder [StaticFinalBuilder]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public final class TopLevelBuilder {
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;

                    public class Ok {

                        public int getInt();
                        @NonNull
                        public List<String> getStrings();
                        @NonNull
                        public List<String> getProperties();
                        @NonNull
                        public List<String> getRays();
                        @NonNull
                        public List<String> getBuses();
                        @NonNull
                        public List<String> getTaxes();
                        @NonNull
                        public List<String> getMessages();
                        public boolean isBoolean();
                        public boolean hasBoolean2();
                        public boolean shouldBoolean3();

                        public static final class OkBuilder {
                            public OkBuilder(@NonNull String goodParameter, int goodParameter2) {}

                            @NonNull
                            public Ok build() { return null; }

                            @NonNull
                            public OkBuilder setInt(int value) { return this; }

                            @NonNull
                            public OkBuilder addString(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addProperty(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addRay(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addBus(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addTax(@NonNull String value) { return this; }

                            @NonNull
                            public OkBuilder addMessages(@NonNull Collection<String> value) {
                                return this;
                            }

                            @NonNull
                            public OkBuilder clearStrings() { return this; }

                            @NonNull
                            public OkBuilder setBoolean(boolean v) { return this; }

                            @NonNull
                            public OkBuilder setHasBoolean2(boolean v) { return this; }

                            @NonNull
                            public OkBuilder setShouldBoolean3(boolean v) { return this; }

                            @NonNull
                            public OkBuilder clear() { return this; }

                            @NonNull
                            public OkBuilder clearAll() { return this; }
                        }

                        public static final class GenericBuilder<B extends GenericBuilder> {
                            @NonNull
                            public B setBoolean(boolean value) { return this; }

                            @NonNull
                            public Ok build() { return null; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;

                    public class SubOk extends Ok {

                        public static final class Builder {
                            public Builder() {}

                            @NonNull
                            public SubOk build() { return null; }

                            @NonNull
                            public Builder setInt(int value) { return this; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;
                    import java.util.Collection;

                    public class Bad {

                        public boolean isBoolean();
                        public boolean getWithoutMatchingGetter();
                        public boolean isReturnsNullable();

                        public class BadBuilder {
                            public BadBuilder(@Nullable String badParameter) {}

                            @NonNull
                            public Bad build() { return null; }

                            @NonNull
                            public BadBuilder addWithoutMatchingGetter(@NonNull String value) { return this; }

                            @NonNull
                            public BadBuilder setWithoutMatchingGetter(boolean v) { return this; }

                            @NonNull
                            public BadBuilder addPluralWithoutMatchingGetter(
                                @NonNull Collection<String> value) {
                                return this;
                            }

                            @NonNull
                            public BadBuilder addPluralWithoutMatchingGetters(
                                @NonNull Collection<String> value) {
                                return this;
                            }

                            @NonNull
                            public BadBuilder withBadSetterStyle(boolean v) { return this; }

                            @Nullable
                            public BadBuilder setReturnsNullable(boolean v) { return this; }

                            public boolean getOnBuilder() { return true; }

                            public void setNotReturningBuilder(boolean v) { return this; }

                            @NonNull
                            public BadBuilder () { return this; }
                        }

                        public static final class NoBuildMethodBuilder {
                            public NoBuildMethodBuilder() {}
                        }

                        public static final class BadGenericBuilder<T extends Bad> {
                            @NonNull
                            public T setBoolean(boolean value) { return this; }

                            @NonNull
                            public Bad build() { return null; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    public class GoodInterface {
                        public interface Builder extends java.lang.Runnable {
                            @NonNull
                            GoodInterface build();
                        }
                    }
                        """
                    ),
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    public class GoodClass {
                        public static abstract class Builder extends Base {
                            @NonNull
                            public abstract GoodClass build();
                        }
                        public class Base {}
                    }
                        """
                    ),
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    public class BadInterface {
                        public interface Builder {
                            @NonNull
                            BadInterface build();
                        }
                    }
                        """
                    ),
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    public class BadClass {
                        public static abstract class Builder {
                            @NonNull
                            public abstract BadClass build();
                        }
                    }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                    KnownSourceFiles.androidxNullableJavaSource
                )
        )
    }

    /** Example created from go/android-api-guidelines#builders-return-builder */
    @Test
    fun `Check setters incorrectly returning non-builder object`() {
        check(
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.NonNull;
                            import java.util.List;
                            public class Test {
                                private Test() {}
                                public int getSomething() {return -1;}
                                public @NonNull List<Other> getOthers() {return List.of();}
                                public static final class Builder {
                                    public void setSomething(int i) {}
                                    public @NonNull OtherBuilder addOther() {return OtherBuilder();}
                                    public @NonNull Test build() {return Tone();}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.NonNull;
                            public class Other {
                                private Other() {}
                                public boolean isElse() {return false;}
                                public static final class Builder {
                                    public boolean setElse(boolean b) {return false;}
                                    public @NonNull Other build() {return Other();}
                                }
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                ),
            expectedIssues =
                """
                    src/test/pkg/Other.java:7: warning: Methods must return the builder object (return type test.pkg.Other.Builder instead of boolean): method test.pkg.Other.Builder.setElse(boolean) [SetterReturnsThis]
                    src/test/pkg/Test.java:9: warning: Methods must return the builder object (return type test.pkg.Test.Builder instead of void): method test.pkg.Test.Builder.setSomething(int) [SetterReturnsThis]
                    src/test/pkg/Test.java:10: warning: Methods must return the builder object (return type test.pkg.Test.Builder instead of OtherBuilder): method test.pkg.Test.Builder.addOther() [SetterReturnsThis]
                """,
        )
    }

    /** Example created from go/android-api-guidelines#builders-return-builder */
    @Test
    fun `Check setters correctly returning builder object`() {
        check(
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.NonNull;
                            import java.util.List;
                            public class Test {
                                private Test() {}
                                public int getSomething() {return -1;}
                                public @NonNull List<Other> getOthers() {return List.of();}
                                public static final class Builder {
                                    public @NonNull Builder setSomething(int i) {return this;}
                                    public @NonNull Builder addOther(@NonNull Other other) {return this;}
                                    public @NonNull Test build() {return Tone();}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Other {
                                public Other() {}
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                ),
        )
    }

    /** Example created from go/android-api-guidelines#builders-return-builder */
    @Test
    fun `Check setters correctly returning generic builder object`() {
        check(
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.SuppressLint;
                            import androidx.annotation.NonNull;
                            import java.util.List;
                            public class Test {
                                private Test() {}
                                public int getSomething() {return -1;}
                                // TODO(b/476956538): This should not be required.
                                // This builder is specifically designed to be extended.
                                @SuppressLint("StaticFinalBuilder")
                                public static class Builder<T extends Builder<T>> {
                                    public @NonNull Builder<T> setSomething(int i) {return this;}
                                    public @NonNull Test build() {return Tone();}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Other extends Test {
                                public Other() {}
                                public boolean isOr() {return false;}
                                public int getElse() {return false;}
                                public static final class Builder<T extends Builder<T>> extends Test.Builder<T> {
                                    public @NonNull Builder<T> setOr(boolean b) {return this;}
                                    public @NonNull T setElse(int i) {return this;}
                                    public @NonNull Other build() {return Other();}
                                }
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                    suppressLintSource,
                ),
        )
    }

    @Test
    fun `Check builder only containing build method`() {
        check(
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import androidx.annotation.NonNull;
                        public class Foo {
                            public static final class FooBuilder {
                                @NonNull
                                public Foo build() {
                                    return new Foo();
                                }
                            }
                        }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                ),
            expectedIssues =
                "src/test/pkg/Foo.java:4: warning: Public API for builder class test.pkg.Foo.FooBuilder only contains `build` method. A public constructor on the built class should be provided instead. [EmptyBuilder]",
        )
    }
}
