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

package com.android.tools.metalava.model.annotation.binding

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.asBoolean
import com.android.tools.metalava.model.value.asInt
import com.android.tools.metalava.model.value.asLong
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.reporter.Issues
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * Binder that can create an instance of [kClass] from an [AnnotationItem] that uses the
 * [annotationDefaults].
 */
internal class AnnotationBinding<T : Any>(
    private val kClass: KClass<T>,
    private val annotationDefaults: AnnotationDefaults?,
) : AnnotationBindingFactory<T> {
    /**
     * The [KFunction] representing the constructor that will be used to create the instance of
     * [kClass].
     */
    private val bindingConstructor = kClass.selectBindingConstructor()

    /**
     * Map from attribute name to the [ParameterBinder] that will bind that attributes values to a
     * parameter in [bindingConstructor].
     */
    private val bindersByName =
        bindingConstructor.parameters
            .mapNotNull { parameter ->
                val name =
                    parameter.name
                        ?: error(
                            "internal error: ${kClass.qualifiedName}: Cannot get name for parameter ${parameter.index}"
                        )
                val type = parameter.type
                val parameterKClass =
                    type.classifier as? KClass<*> ?: error("internal error: Cannot support $type")

                val nullableConverter = converterForType(type) ?: return@mapNotNull null

                // Get the default value specified in the annotation class declaration, if any.
                val default = annotationDefaults?.get(name)
                val defaultProvider: DefaultProvider? =
                    if (default != null) {
                        // Wrap the default [Value] in a lambda that will convert it into an
                        // appropriate value for the parameter.
                        {
                            val any = nullableConverter(default)
                            if (any == null) {
                                reportInvalidAttributeValue(
                                    name,
                                    default,
                                    parameter.type,
                                    label = "default ",
                                )
                            }
                            any
                        }
                    } else if (parameterKClass == Item::class) {
                        // A parameter of type Item must be bound to the annotated item. This makes
                        // the annotated item the default value of the parameter.
                        { item }
                    } else {
                        null
                    }

                // Get a zero value to use as a last resort when a parameter is required but no
                // value has been provided.
                val zero =
                    if (type.isMarkedNullable) {
                        // If the parameter is nullable then always use a `null` as the `zero`
                        // value.
                        null
                    } else {
                        // Otherwise, compute one for the type.
                        zeroValueForNonNullableType(type)
                    }

                ParameterBinder(
                    name,
                    parameter,
                    nullableConverter,
                    defaultProvider,
                    zero,
                )
            }
            .associateBy { it.name }

    override fun createInstanceFrom(
        annotationItem: AnnotationItem,
        item: Item,
    ): T? {
        val context = ConverterContext(annotationItem, item)

        // Construct a map from KParameter to the value of the parameter's type to pass for that
        // parameter to the bindingConstructor.
        val argsByName = buildMap {
            // Iterate over the annotation attributes, converting their values to the appropriate
            // type for the parameter.
            for (attribute in annotationItem.attributes) {
                val name = attribute.name
                val binder = bindersByName[name] ?: continue
                val value = attribute.value
                binder.bindToValue(context, this, value)
            }

            // Now iterate over all the parameter binders adding any additional defaults needed.
            for (binder in bindersByName.values) {
                binder.bindToDefault(context, this)
            }
        }

        // If an error was encountered during binding then return `null`.
        if (context.doNotInstantiate) {
            return null
        }

        // Construct an instance passing in parameters by name. While great care is taken to avoid
        // it this could throw an exception if there is some inconsistency between the annotation
        // definition/use and the binding constructor parameters. If the exception could only be
        // caused by errors within Metalava then it would be acceptable to have it abort processing
        // but this could possibly be caused by changes to sources too. Aborting processing in that
        // case would not be helpful so this catches it and reports it, returning `null` in the
        // process.
        return try {
            bindingConstructor.callBy(argsByName)
        } catch (e: Exception) {
            item.codebase.reporter.report(
                Issues.INVALID_ANNOTATION_BINDING,
                item,
                "internal error: could not bind ${kClass.qualifiedName} to ${annotationItem.qualifiedName}: ${e.message}",
                location = annotationItem.fileLocation
            )
            null
        }
    }

    /** Binds a specific [parameter] to the value of an attribute [name]. */
    private class ParameterBinder(
        /** The parameter name, provided here as [KParameter.name] is nullable. */
        val name: String,

        /** The [KParameter] to bound. */
        private val parameter: KParameter,

        /** The [ValueConverter] that may produce a `null` value. */
        private val nullableConverter: ValueConverter<*>,

        /** The default [Value] as provided by the annotation definition in the sources. */
        private val defaultProvider: DefaultProvider?,

        /**
         * The zero value, used as a last resort for non-nullable parameters that have no
         * [defaultProvider] and are not optional.
         *
         * If this is used then it indicates a problem in the sources somewhere.
         */
        private val zero: Any?,
    ) {
        /** Indicates whether the parameter is nullable. */
        private val nullable: Boolean = parameter.type.isMarkedNullable

        /** Convert this [Value] within [context] to a value appropriate for [parameter]. */
        private fun Value.convert(context: ConverterContext) = context.nullableConverter(this)

        /**
         * Bind this [parameter] in [map] to the result of converting [value] to the parameter's
         * type using [context].
         */
        fun bindToValue(
            context: ConverterContext,
            map: MutableMap<KParameter, Any?>,
            value: Value
        ) {
            val any = value.convert(context)
            map[parameter] = any
            if (any == null) {
                context.reportInvalidAttributeValue(name, value, parameter.type)
            }
        }

        /**
         * If this [parameter] is not already bound in [map] then bind it to the result of
         * [defaultProvider], falling back to [zero] if necessary.
         */
        fun bindToDefault(context: ConverterContext, map: MutableMap<KParameter, Any?>) {
            // If the parameter has already been set to a non-null value then there is no need to
            // provide a default.
            if (map[parameter] != null) return

            // If the parameter is nullable and not yet been set then set it to `null`.
            if (nullable) {
                map[parameter] = null
                return
            }

            // If the annotation definition provides a default then use it.
            defaultProvider?.let { provider ->
                context.provider()?.let {
                    map[parameter] = it
                    return
                }
            }

            // At this point if no parameter was set in the map there is a required attribute that
            // has not been provided so report it missing.
            if (parameter !in map) {
                context.reportMissingAttribute(name)
            }

            // If the parameter is optional then it has its own default.
            if (parameter.isOptional) return

            // Otherwise, fallback to the zero value, if possible.
            if (zero == null) {
                // Null is not a valid value for the parameter. If it was then this function would
                // have returned above. That means that an instance cannot be instantiated.
                context.doNotInstantiate()
            } else {
                map[parameter] = zero
            }
        }
    }

    /** Factory for creating [ValueConverter]s from [KType]. */
    private interface ConverterFactory {
        /** Create a [ValueConverter] from [type]. */
        fun createConverter(type: KType): ValueConverter<*>
    }

    /**
     * Creates [ValueConverter] that will convert a [Value] to a [List] of some element type `E`.
     *
     * The values of element type `E` will be converted from [Value]s by a [ValueConverter]
     * retrieved by [converterForType].
     */
    private class ListConverterFactory : ConverterFactory {
        override fun createConverter(type: KType): ValueConverter<List<*>> {
            val elementType = type.arguments[0].type ?: error("Star projections are not supported")
            val elementConverter =
                converterForType(elementType) ?: error("unsupported element type: $elementType")
            return { value ->
                val context = this
                value.asFlatList().mapNotNull { context.elementConverter(it) }
            }
        }
    }

    /** Creates [ValueConverter] that will bind a nested [AnnotationItem] to a class. */
    private class NestedBindingConverterFactory<T : Any> : ConverterFactory {
        override fun createConverter(type: KType): ValueConverter<T> {
            @Suppress("UNCHECKED_CAST") val bindingClass = type.classifier as KClass<T>
            return { value ->
                val annotationItem = annotationItemConverter(value)
                annotationItem?.bindTo(bindingClass, item)
            }
        }
    }

    companion object {
        /**
         * Select the binding constructor to use on this [KClass].
         *
         * If [KClass] only has a single constructor that is returned. Otherwise, the constructor
         * that is annotated with [BindingConstructor] is returned.
         */
        private fun <T : Any> KClass<T>.selectBindingConstructor(): KFunction<T> {
            val constructors = constructors
            return when (constructors.size) {
                0 ->
                    error(
                        "Cannot create an instance of ${this.qualifiedName} as it has no constructors"
                    )
                1 -> constructors.single()
                else -> {
                    val annotated =
                        constructors.filter { constructor ->
                            constructor.annotations.any { annotation ->
                                annotation is BindingConstructor
                            }
                        }
                    when (annotated.size) {
                        0 ->
                            error(
                                "Found multiple constructors in ${this.qualifiedName}; please annotate one with @BindingConstructor"
                            )
                        1 -> annotated.single()
                        else ->
                            error(
                                "Found multiple constructors in ${this.qualifiedName} that are annotated with @BindingConstructor, please annotate only one"
                            )
                    }
                }
            }
        }

        /** Create a [ValueConverter] to type [T], using [conversion]. */
        private inline fun <reified T : Any> MutableMap<KClass<*>, ValueConverter<*>>
            .registerValueConverter(
            noinline conversion: ValueConverter<T>,
        ) = conversion.also { put(T::class, it) }

        /** Map from [KClass] to the [ValueConverter]. */
        private val valueConvertersByKClass = buildMap {
            // Convert from a [Value] to an [AnnotationItem].
            registerValueConverter { value -> (value as? AnnotationValue)?.annotationItem }

            // Converter from a [Value] to an [ArrayElementValue].
            registerValueConverter { value -> value as? ArrayElementValue }

            // Converter from a [Value] to a [Boolean].
            registerValueConverter { value -> value.asBoolean() }

            // Converter from a [Value] to a [TypeItem].
            val typeItemConverter = registerValueConverter { value ->
                (value as? ClassObjectValue)?.typeItem
            }

            // Converter from a [Value] to a [ClassItem].
            registerValueConverter { value ->
                typeItemConverter(this, value)?.let { typeItem ->
                    (typeItem as? ClassTypeItem)?.let { classTypeItem ->
                        val qualifiedName = classTypeItem.qualifiedName
                        classResolver.resolveClass(qualifiedName)
                    }
                }
            }

            // Converter from a [Value] to an [Int].
            registerValueConverter { value -> value.asInt() }

            // Converter from a [Value] to a [Long].
            registerValueConverter { value -> value.asLong() }

            // Converter from a [Value] to a [String].
            registerValueConverter { value -> value.asString() }
        }

        /** Convert a [Value] to an [AnnotationItem]. */
        private val annotationItemConverter = converterForClass<AnnotationItem>()

        /** Get the [ValueConverter] to use for converting [Value]s to [T]. */
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T : Any> converterForClass() =
            valueConvertersByKClass[T::class] as ValueConverter<T?>

        /** Map from [KClass] to the [ConverterFactory]. */
        private val valueConverterFactoryByKClass = buildMap {
            // [ConverterFactory] that will convert to a [List] of elements.
            put(List::class, ListConverterFactory() as ConverterFactory)
        }

        /** Create [ValueConverter] that will bind a nested [AnnotationItem] to a nested class. */
        private val nestedBindingConverterFactory = NestedBindingConverterFactory<Any>()

        /** Get the [ValueConverter] to use for converting [Value]s to [type]. */
        private fun converterForType(type: KType): (ValueConverter<*>)? {
            val classifier = type.classifier
            val kClass = classifier as? KClass<*> ?: return null

            // If binding to the annotated item then there is no value to convert. This returns a
            // non-null converter to prevent an error being reported. The converter returns null to
            // ensure that if a value was provided for a parameter of that type that it would
            // report an error.
            if (kClass == Item::class) {
                return { null }
            }

            // Try one of the built-in conversions first.
            valueConvertersByKClass[kClass]?.let {
                return it
            }

            // Get the factory for the classifier.
            val factory =
                converterFactoryForKClass(kClass)
                    // No factory was found so return `null` to indicate that the type is not
                    // supported.
                    ?: return null

            // Create the converter from the type.
            return factory.createConverter(type)
        }

        /** Get the [ConverterFactory], if any, for [kClass] */
        private fun converterFactoryForKClass(kClass: KClass<*>): ConverterFactory? {
            valueConverterFactoryByKClass[kClass]?.let {
                return it
            }

            // Ignore classes that cannot be created from an annotation.
            if (kClass.isAbstract || kClass.isValue || kClass.isInner || kClass.java.isPrimitive)
                return null

            // Use the nested binding factory.
            return nestedBindingConverterFactory
        }

        /**
         * Get a zero value for [type], or `null` if none could be found.
         *
         * Ignores [KType.isMarkedNullable] on [type].
         */
        private fun zeroValueForNonNullableType(type: KType) =
            when (val classifier = type.classifier) {
                null -> null
                Boolean::class -> false
                Int::class -> 0
                Long::class -> 0L
                String::class -> ""
                List::class -> emptyList<Any>()
                else ->
                    if (classifier.javaClass.isPrimitive)
                        error("Must provide a zero value for primitive type $classifier")
                    else null
            }
    }
}

