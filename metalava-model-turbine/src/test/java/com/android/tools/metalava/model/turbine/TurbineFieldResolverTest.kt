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

package com.android.tools.metalava.model.turbine

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.java
import com.google.common.collect.ImmutableList
import com.google.turbine.binder.Binder
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.JimageClassBinder
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.env.SimpleEnv
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.diag.SourceFile
import com.google.turbine.parse.Parser
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.Ident
import java.util.Optional
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TurbineFieldResolverTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    /** Parse and bind [sources] into a [CompoundEnv]. */
    private fun bind(sources: List<TestFile>): CompoundEnv<ClassSymbol, TypeBoundClass> {
        val srcDir = temporaryFolder.newFolder("src")

        // Parse all the files.
        val units =
            sources
                .stream()
                .map { it.createFile(srcDir) }
                .map { file -> Parser.parse(SourceFile(file.path, file.readText())) }
                .collect(ImmutableList.toImmutableList())

        // Bind them together.
        val classPath = ClassPathBinder.bindClasspath(listOf())
        val bootClassPath = JimageClassBinder.bindDefault()
        val result =
            Binder.bind(units, classPath, bootClassPath, /* moduleVersion= */ Optional.empty())
                ?: error("Binding failed")

        // Get mapping from ClassSymbol to TypeBoundClass from the class path.
        val classPathEnv: CompoundEnv<ClassSymbol, TypeBoundClass> =
            CompoundEnv.of(result.classPathEnv())

        // Get mapping from ClassSymbol to SourceTypeBoundClass from sources.
        val sourceEnv = SimpleEnv(result.units())

        // Combine the mappings together. Searching sources first then class path.
        val combinedEnv = classPathEnv.append(sourceEnv)
        return combinedEnv
    }

    /** Create a [TurbineFieldResolver] for resolving fields as if from within [binaryClassName]. */
    private fun CompoundEnv<ClassSymbol, TypeBoundClass>.resolverFor(
        binaryClassName: String
    ): TurbineFieldResolver {
        // Select the class from where the field will be resolved.
        val testClassSym = ClassSymbol(binaryClassName)
        val testClassInfo =
            this[testClassSym] as? SourceTypeBoundClass ?: error("unknown class $testClassSym")

        // Create a resolver.
        val fieldResolver =
            TurbineFieldResolver(
                testClassSym,
                testClassSym,
                testClassInfo.memberImports(),
                testClassInfo.scope(),
                this
            )
        return fieldResolver
    }

    private fun assertFieldCanBeResolved(fieldResolver: TurbineFieldResolver, fieldName: String) {
        val idents =
            fieldName
                .split(".")
                .stream()
                .map { Ident(1, it) }
                .collect(ImmutableList.toImmutableList())

        fieldResolver.resolveField(Tree.ConstVarName(0, idents))
            ?: fail("Could not resolve field $fieldName within $fieldResolver")
    }

    @Test
    fun `Test basic resolver`() {
        val sources =
            listOf(
                java(
                    """
                        package test.pkg;
                        import test.other.pkg.Imported;
                        import static test.other.pkg.ImportedStatically.STATIC;
                        import test.wildcard.pkg.*;
                        import static test.wildcard.pkg.ImportedWildcardStatically.*;
                        public class Test {
                          public static int instanceField = 1;
                          public static final int STATIC_FIELD = 2;
                          public class Nested {
                            public static final int NESTED_FIELD = 3;
                          }
                        }
                    """
                ),
                java(
                    """
                        package test.other.pkg;
                        public enum NotImported {
                          ENUM1,
                          ENUM2,
                        }
                    """
                ),
                java(
                    """
                        package test.other.pkg;
                        public enum Imported {
                          CONST,
                        }
                    """
                ),
                java(
                    """
                        package test.other.pkg;
                        public enum ImportedStatically {
                          STATIC,
                        }
                    """
                ),
                java(
                    """
                        package test.wildcard.pkg;
                        public enum ImportedWildcard {
                          WILDCARD,
                        }
                    """
                ),
                java(
                    """
                        package test.wildcard.pkg;
                        public enum ImportedWildcardStatically {
                          STATIC_WILDCARD,
                        }
                    """
                ),
            )

        val combinedEnv = bind(sources)

        combinedEnv.resolverFor("test/pkg/Test").let { fieldResolver ->
            assertFieldCanBeResolved(fieldResolver, "instanceField")
            assertFieldCanBeResolved(fieldResolver, "STATIC_FIELD")
            assertFieldCanBeResolved(fieldResolver, "Nested.NESTED_FIELD")
            assertFieldCanBeResolved(fieldResolver, "Float.NaN")
            assertFieldCanBeResolved(fieldResolver, "test.other.pkg.NotImported.ENUM1")
            assertFieldCanBeResolved(fieldResolver, "Imported.CONST")
            assertFieldCanBeResolved(fieldResolver, "STATIC")
            assertFieldCanBeResolved(fieldResolver, "ImportedWildcard.WILDCARD")
            assertFieldCanBeResolved(fieldResolver, "STATIC_WILDCARD")
        }

        combinedEnv.resolverFor("test/pkg/Test${"$"}Nested").let { fieldResolver ->
            assertFieldCanBeResolved(fieldResolver, "instanceField")
            assertFieldCanBeResolved(fieldResolver, "STATIC_FIELD")
        }
    }
}
