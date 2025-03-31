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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.reporter.FileLocation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.model.Const
import com.google.turbine.model.Const.ArrayInitValue
import com.google.turbine.model.Const.Kind
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.ArrayInit
import com.google.turbine.tree.Tree.Assign
import com.google.turbine.tree.Tree.Expression
import com.google.turbine.tree.Tree.Literal
import com.google.turbine.type.AnnoInfo

/**
 * Factory for creating [AnnotationItem]s from [AnnoInfo]s.
 *
 * @param codebase the [Codebase] to which the [AnnotationItem] will belong.
 * @param sourceFileCache provides mapping from [AnnoInfo.source] to location.
 */
internal class TurbineAnnotationFactory(
    private val codebase: Codebase,
    private val sourceFileCache: TurbineSourceFileCache,
) {
    /** Creates a list of AnnotationItems from given list of Turbine Annotations */
    internal fun createAnnotations(annotations: List<AnnoInfo>): List<AnnotationItem> {
        return annotations.mapNotNull { createAnnotation(it) }
    }

    /** Create an [AnnotationItem] from an [AnnoInfo]. */
    private fun createAnnotation(annotation: AnnoInfo): AnnotationItem? {
        // Get the source representation of the annotation. This will be null for an annotation
        // loaded from a class file.
        val tree: Tree.Anno? = annotation.tree()
        // An annotation that has no definition in scope has a null sym, in that case fall back
        // to use the name used in the source. The sym can only be null in sources, so if sym is
        // null then tree cannot be null.
        val qualifiedName = annotation.sym()?.qualifiedName ?: tree!!.name().dotSeparatedName

        val fileLocation =
            annotation
                .source()
                ?.let { sourceFile -> sourceFileCache.turbineSourceFile(sourceFile) }
                ?.let { sourceFile -> TurbineFileLocation.forTree(sourceFile, tree) }
                ?: FileLocation.UNKNOWN

        return DefaultAnnotationItem.create(codebase, fileLocation, qualifiedName) {
            getAnnotationAttributes(annotation.values(), tree?.args())
        }
    }

    /** Creates a list of AnnotationAttribute from the map of name-value attribute pairs */
    private fun getAnnotationAttributes(
        attrs: ImmutableMap<String, Const>,
        exprs: ImmutableList<Expression>?
    ): List<AnnotationAttribute> {
        val attributes = mutableListOf<AnnotationAttribute>()
        if (exprs != null) {
            for (exp in exprs) {
                when (exp.kind()) {
                    Tree.Kind.ASSIGN -> {
                        exp as Assign
                        val name = exp.name().value()
                        val assignExp = exp.expr()
                        attributes.add(
                            DefaultAnnotationAttribute(
                                name,
                                createAttrValue(attrs[name]!!, assignExp)
                            )
                        )
                    }
                    else -> {
                        val name = ANNOTATION_ATTR_VALUE
                        val value =
                            attrs[name]
                                ?: (exp as? Literal)?.value()
                                    ?: error(
                                    "Cannot find value for default 'value' attribute from $exp"
                                )
                        attributes.add(
                            DefaultAnnotationAttribute(name, createAttrValue(value, exp))
                        )
                    }
                }
            }
        } else {
            for ((name, value) in attrs) {
                attributes.add(DefaultAnnotationAttribute(name, createAttrValue(value, null)))
            }
        }
        return attributes
    }

    private fun createAttrValue(const: Const, expr: Expression?): AnnotationAttributeValue {
        if (const.kind() == Kind.ARRAY) {
            const as ArrayInitValue
            if (const.elements().count() == 1 && expr != null && expr !is ArrayInit) {
                // This is case where defined type is array type but provided attribute value is
                // single non-array element
                // For e.g. @Anno(5) where Anno is @interface Anno {int [] value()}
                val constLiteral = const.elements().single()
                return DefaultAnnotationSingleAttributeValue(
                    { TurbineValue(constLiteral, expr).getSourceForAnnotationValue() },
                    { constLiteral.underlyingValue }
                )
            }
            return DefaultAnnotationArrayAttributeValue(
                { TurbineValue(const, expr).getSourceForAnnotationValue() },
                { const.elements().map { createAttrValue(it, null) } }
            )
        }
        return DefaultAnnotationSingleAttributeValue(
            { TurbineValue(const, expr).getSourceForAnnotationValue() },
            { const.underlyingValue }
        )
    }
}
