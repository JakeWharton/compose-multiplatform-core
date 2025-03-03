/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.window.core

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for [ConsumerAdapter] ensuring that the reflection calls work as expected.
 */
class ConsumerAdapterTest {

    internal class TestListenerInterface {

        val consumers = mutableListOf<Consumer<String>>()

        fun addConsumer(c: Consumer<String>) {
            consumers.add(c)
        }

        @Suppress("UNUSED_PARAMETER")
        fun addConsumer(context: Context, c: Consumer<String>) {
            consumers.add(c)
        }

        @Suppress("UNUSED_PARAMETER")
        fun addConsumer(activity: Activity, c: Consumer<String>) {
            consumers.add(c)
        }

        fun removeConsumer(c: Consumer<String>) {
            consumers.remove(c)
        }
    }

    private val value = "SOME_VALUE"
    private val loader = ConsumerAdapterTest::class.java.classLoader!!
    private val listenerInterface = TestListenerInterface()
    private val adapter = ConsumerAdapter(loader)

    @Test
    @SuppressLint("NewApi") // Test runs on the JVM so SDK check isn't necessary
    fun testAddByReflection() {
        val values = mutableListOf<String>()
        adapter.addConsumer(listenerInterface, String::class, "addConsumer") { s: String ->
            values.add(s)
        }

        assertEquals(1, listenerInterface.consumers.size)
        listenerInterface.consumers.first().accept(value)
        assertEquals(listOf(value), values)
    }

    @Test
    @SuppressLint("NewApi") // Test runs on the JVM so SDK check isn't necessary
    fun testSubscribeByReflection() {
        val values = mutableListOf<String>()
        adapter.createSubscription(
            listenerInterface,
            String::class,
            "addConsumer",
            "removeConsumer",
            mock()
        ) { s: String ->
            values.add(s)
        }

        assertEquals(1, listenerInterface.consumers.size)
        listenerInterface.consumers.first().accept(value)
        assertEquals(listOf(value), values)
    }

    @Test
    fun testSubscribeByReflectionForContext() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // java.util.function.Consumer#accept is supported after N.
            return
        }
        val values = mutableListOf<String>()
        val context = mock<Context>()
        adapter.createSubscription(
            listenerInterface,
            String::class,
            "addConsumer",
            "removeConsumer",
            context
        ) { s: String ->
            values.add(s)
        }

        assertEquals(1, listenerInterface.consumers.size)
        listenerInterface.consumers.first().accept(value)
        assertEquals(listOf(value), values)
    }

    @Test
    fun testDisposeSubscribe() {
        val values = mutableListOf<String>()
        val subscription = adapter.createSubscription(
            listenerInterface,
            String::class,
            "addConsumer",
            "removeConsumer",
            mock()
        ) { s: String ->
            values.add(s)
        }
        subscription.dispose()

        assertTrue(listenerInterface.consumers.isEmpty())
    }

    @Test
    fun testDisposeSubscribeForContext() {
        val values = mutableListOf<String>()
        val context = mock<Context>()
        val subscription = adapter.createSubscription(
            listenerInterface,
            String::class,
            "addConsumer",
            "removeConsumer",
            context
        ) { s: String ->
            values.add(s)
        }
        subscription.dispose()

        assertTrue(listenerInterface.consumers.isEmpty())
    }

    @Test
    fun testToStringAdd() {
        val values = mutableListOf<String>()
        val consumer: (String) -> Unit = { s: String -> values.add(s) }
        adapter.addConsumer(listenerInterface, String::class, "addConsumer", consumer)
        assertEquals(consumer.toString(), listenerInterface.consumers.first().toString())
    }
}