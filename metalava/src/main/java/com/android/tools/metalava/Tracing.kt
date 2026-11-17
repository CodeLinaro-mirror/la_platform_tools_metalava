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

import androidx.tracing.AbstractTraceSink
import androidx.tracing.DelicateTracingApi
import androidx.tracing.PooledTracePacketArray
import androidx.tracing.Tracer
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import okio.appendingSink
import okio.buffer

/**
 * Wraps [TraceSink] to serialize all sink operations and drain queued packet arrays synchronously
 * on the calling thread via [EmptyCoroutineContext].
 *
 * `androidx.tracing` backs each track's packet array pool (`androidx.tracing.Pool`) with an
 * unsynchronized [ArrayDeque]. When [TraceSink] drains asynchronously on a background dispatcher,
 * `PooledTracePacketArray.recycle()` (`Pool.release` / `ArrayDeque.addFirst`) races with the
 * tracing thread's `ProtoPool.obtainTracePacketArray()` (`Pool.obtain` /
 * `ArrayDeque.removeFirstOrNull`), which can duplicate a packet array reference in the pool and
 * throw `ArrayIndexOutOfBoundsException: Index 32 out of bounds for length 32` (b/570135466).
 *
 * Synchronizing [enqueue] and running [TraceSink] with [EmptyCoroutineContext] ensures that
 * `PooledTracePacketArray.recycle()` completes on the calling thread while `Track.lock` is still
 * held and before `ProtoPool.obtainTracePacketArray()` is called.
 */
@OptIn(DelicateTracingApi::class)
private class SynchronizedTraceSink(private val delegate: AbstractTraceSink) : AbstractTraceSink() {
    @Synchronized
    override fun enqueue(pooledPacketArray: PooledTracePacketArray) {
        delegate.enqueue(pooledPacketArray)
    }

    @Synchronized
    override fun onDroppedTraceEvent() {
        delegate.onDroppedTraceEvent()
    }

    @Synchronized
    override fun flush() {
        delegate.flush()
    }

    @Synchronized
    override fun close() {
        delegate.close()
    }
}

internal fun createTraceDriver(traceFile: String?): TraceDriver {
    if (traceFile == null) return TraceDriver.getStubTraceDriver()

    val trace = File(traceFile)
    trace.parentFile.mkdirs()
    val traceSink =
        SynchronizedTraceSink(
            TraceSink(sequenceId = 1, trace.appendingSink().buffer(), EmptyCoroutineContext)
        )
    return TraceDriver(sink = traceSink)
}

internal inline fun <T> Tracer.trace(
    name: String,
    crossinline block: () -> T,
): T {
    return trace(category = "main", name = name, block = block)
}
