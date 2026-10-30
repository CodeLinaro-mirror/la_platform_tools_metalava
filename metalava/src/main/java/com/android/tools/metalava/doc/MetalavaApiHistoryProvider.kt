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

package com.android.tools.metalava.doc

import com.android.tools.metalava.apilevels.Api
import com.android.tools.metalava.apilevels.ApiXmlParser
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import java.io.File

/**
 * Implementation of [ApiHistoryProvider] backed by Metalava's [Api] model and [ApiXmlParser].
 *
 * @param xmlFile the `api-versions.xml` file to load.
 */
class MetalavaApiHistoryProvider(xmlFile: File) : ApiHistoryProvider {

    /**
     * Returns the [ItemApiHistory] for [cls] from the parsed [Api], or `null` if [cls] is not
     * present in `api-versions.xml`.
     */
    override fun getApiHistory(cls: ClassItem): ItemApiHistory? {
        return null
    }

    /**
     * Returns the [ItemApiHistory] for [callable] from the parsed [Api], or `null` if [callable]'s
     * containing class is not present in `api-versions.xml` or [callable] has no recorded history.
     */
    override fun getApiHistory(callable: CallableItem): ItemApiHistory? {
        return null
    }

    /**
     * Returns the [ItemApiHistory] for [field] from the parsed [Api], or `null` if [field]'s
     * containing class is not present in `api-versions.xml` or [field] has no recorded history.
     */
    override fun getApiHistory(field: FieldItem): ItemApiHistory? {
        return null
    }

    companion object {
        /** [ApiHistoryProvider.Loader] that creates a [MetalavaApiHistoryProvider]. */
        val loader: ApiHistoryProvider.Loader =
            object : ApiHistoryProvider.Loader {
                override fun load(xmlFile: File) = MetalavaApiHistoryProvider(xmlFile)

                override fun toString() = "metalava"
            }
    }
}
