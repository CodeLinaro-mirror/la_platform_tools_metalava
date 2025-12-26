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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentPredicate

/**
 * A component of a Javadoc comment.
 *
 * This represents a block of text and inline tags in a Javadoc comment. It can either be in the
 * main description for the item or the description of a block tag in the item.
 */
internal sealed interface JavadocContent : DocContent {
    /** Add this to [list], flattening if this is a [JavadocContentList]. */
    fun flattenTo(list: MutableList<JavadocContent>) {
        list.add(this)
    }

    /**
     * Call type specific method in [JavadocContentVisitor] corresponding to the implement of this.
     */
    fun <R> accept(visitor: JavadocContentVisitor<R>): R

    /** A specialized [accept] for handling [JavadocContentVisitor]s that return a [Boolean]. */
    fun matches(predicate: JavadocContentVisitor<Boolean>): Boolean = accept(predicate)

    /** Implements [DocContent.check]. */
    override fun check(predicate: DocContentPredicate) =
        matches(predicate as JavadocContentPredicate)
}

/** Visitor of [JavadocContent] subclasses. */
internal interface JavadocContentVisitor<R> {
    fun visit(list: JavadocContentList): R

    fun visit(inlineTag: JavadocInlineTag): R

    fun visit(text: JavadocText): R
}

/** A specialised [JavadocContentVisitor] that can be treated as a predicate on [JavadocContent]. */
internal interface JavadocContentPredicate : JavadocContentVisitor<Boolean>, DocContentPredicate

/**
 * Convert this [List] to an optional [JavadocContent].
 *
 * If this is empty then returns null, if there is a single [JavadocContent] then just return it,
 * otherwise create a [JavadocContentList] wrapper around this.
 */
internal fun List<JavadocContent>.toOptionalJavadocContent() =
    when (size) {
        0 -> null
        1 -> this[0]
        else -> JavadocContentList(this)
    }

/**
 * Convert this [String] to an optional [JavadocContent].
 *
 * If this is empty then returns null, otherwise create a [JavadocText] wrapper around this.
 */
internal fun String.toOptionalJavadocContent() = if (isEmpty()) null else JavadocText(this)

/**
 * A [JavadocContent] that encapsulates multiple [JavadocContent] instances.
 *
 * @param contents a list containing multiple [JavadocContent] instances.
 */
internal class JavadocContentList(val contents: List<JavadocContent>) : JavadocContent {
    init {
        require(contents.size > 1) { "contents list must contain more than one item" }
    }

    /** Visit the contents of this in turn. */
    fun <R> visitContents(visitor: JavadocContentVisitor<R>) {
        contents.forEach { content -> content.accept(visitor) }
    }

    /** Flatten this by adding all of [contents] to [list]. */
    override fun flattenTo(list: MutableList<JavadocContent>) {
        list.addAll(contents)
    }

    override fun <R> accept(visitor: JavadocContentVisitor<R>) = visitor.visit(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavadocContentList

        return contents == other.contents
    }

    override fun hashCode() = contents.hashCode()

    override fun toString() = buildString {
        append("JavadocContentList(")
        contents.joinTo(this)
        append(")")
    }
}

/**
 * A wrapper around [contents] that will ensure that any [JavadocContent] instances are flattened
 * into the list.
 */
@JvmInline
internal value class ConcatJavadocContent(private val contents: MutableList<JavadocContent>) {
    /**
     * Adds [content] to [contents], flattening if needed.
     *
     * If [content] is a [JavadocContentList] then it will not be added to [contents], instead each
     * [JavadocContent] in its [JavadocContentList.contents] list will be added individually. This
     * helps keep the [JavadocContent] hierarchy shallow and easier to understand.
     */
    fun add(content: JavadocContent) {
        content.flattenTo(contents)
    }
}

/**
 * Builder of [JavadocContent] that concatenates a number of [JavadocContent].
 *
 * The result will be a [JavadocContent] instance, or null if none were added. If only a single
 * [JavadocContent] was added then it will be returned directly. Otherwise, it will return a flat
 * [JavadocContentList] (see [ConcatJavadocContent.add]).
 */
internal inline fun concatJavadocContent(
    builderAction: ConcatJavadocContent.() -> Unit
): JavadocContent? {
    val contents = mutableListOf<JavadocContent>()
    ConcatJavadocContent(contents).builderAction()
    return contents.toOptionalJavadocContent()
}
