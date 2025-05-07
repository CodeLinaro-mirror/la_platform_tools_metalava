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
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.reporter.FileLocation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.bound.TypeBoundClass
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
 * @param globalContext provides access to some global context needed by this.
 */
internal class TurbineAnnotationFactory(globalContext: TurbineGlobalContext) :
    TurbineGlobalContext by globalContext {
    /** Creates a list of AnnotationItems from given list of Turbine Annotations */
    internal fun createAnnotations(
        annotations: List<AnnoInfo>,
        fieldResolver: TurbineFieldResolver? = null,
    ): List<AnnotationItem> {
        return annotations.mapNotNull { createAnnotation(it, fieldResolver) }
    }

    /** Create an [AnnotationItem] from an [AnnoInfo]. */
    internal fun createAnnotation(
        annotation: AnnoInfo,
        fieldResolver: TurbineFieldResolver? = null,
    ): AnnotationItem? {
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

        val annotationClass = annotation.sym()?.let { typeBoundClassForSymbol(it) }

        return DefaultAnnotationItem.createAttributesLazily(
            codebase,
            fileLocation,
            qualifiedName
        ) { annotationItem ->
            getAnnotationAttributes(
                annotationClass,
                annotation.values(),
                tree?.args(),
                fieldResolver,
            )
        }
    }

    /** Creates a list of AnnotationAttribute from the map of name-value attribute pairs */
    private fun getAnnotationAttributes(
        annotationClass: TypeBoundClass?,
        attrs: ImmutableMap<String, Const>,
        exprs: ImmutableList<Expression>?,
        fieldResolver: TurbineFieldResolver?,
    ): List<AnnotationAttribute> {
        val attributes = mutableListOf<AnnotationAttribute>()
        if (exprs != null) {
            for (exp in exprs) {
                when (exp.kind()) {
                    Tree.Kind.ASSIGN -> {
                        exp as Assign
                        val name = exp.name().value()
                        val assignExp = exp.expr()
                        val const = attrs[name]!!
                        attributes.add(
                            DefaultAnnotationAttribute(
                                name,
                                createAttributeValueProvider(
                                    annotationClass,
                                    name,
                                    const,
                                    assignExp,
                                    fieldResolver,
                                ),
                                createAttrValue(const, assignExp, fieldResolver),
                            )
                        )
                    }
                    else -> {
                        val name = ANNOTATION_ATTR_VALUE
                        val const =
                            attrs[name]
                                ?: (exp as? Literal)?.value()
                                ?: error(
                                    "Cannot find value for default 'value' attribute from $exp"
                                )
                        attributes.add(
                            DefaultAnnotationAttribute(
                                name,
                                createAttributeValueProvider(
                                    annotationClass,
                                    name,
                                    const,
                                    exp,
                                    fieldResolver,
                                ),
                                createAttrValue(const, exp, fieldResolver),
                            )
                        )
                    }
                }
            }
        } else {
            for ((name, const) in attrs) {
                attributes.add(
                    DefaultAnnotationAttribute(
                        name,
                        createAttributeValueProvider(
                            annotationClass,
                            name,
                            const,
                            null,
                            fieldResolver,
                        ),
                        createAttrValue(const, null, fieldResolver),
                    )
                )
            }
        }
        return attributes
    }

    /**
     * Create a [CombinedValueProvider] that will create (and cache) a [Value]
     *
     * @param annotationClass the optional [TypeBoundClass] for the annotation. If provided it will
     *   be used to find a [TypeItem] for the annotation attribute called [attributeName].
     * @param attributeName the name of the annotation.
     * @param const the [Const] value.
     * @param expr the optional source [Expression].
     * @param fieldResolver the optional [TurbineFieldResolver] used to resolve field [expr]s to the
     *   field definition.
     */
    private fun createAttributeValueProvider(
        annotationClass: TypeBoundClass?,
        attributeName: String,
        const: Const,
        expr: Expression?,
        fieldResolver: TurbineFieldResolver?,
    ): ValueProvider {
        val turbineValue = TurbineValue(const, expr, fieldResolver)
        return valueFactory.providerForAnnotationValue(annotationClass, attributeName, turbineValue)
    }

    private fun createAttrValue(
        const: Const,
        expr: Expression?,
        fieldResolver: TurbineFieldResolver?,
    ): AnnotationAttributeValue {
        if (const.kind() == Kind.ARRAY) {
            const as ArrayInitValue
            if (const.elements().count() == 1 && expr != null && expr !is ArrayInit) {
                // This is case where defined type is array type but provided attribute value is
                // single non-array element
                // For e.g. @Anno(5) where Anno is @interface Anno {int [] value()}
                val constLiteral = const.elements().single()
                return DefaultAnnotationSingleAttributeValue(
                    {
                        TurbineValue(constLiteral, expr, fieldResolver)
                            .getSourceForAnnotationValue()
                    },
                    { constLiteral.underlyingValue }
                )
            }
            return DefaultAnnotationArrayAttributeValue(
                { TurbineValue(const, expr, fieldResolver).getSourceForAnnotationValue() },
                { const.elements().map { createAttrValue(it, null, fieldResolver) } }
            )
        }
        return DefaultAnnotationSingleAttributeValue(
            { TurbineValue(const, expr, fieldResolver).getSourceForAnnotationValue() },
            { const.underlyingValue }
        )
    }
}
