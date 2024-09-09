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

package com.android.tools.metalava.model.testsuite.memberitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.testsuite.BaseModelTest

/**
 * Base class for tests for [MemberItem.duplicate] and [ClassItem.inheritMethodFromNonApiAncestor].
 */
abstract class CommonCopyMemberItemTest<M : MemberItem> : BaseModelTest() {

    /** Check to see if this test supports the [inputFormat]. */
    protected open fun supportsInputFormat() = true

    /** Get the [MemberItem] that will be copied. */
    protected abstract fun getMember(sourceClassItem: ClassItem): M

    /** Copy the [MemberItem]. */
    protected abstract fun copyMember(sourceMemberItem: M, targetClassItem: ClassItem): M

    protected inner class CopyContext(
        codebaseContext: CodebaseContext,
        val sourceClassItem: ClassItem,
        val targetClassItem: ClassItem,
        val sourceMemberItem: M,
        val copiedMemberItem: M,
    ) : CodebaseContext by codebaseContext

    protected fun runCopyTest(
        vararg inputs: InputSet,
        test: CopyContext.() -> Unit,
    ) {
        // If the copy method does not support the current input format then just return.
        if (!supportsInputFormat()) return

        runCodebaseTest(
            *inputs,
        ) {
            val sourceClassItem = codebase.assertClass("test.pkg.Source")
            val targetClassItem = codebase.assertClass("test.pkg.Target")

            val sourceMemberItem = getMember(sourceClassItem)
            val targetMemberItem = copyMember(sourceMemberItem, targetClassItem)

            val context =
                CopyContext(
                    this,
                    sourceClassItem,
                    targetClassItem,
                    sourceMemberItem,
                    targetMemberItem,
                )

            context.test()
        }
    }
}
