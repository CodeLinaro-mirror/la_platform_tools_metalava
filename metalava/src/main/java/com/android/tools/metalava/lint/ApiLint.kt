/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.metalava.KotlinInteropChecks
import com.android.tools.metalava.lint.ResourceType.AAPT
import com.android.tools.metalava.lint.ResourceType.ANIM
import com.android.tools.metalava.lint.ResourceType.ANIMATOR
import com.android.tools.metalava.lint.ResourceType.ARRAY
import com.android.tools.metalava.lint.ResourceType.ATTR
import com.android.tools.metalava.lint.ResourceType.BOOL
import com.android.tools.metalava.lint.ResourceType.COLOR
import com.android.tools.metalava.lint.ResourceType.DIMEN
import com.android.tools.metalava.lint.ResourceType.DRAWABLE
import com.android.tools.metalava.lint.ResourceType.FONT
import com.android.tools.metalava.lint.ResourceType.FRACTION
import com.android.tools.metalava.lint.ResourceType.ID
import com.android.tools.metalava.lint.ResourceType.INTEGER
import com.android.tools.metalava.lint.ResourceType.INTERPOLATOR
import com.android.tools.metalava.lint.ResourceType.LAYOUT
import com.android.tools.metalava.lint.ResourceType.MACRO
import com.android.tools.metalava.lint.ResourceType.MENU
import com.android.tools.metalava.lint.ResourceType.MIPMAP
import com.android.tools.metalava.lint.ResourceType.NAVIGATION
import com.android.tools.metalava.lint.ResourceType.OVERLAYABLE
import com.android.tools.metalava.lint.ResourceType.PLURALS
import com.android.tools.metalava.lint.ResourceType.PUBLIC
import com.android.tools.metalava.lint.ResourceType.RAW
import com.android.tools.metalava.lint.ResourceType.SAMPLE_DATA
import com.android.tools.metalava.lint.ResourceType.STRING
import com.android.tools.metalava.lint.ResourceType.STYLE
import com.android.tools.metalava.lint.ResourceType.STYLEABLE
import com.android.tools.metalava.lint.ResourceType.STYLE_ITEM
import com.android.tools.metalava.lint.ResourceType.TRANSITION
import com.android.tools.metalava.lint.ResourceType.XML
import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.SetMinSdkVersion
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrVariableTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.InheritableItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_STRING
import com.android.tools.metalava.model.JAVA_LANG_THROWABLE
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MultipleTypeVisitor
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterListOwner
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.value.asInt
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.ABSTRACT_INNER
import com.android.tools.metalava.reporter.Issues.ACRONYM_NAME
import com.android.tools.metalava.reporter.Issues.ACTION_VALUE
import com.android.tools.metalava.reporter.Issues.ALL_UPPER
import com.android.tools.metalava.reporter.Issues.ANDROID_URI
import com.android.tools.metalava.reporter.Issues.ARRAY_RETURN
import com.android.tools.metalava.reporter.Issues.ASYNC_SUFFIX_FUTURE
import com.android.tools.metalava.reporter.Issues.AUTO_BOXING
import com.android.tools.metalava.reporter.Issues.BAD_FUTURE
import com.android.tools.metalava.reporter.Issues.BANNED_THROW
import com.android.tools.metalava.reporter.Issues.BUILDER_SET_STYLE
import com.android.tools.metalava.reporter.Issues.CALLBACK_INTERFACE
import com.android.tools.metalava.reporter.Issues.CALLBACK_METHOD_NAME
import com.android.tools.metalava.reporter.Issues.CALLBACK_NAME
import com.android.tools.metalava.reporter.Issues.COMPILE_TIME_CONSTANT
import com.android.tools.metalava.reporter.Issues.CONCRETE_COLLECTION
import com.android.tools.metalava.reporter.Issues.CONFIG_FIELD_NAME
import com.android.tools.metalava.reporter.Issues.CONTEXT_FIRST
import com.android.tools.metalava.reporter.Issues.CONTEXT_NAME_SUFFIX
import com.android.tools.metalava.reporter.Issues.DATA_CLASS_DEFINITION
import com.android.tools.metalava.reporter.Issues.ENDS_WITH_IMPL
import com.android.tools.metalava.reporter.Issues.ENUM
import com.android.tools.metalava.reporter.Issues.EQUALS_AND_HASH_CODE
import com.android.tools.metalava.reporter.Issues.EXCEPTION_NAME
import com.android.tools.metalava.reporter.Issues.EXECUTOR_REGISTRATION
import com.android.tools.metalava.reporter.Issues.EXTENDS_ERROR
import com.android.tools.metalava.reporter.Issues.FORBIDDEN_SUPER_CLASS
import com.android.tools.metalava.reporter.Issues.FRACTION_FLOAT
import com.android.tools.metalava.reporter.Issues.GENERIC_CALLBACKS
import com.android.tools.metalava.reporter.Issues.GENERIC_EXCEPTION
import com.android.tools.metalava.reporter.Issues.GETTER_ON_BUILDER
import com.android.tools.metalava.reporter.Issues.GETTER_SETTER_NAMES
import com.android.tools.metalava.reporter.Issues.HEAVY_BIT_SET
import com.android.tools.metalava.reporter.Issues.INTENT_BUILDER_NAME
import com.android.tools.metalava.reporter.Issues.INTENT_NAME
import com.android.tools.metalava.reporter.Issues.INTERFACE_CONSTANT
import com.android.tools.metalava.reporter.Issues.INTERNAL_CLASSES
import com.android.tools.metalava.reporter.Issues.INTERNAL_FIELD
import com.android.tools.metalava.reporter.Issues.INVALID_NULLABILITY_OVERRIDE
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.Issues.KOTLIN_DEFAULT_PARAMETER_ORDER
import com.android.tools.metalava.reporter.Issues.KOTLIN_OPERATOR
import com.android.tools.metalava.reporter.Issues.LISTENER_INTERFACE
import com.android.tools.metalava.reporter.Issues.LISTENER_LAST
import com.android.tools.metalava.reporter.Issues.MANAGER_CONSTRUCTOR
import com.android.tools.metalava.reporter.Issues.MANAGER_LOOKUP
import com.android.tools.metalava.reporter.Issues.MENTIONS_GOOGLE
import com.android.tools.metalava.reporter.Issues.METHOD_NAME_TENSE
import com.android.tools.metalava.reporter.Issues.METHOD_NAME_UNITS
import com.android.tools.metalava.reporter.Issues.MIN_MAX_CONSTANT
import com.android.tools.metalava.reporter.Issues.MISSING_BUILD_METHOD
import com.android.tools.metalava.reporter.Issues.MISSING_GETTER_MATCHING_BUILDER
import com.android.tools.metalava.reporter.Issues.MISSING_INNER_NULLABILITY
import com.android.tools.metalava.reporter.Issues.MISSING_NULLABILITY
import com.android.tools.metalava.reporter.Issues.MUTABLE_BARE_FIELD
import com.android.tools.metalava.reporter.Issues.NOT_CLOSEABLE
import com.android.tools.metalava.reporter.Issues.NO_BYTE_OR_SHORT
import com.android.tools.metalava.reporter.Issues.NO_CLONE
import com.android.tools.metalava.reporter.Issues.NO_SETTINGS_PROVIDER
import com.android.tools.metalava.reporter.Issues.NULLABLE_COLLECTION
import com.android.tools.metalava.reporter.Issues.NULLABLE_COLLECTION_ELEMENT
import com.android.tools.metalava.reporter.Issues.ON_NAME_EXPECTED
import com.android.tools.metalava.reporter.Issues.OPTIONAL_BUILDER_CONSTRUCTOR_ARGUMENT
import com.android.tools.metalava.reporter.Issues.OVERLAPPING_CONSTANTS
import com.android.tools.metalava.reporter.Issues.PACKAGE_LAYERING
import com.android.tools.metalava.reporter.Issues.PAIRED_REGISTRATION
import com.android.tools.metalava.reporter.Issues.PARCELABLE_LIST
import com.android.tools.metalava.reporter.Issues.PARCEL_CONSTRUCTOR
import com.android.tools.metalava.reporter.Issues.PARCEL_CREATOR
import com.android.tools.metalava.reporter.Issues.PARCEL_NOT_FINAL
import com.android.tools.metalava.reporter.Issues.PERCENTAGE_INT
import com.android.tools.metalava.reporter.Issues.PROTECTED_MEMBER
import com.android.tools.metalava.reporter.Issues.PUBLIC_TYPEDEF
import com.android.tools.metalava.reporter.Issues.RAW_AIDL
import com.android.tools.metalava.reporter.Issues.RESOURCE_FIELD_NAME
import com.android.tools.metalava.reporter.Issues.RESOURCE_STYLE_FIELD_NAME
import com.android.tools.metalava.reporter.Issues.RESOURCE_VALUE_FIELD_NAME
import com.android.tools.metalava.reporter.Issues.RETHROW_REMOTE_EXCEPTION
import com.android.tools.metalava.reporter.Issues.SERVICE_NAME
import com.android.tools.metalava.reporter.Issues.SETTER_RETURNS_THIS
import com.android.tools.metalava.reporter.Issues.SINGLETON_CONSTRUCTOR
import com.android.tools.metalava.reporter.Issues.SINGLE_METHOD_INTERFACE
import com.android.tools.metalava.reporter.Issues.SINGULAR_CALLBACK
import com.android.tools.metalava.reporter.Issues.START_WITH_LOWER
import com.android.tools.metalava.reporter.Issues.START_WITH_UPPER
import com.android.tools.metalava.reporter.Issues.STATIC_FINAL_BUILDER
import com.android.tools.metalava.reporter.Issues.STATIC_UTILS
import com.android.tools.metalava.reporter.Issues.STREAM_FILES
import com.android.tools.metalava.reporter.Issues.TOP_LEVEL_BUILDER
import com.android.tools.metalava.reporter.Issues.TYPE_PARAMETER_NAME
import com.android.tools.metalava.reporter.Issues.UNIQUE_KOTLIN_OPERATOR
import com.android.tools.metalava.reporter.Issues.USER_HANDLE
import com.android.tools.metalava.reporter.Issues.USER_HANDLE_NAME
import com.android.tools.metalava.reporter.Issues.USE_ICU
import com.android.tools.metalava.reporter.Issues.USE_PARCEL_FILE_DESCRIPTOR
import com.android.tools.metalava.reporter.Issues.VISIBLY_SYNCHRONIZED
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import java.util.Locale

/**
 * The [ApiLint] analyzer checks the API against a known set of preferred API practices by the
 * Android API council.
 */
