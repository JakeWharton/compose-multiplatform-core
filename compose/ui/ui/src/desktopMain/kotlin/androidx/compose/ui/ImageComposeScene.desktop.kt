/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.mouse.MouseScrollOrientation
import androidx.compose.ui.input.mouse.MouseScrollUnit
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Render Compose [content] into an [Image]
 *
 * @param width The width of the content.
 * @param height The height of the content.
 * @param density Density of the content which will be used to convert [dp] units.
 * @param content Composable content which needed to be rendered.
 */
@ExperimentalComposeUiApi
fun renderComposeScene(
    width: Int,
    height: Int,
    density: Density = Density(1f),
    content: @Composable () -> Unit
): Image = ImageComposeScene(
    width = width,
    height = height,
    density = density,
    content = content
).use { it.render() }

/**
 * Executes the given [block] function on this [ImageComposeScene] and then closes it down
 * correctly whether an exception is thrown or not.
 *
 * @param block a function to process this [ImageComposeScene].
 * @return the result of [block] function invoked on this [ImageComposeScene].
 */
@OptIn(ExperimentalComposeUiApi::class)
inline fun <R> ImageComposeScene.use(
    block: (ImageComposeScene) -> R
): R {
    return try {
        block(this)
    } finally {
        close()
    }
}

/**
 * A virtual container that encapsulates Compose UI content with ability to draw it into an image.
 *
 * To set content, use `content` parameter of the constructor, or [setContent] method.
 *
 * To draw content into an image, use [render] method.
 *
 * After [ImageComposeScene] will no longer needed, you should call [close] method, so all resources
 * and subscriptions will be properly closed. Otherwise there can be a memory leak.
 *
 * Instead of calling [close] manually, you can use the helper function [use],
 * it will close scene for you.
 *
 * @param width The width of the content.
 * @param height The height of the content.
 * @param density Density of the content which will be used to convert [dp] units.
 * @param coroutineContext Context which will be used to launch effects ([LaunchedEffect],
 * [rememberCoroutineScope]) and run recompositions.
 * @param content Composable content which needed to be rendered.
 */
@ExperimentalComposeUiApi
class ImageComposeScene(
    width: Int,
    height: Int,
    density: Density = Density(1f),
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    content: @Composable () -> Unit = {},
) {
    private val surface = Surface.makeRasterN32Premul(width, height)

    private val scene = ComposeScene(
        density = density,
        coroutineContext = coroutineContext
    ).apply {
        constraints = Constraints(maxWidth = surface.width, maxHeight = surface.height)
        setContent(content = content)
    }

    /**
     * Close all resources and subscriptions. Not calling this method when [ImageComposeScene] is no
     * longer needed will cause a memory leak.
     *
     * All effects launched via [LaunchedEffect] or [rememberCoroutineScope] will be cancelled
     * (but not immediately).
     *
     * After calling this method, you cannot call any other method of this [ImageComposeScene].
     */
    fun close(): Unit = scene.close()

    /**
     * All currently registered [RootForTest]s. After calling [setContent] the first root
     * will be added. If there is an any [Popup] is present in the content, it will be added as
     * another [RootForTest]
     */
    val roots: Set<RootForTest> get() = scene.roots

    /**
     * Constraints used to measure and layout content.
     */
    var constraints: Constraints
        get() = scene.constraints
        set(value) {
            scene.constraints = value
        }

    /**
     * Update the composition with the content described by the [content] composable. After this
     * has been called the changes to produce the initial composition has been calculated and
     * applied to the composition.
     *
     * Will throw an [IllegalStateException] if the composition has been disposed.
     *
     * @param content Content of the [ImageComposeScene]
     */
    fun setContent(content: @Composable () -> Unit): Unit =
        scene.setContent(content = content)

    /**
     * Render the current content into an image. [nanoTime] will be used to drive all
     * animations in the content (or any other code, which uses [withFrameNanos]
     */
    fun render(nanoTime: Long = 0): Image {
        scene.render(surface.canvas, nanoTime)
        return surface.makeImageSnapshot()
    }

    /**
     * Render the current content into an image. [time] will be used to drive all
     * animations in the content (or any other code, which uses [withFrameNanos]
     */
    @ExperimentalTime
    fun render(time: Duration): Image =
        render(time.toLong(NANOSECONDS))

    /**
     * Send pointer event to the content.
     *
     * @param eventType Indicates the primary reason that the event was sent.
     * @param position The [Offset] of the current pointer event, relative to the content.
     * @param timeMillis The time of the current pointer event, in milliseconds. The start (`0`) time
     * is platform-dependent.
     * @param type The device type that produced the event, such as [mouse][PointerType.Mouse],
     * or [touch][PointerType.Touch].
     * @param mouseEvent The original native event.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun sendPointerEvent(
        eventType: PointerEventType,
        position: Offset,
        timeMillis: Long = System.nanoTime() / 1_000_000L,
        type: PointerType = PointerType.Mouse,
        mouseEvent: MouseEvent? = null
    ): Unit = scene.sendPointerEvent(
        eventType, position, timeMillis, type, mouseEvent
    )

    // TODO(demin): remove/change when we will have scroll event support in the common code
    // TODO(demin): return Boolean (when it is consumed).
    //  see ComposeLayer todo about AWTDebounceEventQueue
    /**
     * Send pointer scroll event to the content.
     *
     * @param position The [Offset] of the current pointer event, relative to the content
     * @param delta Change of mouse scroll.
     * Positive if scrolling down, negative if scrolling up.
     * @param orientation Orientation in which scrolling event occurs.
     * Up/down wheel scrolling causes events in vertical orientation.
     * Left/right wheel scrolling causes events in horizontal orientation.
     * @param timeMillis The time of the current pointer event, in milliseconds. The start (`0`) time
     * is platform-dependent.
     * @param type The device type that produced the event, such as [mouse][PointerType.Mouse],
     * or [touch][PointerType.Touch].
     * @param mouseEvent The original native event
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Suppress("UNUSED_PARAMETER")
    @ExperimentalComposeUiApi // it is more experimental than ComposeScene itself
    fun sendPointerScrollEvent(
        position: Offset,
        delta: MouseScrollUnit,
        orientation: MouseScrollOrientation = MouseScrollOrientation.Vertical,
        timeMillis: Long = System.nanoTime() / 1_000_000L,
        type: PointerType = PointerType.Mouse,
        mouseEvent: MouseEvent? = null,
//        buttons: PointerButtons? = null,
//        keyboardModifiers: PointerKeyboardModifiers? = null,
    ): Unit = scene.sendPointerScrollEvent(
        position, delta, orientation, timeMillis, type, mouseEvent
    )

    /**
     * Send [KeyEvent] to the content.
     * @return true if the event was consumed by the content
     */
    fun sendKeyEvent(event: KeyEvent): Boolean = scene.sendKeyEvent(event)
}