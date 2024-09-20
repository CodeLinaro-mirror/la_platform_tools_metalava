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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem

/**
 * A [BaseItemVisitor] that will delegate to [delegate].
 *
 * Preserves class nesting as required by the [delegate]'s [DelegatedVisitor.requiresClassNesting]
 * property.
 */
open class NonFilteringDelegatingVisitor(private val delegate: DelegatedVisitor) :
    BaseItemVisitor(preserveClassNesting = delegate.requiresClassNesting) {

    override fun visitCodebase(codebase: Codebase) {
        delegate.visitCodebase(codebase)
    }

    override fun afterVisitCodebase(codebase: Codebase) {
        delegate.afterVisitCodebase(codebase)
    }

    override fun visitPackage(pkg: PackageItem) {
        delegate.visitPackage(pkg)
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        delegate.afterVisitPackage(pkg)
    }

    override fun visitClass(cls: ClassItem) {
        delegate.visitClass(cls)
    }

    override fun afterVisitClass(cls: ClassItem) {
        delegate.afterVisitClass(cls)
    }

    override fun visit(constructor: ConstructorItem) {
        delegate.visitConstructor(constructor)
    }

    override fun visitMethod(method: MethodItem) {
        delegate.visitMethod(method)
    }

    override fun visitField(field: FieldItem) {
        delegate.visitField(field)
    }

    override fun visitProperty(property: PropertyItem) {
        delegate.visitProperty(property)
    }
}
