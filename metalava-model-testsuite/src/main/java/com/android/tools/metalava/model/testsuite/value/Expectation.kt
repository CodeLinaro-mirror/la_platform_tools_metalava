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
interface Expectation<T> {
    /** Get the expectations of type [T] for [producerKind] at [valueUseSite]. */
    fun expectationFor(producerKind: ProducerKind, valueUseSite: ValueUseSite): T
}

/**
 * Builder for expectations.
 *
 * This makes it easy to create a set of expectations for all possible combinations of
 * [ProducerKind] and [ValueUseSite] without duplicating effort.
 */
internal fun <T> expectations(body: ExpectationsBuilder<T>.() -> Unit): Expectation<T> {
    val builder = ExpectationsBuilder<T>()
    builder.body()
    return builder.expectations()
}

/**
 * A [ReadWriteProperty] which will store the value that is set on the property in [map] for all
 * [keys].
 */
internal class MutableMapDelegate<K, T>(
    internal val map: MutableMap<K, T>,
    internal val keys: List<K>
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = error("Cannot read value")

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        for (key in keys) {
            map[key] = value
        }
    }
}

/**
 * Populates [expectationMap] with values for all [producerKinds].
 *
 * @param producerKinds the list of [ProducerKind]s whose expectations will be set.
 */
internal open class PerProducerKindBuilder<T>(
    internal val producerKinds: List<ProducerKind>,
    val expectationMap: MutableMap<Pair<ProducerKind, ValueUseSite>, T>
) {
    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [ValueUseSite.entries].
     *
     * This must be set before any of the other properties as this will overwrite them.
     */
    var common: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.flatMap { producerKind ->
                ValueUseSite.entries.map { producerKind to it }
            }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [ValueUseSite.ATTRIBUTE_VALUE].
     */
    var attributeValue: T by
        MutableMapDelegate(expectationMap, producerKinds.map { it to ValueUseSite.ATTRIBUTE_VALUE })

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [ValueUseSite.ATTRIBUTE_DEFAULT_VALUE].
     */
    var attributeDefaultValue: T by
        MutableMapDelegate(
            expectationMap,
            producerKinds.map { it to ValueUseSite.ATTRIBUTE_DEFAULT_VALUE }
        )

    /**
     * Stores its value in [expectationMap] for the cross product of [producerKinds] and
     * [ValueUseSite.FIELD_VALUE].
     */
    var fieldValue: T by
        MutableMapDelegate(expectationMap, producerKinds.map { it to ValueUseSite.FIELD_VALUE })
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
    fun expectations(): Expectation<T> = ExpectationMap(expectationMap)

    /**
     * [Expectation] implementation that just delegates to [Map], throwing an exception if no value
     * could be found.
     */
    internal class ExpectationMap<T>(
        val map: MutableMap<Pair<ProducerKind, ValueUseSite>, T>,
    ) : Expectation<T> {
        override fun expectationFor(producerKind: ProducerKind, valueUseSite: ValueUseSite): T {
            val key = producerKind to valueUseSite
            return map[key] ?: error("Could not find expectation for $key")
        }
    }
}
