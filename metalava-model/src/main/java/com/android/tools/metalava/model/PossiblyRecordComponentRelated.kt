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

package com.android.tools.metalava.model

/**
 * Implemented by [Item]s that are related to a [RecordComponentItem].
 *
 * A [RecordComponentItem] can be related to other parts of the API as follows:
 * * The canonical [ConstructorItem] used to create the record class.
 * * A constructor [ParameterItem] used to specify and initialize the [RecordComponentItem].
 * * A getter [MethodItem] for getting the value of the [RecordComponentItem].
 */
interface PossiblyRecordComponentRelated {
    /** True if this is related to a [RecordComponentItem]. */
    val isRecordComponentRelated: Boolean

    /**
     * A description of the relationship, if any, between this and a record component.
     *
     * This will be prepending before the [Item.describe] result.
     */
    val recordComponentRelationship: String?
}
