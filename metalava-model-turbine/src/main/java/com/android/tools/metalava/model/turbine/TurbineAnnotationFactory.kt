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
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.reporter.FileLocation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.bound.TurbineAnnotationValue
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.model.Const
import com.google.turbine.model.TurbineTyKind
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.Assign
import com.google.turbine.tree.Tree.Expression
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type

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
        fieldResolver: FieldResolver?,
    ): List<AnnotationItem> {
        return buildList {
            // The annotations could be a single annotation, or a container for a repeatable
            // annotation. In the latter case the container is discarded and the repeated
            // annotations are added to the list.
            for (possibleContainer in annotations) {
                // Check to see if the annotation is a repeatable container.
                if (possibleContainer.isContainerForRepeatableAnnotations()) {
                    // It is so unwrap it and add each of the repeated annotations.
                    for (wrapped in possibleContainer.unwrapRepeatableContainer()) {
                        createAndAddAnnotationItemIfNotNull(wrapped, fieldResolver)
                    }
                } else {
                    // It is not a repeatable container so just add it.
                    createAndAddAnnotationItemIfNotNull(possibleContainer, fieldResolver)
                }
            }
        }
    }

    /**
     * Try and create an [AnnotationItem] for [annotation] and if successful, adds it to this list.
     */
    fun MutableList<AnnotationItem>.createAndAddAnnotationItemIfNotNull(
        annotation: AnnoInfo,
        fieldResolver: FieldResolver?
    ) {
        createAnnotation(annotation, fieldResolver)?.let { add(it) }
    }

    /** Check to see if [this] is an instance of a container for a [Repeatable] annotation. */
    private fun AnnoInfo.isContainerForRepeatableAnnotations(): Boolean {
        // Get the class definition for the annotation that is a possible container.
        val possibleContainerSym = sym()
        val possibleContainerClass =
            possibleContainerSym?.let { sym -> typeBoundClassForSymbol(sym) }
                // Cannot find the class so assume it is not a container.
                ?: return false

        // Container class must have a "value" method...
        val valueMethod =
            possibleContainerClass.methods().find { it.name() == ANNOTATION_ATTR_VALUE }
                ?: return false
        val returnType = valueMethod.returnType()

        // That returns an array ...
        if (returnType !is Type.ArrayTy) return false

        // Of a class type ...
        val elementType = returnType.elementType()
        if (elementType !is Type.ClassTy) return false

        // That can be resolved ...
        val possibleContainedClass =
            elementType.sym()?.let { sym -> typeBoundClassForSymbol(sym) }
                // Cannot find the class so assume it is not an annotation.
                ?: return false

        // Which is an annotation class ...
        if (possibleContainedClass.kind() != TurbineTyKind.ANNOTATION) return false

        // And is tagged as repeatable ...
        val annotationMetadata = possibleContainedClass.annotationMetadata() ?: return false
        val containerSym = annotationMetadata.repeatable() ?: return false

        // And uses the container.
        if (containerSym != possibleContainerSym) return false

        return true
    }

    /** Unwrap [this] which is a container for a [Repeatable] annotation. */
    private fun AnnoInfo.unwrapRepeatableContainer(): List<AnnoInfo> {
        val value = values()[ANNOTATION_ATTR_VALUE]
        value as? Const.ArrayInitValue ?: return emptyList()
        return value.elements().mapNotNull { (it as? TurbineAnnotationValue)?.info() }
    }

    /** Create an [AnnotationItem] from an [AnnoInfo]. */
    internal fun createAnnotation(
        annotation: AnnoInfo,
        fieldResolver: FieldResolver?,
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

        return AnnotationItem.createAttributesLazily(codebase, fileLocation, qualifiedName) {
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
        fieldResolver: FieldResolver?,
    ): List<AnnotationAttribute> {
        val attributes = mutableListOf<AnnotationAttribute>()

        /**
         * Add an attribute called [name] with constant value [const] and optional value expression
         * [valueExpr] to the `attributes` list.
         */
        fun addAttribute(name: String, const: Const?, valueExpr: Expression?) {
            attributes.add(
                AnnotationAttribute.createLazyAttribute(
                    name,
                    createAttributeValueProvider(
                        annotationClass,
                        name,
                        const,
                        valueExpr,
                        fieldResolver,
                    ),
                )
            )
        }

        // Source annotations have expressions, binary annotations do not.
        if (exprs != null) {
            // This is for a source annotation.
            for (exp in exprs) {
                // Get the attribute name and value expression.
                val (name, valueExpr) =
                    if (exp is Assign) {
                        val name = exp.name().value()
                        val assignExp = exp.expr()
                        name to assignExp
                    } else {
                        ANNOTATION_ATTR_VALUE to exp
                    }

                // Get the constant value, if any.
                val const = attrs[name]

                // Add an attribute.
                addAttribute(name, const, valueExpr)
            }
        } else {
            // This is for a binary annotation.
            for ((name, const) in attrs) {
                // Add an attribute for a binary annotation which has no expression.
                addAttribute(name, const, null)
            }
        }
        return attributes
    }

    /**
     * Create a [ValueProvider] that will create (and cache) a [Value]
     *
     * @param annotationClass the optional [TypeBoundClass] for the annotation. If provided it will
     *   be used to find a [TypeItem] for the annotation attribute called [attributeName].
     * @param attributeName the name of the annotation.
     * @param const the optional [Const] value.
     * @param expr the optional source [Expression].
     * @param fieldResolver the optional [FieldResolver] used to resolve field [expr]s to the field
     *   definition.
     */
    private fun createAttributeValueProvider(
        annotationClass: TypeBoundClass?,
        attributeName: String,
        const: Const?,
        expr: Expression?,
        fieldResolver: FieldResolver?,
    ): ValueProvider {
        val turbineValue = TurbineValue(const, expr, fieldResolver)
        return valueFactory.providerForAnnotationValue(annotationClass, attributeName, turbineValue)
    }
}
