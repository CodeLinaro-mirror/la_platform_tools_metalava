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

package com.android.tools.metalava.cli.historical

import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.reporter.ThrowingReporter
import com.android.tools.metalava.testing.TemporaryFolderOwner
import java.io.File
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoricalApiVersionInfoTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    private fun List<HistoricalApiVersionInfo>.dump() =
        buildString {
                for (versionInfo in this@dump) {
                    append("HistoricalApiVersionInfo(\n")
                    append("  version=").append(versionInfo.version).append(",\n")
                    append("  infoBySurface={\n")
                    for (surfaceInfo in versionInfo.infoBySurface.values) {
                        append("    SurfaceInfo(\n")
                        append("      jarFile=").append(surfaceInfo.jarFile).append(",\n")
                        append("      signatureFile=")
                            .append(surfaceInfo.signatureFile)
                            .append(",\n")
                        append("    )\n")
                    }
                    append("  },\n")
                    append(")\n")
                }
            }
            .trimEnd()

    private fun scanForHistoricalApiVersionInfo(root: File): List<HistoricalApiVersionInfo> {
        val apiSurfaceNames = listOf("public", "system")

        val apiSurfaces =
            ApiSurfaces.build {
                for ((index, apiSurfaceName) in apiSurfaceNames.withIndex()) {
                    // The main surface is irrelevant at the moment because this always generates
                    // the whole API surface. However, a main surface is required so this just uses
                    // the first one as the main surface for now.
                    val isMain = index == 0
                    createSurface(apiSurfaceName, isMain = isMain)
                }
            }

        val scanConfig =
            PatternNode.ScanConfig(
                dir = root,
                apiSurfaceByName = apiSurfaces.all.associateBy { it.name },
            )

        val list =
            HistoricalApiVersionInfo.scan(
                ThrowingReporter.INSTANCE,
                jarFilePattern = "{version:major.minor?}/{surface}/api.jar",
                signatureFilePattern = "{version:major.minor?}/{surface}/api/api.txt",
                scanConfig
            )
        return list
    }

    @Test
    fun `Test simple`() {
        val root = buildFileStructure {
            // Create the version directories in non-version order to verify that the list returned
            // by scanning will be sorted by version.
            dir("2") {
                dir("public") {
                    emptyFile("api.jar")
                    dir("api") { emptyFile("api.txt") }
                }
                dir("system") {
                    emptyFile("api.jar")
                    dir("api") { emptyFile("api.txt") }
                }
            }
            dir("1") {
                dir("public") {
                    emptyFile("api.jar")
                    dir("api") { emptyFile("api.txt") }
                }
            }
        }

        val list = scanForHistoricalApiVersionInfo(root)
        assertEquals(
            """
                HistoricalApiVersionInfo(
                  version=1,
                  infoBySurface={
                    SurfaceInfo(
                      jarFile=1/public/api.jar,
                      signatureFile=1/public/api/api.txt,
                    )
                  },
                )
                HistoricalApiVersionInfo(
                  version=2,
                  infoBySurface={
                    SurfaceInfo(
                      jarFile=2/public/api.jar,
                      signatureFile=2/public/api/api.txt,
                    )
                    SurfaceInfo(
                      jarFile=2/system/api.jar,
                      signatureFile=2/system/api/api.txt,
                    )
                  },
                )
            """
                .trimIndent(),
            list.dump()
        )
    }
}
