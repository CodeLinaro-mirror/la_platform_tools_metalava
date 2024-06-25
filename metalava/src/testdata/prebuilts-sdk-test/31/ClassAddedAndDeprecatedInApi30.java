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

package android.test;

/** @deprecated */
@Deprecated
public class ClassAddedAndDeprecatedInApi30 {
    /**
     * Explicitly deprecate this in API 31 to show the impact inconsistent handling of inherited deprecation status
     * across releases has on api-versions.xml output.
     *
     * @deprecated
     */
    public ClassAddedAndDeprecatedInApi30(int implicitlyDeprecated) {}

    /** @deprecated */
    @Deprecated
    public ClassAddedAndDeprecatedInApi30(float explicitlyDeprecated) {}

    /**
     * Explicitly deprecate this in API 31 to show the impact inconsistent handling of inherited deprecation status
     * across releases has on api-versions.xml output.
     *
     * @deprecated
     */
    @Deprecated
    public void methodImplicitlyDeprecated() { throw new RuntimeException("Stub!"); }

    /** @deprecated */
    @Deprecated
    public void methodExplicitlyDeprecated() { throw new RuntimeException("Stub!"); }

    /**
     * Explicitly deprecate this in API 31 to show the impact inconsistent handling of inherited deprecation status
     * across releases has on api-versions.xml output.
     *
     * @deprecated
     */
    @Deprecated
    public static final int FIELD_IMPLICITLY_DEPRECATED = 1;

    /** @deprecated */
    @Deprecated
    public static final int FIELD_EXPLICITLY_DEPRECATED = 1;
}
