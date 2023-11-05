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

package com.android.tools.metalava.model.text

import kotlin.test.assertTrue
import org.junit.Test

class TextClassItemTest {
    @Test
    fun `test hasEqualReturnType() when return types are derived from interface type variables`() {
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package java.lang {
              public final class Float extends java.lang.Number implements java.lang.Comparable<java.lang.Float> {
              }
            }
            package java.time {
              public final class LocalDateTime implements java.time.chrono.ChronoLocalDateTime<java.time.LocalDate> java.io.Serializable java.time.temporal.Temporal java.time.temporal.TemporalAdjuster {
                method public java.time.LocalDate toLocalDate();
              }
            }
            package java.time.chrono {
              public interface ChronoLocalDateTime<D extends java.time.chrono.ChronoLocalDate> extends java.time.temporal.Temporal java.lang.Comparable<java.time.chrono.ChronoLocalDateTime<?>> java.time.temporal.TemporalAdjuster {
                method public D toLocalDate();
              }
            }
            package android.animation {
              public interface TypeEvaluator<T> {
                method public T evaluate(float, T, T);
              }
              public class FloatEvaluator implements android.animation.TypeEvaluator<java.lang.Number> {
                method public Float evaluate(float, Number, Number);
              }
            }
            package android.widget {
              public abstract class AdapterView<T extends android.widget.Adapter> extends android.view.ViewGroup {
                method public abstract T getAdapter();
              }
              public abstract class AbsListView extends android.widget.AdapterView<android.widget.ListAdapter> implements android.widget.Filter.FilterListener android.text.TextWatcher android.view.ViewTreeObserver.OnGlobalLayoutListener android.view.ViewTreeObserver.OnTouchModeChangeListener {
              }
              public interface ListAdapter extends android.widget.Adapter {
              }
              @android.widget.RemoteViews.RemoteView public class ListView extends android.widget.AbsListView {
                method public android.widget.ListAdapter getAdapter();
              }
            }
            package android.content {
              public abstract class AsyncTaskLoader<D> extends android.content.Loader<D> {
                method public abstract D loadInBackground();
              }
              public class CursorLoader extends android.content.AsyncTaskLoader<android.database.Cursor> {
                method public android.database.Cursor loadInBackground();
              }
            }
            package android.database {
              public final class CursorJoiner implements java.lang.Iterable<android.database.CursorJoiner.Result> java.util.Iterator<android.database.CursorJoiner.Result> {
                method public android.database.CursorJoiner.Result next();
              }
            }
            package java.util {
              public interface Iterator<E> {
                method public E next();
              }
            }
            package java.lang.invoke {
              public final class MethodType implements java.io.Serializable java.lang.invoke.TypeDescriptor.OfMethod<java.lang.Class<?>,java.lang.invoke.MethodType> {
                method public java.lang.invoke.MethodType changeParameterType(int, Class<?>);
                method public Class<?>[] parameterArray();
              }
              public static interface TypeDescriptor.OfMethod<F extends java.lang.invoke.TypeDescriptor.OfField<F>, M extends java.lang.invoke.TypeDescriptor.OfMethod<F, M>> extends java.lang.invoke.TypeDescriptor {
                method public M changeParameterType(int, F);
                method public F[] parameterArray();
              }
            }
            """
                    .trimIndent(),
            )

        val toLocalDate1 =
            codebase.getOrCreateClass("java.time.LocalDateTime").findMethod("toLocalDate", "")!!
        val toLocalDate2 =
            codebase
                .getOrCreateClass("java.time.chrono.ChronoLocalDateTime")
                .findMethod("toLocalDate", "")!!
        val evaluate1 =
            codebase
                .getOrCreateClass("android.animation.TypeEvaluator<T>")
                .findMethod("evaluate", "float, java.lang.Object, java.lang.Object")!!
        val evaluate2 =
            codebase
                .getOrCreateClass("android.animation.FloatEvaluator")
                .findMethod("evaluate", "float, java.lang.Number, java.lang.Number")!!
        val loadInBackground1 =
            codebase
                .getOrCreateClass("android.content.AsyncTaskLoader<D>")
                .findMethod("loadInBackground", "")!!
        val loadInBackground2 =
            codebase
                .getOrCreateClass("android.content.CursorLoader")
                .findMethod("loadInBackground", "")!!
        val next1 =
            codebase.getOrCreateClass("android.database.CursorJoiner").findMethod("next", "")!!
        val next2 = codebase.getOrCreateClass("java.util.Iterator<E>").findMethod("next", "")!!
        val changeParameterType1 =
            codebase
                .getOrCreateClass("java.lang.invoke.MethodType")
                .findMethod("changeParameterType", "int, java.lang.Class")!!
        val changeParameterType2 =
            codebase
                .getOrCreateClass("java.lang.invoke.TypeDescriptor.OfMethod")
                .findMethod("changeParameterType", "int, java.lang.invoke.TypeDescriptor.OfField")!!
        val parameterArray1 =
            codebase
                .getOrCreateClass("java.lang.invoke.MethodType")
                .findMethod("parameterArray", "")!!
        val parameterArray2 =
            codebase
                .getOrCreateClass("java.lang.invoke.TypeDescriptor.OfMethod")
                .findMethod("parameterArray", "")!!

        assertTrue(TextClassItem.hasEqualReturnType(toLocalDate1, toLocalDate2))
        assertTrue(TextClassItem.hasEqualReturnType(evaluate1, evaluate2))
        assertTrue(TextClassItem.hasEqualReturnType(loadInBackground1, loadInBackground2))
        assertTrue(TextClassItem.hasEqualReturnType(next1, next2))
        assertTrue(TextClassItem.hasEqualReturnType(changeParameterType1, changeParameterType2))
        assertTrue(TextClassItem.hasEqualReturnType(parameterArray1, parameterArray2))
    }

    @Test
    fun `test hasEqualReturnType() with equal bounds return types`() {
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package java.lang {
              public final class Class<T> implements java.lang.reflect.AnnotatedElement {
                method @Nullable public <A extends java.lang.annotation.Annotation> A getAnnotation(@NonNull Class<A>);
              }
              public interface AnnotatedElement {
                method @Nullable public <T extends java.lang.annotation.Annotation> T getAnnotation(@NonNull Class<T>);
              }
            }
            """
                    .trimIndent(),
            )

