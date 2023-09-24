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

class UastTestK2 : UastTestBase() {

    @Test
    fun `Test RequiresOptIn and OptIn -- K2`() {
        `Test RequiresOptIn and OptIn`(isK2 = true)
    }

    @Test
    fun `renamed via @JvmName -- K2`() {
        // NB: getInterpolated -> isInterpolated
        `renamed via @JvmName`(
            isK2 = true,
            api =
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
    fun `Kotlin Reified Methods -- K2`() {
        `Kotlin Reified Methods`(isK2 = true)
    }

    @Test
    fun `Annotation on parameters of data class synthetic copy -- K2`() {
        `Annotation on parameters of data class synthetic copy`(isK2 = true)
    }

    @Test
    fun `declarations with value class in its signature -- K2`() {
        `declarations with value class in its signature`(isK2 = true)
    }

    @Test
    fun `non-last vararg type -- K2`() {
        `non-last vararg type`(isK2 = true)
    }

    @Test
    fun `implements Comparator -- K2`() {
        `implements Comparator`(isK2 = true)
    }

    @Test
    fun `constant in file-level annotation -- K2`() {
        `constant in file-level annotation`(isK2 = true)
    }

    @Test
    fun `final modifier in enum members -- K2`() {
        `final modifier in enum members`(isK2 = true)
    }

    @Test
    fun `lateinit var as mutable bare field -- K2`() {
        `lateinit var as mutable bare field`(isK2 = true)
    }

    @Test
    fun `Upper bound wildcards -- K2`() {
        `Upper bound wildcards`(isK2 = true)
    }

    @Test
    fun `boxed type argument as method return type -- K2`() {
        `boxed type argument as method return type`(isK2 = true)
    }
}
