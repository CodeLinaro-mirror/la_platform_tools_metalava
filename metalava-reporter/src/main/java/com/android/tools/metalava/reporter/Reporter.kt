/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

interface Reporter {

    /**
     * Report an issue with a specific file.
     *
     * Delegates to calling `report(id, null, message, Location.forFile(file)`.
     *
     * @param id the id of the issue.
     * @param file the optional source file for which the issue is reported.
     * @param message the message to report.
     * @param maximumSeverity the maximum [Severity] that will be reported. An issue that is
     *   configured to have a higher [Severity] that this will use the [maximumSeverity] instead.
     * @return true if the issue was reported false it is a known issue in a baseline file.
     */
    fun report(
        id: Issues.Issue,
        file: File?,
        message: String,
        maximumSeverity: Severity = Severity.UNLIMITED,
    ): Boolean {
        val location = FileLocation.forFile(file)
        return report(id, null, message, location, maximumSeverity)
    }

    /**
     * Report an issue.
     *
     * The issue is handled as follows:
     * 1. If the item is suppressed (see [isSuppressed]) then it will only be reported to a file
     *    into which suppressed issues are reported and this will return `false`.
     * 2. If possible the issue will be checked in a relevant baseline file to see if it is a known
     *    issue and if so it will simply be ignored.
     * 3. Otherwise, it will be reported at the appropriate severity to the command output and if
     *    possible it will be recorded in a new baseline file that the developer can copy to silence
     *    the issue in the future.
     *
     * If no [location] or [reportable] is provided then no location is reported in the error
     * message, and the baseline file is neither checked nor updated.
     *
     * If a [location] is provided but no [reportable] then it is used both to report the message
     * and as the baseline key to check and update the baseline file.
     *
     * If an [reportable] is provided but no [location] then it is used both to report the message
     * and as the baseline key to check and update the baseline file.
     *
     * If both an [reportable] and [location] are provided then the [reportable] is used as the
     * baseline key to check and update the baseline file and the [location] is used to report the
     * message. The reason for that is the [location] is assumed to be a more accurate indication of
     * where the problem lies but the [reportable] is assumed to provide a more stable key to use in
     * the baseline as it will not change simply by adding and removing lines in the containing
     * file.
     *
     * @param id the id of the issue.
     * @param reportable the optional object for which the issue is reported.
     * @param message the message to report.
     * @param location the optional location to specify.
     * @param maximumSeverity the maximum [Severity] that will be reported. An issue that is
     *   configured to have a higher [Severity] that this will use the [maximumSeverity] instead.
     * @return true if the issue was reported false it is a known issue in a baseline file.
     */
    fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: FileLocation = FileLocation.UNKNOWN,
        maximumSeverity: Severity = Severity.UNLIMITED,
    ): Boolean

    /**
     * Check to see whether the issue is suppressed.
     * 1. If the [Severity] of the [Issues.Issue] is [Severity.HIDDEN] then this returns `true`.
     * 2. If the [reportable] is `null` then this returns `false`.
     * 3. If the item has a suppression annotation that lists the name of the issue then this
     *    returns `true`.
     * 4. Otherwise, this returns `false`.
     */
    fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable? = null,
        message: String? = null
    ): Boolean
}

/**
 * Abstract implementation of a [Reporter] that performs no filtering and delegates the handling of
 * a report to [handleFormattedMessage].
 */
abstract class AbstractBasicReporter : Reporter {
    override fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: FileLocation,
        maximumSeverity: Severity,
    ): Boolean {
        val formattedMessage = buildString {
            val usableLocation = reportable?.fileLocation ?: location
            append(usableLocation.path)
            if (usableLocation.line > 0) {
                append(":")
                append(usableLocation.line)
            }
            append(": ")
            val severity = id.defaultLevel
            append(severity)
            append(": ")
            append(message)
            append(severity.messageSuffix)
            append(" [")
            append(id.name)
            append("]")
        }
        return handleFormattedMessage(formattedMessage)
    }

    abstract fun handleFormattedMessage(formattedMessage: String): Boolean

    override fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String?
    ): Boolean = false
}

/**
 * Basic implementation of a [Reporter] that performs no filtering and simply outputs the message to
 * the supplied [PrintWriter].
 */
class BasicReporter(private val stderr: PrintWriter) : AbstractBasicReporter() {
    constructor(writer: Writer) : this(stderr = PrintWriter(writer))

    constructor(outputStream: OutputStream) : this(stderr = PrintWriter(outputStream))

    override fun handleFormattedMessage(formattedMessage: String): Boolean {
        stderr.println(formattedMessage)
        stderr.flush()
        return true
    }

    override fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String?
    ): Boolean = false
}

/** A [Reporter] which will record issues in an internal buffer, accessible through [issues]. */
class RecordingReporter : AbstractBasicReporter() {
    private val stringWriter = StringWriter()

    override fun handleFormattedMessage(formattedMessage: String): Boolean {
        stringWriter.append(formattedMessage).append("\n")
        return true
    }

    val issues
        get() = stringWriter.toString().trim()
}

/**
 * A [Reporter] which will throw an exception for the first issue, even warnings or hidden, that is
 * reported.
 *
 * Safe to use when no issues are expected as it will prevent any issues from being silently
 * ignored.
 */
class ThrowingReporter private constructor() : AbstractBasicReporter() {
    override fun handleFormattedMessage(formattedMessage: String): Boolean {
        error(formattedMessage)
    }

    companion object {
        val INSTANCE = ThrowingReporter()
    }
}
