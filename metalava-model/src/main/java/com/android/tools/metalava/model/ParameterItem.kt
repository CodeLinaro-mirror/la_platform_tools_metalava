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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner

@MetalavaApi
interface ParameterItem :
    ClassContentItem, Item, PossiblyPropertyRelated, PossiblyRecordComponentRelated {
    /** The name of this field */
    fun name(): String

    override fun describe(capitalize: Boolean) = buildString {
        append(if (capitalize) "Parameter" else "parameter")
        append(' ')
        append(name())
        append(" in ")
        when (val parent = parent()) {
            is CallableItem ->
                with(parent) {
                    appendCallableSignature(
                        includeParameterNames = true,
                        includeParameterTypes = true,
                    )
                }
            is PropertyItem -> append(parent.describe(capitalize = false))
        }
    }

    /** The type of this field */
    @MetalavaApi override fun type(): TypeItem

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) =
        when (val parent = parent()) {
            is CallableItem ->
                parent
                    .findCorrespondingItemIn(
                        codebase,
                        superMethods = superMethods,
                        duplicate = duplicate
                    )
                    ?.parameters()
                    ?.getOrNull(parameterIndex)
            is PropertyItem ->
                parent
                    .findCorrespondingItemIn(
                        codebase,
                        superMethods = superMethods,
                        duplicate = duplicate,
                    )
                    ?.contextParameters
                    ?.getOrNull(parameterIndex)
            else -> error("Unexpected parent of type ${parent::class.java} for $this")
        }

    /** The containing callable. */
    fun containingCallable(): CallableItem?

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

    /** Whether this is a varargs parameter */
    fun isVarArgs(): Boolean = modifiers.isVarArg()

    /** The kind of parameter this is. See the values of [ParameterKind] for more details. */
    val kind: ParameterKind

    /**
     * The property declared by this parameter; inverse of [PropertyItem.constructorParameter].
     *
     * Overridden to provide more specific documentation.
     */
    override var property: PropertyItem?

    override val isRecordComponentRelated: Boolean
        get() = containingCallable()?.isRecordComponentRelated == true

    override val recordComponentRelationship: String?
        get() = if (isRecordComponentRelated) "canonical constructor" else null

    override fun parent(): MemberItem

    override val effectivelyDeprecated: Boolean
        get() = originallyDeprecated || parent().effectivelyDeprecated

    override fun baselineElementId() =
        parent().baselineElementId() + " parameter #" + parameterIndex

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    /**
     * Create a duplicate of this for [containingItem].
     *
     * The duplicate's [ParameterItem.type] is the result of applying [typeConverter] to this
     * [ParameterItem]'s [type].
     *
     * This is called from within the constructor of the [containingItem] so must only access its
     * `name` and its reference. In particularly it must not access its [CallableItem.parameters]
     * property as this is called during its initialization.
     */
    fun duplicate(
        containingItem: MemberItem,
        typeConverter: TypeItemConverter,
        newParameterIndex: Int = parameterIndex,
    ): ParameterItem

    override val description: DocContent?
        get() = parent().documentation?.paramTagDescription(name())

    override val descriptionOwner: DocContentOwner
        get() = parent().requiredDocumentation.paramTagDescriptionOwner(name())

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParameterItem) return false

        return parameterIndex == other.parameterIndex && parent() == other.parent()
    }

    override fun hashCodeForItem(): Int {
        return name().hashCode()
    }

    override fun toStringForItem() = "parameter ${name()}"

    override fun containingClass(): ClassItem = parent().containingClass()

    override fun containingPackage(): PackageItem? = parent().containingPackage()

    override val targetLanguages: Set<TargetLanguage>
        get() = parent().targetLanguages

    // TODO: modifier list
}

/** The possible kinds of [ParameterItem]s that can be defined in Java and Kotlin. */
enum class ParameterKind {
    /**
     * Any parameter from Java source or loaded from a jar, or a value parameter from Kotlin source.
     */
    VALUE,

    /**
     * The synthetic receiver parameter generated for a Kotlin
     * [extension](https://kotlinlang.org/docs/extensions.html#receivers).
     */
    RECEIVER,

    /** A Kotlin [context parameter](https://kotlinlang.org/docs/context-parameters.html). */
    CONTEXT,

    /**
     * The synthetic
     * [continuation parameter](https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html#continuation-passing-style)
     * for a Kotlin suspend function.
     */
    CONTINUATION,
}
