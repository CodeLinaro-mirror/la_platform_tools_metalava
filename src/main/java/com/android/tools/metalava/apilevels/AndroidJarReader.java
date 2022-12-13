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

import com.android.SdkConstants;
import com.android.tools.metalava.model.Codebase;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads all the android.jar files found in an SDK and generate a map of {@link ApiClass}.
 */
class AndroidJarReader {
    private final int mMinApi;
    private final File[] mApiLevels;
    private final Codebase mCodebase;

    AndroidJarReader(@NotNull File[] apiLevels, int firstApiLevel, @Nullable Codebase codebase) {
        mApiLevels = apiLevels;
        mCodebase = codebase;
        mMinApi = firstApiLevel;
    }

    public Api getApi() throws IOException {
        int max = mApiLevels.length - 1;
        if (mCodebase != null) {
            max = mCodebase.getApiLevel();
        }

        Api api = new Api(mMinApi, max);
        for (int apiLevel = mMinApi; apiLevel < mApiLevels.length; apiLevel++) {
            File jar = getAndroidJarFile(apiLevel);
            JarReaderUtilsKt.readAndroidJar(api, apiLevel, jar);
        }
        if (mCodebase != null) {
            int apiLevel = mCodebase.getApiLevel();
            if (apiLevel != -1) {
                processCodebase(api, apiLevel);
            }
        }
        return api;
    }

    private void processCodebase(Api api, int apiLevel) {
        if (mCodebase == null) {
            return;
        }
        AddApisFromCodebaseKt.addApisFromCodebase(api, apiLevel, mCodebase);
    }

    private File getAndroidJarFile(int apiLevel) {
        return mApiLevels[apiLevel];
    }
}
