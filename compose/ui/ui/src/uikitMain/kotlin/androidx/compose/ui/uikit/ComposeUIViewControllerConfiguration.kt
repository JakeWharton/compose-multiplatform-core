/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.uikit

/**
 * Configuration of ComposeUIViewController behavior.
 */
class ComposeUIViewControllerConfiguration {
    /**
     * Control Compose behaviour on focus changed inside Compose.
     */
    var onFocusBehavior: OnFocusBehavior = OnFocusBehavior.FocusableAboveKeyboard

    /**
     * Reassign this property with an object implementing [ComposeUIViewControllerDelegate] to receive
     * UIViewController lifetime events.
     */
    var delegate = object : ComposeUIViewControllerDelegate {}
}

/**
 * Interface for UIViewController specific lifetime callbacks to allow injecting logic without overriding internal ComposeWindow.
 * All of those callbacks are invoked at the very end of overrided function implementation.
 */
interface ComposeUIViewControllerDelegate {
    fun viewDidLoad() = Unit
    fun viewWillAppear(animated: Boolean) = Unit
    fun viewDidAppear(animated: Boolean) = Unit
    fun viewWillDisappear(animated: Boolean) = Unit
    fun viewDidDisappear(animated: Boolean) = Unit
}

sealed interface OnFocusBehavior {
    /**
     * The Compose view will stay on the current position.
     */
    object DoNothing : OnFocusBehavior

    /**
     * The Compose view will be panned in "y" coordinates.
     * A focusable element should be displayed above the keyboard.
     */
    object FocusableAboveKeyboard : OnFocusBehavior

    // TODO Better to control OnFocusBehavior with existing WindowInsets.
    // Definition: object: FocusableBetweenInsets(insets: WindowInsets) : OnFocusBehavior
    // Usage: onFocusBehavior = FocusableBetweenInsets(WindowInsets.ime.union(WindowInsets.systemBars))
}
