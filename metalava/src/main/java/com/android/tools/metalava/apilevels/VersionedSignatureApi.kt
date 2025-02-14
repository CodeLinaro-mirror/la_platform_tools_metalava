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

import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.text.SignatureFile
import java.io.File

/**
 * Supports updating [Api] with information from the [apiVersion] of the API that is defined in the
 * signature [file].
 */
class VersionedSignatureApi(
    private val signatureFileLoader: SignatureFileLoader,
    private val file: File,
    override val apiVersion: ApiVersion,
) : VersionedApi {
    override fun updateApi(api: Api) {
        val codebase = signatureFileLoader.load(SignatureFile.fromFiles(file))
        val codebaseFragment = CodebaseFragment.create(codebase, ::NonFilteringDelegatingVisitor)
        addApisFromCodebase(api, apiVersion, codebaseFragment)
    }

    override fun toString() = "VersionedSignatureApi(file=$file, version=$apiVersion)"
}
