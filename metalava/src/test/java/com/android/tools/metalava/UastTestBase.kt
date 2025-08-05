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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.FilterAction.EXCLUDE
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.stripBlankLines
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.defaultJvmPlatforms
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Base class to collect test inputs whose behaviors (API/lint) vary depending on UAST versions. */
@RequiresCapabilities(Capability.KOTLIN)
abstract class UastTestBase : DriverTest() {

    @Test
    fun `Test RequiresOptIn and OptIn`() {
        // See http://b/248341155 for more details
        val klass = if (isK2) "Class" else "kotlin.reflect.KClass"
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    @RequiresOptIn
                    @Retention(AnnotationRetention.BINARY)
                    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
                    annotation class ExperimentalBar

                    @ExperimentalBar
                    class FancyBar

                    @OptIn(FancyBar::class) // @OptIn should not be tracked as it is not API
                    class SimpleClass {
                        fun methodUsingFancyBar() {
                            val fancyBar = FancyBar()
                        }
                    }

                    @androidx.annotation.experimental.UseExperimental(FancyBar::class) // @UseExperimental should not be tracked as it is not API
                    class AnotherSimpleClass {
                        fun methodUsingFancyBar() {
                            val fancyBar = FancyBar()
                        }
                    }
                """
                    ),
                    kotlin(
                        """
                    package androidx.annotation.experimental

                    import kotlin.annotation.Retention
                    import kotlin.annotation.Target
                    import kotlin.reflect.KClass

                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.CLASS,
                        AnnotationTarget.PROPERTY,
                        AnnotationTarget.LOCAL_VARIABLE,
                        AnnotationTarget.VALUE_PARAMETER,
                        AnnotationTarget.CONSTRUCTOR,
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.PROPERTY_GETTER,
                        AnnotationTarget.PROPERTY_SETTER,
                        AnnotationTarget.FILE,
                        AnnotationTarget.TYPEALIAS
                    )
                    annotation class UseExperimental(
                        /**
                         * Defines the experimental API(s) whose usage this annotation allows.
                         */
                        vararg val markerClass: KClass<out Annotation>
                    )
                """
                    )
                ),
            format = FileFormat.V4,
            api =
                """
                // Signature format: 4.0
                package androidx.annotation.experimental {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE, kotlin.annotation.AnnotationTarget.VALUE_PARAMETER, kotlin.annotation.AnnotationTarget.CONSTRUCTOR, kotlin.annotation.AnnotationTarget.FUNCTION, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER, kotlin.annotation.AnnotationTarget.FILE, kotlin.annotation.AnnotationTarget.TYPEALIAS}) public @interface UseExperimental {
                    ctor @KotlinOnly public UseExperimental(kotlin.reflect.KClass<? extends java.lang.annotation.Annotation>... markerClass);
                    method public abstract $klass<? extends java.lang.annotation.Annotation>[] markerClass();
                    property public abstract kotlin.reflect.KClass<? extends java.lang.annotation.Annotation>[] markerClass;
                  }
                }
                package test.pkg {
                  public final class AnotherSimpleClass {
                    ctor public AnotherSimpleClass();
                    method public void methodUsingFancyBar();
                  }
                  @kotlin.RequiresOptIn @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.FUNCTION}) public @interface ExperimentalBar {
                  }
                  @test.pkg.ExperimentalBar public final class FancyBar {
                    ctor public FancyBar();
                  }
                  public final class SimpleClass {
                    ctor public SimpleClass();
                    method public void methodUsingFancyBar();
                  }
                }
            """
        )
    }

    @Test
    fun `renamed via @JvmName`() {
        val api =
            if (isK2) {
                // NB: getInterpolated -> isInterpolated
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class ColorRamp {
                        ctor public ColorRamp(int[] colors, boolean interpolated);
                        method public int[] getColors();
                        method public int[] getOtherColors();
                        method public boolean isInitiallyEnabled();
                        method public boolean isInterpolated();
                        method public void updateOtherColors(int[]);
                        property public int[] colors;
                        property public boolean initiallyEnabled;
                        property public boolean interpolated;
                        property public int[] otherColors;
                      }
                    }
                """
            } else {
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class ColorRamp {
                        ctor public ColorRamp(int[] colors, boolean interpolated);
                        method public int[] getColors();
                        method public boolean getInterpolated();
                        method public int[] getOtherColors();
                        method public boolean isInitiallyEnabled();
                        method public void updateOtherColors(int[]);
                        property public int[] colors;
                        property public boolean initiallyEnabled;
                        property public boolean interpolated;
                        property public int[] otherColors;
                      }
                    }
                """
            }
        // Regression test from http://b/257444932: @get:JvmName on constructor property
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class ColorRamp(
                            val colors: IntArray,
                            @get:JvmName("isInterpolated")
                            val interpolated: Boolean,
                        ) {
                            @get:JvmName("isInitiallyEnabled")
                            val initiallyEnabled: Boolean = false

                            @set:JvmName("updateOtherColors")
                            var otherColors: IntArray = arrayOf()
                        }
                    """
                    )
                ),
            format = FileFormat.V4,
            api = api,
        )
    }

    @Test
    fun `Annotation on parameters of data class synthetic copy`() {
        // https://youtrack.jetbrains.com/issue/KT-57003
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        annotation class MyAnnotation
                        // No use-site target specified, so the annotation applies to the parameter.
                        data class Foo(@MyAnnotation val p1: Int, val p2: String)
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(@test.pkg.MyAnnotation int p1, String p2);
                    method public int component1();
                    method public String component2();
                    method public test.pkg.Foo copy(optional @test.pkg.MyAnnotation int p1, optional String p2);
                    method public int getP1();
                    method public String getP2();
                    property public int p1;
                    property public String p2;
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                  }
                }
            """
        )
    }

    @Test
    fun `declarations with value class in its signature`() {
        // https://youtrack.jetbrains.com/issue/KT-57546
        // https://youtrack.jetbrains.com/issue/KT-57577
        // https://youtrack.jetbrains.com/issue/KT-72078
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        @kotlin.jvm.JvmInline
                        value class AnchorType internal constructor(internal val ratio: Float) {
                            companion object {
                                val Start = AnchorType(0f)
                                val Center = AnchorType(0.5f)
                                val End = AnchorType(1f)
                            }
                        }
                        class User(
                          val p : AnchorType,
                          var q : AnchorType,
                        ) {
                          fun foo() = p
                          fun bar(): () -> AnchorType = { foo() }
                        }

                        class Alignment(val horizontal: Horizontal, val vertical: Vertical) {
                          @kotlin.jvm.JvmInline
                          value class Horizontal private constructor(private val value: Int) {
                            companion object {
                              val Start: Horizontal = Horizontal(0)
                              val CenterHorizontally: Horizontal = Horizontal(1)
                              val End: Horizontal = Horizontal(2)
                            }
                          }

                          @kotlin.jvm.JvmInline
                          value class Vertical private constructor(private val value: Int) {
                            companion object {
                              val Top: Vertical = Vertical(0)
                              val CenterVertically: Vertical = Vertical(1)
                              val Bottom: Vertical = Vertical(2)
                            }
                          }

                          companion object {
                            val TopStart: Alignment = Alignment(Horizontal.Start, Vertical.Top)
                            val Top: Vertical = Vertical.Top
                            val Start: Horizontal = Horizontal.Start
                          }
                        }
                    """
                    )
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/5V6ZVRc25Y1BLfg7u5QuENwd3d3d4dghbsEJ7iT4C7B3d3d" +
                        "JbhDJ+91f33v7df3e33O2D9qjBrznKqx59xzzbUUpMHA0UCgoaFBQECIQf54" +
                        "oYGAg8iKqgjSS8qJMcoKykmKiSqrMMiKvQ2DgFzLjo7ISNMzTCFI09OMj07U" +
                        "KgFmWTb3nBikZOkkZadcy+qUfkrRO9JIjY7Sqv0cZxwaGt3Z2977AKIgDQX9" +
                        "DZX6G9evB3D8Wgr/6+Oxfy0XU2cXRgdrc0ZBO2MLeycVTwdTBmMbQ2dnX9U1" +
                        "ZVxVtPe1sfuaYPK6pMn5hoAG4kUKg3qtQ6iCaIQPlZ81RCqlaxCkE9Zc3FG1" +
                        "Bcw8yif4OY0JfS8LdzTKuxaxoS7HOocced3dR5iJd2T2s70n3sYXLjK9r+87" +
                        "O99BN1E6zA5rVUfG2cuST5P0tDhGCwcQ2D70E9lT0ZNrK99I3Y0vA7VqSKEB" +
                        "Liv9ebZWXnuBavazWDFFcKS1/NOv7D8cKnQ51matTOeiiLqRsmddxjr6kSRi" +
                        "6aTDBNGQRTGFv+UIL6vYjDXhiHJsJYsJz43AY06HGuN/vAhJj5sES/M+N+Wc" +
                        "op4I6uL/yNsd8PgOdee8yQQ8pKXAiingcSAiDheM4RtHUhmViglAxxKS00WR" +
                        "26rHAs9MQRLSKNCNMLhrMUMursX3Esp0+0Y777VOVU5wL1hWOji5E8owCx/y" +
                        "lFR0qmdIbuKg1wstht8SiTJ+Wfs5El73uKdIqZbX/fWo28Z7glKM3wgp4yn9" +
                        "clA4PjYw2LHth6+OUVhSlJcwk2oZnS6Vw7v9FnPsPbslvLcuGzZK6KuS8tqm" +
                        "TzB3SHJcD1as4xi9i6ZIXGyYDrPLCHsG+QoA8eenUbFXMiiPRp74wdAzyHsp" +
                        "j3mjuI+AuGuprxEh/Vj6/dSuhSpK9NZNecuRDUbLPYWQPzIrgz0bQ90PMR6/" +
                        "t5WtlB1tdp/rdQfC6RTc+G7St59+/VAX//1UiL+Am0Ka7cIv81ShxyEbtcoa" +
                        "SABayJ2SXYnDjm3CoXVb0b9zJEXvrez7ldXabZ3SWo6pnLX7PORZo5eG3zDs" +
                        "jMeYcy6cSQDH0Z0w5xIPHWZm0BlD+sfJTQwAfgzHjLw1d9m6KO9ITW3KtWff" +
                        "We7Zsyfbft5q9vItKG287lg066PPd29931Rl28KKRdijYC/aLt9r8n5M2J6y" +
                        "Av+CS1Q2aSrlxAZ3DpY8AFFYYFsr9bR8HGkeEkLcKQk2GfbtuCNDvre8Hfyp" +
                        "XfY5LqbTKexJxnn0uR1Fdw2AaveDt0Bw0NRXvHTspB6XmCjxvUMDh897i2ed" +
                        "Hc6d/SaOYimth+Ui3PuHLXucR14SlBcnXnu9Qqu3Xvb2LBLcATUrz0AY7w1m" +
                        "pasby7aSEruc00jVjK0HIeXG+hatWe30wsq5WeocmXRXv+LHaGYpKVtNJrYQ" +
                        "JGkfYnnWlMLjzOmf43y1Ihc+tR1F6dliNMctVvvWEaaluzI6rOlmkvQWL9rT" +
                        "ka0+VDT9JXOCJHwi8jhjIORrBcqpu2AJMG8p8Xi79z/i4bJ13EwQx7QHBRsP" +
                        "XdJ8FNckOkRcTHGVSDpCRKzgxPfi6eFttnon5MoFilWdAjiPmgxf+G4zM3d6" +
                        "PjGnP6KbuwFm7VD7AONXP7Zis8xZdpeClXg8XjUzBC0EJCAxuaGGGlYeBA8L" +
                        "r2wwSrj5synF34N/ouV21U7pQmt7XKtZeUBd+EywCZ5LdJFqkSIZvX9Vd2i7" +
                        "S0yga5d5OIelyMSjcJy7nuD5ociUPXP4sepZsnmfiZG6w3nUNEDAM/uRbUJn" +
                        "1MaYhuUGd5zX3cnztB41qe1CevWenJu+6CBQ32diwTKWgPhWGZcpkAfJCYXD" +
                        "cUQ4h3LiRfuUPDh5ln3d1l4TvhE/X+9tY3qdWc/WOynuiy6K6KgHpLKe7+Us" +
                        "UvUrWvxmLDSkT4cwUgEu1FHEDl/sGaeZZ3jSzarfC6ocEdED3HnLlm8euFDH" +
                        "BkzzLMyjIOVuu7BEeik6yYqoHE8ieIGekWuI7c4MpsbJsuAjxDmFijiDZEzT" +
                        "vc+GfKZNS8kouCdewSXsJlyxb1S2d8C5HYo+t6ONj2iNVCvskGvNpBoylVF+" +
                        "mBUy26wlXDs0WLQqAA7RsGo7LKQWgw6uONN1WWi9u6C204hY/CI4JMqZ13yr" +
                        "LaSgFlETWrvpnfC3iBMsYen5QfySaNi/E3HSfy3iZML2tg6Gdpb2dv+U8+Qk" +
                        "WXlUQayB99o1u5XCT9SMH2DD1CCSOj7bw/SDR5LJfkLp0uNMNcQ2tGxdOZXB" +
                        "bUdG9IO2fwKV4ZTkxQrkEEi6TLOthowM/+L5+J6zwcLCM3Eam6PfCdoUKw6P" +
                        "KiWRXK5+5zyS7bwJtWJ/QqJ8Y+163tyBCWYsgX9FMaHa3JizssiHFhNB3G3A" +
                        "58inzmcaNjqakhjWXWNI0m9QBqbOLFP5ynKNsreGaSV9+WmsU0fThr08tken" +
                        "HD5IdxDLC3t61TE4Kc0ZsDObeCg+LUaoBA9WgBlSLDI5xLkoqKU2CN+QSgzk" +
                        "Z8729sYnmgOVQMz/Vpcnjg3LYo2ur6rL6L5YsaeTNbEztxrhMYarqnrDVxoI" +
                        "zH8xyIJCR6YbS7HFesqHN8uMeSzDaIFfeh8Ro+SFmg/Z9tZxnI7SYhFqHHJJ" +
                        "nETA0gi54ajK3rOsUzd4cPBiNcHNB9oWyMXakNrVUVxqvJz6mtzcWPwYrDWv" +
                        "bnK8G2JW6t/XoGG22x27oDy9CcMRq5G1sFmNElfGWLocORWxWPmyxEd9cpo1" +
                        "ZSy+Y05IeR3lBqh0b+q66aicx2eCdy8n4KbLvwqbbAeI9MAaSLwwUAPX5Wwa" +
                        "hLI1b+7Bhd/TT8Ixo5eroBse7ZKTbtIvflqlQVVlN/aZDF2LKFQ4UUOgf2/f" +
                        "4/ggXtGyjLti4zFcGV5Dqwurd8er7ygaKYV2CJR9XTpCwHrCn+SCZR058p48" +
                        "XsXsjbU0MAjQokWTpVPKsIEgAs14Ar0JjgDW2i7y/ug6gLO5QZx/GStqre7B" +
                        "1Cyn5ZmOxm8d7H8zO5oSZVo3OyjlWIbq8MKbz4UU0zLyZuJrAcJckdyAfbDE" +
                        "Oc7/xgaE2gr05C3wC7qRjMhaFiwIPiGX+eIAxmEZJgPT6OANJbP90cwtLuk7" +
                        "uRk5XThXeCuKZWmfN8o1CimKZni2/m8qwGpOH7d8AAG5Af87KqD+kQqqzqZO" +
                        "/7n1VWzlccWR/O5enO4evyOiQnPSS3ZZRmtzglmmg0tJhdFxkbCOwdFsC7uy" +
                        "hzo/+IjbL9jNyJcQPgksEyqwKeWrVCoRP1UK0AyfPhrxoN32Xa2Nj/Jfj46O" +
                        "n77eHjcKgOj02QKRDZ0NCRrS943toGiwghwNXOln6tuKHjVfu+yg0ZmAu2U7" +
                        "XEbRxIplagcN2EaFzbb4MCU42xJM34W9yrhZTaYVvibeJcfYyI8w9fahhrLs" +
                        "sm9ENHV/5bB19H+7zNqzW2gnuFLz7p+AEsuSdj14a3yxvLXepgmhLik3ye4U" +
                        "lTS98/UPy7ODgGcpbB5LYdQZYEFssXXu3GEL3vFerRBTCgnceFCfPCbacv12" +
                        "2YtIwMdXpz4/6O42g5i+H0B/HLAXF7nn2fHd61y7Y/G4iSG3f72sUaIUHVNV" +
                        "hmVXujgmzz37C6tOrynwyF9Ouo+uh/nWiNN3ANVtU7PKc2C9kbtqkQgT2QWn" +
                        "G/FcU7CckLRUUX49fzUiGnR7xA3aUOPBPwk+3x7vfKl7uTZYlL1wLXoqjulR" +
                        "agTTgkjL5L0/XiukoF0U+GVAHEI2TYW1j5N/+pBwuNSljiZxQFxaKmSNYIIS" +
                        "ofCrilFd/57XlbESE4q7qzBjwdUlryYNqftAVMXq87V6/yEXP4kCbzLLLJQy" +
                        "38EklbulcH5cqRBvcsFZGsEBJuWGeSixGLgk7rEMbyF2WuJFUlSKRNYLQNcw" +
                        "ac1UsxclsTA6h4t8dpdDnIXXhSldrcULbFNIelLB/sG7oY0oxtzeddmVx8Nk" +
                        "Tlfb6QFhZK3hg/hxPNPrpKVXyiE4mb7H5GXwTIO13sLWR9tviBTx53Jj16cG" +
                        "PfWcjZJzBE9ZXw3V8YeY0T3t3scQI1lJHI+TAtvaZnKTENSTydKJy54oLHkK" +
                        "hmL2hxnjGCb4BySaH/D6aycNQ9Yf48T8yJklhP1QWCsS9pQmEPKDP1ENAeiS" +
                        "TIhC3PodGGCNhx9z2RTfIEgFbocSc5lQUY75PolwRlyyZEXPjD+ljaNyEnJt" +
                        "kNWRTNU05bYA2ym9wvzoJWWA9VFGx52qcXws4R8YJoklD6zrgFt+A3343Edp" +
                        "xmj+/H3xuNuTpDHyQqM8GpEKFx8gfXscyaWsgjxMvWevbC4h1v0x1pUllc/T" +
                        "ntFLX89LFN0LVLrxkHsdaE8B+YxfXmS3ZwCnCBUzLdJ3zCEvBpjVOPwVhh1A" +
                        "iFSyPKV8Jm0SkAHmqvPuzUFnn95cR52dQSedLawdzoH4LayD1E3OCcXNh9Qd" +
                        "7oFZ69+UH5aLxa8BAwEZgfy3S5jflCczMnQiY/on8UOUdX+deWh8tfT0uhH0" +
                        "ORgqChXLGIYjoYEY1tDLiwVWJJOY2UVnCkA786HzhgAOv0iBLos8Cx9Ar7h/" +
                        "0qUcKKioTyqP+0X7wf66s5n/y8sTQhdvNjP8Q/d1ZZbYmtZ9cEaFAwcXKqSx" +
                        "Bj0b3kbK8LIdB32pzxaNnTb5esWsx7mNXW1hWYdLkjl6TPup0pH7gqnqmtNq" +
                        "fTt+/MgBltj7GmsUUc/iXnQPNFNwSkGCX3augCo3hy+DblFFJ3m7SQrxnTgz" +
                        "LCofMlYYM7hynwtXhJ0O01QIFgRrhGGJCyg+qxvZUURZnO0Ws0582Rlr7+xT" +
                        "bbPbip4JdME0BHDdeRAAxPZixz8ooIsAB8ro9EFHe8wGGAKSGWyv2Rttqo7n" +
                        "T4YbSp5YD75WsTrsVHAX8wKZxid1Hd2KTZwHzCQKYI/gHmO2MShkVnttgR2o" +
                        "fXqwOQtNE5bU+UsfJ1kMlkXqQMGsLSg/DemW8m1PlYwBogjPMyjwSQ1TxRqR" +
                        "BVYJ2WpOQx5fbakkcnAUMgqjvzQ5zerNWHo+OttkY0dCk2phU1g8jswVIewD" +
                        "5Gez84px+cPrt2fD2t8SlPfc0loT1G2qi6LVdGkuy+YqoO85UehpyFfRM7yz" +
                        "icKNJ1Tn3HhLj0X6UpRJHdVNdR0795Sqm4/rVsuoJKLxPGeUOmwiam7I6rXU" +
                        "hi89zcVMJHMYtiP9kj10BOjGcLLsRKhaPq3jqWK53JtQCORyoD5EPYR+7Is0" +
                        "CY1VOhTu3M9qA+UmS5x/a/pRkSLcOOojcgEqthFvv4m8pEBJcOISQTCH5xrB" +
                        "uINT9R4i0LWISbbxTihPy0dhrTR6OJr8KcYmLvsMjM71Jyjv0SpifLF3AM0F" +
                        "0vE55AfRVVoSVRGcNBIKx84wahn7z57f49Y+TfZEhWoiBhEA/UB/b3j6FNX+" +
                        "sl9nXODfnnFYf7J7Npbmdramdi7/3O9ZKra/S3a/cwriIbOAnzRb7RCwBUJf" +
                        "GcNC1IhtydkmYVnp8+bJKwwNayO9OaKewi8pHxBvnAXQvvdVs3wM2nhRjDn4" +
                        "5WKDZaBMu/nuM69XDzK9x5eeFzZ+V+sYKkAz8R05K/fiuHSWm0DNWRlK+H6k" +
                        "DbX72aTc9JDZnWhbevl5EOG1CrvjcT7gpAQFS7oxdFTUPlowUgqySLDgeo3N" +
                        "K9SIi0pQgCnaYJgosaBh2KllQ/6BqrBgkLmNCTS3Nre5xZiejDEwt/IrFxx6" +
                        "d3XpaneQuDY2B00l/e7YY6bxzJJ9DlrRPpadktkS/giTC0t9u7rMUVH+UpFV" +
                        "wbn1Rgl5j6Q2hfup0nr1qejc+tQp9gWkK7PpDUH1x/3yMExRzG7A2wYVtStb" +
                        "zgInYQGeeuUaunh91o8XBuHnQYuzVrFhAASfBcbeUSHDS2QfOfSk2A/0b4Hq" +
                        "pQGqEmxSg+Y4VwYLYX7s9g/tJx1Y/gBbdHdV5cZRgBg2hfibPMS3EYltX6BF" +
                        "SFRuY79p+Q8ztChScuZMg8PSgAp2gShc+Hopd0jnmAc1NA7HDMOHhNDuh880" +
                        "elXGnstoPmFq2DNNPZOqQaW8AYajSiVaMCnIB9OtmH26Pr0Al1JSw0kbDg9n" +
                        "BQI3CMv4Zd7zyKvGSFC/QUBc/amQlbYzu40qh6cUm+yIIv72Zin5iCYNNmS0" +
                        "tnJzT2R6vl1ZjBR7aRPmV/eAcHIajEmONuG7C5dcxdiCMUlgjtwdj0mpcELh" +
                        "w1CHjED1fQhauEvm6f2AnQTqqRxR0exEI9tCX6GcJcknZglmlyfpLLRv2nIe" +
                        "8wL+xpTPdQDmkV6sM3ix7kvoZl0H+ka82Mw3qr5dhd2bg3UYMaZvYaMW/FQb" +
                        "He6x0Gs1B7p8JJ1tDvswkUn48LfOZtl6jXMX7vWX+KFAw2YTbi/FkVRv7gUO" +
                        "eNsqLQuvxen5ov7J1DlZ21KV1clT1WTATbsZfd5IaQODy0aJiYxy7QxwuFks" +
                        "dA+93OSgPrTlJ/kLZzOcIkASH1HJ/DgOiRBF991Z3ViDaImOQF+dvalGmZfu" +
                        "AJC1Te64F0dL2ovl1H5SQJ9Awb2RyivGunSLczt0vhBGGCfZjckfDf2TvPKV" +
                        "AE22oGct/OfoWjg42VXd17lijCFYfapH2mP9eTIV3s/yi8EN6aE7XzjxzLef" +
                        "fyIWU8sr1jnUlD9j4UOdp0aSSk7oNH9qcgr47g9aLa1Q+eESvGXyDNWtFYvI" +
                        "I6xDnWElzQR6SX/oGd5p0xRh14B+9ycEmatlMPNlnPyR1fbFnTbZwiJTfwzg" +
                        "EWN6mbho3WAfibDzBxizv1MwvzxX9+cDnu6fgiEtAWUoPWw6fYAqVF8Q/WZy" +
                        "mKlOkZ7kr40q3SwJM3WoT7rTOYmZxVmT+k2+Gj0Ldkrw2NzPFvgB8PxjRBm5" +
                        "hlp59ATmP8FzKoMc6YxN8RHwxvDK8ZgHlBlm8cR5xOHEacWj8qONM3ewB679" +
                        "/5EkjqvDeCr8UqQm6H+/CP0vVSKTsHey9LK3czG0+c8D+b8zRbMA2WUlaJUn" +
                        "QWkQSxDt8sLZ6br6yAA1i2q0zhQT3BQbt5k1WRvf44e8Cf6HzcgYxg4XPGt0" +
                        "LJ4doo5HDq5fOsUQLImVObTqfLG08fiU5Wz/9nZ//8uLdykDUav2m5q1CjMz" +
                        "mG5qNOdk3H82mJ7kaBeV4zABID11bUu4hNlGmnnKT7Smool7J7e0IqsqM/aY" +
                        "2rVYsGJMk7eEUI/8M4w418ta9RaiE8wGkIzD9KZk19qHkYY/MkpKfEJDFk8g" +
                        "nhslXlGIbmHHF4famtOSZZGGN1660zjbClJQRmkhcp6gBr4FXa0i8nYHiHMB" +
                        "/Dm/Z4DFsvS6pxlwpvN9BoNpH3SwIRTWqB/ZgZcxDPNwlcTqsoaB5qQmDiyR" +
                        "aJ0Bd15wCTX86mDhHrYWwzanOaJNTn8olFpGulFqhhJxZGUq557CvCYqjfUj" +
                        "LswwymuW9h/Z4jW85JmjLltml6G7GnXdnb/VA3GQBod6JSWBVDdZf9Mtrkry" +
                        "XpUlu3DLWsTyQet+j1OcMsxO6qbf+h2cJgryfg8RJv2M45nXVP0pUeWDnQ1w" +
                        "pi039LqX0jqCp0NslgOrwJdciYNEU3SLHKYepkNG6c6quoRr3EDTGVN3zg0q" +
                        "ki3QUxum8RWIPAYmq5EtZOXGUnQ5WyvZeEKO5gPlrKI580O2dlcz2IJ0qwrR" +
                        "M4emXYk1UJrZxSI5yLNcS1l2jdm4aQAQT69C90XG+STaSu5AM7UsvjWXT+kE" +
                        "mGHPzF28jJ5u8f6GvHd/CSpsNIHd8/59JUqal3GHeMJbtLtOQIGQKZNaw42r" +
                        "saq5vKEsQ07ulx6q6jI7OF2n8OhzPJC5X8i7NyiqynBb3BDXPxvKOc8lHJcV" +
                        "oJZFI7UbRDHCdHzG0SZ+Rc3KKSkWCvd6nLmrncmbJA0LbHuVKcghGhAlJiBJ" +
                        "I8MmXxqauMxi/HZrI8CTlo7Wv2Sa0pIdAXdUQmcSfDk5jjjlwEsnbsbilDRe" +
                        "PdSENxHsvcjZ+rrA8bYQTDc+7JSQLznAmyWCUbMq2DLOp/uFjtHlSY/w1AeZ" +
                        "IuuT5JlPX3DWN2xqz3lWHxXplqxLrDKXo/S6mbUcpkjrFN8LS1kefUfsJ4zj" +
                        "onJ1tj6bw5gOKwsW2bwjoUvviYtCVZVo8rl5VaHVHTjPvCEGtf1ZLYdzGqTb" +
                        "69vjMjMoHEkm2bFx3LIdi10ZnbpjN8XWwmv7dsHVwoaS1K8w0MrCJDfg0xYK" +
                        "prFHaOSPMEOs/s8SZg/JVybEce58S6Jg2JeT7FkM8YwkUO3fkPTXg5POVz9x" +
                        "1wYYX/2E9GKffK/lgPxYmYDYCzOBsHNhv7mLR4iWcBmv72gFZ65lu43Vq8mv" +
                        "8iIIbzMOzh4/iqHnv26EtATHHFgdFLSrZZsueNgOZ3Ou4OrjwpLvL/SAET95" +
                        "PWULjdsEyDp6jyHsWY0E+TKPFdwrcHij65aYoLxf/MMd87LoSxd5Gzrnge1m" +
                        "Cbi8hQvclwzsx/1wItKO69+hYlrA41yXDt5Sb3J8x4bDccgXab5DcgeXHEbH" +
                        "iS+Ht1+KQgQgA74lgnIIFSWj/8iBbP6y3Y76NTQM9Y3+ErbbyQyVpfJVarPF" +
                        "PGnbX7DydXuu4X5kogrTc9WilDHt6Jmg+mcsdGi6iXjYqfVzs9S22SdXlEYP" +
                        "36TLVu8fz+1Unx4wR5+qM+jwiXe9oYgPEIXWuaCbJ4EFllpkQN/dKA7hrlL1" +
                        "ps5R9stNcn54262dL6kI3kb82+EhmN523ENkkoWFjpkSR7nKPa3pmSioGEfn" +
                        "M3Zpu+YElGy8xKVT58oJNJe1LYFxgXmBCUJP4nWimj9wbtABUCclFURnmxVg" +
                        "PT8+N8rxYDk5DK3ouwDkEEOCkKEO8G68hsOCSoL23v8h5T46JYteECAgpX+b" +
                        "J9L//6T8fySLidLyaIJIA+9rzyYaBprrD10DZZ+76cIeulI3IwwRrPLMGPFk" +
                        "WOoApzaIaefk2+EeP4U9Zmox5XEQgZtjiKYVBHHkvFF6Za1vqp3H6rcT+hsd" +
                        "YKnd+EBaMaudqgW11aUufUXXUhlH/qgXjlxmEqqUnqcWBb8SZgP4R7Zq4tVP" +
                        "Jej3R9PpNzKQMMAbXf3HfQJhI4+CdGFYSyhJE6wEs7kFeFK496Z++MrvJGPO" +
                        "pCQs7LpZOd51WuHlCXzXCzjYdUxowQeSlZH+Umjtt3jccc6TMbyqRO0yqVHp" +
                        "Ot+XdMxLwgllI61dz+aPzSyKC7jqV4OvZVNvOFvtOVpzDI08cjHSu9TPl8XW" +
                        "bm1tyT/cB/iQ4wRQh0pllGylAbzo8tKjzMTKPja1d1XlLcB3VAwoAi/fy/sn" +
                        "H6E86KI1MMTi+k9wYY5BzWQj2QwM2Kb1n+dpp2w8A7de79Qae56be65XtD9O" +
                        "XOlSZgydt0nf2NvLXqG05gxdZcVaZh9MQF6FC+pJvT0MNzQOAhLwhViChmxo" +
                        "cooc7gocpPHc5GAb92d47D3LE4fPfTi1jeqCrt1CE0fPWx1u2g+53tddXSBG" +
                        "2THbMYRpzjFpXNCGukwd0vGER/w6mevGaOzZovRsqkYucdAlUnpMhyarN2kf" +
                        "CxT1E6bgqtHilDSg+ZBVRHe1/eVHVZMSFLmQtKSfKMevvyN7XuXaG4NN4Aw/" +
                        "uGre1ssP1UqFiGFpqRgHppNa6kd14DRuRFcZRPAu8OeqincSPadR4HEMFhlX" +
                        "Q82YOLfCWAQIG1NqGfd173g7XNFyC+AbeSN+H/JtcMBbcU4940k544vB5xy9" +
                        "IzQ9IiwGNA3ETA3ef/Q5dl7tnrl0ewwQXl6yzMzV+MAxRKtQh0/e0hfgwS65" +
                        "U+mxddIku6M8zCs9OkmCgp5ZvukDgekQX0Sl5gDzBfMHa5WBBzIXgrixCGkl" +
                        "U5mjav7XSg9ZY5l8dWOd/FuO35wBv2LXWPpVlNlC/B1niP81Z9RMnVwsjf/L" +
                        "/Pj+xfyglISkpIBMgzSoSZeGzC4bfEWnStleQGkwHDLjWFvrvavcieR/noTS" +
                        "d/H5Hw1V4dQ4QwrniXZz/4MT9/H2x4NOgTdwB2x5bC/8ObeaOSdqnnxr8vw8" +
                        "nPHNCCsuLcUpUgP4qxQl9ZoWo6Qa94XG2TxUwc+5PUxf8KRZpIy1CstYMod3" +
                        "Q+FkC+5tI7Zcyu0aio5wy9AFg4l9dFlYDyCFiVjaSIghFUXQHIFVYpBpLXkL" +
                        "s0dU3NCVKuVl1T8AKVw4RxYOApNJwQyDq/aBud90U0IDKDl/kkHtx0C5OW9a" +
                        "Ai2CoICJaezwEhLEY9Xt8IJ9NNEs33mFByuluzXbkIM++8EQB4suZ5Gm1pYu" +
                        "wZvDDrgAU88WVU7HMptzn/ChlWx3uaZkiO05rcKVs3J5cS1gSLFQmKIUv9y1" +
                        "BUe+0G2iCOv46ii7rLgvZg1hankLsL3ECVZgn+yjYpIbohivPtGXDbmwq0YS" +
                        "e85GpB807wdNj5cGO8o4CFx4hVSEQV8JxkUWGfKoVR6mpuSo1SFn5OY0XnOm" +
                        "PMjIOjZyhN9xB7mUfDoWoUqcuA4MPgbXp6DOVqIra+DTUMoyreXWqECRrIt3" +
                        "GLBGQ/YpghlSBMxZiczktHg0q9LmbmJPsGRa4Eykxxir6SR+b/ziMAHmJ6Kq" +
                        "x3j8ttfkIBMSxFWrpAo/xkqiYZY6iDZcJAxcqvQiL7/khRedWFcipGZbG2Jr" +
                        "zlSvYJatcT8JO31+SCV224wyPOTUy1AM31Ll3eX6Jpl6B3mkDLyGmKExbVQx" +
                        "X1Ni7RmnARpyRwhDurn9cMGw4s7ZPO8ZGp+j9Qz6SOMrwco/Wh5Tk8sJexQs" +
                        "xffJHmNHOorxa0qK5LvIDuVx0XdB3RfPiyUuxUNkEcF1b4pp+bj8323UqET4" +
                        "xNPdg8O3BQ7vu+xbSooQR7docjNURWLslJA6AMHiOxNeFOTepI7WxxkuZuzk" +
                        "/CMDRFxz9ze/c9/ooe1MugGudJfROUkT4hH8ghPeSdehK6Pig9sfb+qC0zBJ" +
                        "s28cMovvKfEJ5rLG4vVksVf4giGQuYLcaw/Y+U2WWiPy5ceFNcLy90ZjDnUR" +
                        "6RzH6ZWVJ8cyiWYtYi8N1ysWCZcRRpawxCmPQtkpeIUa2eplb83ln3oZZ2lO" +
                        "yvasC9oMS9Tk1G20yc23EzRrI45to/2lHx3VY0T6wG8FJM6gp2rFgwRd+YqU" +
                        "U3cPyBJf2knT9heGkiH0151NMRGMB0W1XVqkOsJwCvRIW1rAqJeao028cIt3" +
                        "t2xvbPIvMN2hbbTIEcNYqnD7N3Q2hkT5KcjySTsNDTAPtG3zsHoh+JVesG9s" +
                        "x5/Zc28Y1n2mjJC4oX95nzvBmkr2MaKb4Ud291Ju/qajqU4in1CFzb5DgmBb" +
                        "nWy5e9+JWMFz0c/C1uBCp/ouXgytkkPU19ObrbQeK7KbNOuXWdfsDipRlKwt" +
                        "kdZjUbzXqTnIG837NV05UhsvQHuGmNPNJ4mLCyyPo32la28Y2PUbcoIHlLW3" +
                        "6bqfxV33cxfOpAXamshtlnHKaBd8hHwimT/tqxeQ1GZ4Ui3P1OFrqxQD3y/r" +
                        "7iLVvev09dyodx3KCzs8Wu6hRAIFiLEsEvBJauwJ9mli7n3LdprGTvKjBDih" +
                        "1w/EQAM7jcYj10xKY+MIG4QZ6oTXbUN1eAN/WR8SIN9uFI9wV5f1qr4pj8dP" +
                        "2Bxwt8sl2lEI3Wokp77tETCOLBMUyuPE2XQ0yEMV928Uxb2fJVue67TJHwtH" +
                        "JNHlJcrqngMLhF4qvhHHiTPFRvMeEU5hFPKy51YGLC0KeSJI4mLaHLKPt5zU" +
                        "RRPjqq+9ouPu3CgsYlU7TokNqwJtgm0r3hB/y7iWT0m2xy8JT/tb60P79zL+" +
                        "P43PxO94eaCzlv6sBo0KIp7wSjryRWExMIyqH9xKJh2NScJryyDFlE66sdXV" +
                        "XGpXjPdB/LfzkUP57XwEhh/MtEuoBfK+pN13XPiarY+2Pd4+PyOCwHl0Axia" +
                        "48wKVVjVdYoPk5spZyAIJAYaVl3mGdXpJi7bObGEnSaWG2UWriL2n+4IGk3x" +
                        "kIGLygPOuyzAwV0V4gCMXngRZPOwYV1xgMzuu2WoKTCG5EuGn6kZ9fu5qqMj" +
                        "uQ7TAMADgVCcZ850iR2TRLdnxo08QI4M3oVIkrVUU8jDob8JKhofiGPhrNQk" +
                        "526bRQBkkNK8LbwZQWhpFVwQInau7b9hGpXdl+5wwZ9YXWHMiNHaqNW55vK3" +
                        "VtVNu+UjDiqjfzUYg5Xi1cimwZqcnXMtPhP7KcsVxTz6tmGlco39vb+w3lgy" +
                        "krWyjTC30dzKQhUMSyP85j7mitnkbJTJh5vrlKmOf2wygTIUWGTNJh57UkxI" +
                        "6ksZeq0FrOx3PbuRlpG2wn0dGvycEv7Ga0YZygpIQBDaAVNsJ8fO/+6n2sQG" +
                        "j6sespJQPYOolTGVl+NHMA4oM5BR5ZxQWaterpxh0VswdrEQs02y8TM4ZrTC" +
                        "BM6Ksn9MFBSQNrXgs1cG3qFPrN8GjQXB5tBLWl1XdPZgc1/HDOirjazwLtY1" +
                        "6CopeeZsBqJzDn+LDM88uCQ60RJyeMLrt+7EWXwJyTd6xVWV5zbVcwgWHqLN" +
                        "PpCWni764DyLqXircdPJejtweAO3m3Z8m8T9ivCEjI1DMI0F12dIN51aQv4V" +
                        "HXJM1wIOHWJifuuQqaWDvZrUv3rKmd0V2zs/Db896aw96szebICh5iZcG6Bg" +
                        "bm4xfwxFwAvbqg7azlbdkVZjZxhmhMxLwig69dHkEONZ2Dp7v9BhB8pD5prb" +
                        "9CMqS02IxMd8rqg9B1z3QRHuUMfVtQ+oqV27GCgfgF1Jn2QMtDazP31T2PYN" +
                        "wA5I6qr4tPKP8YNCIbTGlV/Wx+BvrQ/Jv+bMX6miMmGPK4r2Zna6mFGfWJHI" +
                        "uiueoXSMURxXgSpFVYC2+kmVlKNyeVBoGFSWPai2ms/arR/ZoYbzpzuI+ccb" +
                        "kT5Qza15AdhLt/YfxNUkjmkcF96/7rFxjueni9/GBxMRuwqnNHrFkn1VasS+" +
                        "pEGlqAm/jkfoMWA4kIlTfFujiT4X53zmlfQojU4JPu2542oMDKhiWZg9gMXM" +
                        "Pj3IXDCW+w07xfoL+rrRIFvBEgI3YxQ0Qo+OpSl4wLTdfOD3LQLJWkJYfUt1" +
                        "KnQswxKgm/uH2hnbeirrJxKOSEkgbfd1wXwfS3uEHiUilCeuMhVA5qNQ+b3V" +
                        "Bq+1jdr2tcrR5DtxfyZtajSnMxoZa1V9i6b/yyNmaJIA3yihmyYWAAN7Xrre" +
                        "zECrwyk2ZqXqWxR8skVzztA30UbThiMAxakXBXRO2HfqnsrkFgQC37G0PA54" +
                        "v7mYfHjr9284wltJP0VwHImiiKIaY5ULPmDQ70x9gJwne9EWuBqu49Ewfyjd" +
                        "oF32yV5ZPbq3sbsMMC5E9T52PtkdmPXpbPwxcmo4v03ZadLUk8PIWSqqJ/d+" +
                        "G3HrXQcItKVJ1pXRNpYDdmOEDIiH2MJiZuKFXAdruXWKbwazeF6BTSwZzw9N" +
                        "jJiK+dqLONt9t3iqFpFZELe8cER62oAaDpNgjKqiE8UccbBVsdMF9t9vy1iX" +
                        "TC1NweK/q8+pnE33a66afg/l75kmH6t9+Lyc4wWPir6HD7l17o7fPF/gOL3y" +
                        "I8AktexR6txl5Zl5Ff2ZXqjIGhT5mOSGVAD0S7Z78uVKTVmM7xmqCjY/GdBH" +
                        "lRrbrjF4PkHnWXQSge9xMDYVDqbWLyDUFcWSW+ljuz/BK9ghSsOgMym16GHW" +
                        "hmF7AcKArxYLkwcc4Uw6IZkBHvvSB6fgn6ltKXEz3wCvK1znZyyrLt2+cSKU" +
                        "29wsqbJATuWDaP2JOU9vkawP++KOX8adpTGbP1oiqqOe02hm470tjzxoigY3" +
                        "g6ayRrqNNMlVo/ET28fTFfv3ed/MeOScUix5mwiQPVvydomXxL0mYNmj3PcT" +
                        "KKm2qfaN0UQ0iWqvVk1XS3kb4NA21YZSI2jv4EdH9bfilONc49TiWONUiWka" +
                        "AxkUjIVJi+PfoX6Tcruhrk0UDAQE7W+7ori/1v+bK7U1tLRjsLZ3sbG007e1" +
                        "N3G1MTU2MDAw+7XAjeQgqRSMJo1A/pEP3JK0taP+Z4tJQRr0AxrIf6P/caD0" +
                        "99Tqn6//bYb1ryh/nGjC/hOCP8jfjKL+FeaP0yCkf4IRgfy3hqH+CvjHXjPq" +
                        "nwDlYf7lSMlfAf7Yu/vzDxNC+JsG9V9h/hi2Y/0JhgXpf2/7/RXljznPn/+e" +
                        "IbR/K6b/K+Afi2D6PwEm4fwfw6K/Qv/RmP15Dw/i/xs19V/h/nhm0f4JLpb0" +
                        "/+Tt/gr8R96R/Am4h+LfOQD/ivdHruH+CU+D5m+5qyANAfn7a/C/7rRfL1RH" +
                        "8/vTfwDsaV/bYy4AAA=="
                ),
            api =
                """
                package test.pkg {
                  public final class Alignment {
                    ctor @BytecodeOnly public Alignment(int, int, kotlin.jvm.internal.DefaultConstructorMarker!);
                    ctor @KotlinOnly public Alignment(test.pkg.Alignment.Horizontal horizontal, test.pkg.Alignment.Vertical vertical);
                    method @BytecodeOnly public int getHorizontal-6VHEJhI();
                    method @BytecodeOnly public int getVertical-8LqPg6A();
                    property public test.pkg.Alignment.Horizontal horizontal;
                    property public test.pkg.Alignment.Vertical vertical;
                    field public static final test.pkg.Alignment.Companion Companion;
                  }
                  public static final class Alignment.Companion {
                    method @BytecodeOnly public int getStart-6VHEJhI();
                    method @BytecodeOnly public int getTop-8LqPg6A();
                    method public test.pkg.Alignment getTopStart();
                    property public test.pkg.Alignment.Horizontal Start;
                    property public test.pkg.Alignment.Vertical Top;
                    property public test.pkg.Alignment TopStart;
                  }
                  @kotlin.jvm.JvmInline public static final value class Alignment.Horizontal {
                    method @BytecodeOnly public static test.pkg.Alignment.Horizontal! box-impl(int);
                    method @BytecodeOnly public int unbox-impl();
                    field public static final test.pkg.Alignment.Horizontal.Companion Companion;
                  }
                  public static final class Alignment.Horizontal.Companion {
                    method @BytecodeOnly public int getCenterHorizontally-6VHEJhI();
                    method @BytecodeOnly public int getEnd-6VHEJhI();
                    method @BytecodeOnly public int getStart-6VHEJhI();
                    property public test.pkg.Alignment.Horizontal CenterHorizontally;
                    property public test.pkg.Alignment.Horizontal End;
                    property public test.pkg.Alignment.Horizontal Start;
                  }
                  @kotlin.jvm.JvmInline public static final value class Alignment.Vertical {
                    method @BytecodeOnly public static test.pkg.Alignment.Vertical! box-impl(int);
                    method @BytecodeOnly public int unbox-impl();
                    field public static final test.pkg.Alignment.Vertical.Companion Companion;
                  }
                  public static final class Alignment.Vertical.Companion {
                    method @BytecodeOnly public int getBottom-8LqPg6A();
                    method @BytecodeOnly public int getCenterVertically-8LqPg6A();
                    method @BytecodeOnly public int getTop-8LqPg6A();
                    property public test.pkg.Alignment.Vertical Bottom;
                    property public test.pkg.Alignment.Vertical CenterVertically;
                    property public test.pkg.Alignment.Vertical Top;
                  }
                  @kotlin.jvm.JvmInline public final value class AnchorType {
                    method @BytecodeOnly public static test.pkg.AnchorType! box-impl(float);
                    method @BytecodeOnly public float unbox-impl();
                    field public static final test.pkg.AnchorType.Companion Companion;
                  }
                  public static final class AnchorType.Companion {
                    method @BytecodeOnly public float getCenter-UD3vvl8();
                    method @BytecodeOnly public float getEnd-UD3vvl8();
                    method @BytecodeOnly public float getStart-UD3vvl8();
                    property public test.pkg.AnchorType Center;
                    property public test.pkg.AnchorType End;
                    property public test.pkg.AnchorType Start;
                  }
                  public final class User {
                    ctor @BytecodeOnly public User(float, float, kotlin.jvm.internal.DefaultConstructorMarker!);
                    ctor @KotlinOnly public User(test.pkg.AnchorType p, test.pkg.AnchorType q);
                    method public kotlin.jvm.functions.Function0<test.pkg.AnchorType> bar();
                    method public float foo();
                    method @BytecodeOnly public float foo-UD3vvl8();
                    method @BytecodeOnly public float getP-UD3vvl8();
                    method @BytecodeOnly public float getQ-UD3vvl8();
                    method @BytecodeOnly public void setQ-YPzsyFw(float);
                    property public test.pkg.AnchorType p;
                    property public test.pkg.AnchorType q;
                  }
                }
        """
        )
    }

    @FilterByProvider("psi", "k2", action = EXCLUDE)
    @Test
    fun `internal setter with delegation`() {
        // https://youtrack.jetbrains.com/issue/KT-70458
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Test {
                          var prop = "zzz"
                            internal set
                          var lazyProp by lazy { setOf("zzz") }
                            internal set
                        }
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Test {
                    ctor public Test();
                    method public java.util.Set<java.lang.String> getLazyProp();
                    method public String getProp();
                    property public java.util.Set<java.lang.String> lazyProp;
                    property public String prop;
                  }
                }
                """
        )
    }

    @Test
    fun `non-last vararg type`() {
        // https://youtrack.jetbrains.com/issue/KT-57547
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        fun foo(vararg vs: String, b: Boolean = true) {
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class TestKt {
                    method public static void foo(String[] vs, optional boolean b);
                  }
                }
            """
        )
    }

    @Test
    fun `implements Comparator`() {
        // https://youtrack.jetbrains.com/issue/KT-57548
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Foo(val x : Int)
                        class FooComparator : Comparator<Foo> {
                          override fun compare(firstFoo: Foo, secondFoo: Foo): Int =
                            firstFoo.x - secondFoo.x
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(int x);
                    method public int getX();
                    property public int x;
                  }
                  public final class FooComparator implements java.util.Comparator<test.pkg.Foo> {
                    ctor public FooComparator();
                    method public int compare(test.pkg.Foo firstFoo, test.pkg.Foo secondFoo);
                  }
                }
            """
        )
    }

    @Test
    fun `constant in file-level annotation`() {
        // https://youtrack.jetbrains.com/issue/KT-57550
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        @file:RequiresApi(31)
                        package test.pkg
                        import androidx.annotation.RequiresApi

                        @RequiresApi(31)
                        fun foo(p: Int) {}
                    """
                    ),
                    requiresApiSource,
                ),
            api =
                """
                package test.pkg {
                  @RequiresApi(31) public final class TestKt {
                    method @RequiresApi(31) public static void foo(int p);
                  }
                }
            """
        )
    }

    @Test
    fun `final modifier in enum members`() {
        // https://youtrack.jetbrains.com/issue/KT-57567
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        enum class Event {
                          ON_CREATE, ON_START, ON_STOP, ON_DESTROY;
                          companion object {
                            @JvmStatic
                            fun upTo(state: State): Event? {
                              return when(state) {
                                State.ENQUEUED -> ON_CREATE
                                State.RUNNING -> ON_START
                                State.BLOCKED -> ON_STOP
                                else -> null
                              }
                            }
                          }
                        }
                        enum class State {
                          ENQUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED;
                          val isFinished: Boolean
                            get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
                          fun isAtLeast(state: State): Boolean {
                            return compareTo(state) >= 0
                          }
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public enum Event {
                    method public static test.pkg.Event? upTo(test.pkg.State state);
                    enum_constant public static final test.pkg.Event ON_CREATE;
                    enum_constant public static final test.pkg.Event ON_DESTROY;
                    enum_constant public static final test.pkg.Event ON_START;
                    enum_constant public static final test.pkg.Event ON_STOP;
                    field public static final test.pkg.Event.Companion Companion;
                  }
                  public static final class Event.Companion {
                    method public test.pkg.Event? upTo(test.pkg.State state);
                  }
                  public enum State {
                    method public boolean isAtLeast(test.pkg.State state);
                    method public boolean isFinished();
                    property public boolean isFinished;
                    enum_constant public static final test.pkg.State BLOCKED;
                    enum_constant public static final test.pkg.State CANCELLED;
                    enum_constant public static final test.pkg.State ENQUEUED;
                    enum_constant public static final test.pkg.State FAILED;
                    enum_constant public static final test.pkg.State RUNNING;
                    enum_constant public static final test.pkg.State SUCCEEDED;
                  }
                }
            """
        )
    }

    @Test
    fun `lateinit var as mutable bare field`() {
        // https://youtrack.jetbrains.com/issue/KT-57569
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Bar
                        class Foo {
                          lateinit var bars: List<Bar>
                            private set
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Bar {
                    ctor public Bar();
                  }
                  public final class Foo {
                    ctor public Foo();
                    method public java.util.List<test.pkg.Bar> getBars();
                    property public java.util.List<test.pkg.Bar> bars;
                  }
                }
            """
        )
    }

    @Test
    fun `Upper bound wildcards -- enum members`() {
        // https://youtrack.jetbrains.com/issue/KT-57578
        val upperBound = "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        enum class PowerCategoryDisplayLevel {
                          BREAKDOWN, TOTAL
                        }

                        enum class PowerCategory {
                          CPU, MEMORY
                        }

                        class PowerMetric {
                          companion object {
                            @JvmStatic
                            fun Battery(): Type.Battery {
                              return Type.Battery()
                            }

                            @JvmStatic
                            fun Energy(
                              categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ): Type.Energy {
                              return Type.Energy(categories)
                            }

                            @JvmStatic
                            fun Power(
                              categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ): Type.Power {
                              return Type.Power(categories)
                            }
                          }
                          sealed class Type(var categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()) {
                            class Power(
                              powerCategories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ) : Type(powerCategories)

                            class Energy(
                              energyCategories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ) : Type(energyCategories)

                            class Battery : Type()
                          }
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public enum PowerCategory {
                    enum_constant public static final test.pkg.PowerCategory CPU;
                    enum_constant public static final test.pkg.PowerCategory MEMORY;
                  }
                  public enum PowerCategoryDisplayLevel {
                    enum_constant public static final test.pkg.PowerCategoryDisplayLevel BREAKDOWN;
                    enum_constant public static final test.pkg.PowerCategoryDisplayLevel TOTAL;
                  }
                  public final class PowerMetric {
                    ctor public PowerMetric();
                    method public static test.pkg.PowerMetric.Type.Battery Battery();
                    method public static test.pkg.PowerMetric.Type.Energy Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                    method public static test.pkg.PowerMetric.Type.Power Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                    field public static final test.pkg.PowerMetric.Companion Companion;
                  }
                  public static final class PowerMetric.Companion {
                    method public test.pkg.PowerMetric.Type.Battery Battery();
                    method public test.pkg.PowerMetric.Type.Energy Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                    method public test.pkg.PowerMetric.Type.Power Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                  }
                  public abstract static sealed class PowerMetric.Type {
                    method public final java.util.Map<test.pkg.PowerCategory,test.pkg.PowerCategoryDisplayLevel> getCategories();
                    method public final void setCategories(java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel>);
                    property public final java.util.Map<test.pkg.PowerCategory,test.pkg.PowerCategoryDisplayLevel> categories;
                  }
                  public static final class PowerMetric.Type.Battery extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Battery();
                  }
                  public static final class PowerMetric.Type.Energy extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Energy();
                    ctor public PowerMetric.Type.Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> energyCategories);
                  }
                  public static final class PowerMetric.Type.Power extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Power();
                    ctor public PowerMetric.Type.Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> powerCategories);
                  }
                }
            """
        )
    }

    @Test
    fun `Upper bound wildcards -- type alias`() {
        // https://youtrack.jetbrains.com/issue/KT-61460
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class PerfettoSdkHandshake(
                          private val targetPackage: String,
                          private val parseJsonMap: (jsonString: String) -> Map<String, String>,
                          private val executeShellCommand: ShellCommandExecutor,
                        )

                        internal typealias ShellCommandExecutor = (command: String) -> String
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class PerfettoSdkHandshake {
                    ctor public PerfettoSdkHandshake(String targetPackage, kotlin.jvm.functions.Function1<? super java.lang.String,? extends java.util.Map<java.lang.String,java.lang.String>> parseJsonMap, kotlin.jvm.functions.Function1<? super java.lang.String,java.lang.String> executeShellCommand);
                  }
                }
                """
        )
    }

    @Test
    fun `Upper bound wildcards -- extension function type`() {
        // https://youtrack.jetbrains.com/issue/KT-61734
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface NavGraphBuilder

                        interface AnimatedContentTransitionScope<S>

                        interface NavBackStackEntry

                        interface EnterTransition

                        fun NavGraphBuilder.compose(
                          enterTransition: (@JvmSuppressWildcards
                              AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
                        ) = TODO()
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public interface AnimatedContentTransitionScope<S> {
                  }
                  public interface EnterTransition {
                  }
                  public interface NavBackStackEntry {
                  }
                  public interface NavGraphBuilder {
                  }
                  public final class NavGraphBuilderKt {
                    method public static Void compose(test.pkg.NavGraphBuilder, optional kotlin.jvm.functions.Function1<test.pkg.AnimatedContentTransitionScope<test.pkg.NavBackStackEntry>,test.pkg.EnterTransition?>? enterTransition);
                  }
                }
                """
        )
    }

    @Test
    fun `Upper bound wildcards -- extension function type -- deprecated`() {
        // https://youtrack.jetbrains.com/issue/KT-61734
        val wildcard1 = if (isK2) "" else "? super "
        val wildcard2 = if (isK2) "" else "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface NavGraphBuilder

                        interface AnimatedContentTransitionScope<S>

                        interface NavBackStackEntry

                        interface EnterTransition

                        fun NavGraphBuilder.after(
                          enterTransition: (@JvmSuppressWildcards
                              AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
                        ): Nothing = TODO()

                        @Deprecated("no more composable", level = DeprecationLevel.HIDDEN)
                        fun NavGraphBuilder.before(
                          enterTransition: (@JvmSuppressWildcards
                              AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
                        ): Nothing = TODO()
                        """
                    )
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/5VWCTSUex8e+/4xlktZQtkZY5/sIxRjhjD2LMPYYjBjTcgl" +
                        "SyJrYqTPvlxKxhIqWcqSJckSYxv7kqWoyPLp3nvuxffdOt//Pb/3nPec9zy/" +
                        "931+z/k9jxGMgpIdQEtLCwAABABHDzuAEgDXMYVK6SF0peFQhJ6ujokpCK67" +
                        "3wkAfIJ3vTaASYH6mGBS4j1dvQRj8Du5yVksSB8uqQfv8yutMl7Xl/IR1+/q" +
                        "kjBb75Hu6OianiXNkgOMYDS0j9jEHp0/bKB0WEb/2J7vsHzROF9p76su0ggH" +
                        "/4tYB29XLT83Dyc0FuTo4YDD+SHgsBZNlhBCX2G8nVs1yx6wUGpcdRFKZ0pF" +
                        "JX3eE1+NScHo+7Opfg4ISszeu0zS9vpVbn2umzvQS71xXANgY38pJqsSZDsf" +
                        "99VWsTbNptF2qL7ddDaCdspYURaUb9nKTCjIVDnDl6PRDTN3ZZsQE5FbnBGC" +
                        "lp2+o1Zy05aB36hnB4+pBRI8DLgwSmOa2oIsAoR014aMGGB1G92Ux86EHM+L" +
                        "0AiNpaBV2iYTS3OcXx6DDiSkLL5fYqusPJtjqfPeW70imKNKVjjv7t0XG8z/" +
                        "8m7GdQ02EO+z25JrQJWgzGQmK0LDbVD6SkjrZXoHoNVmI9nv9EVfX/t0SA6U" +
                        "7Ef0SR2lD4px83TwRTtd8ML4ojG+plgHDM7N180LY+Lo5Y3+g814ozpEiyZ7" +
                        "1nwkzxy2mUNcP4E8OoaexViT3JKe30E7xZ1N2sHRxTNoCFjQGEdeN5fHsvwG" +
                        "2vKL3Fp3wvjW9tY+2SQ7b/PzOf0GPYjPN+Oue3NNfpIK6TtiaGM6KdTinenI" +
                        "IWhkBMOBBxiNm9M1QBCj+1LvbG3P2sVO7g4uFbNaRYAHkxJXeYck7TNPFSng" +
                        "zbvkTbUIlCyi8jt4UcLD2F/Zmm2TRmsGHnu03DWZiU9HJJ1BcuusGvUuWRWU" +
                        "X4Tw7OIE+jOui+Or197vKqI7lpHCuTeK2nKE5UNkkgxcxESGQ7MKHEPPCqas" +
                        "qDsbx50K+0X/tuB4uuxgWVmpuzOl5NQ8sgL9Rvbps0n4iiQCUXzKMCTmVnRU" +
                        "dCEN92vh1lvRyDImKP2yd1ejFiynvGKD6fsgGigTG0GHQ3j0w0Hwn9CxloPj" +
                        "VRPfw5sOxhcb9D+VbPRywn6E1J1ClaaJrJgUKvKsHPR8C9dThHrtXeHLdwlL" +
                        "hfQR/lTy8+ca6mQ2uYYQQmlVk/IVSubK7HbIfcsobse4AE7t9fTKu1DhbsHA" +
                        "LGmkxxVI5Kp8t2ch3PtRmUF+mjnl41Je/wJqZaYbmmLLXvXvo7qSgTmNY1Vq" +
                        "MizZ6xnPGGvwj7mMcTxsPUUHjS3AlWHHvYeJy0N6BQRtDs4SoUDCU3GU329y" +
                        "h5IuvT1wKGmYWu+7apm6NLP5Qg8fev91c/K2xuEWul6g9Rq2VhAiQGT5wMIb" +
                        "aPCYE3WGBljcKpAgGMMaGrvK/J1JxcnVzt1DnnR+yOSxjXDIHhr7t5D/5DGh" +
                        "F9GsyfKq0TJVa8/cKHnpggX/OMQtt5DOPfcVzJDhio/b++TVmo12XBA4K0R0" +
                        "2rS359zEdbmUXec/eLTXpL1fCbJyiVsYgSDV099afPB1UGURsOi00q2Tj1tw" +
                        "qDHFzefoLMAJzbMj2gGdHabK5wVikGMoYYW3npSUnYH7D/GYdhAvkHoU3saZ" +
                        "s67zYlWBiHmFutSNNYDg978wnrG3PbAdrC2wabyCVDZEBmWf//XA1HTWZmzB" +
                        "Bog4IzG9qf/M9TeXunr5Qu9KsmHFiRf/2tea/Gr+DTKljNoEuahAJaI489jy" +
                        "GB5IfUatTPOTWfKQRMMraCWvaXxnUa4tt3nnp4vhpB6P7lWY7x88FphOmFjr" +
                        "sO9/Zk1mCb0JsxZ4qW/tEe7OxmKtEENON7tExdvf5BPofRcvZIMNc1rFyH6I" +
                        "a2mcSpHbp9hxvuiSutGn0bmxAkfxaw7sVLj0rp4P7qkNfq4Wtrv96RHAzF5m" +
                        "JjVRfKJ46GPD061zpPi8+MJlpM5miX64jNOdoLh5Nb53Hx/w8jBE+9NwUW/n" +
                        "sAIj1Lzs/J67Dl2YrYPuP5mdZ8aH5xHBidvlCTWmNhXBOV+0aq2cvk4NtJR/" +
                        "RjykedibVoyJCmcNv3Z6Q7N+pIQUyfT4GugldcASh04uT/FCkE/5MOFtQsy1" +
                        "6A5/OlKDP9fAxzYtXm2ikKoqrdbL0WH/C9Wjm6ofM4MzOOvWKQwTrbFI3jqs" +
                        "VDxjS7nckJ9C8o3goDjNt0FDSWK3wTYBCZEma/nbWReJnD7I1d7RPaRE9vuS" +
                        "Bf5qUvM47StwdpNoZWsEQv2W8zaT4ddNbK5VQi5Yv+ndMPEF8ipjwXZcjuQ3" +
                        "2Sohn3Z8dq7sJQ4FXg5dFdVSEzG/uatPspvOYcCMvYvWvX5caQ2ZZIzTpOtw" +
                        "LueDWDRjns9MnTs3U8vQQQRh8GX0M81bQEbBjPBw6013GhL4NmWLkxJWT15w" +
                        "yvUaa1WqdP6MQiUeCllOmS9j24k3ctd6I/1JGxbI2L8XTRdXDs+UGzUrMR1H" +
                        "c7lpOCpNWVgv2gZTEjyNGBjZxrxVtDbEzbOauUKv3rAi70ddYgBX1Jsxyqrj" +
                        "qs34y+tb18cWO551Leq9aq+8LYNuFYenx/enEPXvRl62j9Ntssg25H4fTuSV" +
                        "cyzsjnRjT74JlXMTLrQQ0Dm78e2L3tn7CbNGq5cVP4nP15icnpPtVwI3KKm9" +
                        "jr4ZNirWHjCH7ibxWH5roGr5QHDayZ81z2QqzU61Yx3g4UMKbROXdlIfAAF6" +
                        "NA2XHqjfIbsXwhhiIUItotginhZI9Kd4PLrJLOqDBxfyKhNHp5N+G7rfnXPL" +
                        "muGJu7sZcTf3lOgQp9kbbAFsbstWJFdxwGuvoSgLxx0/YR+tpEwnq4ZgM1eR" +
                        "+QyKfVAS2d0OtH4oUqFtY+CanZQ1qBE1d8cVKTz27yBUH09oeKwdeJsDVvFg" +
                        "fcQrM+7rPadkcXz+YDViJ75GrlCUFiUVjXeaiHlmMrzytcgN1MeloMxwjZNf" +
                        "JfLUU0R9z+6TCFcP4Y3uWi6nz6QIzsDpNohRxgzAQrlHg3qPO0psVfTm6R7E" +
                        "cqNO8AHdfjVufe1L08Qtc0PXe1X1cNuAc6soxCScePbdp8iRJ6e3SGnFIqJb" +
                        "53KyF/LBFbyvqyEZ8A6+N0zjI203btZKqVYlXmzKdZeyDqSR+GbPukbqU48S" +
                        "ohbigxnAZs6NpwTakShic1tLW6NZCWG/54vaLzkK2xQAgDPtj9YI92H9lQ49" +
                        "HdwwoKtevh5uGDtPLyc/D7Sjvb2982FRohDUopPJsyRRvcO0KNGO4GTdhEni" +
                        "pPUl273Fu7v09KQ+gi6j3qAAvyfDFXGHStm/kiEZOTvg7+ZHU+P3aHr8/FNQ" +
                        "/S+UI+GJ7xhC2E/z5kmso/4vdQzrOdn/Gb5OQh81RP5j0Bcpfh4nTqIdNYbj" +
                        "P51E+TNLPYl1VB3Hv2yI6ufGchLt6MC5j5s9/Q/1ZQSjov7+Gs3hxUcOAGTQ" +
                        "f3/6D8pqH4XNDAAA"
                ),
            api =
                """
                package test.pkg {
                  public interface AnimatedContentTransitionScope<S> {
                  }
                  public interface EnterTransition {
                  }
                  public interface NavBackStackEntry {
                  }
                  public interface NavGraphBuilder {
                  }
                  public final class NavGraphBuilderKt {
                    method public static Void after(test.pkg.NavGraphBuilder, optional kotlin.jvm.functions.Function1<test.pkg.AnimatedContentTransitionScope<test.pkg.NavBackStackEntry>,test.pkg.EnterTransition?>? enterTransition);
                    method @BytecodeOnly @Deprecated public static Void! before(test.pkg.NavGraphBuilder!, kotlin.jvm.functions.Function1!);
                  }
                }
                """
        )
    }

    @Test
    fun `Upper bound wildcards -- suspend continuation with generic collection`() {
        val wildcard = if (isK2) "" else "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class Test {
                          suspend fun foo(): Set<String> {
                            return setOf("blah")
                          }
                        }
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Test {
                    ctor public Test();
                    method public suspend Object? foo(kotlin.coroutines.Continuation<? super java.util.Set<${wildcard}java.lang.String>>);
                  }
                }
                """
        )
    }

    @Test
    fun `boxed type argument as method return type`() {
        // https://youtrack.jetbrains.com/issue/KT-57579
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        abstract class ActivityResultContract<I, O> {
                          abstract fun parseResult(resultCode: Int, intent: Intent?): O
                        }

                        interface Intent

                        class StartActivityForResult : ActivityResultContract<Intent, Boolean>() {
                          override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                            return resultCode == 42
                          }
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public abstract class ActivityResultContract<I, O> {
                    ctor public ActivityResultContract();
                    method public abstract O parseResult(int resultCode, test.pkg.Intent? intent);
                  }
                  public interface Intent {
                  }
                  public final class StartActivityForResult extends test.pkg.ActivityResultContract<test.pkg.Intent,java.lang.Boolean> {
                    ctor public StartActivityForResult();
                    method public Boolean parseResult(int resultCode, test.pkg.Intent? intent);
                  }
                }
            """
        )
    }

    @Test
    fun `setter returns this with type cast`() {
        // https://youtrack.jetbrains.com/issue/KT-61459
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface Alarm {
                          interface Builder<Self : Builder<Self>> {
                            fun build(): Alarm
                          }
                        }

                        abstract class AbstractAlarm<
                          Self : AbstractAlarm<Self, Builder>, Builder : AbstractAlarm.Builder<Builder, Self>>
                        internal constructor(
                          val identifier: String,
                        ) : Alarm {
                          abstract class Builder<Self : Builder<Self, Built>, Built : AbstractAlarm<Built, Self>> : Alarm.Builder<Self> {
                            private var identifier: String = ""

                            fun setIdentifier(text: String): Self {
                              this.identifier = text
                              return this as Self
                            }

                            final override fun build(): Built = TODO()
                          }
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public abstract class AbstractAlarm<Self extends test.pkg.AbstractAlarm<Self, Builder>, Builder extends test.pkg.AbstractAlarm.Builder<Builder, Self>> implements test.pkg.Alarm {
                    method public final String getIdentifier();
                    property public final String identifier;
                  }
                  public abstract static class AbstractAlarm.Builder<Self extends test.pkg.AbstractAlarm.Builder<Self, Built>, Built extends test.pkg.AbstractAlarm<Built, Self>> implements test.pkg.Alarm.Builder<Self> {
                    ctor public AbstractAlarm.Builder();
                    method public final Built build();
                    method public final Self setIdentifier(String text);
                  }
                  public interface Alarm {
                  }
                  public static interface Alarm.Builder<Self extends test.pkg.Alarm.Builder<Self>> {
                    method public test.pkg.Alarm build();
                  }
                }
            """
        )
    }

    @Test
    fun `suspend fun in interface`() {
        // https://youtrack.jetbrains.com/issue/KT-61544
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface MyInterface

                        interface GattClientScope {
                          suspend fun await(block: () -> Unit)
                          suspend fun readCharacteristic(p: MyInterface): Result<ByteArray>
                          suspend fun writeCharacteristic(p: MyInterface, value: ByteArray): Result<Unit>
                        }
                        """
                    )
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                        "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                        "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9FBCX" +
                        "pBaX6Bdkp+v7VnrmlaQWpSUmp+ol5yQWF+f2OfofchBovZ8z1SlUh4vtfwvr" +
                        "o7OTxSerCQj77vmyZUqrjm70YT6XvD96/G2b7U9ZXO1mT59knn9PJf7Z59d/" +
                        "mQ/oVrXoSZ8/mb3RQ233lY2R50vznAQU416t9fN6K3bycUlmEZ+ggNXjRY8u" +
                        "7UpJijhv8tq6KSqX+WGg7toohwVOn8yj85kLNysuLL9hrnw1fGLX0bO/2/96" +
                        "8K6w6+MIf/xfTnDHhlv1k2Y3cV4s3hr5/NnBaW0Ff7cU5oQ/O1plrXt2b7Fo" +
                        "9rapn3kTH3PsX1rwc7H4i1MyXN8idk1U9BBclrEsQ1Goe7LIcovTDnK5c9ID" +
                        "5Da6P5cGhVr2fanL74BhYsqIL9TkkEPNPbGkxDknMzWvJDg5vwAacnND4v2F" +
                        "HQXm7912/ZvXQ42upQpdC5i6ys3U7KYILGTMDf7D/Mo7/o6c995vVe5537v2" +
                        "mdYJ134wlpD3CY7vkT9lEf6bPUrLc/LumTdn3j/75l/Zs7r/P/8xM8gdtGXy" +
                        "PLjjgt+q1LeaokrZM8+ItbcVHzrkwLFCNGr5LNe3m74tCkls43BucOiYFxQs" +
                        "ULE3aoXVIaPctUHrM4/Py+W4vHHntusbv/iY51mse8x5OS3mtJWZlNxXxplb" +
                        "DyU08jDWRd46m/enN7frzLXUUDNztg/iYbGaFnmRqw0Ci1Z2Xl2Ra930b/Wf" +
                        "O5t1ojZJHJr2n724pd8l5FTqF43f83ZdEThjutOgt7co7/YnM4vGP1kL3X/9" +
                        "Oz6ntyM5a8PHE1XmcZvs1Nl7qqfd0HmX05uU9u+KKau0Re++3z8ehJZNd0g8" +
                        "wtZlnTx3uVneroXCvDG/Og77lif+9rmgrKXxa0mUbcNnxxrBr6wCCqFf6g3+" +
                        "Tu27dtGZaf+s3lJXL6+uJWbSXHaTtLZOfd/lMTdg0ftJysdPpinrvlTZv8BL" +
                        "05N/ifqVBK1JHGoXrjmfj4qfv2XfiUlaz8OvFByXv6qcGRkqIPv5sf3+iWnM" +
                        "rI4JBkYLj33+fvVkXJecyFeRVy8+qK+yuvtL5WiBtbeXxqVfe1YI/7fJzLl1" +
                        "aEU9Y1GTSIvXtGQF3k691L+W7s8LjgvPsKpX5VBWZ0nT35fIZCxuIjVtbfDp" +
                        "ZBa18w3yIR+/C9uanDYTndd98Cczf/TCY0Vld5+o9b0qq9cr3Ma+WH+jS2/i" +
                        "pmKDpxc+xjS/nSmWNud1zecyjfk9NY4X8rk5WArrFZwZ3j3wajK446qrHCl4" +
                        "ZfWPY6oxLrd1W4Qech2p+tB5+mHhjDcHl7c9MG7c/KFvSsJa3fzDVlldcaLm" +
                        "lrEtDnxCljNOHhTd0aV25tkDKZVuB4szjqs4OPk+y4hZtNz6ZNXuymfULi2T" +
                        "t83WWyavMMDiiIVOccRDgecGP8VBWWLRwtUVx5kYGPzZ8GUJaSCGl2O5iZl5" +
                        "etn5JTmZefG5+SmlOanJCQkJaUDMkuTHphGQdCGJAVxIfVXas1cYqFMCXEgx" +
                        "MokwIExHLsBApSQqwFVmopuCnKGlUEyox1v0oZuDHApyKOZsZSRUGKCbhexr" +
                        "aRSzTrDgDcUAb1Y2kDIWIFQGWqvCCuIBAECnUIpdBgAA"
                ),
            api =
                """
                package test.pkg {
                  public interface GattClientScope {
                    method public suspend Object? await(kotlin.jvm.functions.Function0<kotlin.Unit> block, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                    method public suspend Object? readCharacteristic(test.pkg.MyInterface p, kotlin.coroutines.Continuation<? super kotlin.Result<? extends byte[]>>);
                    method @BytecodeOnly public Object? readCharacteristic-gIAlu-s(test.pkg.MyInterface, kotlin.coroutines.Continuation<? super kotlin.Result<byte[]!>!>);
                    method public suspend Object? writeCharacteristic(test.pkg.MyInterface p, byte[] value, kotlin.coroutines.Continuation<? super kotlin.Result<? extends kotlin.Unit>>);
                    method @BytecodeOnly public Object? writeCharacteristic-0E7RQCE(test.pkg.MyInterface, byte[], kotlin.coroutines.Continuation<? super kotlin.Result<kotlin.Unit!>!>);
                  }
                  public interface MyInterface {
                  }
                }
                """
        )
    }

    @Test
    fun `nullable return type via type alias`() {
        // https://youtrack.jetbrains.com/issue/KT-61460
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        typealias HasAuthenticationResultsDelegate = () -> Boolean

                        class PrepareGetCredentialResponse private constructor(
                          val hasAuthResultsDelegate: HasAuthenticationResultsDelegate?,
                        )
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class PrepareGetCredentialResponse {
                    method public kotlin.jvm.functions.Function0<java.lang.Boolean>? getHasAuthResultsDelegate();
                    property public kotlin.jvm.functions.Function0<java.lang.Boolean>? hasAuthResultsDelegate;
                  }
                }
            """
        )
    }

    @Test
    fun `IntDef with constant in companion object`() {
        // https://youtrack.jetbrains.com/issue/KT-61497
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @Retention(AnnotationRetention.SOURCE)
                        @Target(AnnotationTarget.ANNOTATION_CLASS)
                        annotation class MyIntDef(
                          vararg val value: Int = [],
                          val flag: Boolean = false,
                        )

                        class RemoteAuthClient internal constructor(
                          private val packageName: String,
                        ) {
                          companion object {
                            const val NO_ERROR: Int = -1
                            const val ERROR_UNSUPPORTED: Int = 0
                            const val ERROR_PHONE_UNAVAILABLE: Int = 1

                            @MyIntDef(NO_ERROR, ERROR_UNSUPPORTED, ERROR_PHONE_UNAVAILABLE)
                            @Retention(AnnotationRetention.SOURCE)
                            annotation class ErrorCode
                          }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) @kotlin.annotation.Target(allowedTargets=kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS) public @interface MyIntDef {
                    ctor @KotlinOnly public MyIntDef(optional int... value, optional boolean flag);
                    method public abstract boolean flag() default false;
                    method public abstract int[] value();
                    property public abstract boolean flag;
                    property public abstract int[] value;
                  }
                  public final class RemoteAuthClient {
                    field public static final test.pkg.RemoteAuthClient.Companion Companion;
                    field public static final int ERROR_PHONE_UNAVAILABLE = 1; // 0x1
                    field public static final int ERROR_UNSUPPORTED = 0; // 0x0
                    field public static final int NO_ERROR = -1; // 0xffffffff
                  }
                  public static final class RemoteAuthClient.Companion {
                    property public static int ERROR_PHONE_UNAVAILABLE;
                    property public static int ERROR_UNSUPPORTED;
                    property public static int NO_ERROR;
                  }
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) @test.pkg.MyIntDef({test.pkg.RemoteAuthClient.NO_ERROR, test.pkg.RemoteAuthClient.ERROR_UNSUPPORTED, test.pkg.RemoteAuthClient.ERROR_PHONE_UNAVAILABLE}) public static @interface RemoteAuthClient.Companion.ErrorCode {
                  }
                }
                """
        )
    }

    @Test
    fun `APIs before and after @Deprecated(HIDDEN) on properties or accessors`() {
        val api =
            if (isK2) {
                // NB: better tracking non-deprecated accessors (thanks to better use-site handling)
                """
                    package test.pkg {
                      @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER}) public @interface MyAnnotation {
                      }
                      public interface TestInterface {
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter_myAnnoOnSetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty_myAnnoOnSetter();
                        method public int getPOld_deprecatedOnSetter();
                        method @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnBoth();
                        method @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnGetter();
                        method public int getPOld_deprecatedOnSetter_myAnnoOnSetter();
                        method @Deprecated public void setPOld_deprecatedOnGetter(int);
                        method @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnBoth(int);
                        method @Deprecated public void setPOld_deprecatedOnGetter_myAnnoOnGetter(int);
                        method @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnSetter(int);
                        property @Deprecated public abstract int pOld_deprecatedOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnBoth;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnSetter;
                        property public abstract int pOld_deprecatedOnSetter;
                        property @test.pkg.MyAnnotation public abstract int pOld_deprecatedOnSetter_myAnnoOnBoth;
                        property @test.pkg.MyAnnotation public abstract int pOld_deprecatedOnSetter_myAnnoOnGetter;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnSetter;
                      }
                      public final class Test_accessors {
                        ctor public Test_accessors();
                        method public String? getPNew_accessors();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnProperty();
                        method public String? getPOld_accessors_deprecatedOnSetter();
                        method public void setPNew_accessors(String?);
                        method @Deprecated public void setPOld_accessors_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnSetter(String!);
                        property public String? pNew_accessors;
                        property @Deprecated public String? pOld_accessors_deprecatedOnGetter;
                        property public String? pOld_accessors_deprecatedOnSetter;
                      }
                      public final class Test_getter {
                        ctor public Test_getter();
                        method public String? getPNew_getter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnProperty();
                        method public String? getPOld_getter_deprecatedOnSetter();
                        method public void setPNew_getter(String?);
                        method @Deprecated public void setPOld_getter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnSetter(String!);
                        property public String? pNew_getter;
                        property @Deprecated public String? pOld_getter_deprecatedOnGetter;
                        property public String? pOld_getter_deprecatedOnSetter;
                      }
                      public final class Test_noAccessor {
                        ctor public Test_noAccessor();
                        method public String getPNew_noAccessor();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnProperty();
                        method public String getPOld_noAccessor_deprecatedOnSetter();
                        method public void setPNew_noAccessor(String);
                        method @Deprecated public void setPOld_noAccessor_deprecatedOnGetter(String);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnSetter(String!);
                        property public String pNew_noAccessor;
                        property @Deprecated public String pOld_noAccessor_deprecatedOnGetter;
                        property public String pOld_noAccessor_deprecatedOnSetter;
                      }
                      public final class Test_setter {
                        ctor public Test_setter();
                        method public String? getPNew_setter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnProperty();
                        method public String? getPOld_setter_deprecatedOnSetter();
                        method public void setPNew_setter(String?);
                        method @Deprecated public void setPOld_setter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnSetter(String!);
                        property public String? pNew_setter;
                        property @Deprecated public String? pOld_setter_deprecatedOnGetter;
                        property public String? pOld_setter_deprecatedOnSetter;
                      }
                    }
                """
            } else {
                """
                    package test.pkg {
                      @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER}) public @interface MyAnnotation {
                      }
                      public interface TestInterface {
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter_myAnnoOnSetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty_myAnnoOnSetter();
                        method @BytecodeOnly public int getPOld_deprecatedOnSetter();
                        method @BytecodeOnly @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnBoth();
                        method @BytecodeOnly @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnGetter();
                        method @BytecodeOnly public int getPOld_deprecatedOnSetter_myAnnoOnSetter();
                        method @BytecodeOnly public void setPOld_deprecatedOnGetter(int);
                        method @BytecodeOnly @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnBoth(int);
                        method @BytecodeOnly public void setPOld_deprecatedOnGetter_myAnnoOnGetter(int);
                        method @BytecodeOnly @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnSetter(int);
                        property @Deprecated public abstract int pOld_deprecatedOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnBoth;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnSetter;
                        property public abstract int pOld_deprecatedOnSetter;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnBoth;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnGetter;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnSetter;
                      }
                      public final class Test_accessors {
                        ctor public Test_accessors();
                        method public String? getPNew_accessors();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnProperty();
                        method @BytecodeOnly public String? getPOld_accessors_deprecatedOnSetter();
                        method public void setPNew_accessors(String?);
                        method @BytecodeOnly public void setPOld_accessors_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnSetter(String!);
                        property public String? pNew_accessors;
                        property @Deprecated public String? pOld_accessors_deprecatedOnGetter;
                        property public String? pOld_accessors_deprecatedOnSetter;
                      }
                      public final class Test_getter {
                        ctor public Test_getter();
                        method public String? getPNew_getter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnProperty();
                        method @BytecodeOnly public String? getPOld_getter_deprecatedOnSetter();
                        method public void setPNew_getter(String?);
                        method @BytecodeOnly public void setPOld_getter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnSetter(String!);
                        property public String? pNew_getter;
                        property @Deprecated public String? pOld_getter_deprecatedOnGetter;
                        property public String? pOld_getter_deprecatedOnSetter;
                      }
                      public final class Test_noAccessor {
                        ctor public Test_noAccessor();
                        method public String getPNew_noAccessor();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnProperty();
                        method @BytecodeOnly public String getPOld_noAccessor_deprecatedOnSetter();
                        method public void setPNew_noAccessor(String);
                        method @BytecodeOnly public void setPOld_noAccessor_deprecatedOnGetter(String);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnSetter(String!);
                        property public String pNew_noAccessor;
                        property @Deprecated public String pOld_noAccessor_deprecatedOnGetter;
                        property public String pOld_noAccessor_deprecatedOnSetter;
                      }
                      public final class Test_setter {
                        ctor public Test_setter();
                        method public String? getPNew_setter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnProperty();
                        method @BytecodeOnly public String? getPOld_setter_deprecatedOnSetter();
                        method public void setPNew_setter(String?);
                        method @BytecodeOnly public void setPOld_setter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnSetter(String!);
                        property public String? pNew_setter;
                        property @Deprecated public String? pOld_setter_deprecatedOnGetter;
                        property public String? pOld_setter_deprecatedOnSetter;
                      }
                    }
                """
            }
        // TODO: https://youtrack.jetbrains.com/issue/KTIJ-27244
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class Test_noAccessor {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_noAccessor_deprecatedOnProperty: String = "42"

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_noAccessor_deprecatedOnGetter: String = "42"

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_noAccessor_deprecatedOnSetter: String = "42"

                            var pNew_noAccessor: String = "42"
                        }

                        class Test_getter {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_getter_deprecatedOnProperty: String? = null
                                get() = field ?: "null?"

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_getter_deprecatedOnGetter: String? = null
                                get() = field ?: "null?"

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_getter_deprecatedOnSetter: String? = null
                                get() = field ?: "null?"

                            var pNew_getter: String? = null
                                get() = field ?: "null?"
                        }

                        class Test_setter {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_setter_deprecatedOnProperty: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_setter_deprecatedOnGetter: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_setter_deprecatedOnSetter: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            var pNew_setter: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }
                        }

                        class Test_accessors {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_accessors_deprecatedOnProperty: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_accessors_deprecatedOnGetter: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_accessors_deprecatedOnSetter: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            var pNew_accessors: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }
                        }

                        @Target(
                          AnnotationTarget.PROPERTY,
                          AnnotationTarget.PROPERTY_GETTER,
                          AnnotationTarget.PROPERTY_SETTER
                        )
                        annotation class MyAnnotation

                        interface TestInterface {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty: Int

                            @get:MyAnnotation
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty_myAnnoOnGetter: Int

                            @set:MyAnnotation
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty_myAnnoOnSetter: Int

                            @get:MyAnnotation
                            @set:MyAnnotation
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty_myAnnoOnBoth: Int

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter: Int

                            @get:MyAnnotation
                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter_myAnnoOnGetter: Int

                            @set:MyAnnotation
                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter_myAnnoOnSetter: Int

                            @get:MyAnnotation
                            @set:MyAnnotation
                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter_myAnnoOnBoth: Int

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter: Int

                            @get:MyAnnotation
                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter_myAnnoOnGetter: Int

                            @set:MyAnnotation
                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter_myAnnoOnSetter: Int

                            @get:MyAnnotation
                            @set:MyAnnotation
                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter_myAnnoOnBoth: Int
                        }
                        """
                    )
                ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/5WZBVDU0b7HYelGcukS6RQB6e6lW2ppWEIaJCSWlFZCUqSk" +
                        "BaRLWpCQFnCpBZZmCQlBeHrvu++pb6533n/nzPx35r+fM/89Zz7ne85PWx0N" +
                        "nQwFGxsbBQWFCeXXiwwFHQWkqC/Lo6qpxAeS1VRVUtTT5wUp3QyjoJyARj5q" +
                        "qPPwThCo83COjYzX6/JP31/Z8ORVA3GrgiZ8yt/pItV4PDjVRka4DJFjfEND" +
                        "I/CNtQ0AirY6FnYNKUfNwx8diPxo2v+2e/ofzdvOy5vvMcSBT//HjaWbu6yN" +
                        "jZ2Xl7snr40L2MsrQf+RFo0yULK+ce7ZkZ3ryCixGs5dMzUK2smSSBIVlZhH" +
                        "lMQK7CREE9ZJORiBcwsmjlcDelfS3/DipsszonGPPjMYfF3Es6JAa1xvCfLa" +
                        "HHm4HCR+e3N+Lo1CgeTpDe2NLHxABFkQpw7ir5pRi8QDz3vwY36VkZ5taHWb" +
                        "fTf6Hc3CYy2G/4N6lJPoR/yUoTLYXetk0hjmMUYeMnJvgXgRkd0Zimoig6V3" +
                        "AYp+/j5TX+qq7jXlMlB5bnB9oxsUHlmwJoGp+MrKxYH3eNN9DkS/aDLDe2cC" +
                        "V8RRJRC9gUDBUYKbYDyJWIunVUlMO0FC6PIFQY88Kajo55rrZ1JGr9enQYUK" +
                        "jGKsW7ntLU+dM5g6Fcjq+dLJ4/njaU01YfwNEfAo+0zb2eld0yHviZ43MaJK" +
                        "sW+OU4wIRbgd4G8sliddO7pxVHRNiZB8T2phKarxq2BAvnXOqlP0zaDP1Dta" +
                        "mAD3c90FPULbanM5DvIjzP05SA7u2DslmvRR5eyMAxufkJFd/IjVqj5j34wY" +
                        "mZBHpo8CO7r5PZI4u1yAk5SI6fD3fZJNHrVH4Pk6yAKq35lrrmBFQ/NUeB/v" +
                        "Ep9Naa+eX4+nIvGHFiwzPivcOOHuPcWFTniixQxd7rpqAhxbEmXEtfULJv5e" +
                        "8po08MAlUWkmIKprTPMYx0lGTWVnXlVO94ts1reR9/OqOlk2fDaIrg+pnK6U" +
                        "0yqyWc86pW85QCnea3tFxAbW5RIUBmvldZKVvi0fx+dq0EjoX2WjwJ0n0T5w" +
                        "HV+d5Jur5DG2vEnVZQWUSFoNoNmbW+kYGjRZ4ghsSlgllTdjxo00pOEFt2gR" +
                        "0kX1GXwVwHdleJk4scJ68pl4ICS3ppHC5TTNoLh15AXBc3faB2ct3zXZ4q/8" +
                        "Tcg7OisWo8SJwiNQ59xoSo6QDzMYVh4YWw+K1XFdJ+ggMksR7GqUJMV5S4rO" +
                        "li0ZkQ++fbR1S3wyFAxwCpJMh5DudtxR12Y4sa9xalQGDlPRa9yi2ollXNA5" +
                        "1Yw+cV2CXGlJlotMUziyEDN5CDDrvkrtkyri16j1MVX9Zgt8z/jKHkdaQRwW" +
                        "IdT05U0S6DbqtCYDWsfgMk97InJVHWuduRUUR77DSiny/GpdS0NjPPnzFETD" +
                        "8/00Ag645ye0VnuVY+6v8CFm8GX4rU2pBaL0G893L/SnGkLPAXqZm5ndDnft" +
                        "068V3C6Gor6zN13i6EnjvElh7REPsu+VShMPTnekdeiOdCgrWoDJSVW2tiXs" +
                        "pA8zXSlOAltANlKVtVc3bdgI+LaBFMVC3AHRM00YT1ZMHizt883bLX1EbBoy" +
                        "Ya7z1Gya/dtD1QtrohSSvAykHY4lOCXKmHMVwn0YhtP+fpj+GBABqADUydSI" +
                        "GBMDSfzvIWUYWfi5XuF440mQsGPj48VjP9kW4NO+ZvjpGST1d0xadBSUIZy/" +
                        "eYb6/3jGwc7b2+5fjjFY1KQxIjN3CGJwuS9shS1EiENIyWaLD1p8p40NlakS" +
                        "DV2LKVlDJThLEbcaN/bCXOg8mr5R7nqVYmqwIx/pHzudFNeKsOZttHEZQtR1" +
                        "nIlB9oMJr8+P8p+iSvRT9L4djawSToLsPDKlnW529eICDLyidSrNMp2Lz2r1" +
                        "8u/j8kVHjULWuW2d4cpb+brxJRL3CIbH8KPvM/h0J3P4sJhZZ76eOdDs2FEm" +
                        "LG93OoqzP9lJrIW8BLnNdyXnzLdMzrTYLU4mULNqCbuPavht9qfICGLiROAz" +
                        "wYssLBOYfXozseKtYk5jQ04TRnzp7zcNEpuUnF4alD9biB7PsQeWVM23OmkP" +
                        "RakNlpjPEfjASdTSWQh4oYbwojl+NpqSfrbsDEE5n5DhXXy/Bb3Y2vVrBfTV" +
                        "wzgaGr4mqoNz7XcbUcDUqvvOIYxXZXGBVB5fztWuQi8C9av787leGoIk184K" +
                        "irejtGvIZWAlY47RPp3Tj5wvhe3sLOfjS10am+o827wmcEWpGy+1sKp08YVq" +
                        "fD1GAve0sNkXQssMRSy8HlU4NyolFg04hS5Re0Vl3g8No9O7doDNkzsamFtd" +
                        "PA4R5la3SeXmQDjUkflQqGZhDY6NUxQDHhGZX9Onk22DDHHisQaNmS5cTmD9" +
                        "Napx9zZO6o+shIqgDOiFd1jVkjOJ5ltxUsw/oMiWpPk/2wi4gPW7Ezpmymxb" +
                        "CRWooINaP8mGb6a9wDKP5xy206XxWogk4tAZHGP20iiBIdwzIWm45rZTTzxd" +
                        "7n8wMsoBr43GgncJLIQ0fQqmMIzbbjjD+UY4zW+6U92Q+88HmJqWxDL6Arbs" +
                        "FSsOndIP9r5bvXaBpMHnTKeWl49azlv5EQvjsBnuqu6ySBErz3mJWs4gvRov" +
                        "bu3BiOlqxtFTkcgB8tI042L3zBGpIhPPPPQq9wH71r5NcWvBUSa/8xzfci9l" +
                        "R8KNW6XTjuy6zQhUjF2hAUkU6xe4rJGocSpux2KjY+5H1/naIRehyPPtU4Z3" +
                        "RCuRg0ec4auQIPm5izX6fIdEY+XkIxMjx/ScnOvCrldmTqkwx6wX/hJURBsn" +
                        "ohU6c5WDwF6gIoDXBrAU3eHHVpNHw0vVkOQ4gHEPSTVobqs6LElCucUvOhPd" +
                        "PaFF5SLtOilNwLHeSpPvLJLHBC+UTOgq8oFeumqCWyH5m9/eCkz0DzRwKbAO" +
                        "P4+RKd1q8Z2A332ydubWkIGYMAig9/MpOFtlXlzE8KBszbBw9fPIMLO94uuA" +
                        "gvyuZxsMCrcAd7gPWLT7EIoSPGt4QarqQQZ994M6iO4hCdLPBLgZ8bjxoBib" +
                        "sH6bsGYZWUaWLWn8A5x1dBKADQANwAIoBNwHTAEub1F/uoOcuLbuFA0Fxfn/" +
                        "6Q6vX91h+DOfkEnVWyxTLFYmyLcIda8a0rBVyme9wPRkKihIp5URlUvzl3km" +
                        "6UlZ41eVW5vdfvTiJukmXMnZoIyIfFi9+MeXhHIH3znUWUqZY7PErpPcQ/H7" +
                        "EdfjT5/eYKBY9nIBTDSp5hZGo84oKAfjPTVz2YkBO2VLMw/0mqgXloY4qo7Y" +
                        "odgKwgQfV8axoceRvehQpteh+DoAwdsJK/R7XBHYItjcQuzLy/d3Q9abNRcJ" +
                        "q7ZzZs8rgwfd9KK7lB9eym28dksTj5p5v252tTCi8dQCkJocF4EON86DFV1X" +
                        "BUmrrybLNFdk06nJtIXFKsLqXKq9ehvfMpCmlYyCeicbpeUntN4pUk3RfHhj" +
                        "6lttJy40d8PN7DLJ+wBHA0svpZa5Uy78w/nG/v6hL84XugLHQYI7Kl+dha7f" +
                        "54L3TwVJHi/uLtKtM0w00d9a9/tSL3BssCW6GWu0TaO4NtlkfYtVFVFSr7Io" +
                        "rdzheoR8wZNoaEgg5tAR+umt+lOxA4yBBco2dDKJM3L0OhR+mHiCyItdn9zd" +
                        "LRVnH/mL5BjgmjUKoCj2yC/qc9pjg6cyK8sn9ZXMINW4orx8Dl0Nbl3RJK3c" +
                        "/CpV1mPB6+2MmCI1rIAHEixaKantFHrySzv9uwxSwSqr5xwb1y5KiYrTW+20" +
                        "z8qOIsgNcMLn/MKRApR5Mv6qoLNuFsBNfajDnbwUGw6DniIPEdX+ovasA9J3" +
                        "Xg1yAxhabzzAvgkxFLZfMtznHzVQW3gtmrh6Aob70Rj5Az/VNk+8tquA8KPV" +
                        "DM5uiLB01e1eCA1wRSn6iVeR7rSTG6m0e7SVZEe32sR0MGgYtlhAOicWb0IW" +
                        "EFK079uBlW014k+eraFahnEYtMGntyC9RUiUw4j4aA54/K3m4yBmQmphJKls" +
                        "vZ9Hel9E99eZzz2hxgV8JSu0vn7ZzVN9BD0P24+x+qRaObSC+RmvqRJxiFA8" +
                        "NDAeENgAUwBDXWHfIIwBBdLzXuFkTh8uyLBE1UXZMPqXiR9x3BCWf7oTjjhQ" +
                        "IeF1wO3DsAxn52ZxWNrIOxUMdqvNegx7YrCWrMjK1KRG0i59PJHbHtX5Xgtz" +
                        "tSnZPrWbkl8RTG2vvcSdjv31YQ6eGDehF7DoZrJUabklbAwRhPCSz11WRlQY" +
                        "VwhNXlJUF/ZJbwmNEEmUFb9KikYLMmsV+Jxsp0+3o9exXmFBXf7tTnlI/Mju" +
                        "XL7RWwdL81Nu6U9KAo7wIUcaFW93q5NnnQlr2jTZjnAOQemugBNW/+3E5dZO" +
                        "amBH0mvDJEZ9zCr8YsHOkwdmPb2CN7xPvNGh4NLOh+HcaJHdYhHYETh+OlbJ" +
                        "DslWj5Nko7pdMevxPfFTNUIHgUVUNwuhAZgfb9F+yiNKB4/46w95OP1VHnT/" +
                        "Rx7g/97eeP3LH//KHiKNVNniTjTYQlb/jB5Fqoxma7OocSyTL9S2rBha0OBU" +
                        "kDGZRZG8hdstSrIyg2IpfXa+lG9s32PLA3m/uKBhBpxJLQbWdMyJIfCvT5Yt" +
                        "n6L5xxhEMSmrKEMO5hqM1veQzgYqTvOCmAOvDo7kq6m+VEJ22wJf7/QRW8dI" +
                        "zYJgAT3EWWPuxcSyQrH81vhgRKC5YPyLmvc5Ds2PKMcGWnXEiQ6mFaYoDA5i" +
                        "FlpbwI9AYHFpgVMtxEcEjCuxKtunPN/RVy8HwgdiStAA4D6hYt4MA0sksATG" +
                        "FuQD5RlAHG05nlatHXohz8RoPeEy/CM+yn6p2SUbj5WbNCZratrdH0a5Oas7" +
                        "lPVJzDKiWLBpz7wboE7nGchy8FLA92DHN/qWrivH3WZ5R+ZU0cnJKSABfPyt" +
                        "wDASjp3N5LVQQo8+UDOxxKcG/kJ5SejTYtE7xfvc7mM1JLBnvqHFTW0/nGSB" +
                        "2pV0+UHfdcJ9o1ZgLUm86EaSaDA8Js2QyZb4Q2tvZSzD2C57WQBHck/88iZq" +
                        "8cez071tpQ4ywhqPaFLlYg/ga8jQF+We4NCwfb1rZZg5+VDFqOB1oOd0vn5U" +
                        "WtHrPFrWtOZd2w9nhPm0oxyKjcCAK4i+TtpoODUVAVG9tT9vdVTQ4cRwfZr7" +
                        "0k1OQbzG8bqjEG1Sea34qr8ZSPtJHU9ww4OOQXUtnSc6ID+l237QYempmWie" +
                        "Djnnztc4/RrlRvmJnQ36h3wdWqBmKCKTS34sMCVzVu7VSkRwNFNAiDTQzZU+" +
                        "M3GJmPVEO0OMLivymRHn9ZfyidFtnt0SY3HHPdyrtMC3X0QiDTYGKJp8/OjQ" +
                        "p8ijjc4tj+3f0uO8bRJj1Go8g60Iu2OLsWVMppLvvq+OI4cRZxbb3CkhIHhe" +
                        "nEEimd+cnVB6GksyTzTEeQBivvV0KZd3KeFJv0tzZDjuWNxSGuNShGRbG7QY" +
                        "kkvG557kSz4mTX6l+8bkVFvhKI5yqHW5GLdLPviEcGKu5Yv7O8LVlqQjA/zv" +
                        "lMh0vpXiIIl82gJCK34Z2N2KZFDbm0Nivi9XaDtHVC7Y3Btg+5W8K4M+ZPin" +
                        "ztO3WN+HURa/RkmgXvPi4E/dZapRp74hEj+LrzkiCfZ8ISXHX0jShMx7f3UR" +
                        "OtAG4pFzLWJrZezwJd+edbORFbEVmNeJj5WJj+Xq/8wo0FnCmwbpN74nPRAO" +
                        "T+6HJV69PAxf2CWc5qV9kFolIw9diFlHY77kim1nYl4m2fQTOXSuiD8fs2tf" +
                        "Tnk5i1Gfu/vyjm8tnMIJ9f6+mn1O5j6JfdUVj+7bNcku5JtZDjH1OQ83mgIS" +
                        "Kc5VMA4DnNTzyAknCTkvnNmHyFxW6lLQXatV0Lf+4GV/90C96G4cSQQJnKSJ" +
                        "9XuBvKm18WtlaxlrtcmnouzXRD81UyEzMvNzf4OF+zfN0PyqGVCArJubuzfY" +
                        "28nd7Z+SidSV1SKVBQYvz07qkuPGvGMMVxGtU2kOCG/euVflmj4DPZUpWVSt" +
                        "925VF5tydu84ypfIHGCRHr5IUFvsvWfRGXzSeb4UdGl/+DT4FnUFPxoKYmnw" +
                        "cA80b5KS0kqdxiYtVUVVYF+OSXmcptLqVhjMwqInk/hWxKFf45DX9AS6b8r3" +
                        "gFOTgrUA6F0coG58R63Y0g7BV4ATTbUTsDJi5IKZUEIJNYLOYPl/78aT5zO2" +
                        "5b0VuGoYUUVzsrW+w7apLEfAurolPWlfvuY4EiWHvNTbmpPscWu0E96T9V3W" +
                        "qxt/uXphWOdYdhDJQHOIDSGieaPOItaD7Sb2XSfRMtbFpBEDfUfglqexlFQp" +
                        "VwS6463L/ZC8dP2FX5z0Yc6wt+dOzqd+b2uMUmiV6SbpCQFtM73noZjqeKGK" +
                        "cy5hYmCUdqtGdIRKZxL5YQk1fgBW8CXrrMejoYBQqTU8ZIx8+t7jeKKbaSP/" +
                        "0WVhvws+udrInD5BtUaJxg2cPMEu5to4wVlul7C7s7VhDh9snhNI5V19PtaR" +
                        "TWNqDN5bwagQ8j2qw1C20bK5Ffk50gP4otMFqCgog4C/jTTtnwuKqtuPLGoP" +
                        "trH751CnZcB0Fw2JB29TE1zjUcHYwBOCgc/E/AWqz4WdADg4OrisEeIa9s1W" +
                        "ZFcrYp8F4eQaXoP1p6+DIzX5xPAzgrOkZEsr0ABPP8iNhCoh9txvA8ecfQxv" +
                        "EF1dN5hIoDQHsBHdpOU5XPfAoXMCpD6dFB+OLa/zKlhgLN6sn1+9BTZuqdCN" +
                        "y/LKqv1uZVJb2hpYJ2dasfutgsfctXFNbkzrVwNZxuczVMxa7JkT9Ve46e1F" +
                        "hBWWIhr1jRA3nGdGxVa2V/ZV4pO2VIYt9lIOdDWOrmI3rzfpFjIa5R2Cnbqq" +
                        "Tbr4d9Uv40Ct22I01F+kqk3S6l4utgt46vYYyNq+gIZL1QubV830e28vFFQw" +
                        "jykodjtWsJR6LQrXBm51dLZcpnPd3W4c4an5bkG1eHzQuqxZSQS7lvSm8+2O" +
                        "PjAYoAx680SChu0V5IwWPhrBVOOKOEwZ+/GI+bi3r5X51MvzXE0jz/ccw3Rg" +
                        "KsVewWbgzI5JqBZ3f6f3IiW7dmLAzVydpOlG2xqL4uX5mgS9Aamz/gzvWOF1" +
                        "qXPKG2qTOFpW3rG5aQM7vrIyVZ8HyMrT7ePzzo/LcrW2/CTTrfYRKckUS+pO" +
                        "sEYfniLF6jdflXg5W+g3U00rQcVGpldFal3RcfdaUDftG5rflc5N5fPevt0/" +
                        "8ucXfEMg8FVGt1eDx5ll31nEW26yMFVTbbQWUq7jZaZLmpr1IksPlLV9b9RJ" +
                        "dVzliaHz2INJUJuqqrPFgdh+7S7GkN3djK8rFKxNdwul9oA+Jrpcpp9DDiC4" +
                        "JIsH5vFc0Xh260VT2+Qh3QYt1zvF4fpYJcXCU37gxOPcVyeYCY1hTeaC8BkN" +
                        "3QEQ1t15n3PYa19HNq2gprJN/pdtaKJU/k+SlkIOGS8DMYba0J34cJXrO8jM" +
                        "ksrOzPxEAw/Rc997HSjmLJApDzj1T1awkePCc8ON4fHhzQkMWQ0JcmHiIVWd" +
                        "ni4cMUsheI9T9nOzDFs4zz7q35jdoUmIjdku4dhx58RMWQELWYpXUfa1eSpC" +
                        "L/blPkm5pADXkjChF3bfrIgCyJSKwNOjwl+doQyXy/cKJyen+vqzhJ7Mbx7M" +
                        "zOnUdBUmza6ROnxEGe/DiQCS+IeuMkr7nTVtK+BJjrDMehwBO85Uvk0yPL9y" +
                        "tZhvPjQmrVqSEBk6bxzW/zhGs6a1zDmiMr55Ud8pgaiGF5lupc8I+4+09ald" +
                        "nSihLErQ2HXxNLYjuA+RxpG8HTfrlxjVzSOoALE6MQof7lWmZWuTQVxPLG3s" +
                        "Cn/vUFpwApJgtbbdGUoMhhIzSTAFqV/roZNCS6Ho4BfI+ItsfBvoIfYyaR5p" +
                        "Kza4B6+HCColJ73KukpekP1J3pjNhdgaatED6bkLjkNG8WupkArj3Iea9Dzq" +
                        "oWX6Kv1TKurZy9p4P5aPRwR/kwrHv5XKXQU7e7CPi7eq62OX/06suSkg9ykZ" +
                        "4qjlhqksKNkkGe5qIbQx2rObMUz0MS4BAxe57kQ5JeHEbB6myK75x7bYrfG1" +
                        "4TCG90ly8b1Gzzj1+1MdHCSu4pdfXp59/2iEovWW9+3q0GUzMGkt2LfoSJ++" +
                        "DCp85V10b+Fj+yfecGJHuoqyuAbBQv/H/hqxScS4LgkfDgm9xTTK2Dl08ibD" +
                        "5nlcZYul5/UHjGuC7BNIsZvoabEr9+Kr5l7GXj0NRQB0NMfK7hy5x+9vNha5" +
                        "HVXEvA8v4XAKG+tin4OV6ZpZHSXsnVt2kvqfScv2XhcNpmJuPghwO63sfJYS" +
                        "iEjW4lPofHsDN3G79JN3H6rQD+hPQL8UWofFDE9zoaEKVNPm+SI5KtqVARWI" +
                        "Gh455VcBT/wA+zsDnz5bpCKTXS8f72E4LwpvOIncuIUhrkKsg9KnZDfr5EEd" +
                        "U7rZk0DOpgbPRFGXvXnVl58qNEjpWWSqSOJMKxIxuJn7zBPTkSd38h0GF0aW" +
                        "4E2jPIr45p5vCmWb4+9FqJ+EG21i1IxhCE5uaETcR3VbFRZrHvcIFb9vndDV" +
                        "dDF2jAUlcY0lj4U089bG0ZCIPP1HLYZlm/919Y+VJRDtP2WI/ykFuYKd3Hgh" +
                        "7t4uTm6Wru62Pi52NlZWVvY/Grq1Jia7tvUna5R/sL8yt3eQ/vgl8B91HlQA" +
                        "Gcr/0n+tAf0sNP1+/buy05+UX0946X8jPP2P1aM/Wb+e+FD/xkKg/+2E+E/O" +
                        "r5u/3zksOH87LfqT82u6o/uNY0XwHzaOf6J+jQ80v6EG7/w1HP4J+lUZtL+B" +
                        "akn/nj3+JP067zh+I2EC/x/C+RP765T7/U3xqP86hbXVMTB/Pobz4/Pqx9/k" +
                        "8I+h+y/TO8ltHR0AAA=="
                ),
            api = api,
        )
    }

    @Test
    fun `actual typealias -- without value class`() {
        // https://youtrack.jetbrains.com/issue/KT-55085
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/PointerEvent.kt",
                """
                        package test.pkg

                        expect class PointerEvent {
                            val keyboardModifiers: PointerKeyboardModifiers
                        }

                        expect class NativePointerKeyboardModifiers

                        class PointerKeyboardModifiers(internal val packedValue: NativePointerKeyboardModifiers)
                        """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/PointerEvent.android.kt",
                """
                        package test.pkg

                        actual class PointerEvent {
                            actual val keyboardModifiers = PointerKeyboardModifiers(42)
                        }

                        internal actual typealias NativePointerKeyboardModifiers = Int
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                package test.pkg {
                  public final class PointerEvent {
                    ctor public PointerEvent();
                    method public test.pkg.PointerKeyboardModifiers getKeyboardModifiers();
                    property public test.pkg.PointerKeyboardModifiers keyboardModifiers;
                  }
                  public final class PointerKeyboardModifiers {
                    ctor public PointerKeyboardModifiers(int packedValue);
                  }
                }
                """
        )
    }

    @Test
    fun `actual typealias -- without common split`() {
        // https://youtrack.jetbrains.com/issue/KT-55085
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/PointerEvent.android.kt",
                        """
                        package test.pkg

                        actual class PointerEvent {
                            actual val keyboardModifiers = PointerKeyboardModifiers(42)
                        }

                        internal actual typealias NativePointerKeyboardModifiers = Int
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/PointerEvent.kt",
                        """
                        package test.pkg

                        expect class PointerEvent {
                            val keyboardModifiers: PointerKeyboardModifiers
                        }

                        expect class NativePointerKeyboardModifiers

                        @kotlin.jvm.JvmInline
                        value class PointerKeyboardModifiers(internal val packedValue: NativePointerKeyboardModifiers)
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class PointerEvent {
                    ctor public PointerEvent();
                    property public test.pkg.PointerKeyboardModifiers keyboardModifiers;
                  }
                  @kotlin.jvm.JvmInline public final value class PointerKeyboardModifiers {
                    ctor @KotlinOnly public PointerKeyboardModifiers(int packedValue);
                  }
                }
                """
        )
    }

    // b/324521456: need to set kotlin-stdlib-common for common module
    @FilterByProvider("psi", "k2", action = EXCLUDE)
    @Test
    fun `actual typealias`() {
        // https://youtrack.jetbrains.com/issue/KT-55085
        // TODO: https://youtrack.jetbrains.com/issue/KTIJ-26853
        val typeAliasExpanded = if (isK2) "test.pkg.NativePointerKeyboardModifiers" else "int"
        val targetLanguages = if (isK2) "" else "@KotlinOnly "
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/PointerEvent.kt",
                """
                        package test.pkg

                        expect class PointerEvent {
                            val keyboardModifiers: PointerKeyboardModifiers
                        }

                        expect class NativePointerKeyboardModifiers

                        @kotlin.jvm.JvmInline
                        value class PointerKeyboardModifiers(internal val packedValue: NativePointerKeyboardModifiers)
                        """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/PointerEvent.android.kt",
                """
                        package test.pkg

                        actual class PointerEvent {
                            actual val keyboardModifiers = PointerKeyboardModifiers(42)
                        }

                        internal actual typealias NativePointerKeyboardModifiers = Int
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                package test.pkg {
                  public final class PointerEvent {
                    ctor public PointerEvent();
                    property public test.pkg.PointerKeyboardModifiers keyboardModifiers;
                  }
                  @kotlin.jvm.JvmInline public final value class PointerKeyboardModifiers {
                    ctor ${targetLanguages}public PointerKeyboardModifiers($typeAliasExpanded packedValue);
                  }
                }
                """
        )
    }

    @Test
    fun `actual inline`() {
        // b/336816056
        val commonSource =
            kotlin(
                "commonMain/src/pkg/TestClass.kt",
                """
                    package pkg
                    public expect class TestClass {
                      public fun test1(a: Int = 0)
                    }
                    public expect inline fun TestClass.test2(a: Int = 0)
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/TestClass.kt",
                """
                            package pkg
                            public actual class TestClass {
                              public actual fun test1(a: Int) {}
                            }
                            public actual inline fun TestClass.test2(a: Int) {
                            }
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                package pkg {
                  public final class TestClass {
                    ctor public TestClass();
                    method public void test1(optional int a);
                  }
                  public final class TestClassKt {
                    method public static inline void test2(pkg.TestClass, optional int a);
                  }
                }
                """
        )
    }

    @Test
    fun `JvmDefaultWithCompatibility as typealias actual`() {
        val commonSources =
            arrayOf(
                kotlin(
                    "commonMain/src/pkg/JvmDefaultWithCompatibility.kt",
                    """
                    package pkg
                    internal expect annotation class JvmDefaultWithCompatibility()
                """
                ),
                kotlin(
                    "commonMain/src/pkg2/TestInterface.kt",
                    """
                    package pkg2

                    import pkg.JvmDefaultWithCompatibility

                    @JvmDefaultWithCompatibility()
                    interface TestInterface {
                      fun foo()
                    }
                """
                ),
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/JvmDefaultWithCompatibility.kt",
                """
                            package pkg
                            internal actual typealias JvmDefaultWithCompatibility = kotlin.jvm.JvmDefaultWithCompatibility
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, *commonSources),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(commonSources),
                ),
            api =
                """
                package pkg2 {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface TestInterface {
                    method public void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `JvmDefaultWithCompatibility as typealias actual using renamed import`() {
        val commonSources =
            arrayOf(
                kotlin(
                    "commonMain/src/pkg/JvmDefaultWithCompatibility.kt",
                    """
                    package pkg
                    internal expect annotation class JvmDefaultWithCompatibility()
                """
                ),
                kotlin(
                    "commonMain/src/pkg2/TestInterface.kt",
                    """
                    package pkg2

                    import pkg.JvmDefaultWithCompatibility

                    @JvmDefaultWithCompatibility
                    interface TestInterface {
                      fun foo()
                    }
                """
                ),
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/JvmDefaultWithCompatibility.kt",
                """
                            package pkg
                            import kotlin.jvm.JvmDefaultWithCompatibility as Compat
                            internal actual typealias JvmDefaultWithCompatibility = Compat
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, *commonSources),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(commonSources),
                ),
            api =
                """
                package pkg2 {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface TestInterface {
                    method public void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `JvmDefaultWithCompatibility as typealias actual using chained typealiases`() {
        val commonSources =
            arrayOf(
                kotlin(
                    "commonMain/src/pkg/JvmDefaultWithCompatibility.kt",
                    """
                    package pkg
                    internal expect annotation class JvmDefaultWithCompatibility()
                """
                ),
                kotlin(
                    "commonMain/src/pkg2/TestInterface.kt",
                    """
                    package pkg2

                    import pkg.JvmDefaultWithCompatibility

                    @JvmDefaultWithCompatibility
                    interface TestInterface {
                      fun foo()
                    }
                """
                ),
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/JvmDefaultWithCompatibility.kt",
                """
                            package pkg
                            private typealias Compat = kotlin.jvm.JvmDefaultWithCompatibility
                            internal actual typealias JvmDefaultWithCompatibility = Compat
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, *commonSources),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(commonSources),
                ),
            api =
                """
                package pkg2 {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface TestInterface {
                    method public void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `internal value class extension property`() {
        // b/385148821
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int)
                            internal var IntValue.isValid
                                get() = this.value != 0
                                set(newValue) = Unit
                        """
                    )
                ),
            api =
                """
            package test.pkg {
              @kotlin.jvm.JvmInline public final value class IntValue {
                ctor @KotlinOnly public IntValue(int value);
                method public int getValue();
                property public int value;
              }
            }
            """
        )
    }

    @Test
    fun `default parameter value from common, without jvm platform set for common`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    expect class Foo {
                        expect fun foo(i: Int = 0): Int
                    }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/JvmDefaultWithCompatibility.kt",
                """
                    package test.pkg
                    actual class Foo {
                        actual fun foo(i: Int) = i
                    }
                """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public int foo(optional int i);
                      }
                    }
                """
        )
    }

    @Test
    fun `default parameter value from common, with jvm platform set for common`() {
        // Verifies that expect/actual linking works when only the JVM platform is used.
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    expect class Foo {
                        expect fun foo(i: Int = 0): Int
                    }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/JvmDefaultWithCompatibility.kt",
                """
                    package test.pkg
                    actual class Foo {
                        actual fun foo(i: Int) = i
                    }
                """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createModuleDescription(
                        moduleName = "commonMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(commonSource),
                        dependsOn = emptyList(),
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public int foo(optional int i);
                      }
                    }
                """
        )
    }

    @Test
    fun `Vararg parameter followed by value class type parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        fun foo(vararg varargParam: String, valueParam: IntValue) = Unit
                        @JvmInline
                        value class IntValue(val value: Int)
                    """
                    )
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/32VeTTU/xrHv5iQrFmGn/olSxljFiNFlm62DM1YU7lkMMId" +
                        "xjKjZBlpmGTPliQyocjayJpShqgZkZ+QURnG70eE7DS6+t1z7q3Oredznj8+" +
                        "53zO+zmf5zzP+2VnIwSSA0RFRQEA2At8G3IACMBYOB2FobGWCMxRLNrSwtEJ" +
                        "jrHcfA4ACxjWi+M2MHivhA1Mq5v1kuGA/EP3PS8Ebo3RRmN6Q8tqHeasYcFa" +
                        "1iwW1HmuG9HVxRrjcXmCgJ2NiGiVLKTKYKvAoa20+2l58FaSvUlkRBDBB4Em" +
                        "kp09/EO94V7+HiRSlNNL0rCT9OZbhv+L++md2Bsd1hc3L77uTn0jUSz3KvZA" +
                        "jMO2OzFFiq+C7Hcl32ZmJ2tEhLMkNgUlXXHvW4tFcmLUzXxypImfMeJtJug6" +
                        "3ZTLSuyIwJUZSvPGjAmfPzsbDTQkIhQd8nnhax1HNQL1z3SV15XrK1LrAuvY" +
                        "kf1krbWlm1H3pttlPNKgzrg26a4JcZqvYVpOoaqkgUAKiDo+MwRv850XXDZq" +
                        "Szv5bjG3zvH6A0KyVhuv/Cp0mb7tPP58sXs95/69ZmfdFBnaoNBwTYvFkeT0" +
                        "BLlXYgeSUElkVW8+SwHvuo/A6K3Oc6MNTbdbgamXYyZOuD5paGUjj0s+//Rw" +
                        "NBaZJ0eDfglcLUNGJjdMz7UGQQwvCsgPBLkNvlUxZZ8tvuUp2iamE5lhxdEd" +
                        "aclar5RP17QP/yPFSSourFrrUgVErL/GWYead8mPVdaZIHssf69f1BDqHlFU" +
                        "L1svgc8ueGnsZefSjeU3BhZzMczYFbffYprqffSUXoFb4tcAF/IF6bsJhjmy" +
                        "+rPCGplyVvREl8aPDWcYJ5an2EhYo+zwS+QhXC1T0UgaJ+X3+7D4VON9sUyf" +
                        "10hzpYfBx7RJsY5GdyNODHfN1hPW+s+xg6yk1XKnXMtP5zcSwvdNhlXsLjzU" +
                        "xkyX3qH5XGCyYNiUDkReuxhtZPywci64Mmhm0a4wlXD/KU9zBv3Gq1aqr5vH" +
                        "qMrusJX6MywGllrk9dvAhb0hy2bH0zVgTWUEVNcVg9OOZL88+cGxx4+2EzNy" +
                        "m8r8zJ7mklkDEhmXS5LTsB+mHFzC1fotsiCybuleLp+nCR0WZU4m8mnTaec3" +
                        "Wm9LxssU7ndSV8iv/Zeh80Jpo+MDc8IHb4YL6kZdmhmvp8QnW+o1y1ITemPJ" +
                        "meFvfD8ubcjXIN+NYV1H6Jsxe1YaUeLjeLJ0LC9Xm36nHUHF0oPtmVaOqhBE" +
                        "fLHx4SVoJpj8fjXpdU5V5lzj7HtlFfT+KVoYuHBhGA/7iEIUBuwecwifFm2i" +
                        "xJ6s5chU/j66VMDvNE4TfztHwnEfxKkcahPHX32ynbi00TnSukgzLVC+Wb4k" +
                        "y6H0/ik9I3x9MnsIXNQz27fD1FZ98fSdKWb1ZF/0pehE3/H2RciqzFuXnv2P" +
                        "Zelycw8sCLuM5qVoQp06Dun/zKHpj6iVmQuuzGNF4euq5TtVCydlWSsoMY6s" +
                        "hyV3vJng+rChJ0qHfLIkpQapN6+c6S68DG91aDqVcASUhAQ3489INI6GH4Ea" +
                        "KSrBqCvVci7vVLFMN67GajYlibdJ0ajmGhYz3e89dr8J8RLaB6WuzB91j0JH" +
                        "cSOYxQUqFuH4Y1emixdcNeY1iw5fkeKv6+evTRR43lg2PmxcMqIy0B9gLh6J" +
                        "v+WLk7D9Kz5UaM9YRgASrDzI6bi7o7/dERwc7Ua+SpIJlSEFvxvg3D7P8+xu" +
                        "T0osTyQ8Culsn4v+6j7b6A7CBiAA+CDyK/dR+n/uY0P+j/8E2GMC+/4hHdWM" +
                        "9h+AVWS8qgK2vTF1BOlXNPBAVlnaxxPfaF2zsrpBVj4Y7I8PVE2R/wLaFKhP" +
                        "ECp7mrwnLidU0EIOQlk4t8yeHmenFrg/Fgij7aZZwHRnHs+e56u8xshI8KAB" +
                        "VJ0wY52dFHVuZfTgxy5J7x3iDB/0p/F2j43yaAefU6UPnkKHarAnOAEgQwyN" +
                        "jUQ3U2ynzmk55a2ppsjCaq3j96iv4xQtJKYO7iy1ag4UFZHcRVui0NXkN1p6" +
                        "W0/WZPtpq+oq6nU77WeMorRD33Zho9+oI7LU8YOVcV9SOA0mI1iSJKlh1FNh" +
                        "YXH2i+4RqiHKZLxC6VSrneQdUku2S54YzIopclvweqc4SxHw26AFNX2+Ja6W" +
                        "uDoJbxt+sVAWceFjSKqt6fVHa3mVIdcMH64Pqm2vYrQkPLtHbA85FTeQuPRU" +
                        "eDSfl/VX9zrlCfzls4IKuldE96pa/Ui67mbtGZ+JLGOa4Aztuo5CG8FG7+yX" +
                        "CduN3MJVlEGW+I7CBN6ToJ7sySu+xzJLY9HHzDFXtUj5++pTaDXOQkpMtSyI" +
                        "NX+ev9bv2dUwhs85N/4IpUfoy50o4iL2nntiz2Qy9DJxvRcysBIJeXT+XRQp" +
                        "Jg7RcdxfxkX9AF2EeW3/Tv25TwoHn9/t4ZsxRLHM7r5dwwbEW5bB7wZNESP+" +
                        "hdI4SGvIRfly4cVIvVYYhxjj3UBdUjiL4rCDENQMNf5zpY8+mt7VFIGvo2Zi" +
                        "mNqoLggAh4V+NWrKW/lfzgZ4+BHhhECyvx/RPSAQH+rv7YXD4c5uJcgTK6z5" +
                        "fucYVxO9xV1oJxbM/cSCbrdhQTBLcHvPHk/gb7j2lr8gQrcUkX/DVUBQDvhf" +
                        "1W/B+5Xu38fPWP+jyrcLBP5OIfrnyP5R5NvWKH0nwgf9YvN+lPn2t8rfyfiK" +
                        "/LKrdjbbhL8+A20dZQEA4Ih8vf0bs6I1lA0JAAA="
                ),
            api =
                """
                package test.pkg {
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method @BytecodeOnly public static test.pkg.IntValue! box-impl(int);
                    method @BytecodeOnly public static int constructor-impl(int);
                    method public int getValue();
                    method @BytecodeOnly public int unbox-impl();
                    property public int value;
                  }
                  public final class IntValueKt {
                    method public static void foo(String[] varargParam, int valueParam);
                    method @BytecodeOnly public static void foo-BObfkT0(String![], int);
                  }
                }
            """
        )
    }

    @Test
    fun `Data class with value class type`() {
        // For K2, no UElement created for a method using a value class type (b/388244267).
        // This will be resolved through b/406833486.
        val copyEntry =
            if (isK2) {
                "method @BytecodeOnly public test.pkg.IntValueData copy-Vxmw0xk(int);"
            } else {
                "method public test.pkg.IntValueData copy-Vxmw0xk(int intValue);"
            }
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val value: Int)
                        data class IntValueData(private val intValue: IntValue)
                        """
                    )
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/4WWeTgU6h7Hh4Nwh7GPQcmWnbGVXZoxZsiWJQ2DCYOxTUy2" +
                        "zI0sByFbhjCKxk5CEUKKsZNmhiOEZDllO8mWk1vnPs+9dZ57zn3f5/fH+zzv" +
                        "+/k97++Pz/O1tfyJTQjAyckJAACkAN8vIQAbwMrMwVQVZY1QtzK1RiHM7B3U" +
                        "rBBfBgCAj1ZDg+ctVdVe8liqKo0MjTZegNK15t6FqFlYqaCsXl6tarqwaaF6" +
                        "RcliaEjZaXNEvb9/6O27hXesAFvLY5wPBBUf6H1tcOZr2f5le/DXInqHEtUJ" +
                        "/j7qqCCiEzbgqreaZwA2NJTkMBz62oHv6Omy+iUniV/8XjlzjYF816LpQQrU" +
                        "jFP3UYqOBDFkihQPuOelrkXAL84N5bTpOfW5LnYOXteQGAhSk5jItZrWmYyd" +
                        "lO8P692ClkZurHXMvFn/Elb85cvuLROW6R4yUHGCOB3t2Zk1Edjs5eCCA6aa" +
                        "zmhNITrwk7cPp3Yol8I8OTWQwq+asZzeofqw+2uW5y/yxQkdMy+U+rCO5n2O" +
                        "3GLdkXie4fKGUJCLyX+IT1N6/q6GorxTsh/uFU51b6Y3VBngtBKxiSs/DT6e" +
                        "ZldPy7ohNK7pkAoFhkt7Px1aknM95V/6Erp2PDH/Qw8SHHcsdknI9Vle1zD0" +
                        "PHDgt/b5FmghX+LgUfBeKzQ6ruXDZhdB0SCGhX2CgJmcPXkuDUe968GJyhft" +
                        "QMmui42sh5BoQCTQPoqe7gBKiIAq36hFcTMeOkHTClMVhxT6UgTNKVLYfzI0" +
                        "q6wBOncYN38fLh408slw2Qn7PcGHOhcIT9jFiMW2tvmfhoyDnyZFSqKJkXwV" +
                        "NwzyhHU3PsvleMWbgRkhM2HTuOoPzAJT9AX5x2RsUJdvskbEya4T44aPITmh" +
                        "3sIKozV1NzUj3qY747jKW9zDq3OL3vhP/Ka8dico+yRCm/HEWQfjSSPwjG+h" +
                        "DeDBiclyaiK2OaTxzvp4OMseL8sRMYqAWfJ0Xfp1Nfa85EW5fEv+KcEmcz+b" +
                        "8RFq4wMwzRqEi4xV5SkvVZqIkCLueGqrJDD6UsosZGk+co7Mbs+aLkr7AbPT" +
                        "qmIgpYqXy39cw4YsC3I8fqpoikGlb0dV3VSyO4V2Tql62BgsCq68sO9wtgQ2" +
                        "u0Yi659ExFNsM50jpqoCC4Z9esMYQrW15DR8vy/6HG8OTLzObtZdRyxWJZju" +
                        "MtywPylwDvfUu81pWOVSulkzKLBtXazODENND8U3JFFEOoH6yb1mfHJWyffb" +
                        "T+bu9C0GZ4BWu7b0au1sIN1gSpxPDFtRhfgChm1kI+tRy2B7fGtkquXqSxnf" +
                        "KX43+1Dzlqj4M9e3pHfPakYMDsYmT4jETAM1M+Ac8i2MDZmrP70S544OIjYw" +
                        "rQmf60elxnk0Z1Qb1ZH33pSD4ynIuUYEIxn7quwL6xFv5tg8XXGPfzZ/4lQH" +
                        "+PLksn82UzTiUFKW1Gd3IcslI1F3RqYKzrq75QZQO5CuWZC+Vyk49Enzt5m3" +
                        "poiFxSfVru0tXSQpAln+loaJ3heDRfdjOyO0rDbnhjNsqVBwq5cbT8s82kR5" +
                        "WhKiEweqF3V+I+11BXydq6PyuGdlp/sVlWvWilsTv25c9BmYjOkfmOM3/lCt" +
                        "fi/tKMZ5zyceZtQgA99ZiKwTiJCwLeoydyNJjBp9HK03+6xWRBmry34/IHJO" +
                        "35hpR3+YUk58cSfdZnNVJC/uakAA9kXswlx+XF6zmm6fXpJukn7Lht/6y7YQ" +
                        "6HuPKVPmMx0bvcWT+5Lf7NNxI3Jbjw0AeH/s7+wj/r/sA8cSsf820G3HGXtx" +
                        "R6GjmeEJHFc9VzE7Gm3KeS8jppbJ6mhco8KBvtfLqtQoq6wi23YlPG5vAWq4" +
                        "bUNAvNl+Jn9iCpKQXnSFb/ZQ/dBEghYaTgahsL2Qlt3pxuXDkY3o6QKTo/21" +
                        "cEB4fffYou/jbLXgOqrw8/7KnpTTVH08W6rprG0znWinwxy/5XSmqV3QNEbK" +
                        "sEq89WOMVJ7DQQgw1stdLZFPWuAGHwQ+Q8N6WAAF8CV8fDLXfDDNEHTDgzTd" +
                        "7oy+AmqDn2+ilja02OU4WnGodZMbphKc2wnpCyR3jAQEazbZrVhiWxvKHuXO" +
                        "jhU6WjJ4DGrFElcr0oOyDH30Q29fzqxJzLYIe0VMac2cnVSiJjfhIHgNIyqp" +
                        "0illRPaSgZPcRh6zBh+IZyaRvbPh/dpa2hR5OmazULQsizx2IedDDz7dNfcc" +
                        "kEt7UzfQwYrmSlCZcS7N6O8IFaXkCvg9qSioWOm5gTMjwM7JbyA/Rm4voq71" +
                        "wqdLgqreNeCERDmyGHOMreiAksmEIN6jU7B2pZucURC8TR5ohRqSyZG2W6ME" +
                        "B5KQKGTJEWvCKzGMGTbGXtRrBWntFpg3nwjTIM7pk2dLuuuqdw7uX7qrUWvE" +
                        "TyqLrvcLedNrUDhppJS142imOmX48wTU9fJKB37Rd5bOKEHUpkTqBuUBYQqV" +
                        "CiRLjdR5FtD2Cb/zPpjGlVMOP88/By/BNPrHxopMxEdvZxkUA+OOgimVV+R5" +
                        "G++UirQPyuQ8Zt3WhnbA91lbe4lv4wOLipMe2Ype3ntPKHcRQLOjMkDhsgeh" +
                        "3hoWy2JTT9yIjJCiiO3zAUbTEBHNgJLT/LkJp6UEdQ8UVfqe5eP0hmxe0dpi" +
                        "35JTXx7N85K4U/gvFT5RM1+ViUwpmYu0kjQx8JHp19HiCUk1pvPeDMG41N/2" +
                        "ntzeCo/Nr3C1Qed7IUIKF9nIywVGeeXmbabgRNqiLVyL2KRMR0ptrdDEIQjI" +
                        "9EIyrxbln7BPOtRzKJ9bwlbA6zyieOsOUNC4C992WbVxtfiUK7Wp6pLNnsTm" +
                        "+p3IR3n2qkXFJO/P0T2E7awyUZfW2mz8YUCiLxwsodOpV51Ler9+k3nL0VLV" +
                        "j/7xqAX1D1zPgD/o6nro8G6/47xn0pLSyo7+M8mALPj4WFfmkr2+0d3ss205" +
                        "+me1GV+U8e5jqC579BOevmuZTNwo99WK5VKYgPnbCs0FYydwscIUrNLdtm2Z" +
                        "412dhs1qW+++INJjGpvXhZm8yCfy+hcWNKU4CiNC6JdncvbZNS3ckWyn2WjP" +
                        "8h/uXEsq7PatIkmunVjUrGWBpp9VT5C8nho2UvtRWB8MImht9RpHtSxbTs+a" +
                        "o9df0zaN1w07MyzKbX6VEEZvUJj3aA/iVkyfXnsoVLIxSpCUMb4tPLWv88Hq" +
                        "01GqLeHBEYjT5KTQ5HuTeOhgbFCbx0bbwBzl9X3vZrXYpL0zYjJE42qXDb+Z" +
                        "KI10Lu5PkH7+Ef8cbtreRtHoDkVSrxBv0kW/aV93xmhN0XYyGEA27oKFBxmH" +
                        "+y1usq+xH+xIzVury9w9awctnZPxQr2ouaFniOZo3BKVVC99hniaFimelcYu" +
                        "bnUaH2D4UOp10uu9F4jupdO7mIIr4uGYe3HM6yzfJGgqO+jU/lWCc5z/T4L/" +
                        "SYCBWL8gNf9gYoBfkHtgsNfVAG9PDw8P3Ndiu2zNoWB7eewy4I9490m6/ang" +
                        "15eif8Q7FlYhwH/p30e/b/nyx/VXafPPlO8VDv6BcP2vQ+OfId+PQPwHyO9s" +
                        "f+v+P4O+/++PIFbuv52frSU7x7drbF/3cRYAwI372+lfMSXvwZELAAA="
                ),
            api =
                """
                package test.pkg {
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method @BytecodeOnly public static test.pkg.IntValue! box-impl(int);
                    method @BytecodeOnly public static int constructor-impl(int);
                    method public int getValue();
                    method @BytecodeOnly public int unbox-impl();
                    property public int value;
                  }
                  public final class IntValueData {
                    ctor @BytecodeOnly public IntValueData(int, kotlin.jvm.internal.DefaultConstructorMarker!);
                    ctor @KotlinOnly public IntValueData(test.pkg.IntValue intValue);
                    $copyEntry
                  }
                }
                """
        )
    }

    @Test
    fun `Private property with defined getter of value class type`() {
        // b/388494377
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg.main
                        class Foo {
                            private var privateVar: IntValue
                                get() = IntValue(0)
                                set(newValue) = Unit
                        }
                        @JvmInline
                        internal value class IntValue(val value: Int)
                    """
                    ),
                ),
            api =
                """
                package test.pkg.main {
                  public final class Foo {
                    ctor public Foo();
                  }
                }
                """
        )
    }

    @Test
    fun `Repeatable annotation with expect actual`() {
        // b/399105459
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/AnnotationCanRepeat.kt",
                """
                    package test.pkg
                    @Repeatable
                    expect annotation class AnnotationCanRepeat(val value: Int)
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/AnnotationCanRepeat.android.kt",
                """
                    package test.pkg
                    @JvmRepeatable(AnnotationCanRepeat.Entries::class)
                    actual annotation class AnnotationCanRepeat
                    actual constructor(actual val value: Int) {
                        annotation class Entries(vararg val value: AnnotationCanRepeat)
                    }
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource))
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Repeatable(AnnotationCanRepeat.Entries::class) public @interface AnnotationCanRepeat {
                    ctor @KotlinOnly public AnnotationCanRepeat(int value);
                    method public abstract int value();
                    property public abstract int value;
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public static @interface AnnotationCanRepeat.Entries {
                    ctor @KotlinOnly public AnnotationCanRepeat.Entries(test.pkg.AnnotationCanRepeat... value);
                    method public abstract test.pkg.AnnotationCanRepeat[] value();
                    property public abstract test.pkg.AnnotationCanRepeat[] value;
                  }
                }
                """
        )
    }

    @Test
    fun `Data class value with type argument`() {
        // Added to test that the nullability of the type argument in the copy method is correct.
        // A K2 update to the source psi for the copy method changed how the kotlin context for the
        // method parameters is computed.
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        data class Foo<T: Any>(val items: List<T>)
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo<T> {
                    ctor public Foo(java.util.List<? extends T> items);
                    method public java.util.List<T> component1();
                    method public test.pkg.Foo<T> copy(optional java.util.List<? extends T> items);
                    method public java.util.List<T> getItems();
                    property public java.util.List<T> items;
                  }
                }
                """
        )
    }

    @Test
    fun `Annotations on property of value class type`() {
        // b/417181888 -- the accessor representation depends on if the type is specified in source
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        annotation class Anno
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg
                        @JvmInline value class IntValue(val value: Int) {
                            companion object {
                                @Anno val withValueClassTypeSpecified: IntValue = IntValue(0)
                                @Anno val withValueClassTypeUnspecified = IntValue(0)
                                @Anno val withNonValueClassTypeSpecified: Int = 0
                            }
                        }
                        """
                    ),
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Anno {
                  }
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method public int getValue();
                    property public int value;
                    field public static final test.pkg.IntValue.Companion Companion;
                  }
                  public static final class IntValue.Companion {
                    method public int getWithNonValueClassTypeSpecified();
                    property @test.pkg.Anno public int withNonValueClassTypeSpecified;
                    property @test.pkg.Anno public test.pkg.IntValue withValueClassTypeSpecified;
                    property @test.pkg.Anno public test.pkg.IntValue withValueClassTypeUnspecified;
                  }
                }
                """
        )
    }

    @Test
    fun `Inherited internal constructor property-parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        open class ParentClass(internal open val internalCtorVal: Int) {
                            internal open fun internalFun() = Unit
                            internal open val internalVal: Int = 0
                        }
                        class ChildClass(override val internalCtorVal: Int): ParentClass(internalCtorVal) {
                            override fun internalFun() = Unit
                            override val internalVal: Int = 0
                        }
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class ChildClass extends test.pkg.ParentClass {
                    ctor public ChildClass(int internalCtorVal);
                  }
                  public class ParentClass {
                    ctor public ParentClass(int internalCtorVal);
                  }
                }
                """
        )
    }

    @Test
    fun `Annotation on generated no-args constructor`() {
        // b/417687416 the annotation is dropped from the no-args constructor with K2
        val noArgsAnnotation = if (isK2) "" else "@test.pkg.Anno "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        annotation class Anno
                        class Foo @Anno constructor(i: Int = 0, s: String = "")
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Anno {
                  }
                  public final class Foo {
                    ctor ${noArgsAnnotation}public Foo();
                    ctor @test.pkg.Anno public Foo(optional int i, optional String s);
                  }
                }
                """
        )
    }

    @Test
    fun `JvmMultifileClass with files in common and android`() {
        // b/417699607 the android method is dropped from K2 tracking
        val androidMethod = if (isK2) "" else "method public static void fooAndroid();"
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("Foo")
                package test.pkg
                fun fooCommon() = Unit
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.android.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("Foo")
                package test.pkg
                fun fooAndroid() = Unit
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    // Set the common module to only list the jvm platform, since it requires jvm
                    // annotations.
                    createModuleDescription(
                        moduleName = "commonMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(commonSource),
                        dependsOn = emptyList(),
                    ),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    $androidMethod
                    method public static void fooCommon();
                  }
                }
                """
                    .stripBlankLines()
        )
    }

    @Test
    fun `Test mapped collections methods`() {
        val maybeExtends = if (isK2) "" else "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class EmptyStringList: List<String> {
                            override val size = 0
                            override fun get(index: Int) = error("empty")
                            override fun indexOf(element: String) = -1
                            override fun contains(element: String) = false
                            override fun containsAll(elements: Collection<String>) = false
                            override fun isEmpty() = true
                            override fun iterator() = error("empty")
                            override fun listIterator() = error("empty")
                            override fun listIterator(index: Int) = error("empty")
                            override fun subList(fromIndex: Int, toIndex: Int) = this
                            override fun lastIndexOf(element: String): Int = -1
                        }
                        """,
                    )
                ),
            // Compiled from the source above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/4WWezgT+h/Hl8I4UrkU6cJGmsx9lZAc1xGWudTMnbmNjW1I" +
                        "Uwfhl3KPcq3VInfFkvtxXdrILXJJLsWWmDCHuXR0/vj96jzPOb/P93k/3+f7" +
                        "PN/v6/P5/vdCWOzeIwEAAoEAAEAO8GNJAPYALI1tDaBwKxNVSwMruIkx0lbF" +
                        "0mT7NQCwbMlkXLSAqvTutYAqdTPfVNqoDWhOfApWMbdUhlv2koqqbBbNoUFK" +
                        "5kzmKfvFbtXOTub0p6lPfACEhSCwXBxSrr3T4MxOEP/Y/thOiF4Eoire31vV" +
                        "OABPvIokBvsGel/0JRBVPLBuBEKp/YaDDF1yG8M/kr4fk5IQRSENtInyud98" +
                        "9l5e1mPa1O8AFmvog+2jg6BhBiN2oQ4DVseD+kPTTrMZAf2hvDWIPnV25d35" +
                        "Lu293KStx+HB3iE01rOiyDVgyEL3GCd7I0tn48O3rbkPAKsJjshW2cLL4dcd" +
                        "qNVfa6bGnfEBrehszSMOAh49M4M0S6hjH5P2ZfN1rTO+oD6zyP/uczTUoWAy" +
                        "siC/YOa0crXn/WFuV/CgmtP70zSbFyXDMrXzni61VDul8K+pYbnuiOzq5N7m" +
                        "ivIgGelLsZNH+18jpB5pt2qN+caaYcIgkWdrp1hGUZTWBt+zIrsJ82TF4o+w" +
                        "905qzv3856ylTOsryHzwL0kGLbtQIIXYrcxNvJNEhvJjsz3Wb66sokKcQh2w" +
                        "XWq5JZisjOKaWOMlL0YDGppuNtcZgG607Ruq6uODsA8FSq4ID6UHJVOQszMQ" +
                        "KU8EmqGZNnkB8xK2KkQ+HPcy21pjxQs11HCBfCJxl65xW+yVAVxKpXuOS0C0" +
                        "3B9eDdR7pH47tTb8SeV9V9P8dUyaU9QvGsVkh7u1hq7EY/y9ciWkYzXmzYuG" +
                        "mZd0ZejARA15R8mTYvwM4lXPUamIaEpxvgZSSf0IcyB9Qfv2I6eJAwezPCb9" +
                        "a3rtPQr4L3ydnjx5tjNZ2HsmkmTke0tpSi066mHNQX5QphAky+hWkepx1isr" +
                        "UswQOEY3rQC0oc897EgrDR63LvBft3GAgxtRwWDUMzPpd+Ncr/5Ce6WM+c/t" +
                        "68If7paUnWogiNCj16smqYVR/btADX7Dvo5D8abQSqKOE3DTHi+XuFzfaJNJ" +
                        "vhzI9klVAEF2wxIqSjxHlU9TFXzO6XR57H5YieiDxRMHBhmD+eO2Ftdf17g/" +
                        "J2VFLVBuKkYkOMk+7c9fB8hUc83hp8ImwNyQcntLN9gT00rcNaEK7IHqQ8dK" +
                        "D40ka9sX3dHU5bIibjpuvgMyQKK4IEPKLRFCdStlykcb+yn+xXzb5TXUgo8O" +
                        "AzmJFnrKSkAqbgfzQXSbn6SfFmKeQWseqxHRiwSHxU4PiknR1VWk6/3i5k6X" +
                        "5JqMBIoh0TGPUrFO99yWgsviut8exUTN6q45+MThj0oPMAWiw4Vm918PXFp9" +
                        "gLskH4O8PyNcLX7/K3oDtaxe0u5ZqF1Ki6nwe5Kh4TF6GPb8IkZ7j7gGPbkb" +
                        "ahWr1cNcmgLJsQxmradLhMdl41qsyu5wetjAOtwL0kG9fk9yklutZdoHSpdQ" +
                        "feLxtq8Rm8llXMQDnsZkY+KLOqeRL0sejQxrqXD/D+b3WExJmANYpbBemsLR" +
                        "7Diuvg2ICMP2bEKjm3rVM+LpUnPvazUP3uM9eUnNZ99wQdu8LOmRojxjHN/X" +
                        "Xr3Q8zsLNthE8v4VHiL/JMg3J3eevOZsawOubCjSkE2gn3jkl2PCnmqt68Oa" +
                        "z4sRhGDqCEOiy8WUhfNZHDmn9A4rSnFRA1p0sfCKsWWwFlydpD/0B1e0isW9" +
                        "9VailAtbzMdsvJWAe+JtI1riuNcshkSfHkWHf5p8v/wx6ldRUeLJS3YzkZmu" +
                        "uujOfJlgbHSWDV272Aza6N2txmW8XYoPVPU7pZB7JrT4YXqG1ugEZ/vjBK2I" +
                        "fdhLDxd4ONEAqTnqgheJy3aIxuUI0N5FPcP4Bb490RLIqzqF7fEuajIgNxeN" +
                        "8G8ZRzcdWVnPQK22qJ9rz11RBXfoTX94YMtvKN+vCNW7YY274gzSCkkcM/1D" +
                        "3yx5UTDcpJ16U79PcxLiejzcyPjSMnbXN/EIzplIMMX1czW1vVmTKtf7bktO" +
                        "oExRn7SPvRHGeyyQKsoLSr1bwErDs9VqWhYVXcpdEEiL1UKCVvBNjSym7u/3" +
                        "B3xQd5nDGDs+e56R5LGtTM9M/qppUKJcVQpmhBXoo4jP4esx2hhWQ9XsnYfr" +
                        "ms5xhObFu1beTPPrw9/w4KnnsjSpA15mwhpG99I5HIKn7DXbWHeSPE0ENqNQ" +
                        "rdOyJgoMtTN0qnsqV8sf8VlzpSM7X2vJAPlOLEp1vlSALGb1y3mVXDm4n3ve" +
                        "cBAphJN/p9uGFH41b9HjwLf9s+WKLvVJbyLktV50Tu26MqY4vXb7/HnTNTOZ" +
                        "+lms3nYFp4Qun5JDx05MjN8PM7C4btAkL5W0caduuXTblZlChLBCpJsArWFi" +
                        "jZI0Al56+dqdL5IHhouTmnniLYn+154bttnlym22jH9dcWxjXFuaXg5TWPS4" +
                        "RZ1Lghw7U7F5X2nVjuyWlBcBqWDdvjvr+SombB+KULe3xhKiVyirl3bd5T+J" +
                        "IuOGlJo5M1KH0W6PR42ODuzVUzzd5fPWwSv6lyMO5fZtgB87Xm6Iyx57BNwG" +
                        "5SkPxupKJytuoRrNQyfIMktmCeQI3EeJSDh2D//nGGvKYnq1awxddtLFmQac" +
                        "QQg1jzriQ+PLeWvIRoJ6XmT5C4VF9C3+sraoyhDhanLRbdrIEWmqP5sGfTWJ" +
                        "LiWFn3uK5ByuMXegg5EbXD3Tb18UfQl83xKsyUfKL4DyZdZmpl1B66Qxocaz" +
                        "9b9x75xosgydf3WwUYcH0Oc97HyW91uZIG6ia4J4wyK54VM29yLC2WgZgMNV" +
                        "at2VkGZRwGvFUjfimnpfws9xepq/sTVAVzeVkx7Mfv1lM9VorGvaYQ1Ggwk2" +
                        "NdcxzrIe0ptx7S73oqn7bQUbRDtidD+6tjTD5QraliuiwaJ5oWps1w6D7maI" +
                        "YMHRnHWp77owmmRdMy0IAFQd+jddkNnJf20lwM03UMUfR8T6BroE4DxJWC8P" +
                        "V1dXzE72uFsJnES497gD/lIRLqi+QXznpdRfKrKLTwLwP/qPmvLdhX6ufzKj" +
                        "v1N+nP7YT4Qb/1dw/s76cVqZn1hlwH/9PcKCX+D7td07a3RnFxb6fvoTwPLm" +
                        "GvsJAAA="
                ),
            // The mapped collection APIs shouldn't be bytecode only, but it is better that they are
            // tracked that way than not tracked at all.
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class EmptyStringList implements kotlin.jvm.internal.markers.KMappedMarker java.util.List<java.lang.String> {
                    ctor public EmptyStringList();
                    method @BytecodeOnly public void add(int, String!);
                    method @BytecodeOnly public boolean add(String!);
                    method @BytecodeOnly public boolean addAll(int, java.util.Collection<? extends java.lang.String!>!);
                    method @BytecodeOnly public boolean addAll(java.util.Collection<? extends java.lang.String!>!);
                    method @BytecodeOnly public void clear();
                    method @BytecodeOnly public boolean contains(Object!);
                    method public boolean contains(String element);
                    method public boolean containsAll(java.util.Collection<${maybeExtends}java.lang.String> elements);
                    method public Void get(int index);
                    method public int getSize();
                    method @BytecodeOnly public int indexOf(Object!);
                    method public int indexOf(String element);
                    method public boolean isEmpty();
                    method public Void iterator();
                    method @BytecodeOnly public int lastIndexOf(Object!);
                    method public int lastIndexOf(String element);
                    method public Void listIterator();
                    method public Void listIterator(int index);
                    method @BytecodeOnly public String! remove(int);
                    method @BytecodeOnly public boolean remove(Object!);
                    method @BytecodeOnly public boolean removeAll(java.util.Collection<? extends java.lang.Object!>!);
                    method @BytecodeOnly public void replaceAll(java.util.function.UnaryOperator<java.lang.String!>!);
                    method @BytecodeOnly public boolean retainAll(java.util.Collection<? extends java.lang.Object!>!);
                    method @BytecodeOnly public String! set(int, String!);
                    method @BytecodeOnly public int size();
                    method @BytecodeOnly public void sort(java.util.Comparator<? super java.lang.String!>!);
                    method public test.pkg.EmptyStringList subList(int fromIndex, int toIndex);
                    method @BytecodeOnly public Object![]! toArray();
                    method @BytecodeOnly public <T> T![]! toArray(T![]!);
                    property public int size;
                  }
                }
                """
        )
    }

    @Test
    fun `Inner class with different number of type parameters than outer class`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Outer<T> {
                            inner class Middle<K, V> {
                                inner class Inner<A, B, C>
                            }
                        }
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Outer<T> {
                    ctor public Outer();
                  }
                  public final class Outer.Middle<K, V> {
                    ctor public Outer.Middle();
                  }
                  public final class Outer.Middle.Inner<A, B, C> {
                    ctor public Outer.Middle.Inner();
                  }
                }
                """
        )
    }
}
