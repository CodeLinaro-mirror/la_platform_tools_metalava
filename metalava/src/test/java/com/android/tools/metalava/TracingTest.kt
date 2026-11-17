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

package com.android.tools.metalava

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TracingTest : DriverTest() {

    @Test
    fun `Check that running tracing produces a file`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class ClassMcClassface {}
                        """
                    ),
                ),
            format = FileFormat.V5,
            expectedApiSignature =
                """
                    package test.pkg {
                      public class ClassMcClassface {
                        ctor public ClassMcClassface();
                      }
                    }
                """,
            enableTracing = true
        )
    }

    @Test
    fun `Check that emitting many trace batches does not corrupt packet pool`() {
        val traceFile = newFile("many-batches.perfetto-trace")
        createTraceDriver(traceFile.path).use { traceDriver ->
            val tracer = traceDriver.tracer
            tracer.trace("init") {}

            // Access the current ThreadTrack's underlying packet array scrapPool (ArrayDeque).
            // In an already-warmed test JVM, HotSpot JIT-compiles ArrayDeque.removeFirst() and
            // CSEs its three `getfield head` loads into a single register, which prevents a packet
            // array reference from being duplicated (and throwing ArrayIndexOutOfBoundsException),
            // while Pool.obtain()'s `scrapPool.removeFirstOrNull() ?: factory(this)` silently masks
            // null slots by allocating new arrays. Inspecting scrapPool after flush() directly
            // detects the null entries and lost size updates caused by concurrent addFirst() and
            // removeFirst() calls without needing to fork a non-JIT subprocess.
            val processTrack =
                tracer.javaClass
                    .getDeclaredField("process")
                    .apply { isAccessible = true }
                    .get(tracer) as androidx.tracing.ProcessTrack
            val threadTrack = processTrack.currentThreadTrack()
            val protoPool =
                androidx.tracing.Track::class
                    .java
                    .getDeclaredField("pool")
                    .apply { isAccessible = true }
                    .get(threadTrack)!!
            val packetArrayPool =
                protoPool.javaClass
                    .getDeclaredField("tracePacketArrayPool")
                    .apply { isAccessible = true }
                    .get(protoPool)!!
            val scrapPool =
                packetArrayPool.javaClass
                    .getDeclaredField("scrapPool")
                    .apply { isAccessible = true }
                    .get(packetArrayPool) as ArrayDeque<*>

            for (round in 1..200) {
                // Emit a small burst of 8 batches (16 trace sections = 32 events per batch)
                // to prime TraceSink.queue so any asynchronous drain thread is actively recycling
                // arrays while scrapPool remains non-empty.
                for (b in 1..8) {
                    for (i in 1..16) {
                        tracer.trace("burst-$b-$i") {}
                    }
                }
                // Emit paced batches while the burst is being drained back into scrapPool so
                // scrapPool.removeFirst() and scrapPool.addFirst() overlap.
                for (b in 1..32) {
                    val target = System.nanoTime() + (b * 500L)
                    while (System.nanoTime() < target) {
                        Thread.onSpinWait()
                    }
                    for (i in 1..16) {
                        tracer.trace("paced-$b-$i") {}
                    }
                }
                traceDriver.flush()
                // After flush(), 1 packet array is held in threadTrack.currentPacketArray and the
                // remaining 255 of 256 pooled arrays must be non-null in scrapPool.
                assertEquals(
                    "Packet pool has null entries on round $round",
                    0,
                    scrapPool.count { it == null }
                )
                assertEquals("Packet pool lost entries on round $round", 255, scrapPool.size)
            }
        }
        assertTrue("Trace file exists", traceFile.exists())
        assertTrue("Trace file is not empty", Files.size(traceFile.toPath()) > 0)
    }
}
