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

import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.type.AnnoInfo

/** Global context that is shared between [TurbineCodebaseInitialiser] and [TurbineClassBuilder]. */
internal interface TurbineGlobalContext {
    /** The [DefaultCodebase] being built. */
    val codebase: DefaultCodebase

    /** Cache of [TurbineSourceFile]s. */
    val sourceFileCache: TurbineSourceFileCache

    /** Factory for creating [DefaultItem] implementations. */
    val itemFactory: DefaultItemFactory

    /** Factory for creating [DefaultAnnotationItem]s from [AnnoInfo] objects. */
    val annotationFactory: TurbineAnnotationFactory

    val valueFactory: TurbineValueFactory

    /** True if comments should be read, false otherwise. */
    val allowReadingComments: Boolean

    /** Find the [TypeBoundClass] for the `ClassSymbol`. */
    fun typeBoundClassForSymbol(classSymbol: ClassSymbol): TypeBoundClass

    /**
     * Create a [TurbineFieldResolver] for resolving fields from within
     * [classSymbol]/[sourceTypeBoundClass].
     */
    fun createFieldResolver(
        classSymbol: ClassSymbol,
        sourceTypeBoundClass: SourceTypeBoundClass,
    ): TurbineFieldResolver
}
