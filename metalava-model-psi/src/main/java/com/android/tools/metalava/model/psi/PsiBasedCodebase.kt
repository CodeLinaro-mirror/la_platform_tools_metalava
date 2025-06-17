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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import java.io.File
import org.jetbrains.uast.UMethod

const val METHOD_ESTIMATE = 1000

/**
 * A codebase containing Java, Kotlin, or UAST PSI classes
 *
 * After creation, a list of PSI file is passed to [PsiCodebaseAssembler.initializeFromSources] or a
 * JAR file is passed to [PsiCodebaseAssembler.initializeFromJar]. This creates package and class
 * items along with their members. Any classes defined in those files will have [ClassItem.origin]
 * set based on [fromClasspath].
 *
 * Classes that are created through [findOrCreateClass] will have [ClassItem.origin] set to
 * [ClassOrigin.SOURCE_PATH] or [ClassOrigin.CLASS_PATH] depending on whether the class is defined
 * on the source path or on the class path respectively.
 */
internal class PsiBasedCodebase(
    location: File,
    description: String = "Unknown",
    config: Codebase.Config,
    val allowReadingComments: Boolean,
    val fromClasspath: Boolean = false,
    assembler: PsiCodebaseAssembler,
    val isMultiplatform: Boolean,
) :
    DefaultCodebase(
        location = location,
        description = description,
        preFiltered = false,
        config = config,
        trustedApi = false,
        supportsDocumentation = true,
        assembler = assembler,
    ) {

    internal val psiAssembler = assembler

    internal val project: Project
        get() = psiAssembler.project

    /**
     * Map from classes to the set of callables for each (but only for classes where we've called
     * [findCallableByPsiMethod]
     */
    private val methodMap: MutableMap<ClassItem, MutableMap<PsiMethod, PsiCallableItem>> =
        HashMap(METHOD_ESTIMATE)

    /** [PsiTypeItemFactory] used to create [PsiTypeItem]s. */
    internal val globalTypeItemFactory
        get() = psiAssembler.globalTypeItemFactory

    /** Creates [ValueProvider]s and [Value]s from Psi classes. */
    internal val valueFactory by
        lazy(LazyThreadSafetyMode.NONE) { PsiValueFactory(this, globalTypeItemFactory) }

    override fun dispose() {
        psiAssembler.dispose()
        super.dispose()
    }

    fun findClass(psiClass: PsiClass): ClassItem? {
        val qualifiedName: String = psiClass.classQualifiedName
        return findClass(qualifiedName)
    }

    internal fun findOrCreateClass(psiClass: PsiClass) = psiAssembler.findOrCreateClass(psiClass)

    internal fun findCallableByPsiMethod(method: PsiMethod): PsiCallableItem {
        val containingClass = method.containingClass
        val cls = findOrCreateClass(containingClass!!)

        // Ensure initialized/registered via [#registerMethods]
        if (methodMap[cls] == null) {
            val map = HashMap<PsiMethod, PsiCallableItem>(40)
            registerCallablesByPsiMethod(cls.methods(), map)
            registerCallablesByPsiMethod(cls.constructors(), map)
            methodMap[cls] = map
        }

        val methods = methodMap[cls]!!
        val methodItem = methods[method]
        if (methodItem == null) {
            // Probably switched psi classes (e.g. used source PsiClass in registry but found
            // duplicate class in .jar library, and we're now pointing to it; in that case, find the
            // equivalent method by signature
            val psiClass = (cls as PsiClassItem).psiClass
            val updatedMethod = psiClass.findMethodBySignature(method, true)
            val result = methods[updatedMethod!!]
            if (result == null) {
                val extra =
                    PsiMethodItem.create(this, cls, updatedMethod, globalTypeItemFactory.from(cls))
                methods[method] = extra
                methods[updatedMethod] = extra

                return extra
            }
            return result
        }

        return methodItem
    }

    private fun registerCallablesByPsiMethod(
        callables: List<CallableItem>,
        map: MutableMap<PsiMethod, PsiCallableItem>
    ) {
        for (callable in callables) {
            val psiMethod = (callable as PsiCallableItem).psiMethod
            map[psiMethod] = callable
            if (psiMethod is UMethod) {
                // Register LC method as a key too
                // so that we can find the corresponding [CallableItem]
                // Otherwise, we will end up creating a new [CallableItem]
                // without source PSI, resulting in wrong modifier.
                map[psiMethod.javaPsi] = callable
            }
        }
    }

    override fun isFromClassPath() = fromClasspath

    override fun createAnnotation(source: String, context: Item?) =
        psiAssembler.createAnnotation(source, context)

    /**
     * Override to allow access to the [AnnotationDefaults] without having to resolve a [ClassItem]
     * which can have side effects which can cause problems during [PsiBasedCodebase] construction.
     *
     * The side effects are encountered as follows:
     * * There is an optimization in [PsiCodebaseAssembler] where it will not create inaccessible
     *   classes.
     * * An `internal` class may be accessible if it has a show annotation.
     * * Computing the [AnnotationItem.showability] requires getting values for all an
     *   [AnnotationItem]'s attributes, including default values.
     * * Getting [AnnotationDefaults] using the default implementation of this will resolve the
     *   [AnnotationItem]'s [ClassItem].
     * * Resolving the [ClassItem] before it has itself been created and registered causes problems,
     *   e.g. it has the wrong value for [ClassItem.emit].
     */
    override fun defaultsForAnnotationClass(qualifiedName: String): AnnotationDefaults {
        // Use defaults from a class if it was already created.
        findClass(qualifiedName)?.let {
            return it.annotationClass.defaults
        }

        // Otherwise, find the `PsiClass` and compute the values from that instead. This does not
        // cache the results as there are very few places outside tests where this is used.
        psiAssembler.findPsiClass(qualifiedName)?.let { psiClass ->
            if (psiClass.methods.isNotEmpty()) {
                val defaultsByName =
                    psiClass.methods
                        .mapNotNull { psiMethod ->
                            val psiReturnType = psiMethod.returnType ?: return@mapNotNull null
                            val optionalTypeItem =
                                globalTypeItemFactory.getType(
                                    psiReturnType,
                                    psiMethod,
                                    ContextNullability.forceNonNull,
                                )
                            val valueProvider =
                                psiMethod.defaultValueProvider(this, optionalTypeItem)
                                    ?: return@mapNotNull null

                            psiMethod.name to valueProvider.value
                        }
                        .toMap()

                return AnnotationDefaults(defaultsByName)
            }
        }

        return AnnotationDefaults.EMPTY
    }
}
