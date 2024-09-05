/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.CallableBodyFactory
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

open class DefaultMethodItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    name: String,
    containingClass: ClassItem,
    typeParameterList: TypeParameterList,
    returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    throwsTypes: List<ExceptionTypeItem>,
    callableBodyFactory: CallableBodyFactory,
    private val annotationDefault: String = "",
) :
    DefaultCallableItem(
        codebase,
        fileLocation,
        itemLanguage,
        modifiers,
        documentationFactory,
        variantSelectorsFactory,
        name,
        containingClass,
        typeParameterList,
        returnType,
        parameterItemsFactory,
        throwsTypes,
        callableBodyFactory,
    ),
    MethodItem {

    final override var inheritedFrom: ClassItem? = null

    override fun isExtensionMethod(): Boolean = false // java does not support extension methods

    override fun defaultValue() = annotationDefault

    private lateinit var superMethodList: List<MethodItem>

    /**
     * Super methods for a given method M with containing class C are calculated as follows:
     * 1) Superclass Search: Traverse the class hierarchy, starting from C's direct superclass, and
     *    add the first method that matches M's signature to the list.
     * 2) Interface Supermethod Search: For each direct interface implemented by C, check if it
     *    contains a method matching M's signature. If found, return that method. If not,
     *    recursively apply this method to the direct interfaces of the current interface.
     *
     * Note: This method's implementation is based on MethodItem.matches method which only checks
     * that name and parameter list types match. Parameter names, Return types and Throws list types
     * are not matched
     */
    final override fun superMethods(): List<MethodItem> {
        if (!::superMethodList.isInitialized) {
            superMethodList = computeSuperMethods()
        }
        return superMethodList
    }

    @Deprecated("This property should not be accessed directly.")
    final override var _requiresOverride: Boolean? = null

    override fun duplicate(targetContainingClass: ClassItem): MethodItem {
        val typeVariableMap = targetContainingClass.mapTypeVariables(containingClass())

        return DefaultMethodItem(
                codebase = codebase,
                fileLocation = fileLocation,
                itemLanguage = itemLanguage,
                modifiers = modifiers.duplicate(),
                documentationFactory = documentation::duplicate,
                variantSelectorsFactory = variantSelectors::duplicate,
                name = name(),
                containingClass = targetContainingClass,
                typeParameterList = typeParameterList,
                returnType = returnType.convertType(typeVariableMap),
                parameterItemsFactory = { containingCallable ->
                    // Duplicate the parameters
                    parameters.map { it.duplicate(containingCallable, typeVariableMap) }
                },
                throwsTypes = throwsTypes,
                annotationDefault = annotationDefault,
                callableBodyFactory = body::duplicate,
            )
            .also { duplicated ->
                duplicated.inheritedFrom = containingClass()

                duplicated.updateCopiedMethodState()
            }
    }

    /**
     * Compute the super methods of this method.
     *
     * A super method is a method from a super class or super interface that is directly overridden
     * by this method.
     */
    private fun computeSuperMethods(): List<MethodItem> {
        // Methods that are not overrideable will have no super methods.
        if (!isOverrideable()) {
            return emptyList()
        }

        // TODO(b/321216636): Remove this awful hack.
        // For some reason `psiMethod.findSuperMethods()` would return an empty list for this
        // specific method. That is incorrect as it clearly overrides a method in `DrawScope` in the
        // same package. However, it is unclear what makes this method distinct from any other
        // method including overloaded methods in the same class that also override methods
        // in`DrawScope`. Returning a correct non-empty list for that method results in the method
        // being removed from an API signature file even though the super method is abstract and
        // this is concrete. That is because AndroidX does not yet set
        // `add-additional-overrides=yes`. When it does then this hack can be removed.
        if (
            containingClass().qualifiedName() ==
                "androidx.compose.ui.graphics.drawscope.CanvasDrawScope" &&
                name() == "drawImage" &&
                toString() ==
                    "method androidx.compose.ui.graphics.drawscope.CanvasDrawScope.drawImage(androidx.compose.ui.graphics.ImageBitmap, long, long, long, long, float, androidx.compose.ui.graphics.drawscope.DrawStyle, androidx.compose.ui.graphics.ColorFilter, int)"
        ) {
            return emptyList()
        }

        // Ideally, the search for super methods would start from this method's ClassItem.
        // Unfortunately, due to legacy reasons for methods that were inherited from another
        // ClassItem it is necessary to start the search from the original ClassItem. That is
        // because the psi model's implementation behaved this way and the code that is built of top
        // of superMethods, like the code to determine if overriding methods should be elided from
        // the API signature file relied on that behavior.
        val startingClass = inheritedFrom ?: containingClass()
        return buildSet { appendSuperMethods(this, startingClass) }.toList()
    }

    /**
     * Append the super methods of this method from the [cls] hierarchy to the [methods] set.
     *
     * @param methods the mutable, order preserving set of super [MethodItem].
     * @param cls the [ClassItem] whose super class and implemented interfaces will be searched for
     *   matching methods.
     */
    private fun appendSuperMethods(methods: MutableSet<MethodItem>, cls: ClassItem) {
        // Method from SuperClass or its ancestors
        cls.superClass()?.let { superClass ->
            // Search for a matching method in the super class.
            val superMethod = superClass.findMethod(this)
            if (superMethod == null) {
                // No matching method was found so continue searching in the super class.
                appendSuperMethods(methods, superClass)
            } else {
                // Matching does not check modifiers match so make sure that the matched method is
                // overrideable.
                if (superMethod.isOverrideable()) {
                    methods.add(superMethod)
                }
            }
        }

        // Methods implemented from direct interfaces or its ancestors
        appendSuperMethodsFromInterfaces(methods, cls)
    }

    /**
     * Append the super methods of this method from the interface hierarchy of [cls] to the
     * [methods] set.
     *
     * @param methods the mutable, order preserving set of super [MethodItem].
     * @param cls the [ClassItem] whose implemented interfaces will be searched for matching
     *   methods.
     */
    private fun appendSuperMethodsFromInterfaces(methods: MutableSet<MethodItem>, cls: ClassItem) {
        for (itf in cls.interfaceTypes()) {
            val itfClass = itf.asClass() ?: continue

            // Find the method in the interface.
            itfClass.findMethod(this)?.let { superMethod ->
                // A matching method was found so add it to the super methods if it is overrideable.
                if (superMethod.isOverrideable()) {
                    methods.add(superMethod)
                }
            }
            // A method could not be found in this interface so search its interfaces.
            ?: appendSuperMethodsFromInterfaces(methods, itfClass)
        }
    }

    /**
     * Update the state of a [MethodItem] that has been copied from one [ClassItem] to another.
     *
     * This will update the [MethodItem] on which it is called to ensure that it is consistent with
     * the [ClassItem] to which it now belongs. Called from the implementations of
     * [MethodItem.duplicate].
     */
    protected fun updateCopiedMethodState() {
        val mutableModifiers = mutableModifiers()
        if (mutableModifiers.isDefault() && !containingClass().isInterface()) {
            mutableModifiers.setDefault(false)
        }
    }
}

/**
 * Check to see if the method is overrideable.
 *
 * Private and static methods cannot be overridden.
 */
private fun MethodItem.isOverrideable(): Boolean = !modifiers.isPrivate() && !modifiers.isStatic()
