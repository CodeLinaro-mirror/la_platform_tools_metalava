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

package com.android.tools.metalava.model

/**
 * An [Item] that can be selected to be a part of an API in its own right.
 *
 * e.g. a [MethodItem] is selectable because while a method's [MethodItem.containingClass] has to be
 * part of the same API just because the [ClassItem] is selected does not mean that a [MethodItem]
 * has to be.
 *
 * Conversely, a [ParameterItem] is not selectable because it cannot be selected on its own, it is
 * an indivisible part of the [ParameterItem.containingCallable].
 */
interface SelectableItem : Item {
    // At the moment this is a marker interface but over time more functionality related to
    // selection will be migrated here, e.g. [Item.hidden] and related members.
}
