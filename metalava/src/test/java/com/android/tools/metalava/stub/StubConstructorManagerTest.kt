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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.MatchAllPredicate
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class StubConstructorManagerTest : Assertions {

    @Test
    fun `addConstructors skips non-emitted classes unless extended by emitted class`() {
        val classPathFile =
            SignatureFile.fromText(
                "classpath.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class SuperClass {
                        ctor public SuperClass(int);
                      }
                      public class UnrelatedClass {
                        ctor public UnrelatedClass(int);
                      }
                    }
                """,
                classOrigin = ClassOrigin.CLASS_PATH,
            )
        val commandLineFile =
            SignatureFile.fromText(
                "api.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class EmittedClass extends test.pkg.SuperClass {
                        ctor public EmittedClass(int);
                      }
                    }
                """,
                classOrigin = ClassOrigin.COMMAND_LINE,
            )

        val codebase = ApiFile.parseApi(listOf(classPathFile, commandLineFile))
        val superClass = codebase.assertClass("test.pkg.SuperClass", expectedEmit = false)
        val unrelatedClass = codebase.assertClass("test.pkg.UnrelatedClass", expectedEmit = false)
        val emittedClass = codebase.assertClass("test.pkg.EmittedClass")

        val manager = StubConstructorManager(codebase)
        manager.addConstructors(MatchAllPredicate)

        // EmittedClass is emitted so its stub and super constructors should be computed.
        assertNotNull(manager.optionalStubConstructor(emittedClass))
        assertEquals(
            superClass.constructors().single(),
            manager.optionalSuperConstructor(emittedClass),
        )

        // SuperClass is not emitted, but EmittedClass extends it so its stub constructor must be
        // computed.
        assertEquals(
            superClass.constructors().single(),
            manager.optionalStubConstructor(superClass),
        )

        // UnrelatedClass is not emitted and no emitted class extends it, so StubConstructorManager
        // should not visit it or compute its stub constructor.
        assertNull(manager.optionalStubConstructor(unrelatedClass))
    }
}
