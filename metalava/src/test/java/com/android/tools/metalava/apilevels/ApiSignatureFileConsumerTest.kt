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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [ApiSignatureFileConsumer]. */
class ApiSignatureFileConsumerTest {

    /**
     * Populates an [Api] across [versions] (where each version is a single signature file) using
     * [ApiSignatureFileConsumer], verifies that its XML output matches the XML produced by
     * [ApiFile.parseApi] + [addApisFromCodebase], and optionally asserts that the XML matches
     * [expectedXml].
     */
    private fun checkSignatureFiles(
        vararg versions: String,
        expectedXml: String? = null,
        useInternalNames: Boolean = true,
    ) {
        checkSignatureFiles(
            versions = versions.map { listOf(it) }.toTypedArray(),
            expectedXml = expectedXml,
            useInternalNames = useInternalNames,
        )
    }

    /**
     * Populates an [Api] across [versions] (where each version may consist of multiple delta
     * signature files) using [ApiSignatureFileConsumer], verifies that its XML output matches the
     * XML produced by [ApiFile.parseApi] + [addApisFromCodebase], and optionally asserts that the
     * XML matches [expectedXml].
     */
    private fun checkSignatureFiles(
        vararg versions: List<String>,
        expectedXml: String? = null,
        useInternalNames: Boolean = true,
    ) {
        val consumerApi = Api(useInternalNames = useInternalNames)
        val codebaseApi = Api(useInternalNames = useInternalNames)

        val versionedApis =
            versions.mapIndexed { index, versionFiles ->
                val apiVersion = ApiVersion.fromLevel(index + 1)
                val updater = ApiHistoryUpdater.forApiVersion(apiVersion)
                val signatureFiles =
                    versionFiles.mapIndexed { fileIndex, contents ->
                        SignatureFile.fromText("$apiVersion-$fileIndex.txt", contents)
                    }

                ApiFile.consumeApi(
                    signatureFiles,
                    ApiSignatureFileConsumer(consumerApi, updater),
                )

                val codebase = ApiFile.parseApi(signatureFiles)
                val fragment =
                    CodebaseFragment.create(
                        codebase,
                        factory = ::EmittableDelegatingVisitor,
                    )
                addApisFromCodebase(codebaseApi, updater, fragment)

                VersionedSourceApi({ fragment }, apiVersion)
            }

        consumerApi.clean()
        codebaseApi.clean()

        val consumerXml = printXml(consumerApi, versionedApis)
        val codebaseXml = printXml(codebaseApi, versionedApis)

        if (expectedXml != null) {
            assertEquals(expectedXml.trimIndent(), consumerXml)
        }
        assertEquals(codebaseXml, consumerXml)
    }

    /** Formats [api] using [ApiXmlPrinter] with 4-space indentation. */
    private fun printXml(api: Api, versionedApis: List<VersionedApi>): String {
        val writer = StringWriter()
        val printer = ApiXmlPrinter(null, versionedApis)
        PrintWriter(writer).use { printWriter -> printer.print(api, printWriter) }
        return writer.toString().trimIndent().replace("\t", "    ")
    }

