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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.KnownConfigFiles
import com.android.tools.metalava.apiSurfacesFromConfig
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.config.ConfigParser
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
                        val extendsInfo = surfaceInfo.extends
                        if (extendsInfo != null) {
                            append("      extends=${extendsInfo.surface.name},\n")
                        }
                        append("    )\n")
                    }
                    append("  },\n")
                    append(")\n")
                }
            }
            .let { replaceFileWithSymbol(it) }

    private fun buildApiSurfacesFromConfig(configFile: TestFile): ApiSurfaces {
        val config = ConfigParser.parse(listOf(configFile.createFile(temporaryFolder.newFolder())))
        val surfaceConfigs =
            config.apiSurfaces ?: error("No <api-surface/>s specified in config file")
        return apiSurfacesFromConfig(surfaceConfigs.apiSurfaceList, "public")
    }

    private fun scanForHistoricalApiVersionInfo(root: File): List<HistoricalApiVersionInfo> {
        val apiSurfaces = buildApiSurfacesFromConfig(KnownConfigFiles.configPublicAndSystemSurfaces)
        val scanConfig =
            PatternNode.ScanConfig(
                dir = root,
                apiSurfaceByName = apiSurfaces.byName,
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
                      jarFile=TESTROOT/1/public/api.jar,
                      signatureFile=TESTROOT/1/public/api/api.txt,
                    )
                  },
                )
                HistoricalApiVersionInfo(
                  version=2,
                  infoBySurface={
                    SurfaceInfo(
                      jarFile=TESTROOT/2/public/api.jar,
                      signatureFile=TESTROOT/2/public/api/api.txt,
                    )
                    SurfaceInfo(
                      jarFile=TESTROOT/2/system/api.jar,
                      signatureFile=TESTROOT/2/system/api/api.txt,
                      extends=public,
                    )
                  },
                )
            """
                .trimIndent(),
            list.dump()
        )

        val last = list.last()
        assertEquals(ApiVersion.fromLevel(2), last.version, "last version")
        val contributingFiles = buildString {
            for (surfaceInfo in last.infoBySurface.values) {
                append(surfaceInfo.surface.name)
                append("\n")
                for (file in surfaceInfo.contributingSignatureFiles()) {
                    append("  ")
                    append(file)
                    append("\n")
                }
            }
        }

        val expected =
            """
                public
                  SignatureFileFromFile(file=TESTROOT/2/public/api/api.txt, forMainApiSurface=true, apiVariantType=CORE)
                system
                  SignatureFileFromFile(file=TESTROOT/2/public/api/api.txt, forMainApiSurface=true, apiVariantType=CORE)
                  SignatureFileFromFile(file=TESTROOT/2/system/api/api.txt, forMainApiSurface=true, apiVariantType=CORE)
            """
                .trimIndent()
        assertEquals(expected, replaceFileWithSymbol(contributingFiles), "contributing files")
    }
}
