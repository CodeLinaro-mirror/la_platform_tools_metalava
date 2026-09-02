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

package com.android.tools.metalava.model.annotation

import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROIDX_FLOAT_RANGE
import com.android.tools.metalava.model.ANDROIDX_INT_RANGE
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANDROID_NONNULL
import com.android.tools.metalava.model.ANDROID_NULLABLE
import com.android.tools.metalava.model.ANDROID_REQUIRES_FLAG
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.ANDROID_TEST_API
import com.android.tools.metalava.model.ANNOTATION_EXTERNAL
import com.android.tools.metalava.model.ANNOTATION_EXTERNAL_ONLY
import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.ANNOTATION_SDK_STUBS_ONLY
import com.android.tools.metalava.model.ANNOTATION_SIGNATURE_ONLY
import com.android.tools.metalava.model.ANNOTATION_STUBS_ONLY
import com.android.tools.metalava.model.AnnotationInfo
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.BaseAnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.JVM_FIELD
import com.android.tools.metalava.model.JVM_NAME
import com.android.tools.metalava.model.JVM_STATIC
import com.android.tools.metalava.model.KOTLIN_DEPRECATED
import com.android.tools.metalava.model.KOTLIN_METADATA
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.android.tools.metalava.model.RECENTLY_NONNULL
import com.android.tools.metalava.model.RECENTLY_NULLABLE
import com.android.tools.metalava.model.SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.ShowOrHide
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.Showability.Companion.REVERT_UNSTABLE_API
import com.android.tools.metalava.model.TypedefMode
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager.Config
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.api.SelectedApiUpdater
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.flags.optionalFlagName
import com.android.tools.metalava.model.computeTypeNullability
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isNonNullAnnotation
import com.android.tools.metalava.model.isNullableAnnotation
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.ThrowingReporter
import kotlin.getValue

/** The type of lambda that can construct a key from an [AnnotationItem] */
typealias KeyFactory = (annotationItem: AnnotationItem) -> String

class DefaultAnnotationManager(private val config: Config = Config()) : BaseAnnotationManager() {

    data class Config(
        val reporter: Reporter = ThrowingReporter.INSTANCE,
        val passThroughAnnotations: Set<String> = emptySet(),
        val apiSurfaceSelector: ApiSurfaceSelector = ApiSurfaceSelector(),
        val suppressCompatibilityMetaAnnotations: Set<String> = emptySet(),
        val excludeAnnotations: Set<String> = emptySet(),
        val typedefMode: TypedefMode = TypedefMode.NONE,
        val apiPredicate: FilterPredicate = FilterPredicate { true },
        /**
         * Provider of an optional [Codebase] object that will be used when reverting flagged APIs.
         */
        val previouslyReleasedCodebaseProvider: () -> Codebase? = { null },

        /**
         * The set of available [ApiFlag]s.
         *
         * If this is `null` then no [ApiFlag]s have been provided, otherwise it contains an
         * [ApiFlag] for every provided flag and will use a default for any others.
         */
        val apiFlags: ApiFlags? = null,

        /** Map from annotation class qualified name to its targets. */
        val annotationClassTargets: Map<String, Set<AnnotationTarget>> = emptyMap(),
    )

    override val apiSurfaceSelector = config.apiSurfaceSelector

    /** The set of all annotation names that should be preserved during normalization. */
    private val annotationNamesToPreserveDuringNormalization = buildSet {
        // Add all the annotations specifically configured to be passed through.
        addAll(config.passThroughAnnotations)

        // Add all the annotations used for API surface selection.
        addAll(apiSurfaceSelector.annotationNames)
    }

    /**
     * Map from annotation name to the [KeyFactory] to use to create a key.
     *
     * See [getKeyForAnnotationItem] to see how this is used.
     */
    private val annotationNameToKeyFactory: Map<String, KeyFactory>

