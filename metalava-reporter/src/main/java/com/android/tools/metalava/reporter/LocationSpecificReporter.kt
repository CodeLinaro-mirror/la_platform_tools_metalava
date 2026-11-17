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

package com.android.tools.metalava.reporter

/**
 * Reports issues for a specific location in the source determined by the provider of this.
 *
 * This should be used for lower level code that needs to report issues with information obtained
 * from the source but does not have access to the location in the sources where the information was
 * obtained. In that case the code that does know the source location of the information can provide
 * an instance of this which will attach the location to the issue before forwarding it on to a
 * [Reporter].
 */
interface LocationSpecificReporter {
    /**
     * Report [issue] with [message] for the context with which this reporter is associated.
     *
     * @param issue the [Issues.Issue] to report.
     * @param [message] the message to report.
     */
    fun report(issue: Issues.Issue, message: String)
}
