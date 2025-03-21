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

import com.android.tools.metalava.model.item.ParameterDefaultValue

@MetalavaApi
interface ParameterItem : ClassContentItem, Item {
    /** The name of this field */
    fun name(): String

    /** The type of this field */
    @MetalavaApi override fun type(): TypeItem

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) =
        containingCallable()
            .findCorrespondingItemIn(codebase, superMethods = superMethods, duplicate = duplicate)
            ?.parameters()
            ?.getOrNull(parameterIndex)

    /** The containing callable. */
    fun containingCallable(): CallableItem

    /** The possible containing method, returns null if this is a constructor parameter. */
    fun possibleContainingMethod(): MethodItem? = containingCallable().let { it as? MethodItem }

    /** Index of this parameter in the parameter list (0-based) */
    val parameterIndex: Int

    /**
     * The public name of this parameter. In Kotlin, names are part of the public API; in Java they
     * are not.
     */
    fun publicName(): String?

    /**
     * Returns whether this parameter has a default value. In Kotlin, this is supported directly; in
     * Java, it's supported via a special annotation, {@literal @DefaultValue("source"). This does
     * not necessarily imply that the default value is accessible, and we know the body of the
     * default value.
     */
    fun hasDefaultValue(): Boolean

    /** The default value of this [ParameterItem]. */
    val defaultValue: ParameterDefaultValue

    /** Whether this is a varargs parameter */
    fun isVarArgs(): Boolean = modifiers.isVarArg()

    /** The property declared by this parameter; inverse of [PropertyItem.constructorParameter] */
    val property: PropertyItem?
        get() = null

    override fun parent(): CallableItem? = containingCallable()

    override val effectivelyDeprecated: Boolean
        get() = originallyDeprecated || containingCallable().effectivelyDeprecated

    override fun baselineElementId() =
        containingCallable().baselineElementId() + " parameter #" + parameterIndex

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    /**
     * Returns whether this parameter is SAM convertible or a Kotlin lambda. If this parameter is
     * the last parameter, it also means that it could be called in Kotlin using the trailing lambda
     * syntax.
     *
     * Specifically this will attempt to handle the follow cases:
     * - Java SAM interface = true
     * - Kotlin SAM interface = false // Kotlin (non-fun) interfaces are not SAM convertible
     * - Kotlin fun interface = true
     * - Kotlin lambda = true
     * - Any other type = false
     */
    fun isSamCompatibleOrKotlinLambda(): Boolean =
        // TODO(b/354889186): Implement correctly
        false

    /**
     * Create a duplicate of this for [containingCallable].
     *
     * The duplicate's [type] must have applied the [typeVariableMap] substitutions by using
     * [TypeItem.convertType].
     *
     * This is called from within the constructor of the [containingCallable] so must only access
     * its `name` and its reference. In particularly it must not access its
     * [CallableItem.parameters] property as this is called during its initialization.
     */
    fun duplicate(
        containingCallable: CallableItem,
        typeVariableMap: TypeParameterBindings,
    ): ParameterItem

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParameterItem) return false

        return parameterIndex == other.parameterIndex &&
            containingCallable() == other.containingCallable()
    }

    override fun hashCodeForItem(): Int {
        return name().hashCode()
    }

    override fun toStringForItem() = "parameter ${name()}"

    override fun containingClass(): ClassItem = containingCallable().containingClass()

    override fun containingPackage(): PackageItem? = containingCallable().containingPackage()

    // TODO: modifier list
}
