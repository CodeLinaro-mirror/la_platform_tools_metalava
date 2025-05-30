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

package com.android.tools.metalava.model.turbine

import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.model.Const
import com.google.turbine.tree.Tree.ConstVarName
import com.google.turbine.tree.Tree.Expression

/**
 * A representation of a value in Turbine.
 *
 * A value could be:
 * * A literal constant, e.g. `"string"`, or `3.4`.
 * * An enum constant, e.g. `RetentionPolicy.CLASS`.
 * * A class literal.
 * * A constant field.
 * * An array of one of the above types.
 *
 * They can be used as constant field values and annotation attribute values, including default
 * values.
 *
 * It consists of two parts.
 * * [const] - this is the constant value and has been evaluated by Turbine.
 * * [expr] - the optional source representation of the value. This is `null` when the value is
 *   obtained from a binary file, e.g. the value of an annotation attribute of an annotation on a
 *   class loaded from the class path.
 *
 * The model needs information from both so this encapsulates them together to make them easier to
 * use and provide a convenient place for code that manipulate them.
 */
internal class TurbineValue(
    /** The constant object representing the annotation value. */
    val const: Const,

    /** An optional [Expression] that might provide additional context for value extraction. */
    val expr: Expression?,

    /** If available, then can be used to resolve [ConstVarName] to [TypeBoundClass.FieldInfo]. */
    val fieldResolver: TurbineFieldResolver?,
)
