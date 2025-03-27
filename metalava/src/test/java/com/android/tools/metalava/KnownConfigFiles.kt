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

package com.android.tools.metalava

import com.android.tools.metalava.testing.xml

object KnownConfigFiles {
    val configPublicSurface =
        xml(
            "config-public-surface.xml",
            """
                <config xmlns="http://www.google.com/tools/metalava/config"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                    <api-surfaces>
                        <api-surface name="public"/>
                    </api-surfaces>
                </config>
            """
        )

    val configPublicAndSystemSurfaces =
        xml(
            "config-public-and-system-surfaces.xml",
            """
                <config xmlns="http://www.google.com/tools/metalava/config"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                    <api-surfaces>
                        <api-surface name="public"/>
                        <api-surface name="system" extends="public"/>
                    </api-surfaces>
                </config>
            """
        )

    val configEmptyApiFlags =
        xml(
            "config-empty-api-flags.xml",
            """
                <config xmlns="http://www.google.com/tools/metalava/config"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                    <api-flags/>
                </config>
            """
        )
}
