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

import com.android.tools.metalava.model.Codebase;
import com.android.tools.metalava.SdkIdentifier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
public class ApiGenerator {
    public static void main(String[] args) {
        boolean error = false;
        int minApi = 1;
        int currentApi = -1;
        String currentCodename = null;
        File currentJar = null;
        List<String> patterns = new ArrayList<>();
        String outPath = null;

        for (int i = 0; i < args.length && !error; i++) {
            String arg = args[i];

            if (arg.equals("--pattern")) {
                i++;
                if (i < args.length) {
                    patterns.add(args[i]);
                } else {
                    System.err.println("Missing argument after " + arg);
                    error = true;
                }
            } else if (arg.equals("--current-version")) {
                i++;
                if (i < args.length) {
                    currentApi = Integer.parseInt(args[i]);
                    if (currentApi <= 22) {
                        System.err.println("Suspicious currentApi=" + currentApi + ", expected at least 23");
                        error = true;
                    }
                } else {
                    System.err.println("Missing number >= 1 after " + arg);
                    error = true;
                }
            } else if (arg.equals("--current-codename")) {
                i++;
                if (i < args.length) {
                    currentCodename = args[i];
                } else {
                    System.err.println("Missing codename after " + arg);
                    error = true;
                }
            } else if (arg.equals("--current-jar")) {
                i++;
                if (i < args.length) {
                    if (currentJar != null) {
                        System.err.println("--current-jar should only be specified once");
                        error = true;
                    }
                    String path = args[i];
                    currentJar = new File(path);
                } else {
                    System.err.println("Missing argument after " + arg);
                    error = true;
                }
            } else if (arg.equals("--min-api")) {
                i++;
                if (i < args.length) {
                    minApi = Integer.parseInt(args[i]);
                } else {
                    System.err.println("Missing number >= 1 after " + arg);
                    error = true;
                }
            } else if (arg.length() >= 2 && arg.startsWith("--")) {
                System.err.println("Unknown argument: " + arg);
                error = true;
            } else if (outPath == null) {
                outPath = arg;
            } else if (new File(arg).isDirectory()) {
                String pattern = arg;
                if (!pattern.endsWith(File.separator)) {
                    pattern += File.separator;
                }
                pattern += "platforms" + File.separator + "android-%" + File.separator + "android.jar";
                patterns.add(pattern);
            } else {
                System.err.println("Unknown argument: " + arg);
                error = true;
            }
        }

        if (!error && outPath == null) {
            System.err.println("Missing out file path");
            error = true;
        }

        if (!error && patterns.isEmpty()) {
            System.err.println("Missing SdkFolder or --pattern.");
            error = true;
        }

        if (currentJar != null && currentApi == -1 || currentJar == null && currentApi != -1) {
            System.err.println("You must specify both --current-jar and --current-version (or neither one)");
            error = true;
        }

        // The SDK version number
        if (currentCodename != null && !"REL".equals(currentCodename)) {
            currentApi++;
        }

        if (error) {
            printUsage();
            System.exit(1);
        }

        try {
            if (!generate(minApi, currentApi, currentJar, patterns, outPath, null)) {
                System.exit(1);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static boolean generate(int minApi,
                                    int currentApi,
                                    @NotNull File currentJar,
                                    @NotNull List<String> patterns,
                                    @NotNull String outPath,
                                    @Nullable Codebase codebase) throws IOException {
        AndroidJarReader reader = new AndroidJarReader(patterns, minApi, currentJar, currentApi, codebase);
        Api api = reader.getApi();
        return createApiFile(new File(outPath), api, Collections.emptySet());
    }

    public static boolean generate(@NotNull File[] apiLevels,
                                   int firstApiLevel,
                                   @NotNull File outputFile,
                                   @Nullable Codebase codebase,
                                   @Nullable File sdkJarRoot,
                                   @Nullable File sdkFilterFile) throws IOException, IllegalArgumentException {
        if ((sdkJarRoot == null) != (sdkFilterFile == null)) {
            throw new IllegalArgumentException("sdkJarRoot and sdkFilterFile must both be null, or non-null");
        }
        AndroidJarReader reader = new AndroidJarReader(apiLevels, firstApiLevel, codebase);
        Api api = reader.getApi();
        Set<SdkIdentifier> sdkIdentifiers = Collections.emptySet();
        if (sdkJarRoot != null && sdkFilterFile != null) {
            sdkIdentifiers = processExtensionSdkApis(api, sdkJarRoot, sdkFilterFile);
        }
        return createApiFile(outputFile, api, sdkIdentifiers);
    }

    private static void printUsage() {
        System.err.println("\nGenerates a single API file from the content of an SDK.");
        System.err.println("Usage:");
        System.err.println("\tApiCheck [--min-api=1] OutFile [SdkFolder | --pattern sdk/%/public/android.jar]+");
        System.err.println("Options:");
        System.err.println("--min-api <int> : The first API level to consider (>=1).");
        System.err.println("--pattern <pattern>: Path pattern to find per-API android.jar files, where\n" +
            "            '%' is replaced by the API level.");
        System.err.println("--current-jar <path>: Path pattern to find the current android.jar");
        System.err.println("--current-version <int>: The API level for the current API");
        System.err.println("--current-codename <name>: REL, if a release, or codename for previews");
        System.err.println("SdkFolder: if given, this adds the pattern\n" +
            "           '$SdkFolder/platforms/android-%/android.jar'");
        System.err.println("If multiple --pattern are specified, they are tried in the order given.\n");
    }

    /**
     * Modify the extension SDK API parts of an API as dictated by a filter.
     *
     *   - remove APIs not listed in the filter
     *   - assign APIs listed in the filter their corresponding extensions
     *
     * @param api the api to modify
     * @param sdkJarRoot path to directory containing extension SDK jars (usually $ANDROID_ROOT/prebuilts/sdk/extensions)
     * @param filterPath: path to the filter file. @see ApiToExtensionsMap
     * @throws IOException if the filter file can not be read
     * @throws IllegalArgumentException if an error is detected in the filter file, or if no jar files were found
     */
    private static Set<SdkIdentifier> processExtensionSdkApis(@NotNull Api api, @NotNull File sdkJarRoot, @NotNull File filterPath) throws IOException, IllegalArgumentException {
        String rules = new String(Files.readAllBytes(filterPath.toPath()));

        Map<String, List<VersionAndPath>> map = ExtensionSdkJarReader.Companion.findExtensionSdkJarFiles(sdkJarRoot);
        if (map.isEmpty()) {
            throw new IllegalArgumentException("no extension sdk jar files found in " + sdkJarRoot);
        }
        for (Map.Entry<String, List<VersionAndPath>> entry : map.entrySet()) {
            String mainlineModule = entry.getKey();
            ApiToExtensionsMap extensionsMap = ApiToExtensionsMap.Companion.fromString(mainlineModule, rules);
            ExtensionSdkJarReader sdkReader = new ExtensionSdkJarReader(mainlineModule, entry.getValue());
            Api sdkApi = sdkReader.getApi();

            for (ApiClass sdkClass : sdkApi.getClasses()) {
                ApiClass clazz = api.findClass(sdkClass.getName());
                if (clazz == null) {
                    continue;
                }

                Set<String> extensions = extensionsMap.getExtensions(clazz);
                String clazzFromAttr = extensionsMap.calculateFromAttr(clazz.getSince(), extensions, sdkClass.getSince());
                if (!extensions.isEmpty()) {
                    clazz.updateMainlineModule(mainlineModule);
                    clazz.updateFrom(clazzFromAttr);
                }

                Iterator<ApiElement> iter = clazz.getFieldIterator();
                while (iter.hasNext()) {
                    ApiElement field = iter.next();
                    extensions = extensionsMap.getExtensions(clazz, field);
                    if (!extensions.isEmpty()) {
                        ApiElement sdkField = sdkClass.getField(field.getName());
                        if (sdkField != null) {
                            String from = extensionsMap.calculateFromAttr(field.getSince(), extensions, sdkField.getSince());
                            if (!clazzFromAttr.equals(from)) {
                                field.updateFrom(from);
                            }
                        } else {
                            // TODO: this is a new field that was added in the current REL version. What to do?
                            // Introduce something equivalent to ARG_CURRENT_VERSION?
                        }
                    }
                }

                iter = clazz.getMethodIterator();
                while (iter.hasNext()) {
                    ApiElement method = iter.next();
                    extensions = extensionsMap.getExtensions(clazz, method);
                    if (!extensions.isEmpty()) {
                        ApiElement sdkMethod = sdkClass.getMethod(method.getName());
                        if (sdkMethod != null) {
                            String from = extensionsMap.calculateFromAttr(method.getSince(), extensions, sdkMethod.getSince());
                            if (!clazzFromAttr.equals(from)) {
                                method.updateFrom(from);
                            }
                        } else {
                            // TOOD: this is a new method that was added in the current REL version. What to do?
                            // Introduce something equivalent to ARG_CURRENT_VERSION?
                        }
                    }
                }
            }
        }
        return ApiToExtensionsMap.Companion.fromString("", rules).getSdkIdentifiers();
    }

    /**
     * Creates the simplified diff-based API level.
     *
     * @param outFile        the output file
     * @param api            the api to write
     * @param sdkIdentifiers SDKs referenced by the api
     */
    private static boolean createApiFile(@NotNull File outFile, @NotNull Api api, @NotNull Set<SdkIdentifier> sdkIdentifiers) {
        File parentFile = outFile.getParentFile();
        if (!parentFile.exists()) {
            boolean ok = parentFile.mkdirs();
            if (!ok) {
                System.err.println("Could not create directory " + parentFile);
                return false;
            }
        }
        try (PrintStream stream = new PrintStream(outFile, "UTF-8")) {
            stream.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            api.print(stream, sdkIdentifiers);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
