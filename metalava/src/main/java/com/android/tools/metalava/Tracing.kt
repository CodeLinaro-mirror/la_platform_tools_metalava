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
import kotlinx.coroutines.Dispatchers
import okio.appendingSink
import okio.buffer

internal fun createTraceDriver(traceFile: String?): TraceDriver {
    val traceSink =
        if (traceFile != null) {
            val trace = File(traceFile)
            trace.parentFile.mkdirs()
            TraceSink(sequenceId = 1, trace.appendingSink().buffer(), Dispatchers.IO)
        } else
            object : AbstractTraceSink() {
                @OptIn(DelicateTracingApi::class)
                override fun enqueue(pooledPacketArray: PooledTracePacketArray) {}

                override fun onDroppedTraceEvent() {}

                override fun flush() {}

                override fun close() {}
            }
    return TraceDriver(sink = traceSink, isCategoryEnabled = { traceFile != null })
}

internal inline fun <T> Tracer.trace(
    name: String,
    crossinline block: () -> T,
): T {
    return trace(category = "main", name = name, block = block)
}
