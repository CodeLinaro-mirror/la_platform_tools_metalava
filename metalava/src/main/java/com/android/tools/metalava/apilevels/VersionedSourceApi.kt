/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.CodebaseFragment

/**
 * Supports updating an [Api] with information from the [apiVersion] of the API that is defined by
 * the [CodebaseFragment] of the sources created by [codebaseFragmentProvider].
 */
class VersionedSourceApi(
    private val codebaseFragmentProvider: () -> CodebaseFragment,
    override val apiVersion: ApiVersion,
    private val useInternalNames: Boolean,
) : VersionedApi {
    override fun updateApi(api: Api) {
        val codebaseFragment = codebaseFragmentProvider()
        addApisFromCodebase(api, apiVersion, codebaseFragment, useInternalNames)
    }
}
