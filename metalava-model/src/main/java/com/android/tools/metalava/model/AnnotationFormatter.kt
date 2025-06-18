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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.value.LegacyValueFormatter
import java.lang.StringBuilder

/** Formats [AnnotationItem]s. */
sealed interface AnnotationFormatter {
    /** Format [annotationItem] for [target] as part of [context]. */
    fun formatAnnotation(
        annotationItem: AnnotationItem,
        target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE,
        context: Item? = null,
    ) = buildString { appendFormatAnnotation(this, annotationItem, target, context) }

    /** Format [annotationItem] for [target] as part of [context] and append to [builder]. */
    fun appendFormatAnnotation(
        builder: StringBuilder,
        annotationItem: AnnotationItem,
        target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE,
        context: Item? = null,
    )

    companion object {
        /** [AnnotationFormatter] wrapper for [LegacyValueFormatter.ANNOTATION_SOURCE_FORMATTER]. */
        private val legacyFormatter =
            LegacyAnnotationFormatter(LegacyValueFormatter.ANNOTATION_SOURCE_FORMATTER)

        /** An [AnnotationFormatter] that supports the legacy behavior. */
        fun legacyAnnotationFormatter(): AnnotationFormatter = legacyFormatter
    }

    /** An [AnnotationFormatter] that wraps a [LegacyValueFormatter]. */
    private class LegacyAnnotationFormatter(
        private val legacyValueFormatter: LegacyValueFormatter
    ) : AnnotationFormatter {
        override fun appendFormatAnnotation(
            builder: StringBuilder,
            annotationItem: AnnotationItem,
            target: AnnotationTarget,
            context: Item?
        ) {
            legacyValueFormatter.appendFormatAnnotation(builder, annotationItem, target, context)
        }
    }
}
