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

package com.android.tools.metalava.model.multiplatform

/** Basic visitor interface for a [MultiplatformCodebase]. */
interface MultiplatformItemVisitor {
    fun visit(codebase: MultiplatformCodebase) = Unit

    fun visit(packageItem: MultiplatformPackageItem) = Unit

    fun visit(classItem: MultiplatformClassItem) = Unit

    fun visit(constructorItem: MultiplatformConstructorItem) = Unit

    fun visit(methodItem: MultiplatformMethodItem) = Unit

    fun visit(parameterItem: MultiplatformParameterItem) = Unit

    fun visit(propertyItem: MultiplatformPropertyItem) = Unit
}

/**
 * Provides common visitor patterns for a [MultiplatformCodebase]. A visitor extending this class
 * should provide implementations for any of the `visit*Item` methods that are needed.
 */
open class BaseMultiplatformItemVisitor : MultiplatformItemVisitor {
    final override fun visit(codebase: MultiplatformCodebase) {
        for (packageItem in codebase.packages) {
            visit(packageItem)
        }
    }

    final override fun visit(packageItem: MultiplatformPackageItem) {
        if (skip(packageItem)) return

        visitItem(packageItem)
        visitSelectableItem(packageItem)
        visitPackageItem(packageItem)

        for (classItem in packageItem.topLevelClassesFromSource) {
            visit(classItem)
        }
        for (methodItem in packageItem.topLevelFunctions) {
            visit(methodItem)
        }
        for (propertyItem in packageItem.topLevelProperties) {
            visit(propertyItem)
        }
    }

    final override fun visit(classItem: MultiplatformClassItem) {
        if (skip(classItem)) return

        visitItem(classItem)
        visitSelectableItem(classItem)
        visitClassItem(classItem)

        for (constructorItem in classItem.constructors) {
            visit(constructorItem)
        }
        for (methodItem in classItem.methods) {
            visit(methodItem)
        }
        for (propertyItem in classItem.properties) {
            visit(propertyItem)
        }
        for (nestedClassItem in classItem.nestedClasses) {
            visit(nestedClassItem)
        }
    }

    final override fun visit(constructorItem: MultiplatformConstructorItem) {
        if (skip(constructorItem)) return

        visitItem(constructorItem)
        visitSelectableItem(constructorItem)
        visitCallableItem(constructorItem)
        visitConstructorItem(constructorItem)

        for (parameterItem in constructorItem.parameters) {
            visit(parameterItem)
        }
    }

    final override fun visit(methodItem: MultiplatformMethodItem) {
        if (skip(methodItem)) return

        visitItem(methodItem)
        visitSelectableItem(methodItem)
        visitCallableItem(methodItem)
        visitMethodItem(methodItem)

        for (parameterItem in methodItem.parameters) {
            visit(parameterItem)
        }
    }

    final override fun visit(parameterItem: MultiplatformParameterItem) {
        if (skip(parameterItem)) return

        visitItem(parameterItem)
        visitParameterItem(parameterItem)
    }

    final override fun visit(propertyItem: MultiplatformPropertyItem) {
        if (skip(propertyItem)) return

        visitItem(propertyItem)
        visitSelectableItem(propertyItem)
        visitPropertyItem(propertyItem)
    }

    open fun skip(item: MultiplatformItem<*>): Boolean = false

    open fun visitItem(item: MultiplatformItem<*>) = Unit

    open fun visitSelectableItem(item: MultiplatformItem<*>) = Unit

    open fun visitPackageItem(packageItem: MultiplatformPackageItem) = Unit

    open fun visitClassItem(classItem: MultiplatformClassItem) = Unit

    open fun visitCallableItem(callableItem: MultiplatformCallableItem<*>) = Unit

    open fun visitConstructorItem(constructor: MultiplatformConstructorItem) = Unit

    open fun visitMethodItem(methodItem: MultiplatformMethodItem) = Unit

    open fun visitParameterItem(parameter: MultiplatformParameterItem) = Unit

    open fun visitPropertyItem(propertyItem: MultiplatformPropertyItem) = Unit
}
