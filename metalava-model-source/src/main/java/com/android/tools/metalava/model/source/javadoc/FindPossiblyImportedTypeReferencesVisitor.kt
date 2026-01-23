/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.BlockTagSection
import com.android.tools.metalava.model.source.doc.DocComment
import com.android.tools.metalava.model.source.doc.DocCommentPredicate
import com.android.tools.metalava.model.source.doc.LabeledRefTagData
import com.android.tools.metalava.model.source.doc.ParamTagData
import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.ThrowsTagData
import com.android.tools.metalava.model.source.doc.containsWord

/**
 * A [DocCommentPredicate] that will search [DocComment] for any references to [importedTypeName].
 *
 * This is used to see if there are any references to [importedTypeName] in the documentation that
 * may need the imported name to fully resolve the reference. If there are then this returns `true`
 * and the import is kept.
 *
 * This is needed because Metalava does not currently resolve all Javadoc references correctly and
 * relies on doclava resolving the remainder.
 *
 * TODO(b/447588621): Remove once all references in Javadoc are fully resolved.
 */
internal class FindPossiblyImportedTypeReferencesVisitor(private val importedTypeName: String) :
    DocCommentPredicate {
    override fun visit(list: JavadocContentList) = list.contents.any { it.accept(this) }

    /**
     * Checks to see whether this [TagData] requires an import of [importedTypeName] to be kept.
     *
     * Does not treat inline and block tags separately to avoid errors in documentation that uses
     * inline tags as block tags or vice versa.
     */
    private fun TagData?.requiresImportBeKept() =
        when (this) {
            null -> false
            // @link, @linkplain and @see tags may contain references to imported types.
            // Only keep the imported name if the reference was not fully resolved.
            is LabeledRefTagData ->
                wasReferenceFullyResolved() && sourceReference.containsWord(importedTypeName)
            // Parameter names should not cause an import of the same name to be kept as they are
            // not resolved against imported names.
            is ParamTagData -> false
            // Throwable classes are fully resolved by Metalava so should not cause the imported
            // name to be kept.
            is ThrowsTagData -> false
            else -> error("unknown tag data $this")
        }

    /**
     * Checks to see whether this [DocTag] requires an import of [importedTypeName] to be kept.
     *
     * Checks the [DocTag.tagData] and [DocTag.content].
     */
    private fun DocTag.requiresImportBeKept() =
        // Check tag data for any references that may have been removed from the tag description and
        // stored in the tag data.
        tagData.requiresImportBeKept()
        // Check the remaining content for any references to imported types.
        || content?.accept(this@FindPossiblyImportedTypeReferencesVisitor) == true

    override fun visit(inlineTag: JavadocInlineTag) = inlineTag.requiresImportBeKept()

    override fun visit(text: JavadocText) =
        // Use of an imported name in the text does not keep the corresponding import as the name is
        // never resolved against the import.
        false

    override fun visit(blockTagSection: BlockTagSection) = blockTagSection.requiresImportBeKept()
}