    init {
        /** Use the complete source representation of the item as the key. */
        fun useSourceAsKey(annotationItem: AnnotationItem): String {
            val qualifiedName = annotationItem.qualifiedName
            val attributes = annotationItem.attributes
            if (attributes.isEmpty()) {
                return qualifiedName
            }
            return buildString {
                append(qualifiedName)
                append("(")
                attributes.forEachIndexed { index, attribute ->
                    if (index > 0) {
                        append(",")
                    }
                    append(attribute)
                }
                append(")")
            }
        }

        // Build a list of the names of annotations whose AnnotationInfo could be dependent on an
        // annotation attributes and not just its name.
        val annotationNames = buildList {
            // Add all the annotation names matched by all the API surface selection filters as they
            // can match on attribute values as well as the annotation name.
            addAll(apiSurfaceSelector.annotationNames)

            // ApiFlags have been provided so the flag name specified on an
            // `android.annotation.FlaggedApi` will affect the state of the associated
            // AnnotationInfo so make sure to use the flag name in the cache key for `FlaggedApi`
            // annotations.
            if (config.apiFlags != null) add(ANDROID_FLAGGED_API)
        }

        // Use KeyFactory that uses the complete source representation as the key and not just the
        // annotation name which is the default.
        annotationNameToKeyFactory = annotationNames.associateWith { ::useSourceAsKey }
    }

    override fun getKeyForAnnotationItem(annotationItem: AnnotationItem): String {
        val qualifiedName = annotationItem.qualifiedName

        // Check to see if this requires a special [KeyFactory] and use it if it does.
        val keyFactory = annotationNameToKeyFactory[qualifiedName]
        if (keyFactory != null) {
            return keyFactory(annotationItem)
        }

        // No special key factory is needed so just use the qualified name as the key.
        return qualifiedName
    }

    override fun computeAnnotationInfo(annotationItem: AnnotationItem): AnnotationInfo {
        return LazyAnnotationInfo(this, config, annotationItem)
    }

