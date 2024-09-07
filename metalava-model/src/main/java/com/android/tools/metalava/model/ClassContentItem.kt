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
 * An [Item] that can be the content of a [ClassItem].
 *
 * i.e.
 * * Nested [ClassItem]s.
 * * Class members:
 *     * Constructors
 *     * Methods
 *     * Fields
 *     * Properties
 * * Parameters
 *
 * Basically, every [Item] except [PackageItem].
 */
interface ClassContentItem : Item {

    /**
     * The origin of this item.
     *
     * If this [Item] was copied from a class in the class path into a source class then this will
     * return [ClassOrigin.CLASS_PATH].
     */
    val origin: ClassOrigin
        get() =
            if (codebase.isFromClassPath()) ClassOrigin.CLASS_PATH
            else
                containingClass()?.origin
                    ?:
                    // This should never happen as this will only be called for a top level class
                    // and
                    // ClassItem implementation should override this method.
                    error("unknown origin")
}
