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

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.ARG_HIDE_ANNOTATION
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.KnownSourceFiles.restrictToSource
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Integration tests for [ApiContents]. */
class ApiContentsTest : DriverTest() {
    @Test
    fun `Test hidden nested class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public interface Outer {
                                /** @hide */
                                interface Nested {}
                            }
                        """
                    ),
                ),
            expectedIssues = "",
        )
    }

    @Test
    fun `Accessible nested class inside inaccessible class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            interface Outer {
                                interface Nested {}
                            }
                        """
                    ),
                ),
            expectedIssues = "",
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't flag indirect implementor of super-interface marked with RestrictTo(LIBRARY_GROUP_PREFIX)`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/test/pkg/RestrictedParentInterface.kt:7: warning: Public class test.pkg.PublicChildInterface stripped of unavailable superclass test.pkg.RestrictedParentInterface [HiddenSuperclass]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            import androidx.annotation.RestrictTo
                            import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX

                            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                            public interface RestrictedParentInterface {}
                            public interface PublicChildInterface : RestrictedParentInterface {}
                            public class PublicGrandchildClass : PublicChildInterface {}
                        """
                    ),
                    restrictToSource,
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_ANNOTATION,
                    "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX)"
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't flag indirect descendant of superclass marked with RestrictTo(LIBRARY_GROUP_PREFIX)`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/test/pkg/RestrictedParentClass.kt:7: warning: Public class test.pkg.PublicChildClass stripped of unavailable superclass test.pkg.RestrictedParentClass [HiddenSuperclass]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            import androidx.annotation.RestrictTo
                            import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX

                            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                            public open class RestrictedParentClass {}
                            public open class PublicChildClass : RestrictedParentClass() {}
                            public class PublicGrandchildClass : PublicChildClass() {}
                        """
                    ),
                    restrictToSource,
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_ANNOTATION,
                    "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX)"
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Flag superclasses that are marked with RestrictTo(LIBRARY_GROUP_PREFIX)`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/test/pkg/RestrictedParentClass.kt:7: warning: Public class test.pkg.PublicChildClass stripped of unavailable superclass test.pkg.RestrictedParentClass [HiddenSuperclass]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            import androidx.annotation.RestrictTo
                            import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX

                            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
                            public open class RestrictedParentClass {}
                            public class PublicChildClass : RestrictedParentClass() {}
                        """
                    ),
                    restrictToSource,
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_ANNOTATION,
                    "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX)"
                ),
        )
    }

    @Test
    fun `Test that usage of a hidden class as type parameter of an outer class is flagged`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:3: warning: Field test.pkg.Foo.fieldReferencesHidden1 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:3: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden1 [ReferencesHidden]
                    src/test/pkg/Foo.java:4: warning: Field test.pkg.Foo.fieldReferencesHidden2 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:4: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden2 [ReferencesHidden]
                    src/test/pkg/Foo.java:5: warning: Field test.pkg.Foo.fieldReferencesHidden3 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:5: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden3 [ReferencesHidden]
                    src/test/pkg/Foo.java:6: warning: Field test.pkg.Foo.fieldReferencesHidden4 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                    src/test/pkg/Foo.java:6: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden4 [ReferencesHidden]
                """,
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
                            /** @hide */
                            interface HiddenInterface
                            class PublicClass {
                                fun returnsHiddenInterface(): HiddenInterface = TODO()
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class PublicClass {
                        ctor public PublicClass();
                        method public test.pkg.HiddenInterface returnsHiddenInterface();
                      }
                    }
                """,
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
                    ),
                ),
            expectedApiSignature =
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
                "src/test/pkg/Container.java:4: warning: Public class test.pkg.Container.PublicInnerClass extends private class test.pkg.Container.PrivateInnerClass [PrivateSuperclass]",
        )
    }

    @RequiresCapabilities(Capability.KOTLIN, Capability.JAR_WITH_SOURCES)
    @Test
    fun `Checks do not run on bytecode-only items`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/IntValue.kt:8: warning: Method test.pkg.Foo.usesHiddenTypeAndValueClass(test.pkg.IntValue) references hidden type test.pkg.HiddenClass. [HiddenTypeParameter]
                    src/test/pkg/IntValue.kt:8: warning: Return type of unavailable type test.pkg.HiddenClass in test.pkg.Foo.usesHiddenTypeAndValueClass() [UnavailableSymbol]
                    src/test/pkg/IntValue.kt:8: error: Class test.pkg.HiddenClass is hidden but was referenced (in return type) from public method test.pkg.Foo.usesHiddenTypeAndValueClass(test.pkg.IntValue) [ReferencesHidden]
                """,
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
                    ),
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

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Reference to hidden type in property context parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            /** @hide */
                            class HiddenType
                            class Foo {
                                context(hidden: HiddenType)
                                val propertyWithContext: Int get() = 0
                            }
                        """
                    ),
                ),
            expectedIssues =
                // One `HiddenTypeParameter` error is for the property, one is for the getter.
                // The `ReferencesHidden` and `UnavailableSymbol` checks look specifically at
                // methods so the errors are for the getter.
                """
                    src/test/pkg/HiddenType.kt:5: warning: Parameter hidden references hidden type test.pkg.HiddenType. [HiddenTypeParameter]
                    src/test/pkg/HiddenType.kt:5: error: Class test.pkg.HiddenType is hidden but was referenced (in parameter type) from public parameter hidden in test.pkg.Foo.getPropertyWithContext(test.pkg.HiddenType hidden) [ReferencesHidden]
                    src/test/pkg/HiddenType.kt:6: warning: Parameter of unavailable type test.pkg.HiddenType in test.pkg.Foo.getPropertyWithContext() [UnavailableSymbol]
                """,
        )
    }

    @Test
    fun `Reference to hidden nested class from source path`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public interface Test<T extends Outer.Hidden> {
                            }
                        """
                    ),
                ),
            additionalSourcePathFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @hide */
                            public interface Outer {
                                interface Hidden {}
                            }
                        """
                    ),
                ),
            expectedIssues =
                """
                    ADDITIONAL-SOURCE-PATH/test/pkg/Outer.java:4: error: Class test.pkg.Outer is hidden but was referenced (as containing class) from public class test.pkg.Outer.Hidden [ReferencesHidden]
                    src/test/pkg/Test.java:2: error: Class test.pkg.Outer is hidden but was referenced (as type parameter) from public class test.pkg.Test [ReferencesHidden]
                    src/test/pkg/Test.java:2: error: Class test.pkg.Outer.Hidden is hidden but was referenced (as type parameter) from public class test.pkg.Test [ReferencesHidden]
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Reference to hidden class from public typealias`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        /** @hide */
                        class Hidden
                        typealias Public = Hidden
                        """
                    )
                ),
            expectedIssues =
                """
                src/test/pkg/Hidden.kt:4: warning: Typealias test.pkg.Public references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                src/test/pkg/Hidden.kt:4: error: Class test.pkg.Hidden is hidden but was referenced (aliased type) from public typealias test.pkg.Public [ReferencesHidden]
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Reference to hidden class from public typealias for multiplatform codebase`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                annotation class Hide
                @Hide class Hidden
                expect class Public
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Native.kt",
                """
                package test.pkg
                actual typealias Public = Hidden
                """
            )
        check(
            enableMultiplatform = true,
            skipSourceArgs = true,
            sourceFiles = arrayOf(commonSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
            hideAnnotations = arrayOf("test.pkg.Hide"),
            expectedIssues =
                """
                nativeMain/src/test/pkg/Native.kt:2: warning: Typealias test.pkg.Public references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Reference to internal class from public API`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            internal interface InternalInterface

                            class PublicClass {
                                @Suppress("EXPOSED_TYPE_PARAMETER_BOUND_DEPRECATION_WARNING")
                                fun <T : InternalInterface> method(t: T) {}
                            }
                        """
                    ),
                ),
            expectedIssues =
                """
                    src/test/pkg/InternalInterface.kt:7: error: Class test.pkg.InternalInterface is not public but was referenced (as type parameter) from public method test.pkg.PublicClass.method(T) [ReferencesHidden]
                """,
        )
    }
}
