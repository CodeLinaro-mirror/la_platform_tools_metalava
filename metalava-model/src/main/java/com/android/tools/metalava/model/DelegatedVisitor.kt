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

package com.android.tools.metalava.model

/**
 * A special visitor interface suitable for use with code that traverses a [Codebase] and which
 * wants to delegate the visiting of the [Item]s to another class.
 */
interface DelegatedVisitor {
    /**
     * If `true` then a nested class is visited while visiting the containing class, otherwise
     * nested classes are visited after visiting the containing class. A class is being visited
     * between calls to [visitClass] and [afterVisitClass].
     *
     * Defaults to `false` simply because most implementations do not need to preserve class
     * nesting.
     */
    val requiresClassNesting: Boolean
        get() = false

    fun visitCodebase(codebase: Codebase) {}

    fun afterVisitCodebase(codebase: Codebase) {}

    fun visitPackage(pkg: PackageItem) {}

    fun afterVisitPackage(pkg: PackageItem) {}

    fun visitClass(cls: ClassItem) {}

    fun afterVisitClass(cls: ClassItem) {}

    fun visitConstructor(constructor: ConstructorItem) {}

    fun visitField(field: FieldItem) {}

    fun visitMethod(method: MethodItem) {}

    fun visitProperty(property: PropertyItem) {}

    fun visitTypeAlias(typeAlias: TypeAliasItem) {}
}
