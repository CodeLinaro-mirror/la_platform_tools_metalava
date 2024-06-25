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
    /** @deprecated */
    @Deprecated
    public ClassAddedAndDeprecatedInApi30(float explicitlyDeprecated) {}

    /**
     * Do not explicitly deprecate this in API 30 as earlier releases did not consistent explicitly deprecate members of
     * deprecated classes.
     */
    public ClassAddedAndDeprecatedInApi30(int implicitlyDeprecated) {}

    /** @deprecated */
    @Deprecated
    public void methodExplicitlyDeprecated() { throw new RuntimeException("Stub!"); }

    /**
     * Do not explicitly deprecate this in API 30 as earlier releases did not consistent explicitly deprecate members of
     * deprecated classes.
     */
    public void methodImplicitlyDeprecated() { throw new RuntimeException("Stub!"); }

    /** @deprecated */
    @Deprecated
    public static final int FIELD_EXPLICITLY_DEPRECATED = 1;

    /**
     * Do not explicitly deprecate this in API 30 as earlier releases did not consistent explicitly deprecate members of
     * deprecated classes.
     */
    public static final int FIELD_IMPLICITLY_DEPRECATED = 2;
}
