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

package com.android.tools.metalava.model.source.doc

/**
 * The order of entries in this defines the order of block tags as specified
 * [here](https://www.oracle.com/uk/technical-resources/articles/java/javadoc-tool.html#tag).
 */
enum class BlockTagOrder(
    /** The optional custom tag type name if the lower case [name] is not correct. */
    private val customTagTypeName: String? = null,
) {
    /**
     * Not actually a block tag but is sometimes used as such in Android. Doclava does support it
     * but it really needs to appear at the beginning of the block tag list.
     */
    INHERIT_DOC("inheritDoc"),
    AUTHOR,
    VERSION,
    PARAM,
    RETURN,
    ATTR,
    THROWS,
    SEE,
    SINCE,
    SERIAL,
    SERIAL_DATA("serialData"),
    SERIAL_FIELD("serialField"),
    DEPRECATED,
    HIDE,
    API_SINCE("apiSince"),
    SDK_EXT_SINCE("sdkExtSince"),
    DEPRECATED_SINCE("deprecatedSince"),

    /** Unknown tag types appear at this point in the order. */
    UNKNOWN,
    ;

    /** The name of the [TagType] to which this applies. */
    val tagTypeName
        get() = customTagTypeName ?: name.lowercase()

    companion object {
        /**
         * Map from tag type name to the order for all [BlockTagOrder] instances except [UNKNOWN].
         */
        private val nameToOrder = buildMap {
            for (entry in BlockTagOrder.entries) {
                if (entry != UNKNOWN) {
                    put(entry.tagTypeName, entry.ordinal)
                }
            }
        }

        /** The default ordinal for an unknown tag type. */
        private val defaultOrdinal = UNKNOWN.ordinal

        /**
         * Get the ordinal for [tagTypeName], defaulting to [defaultOrdinal] for unknown tag types.
         */
        fun ordinalForTagType(tagTypeName: String) = nameToOrder[tagTypeName] ?: defaultOrdinal
    }
}
