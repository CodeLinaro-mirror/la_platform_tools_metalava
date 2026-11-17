/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class GenericCallbacksTest : DriverTest() {
    @Test
    fun `Listener replaceable with OutcomeReceiver or ListenableFuture`() {
        check(
            apiLint = "", // enabled
            // Ignore other issues.
            extraArguments =
                hiddenIssues(
                    Issues.ARRAY_RETURN,
                ),
            expectedIssues =
                """
                    src/android/pkg/Cases.java:8: error: Cases.BadCallback can be replaced with OutcomeReceiver<R,E> (platform) or suspend fun / ListenableFuture (AndroidX). [GenericCallbacks]
                    src/android/pkg/Cases.java:12: error: Cases.BadListener can be replaced with OutcomeReceiver<R,E> (platform) or suspend fun / ListenableFuture (AndroidX). [GenericCallbacks]
                    src/android/pkg/Cases.java:16: error: Cases.BadGenericListener can be replaced with OutcomeReceiver<R,E> (platform) or suspend fun / ListenableFuture (AndroidX). [GenericCallbacks]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.NonNull;
                            import java.io.IOException;
                            import java.util.List;

                            public final class Cases {
                                public class BadCallback {
                                    public abstract void onSuccess(@NonNull String result);
                                    public void onFailure(@NonNull Throwable error) {}
                                }
                                public interface BadListener {
                                    void onResult(@NonNull Object result);
                                    void onError(@NonNull IOException error);
                                }
                                public interface BadGenericListener<R, E extends Throwable> {
                                    void onResult(@NonNull R result);
                                    void onError(@NonNull E error);
                                }
                                public interface OkListener {
                                    void onResult(@NonNull Object result);
                                    void onError(int error);
                                }
                                public interface Ok2Listener {
                                    void onResult(int result);
                                    void onError(@NonNull Throwable error);
                                }
                                public interface Ok3Listener {
                                    void onSuccess(@NonNull String result);
                                    void onOtherThing(@NonNull String result);
                                    void onFailure(@NonNull Throwable error);
                                }
                                public interface Ok4Listener {
                                    void onResult(@NonNull String result);
                                    void onError(@NonNull Throwable[] error);
                                }
                                public interface Ok5Listener {
                                    void onResult(@NonNull String result);
                                    void onError(@NonNull List<Throwable> error);
                                }
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                )
        )
    }
}
