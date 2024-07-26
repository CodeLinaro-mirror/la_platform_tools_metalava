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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.JAVA_LANG_ANNOTATION
import com.android.tools.metalava.model.JAVA_LANG_ENUM
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultTypeItemFactory

internal class TextTypeItemFactory(
    private val codebase: TextCodebase,
    private val typeParser: TextTypeParser,
    typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
) : DefaultTypeItemFactory<String, TextTypeItemFactory>(typeParameterScope) {

    /** A [JAVA_LANG_ANNOTATION] suitable for use as a super type. */
    val superAnnotationType
        get() = getInterfaceType(JAVA_LANG_ANNOTATION)

    /** A [JAVA_LANG_ENUM] suitable for use as a super type. */
    val superEnumType
        get() = getSuperClassType(JAVA_LANG_ENUM)

    /** A [JAVA_LANG_OBJECT] suitable for use as a super type. */
    val superObjectType
        get() = getSuperClassType(JAVA_LANG_OBJECT)

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        TextTypeItemFactory(codebase, typeParser, scope)

    override fun getType(
        underlyingType: String,
        contextNullability: ContextNullability,
        isVarArg: Boolean
    ): TypeItem {
        var typeItem =
            typeParser.obtainTypeFromString(
                underlyingType,
                typeParameterScope,
                contextNullability,
            )

        // Check if the type is an array and its component nullability needs to be updated based on
        // the context.
        val forcedComponentNullability = contextNullability.forcedComponentNullability
        if (
            typeItem is ArrayTypeItem &&
                forcedComponentNullability != null &&
                forcedComponentNullability != typeItem.componentType.modifiers.nullability
        ) {
            typeItem =
                typeItem.substitute(
                    componentType = typeItem.componentType.substitute(forcedComponentNullability),
                )
        }

        // Check if the type's nullability needs to be updated based on the context.
        val typeNullability = typeItem.modifiers.nullability
        val actualTypeNullability =
            contextNullability.compute(typeNullability, typeItem.modifiers.annotations)
        return if (actualTypeNullability != typeNullability) {
            typeItem.substitute(actualTypeNullability)
        } else typeItem
    }

    override fun getExceptionType(underlyingType: String) =
        super.getExceptionType(underlyingType).also { exceptionTypeItem ->
            if (exceptionTypeItem is ClassTypeItem) {
                codebase.requireStubKindFor(exceptionTypeItem, StubKind.THROWABLE)
            }
        }

    override fun getInterfaceType(underlyingType: String) =
        super.getInterfaceType(underlyingType).also { classTypeItem ->
            codebase.requireStubKindFor(classTypeItem, StubKind.INTERFACE)
        }
}
