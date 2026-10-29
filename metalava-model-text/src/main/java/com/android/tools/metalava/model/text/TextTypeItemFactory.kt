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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultTypeItemFactory

internal class TextTypeItemFactory(
    private val assembler: TextCodebaseAssembler,
    private val typeParser: TextTypeParser,
    typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
) : DefaultTypeItemFactory<String, TextTypeItemFactory>(typeParameterScope) {

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        TextTypeItemFactory(assembler, typeParser, scope)

    override fun getType(
        underlyingType: String,
        contextNullability: ContextNullability,
        isVarArg: Boolean
    ): TypeItem =
        typeParser.obtainTypeFromString(
            underlyingType,
            typeParameterScope,
            contextNullability,
        )

    override fun getExceptionType(underlyingType: String) =
        super.getExceptionType(underlyingType).also { exceptionTypeItem ->
            if (exceptionTypeItem is ClassTypeItem) {
                assembler.requireStubKindFor(exceptionTypeItem, StubKind.THROWABLE)
            }
        }

    override fun getInterfaceType(underlyingType: String) =
        super.getInterfaceType(underlyingType).also { classTypeItem ->
            assembler.requireStubKindFor(classTypeItem, StubKind.INTERFACE)
        }
}