    override fun normalizeInputName(qualifiedName: String): String? {
        if (preserveAnnotationDuringNormalization(qualifiedName)) {
            return qualifiedName
        }

        if (config.excludeAnnotations.contains(qualifiedName)) {
            return null
        }

        when (qualifiedName) {
            // Resource annotations
            "android.annotation.AnimRes" -> return "androidx.annotation.AnimRes"
            "android.annotation.AnimatorRes" -> return "androidx.annotation.AnimatorRes"
            "android.annotation.AnyRes" -> return "androidx.annotation.AnyRes"
            "android.annotation.ArrayRes" -> return "androidx.annotation.ArrayRes"
            "android.annotation.AttrRes" -> return "androidx.annotation.AttrRes"
            "android.annotation.BoolRes" -> return "androidx.annotation.BoolRes"
            "android.annotation.ColorRes" -> return "androidx.annotation.ColorRes"
            "android.annotation.DimenRes" -> return "androidx.annotation.DimenRes"
            "android.annotation.DrawableRes" -> return "androidx.annotation.DrawableRes"
            "android.annotation.FontRes" -> return "androidx.annotation.FontRes"
            "android.annotation.FractionRes" -> return "androidx.annotation.FractionRes"
            "android.annotation.IdRes" -> return "androidx.annotation.IdRes"
            "android.annotation.IntegerRes" -> return "androidx.annotation.IntegerRes"
            "android.annotation.InterpolatorRes" -> return "androidx.annotation.InterpolatorRes"
            "android.annotation.LayoutRes" -> return "androidx.annotation.LayoutRes"
            "android.annotation.MenuRes" -> return "androidx.annotation.MenuRes"
            "android.annotation.PluralsRes" -> return "androidx.annotation.PluralsRes"
            "android.annotation.RawRes" -> return "androidx.annotation.RawRes"
            "android.annotation.StringRes" -> return "androidx.annotation.StringRes"
            "android.annotation.StyleRes" -> return "androidx.annotation.StyleRes"
            "android.annotation.StyleableRes" -> return "androidx.annotation.StyleableRes"
            "android.annotation.TransitionRes" -> return "androidx.annotation.TransitionRes"
            "android.annotation.XmlRes" -> return "androidx.annotation.XmlRes"

            // Threading
            "android.annotation.AnyThread" -> return "androidx.annotation.AnyThread"
            "android.annotation.BinderThread" -> return "androidx.annotation.BinderThread"
            "android.annotation.MainThread" -> return "androidx.annotation.MainThread"
            "android.annotation.UiThread" -> return "androidx.annotation.UiThread"
            "android.annotation.WorkerThread" -> return "androidx.annotation.WorkerThread"

            // Colors
            "android.annotation.ColorInt" -> return "androidx.annotation.ColorInt"
            "android.annotation.ColorLong" -> return "androidx.annotation.ColorLong"
            "android.annotation.HalfFloat" -> return "androidx.annotation.HalfFloat"

            // Ranges and sizes
            "android.annotation.FloatRange" -> return ANDROIDX_FLOAT_RANGE
            "android.annotation.IntRange" -> return ANDROIDX_INT_RANGE
            "android.annotation.Size" -> return "androidx.annotation.Size"
            "android.annotation.Px" -> return "androidx.annotation.Px"
            "android.annotation.Dimension" -> return "androidx.annotation.Dimension"

            // Environments
            "android.annotation.RestrictedForEnvironment" ->
                return "androidx.annotation.RestrictedForEnvironment"

            // Null
            // Preserve recently/newly nullable annotation as they need to be passed through to
            // stubs. They will be treated as nullable/non-null just as if they were mapped to
            // ANDROIDX_NULLABLE or ANDROIDX_NONNULL.
            RECENTLY_NULLABLE -> return qualifiedName
            RECENTLY_NONNULL -> return qualifiedName

            // Normalize the known nullable annotations to ANDROIDX_NULLABLE
            ANDROIDX_NULLABLE,
            ANDROID_NULLABLE,
            "libcore.util.Nullable",
            "org.jetbrains.annotations.Nullable" -> return ANDROIDX_NULLABLE

            // Normalize the known non-null annotations to ANDROIDX_NONNULL
            ANDROIDX_NONNULL,
            ANDROID_NONNULL,
            "libcore.util.NonNull",
            "org.jetbrains.annotations.NotNull" -> return ANDROIDX_NONNULL

            // Typedefs
            "android.annotation.IntDef" -> return "androidx.annotation.IntDef"
            "android.annotation.StringDef" -> return "androidx.annotation.StringDef"
            "android.annotation.LongDef" -> return "androidx.annotation.LongDef"

            // Context Types
            "android.annotation.UiContext" -> return "androidx.annotation.UiContext"
            "android.annotation.DisplayContext" -> return "androidx.annotation.DisplayContext"
            "android.annotation.NonUiContext" -> return "androidx.annotation.NonUiContext"

            // Misc
            "android.annotation.CallSuper" -> return "androidx.annotation.CallSuper"
            "android.annotation.CheckResult" -> return "androidx.annotation.CheckResult"
            "android.annotation.Discouraged" -> return "androidx.annotation.Discouraged"
            "android.annotation.RequiresPermission" ->
                return "androidx.annotation.RequiresPermission"
            "android.annotation.RequiresPermission.Read" ->
                return "androidx.annotation.RequiresPermission.Read"
            "android.annotation.RequiresPermission.Write" ->
                return "androidx.annotation.RequiresPermission.Write"

            // These aren't support annotations, but could/should be:
            "android.annotation.CurrentTimeMillisLong",
            "android.annotation.DurationMicrosLong",
            "android.annotation.DurationMillisLong",
            "android.annotation.ElapsedRealtimeLong",
            "android.annotation.UserIdInt",
            "android.annotation.BytesLong",

            // These aren't support annotations
            "android.annotation.AppIdInt",
            "android.annotation.SuppressAutoDoc",
            ANDROID_SYSTEM_API,
            ANDROID_TEST_API,
            "android.annotation.CallbackExecutor",
            "android.annotation.Condemned",
            "android.annotation.Hide",
            "android.annotation.Widget" -> return qualifiedName

            // Included for analysis, but should not be exported:
            "android.annotation.BroadcastBehavior",
            "android.annotation.SdkConstant",
            "android.annotation.RequiresFeature",
            "android.annotation.SystemService" -> return qualifiedName

            // Should not be mapped to a different package name:
            "android.annotation.TargetApi",
            "android.annotation.SuppressLint" -> return qualifiedName
            ANDROID_FLAGGED_API -> return qualifiedName

            // This implementation only annotation shouldn't be used by metalava at all.
            "dalvik.annotation.codegen.CovariantReturnType" -> return null
            else -> {
                // Some new annotations added to the platform: assume they are support
                // annotations?
                return when {
                    // Other third party nullness annotations?
                    isNullableAnnotation(qualifiedName) -> ANDROIDX_NULLABLE
                    isNonNullAnnotation(qualifiedName) -> ANDROIDX_NONNULL

                    // AndroidX annotations are all included, as is the built-in stuff like
                    // @Retention
                    qualifiedName.startsWith(ANDROIDX_ANNOTATION_PREFIX) -> qualifiedName
                    qualifiedName.startsWith(JAVA_LANG_PREFIX) -> qualifiedName

                    // Unknown Android platform annotations
                    qualifiedName.startsWith(ANDROID_ANNOTATION_PREFIX) -> {
                        qualifiedName
                    }

                    // Ravenwood annotations are meaningless to Metalava.
                    qualifiedName.startsWith("android.ravenwood.") -> null

                    // Keep any other unknown annotations.
                    else -> qualifiedName
                }
            }
        }
    }

