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

package com.android.tools.metalava.testing

import androidx.tracing.AbstractTraceSink
import androidx.tracing.DelicateTracingApi
import androidx.tracing.PooledTracePacketArray
import androidx.tracing.Tracer
import androidx.tracing.wire.TraceDriver

fun getNoopTracer(): Tracer {
    return TraceDriver(
            sink =
                object : AbstractTraceSink() {
                    @OptIn(DelicateTracingApi::class)
                    override fun enqueue(pooledPacketArray: PooledTracePacketArray) {}

                    override fun onDroppedTraceEvent() {}

                    override fun flush() {}

                    override fun close() {}
                },
            isCategoryEnabled = { false }
        )
        .tracer
}
