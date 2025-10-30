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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.concatJavadocContent
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Base class for classes that own a [JavadocContent] description.
 *
 * @param descriptionSupplier Supplies a [JavadocContent] instance when requested. May produce it
 *   lazily.
 */
internal open class DescriptionOwner(
    val context: DocCommentContext,
    protected val descriptionSupplier: ContentSupplier,
) : DocContentOwner {
    /**
     * A mutable and optional [JavadocContent] that is initialized lazily from [descriptionSupplier]
     * in [initializeDescription].
     */
    private lateinit var _description: Optional<JavadocContent>

    /**
     * Provides access to the [JavadocContent] in [_description].
     *
     * If [_description] is not initialized then this will initialize it from the
     * [descriptionSupplier]. It may need to do a lot of work to produce the [JavadocContent] so
     * this must only be accessed when absolutely necessary and only when processing actual API
     * documentation.
     */
    val description: JavadocContent?
        get() {
            ensureDescriptionIsInitialized()
            return _description.getOrNull()
        }

    /**
     * Ensure that [_description] is initialized from [descriptionSupplier].
     *
     * This can be called by subclasses to ensure that [initializeDescription] has been called
     * without retrieving [description].
     */
    protected fun ensureDescriptionIsInitialized() {
        if (!::_description.isInitialized) {
            initializeDescription(descriptionSupplier.content)
        }
    }

    /**
     * Initialize [_description] from [suppliedDescription].
     *
     * This can be overridden by subclasses to customize the initialization.
     */
    protected open fun initializeDescription(suppliedDescription: JavadocContent?) {
        _description = Optional.ofNullable(suppliedDescription)
    }

    override val docContent: DocContent?
        get() = description

    /**
     * Update [description] to [new].
     *
     * If [new] is the same as [description] then does nothing. Otherwise, it sets [_description] to
     * [new] and notifies any listener that the containing [DocComment] has changed.
     */
    private fun updateDescription(new: JavadocContent?) {
        if (new !== description) {
            _description = Optional.ofNullable(new)

            // Notify any listener.
            context.mutationListener.docCommentMutated()
        }
    }

    override fun append(other: DocContent) {
        updateDescription(description.append(other as JavadocContent))
    }

    override fun append(text: String) {
        val supplier = LazyContentSupplier(context, DocumentationIssueReporter.THROWING, text)
        val content = supplier.content ?: return
        updateDescription(description.append(content))
    }

    /**
     * Append [other] to [this] optional [JavadocContent] and return the result.
     *
     * If [this] is null then just returns [other], else joins [this], a [BR_SEPARATOR] and [other]
     * into a single [JavadocContent].
     */
    fun JavadocContent?.append(other: JavadocContent) =
        this?.let {
            concatJavadocContent {
                add(it)
                add(BR_SEPARATOR)
                add(other)
            }
        } ?: other

    companion object {
        /**
         * The `<br>` separator inserted between existing content and the appended content in
         * [append].
         *
         * It has this specific form to ensure that it appears on its own line with the correct
         * indentation and the appended text starts on the line after that also with the correct
         * indentation.
         */
        private val BR_SEPARATOR = JavadocText("\n <br>\n ")
    }
}