    override fun normalizeOutputName(qualifiedName: String, target: AnnotationTarget): String {
        if (preserveAnnotationDuringNormalization(qualifiedName)) {
            return qualifiedName
        }

        when (qualifiedName) {
            ANDROIDX_NULLABLE ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) ANDROID_NULLABLE
                else qualifiedName
            ANDROIDX_NONNULL ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) ANDROID_NONNULL
                else qualifiedName
            RECENTLY_NULLABLE ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) qualifiedName
                else ANDROIDX_NULLABLE
            RECENTLY_NONNULL ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) qualifiedName
                else ANDROIDX_NONNULL
        }

        return qualifiedName
    }

    /**
     * Returns `true` if [qualifiedName] should be preserved unchanged by [normalizeInputName] and
     * [normalizeOutputName].
     */
    private fun preserveAnnotationDuringNormalization(qualifiedName: String) =
        annotationNamesToPreserveDuringNormalization.contains(qualifiedName)

    /**
     * Targets for type def annotations, i.e. `@IntDef` and `@StringDef` annotated annotations.
     *
     * Depends on the [DefaultAnnotationManager.Config.typedefMode].
     */
    private val typedefAnnotationTargets =
        if (
            config.typedefMode == TypedefMode.INLINE || config.typedefMode == TypedefMode.NONE
        ) // just here for compatibility purposes
         ANNOTATION_EXTERNAL
        else ANNOTATION_EXTERNAL_ONLY

    /** Map of annotation class qualified name to its targets. */
    private val annotationClassTargets: Map<String, Set<AnnotationTarget>> = buildMap {
        putAll(DEFAULT_ANNOTATION_CLASS_TARGETS)
        // The typedef annotations are special: they should not be in the signature
        // files, but we want to include them in the external annotations file such that
        // tools can enforce them.
        for (name in TYPEDEF_ANNOTATION_NAMES) {
            put(name, typedefAnnotationTargets)
        }
        putAll(config.annotationClassTargets)
    }

    /**
     * The applicable targets for the [annotation].
     *
     * Care must be taken to ensure that this only accesses [AnnotationItem.qualifiedName] and
     * [AnnotationItem.resolve]. In particular, it must NOT access the attributes. That is because
     * the result must be identical for all [AnnotationItem] instances of an annotation class.
     */
    internal fun computeTargets(annotation: AnnotationItem): Set<AnnotationTarget> {
        val qualifiedName = annotation.qualifiedName
        if (config.passThroughAnnotations.contains(qualifiedName)) {
            return ANNOTATION_IN_ALL_STUBS
        }
        if (qualifiedName == ANDROID_REQUIRES_FLAG || qualifiedName == ANDROID_FLAGGED_API) {
            return annotation.apiFlag?.annotationTargets ?: ANNOTATION_IN_ALL_STUBS
        }

        val targets = annotationClassTargets[qualifiedName]
        if (targets != null) {
            return targets
        }

        if (qualifiedName.startsWith("android.annotation.")) {
            // internal annotations not mapped to androidx: things like @SystemApi. Skip from
            // stubs, external annotations, signature files, etc.
            return NO_ANNOTATION_TARGETS
        }

        if (qualifiedName.startsWith("android.processor.devicepolicy.")) {
            // We don't want to export device policy definition annotations.
            // Skip them from checking into the API signature, external
            // annotations, stubs, etc.
            return NO_ANNOTATION_TARGETS
        }

        // Determine the retention of the annotation: source retention annotations go
        // in the external annotations file, class and runtime annotations go in
        // the stubs files (except for the androidx annotations which are not included
        // in the SDK and therefore cannot be referenced from it due to apt's unfortunate
        // habit of loading all annotation classes it encounters.)

        if (qualifiedName.startsWith("androidx.annotation.")) {
            return ANNOTATION_EXTERNAL
        }

        // See if the annotation is pointing to an annotation class that is part of the API; if
        // not, skip it.
        val cls = annotation.resolve() ?: return NO_ANNOTATION_TARGETS
        if (!config.apiPredicate.test(cls)) {
            if (config.typedefMode != TypedefMode.NONE) {
                if (cls.modifiers.hasAnnotation(AnnotationItem::isTypeDefAnnotation)) {
                    return ANNOTATION_SIGNATURE_ONLY
                }
            }

            return NO_ANNOTATION_TARGETS
        }

        if (cls.isAnnotationType()) {
            val retention = cls.annotationClass.retention
            if (
                retention == AnnotationRetention.RUNTIME ||
                    retention == AnnotationRetention.CLASS ||
                    retention == AnnotationRetention.BINARY
            ) {
                return ANNOTATION_IN_ALL_STUBS
            }
        }

        return ANNOTATION_EXTERNAL
    }

    /** Check whether this has been configured in a way that could cause items to be reverted. */
    private fun couldRevertItems(): Boolean = config.apiFlags != null

    override fun hasAnyStubPurposesAnnotations(): Boolean {
        // This checks if items can be reverted because they were added in an extended API.
        // e.g. if a change to item `X` from the public API was reverted then the
        // previously released version `X'` will need to be written out to the stubs for the system
        // API, just as if it had been annotated with a show annotation for the API surface.
        return apiSurfaceSelector.hasAnyShowForStubPurposesAnnotations || couldRevertItems()
    }

    override fun hasHideAnnotations(modifiers: ModifierList): Boolean {
        // If there are no hide annotations and items cannot be reverted then this can never return
        // true. Reverted items can behave as if they are hidden it they are newly added.
        if (!apiSurfaceSelector.hasAnyHideAnnotations && !couldRevertItems()) {
            return false
        }
        return modifiers.hasAnnotation(AnnotationItem::isHideAnnotation)
    }

    override fun hasSuppressCompatibilityMetaAnnotations(modifiers: ModifierList): Boolean {
        if (config.suppressCompatibilityMetaAnnotations.isEmpty()) {
            return false
        }
        return modifiers.hasAnnotation(AnnotationItem::isSuppressCompatibilityAnnotation)
    }

    override fun getShowabilityForItem(item: SelectableItem): Showability {
        // Iterates over the annotations on the item and computes the showability for the item by
        // combining the showability of each annotation. The basic rules are:
        // * `show=true` beats `show=false`
        // * `recurse=true` beats `recurse=false`
        // * `forStubsOnly=false` beats `forStubsOnly=true`

        // The resulting showability of the item.
        var itemShowability = Showability.NO_EFFECT

        for (annotation in item.modifiers.annotations()) {
            val showability = annotation.showability
            if (showability == Showability.NO_EFFECT) {
                // NO_EFFECT has no effect on the result so just ignore it.
                continue
            }
            itemShowability = itemShowability.combineWith(showability)
        }

        if (item is MethodItem) {
            // If any of a method's super methods are part of a unstable API that needs to be
            // reverted then treat the method as if it is too.
            val revertUnstableApi =
                item.superMethods().any { methodItem -> methodItem.showability.revertUnstableApi() }
            if (revertUnstableApi) {
                itemShowability = itemShowability.combineWith(REVERT_UNSTABLE_API)
            }
        }

        val containingClass = item.containingClass()
        if (containingClass != null) {
            if (containingClass.showability.revertUnstableApi()) {
                itemShowability = itemShowability.combineWith(REVERT_UNSTABLE_API)
            }
        }

        // If the item is to be reverted then find the [Item] to which it will be reverted, if any,
        // and incorporate that into the [Showability].
        if (itemShowability.revertUnstableApi()) {
            val revertItem = findRevertItem(item)

            // If the [revertItem] cannot be found then there is no need to modify the item
            // showability as it is already in the correct state.
            if (revertItem != null) {
                val narrowerSurfaces = item.codebase.apiSurfaces.main.narrowerSurfaces
                val inNarrowerSurface =
                    narrowerSurfaces.isNotEmpty() &&
                        narrowerSurfaces.any { revertItem.selectedApiVariants.containsAny(it) }

                val forStubsOnly =
                    if (inNarrowerSurface) {
                        // The item was present in an API surface extended by the current API
                        // surface, so reverting an unstable API should keep it marked for stubs
                        // only rather than promoting it to the API surface currently being
                        // generated.
                        ShowOrHide.SHOW
                    } else if (revertItem.emit) {
                        // The reverted item is in the API surface currently being generated, not
                        // one that it extends, so it should always be shown. In that case
                        // forStubsOnly will have no effect whatever the value so this uses
                        // `NO_EFFECT` to indicate that.
                        ShowOrHide.NO_EFFECT
                    } else {
                        // The item is not in the API surface being generated, so must be in one
                        // that it extends so make sure to show it for stubs.
                        ShowOrHide.SHOW
                    }

                // Update the item showability to revert to the [revertItem]. This intentionally
                // does not modify it to use `SHOW` or `HIDE` but keeps it using
                // `REVERT_UNSTABLE_API` so that it can be propagated down onto overriding methods
                // and nested members if applicable.
                itemShowability =
                    itemShowability.copy(
                        forStubsOnly = forStubsOnly,
                        // Incorporate the item to be reverted into the [Showability].
                        revertItem = revertItem,
                    )

                // The codebase contains items which are to be reverted.
                item.codebase.markContainsRevertedItem()
            }
        }

        return itemShowability
    }

    /**
     * Local cache of the previously released codebase to avoid calling the provider for every
     * affected item.
     */
    override val previouslyReleasedCodebase by
        lazy(LazyThreadSafetyMode.NONE) { config.previouslyReleasedCodebaseProvider() }

    /**
     * Find the item to which [item] will be reverted.
     *
     * Searches the previously released API (if available).
     */
    private fun findRevertItem(item: SelectableItem) =
        SelectedApiUpdater.findRevertItem(config.reporter, previouslyReleasedCodebase, item)

    /** Finds the corresponding item in the previously released API, if available. */
    override fun findPreviouslyReleasedItem(item: SelectableItem): SelectableItem? {
        return previouslyReleasedCodebase?.let { item.findCorrespondingItemIn(it) }
    }

    override val typedefMode: TypedefMode = config.typedefMode

    companion object {
        private val TYPEDEF_ANNOTATION_NAMES =
            setOf(
                "android.annotation.IntDef",
                "androidx.annotation.IntDef",
                "android.annotation.StringDef",
                "androidx.annotation.StringDef",
                "android.annotation.LongDef",
                "androidx.annotation.LongDef",
            )

        private val DEFAULT_ANNOTATION_CLASS_TARGETS =
            buildMap<String, Set<AnnotationTarget>> {
                // Not directly API relevant
                put("android.view.ViewDebug.ExportedProperty", ANNOTATION_STUBS_ONLY)
                put("android.view.ViewDebug.CapturedViewProperty", ANNOTATION_STUBS_ONLY)

                // Retained in the sdk/jar stub source code so that SdkConstant files can be
                // extracted from those. This is useful for modularizing the main SDK stubs without
                // having to add a separate module SDK artifact for sdk constants.
                put("android.annotation.SdkConstant", ANNOTATION_SDK_STUBS_ONLY)

                put("android.annotation.RestrictedForEnvironment", ANNOTATION_EXTERNAL)

                // Skip known annotations that we (a) never want in external annotations and (b) we
                // are specially overwriting anyway in the stubs (and which are (c) not API
                // significant)
                val noAnnotationTargets =
                    listOf(
                        "com.android.modules.annotation.MinSdk",
                        "java.lang.annotation.Native",
                        "java.lang.SuppressWarnings",
                        "java.lang.Override",
                        "kotlin.Suppress",
                        "androidx.annotation.experimental.UseExperimental",
                        "androidx.annotation.OptIn",
                        "kotlin.UseExperimental",
                        "kotlin.OptIn",
                        // These optimization-related annotations shouldn't be exported.
                        "dalvik.annotation.optimization.CriticalNative",
                        "dalvik.annotation.optimization.FastNative",
                        "dalvik.annotation.optimization.NeverCompile",
                        "dalvik.annotation.optimization.NeverInline",
                        "dalvik.annotation.optimization.ReachabilitySensitive",
                        // TODO(aurimas): consider using annotation directly instead of modifiers
                        KOTLIN_DEPRECATED,
                        JAVA_LANG_DEPRECATED, // tracked separately as a pseudo-modifier
                        // Metalava already tracks all the methods that get generated due to these
                        // annotations.
                        "kotlin.jvm.JvmOverloads",
                        JVM_FIELD,
                        JVM_STATIC,
                        KOTLIN_METADATA,
                        JVM_NAME,
                    )
                for (name in noAnnotationTargets) {
                    put(name, NO_ANNOTATION_TARGETS)
                }

                // Below this we perform the correct lookup: check API predicate, and
                // check that retention is class or runtime, but we've hardcoded the answers here
                // for some common annotations.
                val inAllStubs =
                    listOf(
                        "android.widget.RemoteViews.RemoteView",
                        "kotlin.annotation.Target",
                        "kotlin.annotation.Retention",
                        "kotlin.annotation.Repeatable",
                        "kotlin.annotation.MustBeDocumented",
                        "kotlin.DslMarker",
                        "kotlin.PublishedApi",
                        "kotlin.ExtensionFunctionType",
                        "java.lang.FunctionalInterface",
                        "java.lang.SafeVarargs",
                        "java.lang.annotation.Documented",
                        "java.lang.annotation.Inherited",
                        "java.lang.annotation.Repeatable",
                        "java.lang.annotation.Retention",
                        "java.lang.annotation.Target",
                        // @android.annotation.Nullable and NonNullable specially recognized
                        // annotations by the Kotlin compiler 1.3 and above: they always go in the
                        // stubs.
                        ANDROID_NULLABLE,
                        ANDROID_NONNULL,
                        ANDROIDX_NULLABLE,
                        ANDROIDX_NONNULL,
                        // @RecentlyNullable and @RecentlyNonNull are specially recognized
                        // annotations by the Kotlin compiler: they always go in the stubs.
                        RECENTLY_NULLABLE,
                        RECENTLY_NONNULL,
                    )
                for (name in inAllStubs) {
                    put(name, ANNOTATION_IN_ALL_STUBS)
                }
            }
    }
}

