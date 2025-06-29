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

import com.android.tools.metalava.model.value.AnnotationAttributeNameValueSeparator
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.LegacyValueFormatter
import com.android.tools.metalava.model.value.SingleArrayElementFormat
import com.android.tools.metalava.model.value.ValueStringConfiguration
import java.lang.StringBuilder

/** Formats [AnnotationItem]s. */
sealed interface AnnotationFormatter {
    /** Format [annotationItem] as part of [context]. */
    fun formatAnnotation(
        annotationItem: AnnotationItem,
        context: Item? = null,
    ) = buildString { appendFormatAnnotation(this, annotationItem, context) }

    /** Format [annotationItem] as part of [context] and append to [builder]. */
    fun appendFormatAnnotation(
        builder: StringBuilder,
        annotationItem: AnnotationItem,
        context: Item? = null,
    )

    companion object {
        /** An [AnnotationFormatter] that supports the legacy behavior. */
        fun legacyAnnotationFormatter(
            target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE
        ): AnnotationFormatter =
            LegacyAnnotationFormatter(LegacyValueFormatter.ANNOTATION_SOURCE_FORMATTER, target)

        /** An [AnnotationFormatter] for use when writing stubs for [target]. */
        fun stubFormatter(target: AnnotationTarget): AnnotationFormatter = StubFormatter(target)

        /** True if this [FieldItem] is not-null, is not hidden or removed and is public. */
        private fun FieldItem?.isAccessible() = this != null && !isHiddenOrRemoved() && isPublic

        /** Inline [value] if it references an inaccessible field. */
        private fun inlineInaccessibleFieldReference(value: FieldReferenceValue) =
            !value.resolve().isAccessible()
    }

    /** An [AnnotationFormatter] that wraps a [LegacyValueFormatter]. */
    private class LegacyAnnotationFormatter(
        private val legacyValueFormatter: LegacyValueFormatter,
        private val target: AnnotationTarget,
    ) : AnnotationFormatter {
        override fun appendFormatAnnotation(
            builder: StringBuilder,
            annotationItem: AnnotationItem,
            context: Item?
        ) {
            legacyValueFormatter.appendFormatAnnotation(builder, annotationItem, target, context)
        }
    }

    /** [AnnotationFormatter] for use in stub files. */
    private class StubFormatter(val target: AnnotationTarget) : AnnotationFormatter {
        /** The default [ValueStringConfiguration] for stub files for [target]. */
        private val defaultConfiguration =
            ValueStringConfiguration(
                annotationAttributeNameValueSeparator =
                    AnnotationAttributeNameValueSeparator.WITHOUT_SPACES,
                annotationQualifiedNameGetter = { annotationItem ->
                    annotationItem.annotationContext.annotationManager.normalizeOutputName(
                        annotationItem.qualifiedName,
                        target
                    )
                },
                inlineFieldReferenceChecker = ::inlineInaccessibleFieldReference,
                singleArrayElementFormat = SingleArrayElementFormat.UNWRAP,
            )

        /**
         * The [ValueStringConfiguration] for stub files for [target] when the annotation's values
         * should always be inlined, e.g. [ANDROID_FLAGGED_API].
         */
        private val alwaysInlineConfiguration =
            defaultConfiguration.copy(
                inlineFieldReferenceChecker = { true },
            )

        override fun appendFormatAnnotation(
            builder: StringBuilder,
            annotationItem: AnnotationItem,
            context: Item?
        ) {
            val alwaysInline = annotationItem.qualifiedName == ANDROID_FLAGGED_API
            val configuration =
                if (alwaysInline) alwaysInlineConfiguration else defaultConfiguration
            annotationItem.appendAnnotationStringTo(
                builder,
                configuration,
                annotationIsValue = false,
            )
        }
    }
}
