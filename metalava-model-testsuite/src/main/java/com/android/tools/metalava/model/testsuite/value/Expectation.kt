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

package com.android.tools.metalava.model.testsuite.value

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Encapsulates a set of expectations about values. */
interface Expectation<out T> {
    /** Get the expectations of type [T] for [producerKind] at [legacyValueUseSite]. */
    fun expectationFor(producerKind: ProducerKind, legacyValueUseSite: LegacyValueUseSite): T

    /**
     * Check to see if this has an expectation (which may be null) for [producerKind] at
     * [legacyValueUseSite].
     */
    fun hasExpectationFor(
        producerKind: ProducerKind,
        legacyValueUseSite: LegacyValueUseSite
    ): Boolean
}

/**
 * Builder for expectations.
 *
 * This makes it easy to create a set of expectations for all possible combinations of
 * [ProducerKind] and [LegacyValueUseSite] without duplicating effort.
 */
internal fun <T : Any> expectations(body: ExpectationsBuilder<T?>.() -> Unit) =
    nullableExpectations(optionalDefaultValueProvider = { null }, body = body)

/**
 * Builder for expectations that allows null.
 *
 * This allows the creation of full sets that throw an exception if it is not complete and partial
 * sets that return `null`.
 *
 * @param optionalDefaultValueProvider lambda that provides the default expected value when no
 *   specific one has been set. Defaults to throwing an exception.
 */
private fun <T> nullableExpectations(
    optionalDefaultValueProvider: ((ExpectationKey) -> T)? = null,
    body: ExpectationsBuilder<T>.() -> Unit
): Expectation<T> {
    val builder = ExpectationsBuilder<T>()
    builder.body()
    return builder.expectations(optionalDefaultValueProvider)
}

/**
 * Create an [Expectation] from an [Expectation] that returns `null`, i.e. a partial set, by falling
 * back to another [Expectation] that does not contain `null`, i.e. a full set.
 *
 * If `this` and [other] are the same then just return [other] as there is no point in chaining
 * them. [other] was chosen to be returned as it has the correct type.
 */
fun <T> Expectation<T?>.fallBackTo(other: Expectation<T>) =
    if (this === other) other else ChainedExpectation(this, other)

/**
 * A [ReadWriteProperty] which will store the value that is set on the property in [map] for all
 * [keys].
 */
internal class MutableMapDelegate<K, T>(
    internal val map: MutableMap<K, T>,
    private val keys: List<K>
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = error("Cannot read value")

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        for (key in keys) {
            map[key] = value
        }
    }
}

/** The key into the map of expected values in [ExpectationsBuilder.ExpectationMap]. */
private typealias ExpectationKey = Pair<ProducerKind, LegacyValueUseSite>

/**
 * Populates [expectationMap] with values for all [producerKinds].
 *
 * @param producerKinds the list of [ProducerKind]s whose expectations will be set.
 */
internal open class PerProducerKindBuilder<T>(
    private val producerKinds: List<ProducerKind>,
    val expectationMap: MutableMap<ExpectationKey, T>
) {
    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [LegacyValueUseSite.entries].
     *
     * This must be set before any of the other properties as this will overwrite them.
     */
    var common: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.flatMap { producerKind ->
                LegacyValueUseSite.entries.map { producerKind to it }
            }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [LegacyValueUseSite.ATTRIBUTE_VALUE].
     */
    var attributeValue: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.map { it to LegacyValueUseSite.ATTRIBUTE_VALUE }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [LegacyValueUseSite.ATTRIBUTE_VALUE].
     */
    var annotationToSource: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.map { it to LegacyValueUseSite.ANNOTATION_TO_SOURCE }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [LegacyValueUseSite.ATTRIBUTE_DEFAULT_VALUE].
     */
    var attributeDefaultValue: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.map { it to LegacyValueUseSite.ATTRIBUTE_DEFAULT_VALUE }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [LegacyValueUseSite.FIELD_VALUE].
     */
    var fieldValue: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.map { it to LegacyValueUseSite.FIELD_VALUE }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON].
     */
    var fieldWriteWithSemicolon: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.map { it to LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON }
        )
}

/**
 * The top level [ExpectationsBuilder].
 *
 * Setting [common] on this will set the same expectation across all tests. Setting one of the other
 * [PerProducerKindBuilder] properties will set the value across all [ProducerKind]s.
 */
internal class ExpectationsBuilder<T> :
    PerProducerKindBuilder<T>(ProducerKind.entries, mutableMapOf()) {

    /** Set expectations just for [ProducerKind.SOURCE]s. */
    fun source(body: PerProducerKindBuilder<T>.() -> Unit) {
        val builder = PerProducerKindBuilder(listOf(ProducerKind.SOURCE), expectationMap)
        builder.body()
    }

    /** Set expectations just for [ProducerKind.JAR]s. */
    fun jar(body: PerProducerKindBuilder<T>.() -> Unit) {
        val builder = PerProducerKindBuilder(listOf(ProducerKind.JAR), expectationMap)
        builder.body()
    }

    /** Get the set of expectations that have been built. */
    fun expectations(
        optionalDefaultValueProvider: ((ExpectationKey) -> T)? = null
    ): Expectation<T> {
        val defaultValueProvider =
            optionalDefaultValueProvider ?: { error("Could not find expectation for $it") }
        return ExpectationMap(expectationMap, defaultValueProvider)
    }

    /**
     * [Expectation] implementation that just delegates to [Map], throwing an exception if no value
     * could be found.
     */
    internal class ExpectationMap<T>(
        val map: MutableMap<ExpectationKey, T>,
        private val defaultValueProvider: (ExpectationKey) -> T,
    ) : Expectation<T> {
        override fun expectationFor(
            producerKind: ProducerKind,
            legacyValueUseSite: LegacyValueUseSite
        ): T {
            val key = producerKind to legacyValueUseSite
            return map[key] ?: defaultValueProvider(key)
        }

        override fun hasExpectationFor(
            producerKind: ProducerKind,
            legacyValueUseSite: LegacyValueUseSite
        ): Boolean {
            val key = producerKind to legacyValueUseSite
            return key in map
        }
    }
}

/**
 * An [Expectation] that will check in [first] and if no expected value is found (i.e.
 * [expectationFor] returns `null`) then delegate to the [second].
 */
private class ChainedExpectation<T>(
    private val first: Expectation<T?>,
    private val second: Expectation<T?>,
) : Expectation<T?> {
    override fun expectationFor(
        producerKind: ProducerKind,
        legacyValueUseSite: LegacyValueUseSite,
    ) =
        if (first.hasExpectationFor(producerKind, legacyValueUseSite)) {
            first.expectationFor(producerKind, legacyValueUseSite)
        } else second.expectationFor(producerKind, legacyValueUseSite)

    override fun hasExpectationFor(
        producerKind: ProducerKind,
        legacyValueUseSite: LegacyValueUseSite
    ) =
        first.hasExpectationFor(producerKind, legacyValueUseSite) ||
            second.hasExpectationFor(producerKind, legacyValueUseSite)
}
