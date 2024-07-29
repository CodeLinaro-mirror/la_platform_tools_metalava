/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Test for the [FileFormat.sortWholeExtendsList] property. */
class InterfaceTypeListOrderTest : DriverTest() {
    private fun runOrderTest(fileFormat: FileFormat, api: String) {
        check(
            format = fileFormat,
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            interface PackagePrivate {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public interface Public {}
                        """
                    ),
                    java(
                        """
                            package test.other;
                            // This sorts before test.pkg.Public when qualified name is used and
                            // after when full name is used.
                            public interface Special {}
                        """
                    ),
                    java(
                        """
                            package test.other;
                            // This sorts before test.pkg.Public when qualified name is used and
                            // uses source order when full name is used.
                            public interface Public {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            // Check the behavior on an interface when the first type is private.
                            // In this case when `FileFormat.sortWholeExtendsList=false` then
                            // `test.other.Special` should be last in the list and
                            // `test.other.Public` should be after `test.pkg.Public` as they are
                            // partially sorted by full name, maintaining source order when the
                            // full names match.
                            public interface InterfaceFirstPrivate extends PackagePrivate, test.other.Special, Public, test.other.Public {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            // Check the behavior on an interface when the first type is not private.
                            // In this case when `FileFormat.sortWholeExtendsList=false` then
                            // `test.other.Special` should be first in the signature list even
                            // though it sorts to the end because the first interface is always
                            // written out first if it is not hidden.
                            // `test.other.Public` should be after `test.pkg.Public` as they are
                            // partially sorted by full name, maintaining source order when the
                            // full names match.
                            public interface InterfaceFirstNotPrivate extends test.other.Special, PackagePrivate, Public, test.other.Public {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            // Check the behavior on a class when the first type is private.
                            // In this case when `FileFormat.sortWholeExtendsList=false` then
                            // `test.other.Special` should be last in the list and
                            // `test.other.Public` should be after `test.pkg.Public` as they are
                            // partially sorted by full name, maintaining source order when the
                            // full names match.
                            public class ClassFirstPrivate implements PackagePrivate, test.other.Special, Public, test.other.Public {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            // Check the behavior on a class when the first type is not private.
                            // In this case when `FileFormat.sortWholeExtendsList=false` then
                            // `test.other.Special` should be last in the list and
                            // `test.other.Public` should be after `test.pkg.Public` as they are
                            // partially sorted by full name, maintaining source order when the
                            // full names match.
                            public class ClassFirstNotPrivate implements test.other.Special, PackagePrivate, Public, test.other.Public {}
                        """
                    ),
                ),
            api = api,
        )
    }

    @Test
    fun `First interface is private, legacy order`() {
        runOrderTest(
            fileFormat = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.other {
                      public interface Public {
                      }
                      public interface Special {
                      }
                    }
                    package test.pkg {
                      public class ClassFirstNotPrivate implements test.pkg.Public test.other.Public test.other.Special {
                        ctor public ClassFirstNotPrivate();
                      }
                      public class ClassFirstPrivate implements test.pkg.Public test.other.Public test.other.Special {
                        ctor public ClassFirstPrivate();
                      }
                      public interface InterfaceFirstNotPrivate extends test.other.Special test.pkg.Public test.other.Public {
                      }
                      public interface InterfaceFirstPrivate extends test.pkg.Public test.other.Public test.other.Special {
                      }
                      public interface Public {
                      }
                    }
                """,
        )
    }

    @Test
    fun `First interface is private, full order`() {
        // All the `implements` lists are in the same order as the whole list is sorted first by
        // full name and then by qualified name.
        runOrderTest(
            fileFormat = FileFormat.V2.copy(specifiedSortWholeExtendsList = true),
            api =
                """
                    // Signature format: 2.0
                    package test.other {
                      public interface Public {
                      }
                      public interface Special {
                      }
                    }
                    package test.pkg {
                      public class ClassFirstNotPrivate implements test.other.Public test.pkg.Public test.other.Special {
                        ctor public ClassFirstNotPrivate();
                      }
                      public class ClassFirstPrivate implements test.other.Public test.pkg.Public test.other.Special {
                        ctor public ClassFirstPrivate();
                      }
                      public interface InterfaceFirstNotPrivate extends test.other.Public test.pkg.Public test.other.Special {
                      }
                      public interface InterfaceFirstPrivate extends test.other.Public test.pkg.Public test.other.Special {
                      }
                      public interface Public {
                      }
                    }
                """,
        )
    }
}
