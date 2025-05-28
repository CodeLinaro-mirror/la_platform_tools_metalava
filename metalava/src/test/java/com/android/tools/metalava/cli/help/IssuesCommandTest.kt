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

package com.android.tools.metalava.cli.help

import com.android.tools.metalava.cli.common.BaseCommandTest
import org.junit.Test

class IssuesCommandTest : BaseCommandTest<HelpCommand>({ HelpCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("help", "issues")
            expectedStdout =
                """
Usage: metalava help issues <issue>?

  Provides help related to issues and issue reporting

Available Issues                             |  Category                             |   Default Severity
---------------------------------------------+---------------------------------------+--------------------
  AbstractInner                              |  api_lint                             |   warning
  AcronymName                                |  api_lint                             |   warning
  ActionValue                                |  api_lint                             |   error
  AddSealed                                  |  source_compatibility_only            |   error
  AddedAbstractMethod                        |  binary_and_source_compatibility      |   error
  AddedAnnotation                            |  other_compatibility                  |   error
  AddedClass                                 |  other_compatibility                  |   hidden
  AddedField                                 |  other_compatibility                  |   hidden
  AddedFinal                                 |  binary_and_source_compatibility      |   error
  AddedFinalUninstantiable                   |  other_compatibility                  |   hidden
  AddedInterface                             |  other_compatibility                  |   hidden
  AddedMethod                                |  other_compatibility                  |   hidden
  AddedPackage                               |  other_compatibility                  |   hidden
  AddedReified                               |  binary_compatibility_only            |   error
  AllUpper                                   |  api_lint                             |   error
  AndroidUri                                 |  api_lint                             |   error
  AnnotationExtraction                       |  unknown                              |   error
  ArrayReturn                                |  api_lint                             |   warning
  AsyncSuffixFuture                          |  api_lint                             |   error
  AutoBoxing                                 |  api_lint                             |   error
  BadFuture                                  |  api_lint                             |   error
  BannedThrow                                |  api_lint                             |   error
  BecameUnchecked                            |  other_compatibility                  |   error
  BothPackageInfoAndHtml                     |  documentation                        |   warning
  BroadcastBehavior                          |  documentation                        |   error
  BuilderSetStyle                            |  api_lint                             |   warning
  CallbackInterface                          |  api_lint                             |   hidden
  CallbackMethodName                         |  api_lint                             |   error
  CallbackName                               |  api_lint                             |   warning
  ChangedAbstract                            |  binary_and_source_compatibility      |   error
  ChangedClass                               |  binary_and_source_compatibility      |   error
  ChangedDefault                             |  binary_and_source_compatibility      |   error
  ChangedDeprecated                          |  source_compatibility_only            |   hidden
  ChangedNative                              |  other_compatibility                  |   hidden
  ChangedScope                               |  binary_and_source_compatibility      |   error
  ChangedStatic                              |  binary_and_source_compatibility      |   error
  ChangedSuperclass                          |  binary_and_source_compatibility      |   error
  ChangedThrows                              |  binary_and_source_compatibility      |   error
  ChangedType                                |  binary_and_source_compatibility      |   error
  ChangedValue                               |  binary_compatibility_only            |   error
  ChangedVolatile                            |  other_compatibility                  |   error
  CompileTimeConstant                        |  api_lint                             |   error
  ConcreteCollection                         |  api_lint                             |   error
  ConditionalRequiresPermissionNotExplained  |  api_lint                             |   hidden
  ConfigFieldName                            |  api_lint                             |   error
  ContextFirst                               |  api_lint                             |   error
  ContextNameSuffix                          |  api_lint                             |   error
  DataClassDefinition                        |  api_lint                             |   hidden
  DefaultValueChange                         |  source_compatibility_only            |   error
  Deprecated                                 |  documentation                        |   hidden
  DeprecationMismatch                        |  documentation                        |   error
  DocumentExceptions                         |  api_lint                             |   error
  DuplicateSourceClass                       |  unknown                              |   warning
  EndsWithImpl                               |  api_lint                             |   error
  Enum                                       |  api_lint                             |   error
  EqualsAndHashCode                          |  api_lint                             |   error
  ExceptionName                              |  api_lint                             |   error
  ExecutorRegistration                       |  api_lint                             |   warning
  ExtendsDeprecated                          |  api_lint                             |   hidden
  ExtendsError                               |  api_lint                             |   error
  FlaggedApiLiteral                          |  api_lint                             |   warning_error_when_new
  ForbiddenSuperClass                        |  api_lint                             |   error
  ForbiddenTag                               |  documentation                        |   error
  FractionFloat                              |  api_lint                             |   error
  FunRemoval                                 |  source_compatibility_only            |   error
  GenericCallbacks                           |  api_lint                             |   error
  GenericException                           |  api_lint                             |   error
  GetterOnBuilder                            |  api_lint                             |   warning
  GetterSetterNames                          |  api_lint                             |   error
  GetterSetterNullability                    |  api_lint                             |   warning_error_when_new
  HeavyBitSet                                |  api_lint                             |   error
  HiddenAbstractMethod                       |  api_lint                             |   error
  HiddenSuperclass                           |  documentation                        |   warning
  HiddenTypeParameter                        |  documentation                        |   warning
  HiddenTypedefConstant                      |  unknown                              |   error
  IgnoringSymlink                            |  unknown                              |   info
  InconsistentMergeAnnotation                |  api_lint                             |   warning_error_when_new
  InfixRemoval                               |  source_compatibility_only            |   error
  InheritChangesSignature                    |  unknown                              |   warning_error_when_new
  IntDef                                     |  documentation                        |   hidden
  IntentBuilderName                          |  api_lint                             |   warning
  IntentName                                 |  api_lint                             |   error
  InterfaceConstant                          |  api_lint                             |   error
  InternalClasses                            |  api_lint                             |   error
  InternalError                              |  unknown                              |   error
  InternalField                              |  api_lint                             |   error
  InvalidFeatureEnforcement                  |  documentation                        |   error
  InvalidNullConversion                      |  source_compatibility_only            |   error
  InvalidNullabilityAnnotation               |  unknown                              |   error
  InvalidNullabilityAnnotationWarning        |  unknown                              |   warning
  InvalidNullabilityOverride                 |  api_lint                             |   error
  InvalidPackage                             |  unknown                              |   error
  InvalidSyntax                              |  unknown                              |   error
  IoError                                    |  unknown                              |   error
  KotlinDefaultParameterOrder                |  api_lint                             |   error
  KotlinKeyword                              |  api_lint                             |   error
  KotlinOperator                             |  api_lint                             |   info
  ListenerInterface                          |  api_lint                             |   error
  ListenerLast                               |  api_lint                             |   warning
  ManagerConstructor                         |  api_lint                             |   error
  ManagerLookup                              |  api_lint                             |   error
  MentionsGoogle                             |  api_lint                             |   error
  MethodNameTense                            |  api_lint                             |   warning
  MethodNameUnits                            |  api_lint                             |   error
  MinMaxConstant                             |  api_lint                             |   warning
  MissingBuildMethod                         |  api_lint                             |   warning
  MissingColumn                              |  documentation                        |   warning
  MissingEnvironmentsValue                   |  api_lint                             |   error
  MissingFromValue                           |  api_lint                             |   error
  MissingGetterMatchingBuilder               |  api_lint                             |   warning
  MissingInnerNullability                    |  api_lint                             |   hidden
  MissingJvmstatic                           |  api_lint                             |   warning
  MissingNullability                         |  api_lint                             |   error
  MissingPermission                          |  documentation                        |   error
  MultipleThreadAnnotations                  |  documentation                        |   error
  MutableBareField                           |  api_lint                             |   error
  NoByteOrShort                              |  api_lint                             |   warning
  NoClone                                    |  api_lint                             |   error
  NoSettingsProvider                         |  api_lint                             |   hidden
  NotCloseable                               |  api_lint                             |   warning
  Nullable                                   |  documentation                        |   hidden
  NullableCollection                         |  api_lint                             |   warning
  NullableCollectionElement                  |  api_lint                             |   warning
  OnNameExpected                             |  api_lint                             |   warning
  OperatorRemoval                            |  source_compatibility_only            |   error
  OptionalBuilderConstructorArgument         |  api_lint                             |   warning
  OverlappingConstants                       |  api_lint                             |   warning
  PackageLayering                            |  api_lint                             |   warning
  PairedRegistration                         |  api_lint                             |   error
  ParameterNameChange                        |  source_compatibility_only            |   error
  ParcelConstructor                          |  api_lint                             |   error
  ParcelCreator                              |  api_lint                             |   error
  ParcelNotFinal                             |  api_lint                             |   error
  ParcelableList                             |  api_lint                             |   warning
  ParseError                                 |  unknown                              |   error
  PercentageInt                              |  api_lint                             |   error
  PrivateSuperclass                          |  documentation                        |   warning
  ProtectedMember                            |  api_lint                             |   error
  PublicTypedef                              |  api_lint                             |   error
  RawAidl                                    |  api_lint                             |   error
  ReferencesDeprecated                       |  api_lint                             |   hidden
  ReferencesHidden                           |  api_lint                             |   error
  RemovedAnnotation                          |  other_compatibility                  |   error
  RemovedClass                               |  binary_and_source_compatibility      |   error
  RemovedDeprecatedClass                     |  binary_and_source_compatibility      |   inherit
  RemovedDeprecatedField                     |  binary_and_source_compatibility      |   inherit
  RemovedDeprecatedMethod                    |  binary_and_source_compatibility      |   inherit
  RemovedField                               |  binary_and_source_compatibility      |   error
  RemovedFinal                               |  binary_compatibility_only            |   error
  RemovedFinalStrict                         |  other_compatibility                  |   error
  RemovedInterface                           |  binary_and_source_compatibility      |   error
  RemovedJvmDefaultWithCompatibility         |  binary_compatibility_only            |   error
  RemovedMethod                              |  binary_and_source_compatibility      |   error
  RemovedPackage                             |  binary_and_source_compatibility      |   error
  RequiresPermission                         |  documentation                        |   error
  ResourceFieldName                          |  api_lint                             |   error
  ResourceStyleFieldName                     |  api_lint                             |   error
  ResourceValueFieldName                     |  api_lint                             |   error
  RethrowRemoteException                     |  api_lint                             |   error
  ReturningUnexpectedConstant                |  unknown                              |   warning
  SamShouldBeLast                            |  api_lint                             |   warning
  SdkConstant                                |  documentation                        |   error
  ServiceName                                |  api_lint                             |   error
  SetterReturnsThis                          |  api_lint                             |   warning
  ShowingMemberInHiddenClass                 |  api_lint                             |   error
  SignatureFileError                         |  unknown                              |   error
  SingleMethodInterface                      |  api_lint                             |   error
  SingletonConstructor                       |  api_lint                             |   error
  SingularCallback                           |  api_lint                             |   error
  StartWithLower                             |  api_lint                             |   error
  StartWithUpper                             |  api_lint                             |   error
  StaticFinalBuilder                         |  api_lint                             |   warning
  StaticUtils                                |  api_lint                             |   error
  StreamFiles                                |  api_lint                             |   warning
  SuperfluousPrefix                          |  unknown                              |   warning
  Todo                                       |  documentation                        |   error
  TopLevelBuilder                            |  api_lint                             |   warning
  TypeParseError                             |  unknown                              |   error
  UnavailableSymbol                          |  documentation                        |   warning
  UnflaggedApi                               |  api_lint                             |   hidden
  UnhiddenSystemApi                          |  api_lint                             |   error
  UniqueKotlinOperator                       |  api_lint                             |   error
  UnmatchedMergeAnnotation                   |  api_lint                             |   error
  UnqualifiedTypeError                       |  unknown                              |   hidden
  UnresolvedImport                           |  unknown                              |   info
  UnresolvedLink                             |  documentation                        |   error
  UseIcu                                     |  api_lint                             |   warning
  UseParcelFileDescriptor                    |  api_lint                             |   error
  UserHandle                                 |  api_lint                             |   warning
  UserHandleName                             |  api_lint                             |   warning
  ValueClassDefinition                       |  api_lint                             |   error
  VarargRemoval                              |  binary_and_source_compatibility      |   error
  VisiblySynchronized                        |  api_lint                             |   error
"""
                    .trimIndent()
        }
    }

    @Test
    fun `Test issue help`() {
        commandTest {
            args += arrayOf("help", "issues", "AddedFinal")

            expectedStdout = "Under construction. No additional help available at the moment."
        }
    }

    @Test
    fun `Test unknown issue`() {
        commandTest {
            args += arrayOf("help", "issues", "AdddFinal")

            expectedStderr =
                """
                Aborting: Usage: metalava help issues <issue>?

                Error: no such issue: "AdddFinal". (Possible issues: AddedFinal, AddedField, AddedFinalUninstantiable)
            """
                    .trimIndent()
        }
    }
}
