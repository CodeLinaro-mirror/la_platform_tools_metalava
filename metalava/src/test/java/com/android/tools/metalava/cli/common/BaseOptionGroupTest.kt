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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Base class for tests of [OptionGroup] classes. */
abstract class BaseOptionGroupTest<O : OptionGroup>(
    private val expectedHelp: String,
) : TemporaryFolderOwner {

    @get:Rule override val temporaryFolder = TemporaryFolder()

    protected abstract fun createOptions(): O

    /**
     * Run a test on the [OptionGroup] of type [O].
     *
     * Generally this will use the [OptionGroup] created by [createOptions] but that can be
     * overridden for a test by providing an [optionGroup] parameter directly.
     *
     * @param args the arguments to pass to Clikt to parse.
     * @param optionGroup the [OptionGroup] implementation to use, if `null` then this will use
     *   [createOptions] to create a new one.
     * @param includeDependentGroups `true` if dependent [OptionGroup]s, i.e. [OptionGroup]s whose
     *   option properties are referenced from [optionGroup], need to be included in the test so
     *   that they are initialized properly. If this is `true` and [optionGroup] implements
     *   [RequiresOtherGroups] then [OptionGroup]s in [RequiresOtherGroups.requiredGroups] are added
     *   to the [CliktCommand] used for testing.
     */
    protected fun runTest(
        vararg args: String,
        optionGroup: O? = null,
        includeDependentGroups: Boolean = true,
        test: Result<O>.() -> Unit,
    ) {
        val testFactory = { optionGroup ?: createOptions() }
        val command = MockCommand(testFactory, includeDependentGroups)
        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()
        val rootCommand = MetalavaCommand(executionEnvironment, null, ProgressTracker())
        rootCommand.subcommands(command)
        rootCommand.process(arrayOf("mock") + args)
        val result =
            Result(
                options = command.options,
                stdout = removeBoilerplate(stdout.toString()),
                stderr = removeBoilerplate(stderr.toString()),
            )
        result.test()
    }

    /** Remove various different forms of boilerplate text. */
    private fun removeBoilerplate(out: String) =
        out.trim()
            .removePrefix("Aborting: ")
            .removePrefix("Usage: metalava mock [options]")
            .trim()
            .removePrefix("Error: ")

    data class Result<O : OptionGroup>(
        val options: O,
        val stdout: String,
        val stderr: String,
    )

    @Test
    fun `Test help`() {
        runTest(
            "--help",
            // Do not include dependent groups when generating the help.
            includeDependentGroups = false,
        ) {
            val trimmedOut =
                stdout.removePrefix(
                    """
                        Options:
                          --help                                     Show this message and exit


                    """
                        .trimIndent()
                )
            Assert.assertEquals(expectedHelp, trimmedOut)
        }
    }
}

/**
 * A [PropertyDelegateProvider] that will retrieve any [RequiresOtherGroups.requiredGroups] from
 * [optionGroup] and treat them as if they were property delegated of the [CliktCommand] to which
 * this belongs.
 */
private class DependentGroupsProvider(
    private val optionGroup: OptionGroup,
    private val includeDependentGroups: Boolean
) : PropertyDelegateProvider<CliktCommand, DependentGroupsProvider> {
    /**
     * If [includeDependentGroups] is `true` and [optionGroup] is a [RequiresOtherGroups] then this
     * will invoke [OptionGroup.provideDelegate] on each [OptionGroup] in
     * [RequiresOtherGroups.requiredGroups].
     */
    override fun provideDelegate(
        thisRef: CliktCommand,
        property: KProperty<*>
    ): DependentGroupsProvider {
        if (includeDependentGroups) {
            if (optionGroup is RequiresOtherGroups) {
                for (dependentGroup in optionGroup.requiredGroups) {
                    dependentGroup.provideDelegate(thisRef, property)
                }
            }
        }

        return this
    }

    /** Needed to be a property delegate; simply returns this. */
    operator fun getValue(command: CliktCommand, property: KProperty<*>): DependentGroupsProvider {
        return this
    }
}

/**
 * A [CliktCommand] that is used to parse test arguments and initialize the [OptionGroup] being
 * tested.
 *
 * @param O the type of [OptionGroup] being tested.
 * @param factory a factory for creating the [OptionGroup] to be tested.
 * @param includeDependentGroups if dependent [OptionGroup]s need to be included in the test. See
 *   [BaseOptionGroupTest.runTest] for more details.
 */
private class MockCommand<O : OptionGroup>(factory: () -> O, includeDependentGroups: Boolean) :
    CliktCommand() {
    val options by factory()

    /**
     * Register the [DependentGroupsProvider] as a property delegate of this instance so that it can
     * register any dependent [OptionGroup]s, if required.
     *
     * This is unused as it is here only for its side effect of registering dependent
     * [OptionGroup]s.
     */
    @Suppress("unused")
    private val dependentGroups by DependentGroupsProvider(options, includeDependentGroups)

    init {
        context {
            localization = MetalavaLocalization()
            helpFormatter = MetalavaHelpFormatter(::plainTerminal, localization)
            helpOptionNames = setOf("--help")
        }
    }

    override fun run() {}
}
