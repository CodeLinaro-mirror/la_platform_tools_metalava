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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class RecordClassCompatibilityCheckTest : DriverTest() {
    @Test
    fun `Check compatibility converting normal class to record`() {
        check(
            expectedIssues =
                """
                    load-api.txt:3: error: Binary breaking change: test.pkg.NotSuitableForRecord changed from class to record class [ChangedClass]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public class NotSuitableForRecord {
                        ctor public NotSuitableForRecord();
                      }
                      public final class SuitableForRecordNoComponents {
                        ctor public SuitableForRecordNoComponents();
                      }
                      public final class SuitableForRecordOneComponent {
                        ctor public SuitableForRecordOneComponent(int);
                      }
                    }
                """,
            signatureSource =
                """
                    package test.pkg {
                      public record NotSuitableForRecord {
                        ctor public NotSuitableForRecord();
                      }
                      public record SuitableForRecordNoComponents {
                        ctor public SuitableForRecordNoComponents();
                      }
                      public record SuitableForRecordOneComponent {
                        record_component #0 component: int;
                        ctor public SuitableForRecordOneComponent(int);
                        method public int component();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Check compatibility record class to normal class`() {
        check(
            expectedIssues =
                """
                    load-api.txt:3: error: Binary breaking change: test.pkg.RecordNoComponents changed from record class to class [ChangedClass]
                    load-api.txt:6: error: Binary breaking change: test.pkg.RecordOneComponent changed from record class to class [ChangedClass]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public record RecordNoComponents {
                        ctor public RecordNoComponents();
                      }
                      public record RecordOneComponent {
                        record_component #0 component: int;
                        ctor public RecordOneComponent(int);
                        method public int component();
                      }
                    }
                """,
            signatureSource =
                """
                    package test.pkg {
                      public final class RecordNoComponents {
                        ctor public RecordNoComponents();
                      }
                      public final class RecordOneComponent {
                        ctor public RecordOneComponent(int);
                        method public int component();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Check compatibility record class change component order`() {
        check(
            expectedIssues =
                """
                    load-api.txt:4: error: Binary breaking change: Record component test.pkg.Point.y changed position of record component y from 1 to 0 [ChangedRecordComponent]
                    load-api.txt:5: error: Binary breaking change: Record component test.pkg.Point.x changed position of record component x from 0 to 1 [ChangedRecordComponent]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public record Point {
                        record_component #0 x: int;
                        record_component #1 y: int;
                        ctor public Point(int, int);
                        method public int x();
                        method public int y();
                      }
                    }
                """,
            signatureSource =
                """
                    package test.pkg {
                      public record Point {
                        record_component #0 y: int;
                        record_component #1 x: int;
                        ctor public Point(int, int);
                        method public int x();
                        method public int y();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Check compatibility record class add component`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Binary breaking change: Class test.pkg.Point added record component z [AddedRecordComponent]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public record Point {
                        record_component #0 x: int;
                        record_component #1 y: int;
                        ctor public Point(int, int);
                        method public int x();
                        method public int y();
                      }
                    }
                """,
            signatureSource =
                // This intentionally preserves a two parameter constructor to prevent the
                // compatibility check from reporting a RemovedMethod issue making it seem as
                // though this type of change was already prevented.
                """
                    package test.pkg {
                      public record Point {
                        record_component #0 x: int;
                        record_component #1 y: int;
                        record_component #2 z: int;
                        ctor public Point(int, int);
                        ctor public Point(int, int, int);
                        method public int x();
                        method public int y();
                        method public int z();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Check compatibility record class change component type`() {
        check(
            expectedIssues =
                """
                    load-api.txt:4: error: Binary breaking change: Record component test.pkg.Point.x changed type of record component x from int to long [ChangedRecordComponent]
                    load-api.txt:5: error: Binary breaking change: Record component test.pkg.Point.y changed type of record component y from int to long [ChangedRecordComponent]
                    load-api.txt:8: error: Binary breaking change: Method test.pkg.Point.x has changed return type from int to long [ChangedType]
                    load-api.txt:9: error: Binary breaking change: Method test.pkg.Point.y has changed return type from int to long [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public record Point {
                        record_component #0 x: int;
                        record_component #1 y: int;
                        ctor public Point(int, int);
                        method public int x();
                        method public int y();
                      }
                    }
                """,
            signatureSource =
                """
                    package test.pkg {
                      public record Point {
                        record_component #0 x: long;
                        record_component #1 y: long;
                        ctor public Point(int, int);
                        ctor public Point(long, long);
                        method public long x();
                        method public long y();
                      }
                    }
                """,
        )
    }
}