/**
 * Extension of [AnnotationInfo] that supports initializing properties based on the
 * [DefaultAnnotationManager.Config].
 *
 * The properties are initialized lazily to avoid doing more work than necessary.
 */
private class LazyAnnotationInfo(
    private val annotationManager: DefaultAnnotationManager,
    private val config: Config,
    private val annotationItem: AnnotationItem,
) : AnnotationInfo {

    private val qualifiedName = annotationItem.qualifiedName

    override val targets by
        lazy(LazyThreadSafetyMode.NONE) { annotationManager.computeTargets(annotationItem) }

    override val typeNullability = computeTypeNullability(qualifiedName)

    /** Compute lazily to avoid doing any more work than strictly necessary. */
    override val surfaceData by
        lazy(LazyThreadSafetyMode.NONE) {
            config.apiSurfaceSelector.findSurfaceAnnotationData(annotationItem)
        }

    /** Compute lazily to avoid doing any more work than strictly necessary. */
    override val showability by
        lazy(LazyThreadSafetyMode.NONE) {
            surfaceData?.showability
                // Check flags before using default
                ?: apiFlag?.showability
                ?: Showability.NO_EFFECT
        }

    override val apiFlag by lazy(LazyThreadSafetyMode.NONE) { getFlagForAnnotation(annotationItem) }

    private fun getFlagForAnnotation(annotationItem: AnnotationItem): ApiFlag? {
        val apiFlags = config.apiFlags ?: return null
        val flagName = annotationItem.optionalFlagName ?: return null
        return apiFlags[flagName]
    }

    override val annotationClass
        get() = annotationClassItem?.annotationClass

    /** Resolve the [AnnotationItem] to a [ClassItem] lazily. */
    private val annotationClassItem by lazy(LazyThreadSafetyMode.NONE, annotationItem::resolve)

    /** Flag to detect whether the [checkResolvedAnnotationClass] is in a cycle. */
    private var isCheckingResolvedAnnotationClass = false

    /**
     * Check to see whether the resolved annotation class matches the supplied predicate.
     *
     * If the annotation class could not be resolved or the annotation is part of a cycle, e.g.
     * `java.lang.annotation.Retention` is annotated with itself, then returns false, otherwise it
     * returns the result of applying the supplied predicate to the resolved class.
     */
    private fun checkResolvedAnnotationClass(test: (ClassItem) -> Boolean): Boolean {
        if (isCheckingResolvedAnnotationClass) {
            return false
        }

        try {
            isCheckingResolvedAnnotationClass = true

            // Try and resolve this to the class to see if it has been annotated with hide meta
            // annotations. If it could not be resolved then assume it has not been annotated.
            val resolved = annotationClassItem ?: return false

            // Return the result of applying the test to the resolved class.
            return test(resolved)
        } finally {
            isCheckingResolvedAnnotationClass = false
        }
    }

    private fun isDirectlyExperimental(qualifiedName: String): Boolean {
        return qualifiedName == SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED ||
            config.suppressCompatibilityMetaAnnotations.contains(qualifiedName)
    }

    /**
     * If true then this annotation will suppress compatibility checking on annotated items.
     *
     * This is true if this annotation is directly annotated with a suppress annotation, or is
     * annotated directly with an annotation that is annotated with a suppress annotation. It won't
     * check more than 1 level up (see b/460835117).
     */
    override val suppressCompatibility by
        lazy(LazyThreadSafetyMode.NONE) {
            isDirectlyExperimental(qualifiedName) ||
                checkResolvedAnnotationClass {
                    it.modifiers.annotations().any { metaAnnotation ->
                        isDirectlyExperimental(metaAnnotation.qualifiedName)
                    }
                }
        }
}
