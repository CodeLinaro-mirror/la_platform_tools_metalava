/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.Item
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isArrayInitializer

internal class UAnnotationItem
private constructor(
    override val annotationContext: PsiBasedCodebase,
    val uAnnotation: UAnnotation,
    originalName: String,
    qualifiedName: String,
) :
    DefaultAnnotationItem(
        annotationContext = annotationContext,
        fileLocation = PsiFileLocation.fromPsiElement(uAnnotation.sourcePsi),
        originalName = originalName,
        qualifiedName = qualifiedName,
        attributesGetter = { getAnnotationAttributes(annotationContext, uAnnotation) },
    ) {

    override fun toSource(target: AnnotationTarget, context: Item?): String {
        val sb = StringBuilder(60)
        appendAnnotation(
            annotationContext,
            sb,
            uAnnotation,
            qualifiedName,
            target,
        )
        return sb.toString()
    }

    override fun snapshot(targetCodebase: Codebase) = this

    companion object {
        private fun getAnnotationAttributes(
            codebase: PsiBasedCodebase,
            uAnnotation: UAnnotation
        ): List<AnnotationAttribute> {
            val annotationPsiClass = uAnnotation.resolve()
            return uAnnotation.attributeValues
                .map { attribute ->
                    val name = attribute.name ?: ANNOTATION_ATTR_VALUE
                    DefaultAnnotationAttribute(
                        name,
                        codebase.valueFactory.providerForAnnotationValue(
                            annotationPsiClass,
                            name,
                            attribute.expression
                        ),
                    )
                }
                .toList()
        }

        fun create(
            codebase: PsiBasedCodebase,
            uAnnotation: UAnnotation,
        ): AnnotationItem? {
            // If the qualified name is a typealias, convert it to the aliased type because that is
            // the version that will be present as a class in the codebase.
            val originalName =
                uAnnotation.qualifiedName?.let {
                    (codebase.findTypeAlias(it)?.aliasedType as? PsiClassTypeItem)?.qualifiedName
                        ?: it
                } ?: return null
            val qualifiedName =
                codebase.annotationManager.normalizeInputName(originalName) ?: return null
            return UAnnotationItem(
                annotationContext = codebase,
                uAnnotation = uAnnotation,
                originalName = originalName,
                qualifiedName = qualifiedName,
            )
        }

        private fun appendAnnotation(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            uAnnotation: UAnnotation,
            originalName: String,
            target: AnnotationTarget,
        ) {
            val qualifiedName = codebase.annotationManager.normalizeOutputName(originalName, target)

            val attributes = uAnnotation.attributeValues
            if (attributes.isEmpty()) {
                sb.append("@$qualifiedName")
                return
            }

            sb.append("@")
            sb.append(qualifiedName)
            sb.append("(")
            if (
                attributes.size == 1 &&
                    (attributes[0].name == null || attributes[0].name == ANNOTATION_ATTR_VALUE)
            ) {
                // Special case: omit "value" if it's the only attribute
                appendValue(codebase, sb, attributes[0].expression, target)
            } else {
                var first = true
                for (attribute in attributes) {
                    if (first) {
                        first = false
                    } else {
                        sb.append(", ")
                    }
                    sb.append(attribute.name ?: ANNOTATION_ATTR_VALUE)
                    sb.append('=')
                    appendValue(codebase, sb, attribute.expression, target)
                }
            }
            sb.append(")")
        }

        private fun appendValue(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            value: UExpression?,
            target: AnnotationTarget,
        ) {
            // Compute annotation string -- we don't just use value.text here
            // because that may not use fully qualified names, e.g. the source may say
            //  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            // and we want to compute
            //
            // @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)

            when (value) {
                null -> {
                    sb.append("null")
                    return
                }
                is ULiteralExpression -> {
                    sb.append(CodePrinter.constantToSource(value.value))
                    return
                }
                is UQualifiedReferenceExpression -> {
                    // the value is a Foo.BAR type of reference, or a Foo::class.java type of
                    // reference.
                    val receiver = value.receiver
                    if (receiver is UReferenceExpression) {
                        // expand `Foo` to fully qualified name `com.example.Foo`
                        appendQualifiedName(codebase, sb, receiver)
                        // append accessor `.`
                        sb.append(value.accessType.name)
                        // append `BAR`
                        sb.append(value.selector.asRenderString())
                    } else {
                        sb.append(value.asSourceString())
                    }
                    return
                }
                is UReferenceExpression -> {
                    // expand Foo to fully qualified name com.example.Foo
                    appendQualifiedName(codebase, sb, value)
                    return
                }
                is UCallExpression -> {
                    if (value.isArrayInitializer()) {
                        sb.append('{')
                        var first = true
                        for (initializer in value.valueArguments) {
                            if (first) {
                                first = false
                            } else {
                                sb.append(", ")
                            }
                            appendValue(codebase, sb, initializer, target)
                        }
                        sb.append('}')
                        return
                    }
                    // TODO: support UCallExpression for other cases than array initializers
                    // Drop out as it did not append on for other cases than array initializers
                }
            }

            // Fallback, first try evaluating to a constant and using that.
            val source = getConstantSource(value!!)
            if (source != null) {
                sb.append(source)
                return
            }

            // Then use the source text.
            val text = value.sourcePsi?.text ?: value.asSourceString()
            sb.append(text)
        }

        private fun appendQualifiedName(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            value: UReferenceExpression
        ) {
            when (val resolved = value.resolve()) {
                is PsiField -> {
                    val containing = resolved.containingClass
                    if (containing != null) {
                        // If it's a field reference, see if it looks like the field is hidden; if
                        // so, inline the value
                        val cls = codebase.findOrCreateClass(containing)
                        val initializer = resolved.initializer
                        if (initializer != null) {
                            val fieldItem = cls.findField(resolved.name)
                            if (fieldItem == null || fieldItem.isHiddenOrRemoved()) {
                                // Use the literal value instead
                                val source = getConstantSource(initializer)
                                if (source != null) {
                                    sb.append(source)
                                    return
                                }
                            }
                        }
                        containing.qualifiedName?.let { sb.append(it).append('.') }
                    }

                    sb.append(resolved.name)
                }
                is PsiClass -> resolved.qualifiedName?.let { sb.append(it) }
                else -> {
                    sb.append(value.sourcePsi?.text ?: value.asSourceString())
                }
            }
        }

        private fun getConstantSource(value: UExpression): String? {
            val constant = value.evaluate()
            return CodePrinter.constantToExpression(constant)
        }

        private fun getConstantSource(value: PsiExpression): String? {
            val constant = JavaConstantExpressionEvaluator.computeConstantExpression(value, false)
            return CodePrinter.constantToExpression(constant)
        }
    }
}
