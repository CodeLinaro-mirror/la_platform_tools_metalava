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

package com.android.tools.metalava.model.testsuite.propertyitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.memberitem.CommonCopyInheritableItemTest
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Common tests for [PropertyItem.duplicate]. */
@SupportedInputFormats(InputFormat.KOTLIN)
class CommonCopyPropertyItemTest : CommonCopyInheritableItemTest<PropertyItem>() {

    override fun getMember(sourceClassItem: ClassItem) = sourceClassItem.assertProperty("property")

    override fun copyMember(sourceMemberItem: PropertyItem, targetClassItem: ClassItem) =
        sourceMemberItem.duplicate(targetClassItem)

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `test duplicate creates item in same codebase as target class`() {
        checkDuplicateUsesTargetCodebase(
            sourceFile =
                kotlin(
                    """
                        package test.pkg
                        class Source {
                            val property: Int = 0
                        }
                    """
                ),
            targetFile =
                kotlin(
                    """
                        package test.pkg
                        class Target {
                        }
                    """
                ),
        )
    }
}
