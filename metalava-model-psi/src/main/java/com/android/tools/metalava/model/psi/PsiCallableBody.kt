/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiSynchronizedStatement
import com.intellij.psi.PsiThisExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PsiCallableBody(private val callable: PsiCallableItem) : CallableBody {

    /**
     * Access [codebase] on demand as [callable] is not properly initialized during initialization
     * of this class.
     */
    private val codebase
        get() = callable.codebase

    /**
     * Access [psiMethod] on demand as [callable] is not properly initialized during initialization
     * of this class.
     */
    private val psiMethod
        get() = callable.psiMethod

    override fun findThrownExceptions(): Set<ClassItem> {
        if (!callable.isKotlin()) {
            return emptySet()
        }

        val exceptions = mutableSetOf<ClassItem>()

        val method = psiMethod as? UMethod ?: return emptySet()
        method.accept(
            object : AbstractUastVisitor() {
                override fun visitThrowExpression(node: UThrowExpression): Boolean {
                    val type = node.thrownExpression.getExpressionType()
                    if (type != null) {
                        val typeItemFactory = codebase.globalTypeItemFactory.from(callable)
                        val exceptionClass = typeItemFactory.getType(type).asClass()
                        if (exceptionClass != null && !isCaught(exceptionClass, node)) {
                            exceptions.add(exceptionClass)
                        }
                    }
                    return super.visitThrowExpression(node)
                }

                private fun isCaught(exceptionClass: ClassItem, node: UThrowExpression): Boolean {
                    var current: UElement = node
                    while (true) {
                        val tryExpression =
                            current.getParentOfType<UTryExpression>(
                                UTryExpression::class.java,
                                true,
                                UMethod::class.java
                            )
                                ?: return false

                        for (catchClause in tryExpression.catchClauses) {
                            for (type in catchClause.types) {
                                val qualifiedName = type.canonicalText
                                if (exceptionClass.extends(qualifiedName)) {
                                    return true
                                }
                            }
                        }

                        current = tryExpression
                    }
                }
            }
        )

        return exceptions
    }

    override fun findVisiblySynchronizedLocations(): List<FileLocation> {
        return buildList {
            val psiMethod = psiMethod
            if (psiMethod is UMethod) {
                psiMethod.accept(
                    object : AbstractUastVisitor() {
                        override fun afterVisitCallExpression(node: UCallExpression) {
                            super.afterVisitCallExpression(node)

                            if (node.methodName == "synchronized" && node.receiver == null) {
                                val arg = node.valueArguments.firstOrNull()
                                if (
                                    arg is UThisExpression ||
                                        arg is UClassLiteralExpression ||
                                        arg is UQualifiedReferenceExpression &&
                                            arg.receiver is UClassLiteralExpression
                                ) {
                                    val psi = arg.sourcePsi ?: node.sourcePsi ?: node.javaPsi
                                    add(PsiFileLocation.fromPsiElement(psi))
                                }
                            }
                        }
                    }
                )
            } else {
                psiMethod.body?.accept(
                    object : JavaRecursiveElementVisitor() {
                        override fun visitSynchronizedStatement(
                            statement: PsiSynchronizedStatement
                        ) {
                            super.visitSynchronizedStatement(statement)

                            val lock = statement.lockExpression
                            if (
                                lock == null ||
                                    lock is PsiThisExpression ||
                                    // locking on any class is visible
                                    lock is PsiClassObjectAccessExpression
                            ) {
                                val psi = lock ?: statement
                                add(PsiFileLocation.fromPsiElement(psi))
                            }
                        }
                    }
                )
            }
        }
    }
}
