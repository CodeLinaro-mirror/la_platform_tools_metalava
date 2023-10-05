/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.apilevels;

import com.android.tools.metalava.SignatureFileLoader;
import com.android.tools.metalava.model.Codebase;
import com.android.tools.metalava.SdkIdentifier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
public class ApiGenerator {
    public static boolean generateXml(@NotNull File[] apiLevels,
                                      int firstApiLevel,
                                      int currentApiLevel,
                                      boolean isDeveloperPreviewBuild,
                                      @NotNull File outputFile,
                                      @NotNull Codebase codebase,
                                      @Nullable File sdkJarRoot,
                                      @Nullable File sdkFilterFile,
                                      boolean removeMissingClasses) throws IOException, IllegalArgumentException {
        if ((sdkJarRoot == null) != (sdkFilterFile == null)) {
            throw new IllegalArgumentException("sdkJarRoot and sdkFilterFile must both be null, or non-null");
        }

        int notFinalizedApiLevel = currentApiLevel + 1;
        Api api = createApiFromAndroidJars(apiLevels, firstApiLevel);
        if (isDeveloperPreviewBuild || apiLevels.length - 1 < currentApiLevel) {
            // Only include codebase if we don't have a prebuilt, finalized jar for it.
            int apiLevel = isDeveloperPreviewBuild ? notFinalizedApiLevel : currentApiLevel;
            AddApisFromCodebaseKt.addApisFromCodebase(api, apiLevel, codebase, true);
        }
        api.backfillHistoricalFixes();

        Set<SdkIdentifier> sdkIdentifiers = Collections.emptySet();
        if (sdkJarRoot != null && sdkFilterFile != null) {
            sdkIdentifiers = processExtensionSdkApis(api, notFinalizedApiLevel, sdkJarRoot, sdkFilterFile);
        }
        api.inlineFromHiddenSuperClasses();
        api.removeImplicitInterfaces();
        api.removeOverridingMethods();
        api.prunePackagePrivateClasses();
        if (removeMissingClasses) {
            api.removeMissingClasses();
        } else {
            api.verifyNoMissingClasses();
        }
        return createApiLevelsXml(outputFile, api, sdkIdentifiers);
    }

    /**
     * Creates an [Api] from a list of past API signature files. In the generated [Api], the oldest API
     * version will be represented as level 1, the next as level 2, etc.
     *
     * @param previousApiFiles A list of API signature files, one for each version of the API, in order
     *                         from oldest to newest API version.
     */
    private static Api createApiFromSignatureFiles(
        List<File> previousApiFiles
    ) {
        // Starts at level 1 because 0 is not a valid API level.
        int apiLevel = 1;
        Api api = new Api(apiLevel);
        for (File apiFile : previousApiFiles) {
            Codebase codebase = SignatureFileLoader.INSTANCE.load(apiFile);
            AddApisFromCodebaseKt.addApisFromCodebase(api, apiLevel, codebase, false);
            apiLevel += 1;
        }
        api.clean();
        return api;
    }

    /**
     * Generates an API version history file based on the API surfaces of the versions provided.
     *
     * @param pastApiVersions   A list of API signature files, ordered from oldest API version to newest.
     * @param currentApiVersion A codebase representing the current API surface.
     * @param outputFile        Path of the JSON file to write output to.
     * @param apiVersionNames   The names of the API versions, ordered starting from version 1. This should include the
     *                          names of all the [pastApiVersions], then the name of the [currentVersion].
     */
    public static void generateJson(@NotNull List<File> pastApiVersions,
                                    @NotNull Codebase currentApiVersion,
                                    @NotNull File outputFile,
                                    @NotNull List<String> apiVersionNames) {
        Api api = createApiFromSignatureFiles(pastApiVersions);
        AddApisFromCodebaseKt.addApisFromCodebase(api, apiVersionNames.size(), currentApiVersion, false);
        ApiJsonPrinter printer = new ApiJsonPrinter(apiVersionNames);
        printer.print(api, outputFile);
    }

    private static Api createApiFromAndroidJars(File[] apiLevels, int firstApiLevel) {
        Api api = new Api(firstApiLevel);
        for (int apiLevel = firstApiLevel; apiLevel < apiLevels.length; apiLevel++) {
            File jar = apiLevels[apiLevel];
            JarReaderUtilsKt.readAndroidJar(api, apiLevel, jar);
        }
        return api;
    }