/** Lambda for converting [Value] to a value of type [T] using the [ConverterContext]. */
internal typealias ValueConverter<T> = ConverterContext.(Value) -> T?

/**
 * Lambda for providing a default value, which may involve converting from a [Value] using the
 * [ConverterContext].
 */
internal typealias DefaultProvider = ConverterContext.() -> Any?

/** Contextual information provided to [ValueConverter]s. */
internal class ConverterContext(
    /** The [AnnotationItem] being converted, may be nested. */
    val annotation: AnnotationItem,

    /** The [Item] to which the [annotation] belongs. */
    val item: Item,
) {
    /** Tracks whether an error was encountered which would prevent instantiation. */
    var doNotInstantiate = false
        private set

    /** Report that attribute [name]'s [value] cannot be converted to [type]. */
    fun reportInvalidAttributeValue(name: String, value: Value, type: KType, label: String = "") {
        item.codebase.reporter.report(
            Issues.INVALID_ANNOTATION_BINDING,
            item,
            "Attribute '$name' is invalid: $label`${value.toValueString()}` cannot be converted to $type",
            location = annotation.fileLocation
        )

        // There was some inconsistency encountered between the source annotation instance and the
        // bound class so do not instantiate an instance.
        doNotInstantiate = true
    }

    /** Report that no value was provided for the required attribute [name]. */
    fun reportMissingAttribute(name: String) {
        item.codebase.reporter.report(
            Issues.MISSING_REQUIRED_ATTRIBUTE,
            item,
            "Required attribute '$name' is missing on @${annotation.originalName}",
            location = annotation.fileLocation
        )
    }

    /**
     * An error that could not be compensated for and which will cause instantiation to fail was
     * found.
     */
    fun doNotInstantiate() {
        doNotInstantiate = true
    }

    /** The [ClassResolver] that can be used to resolve [ClassTypeItem]s to [ClassItem]. */
    val classResolver: ClassResolver = annotation.annotationContext
}
