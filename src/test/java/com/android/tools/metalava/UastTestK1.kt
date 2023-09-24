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

import org.junit.Test

class UastTestK1 : UastTestBase() {

    @Test
    fun `Test RequiresOptIn and OptIn -- K1`() {
        `Test RequiresOptIn and OptIn`(isK2 = false)
    }

    @Test
    fun `renamed via @JvmName -- K1`() {
        `renamed via @JvmName`(
            isK2 = false,
            api =
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
                    property public final int[] colors;
                    property public final boolean initiallyEnabled;
                    property public final boolean interpolated;
                    property public final int[] otherColors;
                  }
                }
            """
        )
    }

    @Test
    fun `Kotlin Reified Methods -- K1`() {
        `Kotlin Reified Methods`(isK2 = false)
    }

    @Test
    fun `Annotation on parameters of data class synthetic copy -- K1`() {
        `Annotation on parameters of data class synthetic copy`(isK2 = false)
    }

    @Test
    fun `declarations with value class in its signature -- K1`() {
        `declarations with value class in its signature`(isK2 = false)
    }

    @Test
    fun `non-last vararg type -- K1`() {
        `non-last vararg type`(isK2 = false)
    }

    @Test
    fun `implements Comparator -- K1`() {
        `implements Comparator`(isK2 = false)
    }

    @Test
    fun `constant in file-level annotation -- K1`() {
        `constant in file-level annotation`(isK2 = false)
    }

    @Test
    fun `final modifier in enum members -- K1`() {
        `final modifier in enum members`(isK2 = false)
    }

    @Test
    fun `lateinit var as mutable bare field -- K1`() {
        `lateinit var as mutable bare field`(isK2 = false)
    }

    @Test
    fun `Upper bound wildcards -- K1`() {
        `Upper bound wildcards`(isK2 = false)
    }

    @Test
    fun `boxed type argument as method return type -- K1`() {
        `boxed type argument as method return type`(isK2 = false)
    }
}