    @Test
    fun `Packages and top-level class kinds`() {
        checkSignatureFiles(
            """
                // Signature format: 5.0
                package java.lang {
                  public class Object {
                  }
                }
                package test.pkg {
                  public class Foo {
                  }
                  public interface Bar {
                  }
                  public enum Baz {
                  }
                  public @interface MyAnnotation {
                  }
                  public final record MyRecord {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="java/lang/Object" since="1">
                        </class>
                        <class name="test/pkg/Bar" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/Baz" since="1">
                            <extends name="java/lang/Enum"/>
                            <method name="valueOf(Ljava/lang/String;)Ltest/pkg/Baz;"/>
                            <method name="values()[Ltest/pkg/Baz;"/>
                        </class>
                        <class name="test/pkg/Foo" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/MyAnnotation" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="java/lang/annotation/Annotation"/>
                        </class>
                        <class name="test/pkg/MyRecord" since="1">
                            <extends name="java/lang/Record"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Class deprecation and history across versions`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  @SomeAnnotation("ignored") public class Foo {
                  }
                }
            """,
            """
                // Signature format: 2.0
                package test.pkg {
                  public deprecated class Foo {
                  }
                  public class Bar {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Bar" since="2">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/Foo" since="1" deprecated="2">
                            <extends name="java/lang/Object"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Fields and enum constants`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    field public static final String CONST = "hello";
                    field protected int count;
                    field int packagePrivateField;
                  }
                  public enum Status {
                    enum_constant public static final test.pkg.Status ACTIVE;
                    enum_constant public static final deprecated test.pkg.Status LEGACY;
                  }
                }
            """,
            """
                // Signature format: 2.0
                package test.pkg {
                  public deprecated class Foo {
                    field public static final String CONST = "hello";
                    field protected deprecated int count;
                    field public String addedInDeprecatedClass;
                  }
                  public enum Status {
                    enum_constant public static final test.pkg.Status ACTIVE;
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Foo" since="1" deprecated="2">
                            <extends name="java/lang/Object"/>
                            <field name="CONST"/>
                            <field name="addedInDeprecatedClass" since="2"/>
                            <field name="count"/>
                        </class>
                        <class name="test/pkg/Status" since="1">
                            <extends name="java/lang/Enum"/>
                            <method name="valueOf(Ljava/lang/String;)Ltest/pkg/Status;"/>
                            <method name="values()[Ltest/pkg/Status;"/>
                            <field name="ACTIVE"/>
                            <field name="LEGACY" deprecated="1" removed="2"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Explicit extends and implements supertypes`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Base {
                  }
                  public interface FirstInterface {
                  }
                  public interface SecondInterface extends test.pkg.FirstInterface, Runnable {
                  }
                  public class Sub extends test.pkg.Base implements test.pkg.SecondInterface, Comparable<test.pkg.Sub> {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Base" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/FirstInterface" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/SecondInterface" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="java/lang/Runnable"/>
                            <implements name="test/pkg/FirstInterface"/>
                        </class>
                        <class name="test/pkg/Sub" since="1">
                            <extends name="test/pkg/Base"/>
                            <implements name="java/lang/Comparable"/>
                            <implements name="test/pkg/SecondInterface"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Implicit interfaces extended by other implemented interfaces`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface SuperInterface {
                  }
                  public interface SubInterface extends test.pkg.SuperInterface {
                  }
                  public class ImplementsBothFromStart implements test.pkg.SubInterface, test.pkg.SuperInterface {
                  }
                  public class ImplementsSubInterfaceLater implements test.pkg.SuperInterface {
                  }
                }
            """,
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface SuperInterface {
                  }
                  public interface SubInterface extends test.pkg.SuperInterface {
                  }
                  public class ImplementsBothFromStart implements test.pkg.SubInterface, test.pkg.SuperInterface {
                  }
                  public class ImplementsSubInterfaceLater implements test.pkg.SubInterface, test.pkg.SuperInterface {
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/ImplementsBothFromStart" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="test/pkg/SubInterface"/>
                        </class>
                        <class name="test/pkg/ImplementsSubInterfaceLater" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="test/pkg/SubInterface" since="2"/>
                            <implements name="test/pkg/SuperInterface"/>
                        </class>
                        <class name="test/pkg/SubInterface" since="1">
                            <extends name="java/lang/Object"/>
                            <implements name="test/pkg/SuperInterface"/>
                        </class>
                        <class name="test/pkg/SuperInterface" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Constructors`() {
        checkSignatureFiles(
            """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    ctor protected Foo(int, String...);
                    ctor Foo(boolean);
                    ctor public deprecated Foo(String?[]);
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Foo" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="&lt;init>(I[Ljava/lang/String;)V"/>
                            <method name="&lt;init>([Ljava/lang/String;)V" deprecated="1"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Methods and enum synthetic methods`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                    method public void doSomething();
                    method public String compute(int, boolean, String...);
                    method protected deprecated int[] legacyCompute(test.pkg.Foo);
                    method void packagePrivateMethod();
                  }
                  public enum Mode {
                    enum_constant public static final test.pkg.Mode FAST;
                    method public static test.pkg.Mode valueOf(String);
                    method public static test.pkg.Mode[] values();
                    method public static java.util.List<test.pkg.Mode> getEntries();
                    method public static test.pkg.Mode valueOf(int);
                    method public int customEnumMethod();
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Foo" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="compute(IZ[Ljava/lang/String;)Ljava/lang/String;"/>
                            <method name="doSomething()V"/>
                            <method name="legacyCompute(Ltest/pkg/Foo;)[I" deprecated="1"/>
                        </class>
                        <class name="test/pkg/Mode" since="1">
                            <extends name="java/lang/Enum"/>
                            <method name="customEnumMethod()I"/>
                            <method name="valueOf(I)Ltest/pkg/Mode;"/>
                            <method name="valueOf(Ljava/lang/String;)Ltest/pkg/Mode;"/>
                            <method name="values()[Ltest/pkg/Mode;"/>
                            <field name="FAST"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Nested classes and inner class constructors`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package android {
                  public final class Manifest {
                  }
                  public static final class Manifest.permission {
                    field public static final String CAMERA = "android.permission.CAMERA";
                  }
                }
                package test.pkg {
                  public class Outer {
                    ctor public Outer();
                  }
                  public static class Outer.StaticNested {
                    ctor public Outer.StaticNested(int);
                  }
                  public class Outer.Inner {
                    ctor public Outer.Inner(String);
                  }
                  public class Outer.Inner.DeepInner {
                    ctor public Outer.Inner.DeepInner(boolean);
                  }
                  public deprecated class DeprecatedOuter {
                  }
                  public static class DeprecatedOuter.Nested {
                    ctor public DeprecatedOuter.Nested();
                    method public void inheritedDeprecation();
                    field public int inheritedDeprecatedField;
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="android/Manifest" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="android/Manifest${'$'}permission" since="1">
                            <extends name="java/lang/Object"/>
                            <field name="CAMERA"/>
                        </class>
                        <class name="test/pkg/DeprecatedOuter" since="1" deprecated="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/DeprecatedOuter${'$'}Nested" since="1" deprecated="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="inheritedDeprecation()V"/>
                            <field name="inheritedDeprecatedField"/>
                        </class>
                        <class name="test/pkg/Outer" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/Outer${'$'}Inner" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>(Ltest/pkg/Outer;Ljava/lang/String;)V"/>
                        </class>
                        <class name="test/pkg/Outer${'$'}Inner${'$'}DeepInner" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>(Ltest/pkg/Outer${'$'}Inner;Z)V"/>
                        </class>
                        <class name="test/pkg/Outer${'$'}StaticNested" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>(I)V"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Class constructor and method type parameters`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Box<T, S extends CharSequence & java.io.Serializable> {
                    ctor public <U extends Number> Box(T, S, U);
                    method public T get();
                    method public void set(T...);
                    method public <U extends V, V extends S> U[] transform(T[], V);
                    method public <T extends Comparable<T>> T shadowed(T);
                  }
                  public class Box.Inner<W extends T> {
                    ctor public Box.Inner(W);
                    method public W getInner();
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Box" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>(Ljava/lang/Object;Ljava/lang/CharSequence;Ljava/lang/Number;)V"/>
                            <method name="get()Ljava/lang/Object;"/>
                            <method name="set([Ljava/lang/Object;)V"/>
                            <method name="shadowed(Ljava/lang/Comparable;)Ljava/lang/Comparable;"/>
                            <method name="transform([Ljava/lang/Object;Ljava/lang/CharSequence;)[Ljava/lang/CharSequence;"/>
                        </class>
                        <class name="test/pkg/Box${'$'}Inner" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>(Ltest/pkg/Box;Ljava/lang/Object;)V"/>
                            <method name="getInner()Ljava/lang/Object;"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Deprecated annotations without deprecated keyword`() {
        checkSignatureFiles(
            """
                // Signature format: 2.0
                package test.pkg {
                  @Deprecated public class Foo {
                  }
                  public class Bar {
                    ctor @java.lang.Deprecated public Bar();
                    method @Deprecated public void oldMethod();
                    field @java.lang.Deprecated(forRemoval = true) public int oldField;
                  }
                }
            """,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/Bar" since="1">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V" deprecated="1"/>
                            <method name="oldMethod()V" deprecated="1"/>
                            <field name="oldField" deprecated="1"/>
                        </class>
                        <class name="test/pkg/Foo" since="1" deprecated="1">
                            <extends name="java/lang/Object"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Multi-file delta class merging`() {
        checkSignatureFiles(
            listOf(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class PublicSuper {
                      }
                      public class SystemSuper extends test.pkg.PublicSuper {
                      }
                      public interface PublicInterface {
                      }
                      public interface SystemInterface {
                      }
                      public class MergedKeepSuper extends test.pkg.PublicSuper implements test.pkg.PublicInterface {
                        method public void publicMethod();
                      }
                      public class MergedOverrideSuper extends test.pkg.PublicSuper implements test.pkg.PublicInterface {
                        method public void publicMethod();
                      }
                    }
                """,
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class MergedKeepSuper {
                        method public void systemMethod();
                      }
                      public class MergedOverrideSuper extends test.pkg.SystemSuper implements test.pkg.PublicInterface, test.pkg.SystemInterface {
                        method public void systemMethod();
                      }
                    }
                """,
            ),
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test/pkg/MergedKeepSuper" since="1">
                            <extends name="test/pkg/PublicSuper"/>
                            <implements name="test/pkg/PublicInterface"/>
                            <method name="publicMethod()V"/>
                            <method name="systemMethod()V"/>
                        </class>
                        <class name="test/pkg/MergedOverrideSuper" since="1">
                            <extends name="test/pkg/SystemSuper"/>
                            <implements name="test/pkg/PublicInterface"/>
                            <implements name="test/pkg/SystemInterface"/>
                            <method name="publicMethod()V"/>
                            <method name="systemMethod()V"/>
                        </class>
                        <class name="test/pkg/PublicInterface" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/PublicSuper" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/SystemInterface" since="1">
                            <extends name="java/lang/Object"/>
                        </class>
                        <class name="test/pkg/SystemSuper" since="1">
                            <extends name="test/pkg/PublicSuper"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Source-style names when useInternalNames is false`() {
        checkSignatureFiles(
            """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo<T> extends java.util.AbstractList<T> implements java.io.Serializable {
                    ctor public Foo(String?, int...);
                    method public <U extends Comparable<U> & java.io.Serializable> void process(T, java.util.Map<String, ? extends U>, String?[]);
                    field public int count;
                  }
                  public class Foo.Bar {
                    ctor public Foo.Bar();
                  }
                  public enum Status {
                    enum_constant public static final test.pkg.Status OK;
                  }
                }
            """,
            useInternalNames = false,
            expectedXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <class name="test.pkg.Foo" since="1">
                            <extends name="java.util.AbstractList"/>
                            <implements name="java.io.Serializable"/>
                            <method name="Foo(java.lang.String,int...)"/>
                            <method name="process&lt;U extends java.lang.Comparable&lt;U> &amp; java.io.Serializable>(T,java.util.Map&lt;java.lang.String,? extends U>,java.lang.String[])"/>
                            <field name="count"/>
                        </class>
                        <class name="test.pkg.Foo.Bar" since="1">
                            <extends name="java.lang.Object"/>
                            <method name="Bar()"/>
                        </class>
                        <class name="test.pkg.Status" since="1">
                            <extends name="java.lang.Enum"/>
                            <method name="valueOf(java.lang.String)"/>
                            <method name="values()"/>
                            <field name="OK"/>
                        </class>
                    </api>
                """,
        )
    }
}
