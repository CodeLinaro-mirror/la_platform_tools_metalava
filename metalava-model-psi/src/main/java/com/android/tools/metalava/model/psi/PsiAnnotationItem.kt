/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.psi.CodePrinter.Companion.constantToExpression
import com.android.tools.metalava.model.psi.CodePrinter.Companion.constantToSource
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import org.jetbrains.kotlin.asJava.elements.KtLightNullabilityAnnotation

internal class PsiAnnotationItem
private constructor(
    override val annotationContext: PsiBasedCodebase,
    val psiAnnotation: PsiAnnotation,
    originalName: String,
    qualifiedName: String,
) :
    DefaultAnnotationItem(
        annotationContext = annotationContext,
        fileLocation = PsiFileLocation.fromPsiElement(psiAnnotation),
        originalName = originalName,
        qualifiedName = qualifiedName,
        attributesGetter = { getAnnotationAttributes(annotationContext, psiAnnotation) },
    ) {

    override fun toSource(target: AnnotationTarget): String {
        val sb = StringBuilder(60)
        appendAnnotation(
            annotationContext,
            sb,
            psiAnnotation,
            qualifiedName,
            target,
        )
        return sb.toString()
    }

    override fun snapshot(targetCodebase: Codebase) = this

    override fun isNonNull(): Boolean {
        if (psiAnnotation is KtLightNullabilityAnnotation<*> && originalName == "") {
            // Hack/workaround: some UAST annotation nodes do not provide qualified name :=(
            return true
        }
        return super.isNonNull()
    }

    companion object {
        private fun getAnnotationAttributes(
            codebase: PsiBasedCodebase,
            psiAnnotation: PsiAnnotation
        ): List<AnnotationAttribute> {
            val annotationPsiClass = psiAnnotation.resolveAnnotationType()
            return psiAnnotation.parameterList.attributes
                .mapNotNull { attribute ->
                    attribute.value?.let { value ->
                        val name = attribute.name ?: ANNOTATION_ATTR_VALUE

                        DefaultAnnotationAttribute(
                            name,
                            codebase.valueFactory.providerForAnnotationValue(
                                annotationPsiClass,
                                name,
                                value,
                            ),
                            createValue(value),
                        )
                    }
                }
                .toList()
        }

        fun create(
            codebase: PsiBasedCodebase,
            psiAnnotation: PsiAnnotation,
        ): AnnotationItem? {
            // If the qualified name is a typealias, convert it to the aliased type because that is
            // the version that will be present as a class in the codebase.
            val originalName =
                psiAnnotation.qualifiedName?.let {
                    (codebase.findTypeAlias(it)?.aliasedType as? PsiClassTypeItem)?.qualifiedName
                        ?: it
                } ?: return null
            val qualifiedName =
                codebase.annotationManager.normalizeInputName(originalName) ?: return null
            return PsiAnnotationItem(
                annotationContext = codebase,
                psiAnnotation = psiAnnotation,
                originalName = originalName,
                qualifiedName = qualifiedName,
            )
        }

        private fun appendAnnotation(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            psiAnnotation: PsiAnnotation,
            qualifiedName: String?,
            target: AnnotationTarget,
        ) {
            qualifiedName ?: return
            val outputName = codebase.annotationManager.normalizeOutputName(qualifiedName, target)

            val alwaysInlineValues = qualifiedName == "android.annotation.FlaggedApi"
            val attributes = psiAnnotation.parameterList.attributes
            if (attributes.isEmpty()) {
                sb.append("@$outputName")
                return
            }

            sb.append("@")
            sb.append(outputName)
            sb.append("(")
            if (
                attributes.size == 1 &&
                    (attributes[0].name == null || attributes[0].name == ANNOTATION_ATTR_VALUE)
            ) {
                // Special case: omit "value" if it's the only attribute
                appendValue(
                    codebase,
                    sb,
                    attributes[0].value,
                    target,
                    alwaysInlineValues = alwaysInlineValues,
                )
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
                    appendValue(
                        codebase,
                        sb,
                        attribute.value,
                        target,
                        alwaysInlineValues = alwaysInlineValues,
                    )
                }
            }
            sb.append(")")
        }

        private fun appendValue(
            codebase: PsiBasedCodebase,
            sb: StringBuilder,
            value: PsiAnnotationMemberValue?,
            target: AnnotationTarget,
            alwaysInlineValues: Boolean,
        ) {
            // Compute annotation string -- we don't just use value.text here
            // because that may not use fully qualified names, e.g. the source may say
            //  @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            // and we want to compute
            //
            // @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            when (value) {
                null -> sb.append("null")
                is PsiLiteral -> sb.append(constantToSource(value.value))
                is PsiReference -> {
                    when (val resolved = value.resolve()) {
                        is PsiField -> {
                            val containing = resolved.containingClass
                            if (containing != null) {
                                // If it's a field reference, see if it looks like the field is
                                // hidden; if
                                // so, inline the value
                                val cls = codebase.findOrCreateClass(containing)
                                val initializer = resolved.initializer
                                if (initializer != null) {
                                    val fieldItem = cls.findField(resolved.name)
                                    if (
                                        alwaysInlineValues ||
                                            fieldItem == null ||
                                            fieldItem.isHiddenOrRemoved() ||
                                            !fieldItem.isPublic
                                    ) {
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
                            sb.append(value.text)
                        }
                    }
                }
                is PsiArrayInitializerMemberValue -> {
                    sb.append('{')
                    var first = true
                    for (initializer in value.initializers) {
                        if (first) {
                            first = false
                        } else {
                            sb.append(", ")
                        }
                        appendValue(
                            codebase,
                            sb,
                            initializer,
                            target,
                            alwaysInlineValues = alwaysInlineValues,
                        )
                    }
                    sb.append('}')
                }
                is PsiAnnotation -> {
                    value.qualifiedName?.let { qualifiedName ->
                        appendAnnotation(
                            codebase,
                            sb,
                            value,
                            // Normalize the input name of the annotation.
                            codebase.annotationManager.normalizeInputName(qualifiedName),
                            target,
                        )
                    }
                }
                else -> {
                    // Special case the formatting of special floating point numbers which are
                    // defined in terms of division by 0.
                    if (
                        value is PsiBinaryExpression &&
                            value.operationTokenType == JavaTokenType.DIV
                    ) {
                        val right = value.rOperand
                        if (right is PsiLiteral) {
                            if (right.value == 0.0f || right.value == 0.0) {
                                val left = value.lOperand
                                if (left is PsiLiteral) {
                                    sb.append(constantToSource(left.value))
                                } else {
                                    val source = getConstantSource(left)
                                    sb.append(source)
                                }
                                sb.append(" / ")
                                sb.append(constantToSource(right.value))
                                return
                            }
                        }
                    }

                    if (value is PsiExpression) {
                        val source = getConstantSource(value)
                        if (source != null) {
                            sb.append(source)
                            return
                        }
                    }
                    sb.append(value.text)
                }
            }
        }

        private fun getConstantSource(value: PsiExpression): String? {
            val constant = JavaConstantExpressionEvaluator.computeConstantExpression(value, false)
            return constantToExpression(constant)
        }
    }
}

private fun createValue(value: PsiAnnotationMemberValue): AnnotationAttributeValue {
    return if (value is PsiArrayInitializerMemberValue) {
        DefaultAnnotationArrayAttributeValue(
            { value.text },
            { value.initializers.map { createValue(it) }.toList() }
        )
    } else {
        PsiAnnotationSingleAttributeValue(value)
    }
}

internal class PsiAnnotationSingleAttributeValue(private val psiValue: PsiAnnotationMemberValue) :
    DefaultAnnotationSingleAttributeValue({ psiValue.text }, { getValue(psiValue) }) {

    companion object {
        private fun getValue(psiValue: PsiAnnotationMemberValue): Any {
            if (psiValue is PsiLiteral) {
                return psiValue.value ?: psiValue.text.removeSurrounding("\"")
            }

            val value = ConstantEvaluator.evaluate(null, psiValue)
            if (value != null) {
                return value
            }

            if (psiValue is PsiClassObjectAccessExpression) {
                // The value of a class literal expression like String.class or String::class
                // is the fully qualified name, java.lang.String
                return psiValue.operand.type.canonicalText
            }

            return psiValue.text ?: psiValue.text.removeSurrounding("\"")
        }
    }
}