        val getAnnotation1 =
            codebase
                .getOrCreateClass("java.lang.Class<T>")
                .findMethod("getAnnotation", "java.lang.Class")!!
        val getAnnotation2 =
            codebase
                .getOrCreateClass("java.lang.AnnotatedElement")
                .findMethod("getAnnotation", "java.lang.Class")!!

        assertTrue(TextClassItem.hasEqualReturnType(getAnnotation1, getAnnotation2))
    }

    @Test
    fun `test hasEqualReturnType() with covariant return types`() {
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package android.widget {
              public abstract class AdapterView<T extends android.widget.Adapter> extends android.view.ViewGroup {
                method public abstract T getAdapter();
              }
              public abstract class AbsListView extends android.widget.AdapterView<android.widget.ListAdapter> implements android.widget.Filter.FilterListener android.text.TextWatcher android.view.ViewTreeObserver.OnGlobalLayoutListener android.view.ViewTreeObserver.OnTouchModeChangeListener {
              }
              public interface ListAdapter extends android.widget.Adapter {
              }
              @android.widget.RemoteViews.RemoteView public class ListView extends android.widget.AbsListView {
                method public android.widget.ListAdapter getAdapter();
              }
            }
            """
                    .trimIndent(),
            )

        val getAdapter1 =
            codebase
                .getOrCreateClass("android.widget.AdapterView<T extends android.widget.Adapter>")
                .findMethod("getAdapter", "")!!
        val getAdapter2 =
            codebase.getOrCreateClass("android.widget.ListView").findMethod("getAdapter", "")!!

        assertTrue(TextClassItem.hasEqualReturnType(getAdapter1, getAdapter2))
    }

    @Test
    fun `test equalMethodInClassContext()`() {
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package java.lang {
              public interface Comparable<T> {
                method public int compareTo(T);
              }
              public final class String implements java.lang.CharSequence java.lang.Comparable<java.lang.String> java.io.Serializable {
                method public int compareTo(@NonNull String);
              }
            }
            package java.lang.invoke {
              public final class MethodType implements java.io.Serializable java.lang.invoke.TypeDescriptor.OfMethod<java.lang.Class<?>,java.lang.invoke.MethodType> {
                method public java.lang.invoke.MethodType insertParameterTypes(int, Class<?>...);
              }
              public static interface TypeDescriptor.OfMethod<F extends java.lang.invoke.TypeDescriptor.OfField<F>, M extends java.lang.invoke.TypeDescriptor.OfMethod<F, M>> extends java.lang.invoke.TypeDescriptor {
                method public M insertParameterTypes(int, F...);
              }
            }
            package android.animation {
              public interface TypeEvaluator<T> {
                method public T evaluate(float, T, T);
              }
              public class ArgbEvaluator implements android.animation.TypeEvaluator {
                method public Object evaluate(float, Object, Object);
              }
              public class FloatArrayEvaluator implements android.animation.TypeEvaluator<float[]> {
                method public float[] evaluate(float, float[], float[]);
              }
            }
            """
                    .trimIndent(),
            )

        val compareTo1 =
            codebase
                .getOrCreateClass("java.lang.Comparable")
                .findMethod("compareTo", "java.lang.Object")!!
        val compareTo2 =
            codebase
                .getOrCreateClass("java.lang.String")
                .findMethod("compareTo", "java.lang.String")!!
        val insertParameterTypes1 =
            codebase
                .getOrCreateClass("java.lang.invoke.MethodType")
                .findMethod("insertParameterTypes", "int, java.lang.Class[]")!!
        val insertParameterTypes2 =
            codebase
                .getOrCreateClass("java.lang.invoke.TypeDescriptor.OfMethod")
                .findMethod(
                    "insertParameterTypes",
                    "int, java.lang.invoke.TypeDescriptor.OfField[]"
                )!!
        val evaluate1 =
            codebase
                .getOrCreateClass("android.animation.TypeEvaluator<T>")
                .findMethod("evaluate", "float, java.lang.Object, java.lang.Object")!!
        val evaluate2 =
            codebase
                .getOrCreateClass("android.animation.ArgbEvaluator")
                .findMethod("evaluate", "float, java.lang.Object, java.lang.Object")!!
        val evaluate3 =
            codebase
                .getOrCreateClass("android.animation.FloatArrayEvaluator")
                .findMethod("evaluate", "float, float[], float[]")!!

        assertTrue(TextClassItem.equalMethodInClassContext(compareTo1, compareTo2))
        assertTrue(
            TextClassItem.equalMethodInClassContext(insertParameterTypes1, insertParameterTypes2)
        )
        assertTrue(TextClassItem.equalMethodInClassContext(evaluate1, evaluate2))
        assertTrue(TextClassItem.equalMethodInClassContext(evaluate1, evaluate3))
    }
}
