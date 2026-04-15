/*
 * Copyright (C) 2026 The Android Open Source Project
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
 * An extension of [ClassItem] that is used when initially constructing [ClassItem].
 *
 * Provides mutation methods to populate the [ClassItem] with members and adjust various other
 * aspects.
 */
interface SkeletonClassItem : ClassItem {
    /** The origin of this class. */
    override var origin: ClassOrigin

    /** Support changing after construction. */
    override var classKind: ClassKind

    /** Support changing after construction. */
    override var optionalAliasedType: TypeItem?

    /** Set the super class [ClassTypeItem]. */
    fun setSuperClassType(superClassType: ClassTypeItem?)

    /** Add a field to this class. */
    fun addField(field: FieldItem)

    /** Add a property to this class. */
    fun addProperty(property: PropertyItem)

    /**
     * If there is already a property with the same signature as [property], replaces the existing
     * version with the new one. If there is not a matching property, just adds [property] to the
     * list of properties.
     */
    fun replaceOrAddProperty(property: PropertyItem)

    /** Add a constructor to this class. */
    fun addConstructor(constructor: ConstructorItem)

    /**
     * If there is already a constructor with the same signature as [constructor], replaces the
     * existing version with the new one. If there is not a matching constructor, just adds
     * [constructor] to the list of constructors.
     */
    fun replaceOrAddConstructor(constructor: ConstructorItem)

    /**
     * Replace an existing method with [method], if no such method exists then just add [method] to
     * the list of methods.
     */
    fun replaceOrAddMethod(method: MethodItem)

    /**
     * Create a [RecordComponents] object and store it in [ClassItem.recordComponents].
     *
     * Must only be called on a [ClassItem] whose [classKind] is [ClassKind.RECORD]. Will construct
     * the [RecordComponents] from all [properties] whose [PropertyItem.isRecordComponent] returns
     * `true`.
     */
    fun initializeRecordComponents()
}
