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

import com.android.tools.metalava.SdkIdentifier;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the whole Android API.
 */
public class Api extends ApiElement {
    private final Map<String, ApiClass> mClasses = new HashMap<>();
    private final int mMin;
    private final int mMax;

    public Api(int min, int max) {
        super("Android API");
        mMin = min;
        mMax = max;
    }

    /**
     * Prints the whole API definition to a stream.
     *
     * @param stream the stream to print the XML elements to
     */
    public void print(PrintStream stream, Set<SdkIdentifier> sdkIdentifiers) {
        stream.print("<api version=\"3\"");
        if (mMin > 1) {
            stream.print(" min=\"" + mMin + "\"");
        }
        stream.println(">");
        for (SdkIdentifier sdk : sdkIdentifiers) {
            stream.println("\t<sdk id=\"" + sdk.getId() + "\" name=\"" + sdk.getName() + "\"/>");
        }
        print(mClasses.values(), "class", "\t", stream);
        printClosingTag("api", "", stream);
    }

    /**
     * Adds or updates a class.
     *
     * @param name       the name of the class
     * @param version    an API version in which the class existed
     * @param deprecated whether the class was deprecated in the API version
     * @return the newly created or a previously existed class
     */
    public ApiClass addClass(String name, int version, boolean deprecated) {
        ApiClass classElement = mClasses.get(name);
        if (classElement == null) {
            classElement = new ApiClass(name, version, deprecated);
            mClasses.put(name, classElement);
        } else {
            classElement.update(version, deprecated);
        }
        return classElement;
    }

    public ApiClass findClass(String name) {
        if (name == null) {
            return null;
        }
        return mClasses.get(name);
    }

    public Collection<ApiClass> getClasses() {
        return Collections.unmodifiableCollection(mClasses.values());
    }

    public void backfillHistoricalFixes() {
        backfillSdkExtensions();
    }

    private void backfillSdkExtensions() {
        // SdkExtensions.getExtensionVersion was added in 30/R, but was a SystemApi
        // to avoid publishing the versioning API publicly before there was any
        // valid use for it.
        // getAllExtensionsVersions was added as part of 31/S
        // The class and its APIs were made public between S and T, but we pretend
        // here like it was always public, for maximum backward compatibility.
        ApiClass sdkExtensions = findClass("android/os/ext/SdkExtensions");

        if (sdkExtensions != null && sdkExtensions.getSince() != 30
                && sdkExtensions.getSince() != 33) {
            throw new AssertionError("Received unexpected historical data");
        } else if (sdkExtensions == null || sdkExtensions.getSince() == 30) {
            // This is the system API db (30), or module-lib/system-server dbs (null)
            // They don't need patching.
            return;
        }
        sdkExtensions.update(30, false);
        sdkExtensions.addSuperClass("java/lang/Object", 30);
        sdkExtensions.getMethod("getExtensionVersion(I)I").update(30, false);
        sdkExtensions.getMethod("getAllExtensionVersions()Ljava/util/Map;").update(31, false);
    }

    /**
     * The bytecode visitor registers interfaces listed for a class. However,
     * a class will <b>also</b> implement interfaces implemented by the super classes.
     * This isn't available in the class file, so after all classes have been read in,
     * we iterate through all classes, and for those that have interfaces, we check up
     * the inheritance chain to see if it has already been introduced in a super class
     * at an earlier API level.
     */
    public void removeImplicitInterfaces() {
        for (ApiClass classElement : mClasses.values()) {
            classElement.removeImplicitInterfaces(mClasses);
        }
    }

    /**
     * @see ApiClass#removeOverridingMethods
     */
    public void removeOverridingMethods() {
        for (ApiClass classElement : mClasses.values()) {
            classElement.removeOverridingMethods(mClasses);
        }
    }

    public void inlineFromHiddenSuperClasses() {
        Map<String, ApiClass> hidden = new HashMap<>();
        for (ApiClass classElement : mClasses.values()) {
            if (classElement.getHiddenUntil() < 0) { // hidden in the .jar files? (mMax==codebase, -1: jar files)
                hidden.put(classElement.getName(), classElement);
            }
        }
        for (ApiClass classElement : mClasses.values()) {
            classElement.inlineFromHiddenSuperClasses(hidden);
        }
    }

    public void prunePackagePrivateClasses() {
        for (ApiClass cls : mClasses.values()) {
            cls.removeHiddenSuperClasses(mClasses);
        }
    }
}
