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

package com.android.tools.metalava.model

open class BaseItemVisitor(
    /**
     * Whether nested classes should be visited "inside" a class; when this property is true, nested
     * classes are visited before the [#afterVisitClass] method is called; when false, it's done
     * afterwards. Defaults to false.
     */
    val preserveClassNesting: Boolean = false,
) : ItemVisitor {
    override fun visit(cls: ClassItem) {
        if (skip(cls)) {
            return
        }

        visitItem(cls)
        visitClass(cls)

        for (constructor in cls.constructors()) {
            constructor.accept(this)
        }

        for (method in cls.methods()) {
            method.accept(this)
        }

        for (property in cls.properties()) {
            property.accept(this)
        }

        if (cls.isEnum()) {
            // In enums, visit the enum constants first, then the fields
            for (field in cls.fields()) {
                if (field.isEnumConstant()) {
                    field.accept(this)
                }
            }
            for (field in cls.fields()) {
                if (!field.isEnumConstant()) {
                    field.accept(this)
                }
            }
        } else {
            for (field in cls.fields()) {
                field.accept(this)
            }
        }

        if (preserveClassNesting) {
            for (nestedCls in cls.nestedClasses()) {
                nestedCls.accept(this)
            }
        } // otherwise done in visit(PackageItem)

        afterVisitClass(cls)
        afterVisitItem(cls)
    }

    override fun visit(field: FieldItem) {
        if (skip(field)) {
            return
        }

        visitItem(field)
        visitField(field)
        afterVisitItem(field)
    }

    override fun visit(constructor: ConstructorItem) {
        visitMethodOrConstructor(constructor) { visitConstructor(it) }
    }

    override fun visit(method: MethodItem) {
        visitMethodOrConstructor(method) { visitMethod(it) }
    }

    private inline fun <T : CallableItem> visitMethodOrConstructor(
        callable: T,
        dispatch: (T) -> Unit
    ) {
        if (skip(callable)) {
            return
        }

        visitItem(callable)
        visitCallable(callable)

        // Call the specific visitX method for the CallableItem subclass.
        dispatch(callable)

        for (parameter in callable.parameters()) {
            parameter.accept(this)
        }

        afterVisitItem(callable)
    }

    /**
     * Get the package's classes to visit directly.
     *
     * If nested classes are to appear as nested within their containing classes then this will just
     * return the package's top level classes. It will then be the responsibility of
     * `visit(ClassItem)` to visit the nested classes. Otherwise, this will return a flattened
     * sequence of each class followed by its nested classes.
     */
    protected fun packageClassesAsSequence(pkg: PackageItem) =
        if (preserveClassNesting) pkg.topLevelClasses().asSequence() else pkg.allClasses()

    override fun visit(pkg: PackageItem) {
        if (skip(pkg)) {
            return
        }

        visitItem(pkg)
        visitPackage(pkg)

        for (cls in packageClassesAsSequence(pkg)) {
            cls.accept(this)
        }

        afterVisitPackage(pkg)
        afterVisitItem(pkg)
    }

    override fun visit(packageList: PackageList) {
        visitCodebase(packageList.codebase)
        packageList.packages.forEach { it.accept(this) }
        afterVisitCodebase(packageList.codebase)
    }

    override fun visit(parameter: ParameterItem) {
        if (skip(parameter)) {
            return
        }

        visitItem(parameter)
        visitParameter(parameter)
        afterVisitItem(parameter)
    }

    override fun visit(property: PropertyItem) {
        if (skip(property)) {
            return
        }

        visitItem(property)
        visitProperty(property)
        afterVisitItem(property)
    }

    open fun skip(item: Item): Boolean = false

    /**
     * Visits the item. This is always called before other more specialized visit methods, such as
     * [visitClass].
     */
    open fun visitItem(item: Item) {}

    open fun visitCodebase(codebase: Codebase) {}

    open fun visitPackage(pkg: PackageItem) {}

    open fun visitClass(cls: ClassItem) {}

    open fun visitCallable(callable: CallableItem) {}

    open fun visitConstructor(constructor: ConstructorItem) {}

    open fun visitMethod(method: MethodItem) {}

    open fun visitField(field: FieldItem) {}

    open fun visitParameter(parameter: ParameterItem) {}

    open fun visitProperty(property: PropertyItem) {}

    open fun afterVisitItem(item: Item) {}

    open fun afterVisitCodebase(codebase: Codebase) {}

    open fun afterVisitPackage(pkg: PackageItem) {}

    open fun afterVisitClass(cls: ClassItem) {}
}