class ApiLint
private constructor(
    private val codebase: Codebase,
    oldCodebase: Codebase?,
    reporter: Reporter,
    apiPredicateConfig: ApiPredicate.Config,
    private val config: Config,
) :
    ApiVisitor(
        visitParameterItems = false,
        apiFilters = ApiType.PUBLIC_API.getNonElidingApiFilters(apiPredicateConfig),
        targetLanguages = TargetLanguageSet.SOURCE,
    ) {

    data class Config(
        /** Platform [Manifest] from which */
        val manifest: Manifest = emptyManifest,

        /** The list of allowed acronyms. */
        val allowedAcronyms: List<String> = emptyList(),
    )

    private val manifest
        get() = config.manifest

    private val allowedAcronyms
        get() = config.allowedAcronyms

    private val filteredReporter = FilteringReporter(reporter, oldCodebase, filterEmit)

    private fun report(
        id: Issue,
        item: Item,
        message: String,
        location: FileLocation = FileLocation.UNKNOWN,
        maximumSeverity: Severity = Severity.UNLIMITED,
    ) {
        filteredReporter.report(id, item, message, location, maximumSeverity)
    }

    private fun check() {
        codebase.accept(this)
    }

    private val kotlinInterop: KotlinInteropChecks = KotlinInteropChecks(this.filteredReporter)

    override fun visitClass(cls: ClassItem) {
        val methods = cls.filteredMethods(filterReference).asSequence()
        val fields = cls.filteredFields(filterReference, showUnannotated).asSequence()
        val constructors = cls.filteredConstructors(filterReference)
        val superClass = cls.filteredSuperclass(filterReference)
        val interfaces = cls.filteredInterfaceTypes(filterReference).asSequence()
        val allCallables = methods.asSequence() + constructors.asSequence()
        filteredReporter.withContext(cls) {
            checkClass(cls, methods, constructors, allCallables, fields, superClass, interfaces)
            kotlinInterop.checkClass(cls, allCallables + fields)
        }
    }

    override fun visitCallable(callable: CallableItem) {
        filteredReporter.withContext(callable) {
            checkExceptions(callable, filterReference)
            checkContextFirst(callable)
            checkListenerLast(callable)
            val returnType = callable.returnType()
            checkType(returnType, callable)
            checkNullableCollections(returnType, callable)
            for (parameter in callable.parameters()) {
                checkType(parameter.type(), parameter)
            }
            checkParameterOrder(callable)
        }
    }

    override fun visitMethod(method: MethodItem) {
        filteredReporter.withContext(method) {
            checkMethodNames(method)
            checkProtected(method)
            checkSynchronized(method)
            checkIntentBuilder(method)
            checkUnits(method)
            checkTense(method)
            checkClone(method)
            checkCallbackOrListenerMethod(method)
            checkMethodSuffixListenableFutureReturn(method)
            checkTypeParameterNames(method)
            kotlinInterop.checkMethod(method)
        }
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        filteredReporter.withContext(constructor) { kotlinInterop.checkConstructor(constructor) }
    }

    override fun visitField(field: FieldItem) {
        filteredReporter.withContext(field) {
            checkField(field)
            checkType(field.type(), field)
            kotlinInterop.checkField(field)
        }
    }

    override fun visitProperty(property: PropertyItem) {
        filteredReporter.withContext(property) { kotlinInterop.checkProperty(property) }
    }

    private fun checkType(type: TypeItem, item: Item) {
        val typeString = type.toTypeString()
        checkPfd(typeString, item)
        checkNumbers(typeString, item)
        checkCollections(type, item)
        checkCollectionsOverArrays(type, typeString, item)
        checkBoxed(type, item)
        checkIcu(type, typeString, item)
        checkBitSet(type, item)
        checkHasNullability(item)
        checkUri(typeString, item)
        checkFutures(typeString, item)
    }

    // Enforce type parameter naming rules:
    // https://developer.android.com/kotlin/style-guide#type_variable_names
    fun <T> checkTypeParameterNames(item: T) where T : Item, T : TypeParameterListOwner {
        val invalidTypeParameters =
            item.typeParameterList
                .filter { !isValidGenericTypeName(it.name()) }
                .map { "\"${it.name()}\"" }

        if (invalidTypeParameters.isNotEmpty()) {
            report(
                TYPE_PARAMETER_NAME,
                item,
                "Invalid type parameter ${if (invalidTypeParameters.size > 1) "names" else "name"} ${
                    invalidTypeParameters.joinToString(
                        separator = ", "
                    )
                }. Type parameter names must follow" +
                    " the Google naming guidelines specified here:" +
                    " https://developer.android.com/kotlin/style-guide#type_variable_names"
            )
        }
    }

    /*
     * Generic parameter naming rules that this method is checking can be found here:
     * https://developer.android.com/kotlin/style-guide#type_variable_names
     */
    private fun isValidGenericTypeName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.length == 1) {
            return name[0] >= 'A' && name[0] <= 'Z'
        }
        return name.endsWith("T") &&
            name[0] >= 'A' &&
            name[0] <= 'Z' &&
            name.all { it.isLetterOrDigit() }
    }

    private fun checkClass(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>,
        callables: Sequence<CallableItem>,
        fields: Sequence<FieldItem>,
        superClass: ClassItem?,
        interfaces: Sequence<TypeItem>
    ) {
        checkTypeParameterNames(cls)
        checkEquals(methods)
        checkEnums(cls)
        checkClassNames(cls)
        checkCallbacks(cls)
        checkListeners(cls, methods)
        checkParcelable(cls, methods, constructors, fields)
        checkRegistrationMethods(cls, methods)
        checkHelperClasses(cls, methods, fields)
        checkBuilder(cls, methods, constructors, superClass, interfaces)
        checkAidl(cls, superClass, interfaces)
        checkInternal(cls)
        checkLayering(cls, callables, fields)
        checkBooleans(methods)
        checkFlags(fields)
        checkGoogle(cls, methods, fields)
        checkManager(cls, methods, constructors)
        checkStaticUtils(cls, methods, constructors, fields)
        // suspend funs are assumed to follow the rules of structured concurrency by default:
        // any callbacks supplied to a suspend fun will not be called (nor references to them held)
        // after the call returns. The calling CoroutineContext generally contains a
        // ContinuationInterceptor/CoroutineDispatcher which provides a default analog to an
        // Executor; the ExecutorRegistration lint check is not necessary for suspend funs.
        checkCallbackHandlers(cls, callables.filterNot { it.modifiers.isSuspend() }, superClass)
        checkGenericCallbacks(cls, methods, constructors, fields)
        checkResourceNames(cls, fields)
        checkFiles(callables)
        checkManagerList(cls, methods)
        checkAbstractInner(cls)
        checkError(cls, superClass)
        checkCloseable(cls, methods)
        checkNotKotlinOperator(methods)
        checkUserHandle(cls, methods)
        checkParams(cls)
        checkSingleton(cls, methods, constructors)
        checkExtends(cls)
        checkTypedef(cls)
        checkAccessorNullabilityMatches(methods)
        checkDataClass(cls)
    }

    private fun checkField(field: FieldItem) {
        val modifiers = field.modifiers
        if (modifiers.isStatic() && modifiers.isFinal()) {
            checkConstantNames(field)
            checkActions(field)
            checkIntentExtras(field)
        }
        checkProtected(field)
        checkServices(field)
        checkFieldName(field)
        checkSettingKeys(field)
        checkNullableCollections(field.type(), field)
    }

    private fun checkEnums(cls: ClassItem) {
        if (cls.isEnum()) {
            report(ENUM, cls, "Enums are discouraged in Android APIs")
        }
    }

    private fun checkMethodNames(method: MethodItem) {
        // Existing violations
        val containing = method.containingClass().qualifiedName()
        if (
            containing.startsWith("android.opengl") ||
                containing.startsWith("android.renderscript") ||
                containing.startsWith("android.database.sqlite.") ||
                containing == "android.system.OsConstants"
        ) {
            return
        }

        val name =
            if (method.isKotlin() && method.name().contains("-")) {
                // Kotlin renames certain methods in binary, e.g. fun foo(bar: Bar) where Bar is an
                // inline class becomes foo-HASHCODE. We only want to consider the original name for
                // this API lint check
                method.name().substringBefore("-")
            } else {
                method.name()
            }
        val first = name[0]

        when {
            first !in 'a'..'z' ->
                report(
                    START_WITH_LOWER,
                    method,
                    "Method name must start with lowercase char: $name"
                )
            hasAcronyms(name, allowedAcronyms) -> {
                report(
                    ACRONYM_NAME,
                    method,
                    "Acronyms should not be capitalized in method names: was `$name`, should this be `${
                        decapitalizeAcronyms(
                            name,
                            allowedAcronyms
                        )
                    }`?"
                )
            }
        }
    }

    private fun checkClassNames(cls: ClassItem) {
        // Don't check the name for the class generated to hold top level declarations, which isn't
        // a real class.
        if (cls.simpleName() == "\$TopLevelDeclarations") return
        // Existing violations
        val qualifiedName = cls.qualifiedName()
        if (
            qualifiedName.startsWith("android.opengl") ||
                qualifiedName.startsWith("android.renderscript") ||
                qualifiedName.startsWith("android.database.sqlite.") ||
                qualifiedName.startsWith("android.R.")
        ) {
            return
        }

        val name = cls.simpleName()
        val first = name[0]
        when {
            first !in 'A'..'Z' -> {
                report(START_WITH_UPPER, cls, "Class must start with uppercase char: $name")
            }
            hasAcronyms(name, allowedAcronyms) -> {
                report(
                    ACRONYM_NAME,
                    cls,
                    "Acronyms should not be capitalized in class names: was `$name`, should this be `${
                        decapitalizeAcronyms(
                            name,
                            allowedAcronyms
                        )
                    }`?"
                )
            }
            name.endsWith("Impl") -> {
                report(
                    ENDS_WITH_IMPL,
                    cls,
                    "Don't expose your implementation details: `$name` ends with `Impl`"
                )
            }
        }
    }

    private fun checkConstantNames(field: FieldItem) {
        // Skip this check on Kotlin
        if (field.isKotlin()) {
            return
        }

        // Existing violations
        val qualified = field.containingClass().qualifiedName()
        if (
            qualified.startsWith("android.os.Build") ||
                qualified == "android.system.OsConstants" ||
                qualified == "android.media.MediaCodecInfo" ||
                qualified.startsWith("android.opengl.") ||
                qualified.startsWith("android.R.")
        ) {
            return
        }

        val name = field.name()
        if (!constantNamePattern.matches(name)) {
            val suggested = SdkVersionInfo.camelCaseToUnderlines(name).uppercase(Locale.US)
            report(
                ALL_UPPER,
                field,
                "Constant field names must be named with only upper case characters: `$qualified#$name`, should be `$suggested`?"
            )
        } else if (
            (name.startsWith("MIN_") || name.startsWith("MAX_")) && !field.type().isString()
        ) {
            report(
                MIN_MAX_CONSTANT,
                field,
                "If min/max could change in future, make them dynamic methods: $qualified#$name"
            )
        } else if (
            (field.type() is PrimitiveTypeItem || field.type().isString()) &&
                field.constantValue?.asLiteralValue() == null
        ) {
            report(
                COMPILE_TIME_CONSTANT,
                field,
                "All constants must be defined at compile time: $qualified#$name"
            )
        }
    }

    private fun checkCallbacks(cls: ClassItem) {
        // Existing violations
        val qualified = cls.qualifiedName()
        if (qualified == "android.speech.tts.SynthesisCallback") {
            return
        }

        val name = cls.simpleName()
        when {
            name.endsWith("Callbacks") -> {
                report(SINGULAR_CALLBACK, cls, "Callback class names should be singular: $name")
            }
            name.endsWith("Observer") -> {
                val prefix = name.removeSuffix("Observer")
                report(CALLBACK_NAME, cls, "Class should be named ${prefix}Callback")
            }
            name.endsWith("Callback") -> {
                if (cls.isInterface()) {
                    report(
                        CALLBACK_INTERFACE,
                        cls,
                        "Callbacks must be abstract class instead of interface to enable extension in future API levels: $name"
                    )
                }
            }
        }
    }

    private fun checkCallbackOrListenerMethod(method: MethodItem) {
        if (method.modifiers.isStatic() || method.modifiers.isFinal()) {
            return
        }
        val cls = method.containingClass()

        // These are not listeners or callbacks despite their name.
        when {
            cls.modifiers.isFinal() -> return
            cls.qualifiedName() == "android.telephony.ims.ImsCallSessionListener" -> return
        }

        val containingClassSimpleName = cls.simpleName()
        val kind =
            when {
                containingClassSimpleName.endsWith("Callback") -> "Callback"
                containingClassSimpleName.endsWith("Listener") -> "Listener"
                else -> return
            }
        val methodName = method.name()

        if (!onCallbackNamePattern.matches(methodName)) {
            report(
                CALLBACK_METHOD_NAME,
                method,
                "$kind method names must follow the on<Something> style: $methodName"
            )
        }

        for (parameter in method.parameters()) {
            // We require nonnull collections as parameters to callback methods
            checkNullableCollections(parameter.type(), parameter)
        }
    }

    private fun checkListeners(cls: ClassItem, methods: Sequence<MethodItem>) {
        val name = cls.simpleName()
        if (name.endsWith("Listener")) {
            if (cls.isClass()) {
                report(
                    LISTENER_INTERFACE,
                    cls,
                    "Listeners should be an interface, or otherwise renamed Callback: $name"
                )
            } else {
                if (methods.count() == 1) {
                    val method = methods.first()
                    val methodName = method.name()
                    if (
                        methodName.startsWith("On") &&
                            !("${methodName}Listener").equals(cls.simpleName(), ignoreCase = true)
                    ) {
                        report(
                            SINGLE_METHOD_INTERFACE,
                            cls,
                            "Single listener method name must match class name"
                        )
                    }
                }
            }
        }
    }

    private fun checkGenericCallbacks(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>,
        fields: Sequence<FieldItem>
    ) {
        val simpleName = cls.simpleName()
        if (!simpleName.endsWith("Callback") && !simpleName.endsWith("Listener")) return

        // The following checks for an interface or abstract class of the same shape as
        // OutcomeReceiver, i.e. two methods, both with the "on" prefix for callbacks, one of
        // them taking a Throwable or subclass.
        if (constructors.any { !it.isImplicitConstructor() }) return
        if (fields.any()) return
        if (methods.count() != 2) return

        fun isSingleParamCallbackMethod(method: MethodItem) =
            method.parameters().size == 1 &&
                method.name().startsWith("on") &&
                method.parameters().first().type() !is PrimitiveTypeItem &&
                (method.returnType() as? PrimitiveTypeItem)?.kind == Primitive.VOID

        if (!methods.all(::isSingleParamCallbackMethod)) return

        fun TypeItem.extendsThrowable() =
            // The only types that can represent a Throwable are either a type parameter or class.
            (this as? ClassOrVariableTypeItem)
                ?.asErasedClass(codebase)
                ?.extends(JAVA_LANG_THROWABLE) == true

        fun isErrorMethod(method: MethodItem) =
            method.name().run { startsWith("onError") || startsWith("onFail") } &&
                method.parameters().first().type().extendsThrowable()

        if (methods.count(::isErrorMethod) == 1) {
            report(
                GENERIC_CALLBACKS,
                cls,
                "${cls.fullName()} can be replaced with OutcomeReceiver<R,E> (platform)" +
                    " or suspend fun / ListenableFuture (AndroidX)."
            )
        }
    }

    private fun checkActions(field: FieldItem) {
        val name = field.name()
        if (
            name.startsWith("EXTRA_") || name == "SERVICE_INTERFACE" || name == "PROVIDER_INTERFACE"
        ) {
            return
        }
        if (!field.type().isString()) {
            return
        }
        val value = field.constantValue?.asString() ?: return
        if (!(name.contains("_ACTION") || name.contains("ACTION_") || value.contains(".action."))) {
            return
        }
        val className = field.containingClass().qualifiedName()
        when (className) {
            "android.Manifest.permission" -> return
        }
        if (!name.startsWith("ACTION_")) {
            report(INTENT_NAME, field, "Intent action constant name must be ACTION_FOO: $name")
            return
        }
        val prefix =
            when (className) {
                "android.content.Intent" -> "android.intent.action"
                "android.provider.Settings" -> "android.settings"
                else -> field.containingClass().containingPackage().qualifiedName() + ".action"
            }
        val expected = prefix + "." + name.substring(7)
        if (value != expected) {
            report(
                ACTION_VALUE,
                field,
                "Inconsistent action value; expected `$expected`, was `$value`"
            )
        }
    }

    private fun checkIntentExtras(field: FieldItem) {
        val className = field.containingClass().qualifiedName()
        if (
            className == "android.app.Notification" ||
                className == "android.appwidget.AppWidgetManager"
        ) {
            return
        }

        val name = field.name()
        if (name.startsWith("ACTION_") || !field.type().isString()) {
            return
        }
        val value = field.constantValue?.asString() ?: return
        if (!(name.contains("_EXTRA") || name.contains("EXTRA_") || value.contains(".extra"))) {
            return
        }
        if (!name.startsWith("EXTRA_")) {
            report(INTENT_NAME, field, "Intent extra constant name must be EXTRA_FOO: $name")
            return
        }

        val packageName = field.containingClass().containingPackage().qualifiedName()
        val prefix =
            when {
                className == "android.content.Intent" -> "android.intent.extra"
                else -> "$packageName.extra"
            }
        val expected = prefix + "." + name.substring(6)
        if (value != expected) {
            report(
                ACTION_VALUE,
                field,
                "Inconsistent extra value; expected `$expected`, was `$value`"
            )
        }
    }

    private fun checkEquals(methods: Sequence<MethodItem>) {
        var equalsMethod: MethodItem? = null
        var hashCodeMethod: MethodItem? = null

        for (method in methods) {
            if (isEqualsMethod(method)) {
                equalsMethod = method
            } else if (isHashCodeMethod(method)) {
                hashCodeMethod = method
            }
        }
        if ((equalsMethod == null) != (hashCodeMethod == null)) {
            val method = equalsMethod ?: hashCodeMethod!!
            report(
                EQUALS_AND_HASH_CODE,
                method,
                "Must override both equals and hashCode; missing one in ${method.containingClass().qualifiedName()}"
            )
        }
    }

    private fun isEqualsMethod(method: MethodItem): Boolean {
        return method.name() == "equals" &&
            method.parameters().size == 1 &&
            method.parameters()[0].type().isJavaLangObject() &&
            !method.modifiers.isStatic()
    }

    private fun isHashCodeMethod(method: MethodItem): Boolean {
        return method.name() == "hashCode" &&
            method.parameters().isEmpty() &&
            !method.modifiers.isStatic()
    }

    private fun checkParcelable(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>,
        fields: Sequence<FieldItem>
    ) {
        if (!cls.implements("android.os.Parcelable")) {
            return
        }

        if (fields.none { it.name() == "CREATOR" }) {
            report(
                PARCEL_CREATOR,
                cls,
                "Parcelable requires a `CREATOR` field; missing in ${cls.qualifiedName()}"
            )
        }
        if (methods.none { it.name() == "writeToParcel" }) {
            report(
                PARCEL_CREATOR,
                cls,
                "Parcelable requires `void writeToParcel(Parcel, int)`; missing in ${cls.qualifiedName()}"
            )
        }
        if (methods.none { it.name() == "describeContents" }) {
            report(
                PARCEL_CREATOR,
                cls,
                "Parcelable requires `public int describeContents()`; missing in ${cls.qualifiedName()}"
            )
        }

        if (!cls.modifiers.isFinal()) {
            report(
                PARCEL_NOT_FINAL,
                cls,
                "Parcelable classes must be final: ${cls.qualifiedName()} is not final"
            )
        }

        val parcelConstructor =
            constructors.firstOrNull {
                val parameters = it.parameters()
                parameters.size == 1 && parameters[0].type().toTypeString() == "android.os.Parcel"
            }

        if (parcelConstructor != null) {
            report(
                PARCEL_CONSTRUCTOR,
                parcelConstructor,
                "Parcelable inflation is exposed through CREATOR, not raw constructors, in ${cls.qualifiedName()}"
            )
        }
    }

    private fun checkProtected(member: MemberItem) {
        val modifiers = member.modifiers
        if (modifiers.isProtected()) {
            if (
                member.name() == "finalize" && member is MethodItem && member.parameters().isEmpty()
            ) {
                return
            }

            report(
                PROTECTED_MEMBER,
                member,
                "Protected ${if (member is MethodItem) "methods" else "fields"} not allowed; must be public: ${member.describe()}}"
            )
        }
    }

    private fun checkFieldName(field: FieldItem) {
        val className = field.containingClass().qualifiedName()
        val modifiers = field.modifiers
        if (!modifiers.isFinal()) {
            if (
                className !in classesWithBareFields &&
                    !className.endsWith("LayoutParams") &&
                    !className.startsWith("android.util.Mutable")
            ) {
                report(
                    MUTABLE_BARE_FIELD,
                    field,
                    "Bare field ${field.name()} must be marked final, or moved behind accessors if mutable"
                )
            }
        }
        if (!modifiers.isStatic()) {
            if (!fieldNamePattern.matches(field.name())) {
                report(
                    START_WITH_LOWER,
                    field,
                    "Non-static field ${field.name()} must be named using fooBar style"
                )
            }
        }
        if (internalNamePattern.matches(field.name())) {
            report(INTERNAL_FIELD, field, "Internal field ${field.name()} must not be exposed")
        }
        if (constantNamePattern.matches(field.name()) && field.isJava()) {
            if (!modifiers.isStatic() || !modifiers.isFinal()) {
                report(ALL_UPPER, field, "Constant ${field.name()} must be marked static final")
            }
        }
    }

    private fun checkSettingKeys(field: FieldItem) {
        val className = field.containingClass().qualifiedName()
        val modifiers = field.modifiers
        val type = field.type()

        if (
            modifiers.isFinal() &&
                modifiers.isStatic() &&
                type.isString() &&
                className in settingsKeyClasses
        ) {
            report(
                NO_SETTINGS_PROVIDER,
                field,
                "New setting keys are not allowed (Field: ${field.name()}); use getters/setters in relevant manager class"
            )
        }
    }

    private fun checkRegistrationMethods(cls: ClassItem, methods: Sequence<MethodItem>) {
        /** Make sure that there is a corresponding method */
        fun ensureMatched(
            cls: ClassItem,
            methods: Sequence<MethodItem>,
            method: MethodItem,
            name: String
        ) {
            if (method.superMethods().isNotEmpty()) return // Do not report for override methods
            for (candidate in methods) {
                if (candidate.name() == name) {
                    return
                }
            }

            report(
                PAIRED_REGISTRATION,
                method,
                "Found ${method.name()} but not $name in ${cls.qualifiedName()}"
            )
        }

        for (method in methods) {
            val name = method.name()
            // the python version looks for any substring, but that includes a lot of other stuff,
            // like plurals
            if (name.endsWith("Callback") || name.endsWith("Listener")) {
                if (name.startsWith("register")) {
                    val unregister = "unregister" + name.substring(8) // "register".length
                    ensureMatched(cls, methods, method, unregister)
                } else if (name.startsWith("unregister")) {
                    val register = "register" + name.substring(10) // "unregister".length
                    ensureMatched(cls, methods, method, register)
                } else if (name.startsWith("add")) {
                    val remove = "remove" + name.substring(3) // "add".length
                    ensureMatched(cls, methods, method, remove)
                } else if (name.startsWith("remove") && !name.startsWith("removeAll")) {
                    val add = "add" + name.substring(6) // "remove".length
                    ensureMatched(cls, methods, method, add)
                }
            }
        }
    }

    private fun checkSynchronized(method: MethodItem) {

        /**
         * Report an error.
         *
         * @param synchronizedStatementLocation an optional [FileLocation] of the synchronized
         *   statement that is the root of the problem.
         */
        fun reportError(synchronizedStatementLocation: FileLocation? = null) {
            val message = StringBuilder("Internal locks must not be exposed")
            if (synchronizedStatementLocation != null) {
                message.append(" (synchronizing on this or class is still externally observable)")
            }
            message.append(": ")
            message.append(method.describe())
            val location = synchronizedStatementLocation ?: FileLocation.UNKNOWN
            report(VISIBLY_SYNCHRONIZED, method, message.toString(), location)
        }

        if (method.modifiers.isSynchronized()) {
            // The synchronizing is being done implicitly bny the method so there is no more
            // specific location to provide.
            reportError()
        } else {
            // Find any visible synchronized statements in the method body.
            val synchronizedLocations = method.body.findVisiblySynchronizedLocations()

            for (location in synchronizedLocations) {
                // Report the location of the synchronized statement that is synchronizing on
                // `this` or the `class` object and causing the problem.
                reportError(location)
            }
        }
    }

    private fun checkIntentBuilder(method: MethodItem) {
        if (method.returnType().toTypeString() == "android.content.Intent") {
            val name = method.name()
            if (name.startsWith("create") && name.endsWith("Intent")) {
                return
            }
            if (method.containingClass().simpleName() == "Intent") {
                return
            }

            report(
                INTENT_BUILDER_NAME,
                method,
                "Methods creating an Intent should be named `create<Foo>Intent()`, was `$name`"
            )
        }
    }

    private fun checkHelperClasses(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        fields: Sequence<FieldItem>
    ) {
        fun ensureFieldValue(fields: Sequence<FieldItem>, fieldName: String, fieldValue: String) {
            fields
                .firstOrNull { it.name() == fieldName }
                ?.let { field ->
                    if (field.constantValue?.asString() != fieldValue) {
                        report(
                            INTERFACE_CONSTANT,
                            field,
                            "Inconsistent interface constant; expected '$fieldValue'`"
                        )
                    }
                }
        }

        fun ensureContextNameSuffixes(cls: ClassItem, suffixes: List<String>) {
            // Check if the class name ends with any of the provided suffixes
            val hasMatchingSuffix = suffixes.any { suffix -> cls.simpleName().endsWith(suffix) }

            if (!hasMatchingSuffix) {
                // Build a user-friendly string of expected suffixes
                val expectedSuffixesString =
                    suffixes.joinToString(separator = "`, `", prefix = "`", postfix = "`")

                report(
                    CONTEXT_NAME_SUFFIX,
                    cls,
                    "Inconsistent class name; should end with one of [${expectedSuffixesString}], but was `${cls.simpleName()}`"
                )
            }
        }

        var testMethods = false

        when {
            cls.extends("android.app.Service") -> {
                testMethods = true
                ensureContextNameSuffixes(cls, arrayListOf("Service", "ServiceCompat"))
                ensureFieldValue(fields, "SERVICE_INTERFACE", cls.qualifiedName())
            }
            cls.extends("android.content.ContentProvider") -> {
                testMethods = true
                ensureContextNameSuffixes(cls, arrayListOf("Provider", "ProviderCompat"))
                ensureFieldValue(fields, "PROVIDER_INTERFACE", cls.qualifiedName())
            }
            cls.extends("android.content.BroadcastReceiver") -> {
                testMethods = true
                ensureContextNameSuffixes(cls, arrayListOf("Receiver", "ReceiverCompat"))
            }
            cls.extends("android.app.Activity") -> {
                testMethods = true
                ensureContextNameSuffixes(cls, arrayListOf("Activity", "ActivityCompat"))
            }
        }

        if (testMethods) {
            for (method in methods) {
                val modifiers = method.modifiers
                if (modifiers.isFinal() || modifiers.isStatic()) {
                    continue
                }
                val name = method.name()
                if (!onCallbackNamePattern.matches(name)) {
                    val message =
                        if (modifiers.isAbstract()) {
                            "Methods implemented by developers should follow the on<Something> style, was `$name`"
                        } else {
                            "If implemented by developer, should follow the on<Something> style; otherwise consider marking final"
                        }
                    report(ON_NAME_EXPECTED, method, message)
                }
            }
        }
    }

    /**
     * Check to see whether [builderClass] is following the builder pattern as defined in
     * http://go/android-api-guidelines#builders and if it is make sure that it is following the
     * guidelines correctly.
     */
    private fun checkBuilder(
        builderClass: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>,
        superClass: ClassItem?,
        interfaces: Sequence<TypeItem>,
    ) {
        // A class is considered to be following the builder pattern if and only if:
        // * Its name ends with "Builder".
        // * It directly extends `java.lang.Object` (implicitly or otherwise).
        // * It does not implement any interfaces.
        if (
            !builderClass.simpleName().endsWith("Builder") ||
                (superClass != null && !superClass.isJavaLangObject()) ||
                interfaces.any()
        ) {
            return
        }

        // Builders must be nested class of the class they are building.
        if (builderClass.isTopLevelClass()) {
            report(
                TOP_LEVEL_BUILDER,
                builderClass,
                "Builder should be defined as nested class: ${builderClass.qualifiedName()}"
            )
        }

        // They must be final classes.
        if (!builderClass.modifiers.isFinal()) {
            report(
                STATIC_FINAL_BUILDER,
                builderClass,
                "Builder must be final: ${builderClass.qualifiedName()}"
            )
        }

        // They must be static classes.
        if (!builderClass.modifiers.isStatic() && !builderClass.isTopLevelClass()) {
            report(
                STATIC_FINAL_BUILDER,
                builderClass,
                "Builder must be static: ${builderClass.qualifiedName()}"
            )
        }

        // Constructor arguments cannot be optional, i.e. nullable. Options properties must be set
        // using setter methods.
        for (constructor in constructors) {
            for (arg in constructor.parameters()) {
                if (arg.type().modifiers.isNullable) {
                    report(
                        OPTIONAL_BUILDER_CONSTRUCTOR_ARGUMENT,
                        arg,
                        "Builder constructor arguments must be mandatory (i.e. not @Nullable): ${arg.describe()}"
                    )
                }
            }
        }

        // Maps each setter to a list of potential getters that would satisfy it.
        val expectedGetters = mutableListOf<Pair<Item, Set<String>>>()
        var builtType: TypeItem? = null
        val builderType = builderClass.type()

        for (method in methods) {
            val name = method.name()
            if (name == "build") {
                // TODO(b/477255952): Provide stricter checking of the `build` methods.
                // * What is there are multiple matching methods? At the moment it just uses the
                //   last one it finds.
                builtType = method.type()
                continue
            } else if (name.startsWith("get") || name.startsWith("is")) {
                report(
                    GETTER_ON_BUILDER,
                    method,
                    "Getter should be on the built object, not the builder: ${method.describe()}"
                )
            } else if (
                name.startsWith("set") || name.startsWith("add") || name.startsWith("clear")
            ) {
                val returnType = method.returnType()

                // If the return type and builder type are not equal (after erasing to handle
                // type variables) then it is an error.
                if (returnType.asErasedType() != builderType.asErasedType()) {
                    report(
                        SETTER_RETURNS_THIS,
                        method,
                        "Methods must return the builder object (return type " +
                            "$builderType instead of $returnType): ${method.describe()}"
                    )
                }

                if (method.returnType().modifiers.isNullable) {
                    report(
                        SETTER_RETURNS_THIS,
                        method,
                        "Builder setter must be @NonNull: ${method.describe()}"
                    )
                }
                val isBool =
                    when (method.parameters().firstOrNull()?.type()?.toTypeString()) {
                        "boolean",
                        "java.lang.Boolean" -> true
                        else -> false
                    }
                val allowedGetters: Set<String>? =
                    if (isBool && name.startsWith("set")) {
                        val pattern =
                            goodBooleanGetterSetterPrefixes.match(
                                name,
                                GetterSetterPattern::setter
                            )!!
                        setOf("${pattern.getter}${name.removePrefix(pattern.setter)}")
                    } else {
                        when {
                                name.startsWith("set") -> listOf(name.removePrefix("set"))
                                name.startsWith("add") -> {
                                    val nameWithoutPrefix = name.removePrefix("add")
                                    when {
                                        name.endsWith("s") -> {
                                            // If the name ends with s, it may already be a plural.
                                            // If the
                                            // add method accepts a single value, it is called
                                            // addFoo() and
                                            // getFoos() is right. If an add method accepts a
                                            // collection, it
                                            // is called addFoos() and getFoos() is right. So we
                                            // allow both.
                                            listOf(nameWithoutPrefix, "${nameWithoutPrefix}es")
                                        }
                                        name.endsWith("sh") ||
                                            name.endsWith("ch") ||
                                            name.endsWith("x") ||
                                            name.endsWith("z") -> listOf("${nameWithoutPrefix}es")
                                        name.endsWith("y") &&
                                            name[name.length - 2] !in
                                                listOf('a', 'e', 'i', 'o', 'u') -> {
                                            listOf("${nameWithoutPrefix.removeSuffix("y")}ies")
                                        }
                                        else -> listOf("${nameWithoutPrefix}s")
                                    }
                                }
                                else -> null
                            }
                            ?.map { "get$it" }
                            ?.toSet()
                    }
                allowedGetters?.let { expectedGetters.add(method to it) }
            } else {
                report(
                    BUILDER_SET_STYLE,
                    method,
                    "Builder methods names should use setFoo() / addFoo() / clearFoo() style: ${method.describe()}"
                )
            }
        }
        if (builtType == null) {
            report(
                MISSING_BUILD_METHOD,
                builderClass,
                "${builderClass.qualifiedName()} does not declare a `build()` method, but builder classes are expected to"
            )
        }

        // TODO(b/477255952): Provide stricter checking of the `build` methods.
        // 1. What if the returned type is an array of classes? At the moment that is
        //    allowed and this will run the check for expected getters on the innermost component.
        // 2. What if the returned type is not a class? At the moment that is allowed but
        //    it is not clear if that makes sense.
        // 3. What is the built class cannot be resolved? At the moment that is allowed.
        // 4. Types that are allowed but do not resolve to a class, e.g. primitives, are ignored
        //    here.

        // Resolve the built type to a class, if possible.
        builtType?.builtClass()?.let { builtClass ->
            // Check to make sure it provides the expected getters.
            checkGettersOnBuiltClass(builtClass, expectedGetters)
        }
    }

    /** Get the [ClassItem] being built by a build method that returned this [TypeItem]. */
    private fun TypeItem.builtClass(): ClassItem? =
        when (this) {
            // The built type can either be a ClassTypeItem or a VariableTypeItem.
            is ClassOrVariableTypeItem -> asErasedClass(codebase)

            // TODO(b/477255952): This should probably be an error.
            // If it is an array then the built class is considered to be the innermost component.
            is ArrayTypeItem -> innermostComponentType().builtClass()

            // Otherwise, there is no built class.
            else -> null
        }

    /** Check that [builtClass] provides the [expectedGetters]. */
    private fun checkGettersOnBuiltClass(
        builtClass: ClassItem,
        expectedGetters: List<Pair<Item, Set<String>>>,
    ) {
        val builtMethods =
            builtClass
                .filteredMethods(filterReference, includeSuperClassMethods = true)
                .map { it.name() }
                .toSet()
        for ((setter, expectedGetterNames) in expectedGetters) {
            if (builtMethods.intersect(expectedGetterNames).isEmpty()) {
                val expectedGetterCalls = expectedGetterNames.map { "$it()" }
                val errorString =
                    if (expectedGetterCalls.size == 1) {
                        "${builtClass.qualifiedName()} does not declare a " +
                            "`${expectedGetterCalls.first()}` method matching " +
                            setter.describe()
                    } else {
                        "${builtClass.qualifiedName()} does not declare a getter method " +
                            "matching ${setter.describe()} (expected one of: " +
                            "$expectedGetterCalls)"
                    }
                report(MISSING_GETTER_MATCHING_BUILDER, setter, errorString)
            }
        }
    }

    private fun checkAidl(cls: ClassItem, superClass: ClassItem?, interfaces: Sequence<TypeItem>) {
        // Instead of ClassItem.implements() and .extends() which performs hierarchy
        // searches, here we only want to flag directly extending or implementing:
        val extendsBinder = superClass?.qualifiedName() == "android.os.Binder"
        val implementsIInterface = interfaces.any { it.toTypeString() == "android.os.IInterface" }
        if (extendsBinder || implementsIInterface) {
            val problem =
                if (extendsBinder) {
                    "extends Binder"
                } else {
                    "implements IInterface"
                }
            report(
                RAW_AIDL,
                cls,
                "Raw AIDL interfaces must not be exposed: ${cls.simpleName()} $problem"
            )
        }
    }

    private fun checkInternal(cls: ClassItem) {
        if (cls.qualifiedName().startsWith("com.android.")) {
            report(INTERNAL_CLASSES, cls, "Internal classes must not be exposed")
        }
    }

    /** Encapsulate a [packageItem] and its associated [rank]. */
    private class PackageRank private constructor(private val codebase: Codebase) :
        BaseTypeVisitor() {
        /**
         * The [PackageItem].
         *
         * Must only be read if [rank] is not [INVALID_RANK].
         */
        private lateinit var packageItem: PackageItem

        private var rank = INVALID_RANK

        /** Construct from a [ClassItem]. */
        constructor(classItem: ClassItem) : this(classItem.codebase) {
            // Extract the package item from the class and get its rank.
            packageItem = classItem.containingPackage()
            rank = packageItem.packageRank()
        }

        /** Construct from a [TypeItem]. */
        constructor(codebase: Codebase, type: TypeItem) : this(codebase) {
            // Visit the [type] searching for any [ClassTypeItem] references passing in this as the
            // visitor. This class's visitor methods will ensure that the rank and packageItem are
            // set the highest package rank for all the classes referenced in this type. If none are
            // found (or no packages have a valid rank) then it will leave this in an invalid state
            // so [TypeItem.packageRank] will discard it.
            type.accept(this)
        }

        override fun visitClassType(classType: ClassTypeItem) {
            val classItem = classType.resolveClass(codebase) ?: return
            val newPackageItem = classItem.containingPackage()
            val newRank = newPackageItem.packageRank()

            // Use the rank if it is higher than the existing one.
            if (newRank > rank) {
                packageItem = newPackageItem
                rank = newRank
            }
        }

        override fun visit(variableType: VariableTypeItem) {
            // Visit the lower type bound.
            variableType.asErasedType().accept(this)
        }

        /**
         * Get the package rank for this [PackageItem].
         *
         * Higher means the package is more restricted about what it can depend upon.
         */
        private fun PackageItem.packageRank() =
            when (qualifiedName()) {
                "android.service",
                "android.accessibilityservice",
                "android.inputmethodservice",
                "android.printservice",
                "android.appwidget",
                "android.webkit",
                "android.preference",
                "android.gesture",
                "android.print" -> 10
                "android.app" -> 20
                "android.widget" -> 30
                "android.view" -> 40
                "android.animation" -> 50
                "android.provider" -> 60
                "android.content",
                "android.graphics.drawable" -> 70
                "android.database" -> 80
                "android.text" -> 90
                "android.graphics" -> 100
                "android.os" -> 110
                "android.util" -> 120
                else -> INVALID_RANK
            }

        /** True if this is value. */
        fun valid() = rank != INVALID_RANK

        operator fun compareTo(other: PackageRank) = rank.compareTo(other.rank)

        override fun toString() = if (valid()) packageItem.toString() else "invalid"

        companion object {
            /** The invalid or unknown rank. */
            private const val INVALID_RANK = -1
        }
    }

    /** Get the [PackageRank] for this [ClassItem], returning `null` if it could not be found. */
    private fun ClassItem.packageRank() = PackageRank(this).takeIf { it.valid() }

    /** Get the [PackageRank] for this [TypeItem], returning `null` if it could not be found. */
    private fun TypeItem.packageRank() = PackageRank(codebase, this).takeIf { it.valid() }

    private fun checkLayering(
        cls: ClassItem,
        callables: Sequence<CallableItem>,
        fields: Sequence<FieldItem>
    ) {
        val classRank = cls.packageRank() ?: return

        for (field in fields) {
            val fieldTypeRank = field.type().packageRank() ?: continue
            if (fieldTypeRank < classRank) {
                report(
                    PACKAGE_LAYERING,
                    field,
                    "Field type `${field.type().toTypeString()}` violates package layering: nothing in `$classRank` should depend on `$fieldTypeRank`"
                )
            }
        }

        for (callable in callables) {
            val returnType = callable.returnType()
            val returnTypeRank = returnType.packageRank() ?: continue
            if (returnTypeRank < classRank) {
                report(
                    PACKAGE_LAYERING,
                    callable,
                    "Method return type `${returnType.toTypeString()}` violates package layering: nothing in `$classRank` should depend on `$returnTypeRank`"
                )
            }

            for (parameter in callable.parameters()) {
                val parameterTypeRank = parameter.type().packageRank() ?: continue
                if (parameterTypeRank < classRank) {
                    report(
                        PACKAGE_LAYERING,
                        parameter,
                        "Method parameter type `${parameter.type().toTypeString()}` violates package layering: nothing in `$classRank` should depend on `$parameterTypeRank`"
                    )
                }
            }
        }
    }

    private fun checkBooleans(methods: Sequence<MethodItem>) {
        /*
            Correct:

            void setVisible(boolean visible);
            boolean isVisible();

            void setHasTransientState(boolean hasTransientState);
            boolean hasTransientState();

            void setCanRecord(boolean canRecord);
            boolean canRecord();

            void setShouldFitWidth(boolean shouldFitWidth);
            boolean shouldFitWidth();

            void setWiFiRoamingSettingEnabled(boolean enabled)
            boolean isWiFiRoamingSettingEnabled()
        */

        fun isBooleanGetter(method: MethodItem): Boolean {
            val returnType = method.returnType()
            return method.parameters().isEmpty() &&
                returnType is PrimitiveTypeItem &&
                returnType.toTypeString() == "boolean"
        }

        fun isBooleanSetter(method: MethodItem): Boolean {
            return method.parameters().size == 1 &&
                method.parameters()[0].type().toTypeString() == "boolean"
        }

        /**
         * Searches the [methods] for one named [actual] that is a boolean getter if
         * [lookingForGetter] is true or a boolean setter if [lookingForGetter] is false. If one is
         * found, reports an error that the method should have been named [expected] instead of
         * [actual] to match the naming of [trigger].
         */
        fun errorIfExists(
            methods: Sequence<MethodItem>,
            trigger: String,
            expected: String,
            actual: String,
            lookingForGetter: Boolean,
        ) {
            for (method in methods) {
                if (
                    method.name() == actual &&
                        ((lookingForGetter && isBooleanGetter(method)) ||
                            (!lookingForGetter && isBooleanSetter(method)))
                ) {
                    report(
                        GETTER_SETTER_NAMES,
                        method,
                        "Symmetric method for `$trigger` must be named `$expected`; was `$actual`"
                    )
                }
            }
        }

        /**
         * Check the Kotlin property associated with the [getter] is well-named and has correctly
         * named accessors.
         */
        fun checkKotlinProperty(getter: MethodItem) {
            val propertyItem = getter.property ?: return
            val setter = propertyItem.setter

            // The property name rules are the same as the getter name rules.
            val pattern =
                goodBooleanGetterSetterPrefixes.match(
                    propertyItem.name(),
                    GetterSetterPattern::getter
                )
            if (pattern == null) {
                report(
                    GETTER_SETTER_NAMES,
                    propertyItem,
                    "Invalid name for boolean property `${propertyItem.name()}`. " +
                        "Should start with one of $goodBooleanPropertyPrefixes."
                )
                return
            }

            // The property starts with a good prefix, but could still also start with a bad prefix
            // (e.g. "isHas")
            val badPrefix =
                badBooleanGetterPrefixes.firstOrNull { propertyItem.name().startsWith(it) }
            if (badPrefix != null && badPrefix != pattern.getter) {
                report(
                    GETTER_SETTER_NAMES,
                    propertyItem,
                    "Invalid prefix `$badPrefix` for boolean property `${propertyItem.name()}`."
                )
                return
            }

            if (getter.name() != propertyItem.name()) {
                // Only properties beginning with "is" have the correct autogenerated getter name.
                // Others need to be set with @JvmName.
                report(
                    GETTER_SETTER_NAMES,
                    getter,
                    "Getter for boolean property `${propertyItem.name()}` is named `${getter.name()}` " +
                        "but should match the property name. Use `@get:JvmName` to rename."
                )
            }

            val target = propertyItem.name().substring(pattern.getter.length)
            val expectedSetter = "${pattern.setter}$target"
            if (setter != null && setter.name() != expectedSetter) {
                // If this happens, the setter name must have been incorrectly set with @set:JvmName
                report(
                    GETTER_SETTER_NAMES,
                    setter,
                    "Invalid name for boolean property setter `${setter.name()}`, should be `$expectedSetter`."
                )
            }
        }

        for (method in methods) {
            val name = method.name()
            if (isBooleanGetter(method)) {
                // Checks for Java and Kotlin getters are handled separately
                if (method.isKotlinProperty) {
                    checkKotlinProperty(method)
                } else {
                    val pattern =
                        goodBooleanGetterSetterPrefixes.match(name, GetterSetterPattern::getter)
                            ?: continue
                    val target = name.substring(pattern.getter.length)
                    val expectedSetter = "${pattern.setter}$target"

                    badBooleanSetterPrefixes.forEach {
                        val actualSetter = "${it}$target"
                        if (actualSetter != expectedSetter) {
                            errorIfExists(methods, name, expectedSetter, actualSetter, false)
                        }
                    }
                }
            } else if (isBooleanSetter(method)) {
                // Handled in the getter case (if the setter is part of the API, the getter also
                // has to be: https://youtrack.jetbrains.com/issue/KT-3110)
                if (method.isKotlinProperty) continue

                val pattern =
                    goodBooleanGetterSetterPrefixes.match(name, GetterSetterPattern::setter)
                        ?: continue
                val target = name.substring(pattern.setter.length)
                val expectedGetter = "${pattern.getter}$target"

                badBooleanGetterPrefixes.forEach {
                    val actualGetter = "${it}$target"
                    if (actualGetter != expectedGetter) {
                        errorIfExists(methods, name, expectedGetter, actualGetter, true)
                    }
                }
            }
        }
    }

    private fun checkCollections(type: TypeItem, item: Item) {
        // Primitive types cannot be collections.
        if (type is PrimitiveTypeItem) {
            return
        }

        // The bundle class is allowed to use ArrayList for legacy reasons. This is not an Android
        // API council guideline but is rather a pragmatic exception due to it using ArrayList
        // since creation.
        if (item.containingClass()?.qualifiedName() == "android.os.Bundle") {
            if (type is ClassTypeItem && type.qualifiedName == "java.util.ArrayList") {
                return
            }
        }

        // If the types uses one of the concrete collection classes then it is a problem.
        if (type.usesAnyClassIn(CONCRETE_COLLECTION_CLASSES)) {
            val where =
                when (item) {
                    is MethodItem -> "Return type"
                    is FieldItem -> "Field type"
                    else -> "Parameter type"
                }
            val erased = type.toErasedTypeString()
            report(
                CONCRETE_COLLECTION,
                item,
                "$where is concrete collection (`$erased`); must be higher-level interface"
            )
        }
    }

    private fun checkNullableCollections(type: TypeItem, item: Item) {
        val superItem: Item? =
            when (item) {
                is MethodItem -> item.findPredicateSuperMethod(filterReference)
                is ParameterItem ->
                    item
                        .possibleContainingMethod()
                        ?.findPredicateSuperMethod(filterReference)
                        ?.parameters()
                        ?.find { it.parameterIndex == item.parameterIndex }
                else -> null
            }
        val superType = superItem?.type()

        // Visit all subtypes of the type (paired with the types from the super method) to check for
        // nullable collections.
        type.accept(
            object : MultipleTypeVisitor() {
                override fun visitType(type: TypeItem, other: List<TypeItem>) {
                    // type is from the main type, other is from the supertype
                    checkNullableCollections(type, item, other.singleOrNull())
                }
            },
            listOfNotNull(superType)
        )
    }

    private fun checkNullableCollections(type: TypeItem, item: Item, superType: TypeItem?) {
        if (!type.isCollection()) return

        // Allow a nullable collection when it is present in the super type
        if (type.modifiers.isNullable && superType?.modifiers?.isNullable != true) {
            val where =
                when (item) {
                    is MethodItem -> "Return type of ${item.describe()}"
                    else -> "Type of ${item.describe()}"
                }

            val erased = type.toErasedTypeString()
            report(
                NULLABLE_COLLECTION,
                item,
                "$where uses a nullable collection (`$erased`); must be non-null"
            )
        }

        // Check the collection element type for nullness.
        val elementType = type.elementType() ?: return
        // Allow a nullable collection element when it is present in the super type
        val superElementType = superType?.elementType()
        if (elementType.modifiers.isNullable && superElementType?.modifiers?.isNullable != true) {
            report(
                NULLABLE_COLLECTION_ELEMENT,
                item,
                "Collection $type should not have a nullable element type ($elementType) in ${item.describe()}"
            )
        }
    }

    /**
     * For collection types (see [isCollection]), returns the element type. For maps and other
     * collections with multiple argument types, this returns the first argument type.
     */
    private fun TypeItem.elementType(): TypeItem? {
        return when (this) {
            is ArrayTypeItem -> componentType
            is ClassTypeItem -> arguments.firstOrNull()
            else -> null
        }
    }

    /**
     * Whether the class is a collection (implements the standard java or kotlin collection
     * interfaces) or a bundle.
     */
    private fun ClassItem.isCollection(): Boolean {
        return extendsOrImplements("java.util.Collection") ||
            extendsOrImplements("kotlin.collections.Collection") ||
            extendsOrImplements("java.util.Map") ||
            extendsOrImplements("kotlin.collections.Map") ||
            qualifiedName() == "android.os.Bundle" ||
            qualifiedName() == "android.os.PersistableBundle"
    }

    /**
     * Whether the type is a collection.
     *
     * To preserve legacy behavior, primitive arrays are not considered collections but other arrays
     * are (b/343748165).
     */
    private fun TypeItem.isCollection(): Boolean =
        when (this) {
            // Historically arrays are considered collections unless their innermost component type
            // is primitive.
            is ArrayTypeItem -> innermostComponentType() !is PrimitiveTypeItem
            // A class reference is a collection if the referenced ClassItem is a collection.
            is ClassTypeItem -> resolveClass(codebase)?.isCollection() == true
            // A variable type is a collection if its erased type is a collection.
            is VariableTypeItem -> asTypeParameter.asErasedType().isCollection()
            else -> false
        }

    private fun checkFlags(fields: Sequence<FieldItem>) {
        var known: MutableMap<String, Int>? = null
        var valueToFlag: MutableMap<Int?, String>? = null
        for (field in fields) {
            val name = field.name()
            val index = name.indexOf("FLAG_")
            if (index != -1) {
                val value = field.constantValue?.asInt() ?: continue
                val scope = name.substring(0, index)
                val prev = known?.get(scope) ?: 0
                if (known != null && (prev and value) != 0) {
                    val prevName = valueToFlag?.get(prev)
                    report(
                        OVERLAPPING_CONSTANTS,
                        field,
                        "Found overlapping flag constant values: `$name` with value $value (0x${Integer.toHexString(
                            value
                        )}) and overlapping flag value $prev (0x${Integer.toHexString(prev)}) from `$prevName`"
                    )
                }
                if (known == null) {
                    known = mutableMapOf()
                }
                known[scope] = value
                if (valueToFlag == null) {
                    valueToFlag = mutableMapOf()
                }
                valueToFlag[value] = name
            }
        }
    }

    private fun checkExceptions(callable: CallableItem, filterReference: FilterPredicate) {
        for (throwableType in callable.filteredThrowsTypes(filterReference)) {
            // Get the throwable class, which for a type parameter will be the lower bound. A
            // method that throws a type parameter is treated as if it throws its lower bound, so
            // it makes sense for this check to treat it as if it was replaced with its lower bound.
            val throwableClass = throwableType.asErasedClass(codebase) ?: continue
            if (isUncheckedException(throwableClass)) {
                report(
                    BANNED_THROW,
                    callable,
                    "Unchecked exception ${throwableType.toTypeString()} does not need to be listed in the method throws clause (only in documentation)"
                )
            } else if (throwableType is VariableTypeItem) {
                // Preserve legacy behavior where the following check did nothing for type
                // parameters as a type parameters qualifiedName(), which is just its name without
                // any package or containing class could never match a qualified exception name.
            } else {
                when (val qualifiedName = throwableClass.qualifiedName()) {
                    "java.lang.Exception",
                    "java.lang.Throwable",
                    "java.lang.Error" -> {
                        report(
                            GENERIC_EXCEPTION,
                            callable,
                            "Methods must not throw generic exceptions (`$qualifiedName`)"
                        )
                    }
                    "android.os.RemoteException" -> {
                        when (callable.containingClass().qualifiedName()) {
                            "android.content.ContentProviderClient",
                            "android.os.Binder",
                            "android.os.IBinder" -> {
                                // exceptions
                            }
                            else -> {
                                report(
                                    RETHROW_REMOTE_EXCEPTION,
                                    callable,
                                    "Methods calling system APIs should rethrow `RemoteException` as `RuntimeException` (but do not list it in the throws clause)"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Unchecked exceptions are subclasses of RuntimeException or Error. These are not checked by
     * the compiler, and it is against API guidelines to put them in the 'throws'. See
     * https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html
     */
    private fun isUncheckedException(exception: ClassItem): Boolean {
        val superNames = exception.allSuperClasses().map { it.qualifiedName() }
        return superNames.any { it == "java.lang.RuntimeException" || it == "java.lang.Error" }
    }

    private fun checkGoogle(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        fields: Sequence<FieldItem>
    ) {
        fun checkName(name: String, item: Item) {
            if (name.contains("Google", ignoreCase = true)) {
                report(MENTIONS_GOOGLE, item, "Must never reference Google (`$name`)")
            }
        }

        checkName(cls.simpleName(), cls)
        for (method in methods) {
            checkName(method.name(), method)
        }
        for (field in fields) {
            checkName(field.name(), field)
        }
    }

    /**
     * Check whether this [TypeItem] uses any of the [qualifiedClassNames] in any way.
     *
     * This will only check the [TypeParameterItem.typeBounds] of a
     * [VariableTypeItem.asTypeParameter] if specifically requested. That is because when the check
     * has to prevent any type being used it is better to check the [TypeParameterItem]s directly
     * rather than every use as the latter would produce a lot of noise.
     *
     * Care has to be taken when checking [TypeParameterItem]s as they can form cycles, e.g.
     * * in `Foo<T extends Foo<T>>` type parameter `T` depends on itself
     * * in `Foo<A extends Foo<A, B>, B extends Foo<A, B>>` both `A` and `B` depend on each other.
     */
    private fun TypeItem.usesAnyClassIn(
        qualifiedClassNames: Set<String>,
        checkVariableTypes: Boolean = false,
    ): Boolean =
        when (this) {
            is ArrayTypeItem ->
                componentType.usesAnyClassIn(qualifiedClassNames, checkVariableTypes)
            is ClassTypeItem ->
                qualifiedName in qualifiedClassNames ||
                    arguments.any { it.usesAnyClassIn(qualifiedClassNames, checkVariableTypes) }
            is WildcardTypeItem ->
                extendsBound?.usesAnyClassIn(qualifiedClassNames, checkVariableTypes) == true ||
                    superBound?.usesAnyClassIn(qualifiedClassNames, checkVariableTypes) == true
            // Make sure that there are no references to the class in the type parameter.
            is VariableTypeItem ->
                checkVariableTypes &&
                    asTypeParameter.typeBounds().any {
                        it.usesAnyClassIn(
                            qualifiedClassNames,
                            // Always set this to `true` to avoid getting trapped in a cycle.
                            checkVariableTypes = false,
                        )
                    }
            else -> false
        }

    private fun checkBitSet(type: TypeItem, item: Item) {
        if (type.usesAnyClassIn(HEAVY_BIT_SET_CLASSES)) {
            report(HEAVY_BIT_SET, item, "Type must not use heavy BitSet (${item.describe()})")
        }
    }

    private fun checkManager(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>
    ) {
        if (!cls.simpleName().endsWith("Manager")) {
            return
        }
        for (method in constructors) {
            method.modifiers.isPublic()
            method.modifiers.isPrivate()
            report(
                MANAGER_CONSTRUCTOR,
                method,
                "Managers must always be obtained from Context; no direct constructors"
            )
        }
        for (method in methods) {
            // A Manager class must not be returned, in any form so report an issue if the return
            // type contains any use of the Manager class name. That includes checking for variable
            // types.
            if (
                method
                    .returnType()
                    .usesAnyClassIn(
                        setOf(cls.qualifiedName()),
                        // Make sure to check that the manager is not returned through a type
                        // parameter.
                        checkVariableTypes = true,
                    )
            ) {
                report(
                    MANAGER_LOOKUP,
                    method,
                    "Managers must always be obtained from Context (`${method.name()}`)"
                )
            }
        }
    }

    private fun checkHasNullability(item: Item) {
        val itemType = item.type() ?: return
        val inherited =
            when (item) {
                is ParameterItem -> item.possibleContainingMethod()?.inheritedFromAncestor == true
                is InheritableItem -> item.inheritedFromAncestor
                else -> false
            }
        val superItems =
            when (item) {
                is ParameterItem ->
                    item.possibleContainingMethod()?.superMethods()?.mapNotNull {
                        it.parameters().find { param ->
                            item.parameterIndex == param.parameterIndex
                        }
                    } ?: emptyList()
                is MethodItem -> item.superMethods()
                else -> emptyList()
            }
        val superTypes = superItems.mapNotNull { it.type() }

        itemType.accept(
            object : MultipleTypeVisitor() {
                override fun visitType(type: TypeItem, other: List<TypeItem>) {
                    val isInner = itemType !== type
                    checkHasNullability(type, item, inherited, other, isInner)
                }
            },
            superTypes
        )
    }

    /**
     * Checks that the [type] from [item] has a nullability (unless it is [inherited]) and that the
     * nullability does not conflict with the nullability of the [supers], which are the
     * corresponding types from the [item]'s super methods.
     *
     * The issue reported for missing nullability is either [MISSING_NULLABILITY] or
     * [MISSING_INNER_NULLABILITY] depending on [isInner].
     */
    private fun checkHasNullability(
        type: TypeItem,
        item: Item,
        inherited: Boolean,
        supers: List<TypeItem>,
        isInner: Boolean,
    ) {
        if (type.modifiers.isPlatformNullability) {
            if (inherited) {
                return // Do not enforce nullability on inherited items (non-overridden)
            }
            if (type is VariableTypeItem) {
                // Generic types should have declarations of nullability set at the site of where
                // the type is set, so that for Foo<T>, T does not need to specify nullability, but
                // for Foo<Bar>, Bar does.
                return // Do not enforce nullability for generics
            }
            val where =
                when (item) {
                    is ParameterItem ->
                        "parameter `${item.name()}` in method `${item.parent()?.name()}`"
                    is FieldItem -> "field `${item.name()}` in class `${item.parent()}`"
                    is ConstructorItem -> "constructor `${item.name()}` return"
                    is MethodItem -> "method `${item.name()}` return"
                    else -> throw IllegalStateException("Unexpected item type: $item")
                }

            if (isInner) {
                report(
                    MISSING_INNER_NULLABILITY,
                    item,
                    "Missing nullability on inner type $type in $where"
                )
            } else {
                report(MISSING_NULLABILITY, item, "Missing nullability on $where")
            }
        } else {
            when (item) {
                is ParameterItem -> {
                    // We don't enforce this check on constructor params
                    if (item.containingCallable().isConstructor()) return
                    if (type.modifiers.isNonNull) {
                        // TODO (b/344859664): Skip warning for inner type
                        if (supers.anyTypeHasNullability(TypeNullability.PLATFORM) && !isInner) {
                            report(
                                INVALID_NULLABILITY_OVERRIDE,
                                item,
                                "Invalid nullability on type $type in parameter `${item.name()}` in method `${item.parent()?.name()}`. " +
                                    "Parameter in method override cannot use a non-null type when the corresponding type from the super method is platform-nullness."
                            )
                        } else if (supers.anyTypeHasNullability(TypeNullability.NULLABLE)) {
                            report(
                                INVALID_NULLABILITY_OVERRIDE,
                                item,
                                "Invalid nullability on type $type in parameter `${item.name()}` in method `${item.parent()?.name()}`. " +
                                    "Parameter in method override cannot use a non-null type when the corresponding type from the super method is nullable."
                            )
                        }
                    }
                }
                is CallableItem -> {
                    if (type.modifiers.isNullable) {
                        // TODO (b/344859664): Skip warning for inner type
                        if (supers.anyTypeHasNullability(TypeNullability.PLATFORM) && !isInner) {
                            report(
                                INVALID_NULLABILITY_OVERRIDE,
                                item,
                                "Invalid nullability on type $type in method `${item.name()}` return. " +
                                    "Method override cannot use a nullable type when the corresponding type from the super method is platform-nullness."
                            )
                        } else if (supers.anyTypeHasNullability(TypeNullability.NONNULL)) {
                            report(
                                INVALID_NULLABILITY_OVERRIDE,
                                item,
                                "Invalid nullability on type $type method `${item.name()}` return. " +
                                    "Method override cannot use a nullable type when the corresponding type from the super method is non-null."
                            )
                        }
                    }
                }
            }
        }
    }

    /** Checks if any of the [TypeItem]s in the list are non-variable types with [nullability]. */
    private fun List<TypeItem>.anyTypeHasNullability(nullability: TypeNullability): Boolean {
        return any { type ->
            // Variable types have been excluded from the check because of previous inconsistency
            // in modeling their nullability.
            type !is VariableTypeItem && type.modifiers.nullability == nullability
        }
    }

    private fun checkBoxed(type: TypeItem, item: Item) {
        fun isBoxType(qualifiedName: String): Boolean {
            return when (qualifiedName) {
                "java.lang.Number",
                "java.lang.Byte",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Short",
                "java.lang.Boolean" -> true
                else -> false
            }
        }

        // Only report issues with an actual type and not a generic type that extends Number as
        // there is nothing that can be done to avoid auto-boxing when using generic types.
        val qualifiedName =
            (type as? ClassTypeItem)?.resolveClass(codebase)?.qualifiedName() ?: return
        if (isBoxType(qualifiedName)) {
            report(AUTO_BOXING, item, "Must avoid boxed primitives (`$qualifiedName`)")
        }
    }

    private fun checkStaticUtils(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>,
        fields: Sequence<FieldItem>
    ) {
        if (!cls.isClass()) {
            return
        }

        val hasDefaultConstructor =
            cls.constructors().any { it.isImplicitConstructor() } ||
                run {
                    if (constructors.count() == 1) {
                        val constructor = constructors.first()
                        constructor.parameters().isEmpty() && constructor.modifiers.isPublic()
                    } else {
                        false
                    }
                }

        if (hasDefaultConstructor) {
            val qualifiedName = cls.qualifiedName()
            if (
                qualifiedName.startsWith("android.opengl.") ||
                    qualifiedName.startsWith("android.R.") ||
                    qualifiedName == "android.R"
            ) {
                return
            }

            if (methods.none() && fields.none()) {
                return
            }

            if (
                methods.none { !it.modifiers.isStatic() } &&
                    fields.none { !it.modifiers.isStatic() }
            ) {
                report(STATIC_UTILS, cls, "Fully-static utility classes must not have constructor")
            }
        }
    }

    private fun checkCallbackHandlers(
        cls: ClassItem,
        callables: Sequence<CallableItem>,
        superClass: ClassItem?
    ) {
        fun packageContainsSegment(packageName: String?, segment: String): Boolean {
            packageName ?: return false
            return (packageName.contains(segment) &&
                (packageName.contains(".$segment.") || packageName.endsWith(".$segment")))
        }

        fun skipPackage(packageName: String?): Boolean {
            packageName ?: return false
            for (segment in uiPackageParts) {
                if (packageContainsSegment(packageName, segment)) {
                    return true
                }
            }

            return false
        }

        // Ignore UI packages which assume main thread
        val classPackage = cls.containingPackage().qualifiedName()
        val extendsPackage = superClass?.containingPackage()?.qualifiedName()

        if (skipPackage(classPackage) || skipPackage(extendsPackage)) {
            return
        }

        // Ignore UI classes which assume main thread
        if (
            packageContainsSegment(classPackage, "app") ||
                packageContainsSegment(extendsPackage, "app")
        ) {
            val fullName = cls.fullName()
            if (
                fullName.contains("ActionBar") ||
                    fullName.contains("Dialog") ||
                    fullName.contains("Application") ||
                    fullName.contains("Activity") ||
                    fullName.contains("Fragment") ||
                    fullName.contains("Loader")
            ) {
                return
            }
        }
        if (
            packageContainsSegment(classPackage, "content") ||
                packageContainsSegment(extendsPackage, "content")
        ) {
            val fullName = cls.fullName()
            if (fullName.contains("Loader")) {
                return
            }
        }

        val found = mutableMapOf<String, CallableItem>()
        val byName = mutableMapOf<String, MutableList<CallableItem>>()
        for (callable in callables) {
            val name = callable.name()
            if (name.startsWith("unregister")) {
                continue
            }
            if (name.startsWith("remove")) {
                continue
            }
            if (name.startsWith("on") && onCallbackNamePattern.matches(name)) {
                continue
            }

            val list =
                byName[name]
                    ?: run {
                        val new = mutableListOf<CallableItem>()
                        byName[name] = new
                        new
                    }
            list.add(callable)

            for (parameter in callable.parameters()) {
                val type = parameter.type().toTypeString()
                if (
                    type.endsWith("Listener") ||
                        type.endsWith("Callback") ||
                        type.endsWith("Callbacks")
                ) {
                    found[name] = callable
                }
            }
        }

        for (f in found.values) {
            var takesExec = false

            // TODO: apilint computed takes_handler but did not use it; should we add more checks or
            // conditions?
            // var takesHandler = false

            val name = f.name()
            for (method in byName[name]!!) {
                // if (method.parameters().any { it.type().toTypeString() == "android.os.Handler" })
                // {
                //    takesHandler = true
                // }
                if (
                    method.parameters().any {
                        it.type().toTypeString() == "java.util.concurrent.Executor"
                    }
                ) {
                    takesExec = true
                }
            }
            if (!takesExec) {
                report(
                    EXECUTOR_REGISTRATION,
                    f,
                    "Registration methods should have overload that accepts delivery Executor: `$name`"
                )
            }
        }
    }

    private fun checkContextFirst(callable: CallableItem) {
        val parameters = callable.parameters()
        // The first parameter for a Kotlin extension method is the receiver
        val effectivelyFirstParameterPosition =
            if (callable is MethodItem && callable.isExtensionMethod()) 1 else 0
        val effectivelySecondParameterPosition = effectivelyFirstParameterPosition + 1
        if (parameters.size <= effectivelySecondParameterPosition) return
        val firstParameterTypeString =
            parameters[effectivelyFirstParameterPosition].type().toTypeString()
        if (firstParameterTypeString != "android.content.Context") {
            for (i in effectivelySecondParameterPosition until parameters.size) {
                val p = parameters[i]
                if (p.type().toTypeString() == "android.content.Context") {
                    report(
                        CONTEXT_FIRST,
                        p,
                        "Context is distinct, so it must be the first argument (method `${callable.name()}`)"
                    )
                }
            }
        }
        if (firstParameterTypeString != "android.content.ContentResolver") {
            for (i in effectivelySecondParameterPosition until parameters.size) {
                val p = parameters[i]
                if (p.type().toTypeString() == "android.content.ContentResolver") {
                    report(
                        CONTEXT_FIRST,
                        p,
                        "ContentResolver is distinct, so it must be the first argument (method `${callable.name()}`)"
                    )
                }
            }
        }
    }

    private fun checkListenerLast(callable: CallableItem) {
        val name = callable.name()
        if (name.contains("Listener") || name.contains("Callback")) {
            return
        }

        // Suspend functions add a synthetic `Continuation` parameter at the end - this is invisible
        // to Kotlin callers so just ignore it.
        val parameters =
            if (callable.modifiers.isSuspend()) {
                callable.parameters().dropLast(1)
            } else {
                callable.parameters()
            }
        if (parameters.size > 1) {
            var found = false
            for (parameter in parameters) {
                val type = parameter.type().toTypeString()
                if (
                    type.endsWith("Callback") ||
                        type.endsWith("Callbacks") ||
                        type.endsWith("Listener")
                ) {
                    found = true
                } else if (found) {
                    report(
                        LISTENER_LAST,
                        parameter,
                        "Listeners should always be at end of argument list (method `${callable.name()}`)"
                    )
                }
            }
        }
    }

    private fun checkResourceNames(cls: ClassItem, fields: Sequence<FieldItem>) {
        if (!cls.qualifiedName().startsWith("android.R.")) {
            return
        }

        val resourceType = ResourceType.fromClassName(cls.simpleName()) ?: return
        when (resourceType) {
            ANIM,
            ANIMATOR,
            COLOR,
            DIMEN,
            DRAWABLE,
            FONT,
            INTERPOLATOR,
            LAYOUT,
            MENU,
            MIPMAP,
            NAVIGATION,
            PLURALS,
            RAW,
            STRING,
            TRANSITION,
            XML -> {
                // Resources defined by files are foo_bar_baz
                // Note: it's surprising that dimen, plurals and string are in this list since
                // they are value resources, not file resources, but keeping api lint compatibility
                // for now.

                for (field in fields) {
                    val name = field.name()
                    if (name.startsWith("config_")) {
                        if (!configFieldPattern.matches(name)) {
                            report(
                                CONFIG_FIELD_NAME,
                                field,
                                "Expected config name to be in the `config_fooBarBaz` style, was `$name`"
                            )
                        }
                        continue
                    }
                    if (!resourceFileFieldPattern.matches(name)) {
                        report(
                            RESOURCE_FIELD_NAME,
                            field,
                            "Expected resource name in `${cls.qualifiedName()}` to be in the `foo_bar_baz` style, was `$name`"
                        )
                    }
                }
            }
            ARRAY,
            ATTR,
            BOOL,
            FRACTION,
            ID,
            INTEGER -> {
                // Resources defined inside files are fooBarBaz
                for (field in fields) {
                    val name = field.name()
                    if (name.startsWith("config_") && configFieldPattern.matches(name)) {
                        continue
                    }
                    if (name.startsWith("layout_") && layoutFieldPattern.matches(name)) {
                        continue
                    }
                    if (name.startsWith("state_") && stateFieldPattern.matches(name)) {
                        continue
                    }
                    if (resourceValueFieldPattern.matches(name)) {
                        continue
                    }
                    report(
                        RESOURCE_VALUE_FIELD_NAME,
                        field,
                        "Expected resource name in `${cls.qualifiedName()}` to be in the `fooBarBaz` style, was `$name`"
                    )
                }
            }
            STYLE -> {
                for (field in fields) {
                    val name = field.name()
                    if (!styleFieldPattern.matches(name)) {
                        report(
                            RESOURCE_STYLE_FIELD_NAME,
                            field,
                            "Expected resource name in `${cls.qualifiedName()}` to be in the `FooBar_Baz` style, was `$name`"
                        )
                    }
                }
            }
            STYLEABLE, // appears as R class but name check is implicitly done as part of style
            // class check
            // DECLARE_STYLEABLE,
            STYLE_ITEM,
            PUBLIC,
            SAMPLE_DATA,
            OVERLAYABLE,
            MACRO,
            AAPT -> {
                // no-op; these are resource "types" in XML but not present as R classes
                // Listed here explicitly to force compiler error as new resource types
                // are added.
            }
        }
    }

    private fun checkFiles(callables: Sequence<CallableItem>) {
        var hasFile: MutableSet<CallableItem>? = null
        var hasStream: MutableSet<String>? = null
        for (callable in callables) {
            for (parameter in callable.parameters()) {
                when (parameter.type().toTypeString()) {
                    "java.io.File" -> {
                        val set =
                            hasFile
                                ?: run {
                                    val new = mutableSetOf<CallableItem>()
                                    hasFile = new
                                    new
                                }
                        set.add(callable)
                    }
                    "java.io.FileDescriptor",
                    "android.os.ParcelFileDescriptor",
                    "java.io.InputStream",
                    "java.io.OutputStream" -> {
                        val set =
                            hasStream
                                ?: run {
                                    val new = mutableSetOf<String>()
                                    hasStream = new
                                    new
                                }
                        set.add(callable.name())
                    }
                }
            }
        }
        val files = hasFile
        if (files != null) {
            val streams = hasStream
            for (method in files) {
                if (streams == null || !streams.contains(method.name())) {
                    report(
                        STREAM_FILES,
                        method,
                        "Methods accepting `File` should also accept `FileDescriptor` or streams: ${method.describe()}"
                    )
                }
            }
        }
    }

    private fun checkManagerList(cls: ClassItem, methods: Sequence<MethodItem>) {
        if (!cls.simpleName().endsWith("Manager")) {
            return
        }
        for (method in methods) {
            val returnType = method.returnType()
            if (returnType is PrimitiveTypeItem) {
                return
            }
            val type = returnType.toTypeString()
            if (type.startsWith("android.") && returnType is ArrayTypeItem) {
                report(
                    PARCELABLE_LIST,
                    method,
                    "Methods should return `List<? extends Parcelable>` instead of `Parcelable[]` to support `ParceledListSlice` under the hood: ${method.describe()}"
                )
            }
        }
    }

    private fun checkAbstractInner(cls: ClassItem) {
        if (
            !cls.isTopLevelClass() &&
                cls.isClass() &&
                cls.modifiers.isAbstract() &&
                !cls.modifiers.isStatic()
        ) {
            report(
                ABSTRACT_INNER,
                cls,
                "Abstract inner classes should be static to improve testability: ${cls.describe()}"
            )
        }
    }

    private fun checkError(cls: ClassItem, superClass: ClassItem?) {
        superClass ?: return
        if (superClass.simpleName().endsWith("Error")) {
            report(
                EXTENDS_ERROR,
                cls,
                "Trouble must be reported through an `Exception`, not an `Error` (`${cls.simpleName()}` extends `${superClass.simpleName()}`)"
            )
        }
        if (
            superClass.simpleName().endsWith("Exception") && !cls.simpleName().endsWith("Exception")
        ) {
            report(
                EXCEPTION_NAME,
                cls,
                "Exceptions must be named `FooException`, was `${cls.simpleName()}`"
            )
        }
    }

    private fun checkUnits(method: MethodItem) {
        val returnType = method.returnType()
        var type = returnType.toTypeString()
        val name = method.name()
        if (type == "int" || type == "long" || type == "short") {
            if (badUnits.any { name.endsWith(it.key) }) {
                val typeIsTypeDef =
                    method.modifiers.hasAnnotation { annotation ->
                        val annotationClass = annotation.resolve() ?: return@hasAnnotation false
                        annotationClass.modifiers.hasAnnotation(AnnotationItem::isTypeDefAnnotation)
                    }
                if (!typeIsTypeDef) {
                    val badUnit = badUnits.keys.find { name.endsWith(it) }
                    val value = badUnits[badUnit]
                    report(
                        METHOD_NAME_UNITS,
                        method,
                        "Expected method name units to be `$value`, was `$badUnit` in `$name`"
                    )
                }
            }
        } else if (type == "void") {
            if (method.parameters().size != 1) {
                return
            }
            type = method.parameters()[0].type().toTypeString()
        }
        if (name.endsWith("Fraction") && (type == "int" || type == "long" || type == "short")) {
            report(FRACTION_FLOAT, method, "Fractions must use floats, was `$type` in `$name`")
        } else if (name.endsWith("Percentage") && (type == "float" || type == "double")) {
            report(PERCENTAGE_INT, method, "Percentage must use ints, was `$type` in `$name`")
        }
    }

    private fun checkCloseable(cls: ClassItem, methods: Sequence<MethodItem>) {
        // AutoCloseable has been added in API 19, so libraries with minSdkVersion <19 cannot use
        // it. If the version
        // is not set, then keep the check enabled.
        val minSdkVersion = manifest.getMinSdkVersion()
        if (minSdkVersion is SetMinSdkVersion && minSdkVersion.value < 19) {
            return
        }

        val foundMethods =
            methods.filter { method ->
                when (method.name()) {
                    "close",
                    "release",
                    "destroy",
                    "finish",
                    "finalize",
                    "disconnect",
                    "shutdown",
                    "stop",
                    "free",
                    "quit" -> true
                    else -> false
                }
            }
        if (
            foundMethods.iterator().hasNext() && !cls.implements("java.lang.AutoCloseable")
        ) { // includes java.io.Closeable
            val foundMethodsDescriptions =
                foundMethods.joinToString { method -> "${method.name()}()" }
            report(
                NOT_CLOSEABLE,
                cls,
                "Classes that release resources ($foundMethodsDescriptions) should implement AutoCloseable and CloseGuard: ${cls.describe()}"
            )
        }
    }

    private fun checkNotKotlinOperator(methods: Sequence<MethodItem>) {
        fun flagKotlinOperator(method: MethodItem, message: String) {
            if (method.isKotlin()) {
                report(
                    KOTLIN_OPERATOR,
                    method,
                    "Note that adding the `operator` keyword would allow calling this method using operator syntax"
                )
            } else {
                report(
                    KOTLIN_OPERATOR,
                    method,
                    "$message (this is usually desirable; just make sure it makes sense for this type of object)"
                )
            }
        }

        for (method in methods) {
            if (
                method.modifiers.isStatic() ||
                    method.modifiers.isOperator() ||
                    method.superMethods().isNotEmpty()
            ) {
                continue
            }
            when (val name = method.name()) {
                // https://kotlinlang.org/docs/reference/operator-overloading.html#unary-prefix-operators
                "unaryPlus",
                "unaryMinus",
                "not" -> {
                    if (method.parameters().isEmpty()) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked as a unary operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#increments-and-decrements
                "inc",
                "dec" -> {
                    if (
                        method.parameters().isEmpty() &&
                            method.returnType().toTypeString() != "void"
                    ) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked as a pre/postfix inc/decrement operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#arithmetic
                "plus",
                "minus",
                "times",
                "div",
                "rem",
                "mod",
                "rangeTo" -> {
                    if (method.parameters().size == 1) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked as a binary operator from Kotlin: `$name`"
                        )
                    }
                    val assignName = name + "Assign"

                    if (
                        methods.any {
                            it.name() == assignName &&
                                it.parameters().size == 1 &&
                                it.returnType().toTypeString() == "void"
                        }
                    ) {
                        report(
                            UNIQUE_KOTLIN_OPERATOR,
                            method,
                            "Only one of `$name` and `${name}Assign` methods should be present for Kotlin"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#in
                "contains" -> {
                    if (
                        method.parameters().size == 1 &&
                            method.returnType().toTypeString() == "boolean"
                    ) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked as a \"in\" operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#indexed
                "get" -> {
                    if (method.parameters().isNotEmpty()) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked with an indexing operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#indexed
                "set" -> {
                    if (method.parameters().size > 1) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked with an indexing operator from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#invoke
                "invoke" -> {
                    if (method.parameters().size > 1) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked with function call syntax from Kotlin: `$name`"
                        )
                    }
                }
                // https://kotlinlang.org/docs/reference/operator-overloading.html#assignments
                "plusAssign",
                "minusAssign",
                "timesAssign",
                "divAssign",
                "remAssign",
                "modAssign" -> {
                    if (
                        method.parameters().size == 1 &&
                            method.returnType().toTypeString() == "void"
                    ) {
                        flagKotlinOperator(
                            method,
                            "Method can be invoked as a compound assignment operator from Kotlin: `$name`"
                        )
                    }
                }
            }
        }
    }

    private fun checkCollectionsOverArrays(type: TypeItem, typeString: String, item: Item) {
        // This check only applies to array types.
        if (type !is ArrayTypeItem) {
            return
        }

        // Vararg parameters have to use arrays.
        if (item is ParameterItem && item.isVarArgs()) {
            return
        }

        // Annotation classes cannot use collections and have to use arrays.
        if (item is MethodItem && item.containingClass().isAnnotationType()) {
            return
        }

        // Arrays can be used for APIs that only target Kotlin
        // (go/android-api-guidelines#exception-for-kotlin)
        if (item.targetLanguages == TargetLanguageSet.KOTLIN_ONLY) return

        // Arrays of some component types are allowed.
        val componentType = type.componentType
        when (componentType) {
            // Primitive arrays are allowed. (go/android-api-guidelines#exception-for-primitives)
            is PrimitiveTypeItem -> return

            // String arrays are also allowed. They are not specifically allowed in the API
            // guidelines but there are hundreds of uses in the existing APIs so disallowing would
            // cause issues.
            is ClassTypeItem -> {
                if (componentType.qualifiedName == JAVA_LANG_STRING) return
            }
        }

        val action =
            when (item) {
                is MethodItem -> "Method should return"
                is FieldItem -> "Field should be"
                is ParameterItem -> "Method parameter should be"
                else -> error("internal error: should never be called for $item")
            }
        val component = componentType.toCanonicalTypeString()
        report(
            ARRAY_RETURN,
            item,
            "$action Collection<$component> (or subclass) instead of raw array; was `$typeString`"
        )
    }

    private fun checkUserHandle(cls: ClassItem, methods: Sequence<MethodItem>) {
        val qualifiedName = cls.qualifiedName()
        if (
            qualifiedName == "android.content.pm.LauncherApps" ||
                qualifiedName == "android.os.UserHandle" ||
                qualifiedName == "android.os.UserManager"
        ) {
            return
        }

        for (method in methods) {
            val parameters = method.parameters()
            if (parameters.isEmpty()) {
                continue
            }
            val name = method.name()
            if (name.startsWith("on") && onCallbackNamePattern.matches(name)) {
                continue
            }
            val hasArg = parameters.any { it.type().toTypeString() == "android.os.UserHandle" }
            if (!hasArg) {
                continue
            }
            if (qualifiedName.endsWith("Manager")) {
                report(
                    USER_HANDLE,
                    method,
                    "When a method overload is needed to target a specific " +
                        "UserHandle, callers should be directed to use " +
                        "Context.createPackageContextAsUser() and re-obtain the relevant " +
                        "Manager, and no new API should be added"
                )
            } else if (!(name.endsWith("AsUser") || name.endsWith("ForUser"))) {
                report(
                    USER_HANDLE_NAME,
                    method,
                    "Method taking UserHandle should be named `doFooAsUser` or `queryFooForUser`, was `$name`"
                )
            }
        }
    }

    private fun checkParams(cls: ClassItem) {
        val qualifiedName = cls.qualifiedName()
        for (suffix in badParameterClassNames) {
            if (
                qualifiedName.endsWith(suffix) &&
                    !((qualifiedName.endsWith("Params") ||
                        qualifiedName == "android.app.ActivityOptions" ||
                        qualifiedName == "android.app.BroadcastOptions" ||
                        qualifiedName == "android.os.Bundle" ||
                        qualifiedName == "android.os.BaseBundle" ||
                        qualifiedName == "android.os.PersistableBundle"))
            ) {
                report(
                    USER_HANDLE_NAME,
                    cls,
                    "Classes holding a set of parameters should be called `FooParams`, was `${cls.simpleName()}`"
                )
            }
        }
    }

    private fun checkServices(field: FieldItem) {
        val type = field.type()
        if (
            !type.isString() ||
                !field.modifiers.isFinal() ||
                !field.modifiers.isStatic() ||
                field.containingClass().qualifiedName() != "android.content.Context"
        ) {
            return
        }
        val name = field.name()
        val endsWithService = name.endsWith("_SERVICE")
        val value = field.constantValue?.asString()

        if (value == null) {
            val mustEndInService =
                if (!endsWithService) " and its name must end with `_SERVICE`" else ""

            report(
                SERVICE_NAME,
                field,
                "Non-constant service constant `$name`. Must be static," +
                    " final and initialized with a String literal$mustEndInService."
            )
            return
        }

        if (name.endsWith("_MANAGER_SERVICE")) {
            report(
                SERVICE_NAME,
                field,
                "Inconsistent service constant name; expected " +
                    "`${name.removeSuffix("_MANAGER_SERVICE")}_SERVICE`, was `$name`"
            )
        } else if (endsWithService) {
            val service = name.substring(0, name.length - "_SERVICE".length).lowercase(Locale.US)
            if (service != value) {
                report(
                    SERVICE_NAME,
                    field,
                    "Inconsistent service value; expected `$service`, was `$value` (Note: Do not" +
                        " change the name of already released services, which will break tools" +
                        " using `adb shell dumpsys`." +
                        " Instead add `@SuppressLint(\"${SERVICE_NAME.name}\"))`"
                )
            }
        } else {
            val valueUpper = value.uppercase(Locale.US)
            report(
                SERVICE_NAME,
                field,
                "Inconsistent service constant name;" +
                    " expected `${valueUpper}_SERVICE`, was `$name`"
            )
        }
    }

    private fun checkTense(method: MethodItem) {
        val name = method.name()
        if (name.endsWith("Enable")) {
            if (method.containingClass().qualifiedName().startsWith("android.opengl")) {
                return
            }
            report(
                METHOD_NAME_TENSE,
                method,
                "Unexpected tense; probably meant `enabled`, was `$name`"
            )
        }
    }

    private fun checkIcu(type: TypeItem, typeString: String, item: Item) {
        if (type is PrimitiveTypeItem) {
            return
        }
        // ICU types have been added in API 24, so libraries with minSdkVersion <24 cannot use them.
        // If the version is not set, then keep the check enabled.
        val minSdkVersion = manifest.getMinSdkVersion()
        if (minSdkVersion is SetMinSdkVersion && minSdkVersion.value < 24) {
            return
        }
        val better =
            when (typeString) {
                "java.util.Calendar" -> "android.icu.util.Calendar"
                "java.util.GregorianCalendar" -> "android.icu.util.GregorianCalendar"
                "java.text.BreakIterator" -> "android.icu.text.BreakIterator"
                "java.text.Collator" -> "android.icu.text.Collator"
                "java.text.DecimalFormatSymbols" -> "android.icu.text.DecimalFormatSymbols"
                "java.text.NumberFormat" -> "android.icu.text.NumberFormat"
                "java.text.DateFormatSymbols" -> "android.icu.text.DateFormatSymbols"
                "java.text.DateFormat" -> "android.icu.text.DateFormat"
                "java.text.SimpleDateFormat" -> "android.icu.text.SimpleDateFormat"
                "java.text.MessageFormat" -> "android.icu.text.MessageFormat"
                "java.text.DecimalFormat" -> "android.icu.text.DecimalFormat"
                else -> return
            }
        report(
            USE_ICU,
            item,
            "Type `$typeString` should be replaced with richer ICU type `$better`"
        )
    }

    private fun checkClone(method: MethodItem) {
        if (method.name() == "clone" && method.parameters().isEmpty()) {
            report(
                NO_CLONE,
                method,
                "Provide an explicit copy constructor instead of implementing `clone()`"
            )
        }
    }

    private fun checkPfd(type: String, item: Item) {
        if (
            item.containingClass()?.qualifiedName() in lowLevelFileClassNames ||
                isServiceDumpMethod(item)
        ) {
            return
        }

        if (type == "java.io.FileDescriptor") {
            report(
                USE_PARCEL_FILE_DESCRIPTOR,
                item,
                "Must use ParcelFileDescriptor instead of FileDescriptor in ${item.describe()}"
            )
        } else if (type == "int" && item is MethodItem) {
            val name = item.name()
            if (
                name.contains("Fd") ||
                    name.contains("FD") ||
                    name.contains("FileDescriptor", ignoreCase = true)
            ) {
                report(
                    USE_PARCEL_FILE_DESCRIPTOR,
                    item,
                    "Must use ParcelFileDescriptor instead of FileDescriptor in ${item.describe()}"
                )
            }
        }
    }

    private fun checkNumbers(type: String, item: Item) {
        if (type == "short" || type == "byte") {
            report(
                NO_BYTE_OR_SHORT,
                item,
                "Should avoid odd sized primitives; use `int` instead of `$type` in ${item.describe()}"
            )
        }
    }

    private fun checkSingleton(
        cls: ClassItem,
        methods: Sequence<MethodItem>,
        constructors: Sequence<ConstructorItem>
    ) {
        if (constructors.none()) {
            return
        }
        if (
            methods.any {
                it.name().startsWith("get") &&
                    it.name().endsWith("Instance") &&
                    it.modifiers.isStatic()
            }
        ) {
            for (constructor in constructors) {
                report(
                    SINGLETON_CONSTRUCTOR,
                    constructor,
                    "Singleton classes should use `getInstance()` methods: `${cls.simpleName()}`"
                )
            }
        }
    }

    private fun checkExtends(cls: ClassItem) {
        // Call cls.superClass().extends() instead of cls.extends() since extends returns true for
        // self
        val superCls = cls.superClass()
        if (superCls != null) {
            if (superCls.extends("android.os.AsyncTask")) {
                report(
                    FORBIDDEN_SUPER_CLASS,
                    cls,
                    "${cls.simpleName()} should not extend `AsyncTask`. AsyncTask is an implementation detail. Expose a listener or, in androidx, a `ListenableFuture` API instead"
                )
            }
            if (superCls.extends("android.app.Activity")) {
                report(
                    FORBIDDEN_SUPER_CLASS,
                    cls,
                    "${cls.simpleName()} should not extend `Activity`. Activity subclasses are impossible to compose. Expose a composable API instead."
                )
            }
        }
        badFutureTypes
            .firstOrNull { cls.extendsOrImplements(it) }
            ?.let {
                // The `badFutureTypes` is a mixture of classes and interfaces. So, when selecting
                // the verb it is necessary to use `extend` if this class is an interface or a class
                // extending another class, and `implement` otherwise.
                val extendOrImplement =
                    if (cls.isInterface() || cls.extends(it)) "extend" else "implement"
                report(
                    BAD_FUTURE,
                    cls,
                    "${cls.simpleName()} should not $extendOrImplement `$it`." +
                        " In AndroidX, use (but do not extend) ListenableFuture. In platform, use a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal`."
                )
            }
    }

    private fun checkTypedef(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            cls.modifiers.findAnnotation(AnnotationItem::isTypeDefAnnotation)?.let {
                report(
                    PUBLIC_TYPEDEF,
                    cls,
                    "Don't expose ${AnnotationItem.simpleName(it)}: ${cls.simpleName()} must be hidden."
                )
            }
        }
    }

    private fun checkUri(typeString: String, item: Item) {
        badUriTypes
            .firstOrNull { typeString.contains(it) }
            ?.let {
                report(ANDROID_URI, item, "Use android.net.Uri instead of $it (${item.describe()})")
            }
    }

    private fun checkFutures(typeString: String, item: Item) {
        badFutureTypes
            .firstOrNull { typeString.contains(it) }
            ?.let {
                report(
                    BAD_FUTURE,
                    item,
                    "Use ListenableFuture (library), " +
                        "or a combination of OutcomeReceiver<R,E>, Executor, and CancellationSignal (platform) instead of $it (${item.describe()})"
                )
            }
    }

    private fun checkMethodSuffixListenableFutureReturn(method: MethodItem) {
        val typeString = method.returnType().toTypeString()
        if (typeString.contains(listenableFuture) && !method.name().endsWith("Async")) {
            report(
                ASYNC_SUFFIX_FUTURE,
                method,
                "Methods returning $listenableFuture should have a suffix *Async to " +
                    "reserve unmodified name for a suspend function"
            )
        }
    }

    /**
     * Make sure that any parameters with default values (in Kotlin) come after all required,
     * non-trailing-lambda parameters.
     */
    private fun checkParameterOrder(callable: CallableItem) {
        // Ignore Java
        if (!callable.isKotlin()) {
            return
        }
        // Suspend functions add a synthetic `Continuation` parameter at the end - this is invisible
        // to Kotlin callers so just ignore it.
        val parameters =
            if (callable.modifiers.isSuspend()) {
                callable.parameters().dropLast(1)
            } else {
                callable.parameters()
            }
        val (optionalParameters, requiredParameters) = parameters.partition { it.hasDefaultValue() }
        if (requiredParameters.isEmpty() || optionalParameters.isEmpty()) return
        val lastRequiredParameter = requiredParameters.last()
        val hasTrailingLambda =
            lastRequiredParameter.parameterIndex == parameters.lastIndex &&
                lastRequiredParameter.type().isSamCompatibleOrKotlinLambda(codebase)
        val lastRequiredParameterIndex =
            if (hasTrailingLambda) {
                    requiredParameters.dropLast(1).lastOrNull()
                } else {
                    requiredParameters.last()
                }
                ?.parameterIndex ?: return
        optionalParameters.forEach { parameter ->
            if (parameter.parameterIndex < lastRequiredParameterIndex) {
                report(
                    KOTLIN_DEFAULT_PARAMETER_ORDER,
                    parameter,
                    "Parameter `${parameter.name()}` has a default value and should come " +
                        "after all parameters without default values (except for a trailing " +
                        "lambda parameter)"
                )
            }
        }
    }

    /**
     * Check that the nullability of [getterType] (from the return type of [getter]) and
     * [setterType] (from the parameter type of [setter]) match.
     */
    private fun compareAccessorNullability(
        getterType: TypeItem,
        setterType: TypeItem,
        getter: MethodItem,
        setter: MethodItem
    ) {
        if (getterType.modifiers.nullability != setterType.modifiers.nullability) {
            val getterTypeString = getterType.toTypeString(KOTLIN_NULLS_TYPE_STRING_CONFIGURATION)
            val setterTypeString = setterType.toTypeString(KOTLIN_NULLS_TYPE_STRING_CONFIGURATION)
            report(
                Issues.GETTER_SETTER_NULLABILITY,
                getter,
                "Nullability of $getterTypeString in getter ${getter.describe()} does not match $setterTypeString in corresponding setter ${setter.describe()}"
            )
        }
    }

    /** Check that the nullability of each getter/setter pair matches. */
    private fun checkAccessorNullabilityMatches(methods: Sequence<MethodItem>) {
        val getters = methods.filter { it.name().startsWith("get") && it.parameters().isEmpty() }

        for (getter in getters) {
            // Don't bother checking accessors generated from Kotlin properties, the nullness is
            // guaranteed to match.
            if (getter.property != null) continue

            val expectedSetterName = getter.name().replaceFirst("get", "set")
            val setter =
                methods.singleOrNull {
                    it.name() == expectedSetterName && it.parameters().size == 1
                } ?: continue

            val getterReturnType = getter.returnType()
            val setterParamType = setter.parameters().single().type()
            // Don't check nullness if the methods don't use the same type (this type equality check
            // doesn't consider modifiers).
            if (getterReturnType != setterParamType) return

            // Recur through the getter and setter type simultaneously.
            getterReturnType.accept(
                object : MultipleTypeVisitor() {
                    override fun visitType(type: TypeItem, other: List<TypeItem>) {
                        // [type] is from the getter, [other] is from the setter. Since the getter
                        // and setter are the same type, it is safe to assert that [other] isn't
                        // empty.
                        compareAccessorNullability(type, other.single(), getter, setter)
                    }
                },
                listOf(setterParamType)
            )
        }
    }

    private fun checkDataClass(cls: ClassItem) {
        if (cls.modifiers.isData()) {
            report(
                DATA_CLASS_DEFINITION,
                cls,
                "Exposing data classes as public API is discouraged because they are " +
                    "difficult to update while maintaining binary compatibility."
            )
        }
    }

    companion object {
        /** [TypeStringConfiguration] for use in [checkAccessorNullabilityMatches] */
        private val KOTLIN_NULLS_TYPE_STRING_CONFIGURATION =
            TypeStringConfiguration(kotlinStyleNulls = true)

        /**
         * Check the supplied [codebase] to see if it adheres to the API lint rules enforced by this
         * class, reporting any issues that it finds.
         *
         * If [oldCodebase] is provided then it will ignore any issues that are present in the
         * [oldCodebase] as there is little that can be done to rectify those.
         */
        fun check(
            codebase: Codebase,
            oldCodebase: Codebase?,
            reporter: Reporter,
            apiPredicateConfig: ApiPredicate.Config,
            config: Config,
        ) {
            val apiLint =
                ApiLint(
                    codebase,
                    oldCodebase,
                    reporter,
                    apiPredicateConfig,
                    config,
                )
            apiLint.check()
        }

        private data class GetterSetterPattern(val getter: String, val setter: String)

        private val goodBooleanGetterSetterPrefixes =
            listOf(
                GetterSetterPattern("has", "setHas"),
                GetterSetterPattern("can", "setCan"),
                GetterSetterPattern("should", "setShould"),
                GetterSetterPattern("is", "set")
            )
        private val goodBooleanPropertyPrefixes =
            goodBooleanGetterSetterPrefixes.joinToString(", ") { "`${it.getter}`" }

        private fun List<GetterSetterPattern>.match(
            name: String,
            prop: (GetterSetterPattern) -> String
        ) = firstOrNull {
            name.startsWith(prop(it)) &&
                name.getOrNull(prop(it).length)?.let { charAfterPrefix ->
                    charAfterPrefix.isUpperCase() || charAfterPrefix.isDigit()
                } ?: false
        }

        private val badBooleanGetterPrefixes = listOf("isHas", "isCan", "isShould", "get", "is")
        private val badBooleanSetterPrefixes = listOf("setIs", "set")

        private val badParameterClassNames =
            listOf(
                "Param",
                "Parameter",
                "Parameters",
                "Args",
                "Arg",
                "Argument",
                "Arguments",
                "Options",
                "Bundle"
            )

        private val badUriTypes = listOf("java.net.URL", "java.net.URI", "android.net.URL")

        private val badFutureTypes =
            listOf("java.util.concurrent.CompletableFuture", "java.util.concurrent.Future")

        private val listenableFuture = "com.google.common.util.concurrent.ListenableFuture"

        /**
         * Classes for manipulating file descriptors directly, where using ParcelFileDescriptor
         * isn't required
         */
        private val lowLevelFileClassNames =
            listOf(
                "android.os.FileUtils",
                "android.system.Os",
                "android.net.util.SocketUtils",
                "android.os.NativeHandle",
                "android.os.ParcelFileDescriptor"
            )

        /**
         * Classes which already use bare fields extensively, and bare fields are thus allowed for
         * consistency with existing API surface.
         */
        private val classesWithBareFields =
            listOf(
                "android.app.ActivityManager.RecentTaskInfo",
                "android.app.Notification",
                "android.content.pm.ActivityInfo",
                "android.content.pm.ApplicationInfo",
                "android.content.pm.ComponentInfo",
                "android.content.pm.ResolveInfo",
                "android.content.pm.FeatureGroupInfo",
                "android.content.pm.InstrumentationInfo",
                "android.content.pm.PackageInfo",
                "android.content.pm.PackageItemInfo",
                "android.content.res.Configuration",
                "android.graphics.BitmapFactory.Options",
                "android.os.Message",
                "android.system.StructPollfd"
            )

        /** Classes containing setting provider keys. */
        private val settingsKeyClasses =
            listOf(
                "android.provider.Settings.Global",
                "android.provider.Settings.Secure",
                "android.provider.Settings.System"
            )

        private val badUnits =
            mapOf(
                "Ns" to "Nanos",
                "Ms" to "Millis or Micros",
                "Sec" to "Seconds",
                "Secs" to "Seconds",
                "Hr" to "Hours",
                "Hrs" to "Hours",
                "Mo" to "Months",
                "Mos" to "Months",
                "Yr" to "Years",
                "Yrs" to "Years",
                "Byte" to "Bytes",
                "Space" to "Bytes"
            )
        private val uiPackageParts =
            listOf("animation", "view", "graphics", "transition", "widget", "webkit")

        private val constantNamePattern = Regex("[A-Z0-9_]+")
        private val internalNamePattern = Regex("[ms][A-Z0-9].*")
        private val fieldNamePattern = Regex("[a-z].*")
        private val onCallbackNamePattern = Regex("on[A-Z][a-z0-9][a-zA-Z0-9]*")
        private val configFieldPattern = Regex("config_[a-z][a-zA-Z0-9]*")
        private val layoutFieldPattern = Regex("layout_[a-z][a-zA-Z0-9]*")
        private val stateFieldPattern = Regex("state_[a-z_]+")
        private val resourceFileFieldPattern = Regex("[a-z0-9_]+")
        private val resourceValueFieldPattern = Regex("[a-z][a-zA-Z0-9]*")
        private val styleFieldPattern = Regex("[A-Z][A-Za-z0-9]+(_[A-Z][A-Za-z0-9]+?)*")

        // An acronym is 2 or more capital letters. Following the acronym there can either be a next
        // word (capital followed by a non-capital), digit, underscore, or word break (digits and
        // underscores count as word characters).
        // Including the next character in the regex means the first capture group will contain just
        // the acronym and not the start of the next word, e.g. for "HTMLWriter" the acronym is
        // "HTML", not "HTMLW".
        private val acronymPattern = Regex("([A-Z]{2,})(?:[A-Z][a-z]|[0-9]|_|\\b)")

        private val serviceDumpMethodParameterTypes =
            listOf("java.io.FileDescriptor", "java.io.PrintWriter", "java.lang.String[]")

        private fun isServiceDumpMethod(item: Item) =
            when (item) {
                is MethodItem -> isServiceDumpMethod(item)
                is ParameterItem ->
                    item.possibleContainingMethod()?.let { isServiceDumpMethod(it) } ?: false
                else -> false
            }

        private fun isServiceDumpMethod(item: MethodItem) =
            item.name() == "dump" &&
                item.containingClass().extends("android.app.Service") &&
                item.parameters().map { it.type().toTypeString() } ==
                    serviceDumpMethodParameterTypes

        private fun hasAcronyms(name: String, allowedAcronyms: List<String>): Boolean {
            return getFirstAcronym(name, allowedAcronyms) != null
        }

        private fun getFirstAcronym(name: String, allowedAcronyms: List<String>): String? {
            val fullMatch = acronymPattern.find(name) ?: return null
            // Group 1 is just the acronym.
            val result = fullMatch.groups[1] ?: return null

            val acronym = name.substring(result.range.first, result.range.last + 1)
            return if (acronym !in allowedAcronyms) {
                acronym
            } else if (fullMatch.range.last < name.length) {
                // Keep searching from the end of the last match, if possible.
                getFirstAcronym(name.substring(result.range.last + 1), allowedAcronyms)
            } else {
                null
            }
        }

        /** for something like "HTMLWriter", returns "HtmlWriter" */
        private fun decapitalizeAcronyms(name: String, allowedAcronyms: List<String>): String {
            var s = name

            if (s.none { it.isLowerCase() }) {
                // The entire thing is capitalized. If so, just perform
                // normal capitalization, but try dropping _'s.
                return SdkVersionInfo.underlinesToCamelCase(s.lowercase(Locale.US))
                    .replaceFirstChar {
                        if (it.isLowerCase()) {
                            it.titlecase(Locale.getDefault())
                        } else {
                            it.toString()
                        }
                    }
            }

            while (true) {
                val acronym = getFirstAcronym(s, allowedAcronyms) ?: return s
                val index = s.indexOf(acronym)
                if (index == -1) {
                    return s
                }
                // Convert all but the first character of the acronym to lowercase.
                val decapitalized = acronym[0] + acronym.substring(1).lowercase(Locale.US)
                s = s.replace(acronym, decapitalized)
            }
        }

        /** The set of heavyweight BitSet classes that are disallowed by [checkBitSet] */
        private val HEAVY_BIT_SET_CLASSES = setOf("java.util.BitSet")

        /** The set of concrete classes that are disallowed by [checkCollections] */
        private val CONCRETE_COLLECTION_CLASSES =
            setOf(
                "java.util.Vector",
                "java.util.LinkedList",
                "java.util.ArrayList",
                "java.util.Stack",
                "java.util.HashMap",
                "java.util.HashSet",
                "android.util.ArraySet",
                "android.util.ArrayMap",
            )
    }
}

internal const val DefaultLintErrorMessage =
    """
************************************************************
Your API changes are triggering API Lint warnings or errors.
To make these errors go away, fix the code according to the
error and/or warning messages above.

If it's not possible to do so, there are two workarounds:

1. Suppress the issues with @Suppress("<id>") / @SuppressWarnings("<id>")
2. Update the baseline passed into metalava
************************************************************
"""
