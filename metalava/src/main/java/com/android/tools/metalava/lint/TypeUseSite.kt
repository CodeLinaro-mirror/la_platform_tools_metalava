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

package com.android.tools.metalava.lint

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.RecordComponentItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterListOwner
import kotlin.reflect.KClass

/** Sites where an [Item] can use a [TypeItem] */
enum class TypeUseSite(
    /** Text label that describes a [TypeUseSite]. */
    private val label: String,

    /**
     * The class on which a [TypeUseSite] can be used.
     *
     * @see describe
     */
    private val supportedClass: KClass<*>,

    /** Prefix to use [describe]. */
    private val descriptionPrefix: String = label,

    /** True for sites that are handled by the [ApiLint.legacyCheckType]. */
    val legacyCheckType: Boolean = false,
) {
    TYPE_PARAMETER(
        label = "Type parameter",
        supportedClass = TypeParameterListOwner::class,
    ),
    RECORD_COMPONENT(
        label = "Record component",
        supportedClass = RecordComponentItem::class,
        // Record components only have a single associated type so no need to differentiate them.
        descriptionPrefix = "Type",
    ),
    SUPER_CLASS(
        label = "Super class",
        supportedClass = ClassItem::class,
    ),
    INTERFACE(
        label = "Implemented interface",
        supportedClass = ClassItem::class,
    ),
    THROWS(
        label = "Throws type",
        supportedClass = CallableItem::class,
    ),
    RETURN(
        label = "Return type",
        supportedClass = CallableItem::class,
        legacyCheckType = true,
    ),
    PARAMETER(
        label = "Parameter type",
        supportedClass = ParameterItem::class,
        // Parameters only have a single associated type so no need to differentiate them.
        descriptionPrefix = "Type",
        legacyCheckType = true,
    ),
    FIELD(
        label = "Field type",
        supportedClass = FieldItem::class,
        // Fields only have a single associated type so no need to differentiate them.
        descriptionPrefix = "Type",
        legacyCheckType = true,
    ),
    ;

    /** Describe this [TypeUseSite] by [item]. */
    fun describe(item: Item): String {
        // Make sure this is being used with the correct items.
        require(supportedClass.isInstance(item)) { "Expected $item to be a $supportedClass" }
        return "$descriptionPrefix of ${item.describe()}"
    }

    override fun toString() = label
}
