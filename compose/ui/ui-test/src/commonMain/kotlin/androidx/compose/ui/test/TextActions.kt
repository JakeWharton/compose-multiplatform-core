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

package androidx.compose.ui.test

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsActions.PerformImeAction
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.performImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction

/**
 * Clears the text in this node in similar way to IME.
 */
fun SemanticsNodeInteraction.performTextClearance() {
    performTextReplacement("")
}

/**
 * Sends the given text to this node in similar way to IME.
 *
 * @param text Text to send.
 */
fun SemanticsNodeInteraction.performTextInput(text: String) {
    getNodeAndFocus()
    performSemanticsAction(SemanticsActions.InsertTextAtCursor) {
        it(AnnotatedString(text))
    }
}

/**
 * Sends the given selection to this node in similar way to IME.
 *
 * @param selection selection to send
 */
@ExperimentalTestApi
fun SemanticsNodeInteraction.performTextInputSelection(selection: TextRange) {
    getNodeAndFocus()
    performSemanticsAction(SemanticsActions.SetSelection) {
        // Pass true as the last parameter since this range is relative to the text before any
        // VisualTransformation is applied.
        it(selection.min, selection.max, true)
    }
}

/**
 * Replaces existing text with the given text in this node in similar way to IME.
 *
 * This does not reflect text selection. All the text gets cleared out and new inserted.
 *
 * @param text Text to send.
 */
fun SemanticsNodeInteraction.performTextReplacement(text: String) {
    getNodeAndFocus()
    performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
}

/**
 * Sends to this node the IME action associated with it in a similar way to the IME.
 *
 * The node needs to define its IME action in semantics via
 * [SemanticsPropertyReceiver.performImeAction].
 *
 * @throws AssertionError if the node does not support input or does not define IME action.
 * @throws IllegalStateException if the node did is not an editor or would not be able to establish
 * an input connection (e.g. does not define [ImeAction][SemanticsProperties.ImeAction] or
 * [PerformImeAction] or is not focused).
 */
fun SemanticsNodeInteraction.performImeAction() {
    val errorOnFail = "Failed to perform IME action."
    assert(hasPerformImeAction()) { errorOnFail }
    assert(!hasImeAction(ImeAction.Default)) { errorOnFail }
    val node = getNodeAndFocus(errorOnFail)

    wrapAssertionErrorsWithNodeInfo(selector, node) {
        performSemanticsAction(PerformImeAction) {
            commonAssert(it()) {
                buildGeneralErrorMessage(
                    "Failed to perform IME action, handler returned false.",
                    selector,
                    node
                )
            }
        }
    }
}

private fun SemanticsNodeInteraction.getNodeAndFocus(
    errorOnFail: String = "Failed to perform text input."
): SemanticsNode {
    val node = fetchSemanticsNode(errorOnFail)
    assert(isEnabled()) { errorOnFail }
    assert(hasSetTextAction()) { errorOnFail }
    assert(hasRequestFocusAction()) { errorOnFail }
    assert(hasInsertTextAtCursorAction()) { errorOnFail }

    if (!isFocused().matches(node)) {
        // Get focus
        performSemanticsAction(SemanticsActions.RequestFocus)
    }

    return node
}

private inline fun <R> wrapAssertionErrorsWithNodeInfo(
    selector: SemanticsSelector,
    node: SemanticsNode,
    block: () -> R
): R {
    try {
        return block()
    } catch (e: AssertionError) {
        throw ProxyAssertionError(e.message.orEmpty(), selector, node, e)
    }
}

private class ProxyAssertionError(
    message: String,
    selector: SemanticsSelector,
    node: SemanticsNode,
    cause: Throwable
) : AssertionError(buildGeneralErrorMessage(message, selector, node)) {
// TODO: [1.4 Update] JDK functionality is commented out. Also cause not passed to constructor

//    init {
//        // Duplicate the stack trace to make troubleshooting easier.
//        stackTrace = cause.stackTrace
//    }
}