    /**
     * Modify the extension SDK API parts of an API as dictated by a filter.
     *
     *   - remove APIs not listed in the filter
     *   - assign APIs listed in the filter their corresponding extensions
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API level. The recommended value is current-api-level + 1,
     * which is what non-finalized APIs use.
     *
     * @param api the api to modify
     * @param apiLevelNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param sdkJarRoot path to directory containing extension SDK jars (usually $ANDROID_ROOT/prebuilts/sdk/extensions)
     * @param filterPath: path to the filter file. @see ApiToExtensionsMap
     * @throws IOException if the filter file can not be read
     * @throws IllegalArgumentException if an error is detected in the filter file, or if no jar files were found
     */
    private static Set<SdkIdentifier> processExtensionSdkApis(
            @NotNull Api api,
            int apiLevelNotInAndroidSdk,
            @NotNull File sdkJarRoot,
            @NotNull File filterPath) throws IOException, IllegalArgumentException {
        String rules = new String(Files.readAllBytes(filterPath.toPath()));

        Map<String, List<VersionAndPath>> map = ExtensionSdkJarReader.Companion.findExtensionSdkJarFiles(sdkJarRoot);
        if (map.isEmpty()) {
            throw new IllegalArgumentException("no extension sdk jar files found in " + sdkJarRoot);
        }
        Map<String, ApiToExtensionsMap> moduleMaps = new HashMap<>();
        for (Map.Entry<String, List<VersionAndPath>> entry : map.entrySet()) {
            String mainlineModule = entry.getKey();
            ApiToExtensionsMap moduleMap = ApiToExtensionsMap.Companion.fromXml(mainlineModule, rules);
            if (moduleMap.isEmpty()) continue; // TODO(b/259115852): remove this (though it is an optimization too).

            moduleMaps.put(mainlineModule, moduleMap);
            for (VersionAndPath f : entry.getValue()) {
                JarReaderUtilsKt.readExtensionJar(api, f.version, mainlineModule, f.path, apiLevelNotInAndroidSdk);
            }
        }
        for (ApiClass clazz : api.getClasses()) {
            String module = clazz.getMainlineModule();
            if (module == null) continue;
            ApiToExtensionsMap extensionsMap = moduleMaps.get(module);
            String sdks = extensionsMap.calculateSdksAttr(clazz.getSince(), apiLevelNotInAndroidSdk,
                extensionsMap.getExtensions(clazz), clazz.getSinceExtension());
            clazz.updateSdks(sdks);

            Iterator<ApiElement> iter = clazz.getFieldIterator();
            while (iter.hasNext()) {
                ApiElement field = iter.next();
                sdks = extensionsMap.calculateSdksAttr(field.getSince(), apiLevelNotInAndroidSdk,
                    extensionsMap.getExtensions(clazz, field), field.getSinceExtension());
                field.updateSdks(sdks);
            }

            iter = clazz.getMethodIterator();
            while (iter.hasNext()) {
                ApiElement method = iter.next();
                sdks = extensionsMap.calculateSdksAttr(method.getSince(), apiLevelNotInAndroidSdk,
                    extensionsMap.getExtensions(clazz, method), method.getSinceExtension());
                method.updateSdks(sdks);
            }
        }
        return ApiToExtensionsMap.Companion.fromXml("", rules).getSdkIdentifiers();
    }

    /**
     * Creates the simplified diff-based API level.
     *
     * @param outFile        the output file
     * @param api            the api to write
     * @param sdkIdentifiers SDKs referenced by the api
     */
    private static boolean createApiLevelsXml(@NotNull File outFile, @NotNull Api api, @NotNull Set<SdkIdentifier> sdkIdentifiers) {
        File parentFile = outFile.getParentFile();
        if (!parentFile.exists()) {
            boolean ok = parentFile.mkdirs();
            if (!ok) {
                System.err.println("Could not create directory " + parentFile);
                return false;
            }
        }
        try (PrintStream stream = new PrintStream(outFile, StandardCharsets.UTF_8)) {
            stream.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            api.print(stream, sdkIdentifiers);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
