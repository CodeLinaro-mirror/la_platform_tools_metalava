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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.KOTLIN_CONTINUATION
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.psi.kotlin.KaTypeItemFactory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.getContainingUMethod

/**
 * A wrapper for a [KaType] and the [PsiElement] that is the use site.
 *
 * The [KaSession] and [KaType] provided in the constructor is not stored. They are used to ensure
 * the provided type is fully expanded, and then the [KaType] is converted to a [KaTypePointer].
 * Further computations with the [KaTypePointer] use a fresh [KaSession] based on the [context].
 */
@OptIn(KaExperimentalApi::class)
internal open class KotlinTypeInfo
private constructor(
    analysisSession: KaSession?,
    kaType: KaType?,
    val context: PsiElement,
    /**
     * A [KaType] for a class contains information about the type parameters for all levels of outer
     * class types. This represents which level to use (0 is the innermost class).
     */
    private val classLevelFromInnermost: Int = 0,
) {
    constructor(context: PsiElement) : this(null, null, context)

    /** Make sure that any typealiases are fully expanded. */
    private val kaTypePointer: KaTypePointer<KaType>? =
        analysisSession?.run { kaType?.fullyExpandedType?.createPointer() }
            ?: kaType?.let {
                error("cannot have non-null kaType ($kaType) with a null analysisSession")
            }

    override fun toString(): String {
        val kaTypeString = analyze { kaType -> kaType.toString() }
        return "KotlinTypeInfo($kaTypeString for $context)"
    }

    /** Runs the [action] in a new analysis scope using the [context], if it is a [KtElement]. */
    protected fun <R> analyze(action: KaSession.(KaType) -> R): R? {
        return (context as? KtElement)?.let {
            analyze(it) { kaTypePointer?.restore()?.let { kaType -> this.action(kaType) } }
        }
    }

    /**
     * Creates a new [KotlinTypeInfo] with the same values as this one except for the
     * [classLevelFromInnermost] (if provided) and the `kaType`, which will be computed based on
     * [computeKaType] in the context of the a [KaSession] created by [analyze].
     */
    private fun copy(
        classLevelFromInnermost: Int = this.classLevelFromInnermost,
        computeKaType: (KaSession.(KaType) -> KaType?),
    ): KotlinTypeInfo {
        return analyze { kaType -> copy(this, computeKaType(kaType), classLevelFromInnermost) }
            // An analysis session could not be created
            ?: KotlinTypeInfo(null, null, context, classLevelFromInnermost)
    }

    /**
     * Creates a new [KotlinTypeInfo] with the same values as this one except for the [kaType] and
     * [classLevelFromInnermost] (if provided).
     */
    private fun copy(
        analysisSession: KaSession,
        kaType: KaType?,
        classLevelFromInnermost: Int = this.classLevelFromInnermost
    ) = KotlinTypeInfo(analysisSession, kaType, context, classLevelFromInnermost)

    /**
     * Finds the nullability of the [KaType] this represents. If there is no [KaType], defaults to
     * `null` to allow for other sources, like annotations and inferred nullability to take effect.
     */
    fun nullability(): TypeNullability? {
        return KaTypeItemFactory.run { analyze { kaType -> typeNullability(kaType) } }
    }

    /** Checks whether the [KaType] this represents is a value class type. */
    fun isValueClassType(): Boolean {
        return analyze { kaType -> typeForValueClass(kaType) } ?: false
    }

    /**
     * Creates [KotlinTypeInfo] for the component type of the [KaType] this represents, assuming it
     * is an array.
     */
    fun forArrayComponentType(): KotlinTypeInfo {
        return copy { kaType -> kaType.arrayElementType }
    }

    /**
     * Creates [KotlinTypeInfo] for the type argument at [index] of this [KotlinTypeInfo], assuming
     * it is a class type.
     */
    open fun forTypeArgument(index: Int): KotlinTypeInfo {
        return copy { kaType ->
            when (kaType) {
                is KaClassType -> {
                    // Find which level of type qualifiers to use. The qualifiers are in order
                    // from outermost to innermost class, and the [classLevelFromInnermost]
                    // starts at 0 for the innermost class.
                    val innermostClassIndex = kaType.qualifiers.lastIndex
                    val thisClassIndex = innermostClassIndex - classLevelFromInnermost
                    val thisClass = kaType.qualifiers.getOrNull(thisClassIndex)
                    thisClass?.typeArguments?.getOrNull(index)?.type
                }
                else -> null
            }
        }
    }

    /**
     * Creates [KotlinTypeInfo] for the outer class type of the [KaType] this represents, assuming
     * it is a class.
     *
     * Uses the same [KaType], but increments the [classLevelFromInnermost].
     */
    fun forOuterClass(): KotlinTypeInfo {
        return copy(classLevelFromInnermost = classLevelFromInnermost + 1) { kaType ->
            // Only keep using the kaType if the outer class level exists.
            kaType.takeIf {
                // If the kaType isn't a class, don't use it for an outer class.
                val finalClassIndex =
                    (kaType as? KaClassType)?.qualifiers?.lastIndex ?: return@takeIf false
                // Don't take the kaType if class level is already at the last of the outer classes.
                finalClassIndex > classLevelFromInnermost
            }
        }
    }

    /** Whether this represents a [KaFunctionType]. */
    fun isLambdaType(): Boolean {
        return analyze { kaType -> kaType is KaFunctionType } ?: false
    }

    /**
     * Converts this [KotlinTypeInfo] to a [KotlinTypeInfo.LambdaType]. This will fail if this does
     * not represent a [KaFunctionType].
     */
    fun asLambdaType(): LambdaType {
        return analyze { kaType ->
            kaType as KaFunctionType
            LambdaType(
                this,
                kaType,
                context,
                isSuspend = kaType.isSuspend,
                hasReceiver = kaType.hasReceiver,
                overrideTypeArguments =
                    // Compute a set of [KtType]s corresponding to the type arguments in the
                    // underlying `kotlin.jvm.functions.Function*`.
                    buildList {
                        // The optional lambda receiver is the first type argument.
                        kaType.receiverType?.let { add(copy(this@analyze, kaType = it)) }
                        // The lambda's explicit parameters appear next.
                        kaType.parameterTypes.mapTo(this) { copy(this@analyze, kaType = it) }
                        // A `suspend` lambda is transformed by Kotlin in the same way that a
                        // `suspend` function is, i.e. an additional continuation parameter is
                        // added at the end of the explicit parameters that encapsulates the
                        // return type and the return type is changed to `Any?`.
                        if (kaType.isSuspend) {
                            // Create a KotlinTypeInfo for the continuation parameter that
                            // encapsulates the actual return type.
                            add(forSyntheticContinuationParameter(kaType.returnType))
                            // Add the `Any?` for the return type.
                            add(nullableAny())
                        } else {
                            // As it is not a `suspend` lambda add the return type last.
                            add(copy(this@analyze, kaType = kaType.returnType))
                        }
                    },
            )
        } ?: error("Cannot create lambda type for $this")
    }

    /** Get a [KotlinTypeInfo] that represents a suspend function's `Continuation` parameter. */
    private fun forSyntheticContinuationParameter(returnType: KaType): KotlinTypeInfo {
        // This cast is safe as this will only be called for a lambda function whose context will
        // be [KtFunction].
        val ktElement = context as KtElement
        return analyze(ktElement) { syntheticContinuationParameter(context, returnType) }
    }

    /** Get a [KotlinTypeInfo] that represents `Any?`. */
    fun nullableAny(): KotlinTypeInfo {
        return copy { builtinTypes.nullableAny }
    }

    companion object {
        /**
         * Creates a [KotlinTypeInfo] instance from the given [context], with null values if the
         * [KaType] for the [context] can't be resolved.
         */
        fun fromContext(context: PsiElement): KotlinTypeInfo {
            return if (context is KtElement) {
                fromKtElement(context, context)
            } else {
                when (val sourcePsi = (context as? UElement)?.sourcePsi) {
                    is KtElement -> fromKtElement(sourcePsi, context)
                    else -> {
                        typeFromSyntheticElement(context)
                    }
                }
            } ?: KotlinTypeInfo(context)
        }

        /**
         * Try and compute [KotlinTypeInfo] from a [KtElement].
         *
         * Multiple different [PsiElement] subclasses can be generated from the same [KtElement] and
         * require different views of its types. The [context] is provided to differentiate between
         * them.
         */
        private fun fromKtElement(ktElement: KtElement, context: PsiElement): KotlinTypeInfo? =
            when (ktElement) {
                is KtProperty -> {
                    analyze(ktElement) {
                        val kaType =
                            when {
                                // If the context is the backing field then use the type of the
                                // delegate, if any.
                                context is UField -> ktElement.delegateExpression?.expressionType
                                else -> null
                            } ?: ktElement.returnType
                        KotlinTypeInfo(this, kaType, ktElement)
                    }
                }
                is KtCallableDeclaration -> {
                    analyze(ktElement) {
                        val kaType =
                            if (ktElement is KtFunction && ktElement.isSuspend()) {
                                // A suspend function is transformed by Kotlin to return Any?
                                // instead of its actual return type.
                                builtinTypes.nullableAny
                            } else {
                                ktElement.returnType
                            }
                        KotlinTypeInfo(this, kaType, ktElement)
                    }
                }
                is KtTypeReference ->
                    analyze(ktElement) { KotlinTypeInfo(this, ktElement.type, ktElement) }
                is KtPropertyAccessor ->
                    analyze(ktElement) { KotlinTypeInfo(this, ktElement.returnType, ktElement) }
                is KtClass -> {
                    analyze(ktElement) {
                        // If this is a named class or object then return a KotlinTypeInfo for the
                        // class. If it is generic then the type parameters will be used as the
                        // type arguments.
                        (ktElement.symbol as? KaNamedClassSymbol)?.let { symbol ->
                            KotlinTypeInfo(this, symbol.defaultType, ktElement)
                        }
                    }
                }
                is KtTypeAlias -> {
                    analyze(ktElement) {
                        KotlinTypeInfo(this, ktElement.getTypeReference()?.type, ktElement)
                    }
                }
                else -> null
            }

        /**
         * Try and compute the type from a synthetic elements, e.g. a property setter.
         *
         * In order to get this far the [context] is either not a [UElement], or it has a null
         * [UElement.sourcePsi]. That means it is most likely a parameter in a synthetic method
         * created for use by code that operates on a "Psi" view of the source, i.e. java code. This
         * method will attempt to reverse engineer the "Kt" -> "Psi" mapping to find the real Kotlin
         * types.
         */
        private fun typeFromSyntheticElement(context: PsiElement): KotlinTypeInfo? {
            // If this is not a UParameter in a UMethod then it is an unknown synthetic element so
            // just return.
            val containingMethod = (context as? UParameter)?.getContainingUMethod() ?: return null

            // Get the parameter index from the containing methods `uastParameters` as the parameter
            // is a `UParameter`.
            val parameterIndex = containingMethod.uastParameters.indexOf(context)

            return when (val sourcePsi = containingMethod.sourcePsi) {
                is KtProperty -> {
                    // This is the parameter of a synthetic setter, so get its type from the
                    // containing method.
                    fromContext(containingMethod)
                }
                is KtParameter -> {
                    // The underlying source representation of the synthetic method is a parameter,
                    // most likely a parameter of the primary constructor. In which case the
                    // synthetic method is most like a property setter. Whatever it may be, use the
                    // type of the parameter as it is most likely to be the correct type.
                    fromKtElement(sourcePsi, context)
                }
                is KtClass -> {
                    // The underlying source representation of the synthetic method is a whole
                    // class.
                    typeFromKtClass(parameterIndex, containingMethod, sourcePsi)
                }
                is KtFunction -> {
                    if (
                        sourcePsi.isSuspend() &&
                            parameterIndex == containingMethod.parameters.size - 1
                    ) {
                        // Compute the [KotlinTypeInfo] for the suspend function's synthetic
                        // [kotlin.coroutines.Continuation] parameter.
                        analyze(sourcePsi) {
                            val returnKaType = sourcePsi.returnType
                            syntheticContinuationParameter(sourcePsi, returnKaType)
                        }
                    } else {
                        // Find the KtParameter with the same index as the UParameter to use as the
                        // source psi.
                        fromKtElement(sourcePsi.valueParameters[parameterIndex], context)
                    }
                }
                is KtPropertyAccessor ->
                    analyze(sourcePsi) {
                        // Getters and setters are always the same type as the property so use its
                        // type.
                        fromKtElement(sourcePsi.property, context)
                    }
                else -> null
            }
        }

        /** Check if this is a `suspend` function. */
        private fun KtFunction.isSuspend() = modifierList?.hasSuspendModifier() == true

        /**
         * Create a [KotlinTypeInfo] that represents the continuation parameter of a `suspend`
         * function with [returnKaType] (`Continuation<$returnType$>`)
         */
        internal fun KaSession.syntheticContinuationParameter(
            context: PsiElement,
            returnKaType: KaType
        ): KotlinTypeInfo {
            val continuationKaType = buildClassType(continuationClassId) { argument(returnKaType) }
            return KotlinTypeInfo(this, continuationKaType, context)
        }

        /**
         * The [ClassId] of the Kotlin `Continuation` class, used by
         * [syntheticContinuationParameter].
         */
        private val continuationClassId by lazy {
            val continuationFqName = FqName(KOTLIN_CONTINUATION)
            val continuationPackageFqName = continuationFqName.parent()
            val continuationClassName = continuationFqName.shortName()
            ClassId(continuationPackageFqName, continuationClassName)
        }

        /** Try and get the type for [parameterIndex] in [containingMethod] from the [ktClass]. */
        private fun typeFromKtClass(
            parameterIndex: Int,
            containingMethod: UMethod,
            ktClass: KtClass
        ) =
            when {
                ktClass.isData() && containingMethod.name == "copy" -> {
                    // The parameters in the copy constructor correspond to the parameters in the
                    // primary constructor so find the corresponding parameter in the primary
                    // constructor and use its type.
                    ktClass.primaryConstructor?.let { primaryConstructor ->
                        val ktParameter = primaryConstructor.valueParameters[parameterIndex]
                        analyze(ktParameter) {
                            KotlinTypeInfo(
                                this,
                                ktParameter.returnType,
                                ktParameter,
                            )
                        }
                    }
                }
                else -> null
            }

        // Mimic `typeForValueClass` in
        // `org.jetbrains.kotlin.light.classes.symbol.classes.symbolLightClassUtils.kt`
        private fun KaSession.typeForValueClass(type: KaType): Boolean {
            val symbol = type.expandedSymbol as? KaNamedClassSymbol ?: return false
            return symbol.isInline
        }
    }

    /** Represents the information for a [KaFunctionType] */
    internal class LambdaType(
        analysisSession: KaSession?,
        kaType: KaType,
        context: PsiElement,
        /**
         * Override list of type arguments with the type arguments as seen by the JVM version of
         * this type, which will be the (optional) receiver, lambda parameter types, and return type
         * (or a continuation type and Any? return type for suspend lambdas).
         */
        private val overrideTypeArguments: List<KotlinTypeInfo>,
        /** Whether this is a suspend lambda. */
        val isSuspend: Boolean,
        /** Whether this lambda has a receiver. */
        val hasReceiver: Boolean,
    ) :
        KotlinTypeInfo(
            analysisSession,
            kaType,
            context,
        ) {

        /** Returns the type argument at the [index] as seen by the JVM version of this type. */
        override fun forTypeArgument(index: Int): KotlinTypeInfo {
            return overrideTypeArguments.getOrNull(index) ?: KotlinTypeInfo(context)
        }
    }
}
