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

package com.android.tools.metalava.model.annotation.binding

/**
 * An annotation that identifies the constructor that should be used to instantiate classes supplied
 * to [bindTo].
 *
 * This is only needed when a class has multiple constructors and must be applied to one and only
 * one of them.
 */
@Target(AnnotationTarget.CONSTRUCTOR) annotation class BindingConstructor
